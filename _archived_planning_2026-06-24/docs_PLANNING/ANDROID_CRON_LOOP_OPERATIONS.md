# Reader for Android Cron Loop Operations

**Date**: 2026-05-13
**Purpose**: How to start, stop, monitor, and recover the 10-minute autonomous development loop

---

## 1. Requirements

- **Claude Code version**: 2.1.138+ (confirmed: `2.1.138`)
- **Scheduled tasks support**: ✅ (`.claude/scheduled_tasks.json` API available)
- **Repository**: `/Users/minliny/Documents/Reader for Android`
- **Reader-Core**: `/Users/minliny/Documents/Reader-Core` (must exist for Core-dependent tasks)

---

## 2. How to Start

### Method A: Default loop (uses .claude/loop.md)

```bash
cd "/Users/minliny/Documents/Reader for Android"
claude
```

Then inside Claude Code:

```
/loop 10m
```

This runs the content of `.claude/loop.md` every 10 minutes.

### Method B: Named command loop (uses .claude/commands/reader-android-loop.md)

```bash
cd "/Users/minliny/Documents/Reader for Android"
claude
```

Then inside Claude Code:

```
/loop 10m /reader-android-loop
```

This runs `.claude/commands/reader-android-loop.md` every 10 minutes.

### Recommendation

Use **Method B** (`/loop 10m /reader-android-loop`) — the command file is self-contained and doesn't affect other loop behaviors.

---

## 3. Loop Lifecycle

- **Scope**: Session-only (dies when Claude Code exits)
- **Interval**: 10 minutes
- **Expiration**: Recurring tasks auto-expire after 7 days (Claude Code limit)
- **Persistence**: NOT durable by default (dies with session)
- **If you want durable**: `/loop 10m /reader-android-loop` + enable durable in settings (NOT recommended for autonomous development without supervision)

---

## 4. Per-Iteration Execution Flow

Each 10-minute iteration:

1. **Preflight**:
   - `git status --short` — check for dirty worktree
   - Read `docs/PLANNING/ANDROID_BLOCKERS_AND_DECISIONS.md` — check P0 blockers
   - Read `docs/PLANNING/ANDROID_AUTODEV_QUEUE.md` — find next READY task

2. **Select task**:
   - First READY task by priority (P0 > P1 > P2 > P3) and stage order
   - Skip if task blockers not resolved
   - If no READY task → auto-cancel loop timer via CronDelete, output `BLOCKED_NEEDS_USER`

3. **Execute**:
   - Mark selected task `IN_PROGRESS`
   - Make the smallest useful change
   - Run task's validation command

4. **Validate**:
   - Success: mark `DONE`, create local git commit, output status table
   - Failure: mark `BLOCKED`, record failure reason, do NOT commit, output status table

5. **Report**:
   - Current HEAD
   - Selected task
   - Changes made
   - Validation result
   - Commit hash (if committed)
   - New blockers (if any)
   - Next READY task
   - Status tag

---

## 5. How to Stop

### Automatic stop (when queue exhausted)

When all tasks are DONE/BLOCKED/SKIPPED (zero READY), the loop **automatically cancels its own timer** via CronDelete. This prevents empty wake-ups every 10 minutes.

### Manual stop

Inside Claude Code:

```
/loop off
```

Or just close Claude Code (Ctrl+C / Cmd+C).

### What happens after auto-cancel

The loop outputs `BLOCKED_NEEDS_USER — loop auto-cancelled`. To resume:
1. Resolve blockers in `docs/PLANNING/ANDROID_BLOCKERS_AND_DECISIONS.md`
2. Update BLOCKED tasks back to READY
3. Restart: `/loop 10m /reader-android-loop`

---

## 6. How to Monitor

Check what tasks the loop is working on:

```bash
cd "/Users/minliny/Documents/Reader for Android"
git log --oneline -10
cat docs/PLANNING/ANDROID_AUTODEV_QUEUE.md | grep -E 'READY|IN_PROGRESS|DONE|BLOCKED'
```

Or inside Claude Code:

```
/loop status
```

---

## 7. How to Handle Blockers

If the loop stops with `BLOCKED_NEEDS_USER`:

1. Read `docs/PLANNING/ANDROID_BLOCKERS_AND_DECISIONS.md`
2. Find OPEN blockers
3. Make decisions and update status to `RESOLVED` or `DEFERRED`
4. Update task statuses from `BLOCKED` back to `READY` as appropriate
5. Restart the loop: `/loop 10m /reader-android-loop`

---

## 8. How to Recover from Failed Iteration

### Dirty Worktree
```bash
cd "/Users/minliny/Documents/Reader for Android"
git status
# If changes are from a failed loop iteration:
git checkout -- .  # discard
# Or if they're useful partial progress:
git stash
```

### Corrupted Task Queue
- Restore from git: `git checkout docs/PLANNING/ANDROID_AUTODEV_QUEUE.md`
- Or manually fix the status field

### Android SDK Missing
```bash
export ANDROID_HOME=~/Library/Android/sdk
# Or install Android Studio and SDK
```

---

## 9. Why NOT System Cron

- Claude Code loops run **inside a Claude Code session** — they maintain context, task state, and conversation memory
- System cron would start a fresh Claude Code process each time, losing all context
- Each fresh start would need to re-read all docs, re-assess state, and re-plan from scratch
- The 10-minute warm-cache loop pattern (under 5 min cache TTL) provides efficient context reuse
- Only switch to system cron + durable tasks if you need **persistent unattended autonomous development** across machine restarts

---

## 10. Safety Rules (NEVER violated by loop)

| Rule | Enforcement |
|------|-------------|
| No `git push` | Loop script hard-codes: commit yes, push NO |
| No Legado source copying | Pre-commit grep for Legado package names |
| No Reader-Core internal access | Pre-commit review of imports |
| No user input during loop | Tasks requiring decisions are BLOCKED, not executed |
| No network access (default) | Only if task explicitly allows it |
| No new dependencies without approval | Design doc tasks only, no build file modification |
| One task per iteration | Hard limit in loop algorithm |
| Validate before commit | Validation command must pass |
| No sensitive data | Grep for secrets/tokens before commit |

---

## 11. Commands Reference

| Command | Purpose |
|---------|---------|
| `/loop 10m /reader-android-loop` | Start the 10-minute dev loop |
| `/loop off` | Stop the loop |
| `/loop status` | Check loop status |
| `/reader-android-loop` | Run one iteration manually |
| `git log --oneline -5` | Check recent loop commits |
| `cat docs/PLANNING/ANDROID_AUTODEV_QUEUE.md \| grep READY` | See ready tasks |
