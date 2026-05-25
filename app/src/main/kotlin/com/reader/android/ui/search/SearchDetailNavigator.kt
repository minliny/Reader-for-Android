package com.reader.android.ui.search

/**
 * Hardened Search → Detail navigation payload with stable id fallback.
 * Does NOT access network or parser internals.
 */
data class SearchDetailPayload(
    val detailUrl: String,
    val title: String,
    val sourceName: String,
    val author: String = ""
) {
    /** Stable id generated from detailUrl hash — deterministic and test-reproducible. */
    val bookId: String get() = detailUrl.ifBlank { "unknown" }.let { "detail-${it.hashCode()}" }

    val isValid: Boolean get() = detailUrl.isNotBlank()
}

object SearchDetailNavigator {

    /** Build hardened payload from search result. */
    fun payload(result: SearchResultUiModel): SearchDetailPayload =
        SearchDetailPayload(
            detailUrl = result.detailTarget,
            title = result.title,
            sourceName = result.sourceName,
            author = result.author
        )

    /** Fallback payload when detailTarget is empty. */
    fun fallback(title: String, sourceName: String): SearchDetailPayload =
        SearchDetailPayload(
            detailUrl = "fallback-${title.hashCode()}",
            title = title,
            sourceName = sourceName,
            author = ""
        )

    /** Payload from fixture — always valid for test/prototype use. */
    fun fixture(title: String = "纸上群山"): SearchDetailPayload =
        SearchDetailPayload(
            detailUrl = "fixture-detail-paper-mountain",
            title = title,
            sourceName = "UI Fixture",
            author = "南溪"
        )
}
