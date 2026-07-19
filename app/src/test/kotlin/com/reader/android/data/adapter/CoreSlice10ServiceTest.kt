package com.reader.android.data.adapter

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreSlice10ServiceTest {

    @Test
    fun `bookmark list is read from Core and never a local repository`() = runBlocking {
        var methodSeen = ""
        val service = CoreSlice10Service(Slice10CoreCommandClient { method, _ ->
            methodSeen = method
            JSONObject().put(
                "bookmarks",
                JSONArray().put(
                    JSONObject()
                        .put("time", 12L)
                        .put("bookName", "Book")
                        .put("bookAuthor", "Author")
                        .put("chapterIndex", 2)
                        .put("chapterPos", 8)
                        .put("chapterName", "Chapter")
                        .put("bookText", "excerpt")
                        .put("content", "note")
                )
            )
        })

        val outcome = service.listBookmarks("Book", "Author")

        assertEquals("bookmark.list", methodSeen)
        val value = (outcome as Slice10Outcome.Success).value.single()
        assertEquals(2, value.chapterIndex)
        assertEquals(8, value.chapterPosition)
    }

    @Test
    fun `bookmark filter requires canonical book identity pair`() = runBlocking {
        var calls = 0
        val service = CoreSlice10Service(Slice10CoreCommandClient { _, _ -> calls++; JSONObject() })

        val outcome = service.listBookmarks(bookName = "Book", bookAuthor = null)

        assertEquals(Slice10FailureCode.INVALID_INPUT, (outcome as Slice10Outcome.Failed).failure.code)
        assertEquals(0, calls)
    }

    @Test
    fun `content edit write uses Core content-edit put`() = runBlocking {
        var captured: JSONObject? = null
        val service = CoreSlice10Service(Slice10CoreCommandClient { method, params ->
            assertEquals("content-edit.put", method)
            captured = JSONObject(params.toString())
            JSONObject().put(
                "edit",
                JSONObject(params.toString())
                    .put("editId", 4L)
            )
        })

        val outcome = service.putContentEdit("book-1", 3, "edited", 10L)

        assertTrue(outcome is Slice10Outcome.Success)
        assertEquals("book-1", captured!!.getString("bookId"))
        assertEquals(3, captured!!.getInt("chapterIndex"))
    }

    @Test
    fun `replace and txt toc lists use their Core stores`() = runBlocking {
        val methods = mutableListOf<String>()
        val service = CoreSlice10Service(Slice10CoreCommandClient { method, _ ->
            methods += method
            val rule = JSONObject()
                .put("id", 1L)
                .put("name", "Rule")
                .put("isEnabled", true)
                .put("enable", true)
                .put("order", 2)
                .put("serialNumber", 3)
            JSONObject().put("rules", JSONArray().put(rule))
        })

        assertTrue(service.listReplaceRules() is Slice10Outcome.Success)
        assertTrue(service.listTxtTocRules() is Slice10Outcome.Success)
        assertEquals(listOf("replace-rule.list", "txt-toc-rule.list"), methods)
    }

    @Test
    fun `HttpTTS list never projects url header login or script`() = runBlocking {
        val service = CoreSlice10Service(Slice10CoreCommandClient { _, _ ->
            JSONObject().put(
                "items",
                JSONArray().put(
                    JSONObject()
                        .put("id", 7L)
                        .put("name", "Engine")
                        .put("url", "https://secret.example/?token=secret")
                        .put("header", "{\"Authorization\":\"secret\"}")
                        .put("loginUi", "secret-ui")
                        .put("jsLib", "secret-script")
                        .put("contentType", "audio/mpeg")
                        .put("enabledCookieJar", true)
                )
            )
        })

        val summary = (service.listHttpTts() as Slice10Outcome.Success).value.single()
        val rendered = summary.toString()

        assertEquals("Engine", summary.name)
        assertFalse(rendered.contains("secret"))
        assertFalse(rendered.contains("Authorization"))
    }

    @Test
    fun `HttpTTS request keeps credentials private and serializes only at Host edge`() = runBlocking {
        val service = CoreSlice10Service(Slice10CoreCommandClient { method, _ ->
            assertEquals("http-tts.build-request", method)
            JSONObject()
                .put("method", "GET")
                .put("url", "https://tts.example/audio?token=secret")
                .put("headers", JSONObject().put("Authorization", "Bearer secret"))
                .put("contentType", "audio/mpeg")
        })

        val descriptor = (service.buildHttpTtsRequest(7, "hello") as Slice10Outcome.Success).value

        assertFalse(descriptor.toString().contains("Bearer secret"))
        val host = descriptor.toHostExecuteParams()
        assertEquals("Bearer secret", host.getJSONObject("headers").getString("Authorization"))
    }

    @Test
    fun `unsafe HttpTTS scheme fails protocol validation`() = runBlocking {
        val service = CoreSlice10Service(Slice10CoreCommandClient { _, _ ->
            JSONObject()
                .put("method", "GET")
                .put("url", "file:///etc/passwd")
                .put("headers", JSONObject())
        })

        val outcome = service.buildHttpTtsRequest(7, "hello") as Slice10Outcome.Failed

        assertEquals(Slice10FailureCode.CORE_PROTOCOL_MISMATCH, outcome.failure.code)
    }

    @Test
    fun `capability matrix keeps missing contracts blocked`() {
        assertEquals(
            AndroidSlice10CapabilityStatus.BLOCKED_MISSING_TRANSACTION_PROTOCOL,
            AndroidSlice10CapabilityMatrix.status("cover.change")
        )
        assertEquals(
            AndroidSlice10CapabilityStatus.BLOCKED_MISSING_CORE_PROTOCOL,
            AndroidSlice10CapabilityMatrix.status("chapter.reviews")
        )
        assertEquals(
            AndroidSlice10CapabilityStatus.BLOCKED_MISSING_HOST_PROTOCOL,
            AndroidSlice10CapabilityMatrix.status("media.session")
        )
    }

    @Test
    fun `Core unavailable is retryable and cannot mutate a second store`() = runBlocking {
        val service = CoreSlice10Service(Slice10CoreCommandClient { _, _ ->
            throw IllegalStateException("not initialized")
        })

        val outcome = service.listContentEdits("book") as Slice10Outcome.Failed

        assertEquals(Slice10FailureCode.CORE_UNAVAILABLE, outcome.failure.code)
        assertTrue(outcome.failure.retryable)
    }
}
