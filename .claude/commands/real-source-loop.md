---
description: Reader for Android Real Source Closure loop — S16-NUI tasks, authorized real network, one task per cycle
---

# Reader for Android Real Source Closure Loop

This is the command file for `/loop 10m /real-source-loop`.

## Mission

Execute the S16-NUI real source closure sequence:
1. RSC-002: BookSource import/enable verification
2. Gap-1: AdapterShell network gate fix
3. RSC-003: Search smoke (real network, authorized)
4. RSC-004: Search fixture capture
5. RSC-005: Detail fixture capture
6. RSC-006: TOC fixture capture
7. RSC-007: Content fixture capture
8. RSC-008: Offline replay tests
9. RSC-009: Error model verification
10. RSC-010: Docs update

**Network Policy**: Real network access is fully authorized for this loop. First smoke MUST save fixtures. Subsequent replays use fixtures only.

## Hard Rules

1. Work only inside: `/Users/minliny/Documents/Reader for Android`
2. Do NOT push to remote. Local commits only.
3. One small task per iteration.
4. Validate before commit. No validation pass = no commit.
5. NEVER push.
6. If a task fails validation, mark BLOCKED, record reason, stop.
7. If no READY task, call CronDelete to cancel loop, output BLOCKED_COMPLETE.
8. Save every successful real-network response as a fixture before moving on.
9. Do not repeat real-network requests for the same URL within 5 minutes (use cached fixture).

## Preflight

Every iteration:
```
cd "/Users/minliny/Documents/Reader for Android"
pwd && git branch --show-current && git status --short && git log --oneline -n 3
./gradlew test
```
If baseline red: STOP. Report BLOCKED_ENVIRONMENT.

## Task Selection

1. Read `docs/PLANNING/ANDROID_AUTODEV_QUEUE.md`.
2. Read `docs/PLANNING/ANDROID_REAL_SOURCE_TASK_QUEUE.md`.
3. Pick first S16-NUI task with Status == READY.
4. Sequential order: S16-NUI-P0-001 → 002 → 003 → 004 → ... → 010.
5. Skip if blocked by unresolved dependency.

## Forbidden Changes

- Compose UI (pages, buttons, layouts, visual styles)
- Reader-Core modifications
- Network-based test fixtures in production code (fixtures OK in test resources)
- git reset --hard / git clean -fd / force push
- Deleting existing tests or user files
- Committing local.properties, .DS_Store, build/, .gradle/, app/build/

## Iteration Steps

### 1. Read State
```
git status --short
cat docs/PLANNING/ANDROID_AUTODEV_QUEUE.md | grep -A2 "S16-NUI"
```

### 2. Select First READY Task
- Mark IN_PROGRESS in queue file
- Read relevant source files before editing

### 3. Execute
- Make the smallest useful change
- Real network task (S16-NUI-P0-003): save response to fixture file immediately

### 4. Validate
```
git diff --check
./gradlew test
```
- PASS → Update task to DONE, commit with `[S16-NUI-P0-XXX] description`
- FAIL → Mark BLOCKED, revert unsafe changes, NO commit

### 5. Report
```
## Real Source Loop Iteration
- HEAD: <hash>
- Task: S16-NUI-P0-XXX — <description>
- Changes: <summary>
- Validation: PASS/FAIL
- Commit: <hash or N/A>
- Next READY: S16-NUI-P0-XXX or NONE
- Status: LOOP_OK / BLOCKED_COMPLETE / BLOCKED_ENV
```

## End Conditions

- All S16-NUI-P0-001~010 DONE → output COMPLETE, call CronDelete
- No READY tasks but not all DONE → output BLOCKED_COMPLETE
- Test failure that cannot be fixed → output BLOCKED_ENV

## Fixture Naming Convention

```
app/src/test/resources/fixtures/real-source/<source-id>/
├── manifest.json
├── search/query_<keyword>.html
├── detail/<book-id>.html
├── toc/<book-id>.html
└── content/<chapter-hash>.html
```

## Completion Criteria

All of:
- S16-NUI-P0-001 through S16-NUI-P0-010 all DONE
- `./gradlew test` passes
- `docs/PLANNING/ANDROID_REAL_SOURCE_CLOSURE_PLAN.md` status updated to CLOSED
- Local commits exist for all 10 tasks
- No uncommitted changes

---

*Canonical loop command for real source closure. Do not edit while loop is running.*
