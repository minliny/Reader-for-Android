# Compose State Model

## 1. Global UI State

```kotlin
sealed interface ReaderUiState {
  data object Loading : ReaderUiState
  data object Empty : ReaderUiState
  data class Error(val message: String, val retryable: Boolean = true) : ReaderUiState
  data object Offline : ReaderUiState
  data class Disabled(val reason: String) : ReaderUiState
  data class PermissionRequired(val permission: String) : ReaderUiState
  data class LocalFileError(val message: String) : ReaderUiState
  data class NetworkSourceError(val sourceId: String, val message: String) : ReaderUiState
  data object WebDavAuthError : ReaderUiState
  data class SyncConflict(val localVersion: String, val remoteVersion: String) : ReaderUiState
  data class ImportSuccess(val targetId: String) : ReaderUiState
  data class ImportFailure(val message: String) : ReaderUiState
}
```

## 2. Reader Control State

```kotlin
sealed interface ReaderControlState {
  data object BaseControlVisible : ReaderControlState
  data class QuickActionOverlay(val type: QuickActionType) : ReaderControlState
  data class BottomFunctionOverlay(val type: BottomFunctionType) : ReaderControlState
  data object NightState : ReaderControlState
}

enum class QuickActionType { Search, AutoScroll, Replace }
enum class BottomFunctionType { Directory, Tts, Appearance, Settings }
enum class BrightnessDock { Left, Right }
```

Additional flags:
- `BrightnessLeftDocked`, `BrightnessRightDocked` -> `BrightnessDock`.
- `PageControlDragging` -> transient drag state with chapter progress.
- `TtsPlaying`, `TtsPaused` -> TTS state.
- `AutoScrollRunning`, `AutoScrollPaused` -> auto-scroll state.

## 3. Boundary Rules

- `QuickActionOverlay` hides brightness only; quick actions, page control, and bottom bar remain visible.
- `BottomFunctionOverlay` hides brightness, quick actions, and page control; top area and bottom bar remain visible.
- `NightState` is not an overlay and must not create a dialog.
- `ReaderSettingsOverlay` is only reading behavior; global settings remain separate.
