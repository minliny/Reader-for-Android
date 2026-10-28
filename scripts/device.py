#!/usr/bin/env python3
"""Explicit-target debug APK overwrite install with one short physical-device lease."""

from contextlib import contextmanager
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import tempfile
import time
import uuid

from android_common import ROOT, android_sdk, digest, file_sha, freeze_directory, utc_now, write_json
from apk_artifact import build_config, inspect_signer, tools, verify_manifest
from data_snapshot import audit_archive, compare

PACKAGE = "io.reader.minliny"
EXCLUDED_REBUILDABLE = {"cache", "code_cache", "lib"}


class Device:
    def __init__(self, serial):
        self.serial = serial
        self.adb = android_sdk() / "platform-tools/adb"
        if not self.adb.is_file() or not serial or any(ord(value) < 32 for value in serial):
            raise RuntimeError("An installed adb and an explicit valid target are required")
        self.deadline = time.monotonic() + 600
        self.target_ref = None
        self.commands = []

    def call(self, *args, timeout=30, allow_error=False, output_path=None):
        remaining = self.deadline - time.monotonic()
        if remaining <= 0:
            raise RuntimeError("Device session reached its ten-minute bound")
        command = [str(self.adb), "-s", self.serial, *map(str, args)]
        started = time.monotonic()
        output_stream = Path(output_path).open("wb") if output_path is not None else subprocess.PIPE
        child = subprocess.Popen(command, stdin=subprocess.DEVNULL, stdout=output_stream,
                                 stderr=subprocess.PIPE, start_new_session=True)
        try:
            output, error = child.communicate(timeout=min(timeout, remaining))
        except (subprocess.TimeoutExpired, KeyboardInterrupt):
            try:
                os.killpg(child.pid, signal.SIGTERM)
            except ProcessLookupError:
                pass
            try:
                child.communicate(timeout=3)
            except subprocess.TimeoutExpired:
                try:
                    os.killpg(child.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
                child.communicate(timeout=3)
            raise RuntimeError("Owned adb client stopped on timeout or interruption; shared server and target were not reset")
        finally:
            if output_path is not None:
                output_stream.close()
        self.commands.append({"operation": str(args[0]), "exitCode": child.returncode,
                              "durationSeconds": round(time.monotonic() - started, 3)})
        if child.returncode and not allow_error:
            # Do not leak raw physical identifiers or paths through errors.
            raise RuntimeError("adb " + str(args[0]) + " failed with status " + str(child.returncode))
        return child.returncode, output or b"", error

    def identify(self):
        _, state, _ = self.call("get-state")
        if state.strip() != b"device":
            raise RuntimeError("Explicit adb target is not in device state")
        _, hardware, _ = self.call("shell", "getprop", "ro.serialno")
        if not hardware.strip() or hardware.strip() == b"unknown":
            _, hardware, _ = self.call("shell", "getprop", "ro.boot.serialno")
        if not hardware.strip() or hardware.strip() == b"unknown":
            raise RuntimeError("Cannot prove physical identity across USB/Wi-Fi; no target lease or write is allowed")
        self.target_ref = digest(b"reader-android-physical-v1\x00" + hardware.strip())
        return self.target_ref

    def not_running(self):
        status, output, _ = self.call("shell", "pidof", PACKAGE, allow_error=True)
        if status not in (0, 1) or output.strip():
            raise RuntimeError("App must be stopped for consistent data evidence; select --stop-app explicitly for install")


@contextmanager
def device_lease(device):
    identity = device.identify()
    # Every checkout and both transports use the same user-level lock domain;
    # TMPDIR and project-local environment overrides cannot split ownership.
    locks = Path.home() / ".codex/locks/reader-android-devices"
    locks.mkdir(parents=True, exist_ok=True, mode=0o700)
    directory = locks / (identity + ".lock")
    owner = {"schemaVersion": 1, "protocol": "reader-android-device-v1", "pid": os.getpid(),
             "token": str(uuid.uuid4()), "targetRef": identity, "startedAt": utc_now(), "maximumSeconds": 600}
    try:
        directory.mkdir()
    except FileExistsError:
        raise RuntimeError("Physical Android target is occupied or has an unreconciled lease; no service recovery is attempted")
    try:
        write_json(directory / "owner.json", owner)
        yield owner
    finally:
        current = json.loads((directory / "owner.json").read_text())
        if current.get("token") != owner["token"] or current.get("pid") != os.getpid():
            raise RuntimeError("Android target owner changed; refusing lease release")
        (directory / "owner.json").unlink()
        directory.rmdir()


def installed_apk(device, run, label):
    status, output, _ = device.call("shell", "pm", "path", PACKAGE, allow_error=True)
    lines = output.decode().splitlines()
    paths = [line.removeprefix("package:") for line in lines if line.startswith("package:")]
    if not paths:
        if status not in (0, 1) or any(line.strip() for line in lines):
            raise RuntimeError("Cannot determine whether Reader is already installed")
        return None
    base = [path for path in paths if path.endswith("/base.apk")]
    if len(base) != 1:
        raise RuntimeError("Cannot identify one installed Reader base APK")
    destination = run / (label + "-installed.apk")
    device.call("pull", base[0], str(destination), timeout=120)
    signer = inspect_signer(destination)
    aapt = tools()["aapt2"]
    badging = subprocess.check_output([str(aapt), "dump", "badging", str(destination)], text=True, stderr=subprocess.STDOUT)
    match = re.search(r"^package: name='([^']+)' versionCode='(\d+)'", badging, re.M)
    if not match or match.group(1) != PACKAGE:
        raise RuntimeError("Installed package identity is not the selected Reader application")
    return {"sha256": file_sha(destination), "signerSha256": signer, "versionCode": int(match.group(2))}


def snapshot(device, run, label):
    device.not_running()
    device.call("shell", "run-as", PACKAGE, "id")
    _, listing, _ = device.call("shell", "run-as", PACKAGE, "ls", "-1A")
    entries = listing.decode().splitlines()
    protected = sorted(name for name in entries if name not in EXCLUDED_REBUILDABLE)
    if any(not re.fullmatch(r"[A-Za-z0-9_.-]+", name) for name in protected):
        raise RuntimeError("Unexpected app-root name; no incomplete data snapshot is accepted")
    if protected:
        archive = run / (label + "-data.tar")
        device.call("exec-out", "run-as", PACKAGE, "tar", "-cf", "-", *protected,
                    timeout=120, output_path=archive)
        result = audit_archive(archive)
    else:
        result = {"schemaVersion": 1, "archiveSha256": None, "files": {}, "sqlite": {}, "directories": []}
    device.not_running()
    result.update({"targetRef": device.target_ref, "applicationId": PACKAGE, "stopped": True,
                   "protectedRoots": protected, "excludedRebuildableRoots": sorted(EXCLUDED_REBUILDABLE)})
    write_json(run / (label + "-data.json"), result)
    return result


def main():
    def interrupted(_number, _frame):
        raise KeyboardInterrupt()

    signal.signal(signal.SIGTERM, interrupted)
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="action", required=True)
    installer = sub.add_parser("install", help="Verify and install exactly one immutable debug APK, preserving existing data")
    installer.add_argument("--manifest", required=True)
    installer.add_argument("--serial", required=True)
    installer.add_argument("--stop-app", action="store_true", help="Explicitly permit stopping this exact Reader before the one pre-copy")
    launcher = sub.add_parser("launch", help="Verify the actual installed APK then launch without interaction")
    launcher.add_argument("--manifest", required=True)
    launcher.add_argument("--serial", required=True)
    args = parser.parse_args()
    run = ROOT / ".build/device-runs" / str(uuid.uuid4())
    run.mkdir(parents=True)
    result = {"schemaVersion": 1, "startedAt": utc_now(), "action": args.action, "result": "FAIL",
              "interaction": "NOT_RUN", "userAcceptance": "OPEN", "runDirectory": str(run),
              "automaticRetries": 0, "uninstallOrClearOrRebootOrServerMaintenance": 0}
    device = None
    try:
        manifest = verify_manifest(args.manifest)
        result.update({"manifest": str(Path(args.manifest).resolve()), "manifestSha256": file_sha(args.manifest),
                       "apkSha256": manifest["apk"]["sha256"]})
        device = Device(args.serial)
        with device_lease(device):
            result["targetRef"] = device.target_ref
            before_package = installed_apk(device, run, "before")
            result["beforePackage"] = before_package
            if args.action == "launch":
                if before_package is None or before_package["sha256"] != manifest["apk"]["sha256"]:
                    raise RuntimeError("Actual installed APK is not the selected immutable artifact")
                component = PACKAGE + "/" + build_config()["launchActivity"]
                status, output, _ = device.call("shell", "am", "start", "-W", "-n", component)
                text = output.decode()
                if status or not re.search(r"^Status:\s*ok\s*$", text, re.M):
                    raise RuntimeError("Android did not acknowledge a successful launch")
                result["result"] = "PASS_LAUNCH_ONLY"
                result["dataPreservation"] = "NOT_RUN_LAUNCH_ONLY"
                result["launchKind"] = "AM_START_ACKNOWLEDGEMENT"
            else:
                if before_package:
                    if before_package["signerSha256"] != manifest["apk"]["signerSha256"]:
                        raise RuntimeError("Installed signer differs; uninstall, data clear and signer rotation are not authorized")
                    if before_package["versionCode"] > manifest["apk"]["versionCode"]:
                        raise RuntimeError("Install revision would downgrade; choose an appropriate build revision")
                    if args.stop_app:
                        device.call("shell", "am", "force-stop", PACKAGE)
                    before = snapshot(device, run, "before")
                else:
                    before = None
                # Exactly one overwrite attempt. No downgrade flag, uninstall,
                # clear-data, reboot, reconnect or adb-server manipulation.
                status, output, _ = device.call("install", "-r", manifest["apk"]["path"], timeout=180, allow_error=True)
                result["installAcknowledged"] = status == 0 and bool(re.search(rb"^Success\s*$", output, re.M))
                after_package = installed_apk(device, run, "after")
                result["afterPackage"] = after_package
                if after_package:
                    after = snapshot(device, run, "after")
                    result["dataPreservation"] = compare(before, after) if before else {
                        "result": "NOT_APPLICABLE_FRESH_INSTALL", "protectedPostSnapshot": "PASS"}
                else:
                    result["dataPreservation"] = {"result": "FAIL", "reason": "Reader package unavailable after the install attempt"}
                if not result["installAcknowledged"]:
                    raise RuntimeError("One install attempt failed; retained package/data evidence is separate")
                if after_package is None or after_package["sha256"] != manifest["apk"]["sha256"]:
                    raise RuntimeError("Actual installed APK bytes differ from the selected artifact")
                if result["dataPreservation"]["result"] not in ("PASS", "NOT_APPLICABLE_FRESH_INSTALL"):
                    raise RuntimeError("Protected Reader data changed or could not be proven preserved")
                result["result"] = "PASS_INSTALL_ONLY"
                result["launch"] = "NOT_RUN"
    except (Exception, KeyboardInterrupt) as error:
        result["error"] = str(error) or "Interrupted; owned device actions stopped"
    if device:
        result["commands"] = device.commands
    result["finishedAt"] = utc_now()
    write_json(run / "result.json", result)
    freeze_directory(run)
    print(json.dumps(result, indent=2))
    return 0 if result["result"].startswith("PASS_") else 1


if __name__ == "__main__":
    sys.exit(main())
