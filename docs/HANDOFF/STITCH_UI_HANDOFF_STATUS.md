# Stitch UI Handoff Status

**Date**: 2026-05-20
**Stage**: STITCH_UI_FIX_S1_CORE_FLOW_COMPLETION
**Source audit**: `docs/stitch-ui-audit-report.md`
**Stitch project**: `14245818599411324705` — "Markdown Design System"

## Overall Status

**Current conclusion: `UI_HANDOFF_NOT_READY`**

The current Stitch project is a useful partial reference for the Android visual direction, but it is not a complete Android UI handoff package. Android should not enter full UI implementation from this Stitch state because the core acquisition and management flows are not designed end to end.

This status file is a handoff gate, not an implementation plan for Compose screens. It records what can be referenced, what blocks development, and what Stitch must complete before Android full UI implementation starts.

## Existing Handoff Documents Checked

| Path | Finding |
|------|---------|
| `docs/HANDOFF/ANDROID_EXTERNAL_UI_TOOL_HANDOFF.md` | Existing external UI boundary document. It states UI pages are owned by external UI tools and local Android work should not design UI pages. |
| `docs/HANDOFF/ANDROID_UI_HANDOFF_FROM_NON_UI_RC1.md` | Existing backend-to-UI capability handoff. It documents complete non-UI contracts and UI-only gaps. |
| `docs/PLANNING/ANDROID_UI_ONLY_AUTODEV_QUEUE.md` | Deprecated queue retained only for handoff reference. It must not be consumed by an Android UI loop. |
| `docs/PLANNING/ANDROID_UI_ONLY_GAP_LIST.md` | Existing UI-only capability gap list, independent of Stitch visual coverage. |

This Stitch status document narrows those existing handoff boundaries using the 2026-05-20 Stitch audit result.

## Locally Referenceable Stitch Scope

Android may use the following areas only as local visual references after manual screen/version selection:

| Scope | Handoff Use | Limits |
|-------|-------------|--------|
| Bookshelf | Partial visual direction for bookshelf cover/list layouts, empty state, grouping, more menu, local import, download/cache management. | Loading/error states and search entry flow are missing. Final screen versions still need explicit marking. |
| Reader page | Partial reference for immersive reading, reading body spacing, control layer direction, and typography mood. | Reading screens contain conflicts in corner metadata, bottom toolbar content, title format, chapter navigation, brightness placement, and sample content. |
| Reader sheets/popups | Partial reference for reader search sheet, auto-page sheet, typography/settings sheet, catalog/bookmark sheet, and TTS sheet. | Sheet consistency, overlay behavior, token usage, and shared toolbar rules are not yet verified. |

## Development-Blocking Scope

The following areas block full Android UI implementation:

| Scope | Blocking Reason |
|-------|-----------------|
| Book detail | Missing bridge page between search/bookshelf and reading. No cover, metadata, synopsis, latest chapter, add-to-bookshelf, start-reading, catalog, source switch, loading, or error design. |
| Search full flow | Missing search input, history, results, multi-source grouping, loading, empty, source failure, clear search, and result-to-detail transition. |
| Source management | Missing source list, enable/disable states, import by JSON/URL/local file, edit, delete, validation/test, success/failure feedback, empty, and error states. |
| Settings page | Missing bottom-tab settings home and subpages for reading preferences, theme, source, sync, about/help/feedback. |
| Discover/RSS | Navigation flow names Discover/RSS tabs, but no matching designed pages exist. |
| Dark mode | Stitch tokens define only LIGHT mode. No DARK tokens or reader night-mode screens are ready. |
| Global state | Missing shared loading, error, empty, disabled, overlay, divider, skeleton, and retry patterns. |

## Android Implementation Gate

Android currently should not enter complete UI implementation.

Allowed Android-side activity:

- Read this handoff status and `docs/stitch-ui-audit-report.md`.
- Reference bookshelf, reader page, and reader sheet visuals only as partial design input.
- Validate future external UI artifacts against existing ViewModel/backend contracts.
- Record integration gaps without creating new Android UI surfaces.

Disallowed Android-side activity:

- Do not create Android Compose pages from the incomplete Stitch project.
- Do not treat current Stitch screens as complete app UI handoff.
- Do not fill missing screens by inventing Android UI in `app/src`.
- Do not start a full Android UI implementation loop until this status changes from `UI_HANDOFF_NOT_READY`.

## Required Stitch Exit Criteria

The handoff status may change only after these conditions are met:

1. The screen coverage matrix is updated and identifies the final Stitch screen for every core route.
2. Book detail, search full flow, source management, settings, Discover/RSS, WebDAV entry points, dark mode, and global states are designed.
3. Reader page conflicts are resolved with one baseline template for corners, title, bottom toolbar, brightness, chapter navigation, and sample content.
4. Design tokens include DARK mode plus disabled, overlay, divider, loading/skeleton, error, empty, and selected/pressed semantics.
5. Every UI-FIX task in `docs/PLANNING/STITCH_UI_FIX_QUEUE.md` reaches Android handoff acceptance.

## Linked Queue

The active Stitch completion queue is:

- `docs/PLANNING/STITCH_UI_FIX_QUEUE.md`

