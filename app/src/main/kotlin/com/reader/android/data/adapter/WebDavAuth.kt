package com.reader.android.data.adapter

sealed class AuthMethod {
    data class Basic(val username: String, val password: String) : AuthMethod()
    data class Digest(val username: String, val password: String) : AuthMethod()
    data class Bearer(val token: String) : AuthMethod()
}

data class WebDavCredential(
    val serverUrl: String,
    val auth: AuthMethod
)
