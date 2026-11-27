package io.reader.rebuild.goldens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import io.reader.rebuild.design.LocalReaderColors
import io.reader.rebuild.design.ReaderTheme
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Duration
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadows.ShadowDialog

internal data class AndroidGoldenScene(
    val id: String,
    val state: String,
    val sample: String,
    val width: Int = 402,
    val height: Int = 700,
    val fontScale: Float = 1f,
    val night: Boolean = false,
    val difference: String = "Android Material 3, Noto, system navigation and Android drawable semantics.",
    val dialog: Boolean = false,
    val content: @Composable () -> Unit,
)

internal object AndroidGoldenRenderer {
    private val nightFlag = "-night"

    fun capture(scene: AndroidGoldenScene): Bitmap {
        // Let the simulated looper advance the frame clock. In automatic mode
        // an indeterminate progress indicator schedules another immediate frame.
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        val resources = org.robolectric.RuntimeEnvironment.getApplication().resources
        val original = org.robolectric.RuntimeEnvironment.getQualifiers()
        val orientation = if (scene.width > scene.height) "land" else "port"
        org.robolectric.RuntimeEnvironment.setQualifiers("zh-rCN-w${scene.width}dp-h${scene.height}dp-$orientation${if (scene.night) nightFlag else ""}-mdpi")
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val host = activity.get()
        val surface = ComposeView(host)
        try {
            val bg = resources.displayMetrics
            check(bg.density == 1f) { "Golden capture requires mdpi" }
            host.setContentView(surface)
            surface.setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, scene.fontScale)) {
                    ReaderTheme(night = scene.night) {
                        Box(Modifier.fillMaxSize().background(LocalReaderColors.current.background)) {
                            scene.content()
                        }
                    }
                }
            }
            val looper = shadowOf(Looper.getMainLooper())
            repeat(if (scene.dialog) 32 else 7) {
                looper.idleFor(Duration.ofMillis(16))
                if (surface.isAttachedToWindow) measure(surface, scene.width, scene.height)
            }
            check(surface.isAttachedToWindow && surface.width == scene.width && surface.height == scene.height)
            val image = Bitmap.createBitmap(scene.width, scene.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(image)
            surface.draw(canvas)
            if (scene.dialog) drawDialog(canvas, scene)
            val pixels = pixels(image)
            check(pixels.asSequence().distinct().take(12).count() == 12) { "${scene.id}: blank or unrendered surface" }
            return image
        } finally {
            surface.disposeComposition()
            activity.pause().stop().destroy()
            shadowOf(Looper.getMainLooper()).idle()
            org.robolectric.RuntimeEnvironment.setQualifiers(original)
        }
    }

    private fun drawDialog(canvas: Canvas, scene: AndroidGoldenScene) {
        val dialog = checkNotNull(ShadowDialog.getLatestDialog()) { "Compose AlertDialog window did not open" }
        val window = checkNotNull(dialog.window)
        val decor = window.decorView
        val dim = if (window.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0)
            (window.attributes.dimAmount * 255f).toInt() else 0
        canvas.drawColor(dim shl 24)
        decor.measure(View.MeasureSpec.makeMeasureSpec(scene.width, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(scene.height, View.MeasureSpec.AT_MOST))
        decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
        check(decor.width > 0 && decor.height > 0) { "Compose AlertDialog measured to zero" }
        canvas.save()
        canvas.translate((scene.width - decor.width) / 2f, (scene.height - decor.height) / 2f)
        decor.draw(canvas)
        canvas.restore()
    }

    private fun measure(view: View, width: Int, height: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
    }

    fun save(image: Bitmap, file: File) {
        file.absoluteFile.parentFile?.mkdirs()
        file.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    fun pixels(image: Bitmap): IntArray = IntArray(image.width * image.height).also {
        image.getPixels(it, 0, image.width, 0, 0, image.width, image.height)
    }

    fun pixelSHA256(image: Bitmap): String {
        val pixels = pixels(image)
        val bytes = ByteBuffer.allocate(pixels.size * Int.SIZE_BYTES)
        bytes.asIntBuffer().put(pixels)
        return sha256(bytes.array())
    }

    fun compare(id: String, actual: Bitmap, baseline: File) {
        val expected = checkNotNull(BitmapFactory.decodeFile(baseline.absolutePath)) { "Missing golden $id: $baseline" }
        try {
            check(expected.width == actual.width && expected.height == actual.height) { "$id viewport changed" }
            val observed = pixels(actual)
            val registered = pixels(expected)
            if (!observed.contentEquals(registered)) {
                val offset = observed.indices.first { observed[it] != registered[it] }
                val candidate = File("build/reports/reader-goldens/$id.actual.png")
                save(actual, candidate)
                error("$id diverged at (${offset % actual.width}, ${offset / actual.width}); actual=${candidate.absolutePath}")
            }
        } finally {
            expected.recycle()
        }
    }

    fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
