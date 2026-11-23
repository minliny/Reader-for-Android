"""Admit native inputs only from the fixed Core and exact resolved runtime AARs."""

import json
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile

from android_common import ROOT, android_sdk, config, digest, file_sha


def readelf_tool():
    ndk = android_sdk() / "ndk" / config()["ndkVersion"] / "toolchains/llvm/prebuilt"
    paths = sorted(ndk.glob("*/bin/llvm-readelf"))
    if len(paths) != 1:
        raise RuntimeError("Expected one exact configured NDK ELF inspector")
    return paths[0]


def inspect_alignment(data):
    with tempfile.TemporaryDirectory(prefix="reader-android-native-") as directory:
        library = Path(directory) / "library.so"
        library.write_bytes(data)
        readelf = readelf_tool()
        header = subprocess.check_output([str(readelf), "--file-header", str(library)], text=True)
        if "ELF64" not in header or "AArch64" not in header or not re.search(r"Type:\s+DYN", header):
            raise RuntimeError("A resolved Native dependency is not an arm64 ELF shared library")
        segments = subprocess.check_output([str(readelf), "--program-headers", "--wide", str(library)], text=True)
        alignments = [int(line.split()[-1], 0) for line in segments.splitlines() if line.strip().startswith("LOAD ")]
        if not alignments or any(value < 16384 for value in alignments):
            raise RuntimeError("A resolved Native dependency lacks 16KB ELF LOAD alignment")
        return {"loadAlignments": alignments, "elf16KBAlignment": "PASS", "runtime16KBDevice": "NOT_RUN"}


def resolve_native_inputs(run, core):
    receipt_path = ROOT / "app/build/reports/debug-runtime-artifacts.json"
    receipt = json.loads(receipt_path.read_text())
    if (receipt.get("schemaVersion") != 1 or receipt.get("configuration") != "debugRuntimeClasspath"
            or receipt.get("runId") != run.name):
        raise RuntimeError("Runtime dependency receipt must come from this exact Gradle build invocation")
    native = {"lib/arm64-v8a/libreader_core_jni.so": {
        "sha256": core["librarySha256"], "source": {"kind": "Reader-Core", "commit": core["coreCommit"]},
        "elf": core["elf"], "elfEvidence": "REUSED_EXACT_CORE_BYTES"}}
    artifacts = []
    for item in receipt.get("artifacts", []):
        path = Path(item["file"])
        identity = {"group": item["group"], "name": item["name"], "version": item["version"],
                    "fileName": path.name, "sha256": file_sha(path)}
        artifacts.append(identity)
        if path.suffix != ".aar":
            continue
        with zipfile.ZipFile(path) as archive:
            entries = archive.namelist()
            if len(entries) != len(set(entries)):
                raise RuntimeError("A resolved runtime AAR contains duplicate ZIP entries")
            for name in sorted(entries):
                if not name.startswith("jni/arm64-v8a/") or not name.endswith(".so"):
                    continue
                relative = name.removeprefix("jni/")
                if not re.fullmatch(r"arm64-v8a/[A-Za-z0-9_.+-]+\.so", relative):
                    raise RuntimeError("Unexpected resolved native-library path")
                destination = "lib/" + relative
                if destination in native:
                    raise RuntimeError("Duplicate native-library ownership across Core/runtime dependencies: " + destination)
                data = archive.read(name)
                native[destination] = {"sha256": digest(data), "bytes": len(data),
                                       "source": {"kind": "AAR", "artifact": identity, "entry": name},
                                       "elf": inspect_alignment(data), "elfEvidence": "EXACT_RESOLVED_AAR_BYTES"}
    if not artifacts:
        raise RuntimeError("Resolved runtime dependency inventory is empty")
    return {"schemaVersion": 1, "runId": run.name, "configuration": receipt["configuration"],
            "resolvedArtifacts": sorted(artifacts, key=lambda item: (item["group"], item["name"], item["version"])),
            "libraries": native, "runtime16KBDevice": "NOT_RUN"}
