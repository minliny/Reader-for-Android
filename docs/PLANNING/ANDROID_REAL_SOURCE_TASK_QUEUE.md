# Android Real Source Task Queue (S16-FIXTURE)

**Date**: 2026-05-29
**Status**: `ANDROID_REAL_SOURCE_S16_OFFLINE_CLOSURE_READY`

---

## Overview

All automated real-source smoke attempts exhausted. Plan switched to **manual browser capture** mode. Fixtures captured, offline replay verified.

**Current state**: OFFLINE_REPLAY_READY — biquges123.com fixture replay all pass

---

## Task Queue: S16-FIXTURE

| ID | Stage | Status | Task | Allowed Changes | Validation | Blockers |
|----|-------|--------|------|-----------------|------------|----------|
| S16-FIXTURE-001 | S16-FIXTURE | DONE | Generate fixture capture guide | docs/PLANNING/ | Guide exists | None |
| S16-FIXTURE-002 | S16-FIXTURE | DONE | Define fixture manifest schema | docs/PLANNING/ | Schema defined | None |
| S16-FIXTURE-003 | S16-FIXTURE | DONE | Create fixture completeness validator | app/src/test/ | Validator compiles | None |
| S16-FIXTURE-004 | S16-FIXTURE | DONE | User provides Search/Detail/TOC/Content HTML | None (user action) | biquges123.com fixture captured | None |
| S16-FIXTURE-005 | S16-FIXTURE | DONE | Integrate FixtureHttpTransport with captured HTML | app/src/test/ | Biquges123OfflineReplayTest | S16-FIXTURE-004 |
| S16-FIXTURE-006 | S16-FIXTURE | DONE | Run Search replay end-to-end | app/src/test/ | Search replay PASS | S16-FIXTURE-005 |
| S16-FIXTURE-007 | S16-FIXTURE | DONE | Run Detail replay end-to-end | app/src/test/ | Detail replay PASS | S16-FIXTURE-006 |
| S16-FIXTURE-008 | S16-FIXTURE | DONE | Run TOC replay end-to-end | app/src/test/ | TOC replay PASS | S16-FIXTURE-007 |
| S16-FIXTURE-009 | S16-FIXTURE | DONE | Run Content replay end-to-end | app/src/test/ | Content replay PASS | S16-FIXTURE-008 |
| S16-FIXTURE-010 | S16-FIXTURE | DONE | Generate real source closure report | docs/PLANNING/ | Closure report exists | S16-FIXTURE-009 |

---

## Fixture Directory Structure

```
app/src/test/resources/fixtures/real-source/
└── <source-id>/              e.g., xingxingxsw/
    ├── manifest.json           # Metadata (required)
    ├── search/
    │   └── search.html         # Search results page (required)
    ├── detail/
    │   └── detail.html         # Book detail page (required)
    ├── toc/
    │   └── toc.html            # Table of contents (required)
    └── content/
        ├── chapter-001.html    # First chapter (required)
        └── chapter-002.html    # Second chapter (optional)
```

---

## Manifest Schema

See `ANDROID_REAL_SOURCE_FIXTURE_CAPTURE_GUIDE.md` §7.

```json
{
  "sourceId": "xingxingxsw",
  "sourceName": "星星小说网",
  "captureMode": "manual-browser",
  "capturedAt": "2026-05-28T00:00:00Z",
  "query": "斗破苍穹",
  "searchUrl": "https://www.xingxingxsw.com/search.php?key=斗破苍穹",
  "detailUrl": "https://www.xingxingxsw.com/xiaoshuo/12345.html",
  "tocUrl": "https://www.xingxingxsw.com/xiaoshuo/12345/",
  "contentUrl": "https://www.xingxingxsw.com/xiaoshuo/12345/1.html",
  "charset": "utf-8",
  "antiBotObserved": false,
  "notes": "Normal browser capture",
  "files": [
    "search/search.html",
    "detail/detail.html",
    "toc/toc.html",
    "content/chapter-001.html",
    "content/chapter-002.html"
  ]
}
```

---

## Validator

`FixtureCompletenessValidatorTest` — validates:
- fixtures root exists
- at least one source directory
- manifest.json present and valid
- All listed files exist and non-empty
- charset is recognized (utf-8, gbk, gb2312)
- captureMode is `manual-browser`

Does **NOT** access network.

---

## Status Summary

**S16-FIXTURE-004 through 010**: All DONE.

- **Source**: biquges123.com (新笔趣阁) — manually captured via controlled browser single pass
- **Query**: 凡人修仙传
- **Fixture path**: `app/src/test/resources/fixtures/real-source/xingxingxsw-manual/`
- **xingxingxsw.com**: 403 blocked — recorded as blocked, not used for replay
- **search.blocked.html**: residual record, excluded from replay

All 5 stages (Search/Detail/TOC/Content/Error model) replay PASS. Zero network access.

## Remaining Items

| Item | Status | Note |
|------|--------|------|
| HttpTransport.kt | UNRELATED | Independent evaluation needed, excluded from this cycle |
| Controlled online live smoke | DEFERRED | Requires explicit single-pass authorization |
| UI work | DEFERRED | Can resume independently, not in this scope |

---

## Previous S16-NUI Status

| Task | Status | Note |
|------|--------|------|
| S16-NUI-P0-001 | ✅ DONE | BookSource import/enable |
| S16-NUI-P0-002 | ✅ DONE | Network gate implemented |
| S16-NUI-P0-003 | ✅ DONE | Search smoke (parser updated) |
| S16-NUI-P0-004~007 | ❌ BLOCKED | Anti-bot / inaccessible sources |
| S16-NUI-P0-008 | ✅ DONE | Offline replay tests pass |
| S16-NUI-P0-009 | ✅ DONE | Error model tests pass |
| S16-NUI-P0-010 | ✅ DONE | Docs updated |

---

## Loop Status

**ANDROID_NON_UI_LOOP_COMPLETE** ✅ — 136/136 non-UI capabilities DONE.

**ANDROID_REAL_SOURCE_S16_OFFLINE_CLOSURE_READY** ✅ — Fixtures captured, offline replay all pass, task queue complete.