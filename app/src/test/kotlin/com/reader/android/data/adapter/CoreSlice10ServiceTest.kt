package com.reader.android.data.adapter

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
            val rule = if (method == "replace-rule.list") {
                persistedRule(id = 1L, replacement = "full replacement")
            } else {
                JSONObject()
                    .put("id", 1L)
                    .put("name", "Rule")
                    .put("enable", true)
                    .put("serialNumber", 3)
            }
            JSONObject().put("rules", JSONArray().put(rule))
        })

        val replace = (service.listReplaceRules() as Slice10Outcome.Success).value.single()
        assertTrue(service.listTxtTocRules() is Slice10Outcome.Success)
        assertEquals(listOf("replace-rule.list", "txt-toc-rule.list"), methods)
        assertEquals("rain", replace.pattern)
        assertEquals("full replacement", replace.replacement)
        assertFalse(replace.scopeTitle)
        assertTrue(replace.scopeContent)
        assertFalse(replace.isRegex)
    }

    @Test
    fun `cache book status sends exact selector and requires the complete Core result`() = runBlocking {
        var captured: JSONObject? = null
        val service = CoreSlice10Service(Slice10CoreCommandClient { method, params ->
            assertEquals("cache.book.status", method)
            captured = JSONObject(params.toString())
            JSONObject()
                .put("sourceId", params.getString("sourceId"))
                .put("bookId", params.getString("bookId"))
                .put("tocAvailable", true)
                .put("chapterCount", 2)
                .put(
                    "chapters",
                    JSONArray().put(
                        JSONObject()
                            .put("chapterIndex", 0)
                            .put("title", "Chapter 1")
                            .put("url", "/chapter/0")
                            .put("state", "cached")
                            .put("cachedBytes", 128L)
                            .put("attempts", 1)
                            .put("maxAttempts", 3)
                            .put("lastError", JSONObject.NULL)
                    )
                )
                .put("cachedCount", 1)
                .put("queuedCount", 0)
                .put("inProgressCount", 0)
                .put("completedCount", 0)
                .put("failedCount", 0)
                .put("cancelledCount", 0)
                .put("missingCount", 1)
                .put(
                    "globalStats",
                    JSONObject()
                        .put("entryCount", 1)
                        .put("totalContentBytes", 128L)
                        .put("oldestCachedAt", JSONObject.NULL)
                        .put("newestCachedAt", 20L)
                        .put("queueEntryCount", 0)
                        .put("queuedCount", 0)
                        .put("inProgressCount", 0)
                        .put("completedCount", 0)
                        .put("failedCount", 0)
                        .put("cancelledCount", 0)
                )
        })

        val outcome = service.cacheBookStatus("source/exact", "book:exact")
        val status = (outcome as Slice10Outcome.Success).value

        assertEquals("source/exact", captured!!.getString("sourceId"))
        assertEquals("book:exact", captured!!.getString("bookId"))
        assertEquals(1, status.chapters.size)
        assertEquals("cached", status.chapters.single().state)
        assertNull(status.chapters.single().lastError)
        assertEquals(20L, status.globalStats.newestCachedAt)
    }

    @Test
    fun `cache prefetch sends start inclusive end exclusive range without reinterpretation`() = runBlocking {
        var captured: JSONObject? = null
        val service = CoreSlice10Service(Slice10CoreCommandClient { method, params ->
            assertEquals("cache.book.prefetch", method)
            captured = JSONObject(params.toString())
            JSONObject()
                .put("sourceId", params.getString("sourceId"))
                .put("bookId", params.getString("bookId"))
                .put("chapterRange", JSONArray(params.getJSONArray("chapterRange").toString()))
                .put("chapterCount", 8)
                .put("prefetchedCount", 2)
                .put("queuedIndexes", JSONArray().put(2).put(3))
                .put("alreadyQueuedIndexes", JSONArray().put(4))
                .put("skippedCachedIndexes", JSONArray().put(1))
        })

        val outcome = service.prefetchBookCache(
            sourceId = "source-1",
            bookId = "book-1",
            startInclusive = 1,
            endExclusive = 5,
            priority = 9,
            requestedAt = 42L
        )
        val result = (outcome as Slice10Outcome.Success).value

        assertEquals(1, captured!!.getJSONArray("chapterRange").getInt(0))
        assertEquals(5, captured!!.getJSONArray("chapterRange").getInt(1))
        assertEquals(2, captured!!.getJSONArray("chapterRange").length())
        assertEquals(9, captured!!.getInt("priority"))
        assertEquals(42L, captured!!.getLong("requestedAt"))
        assertEquals(1, result.startInclusive)
        assertEquals(5, result.endExclusive)
        assertEquals(listOf(2, 3), result.queuedIndexes)
    }

    @Test
    fun `cache clear transmits canonical scope and complete book selector`() = runBlocking {
        val captured = mutableListOf<JSONObject>()
        val service = CoreSlice10Service(Slice10CoreCommandClient { method, params ->
            assertEquals("cache.clear", method)
            captured += JSONObject(params.toString())
            JSONObject()
                .put("scope", params.getString("scope"))
                .put("cacheEntriesRemoved", 1)
                .put("chapterEntriesRemoved", 2)
                .put("queueEntriesRemoved", 3)
                .put("removedContentBytes", 4L)
        })

        val book = service.clearCache(CoreCacheClearScope.BOOK, "source-1", "book-1")
        val all = service.clearCache(CoreCacheClearScope.ALL)

        assertTrue(book is Slice10Outcome.Success)
        assertTrue(all is Slice10Outcome.Success)
        assertEquals("book", captured[0].getString("scope"))
        assertEquals("source-1", captured[0].getString("sourceId"))
        assertEquals("book-1", captured[0].getString("bookId"))
        assertEquals("all", captured[1].getString("scope"))
        assertFalse(captured[1].has("sourceId"))
        assertFalse(captured[1].has("bookId"))
    }

    @Test
    fun `replace persist retains the exact Core issued undo token`() = runBlocking {
        var captured: JSONObject? = null
        val service = CoreSlice10Service(Slice10CoreCommandClient { method, params ->
            assertEquals("replace.persist", method)
            captured = JSONObject(params.toString())
            val rule = JSONObject()
                .put("id", 42L)
                .put("name", "weather")
                .put("pattern", "rain")
                .put("replacement", "sun")
                .put("scopeTitle", false)
                .put("scopeContent", true)
                .put("isEnabled", true)
                .put("isRegex", false)
                .put("timeoutMillisecond", 3000L)
                .put("order", 0)
            val token = JSONObject()
                .put("schemaVersion", 1)
                .put("transactionId", params.getString("transactionId"))
                .put("revision", "a".repeat(64))
                .put("operation", "create")
                .put("ruleId", 42L)
                .put("issuedAt", 10L)
                .put("expiresAt", 310L)
                .put("after", JSONObject(rule.toString()))
            JSONObject()
                .put("operation", "create")
                .put("data", JSONObject().put("rule", rule))
                .put("undoToken", token)
        })

        val outcome = service.persistReplace(
            operation = CoreReplacePersistOperation.CREATE,
            params = JSONObject()
                .put("name", "weather")
                .put("pattern", "rain")
                .put("replacement", "sun")
                .put("scopeContent", true),
            transactionId = "replace-ui-42"
        )
        val result = (outcome as Slice10Outcome.Success).value

        assertEquals("create", captured!!.getString("operation"))
        assertEquals("replace-ui-42", captured!!.getString("transactionId"))
        assertEquals(300, captured!!.getInt("undoTtlSeconds"))
        assertEquals(42L, result.rule!!.id)
        assertEquals("replace-ui-42", result.undoToken.transactionId)
        assertEquals("a".repeat(64), result.undoToken.revision)
        assertEquals(310L, result.undoExpiresAt)
    }

    @Test
    fun `replace undo forwards the complete opaque token and parses explicit result`() = runBlocking {
        fun rule(replacement: String): JSONObject = JSONObject()
            .put("id", 42L)
            .put("name", "weather")
            .put("pattern", "rain")
            .put("replacement", replacement)
            .put("scopeTitle", false)
            .put("scopeContent", true)
            .put("isEnabled", true)
            .put("isRegex", false)
            .put("timeoutMillisecond", 3000L)
            .put("order", 0)

        val token = JSONObject()
            .put("schemaVersion", 1)
            .put("transactionId", "replace-1")
            .put("revision", "a".repeat(64))
            .put("operation", "update")
            .put("ruleId", 42L)
            .put("issuedAt", 10L)
            .put("expiresAt", 20L)
            .put("before", rule("before"))
            .put("after", rule("after"))
        var captured: JSONObject? = null
        val service = CoreSlice10Service(Slice10CoreCommandClient { method, params ->
            assertEquals("replace.undo", method)
            captured = JSONObject(params.toString())
            JSONObject()
                .put("transactionId", "replace-1")
                .put("revision", "a".repeat(64))
                .put("operation", "update")
                .put("ruleId", 42L)
                .put("changed", true)
                .put("undoneAt", 15L)
                .put("restoredRule", rule("before"))
        })

        val result = (service.undoReplace(token) as Slice10Outcome.Success).value

        assertEquals(token.toString(), captured!!.getJSONObject("undoToken").toString())
        assertTrue(result.changed)
        assertNotNull(result.restoredRule)
        assertEquals("before", result.restoredRule!!.replacement)
    }

    @Test
    fun `replace undo fails closed when any echoed token identity field drifts`() = runBlocking {
        val token = replaceUndoToken(
            transactionId = "replace-strict",
            revision = "c".repeat(64),
            operation = "update",
            ruleId = 77L
        )
        val cases = listOf(
            "transactionId" to "other-transaction",
            "revision" to "d".repeat(64),
            "operation" to "delete",
            "ruleId" to 78L
        )

        cases.forEach { (field, drifted) ->
            val service = CoreSlice10Service(Slice10CoreCommandClient { _, _ ->
                JSONObject()
                    .put("transactionId", "replace-strict")
                    .put("revision", "c".repeat(64))
                    .put("operation", "update")
                    .put("ruleId", 77L)
                    .put("changed", true)
                    .put("undoneAt", 15L)
                    .put("restoredRule", persistedRule(77L, "before"))
                    .put(field, drifted)
            })

            val outcome = service.undoReplace(token) as Slice10Outcome.Failed

            assertEquals(field, Slice10FailureCode.CORE_PROTOCOL_MISMATCH, outcome.failure.code)
        }
    }

    @Test
    fun `replace undo requires a complete restored rule before reporting success`() = runBlocking {
        val token = replaceUndoToken(
            transactionId = "replace-restored-rule",
            revision = "e".repeat(64),
            operation = "delete",
            ruleId = 88L
        )
        val service = CoreSlice10Service(Slice10CoreCommandClient { _, _ ->
            JSONObject()
                .put("transactionId", "replace-restored-rule")
                .put("revision", "e".repeat(64))
                .put("operation", "delete")
                .put("ruleId", 88L)
                .put("changed", true)
                .put("undoneAt", 15L)
                .put("restoredRule", JSONObject().put("id", 88L))
        })

        val outcome = service.undoReplace(token) as Slice10Outcome.Failed

        assertEquals(Slice10FailureCode.CORE_PROTOCOL_MISMATCH, outcome.failure.code)
    }

    @Test
    fun `replace undo rejects a complete restored rule that drifts from the token before snapshot`() = runBlocking {
        val token = replaceUndoToken(
            transactionId = "replace-restored-drift",
            revision = "f".repeat(64),
            operation = "update",
            ruleId = 89L
        )
        val service = CoreSlice10Service(Slice10CoreCommandClient { _, _ ->
            JSONObject()
                .put("transactionId", "replace-restored-drift")
                .put("revision", "f".repeat(64))
                .put("operation", "update")
                .put("ruleId", 89L)
                .put("changed", true)
                .put("undoneAt", 15L)
                .put("restoredRule", persistedRule(89L, "drifted"))
        })

        val outcome = service.undoReplace(token) as Slice10Outcome.Failed

        assertEquals(Slice10FailureCode.CORE_PROTOCOL_MISMATCH, outcome.failure.code)
    }

    @Test
    fun `cache and undo reject invalid input before Core dispatch`() = runBlocking {
        var calls = 0
        val service = CoreSlice10Service(Slice10CoreCommandClient { _, _ -> calls++; JSONObject() })

        val range = service.prefetchBookCache("source", "book", 4, 4)
        val selector = service.clearCache(CoreCacheClearScope.BOOK, "source", null)
        val token = service.undoReplace(JSONObject())

        assertEquals(Slice10FailureCode.INVALID_INPUT, (range as Slice10Outcome.Failed).failure.code)
        assertEquals(Slice10FailureCode.INVALID_INPUT, (selector as Slice10Outcome.Failed).failure.code)
        assertEquals(Slice10FailureCode.INVALID_INPUT, (token as Slice10Outcome.Failed).failure.code)
        assertEquals(0, calls)
    }

    private fun persistedRule(id: Long, replacement: String): JSONObject = JSONObject()
        .put("id", id)
        .put("name", "weather")
        .put("pattern", "rain")
        .put("replacement", replacement)
        .put("scopeTitle", false)
        .put("scopeContent", true)
        .put("isEnabled", true)
        .put("isRegex", false)
        .put("timeoutMillisecond", 3000L)
        .put("order", 0)

    private fun replaceUndoToken(
        transactionId: String,
        revision: String,
        operation: String,
        ruleId: Long
    ): JSONObject {
        val before = persistedRule(ruleId, "before")
        val after = persistedRule(ruleId, "after")
        return JSONObject()
            .put("schemaVersion", 1)
            .put("transactionId", transactionId)
            .put("revision", revision)
            .put("operation", operation)
            .put("ruleId", ruleId)
            .put("issuedAt", 10L)
            .put("expiresAt", 20L)
            .apply {
                when (operation) {
                    "create" -> put("after", after)
                    "update" -> put("before", before).put("after", after)
                    "delete" -> put("before", before)
                }
            }
    }

    @Test
    fun `Core error and missing cache result fail explicitly`() = runBlocking {
        val rejected = CoreSlice10Service(Slice10CoreCommandClient { _, _ ->
            throw RuntimeException("core error")
        }).clearCache(CoreCacheClearScope.CACHE) as Slice10Outcome.Failed
        val missing = CoreSlice10Service(Slice10CoreCommandClient { _, _ ->
            JSONObject().put("sourceId", "source").put("bookId", "book")
        }).cacheBookStatus("source", "book") as Slice10Outcome.Failed

        assertEquals(Slice10FailureCode.CORE_REJECTED, rejected.failure.code)
        assertEquals(Slice10FailureCode.CORE_PROTOCOL_MISMATCH, missing.failure.code)
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
