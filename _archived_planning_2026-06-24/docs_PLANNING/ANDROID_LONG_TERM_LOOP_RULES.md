# Reader for Android Long-Term Loop Rules

**Date**: 2026-05-29
**Status**: `ANDROID_LONG_TERM_ROADMAP_PLAN_READY`

---

## 1. Loop State Machine

```
                    ┌──────────────────────────────┐
                    │        LOOP_READY              │
                    │  (idle, waiting for next tick) │
                    └──────────┬───────────────────┘
                               │ select next READY task
                               ▼
                    ┌──────────────────────────────┐
                    │       TASK_SELECTED           │
                    │  (task identified, pre-checks │
                    │   begin)                      │
                    └──────────┬───────────────────┘
                               │ pre-checks
                               ▼
              ┌────────────────┴────────────────┐
              │                                 │
              ▼                                 ▼
   ┌─────────────────────┐          ┌──────────────────────────┐
   │  BLOCKED_DIRTY_     │          │       IN_PROGRESS         │
   │  WORKTREE            │          │  (executing task)         │
   │  (uncommitted        │          └──────────┬───────────────┘
   │   changes found)     │                     │
   └─────────────────────┘          ┌────────────┴────────────┐
                                    │                         │
                                    ▼                         ▼
                         ┌──────────────────┐    ┌──────────────────────────┐
                         │  BLOCKED_TEST_   │    │    TASK_DONE             │
                         │  FAILURE          │    │  (verification passed,   │
                         │  (test/assemble   │    │   commit if applicable)  │
                         │   failed)         │    └──────────┬───────────────┘
                         └──────────────────┘               │
                                                            ▼
                                                 ┌──────────────────────────┐
                                                 │   NEEDS_HUMAN_REVIEW     │
                                                 │  (manual verification    │
                                                 │   required)              │
                                                 └──────────┬───────────────┘
                                                            │
                                                            ▼
                                                 ┌──────────────────────────┐
                                                 │     LOOP_COMPLETE        │
                                                 │  (all READY tasks done   │
                                                 │   or queue exhausted)    │
                                                 └──────────────────────────┘
```

### Device/Network/Auth Sub-states

```
IN_PROGRESS
    │
    ├── BLOCKED_DEVICE_MISSING      (device not connected for Phase C)
    ├── BLOCKED_NETWORK_AUTH_MISSING (READER_ALLOW_REAL_NETWORK not set for Phase D)
    └── BLOCKED_SCOPE_VIOLATION     (task attempted forbidden modification)
```

---

## 2. State Transition Rules

| From | To | Trigger |
|------|----|---------|
| LOOP_READY | TASK_SELECTED | Next READY task found in queue |
| TASK_SELECTED | IN_PROGRESS | Pre-checks pass (HEAD stable, git clean, correct branch, no scope violation) |
| TASK_SELECTED | BLOCKED_DIRTY_WORKTREE | `git status --short` not empty |
| TASK_SELECTED | BLOCKED_DEVICE_MISSING | Task requires device, none connected |
| TASK_SELECTED | BLOCKED_NETWORK_AUTH_MISSING | Task requires network, auth not given |
| IN_PROGRESS | TASK_DONE | Task complete, all verification passes |
| IN_PROGRESS | BLOCKED_TEST_FAILURE | `testDebugUnitTest` or `assembleDebug` fails |
| IN_PROGRESS | BLOCKED_SCOPE_VIOLATION | Reader-Core modified, real network triggered without auth, Prototype Gallery used |
| TASK_DONE | NEEDS_HUMAN_REVIEW | Task marked as requiring human verification |
| TASK_DONE | LOOP_READY | Task complete, no human review needed; queue updated |
| NEEDS_HUMAN_REVIEW | LOOP_READY | Human review complete |
| BLOCKED_DIRTY_WORKTREE | LOOP_READY | Workspace cleaned (stash, commit, or restore) |
| BLOCKED_TEST_FAILURE | IN_PROGRESS | Root cause fixed, re-executing |
| ANY_BLOCKED | LOOP_READY | Task skipped, next READY task selected |
| LOOP_READY | LOOP_COMPLETE | No READY tasks remaining in queue |

---

## 3. Loop Execution Rules

### R1: Single Task Per Cycle
Each loop iteration processes exactly ONE task. No batching. No multi-tasking.

### R2: Pre-Check Sequence
Before executing any task:
1. `git rev-parse --short HEAD` — verify HEAD is expected
2. `git status --short` — verify workspace is clean
3. Verify branch is correct (main)
4. Verify task scope does not violate forbidden zones

### R3: Forbidden Zones (always)
- **Reader-Core internal**: `app/src/main/kotlin/com/reader/android/data/bridge/ReaderCoreBridge.kt` — interface only; never modify parser/runtime internals
- **Real network**: allowed (user authorized 2026-05-29). Controlled single-pass only — one URL per visit, no high-frequency, no retry storm, no anti-bot bypass. Ordinary tests must not trigger uncontrolled network access.
- **Prototype Gallery**: never use as proof of capability
- **Legado source**: never copy; grep gate before commit
- **Push**: never `git push` without explicit user command

### R4: Verification After Each Task
1. `./gradlew :app:testDebugUnitTest` must pass
2. `./gradlew :app:assembleDebug` must pass
3. If task touches UI smoke path: `./gradlew :app:testDebugUnitTest --tests "*UiCapabilitySmokeTest"` must pass
4. If task touches S16 fixtures: relevant fixture tests must pass

### R5: Commit Rules
- One commit per cycle max
- Commit ONLY task-related files
- Never commit: `.claude/*`, build outputs, IDE files, unrelated changes
- Message format: `type: short description` (e.g., `fix:`, `feat:`, `refactor:`, `docs:`)
- Co-Authored-By line required

### R6: Documentation Update
After each task:
- Update task status in `ANDROID_LONG_TERM_TASK_QUEUE.md`
- Update relevant phase doc if state changed
- Update `ANDROID_LONG_TERM_ROADMAP.md` if milestone reached

### R7: Blocker Escalation
If blocked for 3+ consecutive cycles:
- Record blocker with rationale in task queue
- Skip to next READY task
- Do NOT force-unblock by violating scope

### R8: Loop Completion
When no READY tasks remain (all DONE, BLOCKED, or SKIPPED):
- Generate final loop report
- Update all docs
- Output `LOOP_COMPLETE`

---

## 4. Scope Enforcement Matrix

| Task Phase | UI Visuals | CoreBridge | Parser | AppProvider | Network | Reader-Core | Real HTTP |
|------------|-----------|------------|--------|-------------|---------|-------------|-----------|
| Phase A (visual polish) | ALLOWED | FORBIDDEN | FORBIDDEN | ALLOWED (minimal) | FORBIDDEN | FORBIDDEN | FORBIDDEN |
| Phase B (DI cleanup) | FORBIDDEN | ALLOWED | FORBIDDEN | ALLOWED | FORBIDDEN | FORBIDDEN | FORBIDDEN |
| Phase C (device review) | FORBIDDEN | FORBIDDEN | FORBIDDEN | FORBIDDEN | FORBIDDEN | FORBIDDEN | FORBIDDEN |
| Phase D (controlled online) | FORBIDDEN | FORBIDDEN | FORBIDDEN | FORBIDDEN | ALLOWED (controlled) | FORBIDDEN | ALLOWED (single-pass) |
| Phase E (reader UX) | ALLOWED (minimal) | FORBIDDEN | FORBIDDEN | ALLOWED | ALLOWED (controlled) | FORBIDDEN | ALLOWED (single-pass) |
| Phase F (release hardening) | FORBIDDEN | FORBIDDEN | FORBIDDEN | FORBIDDEN | FORBIDDEN | FORBIDDEN | FORBIDDEN |

---

## 5. Test Gate Matrix

| Gate | Phase A | Phase B | Phase C | Phase D | Phase E | Phase F |
|------|---------|---------|---------|---------|---------|---------|
| `testDebugUnitTest` | Required | Required | Required | Required | Required | Required |
| `assembleDebug` | Required | Required | Required | Required | Required | Required |
| `UiCapabilitySmokeTest` | Required | Required | Required | Required | Required | Required |
| `FixtureCompletenessValidatorTest` | Optional | Optional | Optional | Required | Optional | Required |
| `Biquges123OfflineReplayTest` | Optional | Optional | Optional | Required | Optional | Required |
| `installDebug` | N/A | N/A | Required | N/A | Optional | Required |

---

## 6. How to Start a Loop

```
/cloop 10m /reader-android-loop
```

Or for manual step-by-step:
```
Read the next READY task from ANDROID_LONG_TERM_TASK_QUEUE.md
Run pre-checks (HEAD, git status, branch, scope)
Execute the task
Run verification
Update docs
Commit if applicable
Report result
```

---

## 7. Auto-Loop Task Selection Priority

1. Phase A READY tasks (lowest risk, highest urgency)
2. Phase B READY tasks
3. Phase E READY tasks
4. Phase C (requires device) — skip if BLOCKED_BY_DEVICE
5. Phase D (requires network auth) — skip if BLOCKED_BY_USER_NETWORK_AUTH
6. Phase F (requires all prior phases)
