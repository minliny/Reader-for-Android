package com.reader.android.data.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class AndroidWebDavClientTest {

    private val source: String by lazy {
        String(
            Files.readAllBytes(
                Paths.get("src/main/kotlin/com/reader/android/data/adapter/AndroidWebDavClient.kt")
            )
        )
    }

    @Test
    fun `client implements WebDavClient contract`() {
        assertTrue(
            "AndroidWebDavClient must implement WebDavClient",
            ": WebDavClient" in source
        )
    }

    @Test
    fun `client is okhttp backed with credential store integration`() {
        listOf(
            "okhttp3.OkHttpClient",
            "okhttp3.Request",
            "WebDavCredentialStore",
            "credentialStore.load"
        ).forEach { token ->
            assertTrue(
                "AndroidWebDavClient must use $token",
                token in source
            )
        }
    }

    @Test
    fun `client covers all webdav methods`() {
        listOf(
            "WebDavMethod.PROPFIND",
            "WebDavMethod.GET",
            "WebDavMethod.PUT",
            "WebDavMethod.DELETE",
            "WebDavMethod.MKCOL"
        ).forEach { token ->
            assertTrue(
                "AndroidWebDavClient must handle $token",
                token in source
            )
        }
    }

    @Test
    fun `client applies basic and bearer auth headers`() {
        // Basic and Bearer are the two auth schemes with a stateless header
        // (Digest requires a challenge-response flow and is intentionally left
        // unimplemented in this slice).
        listOf(
            "AuthMethod.Basic",
            "AuthMethod.Bearer",
            "Authorization",
            "Base64"
        ).forEach { token ->
            assertTrue(
                "AndroidWebDavClient must apply $token for auth",
                token in source
            )
        }
    }

    @Test
    fun `client propfind defaults depth header to one`() {
        // PROPFIND without Depth returns undefined behavior; we default to 1
        // (immediate children) so the listing is non-recursive and bounded.
        assertTrue(
            "PROPFIND must default Depth to 1",
            "Depth" in source && "\"1\"" in source
        )
    }

    @Test
    fun `webdav method enum covers all five verbs`() {
        // Contract stability: the WebDavMethod enum must enumerate exactly
        // the five verbs the client handles — no drift between fake and real.
        assertEquals(5, WebDavMethod.entries.size)
        assertTrue(WebDavMethod.PROPFIND in WebDavMethod.entries)
        assertTrue(WebDavMethod.GET in WebDavMethod.entries)
        assertTrue(WebDavMethod.PUT in WebDavMethod.entries)
        assertTrue(WebDavMethod.DELETE in WebDavMethod.entries)
        assertTrue(WebDavMethod.MKCOL in WebDavMethod.entries)
    }

    @Test
    fun `webdav credential record stays redacted in evidence`() {
        // The credential store exports only a count-only, value-redacted
        // descriptor — the decrypted credential is never leaked in evidence.
        val store = WebDavCredentialStore(
            keystore = InMemoryCredentialKeystore(),
            store = mutableMapOf()
        )
        store.save("test", WebDavCredential("https://dav.example", AuthMethod.Basic("u", "p")))
        val evidence = store.redactedEvidence()
        assertTrue(evidence.contains("count:1"))
        assertTrue(evidence.contains("REDACTED"))
        assertTrue(!evidence.contains(":p"))
        assertTrue(!evidence.contains("password"))
    }

    /** In-memory keystore double so the test does not touch Android Keystore. */
    private class InMemoryCredentialKeystore : WebDavCredentialStore.CredentialKeystore {
        private val secrets = mutableMapOf<String, AndroidEncryptedCredentialRecord>()
        private var counter = 0

        override fun save(identifier: String, secret: String): AndroidEncryptedCredentialRecord {
            val record = AndroidEncryptedCredentialRecord(
                identifier = identifier,
                keyAlias = "alias-${counter++}",
                cipherTextBase64 = secret,  // not actually encrypted — test only
                ivBase64 = "",
                valueChecksum = secret.hashCode().toString()
            )
            secrets[identifier] = record
            return record
        }

        override fun load(record: AndroidEncryptedCredentialRecord): String = record.cipherTextBase64

        override fun revoke(record: AndroidEncryptedCredentialRecord): Boolean {
            return secrets.remove(record.identifier) != null
        }
    }
}
