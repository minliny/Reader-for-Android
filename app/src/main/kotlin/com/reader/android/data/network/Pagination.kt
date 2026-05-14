package com.reader.android.data.network

data class PageRef(
    val url: String,
    val title: String? = null,
    val index: Int? = null
)

data class PaginationState(
    val currentPage: PageRef? = null,
    val nextPage: PageRef? = null,
    val previousPage: PageRef? = null,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false
) {
    companion object {
        fun fromCurrentUrl(currentUrl: String, nextUrl: String?): PaginationState {
            return PaginationState(
                currentPage = PageRef(url = currentUrl),
                nextPage = nextUrl?.let { PageRef(url = it) },
                hasNext = nextUrl != null
            )
        }

        fun empty() = PaginationState()
    }
}
