#!/usr/bin/env python3
"""Run explicitly selected Android JVM unit tests; never build Core or operate a device."""

import argparse
from collections import Counter
import json
import os
from pathlib import Path
import re
import shutil
import sys
import uuid
import xml.etree.ElementTree as ET

sys.dont_write_bytecode = True

from android_common import (ROOT, bounded_run, config, digest, file_sha,
                            freeze_directory, git, git_identity, utc_now, write_json)
from core_dependency import check_binding


TASK = ":app:testDebugUnitTest"
SELECTOR = re.compile(r"(?:[A-Za-z_]\w*\.)+[A-Za-z_]\w*(?:#[A-Za-z_]\w*)?\Z")
TEST_METHOD = re.compile(r"@Test(?:\s*\([^)]*\))?\s+(?:(?:public|internal)\s+)?fun\s+([A-Za-z_]\w*)\s*\(")


def selected_tests(selectors):
    """Resolve exact selectors against the current, compiled Kotlin test source set."""
    selections, expected, source_files = [], set(), {}
    for selector in selectors:
        if not SELECTOR.fullmatch(selector):
            raise ValueError("Use an exact fully.qualified.Class or Class#method, without wildcards: " + selector)
        class_name, separator, method = selector.partition("#")
        source = ROOT / "app/src/test/kotlin" / (class_name.replace(".", "/") + ".kt")
        if not class_name.startswith("io.") or source.is_symlink() or not source.is_file():
            raise ValueError("Selected class has no source in the configured test source set: " + class_name)
        text = source.read_text()
        package = re.search(r"^\s*package\s+([\w.]+)", text, re.M)
        if package is None or package.group(1) != class_name.rpartition(".")[0]:
            raise ValueError("Selected class does not match its source package: " + class_name)
        methods = TEST_METHOD.findall(text)
        if not methods or len(methods) != len(set(methods)):
            raise ValueError("Expected unique, explicit @Test fun methods in " + str(source))
        if not re.search(r"\bclass\s+" + re.escape(class_name.rpartition(".")[2]) + r"\b", text):
            raise ValueError("Selected class declaration is absent: " + class_name)
        chosen = [method] if separator else methods
        if any(name not in methods for name in chosen):
            raise ValueError("Selected method is absent from its @Test declarations: " + selector)
        pairs = {(class_name, name) for name in chosen}
        if pairs & expected:
            raise ValueError("Selectors overlap or repeat an exact test: " + selector)
        expected.update(pairs)
        relative = source.relative_to(ROOT).as_posix()
        source_files[relative] = file_sha(source)
        selections.append({"selector": selector, "className": class_name, "methods": chosen,
                           "gradleFilter": class_name + ("." + method if separator else ""),
                           "source": relative, "sourceSha256": source_files[relative]})
    return selections, expected, source_files


def production_inputs():
    """Hash actual app/compiler inputs once per boundary, excluding unrelated test sources."""
    result = {}
    for name in ("app/src/main/kotlin/io", "app/src/core/java", "app/src/main/res", "app/src/main/assets",
                 "app/src/main/AndroidManifest.xml", "gradle", "gradlew", "gradle.properties",
                 "settings.gradle.kts", "build.gradle.kts", "app/build.gradle.kts", "gradle.lockfile",
                 "app/gradle.lockfile", "scripts/test.py", "scripts/android_common.py",
                 "scripts/build-lease.mjs", "scripts/core_dependency.py", "scripts/core-dependency.json"):
        path = ROOT / name
        candidates = [path] if path.is_file() or path.is_symlink() else sorted(path.rglob("*"))
        for candidate in candidates:
            if candidate.is_symlink():
                raise RuntimeError("Unit compilation input must not be a symlink: " + str(candidate))
            if candidate.is_file() and "__pycache__" not in candidate.parts:
                result[candidate.relative_to(ROOT).as_posix()] = file_sha(candidate)
    for required in ("app/src/main/AndroidManifest.xml", "app/build.gradle.kts", "gradlew",
                     "scripts/build-lease.mjs", "scripts/core-dependency.json"):
        if required not in result:
            raise RuntimeError("Missing required unit compilation input: " + required)
    check_binding()
    return result


def canonical_lease():
    # Native preparation owns materialization. A unit run may only read the existing
    # fixed Core module; it never calls snapshot(), sync, Cargo, CMake or build-core.
    commit = config()["commit"]
    source = ROOT / ".build/core-source" / commit
    module = source / "scripts/lib/build-resource-lock.mjs"
    if not module.is_file() or module.is_symlink():
        raise RuntimeError("Existing fixed Core lease module is required; run the separate native preparation first")
    if git(source, "rev-parse", "HEAD").decode().strip() != commit:
        raise RuntimeError("Existing Core snapshot has a different commit")
    expected = digest(git(source, "show", commit + ":scripts/lib/build-resource-lock.mjs"))
    if file_sha(module) != expected:
        raise RuntimeError("Canonical lease module differs from its fixed Core Git object")
    return module, {"commit": commit, "module": str(module), "sha256": expected}


def init_script(directory):
    # Fresh, run-private XML is mandatory. Shared app/build/test-results is never
    # consulted, including after a failed Gradle process or a zero-test selection.
    path = directory / "test-output.init.gradle"
    xml = json.dumps(str(directory / "xml")).replace("$", "\\$")
    binary = json.dumps(str(directory / "binary")).replace("$", "\\$")
    outcome = json.dumps(str(directory / "task-outcome.json")).replace("$", "\\$")
    path.write_text("""import org.gradle.api.tasks.testing.Test
import groovy.json.JsonOutput

gradle.projectsEvaluated {
    allprojects { project ->
        project.tasks.withType(Test).configureEach { task ->
            if (task.path == ':app:testDebugUnitTest') {
                task.reports.junitXml.required.set(true)
                task.reports.junitXml.outputLocation.set(project.file(XML_PATH))
                task.reports.html.required.set(false)
                task.binaryResultsDirectory.set(project.file(BINARY_PATH))
                task.filter.failOnNoMatchingTests = true
            }
        }
    }
}
gradle.taskGraph.afterTask { task, state ->
    if (task.path == ':app:testDebugUnitTest') {
        new File(OUTCOME_PATH).text = JsonOutput.prettyPrint(JsonOutput.toJson([
            path: task.path, executed: state.executed, didWork: state.didWork,
            skipped: state.skipped, upToDate: state.upToDate,
            noSource: state.noSource, skipMessage: state.skipMessage,
            failure: state.failure == null ? null : state.failure.toString()
        ]))
    }
}
""".replace("XML_PATH", xml).replace("BINARY_PATH", binary).replace("OUTCOME_PATH", outcome))
    return path


def validate_xml(directory, expected):
    observed, documents, failed = Counter(), [], []
    for path in sorted((directory / "xml").glob("TEST-*.xml")):
        if path.is_symlink() or path.stat().st_size > 8 * 1024 * 1024:
            raise RuntimeError("Invalid or oversized run-private JUnit XML: " + str(path))
        root = ET.fromstring(path.read_bytes())
        cases = list(root.iter("testcase"))
        documents.append({"path": path.relative_to(directory).as_posix(), "sha256": file_sha(path), "tests": len(cases)})
        for case in cases:
            identity = (case.get("classname", ""), case.get("name", ""))
            observed[identity] += 1
            if any(case.find(kind) is not None for kind in ("failure", "error", "skipped")):
                failed.append("#".join(identity))
        # Suite-level errors with no testcase must also remain failures.
        for suite in root.iter("testsuite"):
            if any(int(suite.get(key, "0")) != 0 for key in ("failures", "errors", "skipped")):
                failed.append("suite:" + suite.get("name", "unknown"))
    actual = set(observed)
    result = {"documents": documents, "expectedTests": len(expected), "observedTests": sum(observed.values()),
              "missing": sorted("#".join(item) for item in expected - actual),
              "unexpected": sorted("#".join(item) for item in actual - expected),
              "duplicates": sorted("#".join(item) for item, count in observed.items() if count != 1),
              "failuresErrorsOrSkips": failed,
              "cases": [{"className": key[0], "method": key[1], "count": count}
                        for key, count in sorted(observed.items())]}
    result["exactSelectionPassed"] = bool(documents) and not any(
        result[key] for key in ("missing", "unexpected", "duplicates", "failuresErrorsOrSkips"))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--only", action="append", required=True,
                        help="Exact fully.qualified.Class or Class#method; repeat for another selection")
    parser.add_argument("--rationale", required=True, help="Concrete risk and why this exact selection is necessary")
    parser.add_argument("--timeout", type=int, default=600, help="Entire owned process-family deadline, 1..1800 seconds")
    parser.add_argument("--allow-dependency-download", action="store_true",
                        help="Resolve the locked test dependencies on first use; otherwise require the existing Gradle cache")
    args = parser.parse_args()
    if not args.rationale.strip() or not 1 <= args.timeout <= 1800:
        parser.error("A nonempty rationale and a 1..1800 second deadline are required")
    try:
        selections, expected, selected_sources = selected_tests(args.only)
    except ValueError as error:
        parser.error(str(error))
    directory = ROOT / ".build/test-runs" / str(uuid.uuid4())
    directory.mkdir(parents=True)
    result = {"schemaVersion": 1, "startedAt": utc_now(), "result": "FAIL", "task": TASK,
              "selectors": args.only, "rationale": args.rationale.strip(), "timeoutSeconds": args.timeout,
              "coreBuild": "NOT_RUN", "deviceActions": 0, "retries": 0, "run": str(directory),
              "dependencyResolution": "online" if args.allow_dependency_download else "offline"}
    before = None
    try:
        module, lease_identity = canonical_lease()
        production = production_inputs()
        before = {"schemaVersion": 1, "capturedAt": utc_now(), "gitIdentity": git_identity(),
                  "production": production, "selectedTestSources": selected_sources,
                  "selections": selections, "rationale": args.rationale.strip(), "canonicalLease": lease_identity,
                  "gradleUserHome": str(Path(os.environ.get("GRADLE_USER_HOME", str(Path.home() / ".gradle"))).expanduser().resolve()),
                  "existingBuildDirectory": str(ROOT / "app/build")}
        before["fingerprint"] = digest(json.dumps({"production": production, "tests": selected_sources},
                                                  sort_keys=True, separators=(",", ":")).encode())
        write_json(directory / "inputs-before.json", before)
        node, nice = shutil.which("node"), shutil.which("nice")
        if not node or not nice:
            raise RuntimeError("node and nice are required for the shared Reader build lease")
        init = init_script(directory)
        command = [nice, "-n", "15", node, str(ROOT / "scripts/build-lease.mjs"), "--module", str(module), "--",
                   str(ROOT / "gradlew"), "--no-daemon", "--max-workers=1", "--console=plain",
                   "--init-script", str(init), TASK]
        if not args.allow_dependency_download:
            command.append("--offline")
        for selection in selections:
            command += ["--tests", selection["gradleFilter"]]
        result.update({"command": command, "inputFingerprint": before["fingerprint"],
                       "initScriptSha256": file_sha(init), "canonicalLease": lease_identity})
        write_json(directory / "plan.json", result)
        result["process"] = bounded_run(command, log=directory / "test.log", timeout=args.timeout,
                                         env={**os.environ, "GRADLE_OPTS": "-Dorg.gradle.workers.max=1"})
        result["junit"] = validate_xml(directory, expected)
        outcome_path = directory / "task-outcome.json"
        if not outcome_path.is_file():
            raise RuntimeError("Selected Gradle test task has no current outcome; no test success may be inferred")
        outcome = json.loads(outcome_path.read_text())
        result["gradleTask"] = outcome
        if outcome.get("skipMessage") == "FROM-CACHE":
            result["unitExecution"] = "REUSED_GRADLE_FROM_CACHE"
        elif outcome.get("upToDate"):
            result["unitExecution"] = "REUSED_GRADLE_UP_TO_DATE"
        elif outcome.get("noSource"):
            result["unitExecution"] = "NOT_RUN_NO_SOURCE"
        elif outcome.get("skipped"):
            result["unitExecution"] = "NOT_RUN_SKIPPED"
        else:
            result["unitExecution"] = "EXECUTED"
        if result["process"]["exitCode"] != 0 or result["process"]["timedOut"] or outcome.get("failure"):
            raise RuntimeError("Selected Gradle unit process failed or reached its deadline; original result retained")
        if outcome.get("noSource") or not outcome.get("executed") or not result["junit"]["exactSelectionPassed"]:
            raise RuntimeError("Run-private JUnit XML does not prove every selected method exactly once with zero failures/errors/skips")
        if result["unitExecution"].startswith("NOT_RUN"):
            raise RuntimeError("Selected test task was skipped without a reusable Gradle execution result")
        result["result"] = "PASS_SELECTED_UNIT_TESTS"
    except Exception as error:
        result["error"] = str(error)
    finally:
        if before is not None:
            try:
                after_production = production_inputs()
                after_tests = {name: file_sha(ROOT / name) for name in selected_sources}
                lease_unchanged = file_sha(Path(before["canonicalLease"]["module"])) == before["canonicalLease"]["sha256"]
                unchanged = after_production == before["production"] and after_tests == selected_sources and lease_unchanged
                write_json(directory / "inputs-after.json", {"capturedAt": utc_now(), "production": after_production,
                           "selectedTestSources": after_tests, "canonicalLeaseUnchanged": lease_unchanged,
                           "unchanged": unchanged})
                result["inputsUnchanged"] = unchanged
                if not unchanged:
                    result.update(result="FAIL", error="Production or selected test inputs changed during this exact run")
            except Exception as error:
                result.update(result="FAIL", error="Cannot prove final input identity: " + str(error))
        result["finishedAt"] = utc_now()
        write_json(directory / "result.json", result)
        freeze_directory(directory)
    print(json.dumps(result, indent=2, ensure_ascii=False))
    return 0 if result["result"] == "PASS_SELECTED_UNIT_TESTS" else 1


if __name__ == "__main__":
    sys.exit(main())
