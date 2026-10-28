#!/usr/bin/env python3
"""Build only the pinned arm64 JNI library; no business test or device action."""

import argparse
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys
import uuid

from android_common import (CONFIG_PATH, ROOT, android_sdk, bounded_run, config,
                            digest, file_sha, freeze_directory, git, utc_now, write_json)
from core_dependency import check_binding, check_snapshot, snapshot, sync_binding

LINK_OPTIONS = "-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384"


def dependencies(sdk):
    value = config()
    ndk = sdk / "ndk" / value["ndkVersion"]
    cmake = sdk / "cmake" / value["cmakeVersion"] / "bin/cmake"
    properties = ndk / "source.properties"
    if not properties.is_file() or not re.search(
            r"^Pkg\.Revision\s*=\s*" + re.escape(value["ndkVersion"]) + r"\s*$",
            properties.read_text(), re.M):
        raise RuntimeError("Exact Android NDK " + value["ndkVersion"] + " is missing or incomplete")
    host_candidates = ("darwin-arm64", "darwin-x86_64") if sys.platform == "darwin" else ("linux-x86_64", "linux-aarch64", "linux-arm64")
    host = next((ndk / "toolchains/llvm/prebuilt" / name for name in host_candidates
                 if (ndk / "toolchains/llvm/prebuilt" / name / "bin/llvm-readelf").is_file()), None)
    if host is None or not (ndk / "build/cmake/android.toolchain.cmake").is_file():
        raise RuntimeError("The configured NDK has no complete compatible host toolchain")
    if not cmake.is_file():
        raise RuntimeError("Exact Android SDK CMake " + value["cmakeVersion"] + " is missing")
    configured_clang = os.environ.get("LIBCLANG_PATH")
    clang_candidate = Path(configured_clang).expanduser() if configured_clang else (
        Path("/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/lib")
        if sys.platform == "darwin" else None)
    clang_libraries = [] if clang_candidate is None else (
        [clang_candidate] if clang_candidate.is_file() else
        sorted([*clang_candidate.glob("libclang.dylib"), *clang_candidate.glob("libclang.so*")]))
    clang_library = next((path.resolve() for path in clang_libraries if path.is_file()), None)
    if clang_library is None:
        raise RuntimeError("Android QuickJS bindgen requires a real host libclang; set LIBCLANG_PATH explicitly")
    tools = {}
    for name in ("cargo", "rustup", "node", "javac", "bash", "nice"):
        tools[name] = shutil.which(name)
        if tools[name] is None:
            raise RuntimeError("Missing build dependency: " + name)
    return {"ndk": str(ndk), "ndkPropertiesSha256": file_sha(properties), "ndkHost": str(host),
            "libclang": str(clang_library), "libclangDirectory": str(clang_library.parent),
            "libclangSha256": file_sha(clang_library),
            "cmake": str(cmake), "tools": tools, "kotlinSampleCompiler": shutil.which("kotlinc")}


def cmake_wrapper(real):
    path = ROOT / ".build/tools/cmake-android"
    path.parent.mkdir(parents=True, exist_ok=True)
    # Reassert final-link options on every configure, including reused caches.
    data = ("#!/usr/bin/env bash\nset -euo pipefail\n"
            "if [[ \"${1:-}\" == --build || \"${1:-}\" == --version ]]; then\n"
            "  exec " + shlex.quote(real) + " \"$@\"\nfi\n"
            "exec " + shlex.quote(real) + " \"$@\" "
            + shlex.quote("-DCMAKE_SHARED_LINKER_FLAGS=" + LINK_OPTIONS) + " "
            + shlex.quote("-DCMAKE_MODULE_LINKER_FLAGS=" + LINK_OPTIONS) + "\n")
    if not path.exists() or path.read_text() != data:
        path.write_text(data)
    path.chmod(0o755)
    return path


def inspect_elf(library, readelf):
    header = subprocess.check_output([str(readelf), "--file-header", str(library)], text=True)
    if "ELF64" not in header or "AArch64" not in header or not re.search(r"Type:\s+DYN", header):
        raise RuntimeError("Expected an Android arm64 ELF shared library")
    segments = subprocess.check_output([str(readelf), "--program-headers", "--wide", str(library)], text=True)
    alignments = [int(line.split()[-1], 0) for line in segments.splitlines() if line.strip().startswith("LOAD ")]
    if not alignments or any(value < 16384 for value in alignments):
        raise RuntimeError("JNI ELF LOAD alignment is below 16KB")
    symbols = subprocess.check_output([str(readelf), "--dyn-syms", "--wide", str(library)], text=True)
    if "Java_com_reader_core_NativeCoreBridge_" not in symbols:
        raise RuntimeError("Expected canonical Java NativeCoreBridge JNI symbols")
    return {"class": "ELF64", "machine": "AArch64", "loadAlignments": alignments,
            "elf16KBAlignment": "PASS", "runtime16KBDevice": "NOT_RUN"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--prepare-only", action="store_true", help="Sync exact source/wrapper and check dependencies, without compilation")
    parser.add_argument("--timeout", type=int, default=900, help="Owned build-process deadline in seconds (maximum 1800)")
    args = parser.parse_args()
    if not 1 <= args.timeout <= 1800:
        parser.error("--timeout must be between 1 and 1800 seconds")
    run = ROOT / ".build/core-runs" / str(uuid.uuid4())
    run.mkdir(parents=True)
    result = {"schemaVersion": 1, "startedAt": utc_now(), "runDirectory": str(run),
              "coreCommit": config()["commit"], "businessTests": "NOT_RUN", "deviceActions": 0,
              "runtime16KBDevice": "NOT_RUN", "result": "FAIL", "retries": 0}
    try:
        sync_binding()
        source = snapshot()
        sdk = android_sdk()
        deps = dependencies(sdk)
        targets = subprocess.check_output([deps["tools"]["rustup"], "target", "list", "--installed"], cwd=source, text=True).splitlines()
        if config()["rustTarget"] not in targets:
            raise RuntimeError("Pinned Rust toolchain is missing target " + config()["rustTarget"] + "; install explicitly before rebuilding")
        tool_versions = {name: subprocess.check_output([deps["tools"][name], "--version"], cwd=source, text=True, stderr=subprocess.STDOUT).strip()
                         for name in ("cargo", "rustup", "javac", "node")}
        wrapper = cmake_wrapper(deps["cmake"])
        inputs = {"coreCommit": config()["commit"], "coreTree": git(source, "rev-parse", "HEAD^{tree}").decode().strip(),
                  "coreArchive": json.loads(source.with_suffix(".archive.json").read_text()),
                  "dependencySha256": file_sha(CONFIG_PATH), "builderSha256": file_sha(Path(__file__)),
                  "cmakeWrapperSha256": file_sha(wrapper), "linkOptions": LINK_OPTIONS,
                  "ccFlagsParsing": "CC_SHELL_ESCAPED_FLAGS=1",
                  "leaseOrchestratorSha256": file_sha(ROOT / "scripts/build-lease.mjs"),
                  "androidApi": config()["minimumAndroidApi"], "abis": config()["abis"],
                  "sourcePath": str(source), "cargoTargetDirectory": str(ROOT / ".build/core-target"),
                  "dependencies": deps, "toolVersions": tool_versions,
                  "javaWrapper": {item["destination"]: item["sha256"] for item in config()["files"]},
                  "nestedCompilations": config()["nestedCompilations"], "businessTests": "NOT_RUN"}
        write_json(run / "inputs.json", inputs)
        result["inputsSha256"] = file_sha(run / "inputs.json")
        if args.prepare_only:
            result["result"] = "PASS_PREPARE_ONLY"
            result["compilation"] = "NOT_RUN"
        else:
            env = {**os.environ, "ANDROID_HOME": str(sdk), "ANDROID_SDK_ROOT": str(sdk),
                   "ANDROID_NDK_HOME": deps["ndk"], "ANDROID_NDK_ROOT": deps["ndk"],
                   "ANDROID_ABIS": "arm64-v8a", "ANDROID_API": "24", "CMAKE": str(wrapper),
                   "CARGO_TARGET_DIR": str(ROOT / ".build/core-target"), "CARGO_BUILD_JOBS": "1",
                   "CARGO_NET_OFFLINE": "true", "CMAKE_BUILD_PARALLEL_LEVEL": "1",
                   "LIBCLANG_PATH": deps["libclangDirectory"],
                   # The canonical script quotes its sysroot paths. cc-rs's
                   # default whitespace parser preserves those quote bytes;
                   # use its supported shell parser rather than altering Core.
                   "CC_SHELL_ESCAPED_FLAGS": "1",
                   "ANDROID_JNI_BUILD_ROOT": str(ROOT / ".build/core-target/android-jni"),
                   "READER_CORE_BUILD_IDENTITY_OUTPUT": str(run / "native-identity.json"),
                   "READER_CORE_BUILD_INPUT_SNAPSHOT": str(run / "inputs.json")}
            command = [deps["tools"]["nice"], "-n", "15", deps["tools"]["node"],
                       str(ROOT / "scripts/build-lease.mjs"), "--module",
                       str(source / "scripts/lib/build-resource-lock.mjs"), "--", deps["tools"]["bash"],
                       str(source / config()["canonicalBuild"])]
            result["process"] = bounded_run(command, log=run / "build.log", timeout=args.timeout, cwd=source, env=env)
            if result["process"]["exitCode"] != 0 or result["process"]["timedOut"]:
                raise RuntimeError("Canonical JNI build failed or exceeded its bound; inspect " + str(run / "build.log"))
            check_snapshot(source)
            check_binding()
            if file_sha(CONFIG_PATH) != inputs["dependencySha256"] or file_sha(Path(__file__)) != inputs["builderSha256"]:
                raise RuntimeError("Android build inputs changed during native compilation")
            native_identity = json.loads((run / "native-identity.json").read_text())
            if native_identity.get("gitCommit") != config()["commit"] or native_identity.get("gitDirty") is not False:
                raise RuntimeError("Embedded Core identity is not the exact clean pinned source")
            library = ROOT / ".build/core-target/android-jni/libs/arm64-v8a/libreader_core_jni.so"
            elf = inspect_elf(library, Path(deps["ndkHost"]) / "bin/llvm-readelf")
            immutable = run / "lib/arm64-v8a/libreader_core_jni.so"
            immutable.parent.mkdir(parents=True)
            shutil.copy2(library, immutable)
            destination = ROOT / "app/src/core/jniLibs/arm64-v8a/libreader_core_jni.so"
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(immutable, destination)
            if file_sha(destination) != file_sha(immutable):
                raise RuntimeError("Native library copy pairing failed")
            result.update({"result": "PASS_NATIVE_BUILD_ONLY", "nativeIdentity": native_identity,
                           "librarySha256": file_sha(immutable), "immutableLibrary": str(immutable),
                           "appLibrary": str(destination), "elf": elf})
    except Exception as error:
        result["error"] = str(error)
    result["finishedAt"] = utc_now()
    write_json(run / "result.json", result)
    if result["result"] == "PASS_NATIVE_BUILD_ONLY":
        write_json(ROOT / ".build/core-build.json", result)
    elif result["result"] == "PASS_PREPARE_ONLY":
        write_json(ROOT / ".build/core-preparation.json", result)
    freeze_directory(run)
    print(json.dumps(result, indent=2))
    return 0 if result["result"].startswith("PASS_") else 1


if __name__ == "__main__":
    sys.exit(main())
