#!/usr/bin/env python3
"""Selected negative controls for retirement admission, using private temporary files."""

import argparse
import importlib.util
import json
from pathlib import Path
import shutil
import sys
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--case", choices=["restore-refusal"])
    args = parser.parse_args()
    if args.case is None:
        print("NOT_RUN restore-refusal: selected negative controls only; does not run Android tests")
        return
    root = Path(__file__).resolve().parents[2]
    sys.dont_write_bytecode = True
    spec = importlib.util.spec_from_file_location("design_input_retirement", Path(__file__).with_name("check-design-inputs.py"))
    checker = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(checker)
    policy = json.loads((root / checker.POLICY_PATH).read_text(encoding="utf-8"))
    with tempfile.TemporaryDirectory(prefix="reader-android-input-retirement-") as directory:
        fixture = Path(directory)
        for name in checker.EXECUTABLE_ROOTS:
            if (root / name).exists():
                shutil.copytree(root / name, fixture / name, ignore=shutil.ignore_patterns("__pycache__"))
        assert checker.inspect(fixture) == [], "Current candidate must be accepted before testing counterexamples"
        retired = next(item for item in policy["originalTests"] if item["retiredFile"])
        old_path = fixture / retired["path"]
        old_path.parent.mkdir(parents=True, exist_ok=True)
        old_path.write_text("// restored file with no class\n")
        assert any("Retired JUnit file restored" in e for e in checker.inspect(fixture))
        old_path.unlink()
        probe = fixture / "app/src/test/RestoredUnderNewName.kt"
        probe.write_text(f"class {Path(retired['path']).stem} {{}}\n")
        assert any("Retired JUnit class discovered" in e for e in checker.inspect(fixture))
        method = next(m for item in policy["originalTests"] for m in item["methods"] if m["outcome"] == "RETIRE")
        probe.write_text(f"class Renamed {{ fun `{method['name']}`() {{}} }}\n")
        assert any("Retired JUnit method discovered" in e for e in checker.inspect(fixture))
        probe.write_text('val restoredInput = "' + policy["withdrawnExecutableTokens"][0] + '"\n')
        assert any("Withdrawn design dependency/entry" in e for e in checker.inspect(fixture))
        probe.unlink()
        restored_root = fixture / policy["withdrawnRoots"][0]
        restored_root.mkdir(parents=True)
        assert any("Withdrawn design input restored" in e for e in checker.inspect(fixture))
        restored_root.rmdir()
        preserved = next(m for item in policy["originalTests"] for m in item["methods"] if m["outcome"] == "RETAIN")
        current_path = fixture / preserved["currentPath"]
        current_source = current_path.read_text(encoding="utf-8")
        current_path.write_text(current_source.replace(f"fun `{preserved['currentMethod']}`", "fun removedPreservedAssertion"))
        assert any("Preserved JUnit declaration missing" in e for e in checker.inspect(fixture))
        current_path.write_text(current_source)
        assert checker.inspect(fixture) == [], "Counterexamples must leave the temporary baseline intact"
        policy_path = fixture / checker.POLICY_PATH
        weakened = dict(policy)
        weakened["originalTests"] = policy["originalTests"][1:]
        policy_path.write_text(json.dumps(weakened))
        try:
            checker.inspect(fixture)
        except ValueError as error:
            assert "Retirement definition changed" in str(error)
        else:
            raise AssertionError("Removing a reviewed disposition must fail admission")
    print("PASS restore-refusal: restored path/class/method/dependency/input, removed preserved declaration and weakened disposition are rejected")
    print("NOT_RUN Kotlin/JUnit/Compose/Gradle/APK/device checks")


if __name__ == "__main__":
    main()
