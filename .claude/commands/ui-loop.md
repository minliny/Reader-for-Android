# Reader for Android UI-only Development Loop

## Mission

On Non-UI RC1 base, advance Compose UI, interactions, page integration, and Legado UI parity. No new backend capabilities.

## Preflight

```
cd "/Users/minliny/Documents/Reader for Android"
pwd && git branch --show-current && git status --short && git log --oneline -n 5
git diff --check
./gradlew test
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
```

If red: **STOP**.

## Task Selection

1. Read `docs/PLANNING/ANDROID_UI_ONLY_AUTODEV_QUEUE.md`
2. Pick first READY task in stage order: UI-S0 → UI-S1 → ... → UI-S11
3. One task per iteration
4. Do NOT read Non-UI queue
5. Do NOT add backend capabilities
6. If backend gap found: record as integration gap, do NOT fix

## Forbidden Changes

- Reader-Core modifications
- New backend capabilities (parser, repository, adapter, storage, sync)
- Large refactors of data/ package
- cron configuration
- git push (unless user explicitly requests)
- git reset --hard / clean -fd / force push
- Real network fixtures
- Deleting existing tests
- Committing local.properties, build/, .gradle/

## Allowed Changes

- `app/src/main/kotlin/com/reader/android/ui/**`
- Light ViewModel glue (UI state only, no backend logic)
- UI-only docs
- UI queue
- UI handoff docs
- Preview/fake display helpers
- Compose theme/styling

## Validation

```
git diff --check
./gradlew test
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
```

## End Conditions

- One UI task done + committed
- Test/build failure
- Backend gap found → mark BLOCKED_BY_BACKEND_GAP
- User product decision needed → mark BLOCKED_BY_DECISION
- Git risk detected
- Zero READY UI tasks

## Report Format

```
# Reader for Android UI-only Loop Report
## 1. Status
## 2. Task
## 3. Changes
## 4. UI Scope Compliance
## 5. Backend Contract Usage
## 6. Validation
## 7. Queue Update
## 8. Commit
## 9. Next
```
