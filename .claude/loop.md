# Reader for Android Cron Loop Default Prompt

You are running the Reader for Android autonomous development loop.

## Hard rules

1. Work only inside this repository: `/Users/minliny/Documents/Reader for Android`
2. Do NOT push to remote. Local commits only.
3. Do NOT copy Legado source code.
4. Do NOT rewrite Reader-Core internals in Kotlin.
5. Do NOT access Reader-Core Parser/Runtime internals from Android code.
6. Only use Reader-Core public API: Facade, DTOs, Error Taxonomy, Protocols.
7. If Reader-Core public API is insufficient, record the gap in docs/PLANNING/ANDROID_BLOCKERS_AND_DECISIONS.md — NEVER bypass the boundary.
8. Prefer small, reversible changes. One loop iteration completes at most ONE task.
9. If a task needs user decision, mark it BLOCKED and select another READY task.
10. If no READY task exists, auto-cancel the loop timer via CronDelete, update blockers doc, and stop with BLOCKED_NEEDS_USER.
11. Before editing code, run the blocker preflight.
12. After editing, run the task's validation command(s).
13. Commit only if validation passes and changes are coherent.
14. NEVER push automatically.
15. Do NOT access network unless the current READY task explicitly allows it.
16. Do NOT introduce new dependencies (Room, OkHttp, kTor, etc.) unless the task explicitly allows it.
17. When in doubt, write a design doc instead of code.

## Iteration algorithm

### Step 1: Read state
- `git status --short` — check for dirty worktree
- Read `docs/PLANNING/ANDROID_BLOCKERS_AND_DECISIONS.md` — scan P0 OPEN blockers
- Read `docs/PLANNING/ANDROID_AUTODEV_QUEUE.md` — find next READY task

### Step 2: Blocker preflight
Check each of these. If a check fails and blocks all READY tasks, stop.

1. **Dirty worktree**: If changes from previous failed iteration exist, assess if safe to continue. If not, record BLOCKED_DIRTY_WORKTREE.
2. **P0 OPEN blockers**: Scan blockers doc for P0 OPEN items. If they block the selected task, skip it.
3. **Core path exists**: Check `/Users/minliny/Documents/Reader-Core`. If missing, Core-dependent tasks are BLOCKED but App Shell tasks can continue.
4. **Android SDK**: If `$ANDROID_HOME` not set, Gradle-based tasks are BLOCKED_ENV_ANDROID_SDK. Doc-only tasks can continue.
5. **User decision**: If selected task has "Decision Required: Yes", mark it BLOCKED and find another.
6. **New dependency**: If task would modify build.gradle.kts to add a new dependency, verify task explicitly allows it.
7. **Network**: If task requires HTTP fetch, verify BLOCKED_NETWORK_DECISION is resolved.
8. **Sensitive data**: Never read .env, never commit tokens/passwords/cookies.

### Step 3: Select and mark task
- Select first READY task sorted by Priority (P0 > P1 > P2 > P3), then Stage, then ID
- Skip tasks whose Blockers field contains unresolved P0 blockers
- Update task status to IN_PROGRESS in the queue file
- If no READY task: find this loop's job via CronList, call CronDelete, output "BLOCKED_NEEDS_USER — loop auto-cancelled", update blockers doc, stop

### Step 4: Implement
- Read relevant existing files first
- Make the smallest useful change toward the task goal
- For code tasks: write/compile/validate
- For doc tasks: write/format/verify

### Step 5: Validate
- Run the task's Validation command
- If it passes: update status to DONE, create local git commit
- If it fails: mark task BLOCKED, record failure in blockers doc, revert unsafe changes, do NOT commit

### Step 6: Report
Output a compact status table:

```
## Loop Iteration Result
| Field | Value |
|---|---|
| HEAD | <short hash or "no commits yet"> |
| Selected Task | <task ID> |
| Changes | <summary> |
| Validation | PASS / FAIL |
| Commit | <hash or "N/A"> |
| New Blockers | <list or "None"> |
| Next READY Task | <task ID or "NONE — BLOCKED_NEEDS_USER"> |
| Status | ANDROID_LOOP_OK / BLOCKED_NEEDS_USER / BLOCKED_ENVIRONMENT |
```

## Preferred current stage

S0 (Planning/Infrastructure) → S1 (App Shell) → S2 (Models) → S3 (Core Bridge) → S4 (Book Source Management) → S5 (Main Flow)

Do NOT jump to WebView/JS/WebDAV/TTS before App Shell, local model, and CoreBridge are stable.

## Files to never modify

- `.git/config`
- `.env` (don't even read it)
- `~/.claude.json` (user config)
- Files outside this repository
- Files inside `/Users/minliny/Documents/Reader-Core` (upstream repo, read-only)

## Safe defaults when uncertain

| Situation | Action |
|-----------|--------|
| Package name not decided | Use `com.reader.android` |
| minSdk not decided | Use 26 |
| Compose vs XML | Use Compose |
| Single vs multi-module | Use single module |
| Storage (Room vs DataStore) | Write design doc; don't add dependency |
| HTTP lib (OkHttp vs kTor) | Write design doc; don't add dependency |
| Core bridge strategy | Write design doc; don't implement |
| Network access | Deny by default |
| New dependency | Design doc only; don't modify build files |
