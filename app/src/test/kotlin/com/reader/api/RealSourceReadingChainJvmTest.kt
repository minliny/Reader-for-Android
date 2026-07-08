package com.reader.api

import com.reader.ui.shell.AsyncResultStateValue
import com.reader.ui.shell.ReaderContext
import com.reader.ui.shell.ReaderEntry
import com.reader.ui.shell.ReaderUiIntent
import com.reader.ui.shell.ReaderUiReducer
import com.reader.ui.shell.ReaderUiState
import com.reader.ui.shell.RouteIds
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 6 / audit task 4 — real-source reading chain JVM proof.
 *
 * The prior [com.reader.ui.reading.ReadingLinkAsyncGuardJvmTest] used the
 * `fixture://` source path (synchronous, hardcoded text). This test exercises
 * the **real book-source data shape**: the JSON contracts that Core's
 * `book.search` / `book.detail` / `book.toc` / `chapter.content` commands
 * return, and that [BookApi.parseSearchResult] / [parseBookDetail] /
 * [parseTocResult] / [parseContentResult] decode.
 *
 * The chain proven here mirrors what a real source does on device:
 *   search → pick first book → detail → toc → pick first chapter → content
 *   → build ReaderContext → dispatch EnterReaderFromAction → reducer drives
 *   asyncResult IDLE → PENDING → COMPLETED.
 *
 * Core command dispatch (native .so) is not exercised on the JVM — that lane
 * is covered by [com.reader.CoreRuntimeCapabilityInstrumentedProofTest] on
 * device. This test proves the data-shape → parser → state-machine closure.
 */
class RealSourceReadingChainJvmTest {

    @Test
    fun `search result parses real source JSON shape`() {
        val json = JSONObject().apply {
            put("books", JSONArray().apply {
                put(JSONObject().apply {
                    put("bookId", "https://source.example.com/book/1001")
                    put("title", "长夜余火")
                    put("author", "爱潜水的乌贼")
                    put("coverUrl", "https://source.example.com/cover/1001.jpg")
                    put("intro", "雨声连成一片...")
                    put("lastChapter", "第 32 章 雨夜")
                    put("kind", "玄幻,热血")
                    put("origin", "优书网")
                })
                put(JSONObject().apply {
                    put("bookId", "https://source.example.com/book/1002")
                    put("title", "长夜余火（番外）")
                    put("author", "爱潜水的乌贼")
                    put("coverUrl", "")
                    put("intro", "")
                    put("lastChapter", "番外 03")
                    put("kind", "玄幻")
                    put("origin", "优书网")
                })
            })
        }

        val results = BookApi.parseSearchResult(json)
        assertEquals(2, results.size)

        val first = results[0]
        assertEquals("https://source.example.com/book/1001", first.bookUrl)
        assertEquals("长夜余火", first.name)
        assertEquals("爱潜水的乌贼", first.author)
        assertEquals("https://source.example.com/cover/1001.jpg", first.coverUrl)
        assertEquals("第 32 章 雨夜", first.lastChapter)
        assertEquals("优书网", first.origin)
    }

    @Test
    fun `detail result parses real source JSON shape with tocUrl`() {
        val json = JSONObject().apply {
            put("book", JSONObject().apply {
                put("bookId", "https://source.example.com/book/1001")
                put("tocUrl", "https://source.example.com/toc/1001")
                put("title", "长夜余火")
                put("author", "爱潜水的乌贼")
                put("coverUrl", "https://source.example.com/cover/1001.jpg")
                put("intro", "雨声连成一片...")
                put("kind", "玄幻,热血")
                put("wordCount", "320万字")
                put("lastChapter", "第 32 章 雨夜")
                put("origin", "优书网")
            })
        }

        val book = BookApi.parseBookDetail(json)
        assertEquals("https://source.example.com/book/1001", book.bookUrl)
        assertEquals("https://source.example.com/toc/1001", book.tocUrl)
        assertEquals("长夜余火", book.name)
        assertEquals("爱潜水的乌贼", book.author)
        assertEquals("320万字", book.wordCount)
        assertEquals("第 32 章 雨夜", book.latestChapterTitle)
    }

    @Test
    fun `toc result parses real source JSON shape with chapter list`() {
        val json = JSONObject().apply {
            put("toc", JSONArray().apply {
                put(JSONObject().apply {
                    put("title", "第 31 章 暗涌")
                    put("url", "https://source.example.com/chapter/1001/31")
                    put("index", 30)
                    put("isVip", false)
                    put("isPay", false)
                })
                put(JSONObject().apply {
                    put("title", "第 32 章 雨夜")
                    put("url", "https://source.example.com/chapter/1001/32")
                    put("index", 31)
                    put("isVip", true)
                    put("isPay", true)
                })
            })
        }

        val chapters = BookApi.parseTocResult(json)
        assertEquals(2, chapters.size)

        val first = chapters[0]
        assertEquals("第 31 章 暗涌", first.title)
        assertEquals("https://source.example.com/chapter/1001/31", first.url)
        assertEquals(30, first.index)
        assertEquals(false, first.isVip)

        val second = chapters[1]
        assertEquals("第 32 章 雨夜", second.title)
        assertEquals(true, second.isVip)
        assertEquals(true, second.isPay)
    }

    @Test
    fun `content result parses real source JSON shape`() {
        val json = JSONObject().apply {
            put("content", "雨声在窗外连成一片，像无数细小的针，密密地刺在玻璃上。")
        }

        val content = BookApi.parseContentResult(json)
        assertEquals("雨声在窗外连成一片，像无数细小的针，密密地刺在玻璃上。", content)
    }

    /**
     * Full chain: search → detail → toc → content → ReaderContext → reducer.
     * This is the "real-source reading chain" proof — every step uses the
     * real [BookApi] parser, and the final ReaderContext drives the same
     * async-result guard that [ImmersiveReadingViewModel] engages on device.
     */
    @Test
    fun `full reading chain drives asyncResult pending to completed`() {
        val sourceId = "https://source.example.com"

        // Step 1: search
        val searchJson = sampleSearchResponse()
        val searchResults = BookApi.parseSearchResult(searchJson)
        assertEquals(2, searchResults.size)
        val picked = searchResults.first { it.name == "长夜余火" }

        // Step 2: detail
        val detailJson = sampleDetailResponse(picked.bookUrl)
        val book = BookApi.parseBookDetail(detailJson)
        assertEquals("长夜余火", book.name)
        assertTrue("detail should carry tocUrl", book.tocUrl.isNotEmpty())

        // Step 3: toc
        val tocJson = sampleTocResponse()
        val chapters = BookApi.parseTocResult(tocJson)
        assertEquals(2, chapters.size)
        val firstChapter = chapters.first()

        // Step 4: content
        val contentJson = sampleContentResponse(firstChapter.url)
        val content = BookApi.parseContentResult(contentJson)
        assertTrue("content should be non-blank real chapter text", content.isNotBlank())

        // Step 5: build ReaderContext from real-source data + dispatch entry
        val entryRequestId = "req-real-source-1"
        val readerContext = ReaderContext(
            sourceId = sourceId,
            bookUrl = book.bookUrl,
            bookName = book.name,
            entry = ReaderEntry.COVER_TO_IMMERSIVE,
            entryRequestId = entryRequestId
        )

        // Step 6: reducer drives asyncResult IDLE → PENDING → COMPLETED
        var state = ReaderUiState(readerContext = readerContext)
        assertEquals(AsyncResultStateValue.IDLE, state.asyncResult.state)

        state = ReaderUiReducer.reduce(
            state,
            ReaderUiIntent.StartAsyncRequest(
                fromRoute = RouteIds.IMMERSIVE_READING,
                toRoute = RouteIds.IMMERSIVE_READING,
                requestId = entryRequestId
            )
        )
        assertEquals(AsyncResultStateValue.PENDING, state.asyncResult.state)

        state = ReaderUiReducer.reduce(
            state,
            ReaderUiIntent.CompleteAsyncRequest(
                requestId = entryRequestId,
                value = content,
                currentRoute = RouteIds.IMMERSIVE_READING
            )
        )
        assertEquals(AsyncResultStateValue.COMPLETED, state.asyncResult.state)
        assertEquals(content, state.asyncResult.value)
    }

    /**
     * Stale-result guard with real-source data: entry A loads chapter content,
     * entry B supersedes A; A's late COMPLETED is DISCARDED so the stale
     * chapter text never overwrites entry B's surface.
     */
    @Test
    fun `stale real-source chapter content is discarded after newer entry`() {
        val sourceId = "https://source.example.com"
        val bookUrl = "https://source.example.com/book/1001"

        val reqA = "req-real-source-a"
        val reqB = "req-real-source-b"

        var state = ReaderUiState(
            readerContext = ReaderContext(
                sourceId = sourceId,
                bookUrl = bookUrl,
                bookName = "长夜余火",
                entry = ReaderEntry.COVER_TO_IMMERSIVE,
                entryRequestId = reqA
            )
        )

        // Entry A starts loading (PENDING).
        state = ReaderUiReducer.reduce(
            state,
            ReaderUiIntent.StartAsyncRequest(
                fromRoute = RouteIds.IMMERSIVE_READING,
                toRoute = RouteIds.IMMERSIVE_READING,
                requestId = reqA
            )
        )

        // Entry B supersedes A before A's content arrives.
        state = ReaderUiReducer.reduce(
            state,
            ReaderUiIntent.StartAsyncRequest(
                fromRoute = RouteIds.IMMERSIVE_READING,
                toRoute = RouteIds.IMMERSIVE_READING,
                requestId = reqB
            )
        )
        assertEquals(reqB, state.asyncResult.requestId)

        // Entry A's late content (real chapter text) arrives — must be DISCARDED.
        val staleContent = BookApi.parseContentResult(sampleContentResponse("https://source.example.com/chapter/1001/31"))
        state = ReaderUiReducer.reduce(
            state,
            ReaderUiIntent.CompleteAsyncRequest(
                requestId = reqA,
                value = staleContent,
                currentRoute = RouteIds.IMMERSIVE_READING
            )
        )
        assertEquals(AsyncResultStateValue.DISCARDED, state.asyncResult.state)
        assertEquals(reqB, state.asyncResult.requestId)
    }

    // ── sample JSON fixtures (real source shapes) ───────────────────────────

    private fun sampleSearchResponse(): JSONObject = JSONObject().apply {
        put("books", JSONArray().apply {
            put(JSONObject().apply {
                put("bookId", "https://source.example.com/book/1001")
                put("title", "长夜余火")
                put("author", "爱潜水的乌贼")
                put("coverUrl", "https://source.example.com/cover/1001.jpg")
                put("intro", "雨声连成一片...")
                put("lastChapter", "第 32 章 雨夜")
                put("kind", "玄幻,热血")
                put("origin", "优书网")
            })
            put(JSONObject().apply {
                put("bookId", "https://source.example.com/book/1002")
                put("title", "长夜余火（番外）")
                put("author", "爱潜水的乌贼")
                put("coverUrl", "")
                put("intro", "")
                put("lastChapter", "番外 03")
                put("kind", "玄幻")
                put("origin", "优书网")
            })
        })
    }

    private fun sampleDetailResponse(bookId: String): JSONObject = JSONObject().apply {
        put("book", JSONObject().apply {
            put("bookId", bookId)
            put("tocUrl", "https://source.example.com/toc/1001")
            put("title", "长夜余火")
            put("author", "爱潜水的乌贼")
            put("coverUrl", "https://source.example.com/cover/1001.jpg")
            put("intro", "雨声连成一片...")
            put("kind", "玄幻,热血")
            put("wordCount", "320万字")
            put("lastChapter", "第 32 章 雨夜")
            put("origin", "优书网")
        })
    }

    private fun sampleTocResponse(): JSONObject = JSONObject().apply {
        put("toc", JSONArray().apply {
            put(JSONObject().apply {
                put("title", "第 31 章 暗涌")
                put("url", "https://source.example.com/chapter/1001/31")
                put("index", 30)
                put("isVip", false)
                put("isPay", false)
            })
            put(JSONObject().apply {
                put("title", "第 32 章 雨夜")
                put("url", "https://source.example.com/chapter/1001/32")
                put("index", 31)
                put("isVip", true)
                put("isPay", true)
            })
        })
    }

    private fun sampleContentResponse(chapterUrl: String): JSONObject = JSONObject().apply {
        put("content", "雨声在窗外连成一片，像无数细小的针，密密地刺在玻璃上，汇成一层朦胧的水幕。" +
            "他站在窗前，手里握着那封被雨水润湿的信。")
    }
}
