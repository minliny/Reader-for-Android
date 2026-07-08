package com.reader.host

import com.reader.android.data.adapter.AuthMethod
import com.reader.android.data.adapter.WebDavCredentialStore

/**
 * Production [CredentialProvider] that bridges `credential.resolve` requests
 * to the existing [WebDavCredentialStore] (Android Keystore-backed).
 *
 * The `credentialHandle` from a Core `credential.resolve` request is treated
 * as a [WebDavCredentialStore] identifier — callers that previously stored a
 * credential via `credential.set` (e.g. `webdav-default`) can resolve it back
 * to plaintext `{username, password}` here.
 *
 * Bearer tokens are returned with `username = "<token>"` and
 * `password = "<token>"` so the existing two-field [Credential] contract is
 * honored; downstream consumers that need the raw token should detect this
 * shape. (A future protocol revision may add a dedicated token field.)
 *
 * Returns `null` for unknown handles — [CredentialResolveHandler] turns that
 * into a non-transient `host.error("credential not found")`.
 */
class WebDavCredentialProvider(
    private val store: WebDavCredentialStore
) : CredentialProvider {

    override fun resolve(credentialHandle: String): Credential? {
        val cred = store.load(credentialHandle) ?: return null
        return when (cred.auth) {
            is AuthMethod.Basic -> Credential(
                (cred.auth as AuthMethod.Basic).username,
                (cred.auth as AuthMethod.Basic).password
            )
            is AuthMethod.Digest -> Credential(
                (cred.auth as AuthMethod.Digest).username,
                (cred.auth as AuthMethod.Digest).password
            )
            is AuthMethod.Bearer -> {
                // Credential contract only has username + password; pack the
                // token into both fields so callers can recover it.
                val token = (cred.auth as AuthMethod.Bearer).token
                Credential(token, token)
            }
        }
    }
}
