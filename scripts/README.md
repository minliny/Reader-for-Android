# Android local build and delivery entries

These are debug iteration entries for the new `io.reader.android` Host and the pinned Rust Core. Retained Android design documents and unselected legacy sources are historical inputs; they do not enter this App's compilation or acceptance.

## Fixed source and native library

`core-dependency.json` fixes the Core Git object, Java binding hashes, API, ABI, NDK and CMake. `reference-baseline.json` records fixed Harmony/UI Git objects; dirty worktree contents are excluded.

```sh
python3 scripts/build-core.py --prepare-only
python3 scripts/build-core.py --timeout 900
```

Set `READER_CORE_ROOT` to a local repository containing the fixed commit if it cannot be found in the surrounding Reader workspace. Set `ANDROID_HOME`, or use ignored `local.properties` with `sdk.dir`. Dependencies are never downloaded or silently replaced by these entries.

The source is extracted by `git archive` into `.build/core-source/<commit>`. Private Git metadata binds that same archive to the real commit for Core's existing build identity. The canonical `build-android-jni.sh` is reused with `.build/core-target`, one worker, `nice 15`, offline Cargo and the shared Reader build-resource lease. Its nested Java wrapper/sample and optional Kotlin sample compilation is recorded; business tests are not added. CMake receives explicit 16KB final-link flags. ELF alignment and actual 16KB runtime behavior have separate statuses.

## App artifact

```sh
python3 scripts/build.py --timeout 900
# Use only when the actual installed revision requires it:
python3 scripts/build.py --version-code 2
python3 scripts/apk_artifact.py verify --manifest /absolute/run/manifest.json
```

The App entry explicitly runs `:app:writeDebugRuntimeArtifacts` and `:app:assembleDebug`. The first exports the exact resolved runtime AAR inventory for this run; neither runs business tests. The entry captures selected sources, resources, Gradle and build scripts plus the matching Core receipt. Inputs must remain unchanged. Publication verifies package/SDK identity, one APK signer, exact embedded native bytes and ZIP alignment. Every packaged Native library must belong to the fixed Core or a specific resolved AAR, with source SHA and 16KB ELF evidence; duplicate and unknown libraries are rejected. Original Native bytes are retained by the debug package. The run is then frozen under `.artifacts/<uuid>/`. `.build/app-build.json` points to the immutable manifest. A manifest proves its stated build, not an installation or interaction.

For a test-only correction, reuse the admitted production APK and compile only the separate Runner:

```sh
python3 scripts/build-tests.py --host-manifest /absolute/run/manifest.json \
  --rationale "Concrete failure and test-only correction"
```

This offline entry selects `:app:assembleDebugAndroidTest` under the same build lease. It checks that production compiler inputs and the existing App APK remain identical, captures the test sources, and publishes a separate test APK with its signer. It neither installs nor runs tests, and does not rebuild Core. Changed production inputs require the ordinary App build lane.

## Explicit device operations

```sh
python3 scripts/device.py install --manifest /absolute/run/manifest.json --serial EXACT_TARGET
python3 scripts/device.py install --manifest /absolute/run/manifest.json --serial EXACT_TARGET --stop-app
python3 scripts/device.py launch --manifest /absolute/run/manifest.json --serial EXACT_TARGET
```

Each invocation uses an explicit target and a short lease keyed by the hardware serial hash, so USB and Wi-Fi aliases share the same lock. No server start/kill, reconnect, reboot, uninstall or clear-data recovery is implemented. There is one `install -r` attempt. Existing signer mismatch or revision downgrade stops before installation.

An existing App must be stopped for the one pre-copy; `--stop-app` explicitly permits stopping that exact App. `run-as` archives every App-data root except rebuildable `cache`, `code_cache` and `lib`, once before and once after install. Raw archives stay in ignored private `.build/device-runs/`. Only local extracted copies are opened with SQLite: schema, typed rows and row identities are fingerprinted, including WAL contents. Original files and SQLite semantics must remain unchanged. Installation and launch produce distinct receipts; neither proves interaction or user acceptance. This lane supports debuggable APKs because protected data must be observable through `run-as`.

The narrow offline protection-helper regression is `python3 scripts/test-data-snapshot.py`. It checks progress/file loss, physical SQLite page changes and WAL rows, without building or operating a device.
