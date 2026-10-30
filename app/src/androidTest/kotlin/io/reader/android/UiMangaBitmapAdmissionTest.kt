package io.reader.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.reader.android.runtime.ReaderCoreEventActor
import io.reader.android.runtime.ReaderMangaResourceStore
import io.reader.android.runtime.ReaderMangaTransferIdentity
import io.reader.android.runtime.http.ReaderHttpHost
import io.reader.android.runtime.http.ReaderMangaBitmapPolicy
import io.reader.android.runtime.http.ReaderMangaResourceHost
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Real Android codec bounds and the ordinary Host output consumer, using only UUID-owned cache.
 * No Activity, Library, Core actor/runtime or App store is opened. The guards and typed replies do not
 * establish zero allocation inside the SDK, a process/GPU memory bound, or visual acceptance. */
@RunWith(AndroidJUnit4::class)
class UiMangaBitmapAdmissionTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val phaseRule = TestRule { base, _ -> object : Statement() {
        override fun evaluate() { marker("rule-enter"); try { base.evaluate() } finally { marker("rule-exit") } }
    } }
    @get:Rule val rules: TestRule = RuleChain.outerRule(
        Timeout.builder().withTimeout(30, TimeUnit.SECONDS).withLookingForStuckThread(true).build()
    ).around(phaseRule)

    @Test fun platformBoundsGateOrdinaryAndGraphicsBeforePixelAdmission(): Unit = runBlocking {
        marker("method-enter")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val relative = "reader-manga-bitmap-admission/" + UUID.randomUUID()
        val probe = File(context.cacheDir, relative)
        assertTrue(probe.mkdirs())
        var failure: Throwable? = null
        try {
            val root = File(probe, "resources")
            val store = ReaderMangaResourceStore(root)
            // Defaults are intentional: both consumer paths use the production Android bounds adapter.
            val host = ReaderMangaResourceHost(ReaderHttpHost(), store)
            val small = smallPng()
            val smallBounds = platformBounds(small)
            assertEquals(64 to 96, smallBounds)
            val smallIdentity = identity("small-png")
            store.commit(store.stage(smallIdentity, listOf(small), "image/png"))
            val smallReply = host.execute(output(smallIdentity, small.size.toLong(), 1))
            assertTrue("Ordinary output must expose its actual production consumer", smallReply is ReaderCoreEventActor.HostReply.AssetConsume)
            smallReply as ReaderCoreEventActor.HostReply.AssetConsume
            assertEquals(1L, smallReply.assetId); assertEquals(1L, smallReply.operationId)
            assertEquals(small.size.toLong(), smallReply.bytes)
            val smallResult = smallReply.consumer(small)
            assertTrue("Valid platform PNG must reach Complete", smallResult is ReaderCoreEventActor.HostReply.Complete)
            val complete = JSONObject((smallResult as ReaderCoreEventActor.HostReply.Complete).resultJson)
            assertTrue(complete.getBoolean("consumed")); assertEquals(1L, complete.getLong("assetId"))
            assertEquals(small.size.toLong(), complete.getLong("bytes"))
            assertEquals(small.size.toLong(), complete.getLong("preparedBytes"))
            val decoded = store.readDecodedByTransferId(smallIdentity.wireId, smallIdentity.resourceRef)
            assertArrayEquals("Ordinary Host output must retain the original encoded PNG bytes", small, decoded)
            marker("bitmap-small-platform-consumer-complete", Bundle().apply {
                putInt("width", smallBounds.first); putInt("height", smallBounds.second)
                putInt("encodedBytes", small.size); putString("encodedSha256", sha(small))
                putString("decodedStoreSha256", sha(decoded)); putString("transferId", smallIdentity.wireId)
                putString("scope", "PLATFORM_BITMAPFACTORY_ORDINARY_HOST_CONSUMER_AND_BYTE_PRESERVING_STORE")
            })
            assertTrue(store.evict(smallIdentity, sha(small)))
            assertTrue(root.listFiles().orEmpty().isEmpty())

            val alpha = smallPng(Color.argb(128, 255, 0, 0))
            val regionReservation = ReaderMangaBitmapPolicy.graphics(64, 96, alpha.size.toLong(), 48)
            val decoder = BitmapRegionDecoder.newInstance(alpha, 0, alpha.size, false)
            val observedRegion = try {
                assertEquals(64, decoder.width); assertEquals(96, decoder.height)
                val region = checkNotNull(decoder.decodeRegion(Rect(0, 0, 64, 48),
                    BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565; inScaled = false }))
                try {
                    assertEquals(64, region.width); assertEquals(48, region.height)
                    assertEquals(64L * 48 * 8, regionReservation.stripBytes)
                    assertTrue(region.allocationByteCount > 0 && region.allocationByteCount.toLong() <= regionReservation.stripBytes)
                    Triple(region.config.toString(), region.allocationByteCount, region.hasAlpha())
                } finally { region.recycle() }
            } finally { decoder.recycle() }
            val alphaIdentity = identity("alpha-graphics-png")
            store.commit(store.stage(alphaIdentity, listOf(alpha), "image/png"))
            val alphaReply = host.execute(output(alphaIdentity, alpha.size.toLong(), 5, graphicsPlan(64, 96)))
            assertTrue(alphaReply is ReaderCoreEventActor.HostReply.AssetConsume)
            val alphaResult = (alphaReply as ReaderCoreEventActor.HostReply.AssetConsume).consumer(alpha)
            assertTrue("A valid alpha PNG must not fail when the platform returns a wider region format",
                alphaResult is ReaderCoreEventActor.HostReply.Complete)
            val alphaComplete = JSONObject((alphaResult as ReaderCoreEventActor.HostReply.Complete).resultJson)
            assertTrue(alphaComplete.getBoolean("consumed")); assertEquals(5L, alphaComplete.getLong("assetId"))
            assertEquals(alpha.size.toLong(), alphaComplete.getLong("bytes"))
            val jpeg = store.readDecodedByTransferId(alphaIdentity.wireId, alphaIdentity.resourceRef)
            assertEquals(jpeg.size.toLong(), alphaComplete.getLong("preparedBytes"))
            val jpegBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, jpegBounds)
            assertEquals(64, jpegBounds.outWidth); assertEquals(96, jpegBounds.outHeight)
            assertEquals("image/jpeg", jpegBounds.outMimeType)
            assertArrayEquals("Graphics output must preserve its original encoded alpha PNG",
                alpha, store.read(alphaIdentity, sha(alpha), alpha.size.toLong()).encoded)
            marker("bitmap-alpha-graphics-platform-complete", Bundle().apply {
                putString("requestedRegionConfig", "RGB_565"); putString("actualRegionConfig", observedRegion.first)
                putInt("actualRegionAllocationBytes", observedRegion.second); putBoolean("actualRegionHasAlpha", observedRegion.third)
                putInt("regionWidth", 64); putInt("regionHeight", 48); putLong("regionReservationBytes", regionReservation.stripBytes)
                putInt("outputWidth", jpegBounds.outWidth); putInt("outputHeight", jpegBounds.outHeight)
                putString("outputMimeType", jpegBounds.outMimeType); putInt("outputBytes", jpeg.size)
                putString("outputSha256", sha(jpeg)); putString("encodedInputSha256", sha(alpha))
                putBoolean("encodedInputPreserved", true)
            })
            assertTrue(store.evict(alphaIdentity, sha(alpha)))
            assertTrue(root.listFiles().orEmpty().isEmpty())

            val header = Base64.decode(LONG_JPEG_BASE64, Base64.DEFAULT)
            assertEquals(623, header.size); assertEquals(LONG_JPEG_SHA256, sha(header))
            val longBounds = platformBounds(header)
            assertEquals("The fixed header must exercise actual Android JPEG bounds", 4096 to 32768, longBounds)
            marker("bitmap-fixed-core-header-platform-bounds", Bundle().apply {
                putString("fixtureCoreCommit", FIXTURE_CORE_COMMIT); putString("fixtureCorePath", FIXTURE_CORE_PATH)
                putString("fixtureSha256", sha(header)); putInt("fixtureBytes", header.size)
                putInt("width", longBounds.first); putInt("height", longBounds.second)
                putString("decodeScope", "IN_JUST_DECODE_BOUNDS_ONLY_HEADER_HAS_NO_PIXEL_BODY")
            })
            val longIdentity = identity("header-only-jpeg")
            store.commit(store.stage(longIdentity, listOf(header), "image/jpeg"))
            val longReply = host.execute(output(longIdentity, header.size.toLong(), 2))
            assertTrue(longReply is ReaderCoreEventActor.HostReply.AssetConsume)
            // Never ask BitmapFactory for full pixels here. The real ordinary consumer must reject its own bounds.
            val longResult = (longReply as ReaderCoreEventActor.HostReply.AssetConsume).consumer(header)
            assertError(longResult, "RESOURCE_EXHAUSTED", "MANGA_IMAGE_WORKING_BUDGET")
            assertDecodedAbsent(store, longIdentity)
            assertArrayEquals(header, store.read(longIdentity, sha(header), header.size.toLong()).encoded)
            marker("bitmap-long-header-consumer-budget-rejected", Bundle().apply {
                putString("code", "RESOURCE_EXHAUSTED"); putString("message", "MANGA_IMAGE_WORKING_BUDGET")
                putBoolean("decodedStoreAbsent", true); putBoolean("encodedInputPreserved", true)
                putString("scope", "HOST_ENTRY_GUARD_AFTER_PLATFORM_BOUNDS_BEFORE_FULL_PIXEL_DECODE_AND_STORE_PUT")
                putString("allocationClaim", "NO_SDK_INTERNAL_ZERO_ALLOCATION_OR_WHOLE_PROCESS_MEMORY_CLAIM")
            })
            assertTrue(store.evict(longIdentity, sha(header)))
            assertTrue(root.listFiles().orEmpty().isEmpty())

            // The production execute boundary must reject these plans without issuing an AssetConsume.
            val hugePlan = graphicsPlan(4096, 32768)
            val hugeReply = host.execute(output(identity("huge-graphics"), header.size.toLong(), 3, hugePlan))
            assertFalse(hugeReply is ReaderCoreEventActor.HostReply.AssetConsume)
            assertError(hugeReply, "RESOURCE_EXHAUSTED", "MANGA_IMAGE_WORKING_BUDGET")
            assertTrue(root.listFiles().orEmpty().isEmpty())
            marker("bitmap-huge-graphics-plan-rejected-before-asset-consume", Bundle().apply {
                putLong("width", 4096); putLong("height", 32768); putBoolean("assetConsumeIssued", false)
                putString("code", "RESOURCE_EXHAUSTED"); putString("message", "MANGA_IMAGE_WORKING_BUDGET")
            })

            // This Long would truncate to 1 with getInt/toInt; native org.json must reach the exact integer guard.
            val overflowPlan = graphicsPlan(4_294_967_297L, 96)
            val overflowReply = host.execute(output(identity("overflow-graphics"), small.size.toLong(), 4, overflowPlan))
            assertFalse(overflowReply is ReaderCoreEventActor.HostReply.AssetConsume)
            assertError(overflowReply, "INVALID_PARAMS", "width is outside the integer range")
            assertTrue(root.listFiles().orEmpty().isEmpty())
            marker("bitmap-overflow-graphics-plan-rejected-before-asset-consume", Bundle().apply {
                putLong("width", 4_294_967_297L); putBoolean("assetConsumeIssued", false)
                putString("code", "INVALID_PARAMS"); putString("message", "width is outside the integer range")
            })
        } catch (error: Throwable) {
            failure = error
            marker("method-failure", Bundle().apply {
                putString("failureClass", error.javaClass.name); putString("message", error.message?.take(512))
            })
            throw error
        } finally {
            if (!probe.deleteRecursively()) {
                val cleanup = AssertionError("Owned bitmap-admission cache cleanup failed")
                if (failure != null) failure.addSuppressed(cleanup) else throw cleanup
            }
            marker("bitmap-owned-cache-released", Bundle().apply {
                putString("relativeCachePath", relative); putBoolean("removed", !probe.exists())
            })
        }
        marker("method-exit")
    }

    private fun smallPng(color: Int = Color.rgb(32, 128, 224)): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 96, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            return ByteArrayOutputStream().also { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }.toByteArray()
        } finally { bitmap.recycle() }
    }
    private fun platformBounds(encoded: ByteArray): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(encoded, 0, encoded.size, options)
        return options.outWidth to options.outHeight
    }
    private fun identity(label: String) = ReaderMangaTransferIdentity(
        "rmt1:" + sha(label.toByteArray(Charsets.UTF_8)), "bitmap-admission/" + label)
    private fun output(identity: ReaderMangaTransferIdentity, bytes: Long, operation: Long, graphics: JSONObject? = null) =
        ReaderCoreEventActor.HostEnvelope(runtimeSession = "bitmap-admission-private-host", requestId = operation,
            requestToken = "bitmap-admission-" + operation, operationId = operation,
            capability = ReaderMangaResourceHost.CAPABILITY,
            parametersJson = JSONObject().put("stage", "output").put("graphicsPlan", graphics ?: JSONObject.NULL)
                .put("transferId", identity.wireId).put("resourceRef", identity.resourceRef)
                .put("assetId", operation).put("operationId", operation).put("bytes", bytes).toString())
    private fun graphicsPlan(width: Number, height: Int) = JSONObject()
        .put("__readerMangaGraphicsPlan", true).put("toJSON", JSONObject.NULL)
        .put("width", width).put("height", height).put("format", "jpeg").put("quality", 90).put("pixelFormat", "rgb565")
        .put("strips", JSONArray().put(JSONObject().put("sourceY", 0).put("targetY", 0).put("height", height)))
    private fun assertError(reply: ReaderCoreEventActor.HostReply, code: String, message: String) {
        assertTrue("Expected typed Host rejection, got " + reply, reply is ReaderCoreEventActor.HostReply.Error)
        reply as ReaderCoreEventActor.HostReply.Error
        assertEquals(code, reply.code); assertEquals(message, reply.message); assertFalse(reply.retryable)
    }
    private fun assertDecodedAbsent(store: ReaderMangaResourceStore, identity: ReaderMangaTransferIdentity) {
        try {
            store.readDecodedByTransferId(identity.wireId, identity.resourceRef)
            fail("Budget-rejected input must not publish a decoded resource")
        } catch (error: IllegalStateException) { assertEquals("MANGA_DECODED_ABSENT", error.message) }
    }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun marker(step: String, extras: Bundle = Bundle()) {
        extras.putString("readerStep", step)
        extras.putString("readerMethod", "platformBoundsGateOrdinaryAndGraphicsBeforePixelAdmission")
        extras.putLong("readerElapsedMs", SystemClock.elapsedRealtime()); instrumentation.sendStatus(2, extras)
    }

    companion object {
        // Exact git object bytes, copied without transformation or a custom JPEG parser.
        private const val FIXTURE_CORE_COMMIT = "c3109d570dd62a636eff1ffb88f4e242ede0bf3a"
        private const val FIXTURE_CORE_PATH = "tests/fixtures/manga-codec-profile/jpeg-long-header-only.jpg"
        private const val LONG_JPEG_SHA256 = "bbe8c168b566c7e13d0881a59b72d87d1c6829dfbf941e80fb55caae4d56a64e"
        private const val LONG_JPEG_BASE64 =
            "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAMCAgMCAgMDAwMEAwMEBQgFBQQEBQoHBwYIDAoMDAsKCwsNDhIQDQ4RDgsLEBYQERMUFRUVDA8XGBYUGBIUFRT/2wBDAQMEBAUEBQkFBQkUDQsNFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBT/wAARCIAAEAADASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwA="
    }
}
