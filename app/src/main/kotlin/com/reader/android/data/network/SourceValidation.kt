package com.reader.android.data.network

sealed class SourceValidationResult {
    data class Success(val sourceName: String, val sourceUrl: String) : SourceValidationResult()
    data class Warning(
        val sourceName: String, val sourceUrl: String,
        val warnings: List<String>
    ) : SourceValidationResult()
    data class Error(
        val sourceName: String, val sourceUrl: String,
        val errors: List<String>
    ) : SourceValidationResult()
}
