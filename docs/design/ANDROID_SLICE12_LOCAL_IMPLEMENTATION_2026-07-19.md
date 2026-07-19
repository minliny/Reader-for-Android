# Android Slice 12 local implementation — 2026-07-19

## Status

`LOCAL_RELEASE_GATE_COMPLETE / FORMAL_SLICE_BLOCKED`

Slice 12 cannot close before Slice 9–11, exact artifacts, lifecycle/settings
ownership, form-factor coverage, TalkBack/performance results, and a complete
physical-device journey are closed. Android now has a fail-closed local gate
that reports those facts instead of accepting a tag or summary.

## Local gate

`AndroidSlice12ReleaseGate` and
`scripts/verify_slice12_release_readiness.mjs` require and recompute:

- current Reader UI Git source SHA and clean-worktree state;
- SHA-256 of `UI_RELEASE_MANIFEST.json`;
- every file byte length and SHA-256 declared by that manifest;
- SHA-256 of Android `READER_UI_CONSUMER.json`;
- SHA-256 of the exact Reader Core artifact supplied by path;
- canonical-schema validation of an Android execution evidence manifest;
- matching release identity across source, manifest, Core artifact, and
  consumer lock;
- Slice 9, 10, 11, and 12 `passed` states;
- physical-device complete journey, TalkBack, performance, and rollback
  evidence.

Run the diagnostic without changing exit status:

```bash
READER_UI_ROOT=/absolute/path/to/Reader-UI \
READER_CORE_ARTIFACT=/absolute/path/to/exact/core/artifact \
ANDROID_EVIDENCE_MANIFEST=/absolute/path/to/android-execution-manifest.json \
node scripts/verify_slice12_release_readiness.mjs --report-only
```

Remove `--report-only` for the release gate; any blocker exits non-zero.

## Current explicit blockers

- Android consumer lock identifies an older Reader UI release and is not
  silently rewritten by this task.
- No exact Reader Core release artifact path was supplied.
- No canonical Android execution evidence manifest was supplied.
- Slice 9–11 still lack their required physical-device/corpus evidence.
- Complete journey, TalkBack, performance, Phone/landscape/Tablet/Fold,
  process restoration, migration, and rollback evidence are absent.
- Several settings pages still have local fixture/default state; the frozen
  Core/Host setting owner and migration result are missing.
- Complete process-death restoration and fold posture adapters are not
  implemented/proven.

## Executable admission matrix

`AndroidSlice12CapabilityMatrix` distinguishes existing Host infrastructure
(permission/system integration), locally testable reduced-motion and release
gates, partial semantics/responsive work, and the blocked settings,
lifecycle, fold, dependency, and device domains.

## Local verification

- `AndroidSlice12ReadinessTest` proves missing/stale/dirty inputs fail closed
  and only a fully recomputable physical-evidence set can be admitted.
- The release script validates the current Reader UI manifest's complete file
  list before reporting release identity.

No consumer lock, release manifest, evidence manifest, package lock, Android
manifest, or Git commit is changed by this local slice.
