#!/usr/bin/env python3
"""Compile a separate Runner while proving the admitted production inputs unchanged."""

import argparse
import json
import os
from pathlib import Path
import shutil
import sys
import uuid

from android_common import ROOT, bounded_run, file_sha, freeze_directory, git_identity, utc_now, write_json
from apk_artifact import input_files, inspect_signer, verify_manifest
from core_dependency import snapshot


def production_inputs(files):
    # Operational Python scripts are captured for provenance but are not App
    # compiler inputs. The dependency configuration is consumed by Gradle.
    return {name: sha for name, sha in files.items()
            if not name.startswith("scripts/") or name in {
                "scripts/core-dependency.json", "scripts/android-build.json"}}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host-manifest", required=True)
    parser.add_argument("--rationale", required=True)
    parser.add_argument("--timeout", type=int, default=600)
    args = parser.parse_args()
    if not args.rationale.strip() or not 1 <= args.timeout <= 900:
        parser.error("A concrete rationale and a 1..900 second deadline are required")
    run = ROOT / ".build/android-test-builds" / str(uuid.uuid4())
    run.mkdir(parents=True)
    result = {"schemaVersion": 1, "startedAt": utc_now(), "result": "FAIL",
              "rationale": args.rationale, "sourceIdentity": git_identity(),
              "execution": "NOT_RUN", "deviceActions": 0, "automaticRetries": 0,
              "coreRebuilds": 0, "gradleCache": str(Path(os.environ.get("GRADLE_USER_HOME", "~/.gradle")).expanduser())}
    try:
        host_path = Path(args.host_manifest).resolve()
        host = verify_manifest(host_path)
        old = json.loads((host_path.parent / "capture.json").read_text())["files"]
        expected = production_inputs(old)
        if production_inputs(input_files()) != expected:
            raise RuntimeError("Production compiler inputs changed; this test-only reuse lane is unavailable")
        result.update({"hostManifest": str(host_path), "hostApkSha256": host["apk"]["sha256"],
                       "hostManifestSha256": file_sha(host_path)})
        test_sources = {p.relative_to(ROOT).as_posix(): file_sha(p)
                        for p in sorted((ROOT / "app/src/androidTest/kotlin/io").rglob("*.kt"))}
        result["testSourceFiles"] = test_sources
        for name in test_sources:
            destination = run / "inputs" / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT / name, destination)
        app_output = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
        if not app_output.is_file() or file_sha(app_output) != host["apk"]["sha256"]:
            raise RuntimeError("Existing Gradle App output differs from the admitted installed production APK")
        source = snapshot()
        node, nice = shutil.which("node"), shutil.which("nice")
        if not node or not nice:
            raise RuntimeError("node and nice are required for the shared build lease")
        command = [nice, "-n", "15", node, str(ROOT / "scripts/build-lease.mjs"),
                   "--module", str(source / "scripts/lib/build-resource-lock.mjs"), "--",
                   str(ROOT / "gradlew"), "--no-daemon", "--offline", "--max-workers=1",
                   "--console=plain", ":app:assembleDebugAndroidTest"]
        result["command"] = command
        write_json(run / "plan.json", result)
        result["process"] = bounded_run(command, log=run / "build.log", timeout=args.timeout,
                                        env={**os.environ, "GRADLE_OPTS": "-Dorg.gradle.workers.max=1"})
        if result["process"]["exitCode"] or result["process"]["timedOut"]:
            raise RuntimeError("Runner compilation failed; inspect the original build.log before another attempt")
        if production_inputs(input_files()) != expected or file_sha(app_output) != host["apk"]["sha256"]:
            raise RuntimeError("Production source or APK changed during the Runner-only build")
        if test_sources != {name: file_sha(ROOT / name) for name in test_sources}:
            raise RuntimeError("Test sources changed during compilation")
        candidate = ROOT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
        apk = run / "Reader-tests.apk"
        shutil.copy2(candidate, apk)
        signer = inspect_signer(apk)
        if signer != host["apk"]["signerSha256"]:
            raise RuntimeError("Runner signer differs from the existing production signer")
        result.update({"result": "PASS_TEST_APK_BUILD_ONLY", "apk": str(apk),
                       "apkSha256": file_sha(apk), "signerSha256": signer, "normalApkUnchanged": True})
    except Exception as error:
        result["error"] = str(error)
    result["finishedAt"] = utc_now()
    write_json(run / "result.json", result)
    freeze_directory(run)
    if result["result"] == "PASS_TEST_APK_BUILD_ONLY":
        write_json(ROOT / ".build/test-apk.json", result)
    print(json.dumps({key: result[key] for key in ("result", "error", "apkSha256", "process") if key in result}
                     | {"run": str(run)}, indent=2))
    return 0 if result["result"] == "PASS_TEST_APK_BUILD_ONLY" else 1


if __name__ == "__main__":
    sys.exit(main())
