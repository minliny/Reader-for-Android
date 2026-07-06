package com.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reader.api.CoreException
import com.reader.api.ReaderCoreClient
import com.reader.host.OkHttpHostTransport
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android instrumented proof for the `local_book.parse` Core command (item 6).
 *
 * Exercises the full FFI round-trip: Kotlin -> ReaderCoreClient.sendAndAwait ->
 * HostRuntime -> ReaderCoreHostTransport -> JNI `rc_runtime_send` -> Core
 * `local_book.parse` -> `reader_local_book::parse_txt_text_with_txt_toc_rules`
 * -> `result` event -> poll thread -> future completion.
 *
 * ## What this proves
 * The `local_book.parse` Core command is wired through JNI on Android and
 * honours its JSON contract (`LocalBookParseParams` / `LocalBookParseData` in
 * `crates/reader-contract/src/remote.rs`). Previously this capability was only
 * referenced as a string in `EvidenceRunner.kt`'s blocked list (line 99) — no
 * code ever sent the command. This proof closes that gap.
 *
 * ## Contract (actual, verified against Core integration tests at
 * `crates/reader-runtime/src/runtime.rs:4104+`)
 * Request params (camelCase, `deny_unknown_fields`):
 *   - `bookId` (required, non-blank)
 *   - `title`, `author`, `fileName` (optional)
 *   - `text` (TXT path) OR `bytesBase64` (binary path) — at least one required
 *   - `format` (optional hint: "txt" / "epub" / "pdf" / ...)
 * Response data (`LocalBookParseData`):
 *   - `book`: `{ bookId, title, author, coverUrl?, intro?, kind?, lastChapter?, variables }`
 *   - `format`: "txt"
 *   - `encoding`: "utf8"  (NOT "utf-8" — `LocalBookEncoding` serializes camelCase)
 *   - `byteLen`, `charLen`, `chapterCount`
 *
 * Note: the response does NOT carry a top-level `chapters` array or a
 * `sourceChecksum` field; chapter titles are observable only via
 * `book.lastChapter` and the `chapterCount` count. This mirrors the Core
 * contract exactly — the proof asserts what the contract guarantees.
 *
 * ## TXT auto-split behaviour
 * With a fresh runtime (no stored `TxtTocRule`s), the text path falls back to
 * the Auto heuristic (`reader_local_book::split_chapters`). Auto recognises
 * lines starting with "chapter" (case-insensitive) followed by whitespace or a
 * digit as chapter headings — and unlike the stored-rule path, the Auto
 * fallback applies NO 1000-char gap de-dup. So
 * "Chapter 1\n...\nChapter 2\n..." yields exactly 2 chapters.
 *
 * ## Evidence layering (charter §10.3)
 *  - This test = simulator device proof (NOT real device). It exercises the
 *    real `libreader_core_jni.so` via JNI, which `testDebugUnitTest` cannot.
 *  - No HTTP / MockWebServer: `local_book.parse` is pure parsing (no host
 *    handler is invoked). `OkHttpHostTransport` is wired only to satisfy
 *    `ReaderCoreClient.initForTest`'s signature; it is never called.
 *  - Real device + SAF Uri -> file read -> base64 -> Core (App-tier proof)
 *    deferred to beta.
 *
 * Mirrors iOS `LocalBookCoreImportBridgeTests` (App-level TXT import proof).
 */
@RunWith(AndroidJUnit4::class)
class HostLocalBookParseProofTest {

    private lateinit var client: ReaderCoreClient

    @Before
    fun setUp() {
        // local_book.parse needs no host HTTP handler, but initForTest
        // requires an HttpFetch to wire HostRuntime. Provide the default
        // OkHttp transport; it will never be invoked for these tests.
        client = ReaderCoreClient.initForTest(
            OkHttpHostTransport(OkHttpHostTransport.defaultClient(null))
        )
    }

    @After
    fun tearDown() {
        ReaderCoreClient.resetForTest()
    }

    /**
     * Proof 1: TXT text with two "Chapter N" headings -> Core returns a parsed
     * book with `format`="txt", `chapterCount`=2, non-empty title, and
     * `book.lastChapter`="Chapter 2" (Auto splitter recognised both headings).
     *
     * The response carries no top-level `chapters` array (per
     * `LocalBookParseData` contract), so chapter-title parity is verified via
     * `book.lastChapter`.
     */
    @Test
    fun txtParseReturnsBookWithChapters() = runBlocking {
        val params = JSONObject().apply {
            put("bookId", "proof-txt-1")
            put("title", "Proof TXT Book")
            put("author", "Proof Tester")
            put("fileName", "test.txt")
            put("text", "Chapter 1\nBody line one\nChapter 2\nMore body two")
        }

        val data = client.sendAndAwait("local_book.parse", params)

        assertEquals("format must be txt", "txt", data.getString("format"))
        val chapterCount = data.getInt("chapterCount")
        assertEquals(
            "chapterCount must be 2 (Auto splitter recognises 'Chapter N' headings)",
            2,
            chapterCount
        )
        val book = data.getJSONObject("book")
        assertEquals(
            "book.title must echo the provided title",
            "Proof TXT Book",
            book.getString("title")
        )
        assertEquals(
            "book.author must echo the provided author",
            "Proof Tester",
            book.getString("author")
        )
        assertEquals(
            "book.lastChapter must be 'Chapter 2' (last recognised heading)",
            "Chapter 2",
            book.getString("lastChapter")
        )
    }

    /**
     * Proof 2: TXT text -> `encoding`="utf8". `LocalBookEncoding` serializes as
     * camelCase ("utf8", not "utf-8"), matching the Core integration test at
     * `runtime.rs:4128`.
     */
    @Test
    fun txtParseDetectsEncoding() = runBlocking {
        val params = JSONObject().apply {
            put("bookId", "proof-txt-2")
            put("title", "Encoding Proof")
            put("text", "Chapter 1\nHello UTF-8 body.")
        }

        val data = client.sendAndAwait("local_book.parse", params)

        assertEquals(
            "encoding must be 'utf8' (LocalBookEncoding camelCase, not 'utf-8')",
            "utf8",
            data.getString("encoding")
        )
        assertEquals("format must be txt", "txt", data.getString("format"))
    }

    /**
     * Proof 3: blank text (whitespace-only) with no `bytesBase64` -> Core
     * rejects with `INVALID_PARAMS`
     * (`validate_local_book_parse_params` fails). Proves the validation gate
     * fires over the JNI round-trip, not just in-process.
     */
    @Test
    fun txtParseRejectsBlankText() = runBlocking {
        val params = JSONObject().apply {
            put("bookId", "proof-txt-blank")
            put("text", "   ")
        }

        try {
            client.sendAndAwait("local_book.parse", params)
            fail("expected CoreException for blank text, but got a result")
        } catch (e: CoreException) {
            val error = JSONObject(e.errorJson)
            val code = error.getString("code")
            assertEquals(
                "error.code must be INVALID_PARAMS, got: $code (full: ${e.errorJson})",
                "INVALID_PARAMS",
                code
            )
            val message = error.optString("message", "")
            assertTrue(
                "error.message should mention text/bytesBase64 requirement, got: $message",
                message.contains("text") || message.contains("bytesBase64")
            )
        }
    }

    /**
     * Proof 4: TXT text -> `book.bookId` non-empty (echoes the provided bookId)
     * and `book.lastChapter` non-empty. The Core `Book` struct has no
     * `sourceChecksum` field (the task brief's contract was stale); we assert
     * `bookId` + `lastChapter` + positive `charLen`/`byteLen` instead — all
     * contract-owned fields.
     */
    @Test
    fun txtParseReturnsNonEmptyBookId() = runBlocking {
        val params = JSONObject().apply {
            put("bookId", "proof-txt-id-1")
            put("title", "ID Proof Book")
            put("fileName", "id.txt")
            put("text", "Chapter 1\nFirst body\nChapter 2\nSecond body")
        }

        val data = client.sendAndAwait("local_book.parse", params)

        val book = data.getJSONObject("book")
        val bookId = book.getString("bookId")
        assertTrue(
            "book.bookId must be non-empty, got: '$bookId'",
            bookId.isNotEmpty()
        )
        assertEquals(
            "book.bookId must echo the provided bookId",
            "proof-txt-id-1",
            bookId
        )
        val lastChapter = book.optString("lastChapter", "")
        assertTrue(
            "book.lastChapter must be non-empty (proves chapter split), got: '$lastChapter'",
            lastChapter.isNotEmpty()
        )
        // charLen and byteLen must be positive for non-empty input.
        assertTrue(
            "charLen must be > 0 for non-empty text, got: ${data.opt("charLen")}",
            data.getLong("charLen") > 0
        )
        assertTrue(
            "byteLen must be > 0 for non-empty text, got: ${data.opt("byteLen")}",
            data.getLong("byteLen") > 0
        )
    }
}
