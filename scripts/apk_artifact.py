"""Capture exact Android App inputs and verify immutable APK/native pairing."""

import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import uuid
import zipfile

from android_common import (ROOT, android_sdk, config, digest, file_sha,
                            freeze_directory, git_identity, utc_now, write_json)
from core_dependency import check_binding
from native_artifacts import resolve_native_inputs


def build_config():
    return json.loads((ROOT / "scripts/android-build.json").read_text())


def tools():
    directory = android_sdk() / "build-tools" / build_config()["buildToolsVersion"]
    selected = {name: directory / name for name in ("aapt2", "apksigner", "zipalign")}
    if any(not path.is_file() for path in selected.values()):
        raise RuntimeError("Exact APK inspection build-tools are missing: " + str(directory))
    return selected


def input_files():
    result = {}
    for name in ("app/src/main/AndroidManifest.xml", "app/src/main/kotlin/io", "app/src/main/res",
                 "app/src/main/assets", "app/src/core/java", "app/src/core/jniLibs", "scripts", "gradle",
                 "gradlew", "settings.gradle.kts", "build.gradle.kts", "app/build.gradle.kts", "gradle.properties",
                 "gradle.lockfile", "app/gradle.lockfile", ".build/core-build.json"):
        path = ROOT / name
        candidates = [path] if path.is_file() else sorted(path.rglob("*"))
        for candidate in candidates:
            if candidate.is_symlink():
                raise RuntimeError("App build input must not be a symlink: " + str(candidate))
            if candidate.is_file() and "__pycache__" not in candidate.parts:
                result[candidate.relative_to(ROOT).as_posix()] = file_sha(candidate)
    for required in ("app/src/main/AndroidManifest.xml", "app/build.gradle.kts", "gradlew", ".build/core-build.json"):
        if required not in result:
            raise RuntimeError("Required App build input is missing: " + required)
    check_binding()
    core = json.loads((ROOT / ".build/core-build.json").read_text())
    if core.get("result") != "PASS_NATIVE_BUILD_ONLY" or core.get("coreCommit") != config()["commit"]:
        raise RuntimeError("Successful matching native build receipt is required")
    expected = ROOT / "app/src/core/jniLibs/arm64-v8a/libreader_core_jni.so"
    if file_sha(expected) != core.get("librarySha256"):
        raise RuntimeError("App native input differs from its Core build receipt")
    return result


def capture():
    directory = ROOT / ".artifacts" / str(uuid.uuid4())
    directory.mkdir(parents=True)
    selected = input_files()
    for name in selected:
        destination = directory / "inputs" / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(ROOT / name, destination)
        if file_sha(destination) != selected[name]:
            raise RuntimeError("App input changed while snapshotting: " + name)
    if selected != input_files():
        raise RuntimeError("App inputs changed during capture")
    write_json(directory / "capture.json", {"schemaVersion": 1, "capturedAt": utc_now(),
               "gitIdentity": git_identity(), "files": selected,
               "fingerprint": digest(json.dumps(selected, sort_keys=True, separators=(",", ":")).encode())})
    return directory


def inspect_apk(path, expected_native):
    selected, expected = tools(), build_config()
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise RuntimeError("APK has duplicate ZIP entries")
        native = [name for name in names if name.startswith("lib/") and name.endswith(".so")]
        required = sorted(expected_native)
        if sorted(native) != required:
            raise RuntimeError("APK native entries differ from the exact admitted Core/runtime AAR inventory")
        for name in required:
            if digest(archive.read(name)) != expected_native[name]["sha256"]:
                raise RuntimeError("APK native bytes differ from their paired source: " + name)
            if expected_native[name]["elf"].get("elf16KBAlignment") != "PASS":
                raise RuntimeError("Native library lacks exact-byte 16KB ELF evidence: " + name)
    badging = subprocess.check_output([str(selected["aapt2"]), "dump", "badging", str(path)], text=True, stderr=subprocess.STDOUT)
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)'(?: versionName='([^']*)')?", badging, re.M)
    minimum = re.search(r"^(?:minSdkVersion|sdkVersion):'(\d+)'", badging, re.M)
    target = re.search(r"^targetSdkVersion:'(\d+)'", badging, re.M)
    if package is None or minimum is None or target is None:
        raise RuntimeError("Cannot read APK package/SDK metadata")
    if (package.group(1) != expected["applicationId"] or int(minimum.group(1)) != expected["minimumSdk"]
            or int(target.group(1)) != expected["targetSdk"]):
        raise RuntimeError("APK application or SDK identity differs from the admitted Android lane")
    if not re.search(r"^application-debuggable\s*$", badging, re.M):
        raise RuntimeError("This preserve-data run-as lane supports debuggable iteration APKs only")
    signer = inspect_signer(path, selected["apksigner"])
    aligned = subprocess.run([str(selected["zipalign"]), "-c", "-P", "16", "4", str(path)],
                             stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if aligned.returncode != 0:
        raise RuntimeError("APK ZIP 16KB native-library alignment check failed")
    return {"applicationId": package.group(1), "versionCode": int(package.group(2)),
            "versionName": package.group(3) or None, "minimumSdk": int(minimum.group(1)),
            "targetSdk": int(target.group(1)), "abis": ["arm64-v8a"],
            "signerSha256": signer, "apkZip16KBAlignment": "PASS", "runtime16KBDevice": "NOT_RUN"}


def inspect_signer(path, apksigner=None):
    executable = apksigner or tools()["apksigner"]
    # Keep public fingerprint only; never persist personal certificate fields.
    output = subprocess.check_output([str(executable), "verify", "--print-certs", str(path)],
                                     text=True, stderr=subprocess.STDOUT)
    signers = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})\s*$", output, re.M)
    if len(signers) != 1:
        raise RuntimeError("Expected one verified APK signer")
    return signers[0].lower()


def publish(directory):
    directory = Path(directory).resolve()
    if directory.parent != (ROOT / ".artifacts").resolve():
        raise RuntimeError("Artifact run must be the captured local Android run directory")
    captured = json.loads((directory / "capture.json").read_text())
    if input_files() != captured["files"]:
        raise RuntimeError("App inputs changed during build; no artifact is published")
    core = json.loads((directory / "inputs/.build/core-build.json").read_text())
    candidate = ROOT / build_config()["apkOutput"]
    native_inputs = resolve_native_inputs(directory, core)
    write_json(directory / "native-libraries.json", native_inputs)
    metadata = inspect_apk(candidate, native_inputs["libraries"])
    apk = directory / "Reader-debug.apk"
    shutil.copy2(candidate, apk)
    if file_sha(candidate) != file_sha(apk):
        raise RuntimeError("APK changed while publishing")
    manifest = {"schemaVersion": 1, "runId": directory.name, "publishedAt": utc_now(),
                "result": "PASS_APK_BUILD_ONLY", "gitIdentity": captured["gitIdentity"],
                "inputsFingerprint": captured["fingerprint"], "captureSha256": file_sha(directory / "capture.json"),
                "apk": {"path": str(apk), "bytes": apk.stat().st_size, "sha256": file_sha(apk), **metadata},
                "core": {"commit": core["coreCommit"], "librarySha256": core["librarySha256"],
                         "receipt": "inputs/.build/core-build.json", "receiptSha256": file_sha(directory / "inputs/.build/core-build.json")},
                "nativeLibraries": {"receipt": "native-libraries.json", "sha256": file_sha(directory / "native-libraries.json")},
                "businessTests": "NOT_RUN", "install": "NOT_RUN", "interaction": "NOT_RUN", "userAcceptance": "OPEN"}
    write_json(directory / "manifest.json", manifest)
    return manifest


def verify_manifest(path):
    path = Path(path).resolve()
    manifest = json.loads(path.read_text())
    if manifest.get("schemaVersion") != 1 or manifest.get("result") != "PASS_APK_BUILD_ONLY":
        raise RuntimeError("Expected an immutable successful APK build manifest")
    apk = Path(manifest["apk"]["path"])
    if apk.parent != path.parent or apk.is_symlink() or file_sha(apk) != manifest["apk"]["sha256"]:
        raise RuntimeError("Immutable APK bytes or path differ from its manifest")
    capture_path = path.parent / "capture.json"
    if file_sha(capture_path) != manifest["captureSha256"]:
        raise RuntimeError("Source capture receipt differs from the immutable manifest")
    captured = json.loads(capture_path.read_text())
    fingerprint = digest(json.dumps(captured["files"], sort_keys=True, separators=(",", ":")).encode())
    if fingerprint != manifest["inputsFingerprint"] or captured["gitIdentity"] != manifest["gitIdentity"]:
        raise RuntimeError("Source capture identity differs from the immutable manifest")
    core = path.parent / manifest["core"]["receipt"]
    if core.resolve().parent != (path.parent / "inputs/.build").resolve() or file_sha(core) != manifest["core"]["receiptSha256"]:
        raise RuntimeError("Core pairing receipt differs from its manifest")
    native_path = path.parent / manifest["nativeLibraries"]["receipt"]
    if native_path.resolve().parent != path.parent or file_sha(native_path) != manifest["nativeLibraries"]["sha256"]:
        raise RuntimeError("Native dependency provenance differs from the immutable manifest")
    admitted = json.loads(native_path.read_text())
    if admitted["libraries"].get("lib/arm64-v8a/libreader_core_jni.so", {}).get("sha256") != manifest["core"]["librarySha256"]:
        raise RuntimeError("Core library differs from the admitted native inventory")
    actual = inspect_apk(apk, admitted["libraries"])
    if any(actual[key] != manifest["apk"].get(key) for key in actual):
        raise RuntimeError("APK identity does not match its immutable manifest")
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="action", required=True)
    sub.add_parser("capture")
    publisher = sub.add_parser("publish")
    publisher.add_argument("--run", required=True)
    verifier = sub.add_parser("verify")
    verifier.add_argument("--manifest", required=True)
    args = parser.parse_args()
    if args.action == "capture":
        print(capture())
    elif args.action == "publish":
        manifest = publish(args.run)
        freeze_directory(args.run)
        write_json(ROOT / ".build/app-build.json", {"manifest": str(Path(args.run).resolve() / "manifest.json"),
                   "sha256": file_sha(Path(args.run) / "manifest.json")})
        print(json.dumps(manifest, indent=2))
    else:
        print(json.dumps(verify_manifest(args.manifest), indent=2))
