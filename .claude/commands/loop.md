# Reader for Android Non-UI Development Loop

## Mission

Complete all Reader for Android non-UI backend capabilities. Target: `docs/PLANNING/ANDROID_NON_UI_COMPLETION_TARGET.md` — 136 capabilities, all `ui_required=no` items DONE.

## Preflight

Every iteration:
```
cd "/Users/minliny/Documents/Reader for Android"
pwd && git branch --show-current && git status --short && git log --oneline -n 5
./gradlew test && ./gradlew :app:assembleDebug
```

If baseline red: **STOP**. Do not proceed.

## Task Selection

1. Read `docs/PLANNING/ANDROID_AUTODEV_QUEUE.md`.
2. Pick first task with Status == READY.
3. Stage priority: S6.5 → S6-SETTINGS-NUI → S6-CACHE-NUI → S7-NUI → S8-NUI → S9-NUI → S10-NUI → S11-NUI → S12-NUI → S13-NUI → S14-NUI → S15-NUI.
4. If no READY, scan TODO tasks in stage order; promote first dependency-satisfied one to READY.
5. **Never select a UI task.** All queue tasks are non-UI by construction.
6. S15-NUI-P0-004 and S15-NUI-P0-005 are the final exit gates.

## Forbidden Changes

- Compose UI (pages, buttons, layouts, visual styles, navigation appearance)
- Reader-Core modifications
- Network-based test fixtures (real website HTML)
- Real external services in tests
- New large dependencies without explicit approval
- git reset --hard / git clean -fd / force push
- cron configuration
- Deleting existing tests or user files
- Committing local.properties, .DS_Store, build/, .gradle/, app/build/

## Development Rules

- One small task per iteration.
- New production code must have tests.
- External service capability: interface + fake adapter first.
- Platform capability: contract + fake implementation first.
- Update queue after every iteration.
- Commit after validation. Never auto-push.

## Validation

```
git diff --check
./gradlew test
./gradlew :app:assembleDebug
```

## Completion Condition

All of:
- `ANDROID_NON_UI_COMPLETION_TARGET.md` — all 136 capabilities with `ui_required=no` are DONE
- `ANDROID_AUTODEV_QUEUE.md` — all NUI tasks DONE
- `./gradlew test` — PASS (≥ 100 tests)
- `./gradlew :app:assembleDebug` — PASS
- `S15-NUI-P0-004` — no-UI parity declaration generated
- `S15-NUI-P0-005` — UI-only gap list generated

## End Conditions (per iteration)

- Current task DONE and committed, or
- Test/assembleDebug failure cannot be fixed in scope, or
- Task requires UI work → mark BLOCKED_BY_UI_SCOPE, or
- Task requires new large dependency → mark BLOCKED, record justification, or
- Git risk detected, or
- No READY non-UI tasks → report COMPLETE or BLOCKED_NEEDS_PLAN

## Report Format

```
# Reader for Android Non-UI Loop Report
## 1. Status
## 2. Task
## 3. Changes
## 4. Non-UI Compliance
## 5. Validation
## 6. Queue Update
## 7. Commit
## 8. Next
```
