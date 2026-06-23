# Stitch UI Fix Queue

**Date**: 2026-05-20
**Stage**: STITCH_UI_FIX_S1_CORE_FLOW_COMPLETION
**Source audit**: `docs/stitch-ui-audit-report.md`
**Current handoff status**: `UI_HANDOFF_NOT_READY`

## Queue Rules

- This queue is for Stitch design completion only.
- Do not create Android Compose pages from these tasks.
- Do not modify `app/src` business code for these tasks.
- A task is complete only when both Stitch design acceptance and Android handoff acceptance are met.
- Until this queue is accepted, Android full UI implementation remains blocked.

## Current Partial Reference Scope

| Scope | Status |
|-------|--------|
| Bookshelf | Partially referenceable: cover/list, group management, empty state, more menu, local import, download/cache management. |
| Reader page | Partially referenceable: immersive reading and control-layer direction, but baseline conflicts must be resolved. |
| Reader sheets/popups | Partially referenceable: search, catalog/bookmark, auto-page, typography/settings, TTS sheets. |

## Blocked Android Scope

Book detail, search full flow, source management, settings page, Discover/RSS, WebDAV/sync entry, dark mode, and global state patterns are blocked by missing or incomplete Stitch design.

## Task Queue

### UI-FIX-001 Screen Coverage Matrix

**Status**: TODO

**Missing reason**

The Stitch audit found 26 unique screens, 100+ screen instances, multiple hidden/legacy versions, and no authoritative final-screen mapping. Android cannot tell which screen is the source of truth for each route or state.

**Screens to complete**

- App route matrix covering bookshelf, search, book detail, source management, reader, reader sheets, settings, Discover, RSS, WebDAV/sync, local import, TTS, and global states.
- Final screen selector table that maps each route/state to one Stitch screen ID and title.
- Deprecated/hidden screen inventory.
- Navigation flow coverage for the existing bottom tabs: Bookshelf, Discover, RSS, Settings.
- Reader baseline template matrix for corner metadata, title, bottom toolbar, brightness, and chapter navigation.

**Development blocking impact**

Without this matrix, Android implementation risks using old or conflicting screens, especially in reader controls and sheets. It also prevents reliable acceptance of later UI-FIX tasks.

**Stitch design acceptance**

- Every intended route/state has one final screen or an explicit `MISSING` marker.
- Old variants are marked deprecated, hidden, or excluded from handoff.
- The matrix identifies partial-reference screens separately from full-handoff screens.
- Reader baseline conflicts from the audit are resolved into one canonical template.

**Android handoff acceptance**

- The handoff package lists exact Stitch screen IDs/titles for each Android route.
- Missing routes are explicitly marked as blockers.
- No Android implementer needs to infer final versions from naming suffixes such as V2/V3/V10/final/fixed.
- `docs/HANDOFF/STITCH_UI_HANDOFF_STATUS.md` can be updated from the matrix without ambiguity.

### UI-FIX-002 Book Detail Page

**Status**: TODO

**Missing reason**

Book detail is entirely absent, but it is the required bridge between search/bookshelf and reading. The current flow breaks at search result to detail and detail to reading/catalog.

**Screens to complete**

- Book detail loading state.
- Book detail normal state with cover, title, author, category/source metadata, status, latest chapter, synopsis, and update time.
- Book detail actions: add/remove bookshelf, start/continue reading, catalog, source switch.
- Book detail error state with retry.
- Book detail empty/unavailable state for removed or invalid source content.
- Source switch or source result selector entry from detail.

**Development blocking impact**

Android cannot close the main reading acquisition path without this page. Search results, bookshelf entries, catalog navigation, source switching, and start-reading actions lack visual and interaction authority.

**Stitch design acceptance**

- Normal/loading/error/empty states are designed at mobile 390px baseline.
- Primary and secondary actions are visually prioritized.
- Detail-to-reading and detail-to-catalog transitions are clear.
- Source identity and source-switch affordance are visible.
- Designs use shared global tokens for divider, overlay, disabled, and loading states once UI-FIX-007 is complete.

**Android handoff acceptance**

- Screen IDs and route/state names are listed in the coverage matrix.
- Each action maps to existing backend/UI contracts without requiring new backend behavior.
- Android receives clear empty/loading/error rules and disabled button states.
- Handoff confirms no Compose implementation is expected before this design is accepted.

### UI-FIX-003 Search Full Flow

**Status**: TODO

**Missing reason**

Search is completely missing even though it is the user's primary path for acquiring new books. The bookshelf has a search icon, but no search input, results, or result-to-detail designs exist.

**Screens to complete**

- Search input initial state.
- Search history and suggestions.
- Search loading state.
- Search results grouped by source or source status.
- Multi-source partial failure state.
- Empty result state.
- Search error state with retry.
- Clear-search confirmation or inline clear state.
- Result card pressed/selected state.
- Search result to book detail transition target.

**Development blocking impact**

Android cannot implement the new-book acquisition flow, multi-source result display, or failure recovery. Any Android-created search UI would be invented outside Stitch handoff.

**Stitch design acceptance**

- Search field behavior, history, clearing, and result states are visually specified.
- Multi-source success, partial failure, and all-failed states are distinguishable.
- Result cards include title, author, source, latest chapter or summary, and status markers.
- Loading, empty, error, disabled, and divider patterns align with UI-FIX-007.

**Android handoff acceptance**

- Each search state maps to existing `SearchViewModel` result/loading/error concepts or is documented as a contract gap.
- Result-to-detail navigation is explicit.
- The handoff names minimum result-card fields and fallback behavior for missing metadata.
- Android is not asked to design extra search UI outside the accepted Stitch screens.

### UI-FIX-004 Source Management

**Status**: TODO

**Missing reason**

Source management is a core Reader feature and is entirely absent from Stitch. There are no visuals for source list, import, edit, enable/disable, validation, or error handling.

**Screens to complete**

- Source list normal state.
- Source list empty state.
- Source list loading/error state.
- Source enable/disable state.
- Source detail/edit form.
- Source delete confirmation.
- Source test/validation in progress, success, warning, and failure states.
- Import source entry screen.
- Import by JSON text.
- Import by URL.
- Import by local file.
- Import success and failure summaries.

**Development blocking impact**

Android cannot expose the completed source backend safely. Without design, users cannot add or maintain sources, and search/detail/content flows cannot be managed in a Reader-specific way.

**Stitch design acceptance**

- Source status, validation result, enabled state, and failure details have consistent visual markers.
- Import paths are clearly separated and recoverable.
- Destructive actions use confirmation and disabled states.
- Long URLs/names are handled without text overflow.
- Empty/error/loading states reuse the global state system from UI-FIX-007.

**Android handoff acceptance**

- Actions map to existing source repository and validation contracts.
- Import failure copy and retry paths are explicit.
- Edit/delete/test interactions are represented as route or modal states.
- No Android-side source-management Compose page is created before handoff acceptance.

### UI-FIX-005 Settings / Discover / RSS / WebDAV Entry

**Status**: TODO

**Missing reason**

The Stitch navigation flow defines bottom tabs for Bookshelf, Discover, RSS, and Settings, but Discover/RSS/Settings pages are not designed. WebDAV/sync also lacks any entry point despite backend capability.

**Screens to complete**

- Settings home.
- Reading settings entry and summary.
- Theme/night mode settings entry.
- Source management entry.
- Local import entry.
- WebDAV/sync entry.
- Backup/restore/sync status entry.
- About/help/feedback entry.
- Discover home.
- Discover category/list state.
- RSS subscription list.
- RSS empty state.
- RSS add subscription state.
- RSS update/error state.
- WebDAV configuration entry screen or settings subpage.

**Development blocking impact**

Android cannot complete bottom navigation, settings navigation, subscription management, or sync discoverability. The app would ship with tabs/routes that have no visual handoff.

**Stitch design acceptance**

- Bottom navigation destinations have real target screens, not just a flow diagram.
- Settings hierarchy and entry priorities are clear.
- RSS and Discover are visually distinct if they remain separate tabs.
- WebDAV/sync has a discoverable settings entry and status surface.
- Empty/error/loading states reuse UI-FIX-007.

**Android handoff acceptance**

- Each bottom tab maps to an accepted screen ID.
- Settings entries map to existing backend capability groups or are marked as future-only.
- WebDAV entry does not imply new backend work beyond existing sync/backup contracts.
- Android receives enough hierarchy to wire navigation without inventing page structure.

### UI-FIX-006 Dark Mode / Reader Night Mode

**Status**: TODO

**Missing reason**

Stitch currently defines only LIGHT mode tokens. A reading app requires dark mode and reader night-mode visuals, but no DARK tokens or night reader screens are ready.

**Screens to complete**

- Global dark-mode token set.
- Bookshelf dark mode sample.
- Reader immersive night mode.
- Reader control layer night mode.
- Reader typography/settings sheet night mode.
- Reader catalog/bookmark sheet night mode.
- Search or detail dark-mode sample.
- Settings/theme dark-mode sample.

**Development blocking impact**

Android cannot implement reliable app dark mode or reader night mode from current Stitch output. Any Android implementation would invent colors, overlays, disabled states, and contrast behavior.

**Stitch design acceptance**

- DARK tokens exist for background, surface, primary, secondary, tertiary, error, on-* colors, outline/divider, overlay, disabled, and selected states.
- Reader night mode specifies text/background contrast, corner metadata, toolbar, sheet, and overlay colors.
- Day/night switching does not change layout hierarchy.
- Token names are stable enough for Android mapping.

**Android handoff acceptance**

- Token mapping can be translated to Material/Compose theme values.
- Reader night mode has accepted screens for base page and active controls.
- Contrast-sensitive elements such as body text, disabled actions, dividers, and overlays are specified.
- Handoff explicitly states whether system dark mode and reader night mode share tokens or differ.

### UI-FIX-007 Global Loading/Error/Empty/Disabled/Overlay/Divider Token

**Status**: TODO

**Missing reason**

The audit found missing global patterns for loading, error, empty, disabled, overlay, divider, and skeleton states. These states appear across bookshelf, search, detail, source management, settings, RSS, WebDAV, and reader loading/failure.

**Screens to complete**

- Global loading skeleton pattern.
- Full-page loading state.
- Inline loading row/card state.
- Empty state pattern.
- Recoverable error state with retry.
- Blocking error state.
- Disabled button/list-item/input states.
- Overlay/scrim token example for sheets/dialogs.
- Divider/outline token example for lists and cards.
- Reader chapter loading state.
- Reader chapter load-failure state.

**Development blocking impact**

Without shared state patterns, Android would implement inconsistent loading/error/empty/disabled behavior across modules. It also blocks safe acceptance of all missing P0/P1 screens.

**Stitch design acceptance**

- State patterns are reusable and shown in at least bookshelf, search/detail, source management, and reader contexts.
- Token names cover disabled text/action/container, overlay/scrim, divider/outline, loading/skeleton, error container, and empty illustration/icon color.
- Dialogs, bottom sheets, and full-page states use consistent overlay and elevation treatment.
- Designs account for long text and small-screen 390px layout.

**Android handoff acceptance**

- State tokens can be mapped to Android theme values or component parameters.
- Each major screen family has accepted loading/error/empty/disabled behavior.
- Retry, cancel, close, and disabled action semantics are documented.
- Android handoff can reuse these patterns without inventing per-screen state UI.

## Recommended Execution Order

1. `UI-FIX-001` Screen Coverage Matrix.
2. `UI-FIX-002` Book Detail Page.
3. `UI-FIX-003` Search Full Flow.
4. `UI-FIX-004` Source Management.
5. `UI-FIX-007` Global State Tokens.
6. `UI-FIX-006` Dark Mode / Reader Night Mode.
7. `UI-FIX-005` Settings / Discover / RSS / WebDAV Entry.

The next Stitch page to design should be the book detail normal/loading/error set because it is the shortest path to reconnect bookshelf/search with reading.

