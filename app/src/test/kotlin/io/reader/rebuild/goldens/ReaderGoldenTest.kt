package io.reader.rebuild.goldens

import com.reader.core.protocol.ReaderProtocolJson
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w402dp-h700dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ReaderGoldenTest {
    @Test fun productScenesMatchReviewedPixels() {
        val scenes = messageGoldenScenes() + localGoldenScenes() + onlineGoldenScenes() + sourceGoldenScenes()
        check(scenes.map { it.id }.distinct().size == scenes.size)
        val baseline = File("src/test/resources/goldens")
        val proposal = System.getenv("READER_GOLDEN_CANDIDATES")?.let(::File)
        require(proposal == null || (proposal.isAbsolute && proposal.canonicalFile != baseline.canonicalFile))
        val recorded = if (proposal == null) {
            ReaderProtocolJson.decodeFromString<Manifest>(File(baseline, "manifest.json").readText()).also {
                check(it.pins == pins && it.runtime == runtime && it.rendering == rendering)
                check(it.scenes.map { entry -> entry.id } == scenes.map { scene -> scene.id })
                check(baseline.listFiles().orEmpty().filter { file -> file.extension == "png" }.map(File::nameWithoutExtension)
                    .toSet() == scenes.map(AndroidGoldenScene::id).toSet())
            }
        } else null
        val output = scenes.map { scene ->
            val image = AndroidGoldenRenderer.capture(scene)
            try {
                val file = File(proposal ?: baseline, "${scene.id}.png")
                if (proposal != null) {
                    AndroidGoldenRenderer.save(image, file)
                } else {
                    AndroidGoldenRenderer.compare(scene.id, image, file)
                }
                val entry = Entry(scene.id, scene.state, scene.sample, scene.width, scene.height, scene.fontScale,
                    if (scene.night) "night" else "day", scene.difference,
                    AndroidGoldenRenderer.pixelSHA256(image), AndroidGoldenRenderer.sha256(file.readBytes()))
                if (recorded != null) check(entry == recorded.scenes.single { it.id == scene.id }) {
                    "${scene.id}: manifest sample, environment or SHA-256 drift"
                }
                println("READER_GOLDEN ${entry.id} ${entry.width}x${entry.height} fontScale=${entry.fontScale} theme=${entry.theme} pixel=${entry.pixelSHA256}")
                entry
            } finally {
                image.recycle()
            }
        }
        if (proposal != null) {
            File(proposal, "manifest.json").writeText(manifestJson.encodeToString(Manifest(pins, runtime, rendering, output)) + "\n")
        }
        println("READER_GOLDEN_TOTAL=${output.size}")
    }

    @Serializable private data class Pins(val core: String, val ui: String, val harmony: String)
    @Serializable private data class Entry(
        val id: String,
        val referenceState: String,
        val sample: String,
        val width: Int,
        val height: Int,
        val fontScale: Float,
        val theme: String,
        val platformDifference: String,
        val pixelSHA256: String,
        val pngSHA256: String,
    )
    @Serializable private data class Manifest(val pins: Pins, val runtime: String, val rendering: String, val scenes: List<Entry>)

    companion object {
        private val manifestJson = Json { prettyPrint = true }
        private val pins = Pins("14fdd8f91549a30a7b7235088d3c524d128201d1", "2f09c1855b043608f013da8df666714f387b60c3",
            "0f3b468dcf95c3869b7f28f47cf5019a7c26b8ef")
        private const val runtime = "Android API 35 / Robolectric 4.16.1 (MIT) / native Skia / zh-CN / mdpi"
        private const val rendering = "Production Compose/View content, Android Canvas to 1x ARGB_8888, exact pixels and SHA-256. Approved Android capture differs from iOS by system chrome, layout engines and symbols."
    }
}
