package com.reader.host

import com.reader.android.data.adapter.AndroidEncryptedCredentialRecord
import com.reader.android.data.adapter.AuthMethod
import com.reader.android.data.adapter.WebDavCredential
import com.reader.android.data.adapter.WebDavCredentialStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 6 / audit GAP-D-01 — `credential.resolve` provider proof.
 *
 * [WebDavCredentialProvider] bridges `credential.resolve` requests to the
 * existing [WebDavCredentialStore]. This test uses an in-memory
 * [WebDavCredentialStore.CredentialKeystore] double so the JVM can exercise
 * the full handle → load → Credential mapping without Android Keystore.
 *
 * The handler layer ([CredentialResolveHandler]) is also covered here to
 * prove the end-to-end `credential.resolve` request → reply closure.
 */
class WebDavCredentialProviderJvmTest {

    @Test
    fun `resolves Basic credential from store`() {
        val store = newStoreWith(
            "webdav-default" to WebDavCredential(
                serverUrl = "https://dav.example.com",
                auth = AuthMethod.Basic(username = "alice", password = "s3cret")
            )
        )
        val provider = WebDavCredentialProvider(store)

        val cred = provider.resolve("webdav-default")
        assertNotNull(cred)
        assertEquals("alice", cred!!.username())
        assertEquals("s3cret", cred.password())
    }

    @Test
    fun `resolves Digest credential from store`() {
        val store = newStoreWith(
            "webdav-digest" to WebDavCredential(
                serverUrl = "https://dav.example.com",
                auth = AuthMethod.Digest(username = "bob", password = "d1gestPw")
            )
        )
        val provider = WebDavCredentialProvider(store)

        val cred = provider.resolve("webdav-digest")
        assertNotNull(cred)
        assertEquals("bob", cred!!.username())
        assertEquals("d1gestPw", cred.password())
    }

    @Test
    fun `resolves Bearer token by packing into username and password`() {
        val store = newStoreWith(
            "webdav-bearer" to WebDavCredential(
                serverUrl = "https://dav.example.com",
                auth = AuthMethod.Bearer(token = "tok-12345")
            )
        )
        val provider = WebDavCredentialProvider(store)

        val cred = provider.resolve("webdav-bearer")
        assertNotNull(cred)
        // Bearer token is packed into both fields (see WebDavCredentialProvider KDoc).
        assertEquals("tok-12345", cred!!.username())
        assertEquals("tok-12345", cred.password())
    }

    @Test
    fun `returns null for unknown handle`() {
        val store = WebDavCredentialStore(keystore = InMemoryKeystore())
        val provider = WebDavCredentialProvider(store)

        val cred = provider.resolve("unknown-handle")
        assertNull(cred)
    }

    @Test
    fun `handler returns complete with username and password for valid handle`() {
        val store = newStoreWith(
            "webdav-default" to WebDavCredential(
                serverUrl = "https://dav.example.com",
                auth = AuthMethod.Basic(username = "alice", password = "s3cret")
            )
        )
        val handler = CredentialResolveHandler(WebDavCredentialProvider(store))

        val reply = handler.handle(
            HostRequest(1L, 1L, "credential.resolve",
                """{"credentialHandle":"webdav-default"}""")
        )
        assertTrue("expected complete, got ${reply.kind()}", reply.isComplete())
        val json = org.json.JSONObject((reply as HostReply.Complete).resultJson())
        assertEquals("alice", json.getString("username"))
        assertEquals("s3cret", json.getString("password"))
    }

    @Test
    fun `handler returns error for unknown handle`() {
        val store = WebDavCredentialStore(keystore = InMemoryKeystore())
        val handler = CredentialResolveHandler(WebDavCredentialProvider(store))

        val reply = handler.handle(
            HostRequest(1L, 1L, "credential.resolve",
                """{"credentialHandle":"unknown"}""")
        )
        assertTrue("expected error", reply.isError())
        val error = reply as HostReply.Error
        assertEquals("INTERNAL", error.code())
        // Unknown handle is non-transient (not retryable).
        assertEquals(false, error.retryable())
    }

    @Test
    fun `handler returns error for missing credentialHandle`() {
        val store = WebDavCredentialStore(keystore = InMemoryKeystore())
        val handler = CredentialResolveHandler(WebDavCredentialProvider(store))

        val reply = handler.handle(
            HostRequest(1L, 1L, "credential.resolve", "{}")
        )
        assertTrue("expected error", reply.isError())
        val error = reply as HostReply.Error
        assertEquals("INTERNAL", error.code())
        assertEquals(false, error.retryable())
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun newStoreWith(vararg entries: Pair<String, WebDavCredential>): WebDavCredentialStore {
        val store = WebDavCredentialStore(keystore = InMemoryKeystore())
        entries.forEach { (id, cred) -> store.save(id, cred) }
        return store
    }

    private class InMemoryKeystore : WebDavCredentialStore.CredentialKeystore {
        private val secrets = mutableMapOf<String, String>()

        override fun save(identifier: String, secret: String): AndroidEncryptedCredentialRecord {
            secrets[identifier] = secret
            return AndroidEncryptedCredentialRecord(
                identifier = identifier,
                keyAlias = "alias-$identifier",
                cipherTextBase64 = "cipher-$identifier",
                ivBase64 = "iv-$identifier",
                valueChecksum = "checksum-$identifier"
            )
        }

        override fun load(record: AndroidEncryptedCredentialRecord): String =
            secrets[record.identifier] ?: throw IllegalStateException("no secret for ${record.identifier}")

        override fun revoke(record: AndroidEncryptedCredentialRecord): Boolean {
            return secrets.remove(record.identifier) != null
        }
    }
}
