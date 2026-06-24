package com.reader.android.data.adapter

import org.json.JSONObject

/**
 * Keystore-backed persistence for [WebDavCredential]. Wraps
 * [AndroidKeystoreCredentialStore] to encrypt the credential envelope (auth
 * method + server URL) and persist the resulting [AndroidEncryptedCredentialRecord].
 *
 * The encrypted record stores only the cipher text, IV, and a non-reversible
 * checksum — never the raw password, token, or Basic header. The decrypted
 * [WebDavCredential] is returned only to authorized callers (the WebDAV client)
 * and never exported in evidence.
 *
 * [keystore] and [store] are injectable so unit tests can substitute an
 * in-memory keystore/persistence double without touching the Android runtime.
 */
class WebDavCredentialStore(
    private val keystore: CredentialKeystore = AndroidKeystoreCredentialStore(),
    private val store: MutableMap<String, AndroidEncryptedCredentialRecord> = mutableMapOf()
) {

    /** Abstraction over the Android keystore so tests can inject a double. */
    interface CredentialKeystore {
        fun save(identifier: String, secret: String): AndroidEncryptedCredentialRecord
        fun load(record: AndroidEncryptedCredentialRecord): String
        fun revoke(record: AndroidEncryptedCredentialRecord): Boolean
    }

    /**
     * Persist [credential] for [identifier]. The credential envelope (server URL
     * + auth method JSON) is encrypted; the plaintext is never stored.
     */
    fun save(identifier: String, credential: WebDavCredential): AndroidEncryptedCredentialRecord {
        val envelope = credential.toEnvelopeJson()
        val record = keystore.save(identifier, envelope)
        store[identifier] = record
        return record
    }

    /** Load and decrypt the credential for [identifier], or null if absent. */
    fun load(identifier: String): WebDavCredential? {
        val record = store[identifier] ?: return null
        return keystore.load(record).toCredentialEnvelope()
    }

    /** Revoke (delete) the keystore key + persisted record for [identifier]. */
    fun revoke(identifier: String): Boolean {
        val record = store.remove(identifier) ?: return true
        return keystore.revoke(record)
    }

    /** Export a count-only, value-redacted descriptor for evidence. */
    fun redactedEvidence(): String =
        "webdav_credentials count:${store.size} values:REDACTED"
}

private fun WebDavCredential.toEnvelopeJson(): String = JSONObject().apply {
    put("serverUrl", serverUrl)
    when (auth) {
        is AuthMethod.Basic -> {
            put("authType", "basic")
            put("username", (auth as AuthMethod.Basic).username)
            put("password", (auth as AuthMethod.Basic).password)
        }
        is AuthMethod.Digest -> {
            put("authType", "digest")
            put("username", (auth as AuthMethod.Digest).username)
            put("password", (auth as AuthMethod.Digest).password)
        }
        is AuthMethod.Bearer -> {
            put("authType", "bearer")
            put("token", (auth as AuthMethod.Bearer).token)
        }
    }
}.toString()

private fun String.toCredentialEnvelope(): WebDavCredential {
    val json = JSONObject(this)
    val serverUrl = json.getString("serverUrl")
    val auth: AuthMethod = when (json.optString("authType")) {
        "basic" -> AuthMethod.Basic(json.getString("username"), json.getString("password"))
        "digest" -> AuthMethod.Digest(json.getString("username"), json.getString("password"))
        "bearer" -> AuthMethod.Bearer(json.getString("token"))
        else -> throw IllegalArgumentException("unknown authType")
    }
    return WebDavCredential(serverUrl = serverUrl, auth = auth)
}
