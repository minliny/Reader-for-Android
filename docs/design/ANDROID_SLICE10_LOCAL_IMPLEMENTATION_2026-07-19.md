# Android Slice 10 local implementation status — 2026-07-19

Authority: Reader UI 3.0 `SLICE_PLAN.md` Slice 10 and
`PLATFORM_EVIDENCE_SPEC.md` Slice 10 bundle.

This is a local implementation record, not a passed Slice 10 manifest.

## Implemented

- Search history now calls `search.history.list/add/clear` in Reader Core only.
  The production Room fallback and AppProvider accessor were removed. If Core
  is unavailable, the Host reply is `CORE_UNAVAILABLE`; Android cannot
  acknowledge a write to a second store.
- `CoreSlice10Service` supplies thin Core-only bridges for:
  - bookmark list;
  - content edit put/list;
  - ReplaceRule list;
  - TxtTocRule list;
  - HttpTTS list and `http-tts.build-request`.
- HttpTTS list projection excludes URL, headers, login UI and JS. The request
  descriptor keeps headers/body private, redacts diagnostics, validates
  `http/https`, and exposes credentials only when serialized at the Host
  `http.execute` edge.
- An executable Android capability matrix distinguishes available Core
  protocol from query-only and blocked transaction/Host/device boundaries.

## Explicit fail-closed boundaries

- bookmark jump: bookmark V1 has chapter index/position but no canonical
  locator round-trip;
- reading history: Reader UI ReadRecord list/clear/version protocol not frozen;
- content edit: V1 CRUD has no version/conflict/rollback transaction;
- DictRule: query exists, CRUD/version does not;
- cover change/search/cache: no frozen transaction and rollback DTO;
- chapter reviews: no frozen list/paging/chapter-locator DTO;
- HttpTTS playback/media keys/audio focus/background: no complete Host
  protocol; system TTS is not substituted;
- physical-device and process-recreation evidence absent.

## Local tests

- `CoreSlice10ServiceTest`: 9/9
- `SearchHistoryCapabilityHandlersJvmTest`: 7/7

Target result: 16 tests, 0 failures. Full app regression and assemble are run
after the Slice 10–12 local sequence.
