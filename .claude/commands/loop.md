# Reader for Android Non-UI Development Loop

## Mission

Develop Reader for Android non-UI backend: parser, bridge, repository, storage, adapter, sync, cache, contract, test, fixture, matrix. No UI frontend work.

## Preflight

Every iteration:
```
cd "/Users/minliny/Documents/Reader for Android"
pwd
git branch --show-current
git status --short
git log --oneline -n 5
./gradlew test
./gradlew :app:assembleDebug
```

If baseline red (test or assembleDebug fails), **STOP**. Do not proceed.

## Task Selection

1. Read `docs/PLANNING/ANDROID_AUTODEV_QUEUE.md`.
2. Pick first task with Status == READY.
3. If no READY, scan TODO tasks in stage order and promote the next dependency-satisfied one to READY.
4. Stage priority: S6.5 → S7-NUI → S8-NUI → S9-NUI → S10-NUI → S11-NUI → S12-NUI → S13-NUI → S14-NUI → S15-NUI.
5. **Never select a UI task.** If a task involves Compose UI, skip it and mark BLOCKED_BY_UI_SCOPE.
6. If zero eligible tasks, report BLOCKED_NEEDS_PLAN and stop.

## Forbidden Changes

- Compose UI / new pages / buttons / layouts / visual styles
- UI navigation visual changes
- Reader-Core modifications
- Network-based test fixtures
- Real website HTML as fixture
- New large dependencies without plan approval
- git reset --hard / git clean -fd / force push
- cron configuration
- Deleting user files or existing tests
- Committing local.properties, .DS_Store, build/, .gradle/, app/build/

## Development Rules

- One small task per iteration.
- New production code must have corresponding tests.
- External service: use fake transport first.
- Platform capability: use interface + fake adapter first.
- Update queue after every iteration.
- Commit after successful validation.
- Never auto-push.

## Validation

After each task:
```
git diff --check
./gradlew test
./gradlew :app:assembleDebug
```

## End Conditions

Stop when:
- Current task DONE and committed
- Test/assembleDebug fails and cannot be fixed within task scope
- Task requires UI work
- Task requires user product decision (not covered by non-UI defaults)
- Large new dependency needed
- Git risk detected
- No READY non-UI tasks

## Report Format

```
# Reader for Android Non-UI Loop Report

## 1. Status
[DONE / PARTIAL / BLOCKED_BY_TEST / BLOCKED_BY_BUILD / BLOCKED_BY_UI_SCOPE / BLOCKED_BY_DECISION / BLOCKED_BY_GIT / FAILED]

## 2. Task
- ID:
- Stage:
- Goal:

## 3. Changes
[file list + summary]

## 4. Non-UI Compliance
[confirm no UI work]

## 5. Validation
- git diff --check:
- ./gradlew test:
- ./gradlew :app:assembleDebug:

## 6. Queue Update
- DONE: [this task]
- Next READY: [next task ID]

## 7. Commit
- hash:
- message:
- push: NO

## 8. Next
[one next task]
```
