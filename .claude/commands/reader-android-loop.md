---
description: Reader for Android 10-minute cron loop — one safe task per cycle, auto-cancel when queue exhausted
---

# Reader for Android Cron Loop Command

This is the command file for `/loop 10m /reader-android-loop`.

---

## Hard rules

1. Work only inside this repository: `/Users/minliny/Documents/Reader for Android`
2. Do NOT push to remote. Local commits only.
3. Do NOT copy Legado source code.
4. Do NOT modify Reader-Core internals.
5. Only access Reader-Core via public API/Facade/DTO/Error Taxonomy.
6. One task per cycle. No batching.
7. Default: no real network access (`AppProvider.isNetworkAllowed = false`).
8. Default: no new dependencies.
9. NEVER push.
10. Never read .env or commit secrets.

---

## Iteration Steps

### 1. Pre-Check

```bash
git rev-parse --short HEAD
git status --short
```

If `git status --short` not empty → BLOCKED_DIRTY_WORKTREE. Output status and stop.

### 2. Read Queue

Read: `docs/PLANNING/ANDROID_LONG_TERM_TASK_QUEUE.md`

### 3. Select Task

From the queue:
- Pick first task with `Status == READY`
- Priority: Phase A > Phase B > Phase C > Phase D > Phase E > Phase F
- Within same phase: P0 > P1 > P2
- Skip if scope violation (e.g., task needs device but BLOCKED_BY_DEVICE, needs network but not D-002)
- If no READY task: output `LOOP_COMPLETE` with remaining BLOCKED summary. Auto-cancel via CronDelete.

### 4. Execute

Follow the specific task instructions in `docs/PLANNING/ANDROID_LONG_TERM_LOOP_EXECUTION_PLAN.md`.
If task not found there, follow `docs/PLANNING/ANDROID_LONG_TERM_LOOP_RULES.md`.

Scope enforcement matrix also in `ANDROID_LONG_TERM_LOOP_RULES.md` §4.

### 5. Verify

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

If task touches UI smoke path, also:
```bash
./gradlew :app:testDebugUnitTest --tests "*UiCapabilitySmokeTest"
```

PASS → mark DONE, create commit.
FAIL → mark BLOCKED, revert unsafe changes, record failure reason, NO commit.

### 6. Report

```
## Loop: <LOOP-ID>
- HEAD: <hash>
- Task: <ID> — <description>
- Changes: <summary or "read-only">
- Verification: PASS/FAIL
- Commit: <hash or "N/A">
- Status: LOOP_OK / BLOCKED_xxx
- Next READY: <ID or "NONE — LOOP_COMPLETE">
```

### 7. Update Queue

- Mark completed task DONE in `ANDROID_LONG_TERM_TASK_QUEUE.md`
- Unblock downstream tasks that depended on it
- If milestone reached, update `ANDROID_LONG_TERM_ROADMAP.md`

---

## Task Queue Reference

Tasks: `docs/PLANNING/ANDROID_LONG_TERM_TASK_QUEUE.md`
Rules: `docs/PLANNING/ANDROID_LONG_TERM_LOOP_RULES.md`
Execution plan: `docs/PLANNING/ANDROID_LONG_TERM_LOOP_EXECUTION_PLAN.md`

---

## When Blocked (auto-cancel)

If all tasks DONE/BLOCKED/SKIPPED (zero READY):
1. Call CronDelete to cancel the timer
2. Output `LOOP_COMPLETE` with remaining BLOCKED summary
3. User resolves blockers and restarts: `/loop 10m /reader-android-loop`

## To Stop

```
/loop off
```

---

*This file is the canonical loop command. Updated 2026-05-29 for long-term task queue.*
