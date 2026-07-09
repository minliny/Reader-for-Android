package com.reader.ui.evidence

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.reader.api.ReaderCoreClient
import kotlinx.coroutines.launch

/**
 * Debug-only Activity that runs the unified evidence harness (all 15 canonical
 * capabilities) and displays the resulting JSON artifact.
 *
 * NOT exported (`android:exported="false"`) — must be launched from within the
 * app (e.g. via `adb shell am start -n com.reader.android/.ui.evidence.EvidenceActivity`
 * from a debuggable build, or wired into a debug route).
 *
 * The Activity ensures [ReaderCoreClient] is initialized (production init) before
 * running. The runner itself only invokes Core via [ReaderCoreClient.sendAndAwait]
 * — no socket, no WebView, no plaintext credentials (Core/Host boundary).
 */
class EvidenceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ensure Core is initialized — production init wires OkHttpHostTransport.
        try {
            ReaderCoreClient.get()
        } catch (e: IllegalStateException) {
            ReaderCoreClient.init()
        }
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EvidenceScreen(
                        onRun = { runEvidence() }
                    )
                }
            }
        }
    }

    private suspend fun runEvidence(): UnifiedEvidenceArtifact =
        EvidenceRunner.run(tier = "simulator", filesDir = filesDir)
}

@Composable
private fun EvidenceScreen(onRun: suspend () -> UnifiedEvidenceArtifact) {
    var output by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Button(
            onClick = {
                if (running) return@Button
                running = true
                output = "Running unified evidence (15 capabilities)…"
                scope.launch {
                    try {
                        val artifact = onRun()
                        output = artifact.toJsonString()
                    } catch (e: Throwable) {
                        output = "Error: ${e.javaClass.simpleName}: ${e.message}\n\n" +
                            e.stackTraceToString().take(2000)
                    } finally {
                        running = false
                    }
                }
            },
            enabled = !running
        ) {
            Text(if (running) "Running…" else "Run Unified Evidence (15 capabilities)")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Unified Evidence Artifact (unified-evidence/1)",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.dp)
                .weight(1f),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = output.ifEmpty { "Tap the button to run." },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
