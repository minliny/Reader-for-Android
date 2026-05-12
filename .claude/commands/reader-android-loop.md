# Reader for Android Cron Loop Command

This is the command file for `/loop 10m /reader-android-loop`.

---

## Hard rules

1. Work only inside this repository: `/Users/minliny/Documents/Reader for Android`
2. Do NOT push to remote. Local commits only.
3. Do NOT copy Legado source code.
4. Do NOT rewrite Reader-Core internals in Kotlin.
5. Only access Reader-Core via public API/Facade/DTO/Error Taxonomy.
6. If Core public API is insufficient, record gap — NEVER bypass boundary.
7. One task per iteration maximum.
8. Tasks needing user decisions are skipped (marked BLOCKED).
9. No READY tasks → auto-cancel the loop timer via CronDelete, then output BLOCKED_NEEDS_USER.
10. Validate before commit. No validation pass = no commit.
11. NEVER push.
12. Default: no network access.
13. Default: no new dependencies without design doc approval.
14. Never read .env or commit secrets.

---

## Iteration Steps

### 1. Read Current State

```
git status --short
```

Read these files:
- `docs/PLANNING/ANDROID_BLOCKERS_AND_DECISIONS.md`
- `docs/PLANNING/ANDROID_AUTODEV_QUEUE.md`

### 2. Blocker Preflight

Check each:
1. Git worktree: dirty from prior failed loop? If unsafe, record BLOCKED_DIRTY_WORKTREE.
2. P0 OPEN blockers: scan ANDROID_BLOCKERS_AND_DECISIONS.md for P0 OPEN items.
3. Reader-Core path: `/Users/minliny/Documents/Reader-Core` exists? Missing → Core tasks blocked.
4. Android SDK: `$ANDROID_HOME` set? Missing → Gradle tasks blocked (doc tasks OK).
5. User decision pending for selected task? If yes → skip task.
6. New dependency would be introduced? If task doesn't allow → skip task.
7. Network access needed? If not allowed → skip task.
8. Sensitive data: don't read .env, secrets, tokens.

### 3. Select Task

From ANDROID_AUTODEV_QUEUE.md:
- Sort by Priority (P0 > P1 > P2 > P3), then Stage, then ID
- Pick first task with Status == READY
- Skip if Blockers field contains unresolved P0 blocker
- Mark it IN_PROGRESS
- If no READY task: run CronDelete on this loop's job ID (find via CronList), output BLOCKED_NEEDS_USER, update blockers, stop

### 4. Execute

- Read relevant existing files before editing
- Make the smallest useful change
- Code tasks: implement, compile, validate
- Doc tasks: write, format, verify

### 5. Validate

Run the task's Validation command from the queue.
- PASS → Update task to DONE, create local commit with `[TaskID] description`
- FAIL → Mark task BLOCKED, record failure, revert unsafe changes, NO commit

### 6. Report

Output:

```
## Loop Iteration
- HEAD: <hash>
- Task: <ID> — <description>
- Changes: <summary>
- Validation: PASS/FAIL
- Commit: <hash or N/A>
- New Blockers: <list or None>
- Next READY: <ID or NONE>
- Status: ANDROID_LOOP_OK / BLOCKED_NEEDS_USER / BLOCKED_ENVIRONMENT
```

---

## Task Queue Reference

Tasks are in `docs/PLANNING/ANDROID_AUTODEV_QUEUE.md`.

Current READY tasks (doc-only, no Android SDK needed):
- P0-S0-003: Generate initial BLOCKERS_AND_DECISIONS.md (already done in plan setup)
- P0-S0-004: Verify git state, initial commit of planning docs
- P1-S2-002: Write DATA_LAYER_DESIGN.md
- P1-S3-002: Write CORE_BRIDGE_DESIGN.md
- P2-S3-003-DOC: Write HTTP adapter design doc
- P3-S7-001-DOC: Write WebView/JS adapter design doc
- P3-S11-001-DOC: Write WebDAV design doc

All Gradle/code tasks are BLOCKED until:
1. Android SDK confirmed (BD-017)
2. Package name / minSdk / Compose vs XML decided (BD-001 through BD-004)

---

## When Blocked (auto-cancel)

If all tasks are DONE/BLOCKED/SKIPPED (zero READY):
1. Loop calls CronDelete to cancel the scheduled timer (prevents empty wake-ups)
2. Loop outputs BLOCKED_NEEDS_USER with summary of remaining BLOCKED tasks
3. User reviews `docs/PLANNING/ANDROID_BLOCKERS_AND_DECISIONS.md`
4. User resolves decisions, updates task statuses from BLOCKED to READY
5. User restarts: `/loop 10m /reader-android-loop`

The auto-cancel ensures the loop does not keep firing every 10 minutes when there is nothing to do.

## To Stop manually

```
/loop off
```

---

*This file is the canonical loop command. Do not edit while loop is running.*
