# Android Slice 9 local implementation status — 2026-07-19

Authority:

- `Reader-UI/contracts/SLICE_PLAN.md` Slice 9
- `Reader-UI/contracts/PLATFORM_EVIDENCE_SPEC.md` Slice 9 bundle
- Reader UI screen graph 3.0.0

This note records only current Android repository truth. It is not a
`slice-9=passed` evidence manifest.

## Implemented locally

- Android SAF `file.select` now feeds an actual five-format candidate scan
  instead of the previous fixed preview rows.
- TXT / EPUB / PDF / MOBI / UMD selections are read as opaque `content://`
  streams under a bounded Host read and sent to the real Core command
  `local_book.import` through `bytesBase64`.
- Metadata, chapter splitting, TOC and persistence remain Core-owned. Android
  validates returned `bookId`, format, lengths, chapter count and persisted TOC
  before showing success.
- Empty, oversized, unsupported, MIME/extension-conflicting, permission-denied,
  unreadable, Core-unavailable, Core-rejected and protocol-drift cases have
  structured fail-closed results.
- TXT may enter the existing text reader. EPUB / PDF / MOBI / UMD import can be
  materialized, but opening remains explicitly blocked until each format has a
  contract-backed renderer and locator. None is downgraded to
  `ReadingTextFlow`.
- Host filesystem and media save-path checks use real path-segment boundaries,
  closing the sibling-prefix escape case (`sandbox` vs `sandbox-escape`).
- `media.download` writes successful responses through a sibling staging file
  and atomic replace when available. HTTP error bodies cannot overwrite a
  canonical offline asset, and a requested `savePath` without a configured
  Host storage root fails closed.

## Local test result

Command:

```text
JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home \
./gradlew :app:testDebugUnitTest \
  --tests com.reader.android.data.adapter.CoreLocalBookImportServiceTest \
  --tests com.reader.host.CoreRuntimeCapabilityHandlersJvmTest \
  --tests com.reader.host.MediaDownloadSavePathJvmTest
```

Result: `BUILD SUCCESSFUL`, 38 tests, 0 failures:

- `CoreLocalBookImportServiceTest`: 12
- `CoreRuntimeCapabilityHandlersJvmTest`: 20
- `MediaDownloadSavePathJvmTest`: 6

## Still not proved

- Five real-file corpus through an installed Android app and real Reader Core
  artifact.
- PDF page renderer/locator/zoom/search/recovery.
- EPUB, MOBI and UMD format-specific renderers and canonical locators.
- Manga page renderer, preload/cache failure and canonical progress.
- Audio-content player, audio focus, background playback and media keys. System
  TTS is deliberately not counted as an audio-book player.
- Persistent download queue, task cancellation/retry/restart recovery, storage
  quota/space exhaustion, and protected-media credentials.
- Activity/permission/process-restart proof on a physical Android device.
- Cross-platform corpus diff and the required Slice 9 recordings/artifact
  digests.

Therefore Android Slice 9 is locally implemented only for the admitted
five-format SAF-to-Core import path and hardened Host file/media primitives.
The formal Slice 9 evidence bundle remains blocked and must not be marked
`passed`.
