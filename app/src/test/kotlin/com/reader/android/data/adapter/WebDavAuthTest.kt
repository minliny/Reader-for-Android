package com.reader.android.data.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavAuthTest {

    @Test
    fun `Basic auth holds username and password`() {
        val auth = AuthMethod.Basic("user", "pass")
        assertTrue(auth is AuthMethod.Basic)
        assertEquals("user", auth.username)
    }

    @Test
    fun `Bearer auth holds token`() {
        val auth = AuthMethod.Bearer("token-abc")
        assertTrue(auth is AuthMethod.Bearer)
        assertEquals("token-abc", auth.token)
    }

    @Test
    fun `WebDavCredential pairs server with auth`() {
        val cred = WebDavCredential("https://dav.example.com", AuthMethod.Basic("u", "p"))
        assertEquals("https://dav.example.com", cred.serverUrl)
        assertTrue(cred.auth is AuthMethod.Basic)
    }
}
