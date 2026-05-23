package com.reader.android.ui.reader

import com.reader.android.ui.reader.components.BrightnessDock

object ReaderRuntimeMapper {

    fun toControlBaseParams(state: ReaderRuntimeUiState): ControlBaseParams = ControlBaseParams(
        chapterTitle = state.chapter.chapterTitle,
        bookTitle = state.book.bookTitle,
        sourceName = state.book.sourceName,
        chapterProgress = state.pageProgress.progress,
        brightnessDock = state.brightnessDockState.dock
    )

    fun overlayType(state: ReaderRuntimeUiState): ReaderOverlayType = when (val s = state.controlLayerState) {
        is ReaderControlLayerState.BaseControlVisible -> ReaderOverlayType.NONE
        is ReaderControlLayerState.QuickActionOverlay -> s.type
        is ReaderControlLayerState.BottomFunctionOverlay -> s.type
    }

    fun isQuickActionVisible(state: ReaderRuntimeUiState): Boolean =
        state.controlLayerState is ReaderControlLayerState.QuickActionOverlay

    fun isBottomFunctionVisible(state: ReaderRuntimeUiState): Boolean =
        state.controlLayerState is ReaderControlLayerState.BottomFunctionOverlay

    fun quickActionType(state: ReaderRuntimeUiState): ReaderOverlayType? =
        (state.controlLayerState as? ReaderControlLayerState.QuickActionOverlay)?.type

    fun bottomFunctionType(state: ReaderRuntimeUiState): ReaderOverlayType? =
        (state.controlLayerState as? ReaderControlLayerState.BottomFunctionOverlay)?.type

    data class ControlBaseParams(
        val chapterTitle: String,
        val bookTitle: String,
        val sourceName: String,
        val chapterProgress: Float,
        val brightnessDock: BrightnessDock
    )
}
