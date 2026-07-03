package com.reader.ui.shell

import androidx.lifecycle.ViewModel
import com.reader.ui.motion.ReducedMotionResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Holds the single [ReaderUiState] and dispatches [ReaderUiIntent]s through
 * [ReaderUiReducer]. This is the only mutator of the shell UI state — animations, tab
 * switches, route pushes, and reader entries all flow through [dispatch].
 *
 * Wires the platform [ReducedMotionResolver] at construction so the reduced-motion flag is
 * a real, observable state field rather than a render-side hack.
 */
class AppShellViewModel(
    reducedMotionResolver: ReducedMotionResolver? = null
) : ViewModel() {

    private val _state = MutableStateFlow(
        ReaderUiState(reducedMotion = reducedMotionResolver?.isReducedMotion() ?: false)
    )
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    fun dispatch(intent: ReaderUiIntent) {
        _state.update { ReaderUiReducer.reduce(it, intent) }
    }
}
