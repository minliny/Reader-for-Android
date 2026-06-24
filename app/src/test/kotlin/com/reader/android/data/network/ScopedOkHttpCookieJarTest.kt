package com.reader.android.data.network

import com.reader.android.data.adapter.CookieRecord
import com.reader.android.data.adapter.FakeCookieStore
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScopedOkHttpCookieJarTest {

    @Test
    fun `cookies saved from a response are loaded for the next request`() {
        val store = FakeCookieStore()
        val jar = ScopedOkHttpCookieJar(store)
        val url = "https://source.example/page".toHttpUrl()

        jar.saveFromResponse(url, listOf(
            okhttp3.Cookie.Builder()
                .name("session").value("opaque").domain("source.example").path("/").build()
        ))

        val loaded = jar.loadForRequest(url)
        assertEquals(1, loaded.size)
        assertEquals("session", loaded[0].name)
        assertEquals("opaque", loaded[0].value)

        // The store carries the redacted record (no raw value assertion beyond mirror)
        runBlocking {
            val scope = store.get("https://source.example/page")
            assertEquals(1, scope.cookies.size)
            assertEquals("session", scope.cookies[0].name)
        }
    }

    @Test
    fun `loadForRequest returns empty when no cookies stored`() {
        val jar = ScopedOkHttpCookieJar(FakeCookieStore())
        val loaded = jar.loadForRequest("https://source.example/x".toHttpUrl())
        assertTrue(loaded.isEmpty())
    }

    @Test
    fun `cookies mirror into the shared CookieStore used by the bridge`() {
        val store = FakeCookieStore()
        val jar = ScopedOkHttpCookieJar(store)
        val url = "https://novel.example/c1".toHttpUrl()
        jar.saveFromResponse(url, listOf(
            okhttp3.Cookie.Builder().name("auth").value("tok").domain("novel.example").build()
        ))
        runBlocking {
            val cookieHeader = store.get("https://novel.example/c1").getCookieString()
            assertTrue(cookieHeader.contains("auth=tok"))
        }
    }
}
