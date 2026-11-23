package io.reader.android.ui.ebookdraft

/** UI observations only. Pending Core parameters and receipts are owned by the Library journal. */
internal class ReaderEbookAnchorAdmission(initialAnchor: Int) {
    var desiredAnchor: Int = initialAnchor
        private set
    var lastVisible: Int? = null
        private set

    fun restore(hostAnchor: Int, external: Boolean = false) {
        if (external) lastVisible = hostAnchor // An explicit Host anchor replaces the prior reflow basis.
        desiredAnchor = lastVisible ?: hostAnchor
    }

    /** The synchronous callback admits a display coordinate, not a Core write acknowledgement. */
    fun observe(scalar: Int, admitDisplay: () -> Boolean) {
        if (lastVisible == scalar || !admitDisplay()) return
        lastVisible = scalar
        desiredAnchor = scalar
    }
}
