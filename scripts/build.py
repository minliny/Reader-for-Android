#!/usr/bin/env python3
"""Build one debug APK under the shared Reader resource lease, without tests."""

import argparse
import json
import os
import shutil
import sys

from android_common import ROOT, bounded_run, file_sha, freeze_directory, utc_now, write_json
from apk_artifact import capture, publish
from core_dependency import snapshot


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--timeout", type=int, default=900)
    parser.add_argument("--version-code", type=int, help="Optional explicit install revision; does not set a product version")
    args = parser.parse_args()
    if not 1 <= args.timeout <= 1800 or (args.version_code is not None and args.version_code < 1):
        parser.error("Expected a deadline of 1..1800 seconds and a positive install revision")
    directory = capture()
    result = {"schemaVersion": 1, "startedAt": utc_now(), "result": "FAIL", "businessTests": "NOT_RUN", "deviceActions": 0}
    try:
        source = snapshot()
        node, nice = shutil.which("node"), shutil.which("nice")
        if not node or not nice:
            raise RuntimeError("node and nice are required for the shared Reader build lease")
        command = [nice, "-n", "15", node, str(ROOT / "scripts/build-lease.mjs"),
                   "--module", str(source / "scripts/lib/build-resource-lock.mjs"), "--",
                   str(ROOT / "gradlew"), "--no-daemon", "--max-workers=1", "--console=plain",
                   ":app:writeDebugRuntimeArtifacts", ":app:assembleDebug", "-PreaderArtifactRunId=" + directory.name]
        if args.version_code is not None:
            command.append("-PreaderVersionCode=" + str(args.version_code))
        result["command"] = command
        result["process"] = bounded_run(command, log=directory / "build.log", timeout=args.timeout,
                                         env={**os.environ, "GRADLE_OPTS": "-Dorg.gradle.workers.max=1"})
        if result["process"]["exitCode"] != 0 or result["process"]["timedOut"]:
            raise RuntimeError("Gradle App build failed or exceeded its deadline; inspect the retained build.log")
        manifest = publish(directory)
        result.update({"result": "PASS_APK_BUILD_ONLY", "manifest": str(directory / "manifest.json"),
                       "apkSha256": manifest["apk"]["sha256"]})
    except Exception as error:
        result["error"] = str(error)
    result["finishedAt"] = utc_now()
    write_json(directory / "result.json", result)
    freeze_directory(directory)
    if result["result"] == "PASS_APK_BUILD_ONLY":
        write_json(ROOT / ".build/app-build.json", {"manifest": str(directory / "manifest.json"),
                   "sha256": file_sha(directory / "manifest.json")})
    print(json.dumps(result, indent=2))
    return 0 if result["result"] == "PASS_APK_BUILD_ONLY" else 1


if __name__ == "__main__":
    sys.exit(main())
