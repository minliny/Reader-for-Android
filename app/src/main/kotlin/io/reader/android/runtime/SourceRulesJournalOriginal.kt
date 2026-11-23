package io.reader.android.runtime

/** A read of the committed base only. candidateRetained cannot be silently erased by callers. */
internal data class SourceRulesJournalOriginal(val recordJson: String, val candidateRetained: Boolean)
