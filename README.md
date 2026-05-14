# Reader for Android

Android reading app — Compose + Material 3. Consumer of Reader-Core public API (JSON-level contract).

## Status

| Metric | Value |
|--------|-------|
| Stage | S6.5 Baseline Hardening |
| `./gradlew test` | BUILD SUCCESSFUL |
| `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL |
| Test count | 3 (needs expansion) |
| Next milestone | S6.5-P0-002 Parser contract tests |

## Quick start

```bash
cd "/Users/minliny/Documents/Reader for Android"
./gradlew test
./gradlew :app:assembleDebug
```

## Capability summary

- **S1**: Gradle Kotlin DSL + Compose Scaffold + 4-tab navigation
- **S2**: 6 Kotlin domain DTOs (BookInfo, BookSource, SearchQuery, SearchResultItem, TOCItem, ContentPage)
- **S3**: ReaderCoreBridge contract + FakeCoreBridge + OkHttp 4.12 adapter + DataStore BookSourceRepository
- **S4**: Book source management (list, enable/disable, delete)
- **S5**: Search → Detail → TOC → Reader full flow (fake + real HTTP dual mode)
- **S6**: DataStore reading prefs + Room database (progress, chapter cache)

## Architecture

Single `:app` module, internal package layering:
- `ui/` — Compose screens (bookshelf, booksource, search, detail, toc, reader, settings)
- `data/model/` — Kotlin DTOs matching Reader-Core public contract
- `data/bridge/` — CoreBridge interface + error taxonomy
- `data/repository/` — BookSourceRepository (fake + DataStore)
- `data/network/` — OkHttp client + HTML parsers (Search, BookInfo, TOC, Content)
- `data/storage/` — ThemePreferences (DataStore) + AppDatabase (Room v2)

## Blocked stages

| Stage | Blocker |
|-------|---------|
| S7 WebView/JS | BD-009 (JS engine choice) |
| S7 Cookie/Login | BD-016 (credential strategy) |
| S9 TXT/EPUB | BD-011 (EPUB lib), BD-014 (file permissions) |
| S11 WebDAV | BD-010 (client library) |
