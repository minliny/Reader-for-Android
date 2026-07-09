# Reader UI Generated Kotlin Contract

This source-set is now **superseded by composite build**.

As of 2026-07-09, the Android app no longer copies generated Kotlin contract files into this
directory. Instead, the Reader UI repo is included as a Gradle composite build via
`includeBuild("../Reader UI")` in `settings.gradle.kts`, and the app depends on the
coordinate `io.reader.ui:reader-ui-contract`.

This means:

- Contract changes in `/Users/minliny/Documents/Reader UI/generated/kotlin` are picked up
  automatically by the next Gradle sync — no manual copy step.
- The full 14-file contract surface is available, including `MotionPolicy.kt` (which was missing
  from the previous 13-file copy).
- `app/build.gradle.kts` no longer references this directory via `java.srcDir`.

Refresh procedure (rarely needed now):

1. Run `node tools/codegen/generate.mjs` in `/Users/minliny/Documents/Reader UI`.
2. Gradle sync the Android project — the composite build will recompile the contract module.

Current upstream snapshot: 84 `MotionSpecRegistry` entries, 117 `TokenRegistry` entries,
28 `MotionPolicyRegistry` entries, 200 `RouteId` entries.
