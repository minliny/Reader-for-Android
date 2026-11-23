package io.reader.android.runtime.ebookdraft

import androidx.recyclerview.widget.DiffUtil
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.TimeUnit

/** Core already processed the text. These are original Unicode-scalar coordinates, never UTF-16. */
internal data class EbookProcessedTextEdit(val startScalar: Int, val endScalar: Int, val text: String)

/** Existing AndroidX Myers diff supplies matching; only Reader's finite DOM edit contract lives here. */
internal object ReaderEbookTextEdits {
    private const val MAX_SCALARS = 32_768
    private const val MAX_CHANGES = 100_000
    private const val MAX_COMPARISONS = 2_000_000
    private val MAX_NANOS = TimeUnit.MILLISECONDS.toNanos(500)

    fun changes(original: String, processed: String): List<EbookProcessedTextEdit> {
        // The caller validated the source window's Unicode. Keep unchanged large windows allocation-free.
        if (original == processed) return emptyList()
        require(original.length <= MAX_SCALARS * 2 && processed.length <= MAX_SCALARS * 2 &&
            original.codePointCount(0, original.length) <= MAX_SCALARS &&
            processed.codePointCount(0, processed.length) <= MAX_SCALARS) { "EBOOK_TEXT_EDIT_SCALAR_LIMIT" }
        require(StandardCharsets.UTF_8.newEncoder().canEncode(original) &&
            StandardCharsets.UTF_8.newEncoder().canEncode(processed)) { "EBOOK_TEXT_EDIT_INVALID_UNICODE" }
        val source = original.codePoints().toArray()
        val target = processed.codePoints().toArray()
        val started = System.nanoTime()
        var comparisons = 0
        fun budget() {
            check(++comparisons <= MAX_COMPARISONS && System.nanoTime() - started <= MAX_NANOS) {
                "EBOOK_TEXT_EDIT_DIFF_BUDGET"
            }
        }
        val result = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = source.size
            override fun getNewListSize() = target.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                budget()
                return source[oldItemPosition] == target[newItemPosition]
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                source[oldItemPosition] == target[newItemPosition]
        }, false) // Scalar identity has no independently meaningful move operation.
        val removed = BooleanArray(source.size) { result.convertOldPositionToNew(it) == DiffUtil.DiffResult.NO_POSITION }
        val inserted = BooleanArray(target.size) { result.convertNewPositionToOld(it) == DiffUtil.DiffResult.NO_POSITION }
        val edits = ArrayList<EbookProcessedTextEdit>()
        var old = 0
        var new = 0
        var changed = 0
        while (old < source.size || new < target.size) {
            budget()
            if ((old < source.size && removed[old]) || (new < target.size && inserted[new])) {
                val start = old
                val targetStart = new
                while (old < source.size && removed[old]) old++
                while (new < target.size && inserted[new]) new++
                changed += old - start + new - targetStart
                check(changed <= MAX_CHANGES) { "EBOOK_TEXT_EDIT_CHANGE_LIMIT" }
                val text = buildString {
                    for (index in targetStart until new) {
                        require(target[index] != 0xfffc) { "EBOOK_TEXT_EDIT_INSERTED_IMAGE_UNPROVEN" }
                        appendCodePoint(target[index])
                    }
                }
                edits.add(EbookProcessedTextEdit(start, old, text))
            } else {
                check(old < source.size && new < target.size && source[old] == target[new] &&
                    result.convertOldPositionToNew(old) == new && result.convertNewPositionToOld(new) == old) {
                    "EBOOK_TEXT_EDIT_MATCH_UNPROVEN"
                }
                old++
                new++
            }
        }
        // Exact replay qualifies the algorithm output against the actual Core-confirmed text.
        val replay = StringBuilder(processed.length)
        var cursor = 0
        for (edit in edits) {
            check(edit.startScalar >= cursor && edit.endScalar in edit.startScalar..source.size)
            for (index in cursor until edit.startScalar) replay.appendCodePoint(source[index])
            replay.append(edit.text)
            check(replay.length <= processed.length) { "EBOOK_TEXT_EDIT_REPLAY_LENGTH" }
            cursor = edit.endScalar
        }
        for (index in cursor until source.size) replay.appendCodePoint(source[index])
        check(replay.toString() == processed) { "EBOOK_TEXT_EDIT_REPLAY_MISMATCH" }
        return Collections.unmodifiableList(ArrayList(edits))
    }
}
