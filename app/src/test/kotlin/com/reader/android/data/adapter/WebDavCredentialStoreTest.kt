package com.reader.android.data.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavCredentialStoreTest {

    /** In-memory keystore double — no Android runtime required. */
    private class InMemoryKeystore : WebDavCredentialStore.CredentialKeystore {
        private val keys = mutableMapOf<String, Pair<String, ByteArray>>() // alias -> (plaintext, iv)
        private var counter = 0

        override fun save(identifier: String, secret: String): AndroidEncryptedCredentialRecord {
            val alias = "alias_${identifier}"
            // "encrypt" by base64 (test only — real impl uses AndroidKeyStore AES-GCM)
            val cipherText = secret.toByteArray(Charsets.UTF_8)
            keys[alias] = secret to cipherText
            return AndroidEncryptedCredentialRecord(
                identifier = identifier,
                keyAlias = alias,
                cipherTextBase64 = java.util.Base64.getEncoder().encodeToString(cipherText),
                ivBase64 = "dGVzdA==",
                valueChecksum = secret.hashCode().toString()
            )
        }

        override fun load(record: AndroidEncryptedCredentialRecord): String {
            val (secret, _) = keys[record.keyAlias] ?: error("key missing")
            return secret
        }

        override fun revoke(record: AndroidEncryptedCredentialRecord): Boolean {
            keys.remove(record.keyAlias)
            return !keys.containsKey(record.keyAlias)
        }
    }

    @Test
    fun `basic credential round-trips save load and revoke`() {
        val store = WebDavCredentialStore(keystore = InMemoryKeystore())
        val cred = WebDavCredential(
            serverUrl = "https://dav.example/remote.php/webdav",
            auth = AuthMethod.Basic("reader", "s3cret-pw")
        )

        val record = store.save("primary", cred)
        // The encrypted record must not carry the raw password
        assertTrue(!record.cipherTextBase64.contains("s3cret-pw"))

        val loaded = store.load("primary")
        assertEquals(cred.serverUrl, loaded?.serverUrl)
        assertEquals("reader", (loaded?.auth as AuthMethod.Basic).username)
        assertEquals("s3cret-pw", (loaded.auth as AuthMethod.Basic).password)

        assertTrue(store.revoke("primary"))
        assertNull(store.load("primary"))
    }

    @Test
    fun `bearer token credential round-trips`() {
        val store = WebDavCredentialStore(keystore = InMemoryKeystore())
        store.save("token", WebDavCredential("https://dav.example", AuthMethod.Bearer("tok-123")))
        val loaded = store.load("token")
        assertEquals("tok-123", (loaded?.auth as AuthMethod.Bearer).token)
    }

    @Test
    fun `redacted evidence exports count only, never the secret`() {
        val store = WebDavCredentialStore(keystore = InMemoryKeystore())
        store.save("primary", WebDavCredential("https://dav.example", AuthMethod.Basic("u", "supersecret")))
        val evidence = store.redactedEvidence()
        assertEquals("webdav_credentials count:1 values:REDACTED", evidence)
        assertTrue(!evidence.contains("supersecret"))
    }
}
