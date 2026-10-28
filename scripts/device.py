#!/usr/bin/env python3
"""Explicit-target debug APK overwrite install with one short physical-device lease."""

from contextlib import ExitStack, contextmanager
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


class DeviceCallError(RuntimeError):
    """Preserve the owned client outcome and partial captured bytes."""

    def __init__(self, message, command_record, stdout=b"", stderr=b""):
        super().__init__(message)
        self.command_record, self.stdout, self.stderr = command_record, stdout, stderr


class Device:
    def __init__(self, serial):
        self.serial = serial
        self.adb = android_sdk() / "platform-tools/adb"
        if not self.adb.is_file() or not serial or any(ord(value) < 32 for value in serial):
            raise RuntimeError("An installed adb and an explicit valid target are required")
        self.deadline = time.monotonic() + 600
        self.target_ref = None
        self.commands = []
        self.lease_owner = None
        self.active_clients = set()

    def call(self, *args, timeout=30, allow_error=False, output_path=None, stderr_path=None,
             on_tick=None):
        """on_tick() runs only while this exact client is active; it never retries it."""
        if not args or timeout <= 0:
            raise ValueError("An adb command and a positive timeout are required")
        remaining = self.deadline - time.monotonic()
        if remaining <= 0:
            raise RuntimeError("Device session reached its ten-minute bound")
        command = [str(self.adb), "-s", self.serial, *map(str, args)]
        started = time.monotonic()
        deadline = started + min(timeout, remaining)
        record = {"operation": str(args[0]), "arguments": list(map(str, args)), "startedAt": utc_now(),
                  "timeoutSeconds": round(min(timeout, remaining), 3), "dispatched": False,
                  "timedOut": False, "interrupted": False, "signalsSent": []}
        child, failure = None, None
        output, error = b"", b""
        with ExitStack() as streams:
            # File-backed streams cannot lose TimeoutExpired's partial output or
            # fill a PIPE while the parent handles an exact consent callback.
            stdout = streams.enter_context(Path(output_path).open("xb") if output_path is not None else tempfile.TemporaryFile())
            stderr = streams.enter_context(Path(stderr_path).open("xb") if stderr_path is not None else tempfile.TemporaryFile())
            try:
                child = subprocess.Popen(command, stdin=subprocess.DEVNULL, stdout=stdout,
                                         stderr=stderr, start_new_session=True)
                self.active_clients.add(child.pid)
                record.update({"dispatched": True, "clientPid": child.pid})
                while True:
                    budget = deadline - time.monotonic()
                    if budget <= 0:
                        raise subprocess.TimeoutExpired(command, timeout)
                    try:
                        child.wait(timeout=min(budget, 0.5) if on_tick else budget)
                        break
                    except subprocess.TimeoutExpired:
                        if time.monotonic() >= deadline:
                            raise
                        if on_tick is not None:
                            on_tick()
            except BaseException as caught:
                failure = caught
                record.update({"timedOut": isinstance(caught, subprocess.TimeoutExpired),
                               "interrupted": isinstance(caught, KeyboardInterrupt), "failureType": type(caught).__name__})
                if child is not None:
                    for signum in (signal.SIGTERM, signal.SIGKILL):
                        if child.poll() is not None:
                            break
                        try:
                            os.killpg(child.pid, signum)
                            record["signalsSent"].append(signal.Signals(signum).name)
                        except ProcessLookupError:
                            pass
                        try:
                            child.wait(timeout=3)
                        except subprocess.TimeoutExpired:
                            continue
            finally:
                record.update({"exitCode": child.poll() if child is not None else None,
                               "childExited": child.poll() is not None if child is not None else None,
                               "durationSeconds": round(time.monotonic() - started, 3), "finishedAt": utc_now()})
                if child is not None and record["childExited"]:
                    self.active_clients.discard(child.pid)
                for field, path, stream in (("stdout", output_path, stdout), ("stderr", stderr_path, stderr)):
                    stream.flush()
                    record[field + "Bytes"] = os.fstat(stream.fileno()).st_size
                    if path is not None:
                        record[field + "Path"] = str(Path(path).resolve())
                if output_path is None:
                    stdout.seek(0)
                    output = stdout.read()
                if stderr_path is None:
                    stderr.seek(0)
                    error = stderr.read()
                # Timeout, interruption and spawn errors are first-class command
                # outcomes. They must be present before any exception is raised.
                self.commands.append(record)
        if failure is not None:
            raise DeviceCallError("Owned adb client failed or stopped; partial output and its exact outcome were retained",
                                  record, output, error) from failure
        if child.returncode and not allow_error:
            raise DeviceCallError("adb " + str(args[0]) + " failed with status " + str(child.returncode), record, output, error)
        return child.returncode, output, error

    def require_lease(self):
        owner = self.lease_owner
        if owner is None or owner.get("pid") != os.getpid() or owner.get("targetRef") != self.target_ref:
            raise RuntimeError("An existing physical Android lease is required")
        path = Path.home() / ".codex/locks/reader-android-devices" / (self.target_ref + ".lock/owner.json")
        current = json.loads(path.read_text())
        if current.get("pid") != os.getpid() or current.get("token") != owner.get("token"):
            raise RuntimeError("Physical Android lease identity changed")
        if time.monotonic() >= self.deadline:
            raise RuntimeError("Physical Android lease reached its ten-minute bound")
        return owner

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
        device.lease_owner = owner
        yield owner
    finally:
        if device.active_clients:
            raise RuntimeError("An owned adb client remains active; physical lease retained for diagnosis")
        current = json.loads((directory / "owner.json").read_text())
        if current.get("token") != owner["token"] or current.get("pid") != os.getpid():
            raise RuntimeError("Android target owner changed; refusing lease release")
        (directory / "owner.json").unlink()
        directory.rmdir()
        device.lease_owner = None


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
