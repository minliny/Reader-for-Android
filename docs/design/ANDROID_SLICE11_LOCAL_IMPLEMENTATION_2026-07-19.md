# Android Slice 11 local implementation — 2026-07-19

## Status

`LOCAL_IMPLEMENTATION_COMPLETE / FORMAL_SLICE_BLOCKED`

The Android-local work that can be implemented against frozen Core methods is
present and testable. Slice 11 is not `passed`: profile-isolated WebView,
challenge continuation, protected media descriptors, common source corpus,
physical-device evidence, and the platform evidence manifest remain external
gates.

## Core-only business-data boundary

- `CoreSlice11Service` is the Android bridge for Core `source.*`,
  `rule-sub.*`, `rss.subscription.*`, `rss.subscription.items`, and scoped
  `rss.item.read`.
- Production `ReaderCoreClient` no longer registers the legacy
  `SourceRssContext` Room/DataStore handlers. The in-memory registration exists
  only for the old pure-JVM compatibility tests.
- A Core failure returns `CORE_UNAVAILABLE`, `CORE_REJECTED`, or
  `CORE_PROTOCOL_MISMATCH`; it cannot write/read a second Android RSS/source
  store.
- RSS main-tab loading is all-or-nothing. Subscription refresh, cached item
  loading, error, and empty states use one Core snapshot; no demo or Room row
  fills a partial page.
- RSS subscription management, rule-subscription list, and settings source
  list now load Core summaries instead of `example.com`/fixture rows.
- Source enabled-state stays read-only because Core has no frozen partial
  enabled mutation; Android does not fake the change locally.

## Security boundary

- Source/RuleSub/RSS input URLs accept only HTTP(S), require a host, and reject
  URL user-info credentials.
- Display projections strip queries/fragments so signed URLs are not rendered
  or logged.
- Source export and RSS article links use redacted wrapper types; raw bytes or
  the original link are exposed only at the explicit file/share/browser edge.
- Existing Android WebView Host handlers continue to reject non-empty
  `profileId`. Anti-bot detection remains detection-only; no continuation is
  invented.

## Executable admission matrix

`AndroidSlice11CapabilityMatrix` admits Core source CRUD/check, RuleSub CRUD,
RSS subscriptions/items, basic WebView, and anti-bot detection. It explicitly
blocks:

- DSL version/compatibility set and source-range diagnostics;
- isolated WebView profile/cookie sessions;
- success/cancel/timeout challenge continuation and replay handles;
- credential/profile-bound protected download plus offline integrity result;
- physical-device corpus and recordings.

## Local verification

- `CoreSlice11ServiceTest`: Core ownership, exact method/DTO mapping, source
  check, safe URL projections, RuleSub/RSS CRUD, scoped read state, malformed
  result handling, and blocked capability states.
- The Compose integrations compile as part of `:app:compileDebugKotlin`.
- Formal evidence still requires the Slice 11 files named by the canonical
  `Reader-UI/contracts/SLICE_PLAN.md` on a physical Android device.

No consumer lock, release manifest, package lock, Android manifest, or Git
commit is changed by this local slice.
