package com.reader.android.data.adapter

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CookieStoreTest {

    private val store = FakeCookieStore()

    @Test
    fun `save and get cookies for a source`() = runBlocking {
        val cookies = listOf(
            CookieRecord(name = "session", value = "abc123", domain = "source.com"),
            CookieRecord(name = "token", value = "xyz", domain = "source.com", secure = true)
        )
        store.save("http://source.com", cookies)

        val scope = store.get("http://source.com")
        assertEquals(2, scope.cookies.size)
        assertEquals("source.com", scope.cookies[0].domain)
    }

    @Test
    fun `getCookieString produces valid cookie header`() = runBlocking {
        store.save("http://source.com", listOf(
            CookieRecord("a", "1", "source.com"),
            CookieRecord("b", "2", "source.com")
        ))
        val cookieString = store.get("http://source.com").getCookieString()
        assertTrue(cookieString.contains("a=1"))
        assertTrue(cookieString.contains("b=2"))
    }

    @Test
    fun `clear removes all cookies for a source`() = runBlocking {
        store.save("http://a.com", listOf(CookieRecord("x", "1", "a.com")))
        store.clear("http://a.com")
        assertTrue(store.get("http://a.com").cookies.isEmpty())
    }

    @Test
    fun `clearAll removes everything`() = runBlocking {
        store.save("http://a.com", listOf(CookieRecord("x", "1", "a.com")))
        store.save("http://b.com", listOf(CookieRecord("y", "2", "b.com")))
        store.clearAll()
        assertTrue(store.get("http://a.com").cookies.isEmpty())
        assertTrue(store.get("http://b.com").cookies.isEmpty())
    }

    @Test
    fun `per-source isolation`() = runBlocking {
        store.save("http://a.com", listOf(CookieRecord("x", "1", "a.com")))
        store.save("http://b.com", listOf(CookieRecord("y", "2", "b.com")))

        assertEquals("a.com", store.get("http://a.com").cookies[0].domain)
        assertEquals("b.com", store.get("http://b.com").cookies[0].domain)
        assertEquals(1, store.get("http://a.com").cookies.size)
    }

    @Test
    fun `CookieRecord fields`() {
        val c = CookieRecord(
            name = "token", value = "secret", domain = "source.com",
            path = "/api", secure = true, httpOnly = true, expiresAt = 1700000000L
        )
        assertEquals("token", c.name)
        assertEquals("secret", c.value)
        assertEquals("/api", c.path)
        assertTrue(c.secure)
        assertTrue(c.httpOnly)
        assertEquals(1700000000L, c.expiresAt)
    }
}
