package com.reader.host

import com.reader.android.data.adapter.AndroidEncryptedCredentialRecord
import com.reader.android.data.adapter.AndroidTtsAdapter
import com.reader.android.data.adapter.FakeAndroidTtsAdapter
import com.reader.android.data.adapter.FakePermissionRuntimeAdapter
import com.reader.android.data.adapter.TtsInitResult
import com.reader.android.data.adapter.TtsPlaybackState
import com.reader.android.data.adapter.TtsUtterance
import com.reader.android.data.adapter.WebDavCredentialStore
import io.reader.ui.contract.HostRequestType
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ReaderUi25HostCapabilityHandlersJvmTest {
    private class MemoryCredentialKeystore : WebDavCredentialStore.CredentialKeystore {
        private val values = mutableMapOf<String, String>()
        override fun save(identifier: String, secret: String): AndroidEncryptedCredentialRecord =
            AndroidEncryptedCredentialRecord(identifier, "test", secret, "", "").also {
                values[identifier] = secret
            }

        override fun load(record: AndroidEncryptedCredentialRecord): String =
            values.getValue(record.identifier)

        override fun revoke(record: AndroidEncryptedCredentialRecord): Boolean =
            values.remove(record.identifier) != null
    }

    private class CapturingTts : AndroidTtsAdapter {
        var utterance: TtsUtterance? = null
        var stops = 0
        var pauses = 0
        override suspend fun init() = TtsInitResult(true)
        override suspend fun speak(utterance: TtsUtterance) { this.utterance = utterance }
        override suspend fun stop() { stops++ }
        override suspend fun pause() { pauses++ }
        override suspend fun resume() = Unit
        override fun isAvailable() = true
        override fun getState() = TtsPlaybackState.IDLE
    }

    private class FakeActivityHost : ReaderUiActivityCapabilityHost {
        var currentBrightness = 0.5f
        var keepAwake = false
        var sharedText: String? = null
        var sharedFile: String? = null
        var lastScope: ReaderUiHostRequestScope? = null

        override fun selectFiles(
            scope: ReaderUiHostRequestScope,
            mimeTypes: List<String>,
            allowsMultiple: Boolean
        ): ReaderFileSelectionResult {
            lastScope = scope
            return ReaderFileSelectionResult(listOf(
                ReaderSelectedFile("content://reader/book.txt", "book.txt", "text/plain", 42L)
            ))
        }

        override fun setBrightness(value: Float): Float {
            currentBrightness = value
            return currentBrightness
        }
        override fun getBrightness(): Float = currentBrightness
        override fun setKeepAwake(keepAwake: Boolean): Boolean {
            this.keepAwake = keepAwake
            return true
        }
        override fun shareText(text: String): Boolean { sharedText = text; return true }
        override fun shareFile(path: String): Boolean { sharedFile = path; return true }
        override fun close() = Unit
    }

    private class FakeWebDav25 : ReaderWebDav25Host {
        var backupScope: ReaderUiHostRequestScope? = null
        override fun connect(
            scope: ReaderUiHostRequestScope,
            override: ReaderWebDavConnectionOverride?,
            legacyURL: String?
        ) = ReaderWebDavConnectResult(true, 207, "OK")

        override fun backup(
            scope: ReaderUiHostRequestScope,
            override: ReaderWebDavConnectionOverride?
        ): ReaderWebDavBackupResult {
            backupScope = scope
            return ReaderWebDavBackupResult(true, "https://dav.test/backup.json", 201, 1)
        }

        override fun restore(
            scope: ReaderUiHostRequestScope,
            remoteURL: String,
            override: ReaderWebDavConnectionOverride?
        ) = ReaderWebDavRestoreResult(true, remoteURL, 200, 3)
    }

    private fun facade(
        tts: AndroidTtsAdapter = FakeAndroidTtsAdapter(),
        activity: ReaderUiActivityCapabilityHost? = FakeActivityHost(),
        webDav: ReaderWebDav25Host = FakeWebDav25()
    ): HostFacade = HostFacade(
        context = null,
        tts = tts,
        permission = FakePermissionRuntimeAdapter(),
        notification = null,
        webDav = null,
        credentials = WebDavCredentialStore(MemoryCredentialKeystore()),
        downloadCache = null,
        readerUi25Services = ReaderUi25HostServices(
            activityHostProvider = { activity },
            fontRegistration = ReaderFontRegistrationHost { path ->
                ReaderRegisteredFont(
                    "/registered/${path.substringAfterLast('/')}",
                    "Host Reader Serif",
                    listOf("ReaderSerif-Regular")
                )
            },
            networkStatus = ReaderNetworkStatusHost {
                ReaderNetworkSnapshot(true, "online", "wifi", false, false)
            },
            haptics = ReaderHapticsHost { true },
            backgroundTasks = object : ReaderBackgroundTaskHost {
                override fun start(name: String) = ReaderBackgroundTaskStartResult(
                    "123e4567-e89b-12d3-a456-426614174000"
                )
                override fun end(taskId: String) = true
            },
            webDav = webDav
        )
    )

    private fun adapter(facade: HostFacade = facade()): HostAdapter {
        val runtime = HostRuntime.over(object : HostTransport {
            override fun pollEventJson(timeoutMillis: Long): String? = null
            override fun sendCommand(commandJson: String) = Unit
        })
        // These eight old contract types are registered by ReaderCoreClient's
        // base builder rather than HostFacade. Existing registry tests prove
        // their concrete handlers; this test composes them with the facade.
        listOf(
            "http.execute", "http.cancel", "cookie.get", "cookie.set", "cookie.clear",
            "file.read", "file.write", "file.delete"
        ).forEach { capability ->
            runtime.register(capability, CapabilityHandler { HostReply.complete("{}") })
        }
        val persistence = HostCachePersistenceAdapter(DefaultHostCache())
        runtime.register(PersistenceGetHandler.CAPABILITY, PersistenceGetHandler(persistence))
        runtime.register(PersistencePutHandler.CAPABILITY, PersistencePutHandler(persistence))
        facade.registerHandlers(runtime)
        return runtime.adapter()
    }

    @Test
    fun `generated contract has exactly 58 unique tiered wire names`() {
        val manifest = ReaderUi25HostCapabilityManifest
        assertEquals(58, HostRequestType.entries.size)
        assertEquals(58, manifest.entries.size)
        assertEquals(58, manifest.capabilities.size)
        assertTrue(manifest.entries.all { it.capability.isNotBlank() })
        assertEquals(10, manifest.entriesAt(AndroidHostCapabilityProofTier.JVM).size)
        assertEquals(23, manifest.entriesAt(AndroidHostCapabilityProofTier.ROBOLECTRIC_API35).size)
        assertEquals(25, manifest.entriesAt(AndroidHostCapabilityProofTier.PHYSICAL_DEVICE).size)
        assertEquals(
            AndroidHostCapabilityProofTier.PHYSICAL_DEVICE,
            manifest.entries.single { it.capability == "file.select" }.tier
        )
        assertEquals(
            AndroidHostCapabilityProofTier.ROBOLECTRIC_API35,
            manifest.entries.single { it.capability == "network.status" }.tier
        )
    }

    @Test
    fun `HostFacade plus base runtime registers every contract type exactly once by wire name`() {
        val adapter = adapter()
        assertTrue(ReaderUi25HostCapabilityManifest.missingFrom(adapter).isEmpty())
        val contractRegistrations = adapter.registeredCapabilities()
            .filter { it in ReaderUi25HostCapabilityManifest.capabilities }
        assertEquals(58, contractRegistrations.size)
        assertEquals(ReaderUi25HostCapabilityManifest.capabilities, contractRegistrations.toSet())
    }

    @Test
    fun `long Activity request does not hold HostAdapter registration lock`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val adapter = HostAdapter().apply {
            register("file.select", CapabilityHandler {
                entered.countDown()
                release.await(2, TimeUnit.SECONDS)
                HostReply.complete("{\"selected\":false,\"files\":[]}")
            })
            register("network.status", CapabilityHandler {
                HostReply.complete("{\"connected\":false,\"status\":\"offline\",\"interface\":\"none\",\"isExpensive\":false,\"isConstrained\":false}")
            })
        }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val selection = executor.submit(Callable {
                adapter.dispatch(HostRequest(1, 1, "file.select", "{}"))
            })
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            val network = executor.submit(Callable {
                adapter.dispatch(HostRequest(1, 2, "network.status", "{}"))
            })
            assertTrue(network.get(1, TimeUnit.SECONDS)?.isComplete() == true)
            release.countDown()
            assertTrue(selection.get(1, TimeUnit.SECONDS)?.isComplete() == true)
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `file select keeps operation scope internal and never leaks it`() {
        val activity = FakeActivityHost()
        val reply = FileSelect25Handler(facade(activity = activity)).handle(request(
            "file.select",
            JSONObject()
                .put("mimeTypes", JSONArray().put("text/plain"))
                .put("allowsMultiple", false)
        ))
        assertTrue(reply.isComplete())
        val result = result(reply)
        assertTrue(result.getBoolean("selected"))
        assertEquals(1, result.getJSONArray("files").length())
        assertFalse(result.has("requestId"))
        assertFalse(result.has("operationId"))
        assertFalse(result.has("generation"))
        assertEquals("1:77", activity.lastScope?.key)
    }

    @Test
    fun `file select fails closed without Activity and rejects loose DTO types`() {
        val noActivity = FileSelect25Handler(facade(activity = null)).handle(
            request("file.select", JSONObject())
        )
        assertError(noActivity, "REQUIRES_UI_CONTEXT")

        val looseBoolean = FileSelect25Handler(facade()).handle(request(
            "file.select",
            JSONObject().put("allowsMultiple", "true")
        ))
        assertError(looseBoolean, "INVALID_PARAMS")
    }

    @Test
    fun `font network haptics brightness screen and share dispatch real injected executors`() {
        val activity = FakeActivityHost()
        val facade = facade(activity = activity)

        val font = result(FontRegisterFile25Handler(facade).handle(request(
            "font.registerFile",
            JSONObject().put("path", "/tmp/reader.otf").put("familyName", "Reader Serif")
        )))
        assertEquals("Host Reader Serif", font.getString("familyName"))
        assertEquals("ReaderSerif-Regular", font.getJSONArray("fontNames").getString(0))
        val unregister = result(FontUnregisterFile25Handler(facade).handle(request(
            "font.unregisterFile",
            JSONObject().put("path", font.getString("path")).put("familyName", font.getString("familyName"))
        )))
        assertTrue(unregister.getBoolean("logicalUnregistered"))
        assertFalse(unregister.getBoolean("physicallyUnregistered"))
        assertTrue(unregister.getBoolean("restartRequired"))

        val network = result(NetworkStatus25Handler(facade).handle(request("network.status")))
        assertTrue(network.getBoolean("connected"))
        assertEquals("wifi", network.getString("interface"))

        assertTrue(Haptics25Handler(facade, ReaderHapticStyle.HEAVY)
            .handle(request("haptics.heavy")).isComplete())

        val brightness = result(BrightnessSet25Handler(facade).handle(request(
            "brightness.set", JSONObject().put("value", 0.25)
        )))
        assertEquals(0.25, brightness.getDouble("brightness"), 0.001)
        assertTrue(ScreenKeepAwake25Handler(facade).handle(request("screen.keepAwake")).isComplete())
        assertTrue(activity.keepAwake)
        assertTrue(ScreenAllowSleep25Handler(facade).handle(request("screen.allowSleep")).isComplete())
        assertFalse(activity.keepAwake)

        assertTrue(ShareText25Handler(facade).handle(request(
            "share.text", JSONObject().put("text", "Reader")
        )).isComplete())
        assertEquals("Reader", activity.sharedText)
        assertTrue(ShareFile25Handler(facade).handle(request(
            "share.file", JSONObject().put("path", "content://reader/book.epub")
        )).isComplete())
        assertEquals("content://reader/book.epub", activity.sharedFile)
    }

    @Test
    fun `tts aliases preserve strict voice options and stop pause delegate`() {
        val tts = CapturingTts()
        val facade = facade(tts = tts)
        val start = TtsStart25Handler(facade).handle(request(
            "tts.start",
            JSONObject()
                .put("text", "hello")
                .put("rate", 1.25)
                .put("pitch", 0.8)
                .put("language", "en-US")
        ))
        assertTrue(start.isComplete())
        assertEquals("hello", tts.utterance?.text)
        assertEquals("en-US", tts.utterance?.language)
        assertEquals(1.25f, tts.utterance?.speechRate ?: 0f, 0.001f)
        assertEquals(0.8f, tts.utterance?.pitch ?: 0f, 0.001f)

        assertTrue(TtsStop25Handler(facade).handle(request("tts.stop")).isComplete())
        assertTrue(TtsPause25Handler(facade).handle(request("tts.pause")).isComplete())
        assertEquals(1, tts.stops)
        assertEquals(1, tts.pauses)

        assertError(TtsStart25Handler(facade).handle(request(
            "tts.start", JSONObject().put("text", "hello").put("rate", "fast")
        )), "INVALID_PARAMS")
    }

    @Test
    fun `webdav requires all-or-none credentials and restore remoteURL`() {
        val webDav = FakeWebDav25()
        val facade = facade(webDav = webDav)
        assertError(WebDavConnect25Handler(facade).handle(request(
            "webdav.connect",
            JSONObject().put("serverURL", "https://dav.test")
        )), "INVALID_PARAMS")

        val backup = WebDavBackup25Handler(facade).handle(request(
            "webdav.backup",
            JSONObject()
                .put("serverURL", "https://dav.test")
                .put("username", "reader")
                .put("password", "secret")
                .put("generation", 12)
        ))
        assertTrue(backup.isComplete())
        assertEquals(12L, webDav.backupScope?.generation)

        assertError(WebDavRestore25Handler(facade).handle(request(
            "webdav.restore", JSONObject()
        )), "INVALID_PARAMS")
        assertTrue(WebDavRestore25Handler(facade).handle(request(
            "webdav.restore", JSONObject().put("remoteURL", "https://dav.test/backup.json")
        )).isComplete())
    }

    @Test
    fun `background task uses returned WorkManager identity and strict taskId DTO`() {
        val facade = facade()
        val start = result(BackgroundTaskStart25Handler(facade).handle(request(
            "background.task.start", JSONObject().put("name", "source-refresh")
        )))
        val taskId = start.getString("taskId")
        assertNotNull(UUID.fromString(taskId))

        val end = result(BackgroundTaskEnd25Handler(facade).handle(request(
            "background.task.end", JSONObject().put("taskId", taskId)
        )))
        assertTrue(end.getBoolean("ended"))
        assertEquals(taskId, end.getString("taskId"))
    }

    @Test
    fun `appearance persistence is opaque atomic CAS and rejects stale revision`() {
        val adapter = adapter()
        val namespace = "reader-ui-${UUID.randomUUID()}"
        val key = "appearance.v1"
        val miss = result(adapter.dispatch(request(
            "persistence.get", JSONObject().put("namespace", namespace).put("key", key)
        )))
        assertFalse(miss.getBoolean("found"))

        val value = """{"schemaVersion":1,"revision":1}"""
        val stored = result(adapter.dispatch(request(
            "persistence.put",
            JSONObject()
                .put("namespace", namespace)
                .put("key", key)
                .put("value", value)
                .put("expectedRevision", "0")
        )))
        assertTrue(stored.getBoolean("stored"))
        assertEquals("1", stored.getString("revision"))

        val hit = result(adapter.dispatch(request(
            "persistence.get", JSONObject().put("namespace", namespace).put("key", key)
        )))
        assertEquals(value, hit.getString("value"))
        assertEquals("1", hit.getString("revision"))

        assertError(adapter.dispatch(request(
            "persistence.put",
            JSONObject()
                .put("namespace", namespace)
                .put("key", key)
                .put("value", "stale")
                .put("expectedRevision", "0")
        )), "CONFLICT")
    }

    @Test
    fun `appearance persistence CAS admits exactly one concurrent writer`() {
        val store = HostCachePersistenceAdapter(DefaultHostCache())
        store.put("reader-ui", "appearance.concurrent", "initial", null, "0")
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val attempts = listOf("first", "second").map { value ->
                executor.submit(Callable {
                    ready.countDown()
                    start.await(2, TimeUnit.SECONDS)
                    runCatching {
                        store.put("reader-ui", "appearance.concurrent", value, null, "1")
                    }.isSuccess
                })
            }
            assertTrue(ready.await(2, TimeUnit.SECONDS))
            start.countDown()
            assertEquals(1, attempts.count { it.get(2, TimeUnit.SECONDS) })
            assertEquals("2", store.get("reader-ui", "appearance.concurrent")?.revision)
        } finally {
            start.countDown()
            executor.shutdownNow()
        }
    }

    private fun request(
        capability: String,
        payload: JSONObject = JSONObject()
    ) = HostRequest(1L, 77L, capability, payload.toString())

    private fun result(reply: HostReply): JSONObject {
        assertTrue("expected complete, got ${reply.kind()}", reply.isComplete())
        return JSONObject((reply as HostReply.Complete).resultJson())
    }

    private fun assertError(reply: HostReply, code: String) {
        assertTrue("expected error, got ${reply.kind()}", reply.isError())
        assertEquals(code, (reply as HostReply.Error).code())
    }
}
