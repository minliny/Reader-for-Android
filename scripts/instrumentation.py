"""One exact instrumentation dispatch in an already-held physical-device lease."""

import re
import shlex
import time
from pathlib import Path

from android_common import utc_now, write_json
from device import DeviceCallError, PACKAGE

RUNNER = PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner"
SELECTOR = re.compile(r"[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)+#[A-Za-z_$][A-Za-z0-9_$]*")


def parse_output(text, selector):
    """Read raw AndroidJUnitRunner packets; markers never substitute for JUnit."""
    expected_class, expected_method = selector.split("#", 1)
    packets, packet, final_codes, results = [], {}, [], {}
    last_status_key = None
    for line in text.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS: "):
            key, separator, value = line.removeprefix("INSTRUMENTATION_STATUS: ").partition("=")
            if separator:
                packet[key] = value
                last_status_key = key
        elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            try:
                packet["statusCode"] = int(line.rsplit(":", 1)[1])
            except ValueError:
                packet["statusCode"] = None
            packets.append(packet)
            packet, last_status_key = {}, None
        elif line.startswith("INSTRUMENTATION_RESULT: "):
            key, separator, value = line.removeprefix("INSTRUMENTATION_RESULT: ").partition("=")
            if separator:
                results[key] = value
            last_status_key = None
        elif line.startswith("INSTRUMENTATION_CODE: "):
            try:
                final_codes.append(int(line.rsplit(":", 1)[1]))
            except ValueError:
                final_codes.append(None)
            last_status_key = None
        elif last_status_key is not None:
            packet[last_status_key] += "\n" + line
    exact = lambda item: item.get("class") == expected_class and item.get("test") == expected_method
    junit = [item for item in packets if item.get("id") == "AndroidJUnitRunner" or "class" in item or "test" in item]
    foreign = [item for item in junit if not exact(item)]
    starts = [item for item in junit if exact(item) and item.get("statusCode") == 1]
    terminals = [item for item in junit if exact(item) and item.get("statusCode") in (0, -1, -2, -3, -4)]
    markers = [item for item in packets if item.get("statusCode") == 2 and item.get("readerMethod") == expected_method
               and "readerStep" in item]
    foreign_markers = [item for item in packets if "readerMethod" in item and item["readerMethod"] != expected_method]
    steps = [item["readerStep"] for item in markers]
    count_is_one = bool(starts and terminals) and all(item.get("numtests") == "1" and item.get("current") == "1"
                                                     for item in starts + terminals)
    exact_pass = (len(starts) == 1 and len(terminals) == 1 and terminals[0]["statusCode"] == 0
                  and count_is_one and not foreign and not foreign_markers)
    footer_one = len(re.findall(r"(?m)^OK \(\s*1 tests?\s*\)\s*$", text)) == 1
    aborted = ("INSTRUMENTATION_FAILED:" in text or "INSTRUMENTATION_ABORTED:" in text
               or "FAILURES!!!" in text or bool(results.get("shortMsg")))
    return {"selector": selector, "junitStarted": bool(starts), "junitExactPassed": exact_pass,
            "junitStartCount": len(starts), "junitTerminalCount": len(terminals),
            "junitTerminalCodes": [item["statusCode"] for item in terminals], "junitCountIsOne": count_is_one,
            "unexpectedJUnitPackets": foreign, "unexpectedMarkerMethods": foreign_markers,
            "okOneTest": footer_one, "instrumentationCodes": final_codes, "aborted": aborted,
            "ruleEntered": "rule-enter" in steps, "methodEntered": "method-enter" in steps,
            "methodExited": "method-exit" in steps, "ruleExited": "rule-exit" in steps,
            "finalExitSaveAcknowledged": "final-exit-save-ACK" in steps,
            "lastReaderStep": steps[-1] if steps else None, "readerMarkers": markers,
            "completeExactResult": exact_pass and footer_one and final_codes == [-1] and not aborted}


def run_instrumentation(device, selector, args, run, timeout=300, on_tick=None):
    """Run once; on_tick() is caller-owned exact OEM consent handling only.

    The caller must hold device_lease(device), authorize this exact selector and
    bind its App/Runner beforehand. A callback must honor progress.json's
    remainingSeconds and must not retry the test or maintain shared services.
    This helper never clicks, stops an App, installs, or acquires another lease.
    """
    if not SELECTOR.fullmatch(selector) or not 6 < timeout <= 300:
        raise ValueError("One explicit class#method and a total deadline in (6, 300] seconds are required")
    device.require_lease()
    normalized = {}
    for key, value in args.items():
        if (not re.fullmatch(r"[A-Za-z][A-Za-z0-9_.-]*", key) or key in {
                "class", "notClass", "package", "notPackage", "testFile", "notTestFile"}):
            raise ValueError("Instrumentation arguments may not replace or widen the exact selector")
        value = str(value)
        if "\x00" in value:
            raise ValueError("Instrumentation arguments cannot contain NUL")
        normalized[key] = value
    directory = Path(run).resolve() / "instrumentation"
    directory.mkdir(parents=True, exist_ok=False)
    stdout, stderr = directory / "stdout.txt", directory / "stderr.txt"
    started = time.monotonic()
    total_deadline = min(started + timeout, device.deadline)
    # Reserve up to six seconds to terminate and reap only the owned client.
    client_budget = total_deadline - started - 6
    if client_budget <= 0:
        raise RuntimeError("The existing physical lease has insufficient remaining time")
    shell = ["am", "instrument", "-w", "-r", "-e", "class", selector]
    for key, value in normalized.items():
        shell.extend(("-e", key, value))
    shell.append(RUNNER)
    record = {"schemaVersion": 1, "selector": selector, "arguments": normalized, "runner": RUNNER,
              "targetRef": device.target_ref, "startedAt": utc_now(), "result": "FAIL",
              "directory": str(directory), "stdoutPath": str(stdout), "stderrPath": str(stderr),
              "timeoutSeconds": timeout, "clientTimeoutSeconds": round(client_budget, 3),
              "dispatchCount": 0, "automaticRetries": 0, "serviceMaintenance": 0}
    write_json(directory / "plan.json", record)
    command_index = len(device.commands)

    def update_progress():
        device.require_lease()
        progress = parse_output(stdout.read_text(errors="replace") if stdout.exists() else "", selector)
        progress["remainingSeconds"] = max(0, round(total_deadline - time.monotonic(), 3))
        write_json(directory / "progress.json", progress)
        if on_tick is not None:
            on_tick()

    try:
        # Durable count precedes the one real dispatch. A failed spawn remains
        # an attempted dispatch, with clientDispatched recorded separately.
        record["dispatchCount"] += 1
        write_json(directory / "dispatch.json", record)
        device.call("shell", shlex.join(shell), timeout=client_budget, allow_error=True,
                    output_path=stdout, stderr_path=stderr, on_tick=update_progress)
        record["command"] = device.commands[-1]
    except DeviceCallError as error:
        record["command"] = error.command_record
        record["error"] = str(error)
    except (Exception, KeyboardInterrupt) as error:
        record["error"] = str(error) or "Interrupted"
    finally:
        observation = parse_output(stdout.read_text(errors="replace") if stdout.exists() else "", selector)
        record["observation"] = observation
        record["commands"] = device.commands[command_index:]
        command = record.get("command", {})
        record["clientDispatched"] = command.get("dispatched", False)
        record["timedOut"] = command.get("timedOut", False)
        record["exitCode"] = command.get("exitCode")
        record["childExited"] = command.get("childExited")
        if ("error" not in record and record["exitCode"] == 0 and record["childExited"]
                and not record["timedOut"] and observation["completeExactResult"]):
            record["result"] = "PASS_EXACT_METHOD"
        record["durationSeconds"] = round(time.monotonic() - started, 3)
        record["finishedAt"] = utc_now()
        write_json(directory / "result.json", record)
    return record
