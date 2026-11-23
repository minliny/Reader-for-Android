package io.reader.android

import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.reader.android.ui.ReaderAppTheme
import io.reader.android.ui.ReaderDayColors
import io.reader.android.ui.ReaderNightColors
import io.reader.android.ui.ReaderRoute
import io.reader.android.ui.ReaderViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Source-only ordinary-page geometry/appearance candidate. Never opens the existing book. */
@RunWith(AndroidJUnit4::class)
class UiSystemBarsLayoutTest {
    private val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device by lazy { UiDevice.getInstance(instrumentation) }
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var evidence: File
    private var captures = 0
    private data class Flags(val status: Boolean, val navigation: Boolean)
    @get:Rule val rules: TestRule = RuleChain.outerRule(Timeout.builder().withTimeout(60, TimeUnit.SECONDS).build()).around(compose)

    @Test fun existingBookshelfAndSettingsRespectSafeInsetsAndRestoreSystemBarAppearance() {
        marker("method-enter")
        var original: ReaderAppTheme? = null
        var originalFlags: Flags? = null
        var themeDispatched = false
        var primary: Throwable? = null
        var phase = "precondition"
        try {
            val hash = InstrumentationRegistry.getArguments().getString("expectedSHA")
                ?: throw AssertionError("expectedSHA required from latest stopped proof")
            assertEquals("aac01dbeb5e10710ee802466f077889f5b83d27db70a69e68a33af9ac51658a5", hash)
            scenario = ActivityScenario.launch(MainActivity::class.java)
            val current = activity(); val model = current.readerViewModel
            waitHealthyReady(model)
            assertEquals(ReaderRoute.Bookshelf, model.state.value.route); assertNull(model.state.value.reading)
            assertEquals(listOf("local:$hash"), model.state.value.books.map { it.id })
            assertEquals("local", model.state.value.books.single().sourceId)
            val theme = model.state.value.theme
            val prefs = current.getSharedPreferences("reader.display", 0)
            // This run requires exact final preference bytes, not absent-default materialization.
            assertTrue("Original appTheme key must already exist", prefs.contains("appTheme"))
            assertEquals(theme.name, prefs.getString("appTheme", null))
            original = theme
            evidence = File(current.cacheDir, "A2SystemBarsLayout/${UUID.randomUUID()}")
            assertTrue(evidence.mkdirs())
            val initialFlags = awaitFlags(theme)
            originalFlags = initialFlags
            marker("existing-book-and-theme-bound", Bundle().apply {
                putString("bookId", "local:$hash"); putString("originalTheme", theme.name)
                putBoolean("themeKeyPresent", true)
                putBoolean("originalLightStatusAppearance", initialFlags.status)
                putBoolean("originalLightNavigationAppearance", initialFlags.navigation)
            })

            phase = "bookshelf-safe-bounds"
            assertSafeFrame(ReaderRoute.Bookshelf, "书架", "bookshelf-safe-bounds")
            compose.onNodeWithTag("reader_tab_settings").performClick()
            compose.waitUntil(5_000) { model.state.value.route == ReaderRoute.Settings || failed(model) }
            healthy(model); assertEquals(ReaderRoute.Settings, model.state.value.route)
            phase = "settings-safe-bounds"
            assertSafeFrame(ReaderRoute.Settings, "设置", "settings-safe-bounds")

            phase = "opposite-theme"
            val opposite = if (isNight(theme)) ReaderAppTheme.Light else ReaderAppTheme.Dark
            themeDispatched = true
            selectTheme(opposite)
            val changed = awaitFlags(opposite)
            assertEquals(!initialFlags.status, changed.status)
            assertEquals(!initialFlags.navigation, changed.navigation)
            capture("opposite-theme-flags", "opposite.png", opposite, changed)
        } catch (failure: Throwable) {
            primary = failure
            try { marker("method-failure", Bundle().apply { putString("phase", phase); putString("message", failure.message?.take(256)) }) }
            catch (markerFailure: Throwable) { if (markerFailure !== failure) failure.addSuppressed(markerFailure) }
            throw failure
        } finally {
            try {
                if (themeDispatched) {
                    val captured = original ?: throw AssertionError("Original theme missing")
                    healthy(activity().readerViewModel)
                    assertEquals(ReaderRoute.Settings, activity().readerViewModel.state.value.route)
                    selectTheme(captured)
                    val restored = awaitFlags(captured)
                    assertEquals(originalFlags, restored)
                    assertEquals(captured.name, activity().getSharedPreferences("reader.display", 0).getString("appTheme", null))
                    capture("original-theme-and-flags-restored", "restored.png", captured, restored)
                    assertEquals(2, captures)
                }
            } catch (cleanup: Throwable) {
                val failure = primary
                if (failure == null) { primary = cleanup; throw cleanup }
                if (failure !== cleanup) failure.addSuppressed(cleanup)
            } finally {
                try { finishOwnedHealthyPage() }
                catch (cleanup: Throwable) {
                    val failure = primary
                    if (failure == null) throw cleanup
                    if (failure !== cleanup) failure.addSuppressed(cleanup)
                }
            }
        }
        marker("method-exit")
    }

    private fun activity() = currentOrNull() ?: throw AssertionError("Actual MainActivity absent")
    private fun currentOrNull(): MainActivity? {
        var current: MainActivity? = null
        try { scenario?.onActivity { current = it } } catch (_: IllegalStateException) { }
        return current
    }
    private fun failed(model: ReaderViewModel): Boolean {
        val s = model.state.value
        return s.error != null || s.protocolRecoveryReference != null || s.pendingImports.isNotEmpty() ||
            s.pendingPositions.isNotEmpty() || s.pendingBookmarks.isNotEmpty() || s.pendingSearchClose != null
    }
    private fun healthy(model: ReaderViewModel) { assertFalse("Original operation must not be unresolved/failed", failed(model)); assertEquals(0L, model.state.value.importRevision) }
    private fun waitHealthyReady(model: ReaderViewModel) {
        compose.waitUntil(15_000) { !model.state.value.busy && (model.state.value.ready || failed(model)) }
        healthy(model); assertTrue(model.state.value.ready && model.state.value.pendingReady)
    }
    private fun selected(tag: String) {
        val node = compose.onNodeWithTag(tag).fetchSemanticsNode()
        assertTrue(SemanticsProperties.Selected in node.config && node.config[SemanticsProperties.Selected])
    }
    private fun attachedDraw(view: View) {
        val drawn = AtomicBoolean(false); lateinit var observer: ViewTreeObserver
        val listener = ViewTreeObserver.OnDrawListener { if (view.isAttachedToWindow && view.isShown) drawn.set(true) }
        compose.runOnUiThread { assertTrue(view.isAttachedToWindow && view.isShown); observer = view.viewTreeObserver; observer.addOnDrawListener(listener); view.invalidate() }
        try { compose.waitUntil(5_000) { drawn.get() }; instrumentation.runOnMainSync { assertTrue(view.isAttachedToWindow && view.isShown) } }
        finally { compose.runOnUiThread { if (observer.isAlive) observer.removeOnDrawListener(listener) } }
    }

    private fun isNight(theme: ReaderAppTheme): Boolean = theme == ReaderAppTheme.Dark ||
        (theme == ReaderAppTheme.System && (activity().resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
    private fun expectedDarkIcons(theme: ReaderAppTheme) =
        (if (isNight(theme)) ReaderNightColors else ReaderDayColors).background.luminance() > 0.5f
    private fun flags(): Flags {
        val current = activity()
        return compose.runOnUiThread {
            val controller = WindowCompat.getInsetsController(current.window, current.window.decorView)
            Flags(controller.isAppearanceLightStatusBars, controller.isAppearanceLightNavigationBars)
        }
    }
    private fun awaitFlags(theme: ReaderAppTheme): Flags {
        val expected = expectedDarkIcons(theme)
        compose.waitUntil(5_000) {
            failed(activity().readerViewModel) ||
                (activity().readerViewModel.state.value.theme == theme && flags() == Flags(expected, expected))
        }
        healthy(activity().readerViewModel)
        attachedDraw(activity().window.decorView)
        val actual = flags(); assertEquals(Flags(expected, expected), actual)
        return actual
    }
    private fun selectTheme(theme: ReaderAppTheme) {
        if (activity().readerViewModel.state.value.theme != theme) compose.onNodeWithTag("reader_theme_${theme.name}").performClick()
        compose.waitUntil(5_000) { activity().readerViewModel.state.value.theme == theme || failed(activity().readerViewModel) }
        healthy(activity().readerViewModel); selected("reader_theme_${theme.name}")
        assertEquals(theme, activity().readerViewModel.state.value.theme)
    }

    private fun assertSafeFrame(route: ReaderRoute, title: String, stage: String) {
        healthy(activity().readerViewModel); assertEquals(route, activity().readerViewModel.state.value.route)
        assertNull(activity().readerViewModel.state.value.reading)
        compose.waitForIdle(); attachedDraw(activity().window.decorView)
        compose.onNodeWithTag("reader_page_title").assertTextEquals(title)
        val current = activity()
        val safe = compose.runOnUiThread {
            val decor = current.window.decorView
            val root = ViewCompat.getRootWindowInsets(decor) ?: throw AssertionError("Actual root WindowInsets absent")
            assertFalse("This ordinary-page probe does not admit an open IME", root.isVisible(WindowInsetsCompat.Type.ime()))
            val bars = root.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val origin = IntArray(2).also(decor::getLocationInWindow)
            Rect((origin[0] + bars.left).toFloat(), (origin[1] + bars.top).toFloat(),
                (origin[0] + decor.width - bars.right).toFloat(), (origin[1] + decor.height - bars.bottom).toFloat())
        }
        val header = compose.onNodeWithTag("reader_app_top_bar").fetchSemanticsNode().boundsInWindow
        val headline = compose.onNodeWithTag("reader_page_title").fetchSemanticsNode().boundsInWindow
        val navigation = compose.onNodeWithTag("reader_app_navigation").fetchSemanticsNode().boundsInWindow
        within(header, safe, "header"); within(headline, safe, "headline"); within(navigation, safe, "navigation")
        within(headline, header, "headline in its header")
        assertTrue("Header/navigation overlap", header.bottom <= navigation.top + 1f)
        compose.onNodeWithTag("reader_app_top_bar").assertIsDisplayed()
        compose.onNodeWithTag("reader_page_title").assertIsDisplayed()
        compose.onNodeWithTag("reader_app_navigation").assertIsDisplayed()
        marker(stage, Bundle().apply {
            putString("route", route.name); bounds("safe", safe); bounds("header", header)
            bounds("headline", headline); bounds("navigation", navigation)
            putFloat("roundingTolerancePx", 1f)
        })
    }
    private fun within(actual: Rect, outer: Rect, label: String) {
        assertTrue("$label has actual dimensions", actual.width > 0 && actual.height > 0 && outer.width > 0 && outer.height > 0)
        assertTrue("$label outside safe bounds", actual.left >= outer.left - 1f && actual.top >= outer.top - 1f &&
            actual.right <= outer.right + 1f && actual.bottom <= outer.bottom + 1f)
    }
    private fun Bundle.bounds(prefix: String, rect: Rect) {
        putFloat(prefix + "Left", rect.left); putFloat(prefix + "Top", rect.top)
        putFloat(prefix + "Right", rect.right); putFloat(prefix + "Bottom", rect.bottom)
    }
    private fun capture(stage: String, name: String, theme: ReaderAppTheme, actual: Flags) {
        assertTrue("At most two requested full-window screenshots", captures < 2)
        assertEquals(actual, flags())
        val target = File(evidence, name); assertFalse(target.exists()); captures++
        assertTrue(device.takeScreenshot(target))
        val bytes = target.readBytes()
        assertTrue(bytes.size > 8 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)))
        marker(stage, Bundle().apply {
            putString("theme", theme.name); putBoolean("expectedDarkIcons", expectedDarkIcons(theme))
            putBoolean("lightStatusAppearance", actual.status); putBoolean("lightNavigationAppearance", actual.navigation)
            putString("cacheRelativeEvidence", target.relativeTo(activity().cacheDir).path)
            putLong("pngBytes", bytes.size.toLong()); putString("pngSha256", sha(bytes)); putInt("screenshotIndex", captures)
        })
    }

    private fun finishOwnedHealthyPage() {
        val owned = scenario ?: return
        if (owned.state == Lifecycle.State.DESTROYED) {
            owned.close(); marker("activity-destroyed"); return
        }
        val current = currentOrNull() ?: throw AssertionError("Owned Activity absent before confirmed destruction")
        val state = current.readerViewModel.state.value
        if (state.busy || state.reading != null || state.route !in setOf(ReaderRoute.Bookshelf, ReaderRoute.Settings) || failed(current.readerViewModel)) {
            marker("activity-retained-for-controller")
            throw AssertionError("Activity retained with original state; controller may stop only after instrumentation exits")
        }
        val destroyed = CountDownLatch(1)
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_DESTROY) destroyed.countDown() }
        var added = false
        var primary: Throwable? = null
        try {
            instrumentation.runOnMainSync {
                val latest = current.readerViewModel.state.value
                assertFalse(latest.busy); assertNull(latest.reading); assertTrue(latest.route in setOf(ReaderRoute.Bookshelf, ReaderRoute.Settings))
                current.lifecycle.addObserver(observer); added = true; current.finish()
            }
            assertTrue("Owned ordinary-page Activity did not naturally finish", destroyed.await(5, TimeUnit.SECONDS))
            compose.waitUntil(5_000) { owned.state == Lifecycle.State.DESTROYED }
            assertEquals(Lifecycle.State.DESTROYED, owned.state)
            owned.close() // Close only the already destroyed scenario; no fallback Activity on failure.
            marker("activity-destroyed")
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            if (added) try { instrumentation.runOnMainSync { current.lifecycle.removeObserver(observer) } }
            catch (cleanup: Throwable) {
                val failure = primary
                if (failure == null) throw cleanup
                if (failure !== cleanup) failure.addSuppressed(cleanup)
            }
        }
    }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun marker(step: String, fields: Bundle = Bundle()) {
        fields.putString("readerStep", step)
        fields.putString("readerMethod", "existingBookshelfAndSettingsRespectSafeInsetsAndRestoreSystemBarAppearance")
        fields.putLong("readerElapsedMs", SystemClock.elapsedRealtime())
        instrumentation.sendStatus(2, fields)
    }
}
