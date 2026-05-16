# Reader for Android Non-UI RC1 Freeze

**Date**: 2026-05-16
**Commit**: 4417bc7

## 1. 总体结论

**状态：NON_UI_RC1_READY**

所有 136 个 `ui_required=no` 能力完成。71/71 队列任务 DONE。331 tests, 0 failures。21 个 UI-only gap 已记录。

## 2. Scope

### 本 RC 覆盖
- Backend models, DTOs, error taxonomy
- Adapters (WebView, JS, TTS, WebDAV, RSS, local book)
- Parsers (HTML search/detail/TOC/content, RSS XML, WebDAV XML, TXT, EPUB OPF)
- Storage (Room v3 with 3 entities, DataStore preferences)
- Sync (progress sync protocol, conflict resolver, sync manager)
- Cache (chapter cache with TTL, download cache, offline availability)
- Contracts (CoreBridge, WebRuntimeAdapter, TtsEngine, WebDavClient, etc.)
- Test infrastructure (64 test files, 331 tests)
- Regression fixture registry
- Capability matrix model
- Release gate documentation

### 本 RC 不覆盖
- Compose UI 精装/打磨
- UI 页面 (Bookshelf/Explore/FilePicker/TTS controls/WebDAV settings/Login)
- UI 操作动线
- 任何 UI 视觉/布局/交互改进
- Android instrumentation tests (require emulator)
- Encrypted credential storage
- Third-party EPUB library

## 3. Metrics

| Metric | Value |
|--------|-------|
| Commits | 112 |
| Main Kotlin files | 72 |
| Test files | 64 |
| Docs | 22 |
| Tests | 331 |
| Failures | 0 |
| Queue tasks DONE | 71/71 |
| Non-UI capabilities covered | 136/136 |
| `./gradlew test` | PASS |
| `./gradlew :app:compileDebugKotlin` | PASS |
| `./gradlew :app:assembleDebug` | PASS |

## 4. Completed Capability Summary

| Stage | Tasks | Capabilities | Status |
|-------|-------|-------------|--------|
| S0 | 4 | Planning & infra | DONE |
| S1 | 5 | Gradle + Compose shell | DONE |
| S2 | 3 | Domain models (6 DTOs) | DONE |
| S3 | 3 | CoreBridge + OkHttp + DataStore | DONE |
| S4 | 2 | Book source CRUD | DONE |
| S5 | 5 | Search→Detail→TOC→Reader flow | DONE |
| S6 | 3 | Reader settings + Room + cache | DONE |
| S6.5 | 7 | Baseline hardening | DONE |
| S6-SET | 5 | Theme/Color/Alpha/Tap/Font config | DONE |
| S6-CACHE | 1 | Prefetch strategy | DONE |
| S7-NUI | 12 | WebView/JS/Cookie/POST backend | DONE |
| S8-NUI | 3 | RSS parser + subscription | DONE |
| S9-NUI | 10 | TXT/EPUB local book backend | DONE |
| S10-NUI | 5 | TTS engine/adapter/queue | DONE |
| S11-NUI | 7 | WebDAV client/backup/sync | DONE |
| S12-NUI | 5 | Sync manager/conflict/restore | DONE |
| S13-NUI | 5 | Remote content/cache/listing | DONE |
| S14-NUI | 3 | Capability matrix/fixture/gate | DONE |
| S15-NUI | 5 | RC gate/audit/parity/gap list | DONE |

## 5. Verification

```
git diff --check          → OK
./gradlew test            → BUILD SUCCESSFUL, 331 tests, 0 failures
./gradlew :app:compileDebugKotlin → BUILD SUCCESSFUL
./gradlew :app:assembleDebug      → BUILD SUCCESSFUL
```

## 6. Remaining UI-only Gaps

21 UI-only gaps listed in `docs/PLANNING/ANDROID_UI_ONLY_GAP_LIST.md`. All backend contracts, models, and adapters for these UI features are complete. No backend work remains.

## 7. Risks

| Risk | Severity | Mitigation |
|------|----------|------------|
| UI not yet integrated with all backends | P2 | Handoff doc generated |
| No Android instrumentation tests | P2 | All tests are pure JUnit, no emulator needed |
| Real WebView/TTS/WebDAV untested on device | P2 | Contracts + fake adapters verified |
| EPUB library not selected | P2 | ZIP/XML parsing uses built-in JDK |
| Encrypted storage deferred | P2 | CookieStore uses DataStore without encryption |

## 8. Tag Recommendation

```
git tag -a android-non-ui-rc1 -m "Reader for Android Non-UI RC1 — 112 commits, 331 tests, 71/71 tasks"
```
