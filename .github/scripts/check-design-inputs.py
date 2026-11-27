#!/usr/bin/env python3
"""Explicit, source-only admission check for withdrawn Android design inputs."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys


POLICY_PATH = Path("tools/design-input-retirement.json")
# Binds reviewed identities, original definition hashes and individual dispositions; not inventory counts.
RETIREMENT_DEFINITION_SHA256 = "89a08a69f43970ee34c71896320e109cf4ad95f14d73536971d18295ad9a7d48"
EXECUTABLE_ROOTS = ("app", ".github", "tools")
EXECUTABLE_SUFFIXES = {".kt", ".kts", ".java", ".py", ".js", ".mjs", ".sh", ".yml", ".yaml"}


def inspect(root):
    """Return violations; do not execute, import, compile or repair product code."""
    policy = json.loads((root / POLICY_PATH).read_text(encoding="utf-8"))
    fields = ("version", "withdrawnRoots", "withdrawnExecutableTokens", "retiredCiSelections", "originalTests")
    definition = json.dumps({key: policy[key] for key in fields}, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
    if hashlib.sha256(definition.encode("utf-8")).hexdigest() != RETIREMENT_DEFINITION_SHA256:
        raise ValueError("Retirement definition changed; review the exact identity, original definition and disposition before updating its pinned digest")
    errors = []
    identities = set()
    forbidden_classes = set()
    forbidden_methods = set()
    for entry in policy["originalTests"]:
        path = Path(entry["path"])
        if path.is_absolute() or ".." in path.parts:
            raise ValueError("Policy paths must stay inside this checkout")
        if entry["retiredFile"]:
            forbidden_classes.add(path.stem)
            if (root / path).exists():
                errors.append(f"Retired JUnit file restored: {path}")
        for method in entry["methods"]:
            identity = (entry["path"], method["name"])
            if identity in identities:
                raise ValueError(f"Duplicate method disposition: {identity}")
            identities.add(identity)
            outcome = method["outcome"]
            if outcome not in {"RETAIN", "MIGRATE", "RETIRE"}:
                raise ValueError(f"Unknown disposition: {outcome}")
            if outcome == "RETIRE" or outcome == "MIGRATE":
                forbidden_methods.add(method["name"])
            if outcome == "RETIRE":
                if "retiredMethod" in method:
                    forbidden_methods.add(method["retiredMethod"])
                if "retiredPath" in method:
                    former_path = Path(method["retiredPath"])
                    if former_path.is_absolute() or ".." in former_path.parts:
                        raise ValueError("Policy paths must stay inside this checkout")
                    forbidden_classes.add(former_path.stem)
                    if (root / former_path).exists():
                        errors.append(f"Retired JUnit file restored: {former_path}")
            else:
                target = root / method["currentPath"]
                declaration = f'fun `{method["currentMethod"]}`'
                if not target.is_file() or declaration not in target.read_text(encoding="utf-8"):
                    errors.append(f"Preserved JUnit declaration missing: {method['currentPath']}#{method['currentMethod']}")

    for name in policy["withdrawnRoots"]:
        if (root / name).exists():
            errors.append(f"Withdrawn design input restored: {name}")

    paths = set()
    for name in EXECUTABLE_ROOTS:
        directory = root / name
        if directory.exists():
            paths.update(p for p in directory.rglob("*") if p.is_file() and p.suffix in EXECUTABLE_SUFFIXES)
    paths.update(p for p in root.iterdir() if p.is_file() and p.suffix in EXECUTABLE_SUFFIXES)
    for path in sorted(paths):
        source = path.read_text(encoding="utf-8")
        relative = path.relative_to(root)
        for token in policy["withdrawnExecutableTokens"]:
            if token in source:
                errors.append(f"Withdrawn design dependency/entry in {relative}: {token}")
        for name in forbidden_classes:
            if re.search(r"\bclass\s+" + re.escape(name) + r"\b", source):
                errors.append(f"Retired JUnit class discovered in {relative}: {name}")
        for name in forbidden_methods:
            if f"fun `{name}`" in source:
                errors.append(f"Retired JUnit method discovered in {relative}: {name}")
    return sorted(set(errors))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--case", choices=["retired-inputs"])
    args = parser.parse_args()
    if args.case is None:
        print("NOT_RUN retired-inputs: checks retirement/admission only; no Kotlin, JUnit, Gradle, APK or device execution")
        return 0
    root = Path(__file__).resolve().parents[2]
    try:
        errors = inspect(root)
    except (OSError, ValueError, KeyError, TypeError) as error:
        print(f"FAIL retired-inputs: {error}", file=sys.stderr)
        return 1
    if errors:
        print("FAIL retired-inputs:\n" + "\n".join(errors), file=sys.stderr)
        return 1
    print("PASS retired-inputs: withdrawn inputs/entries absent and preserved test declarations present; source governance only")
    print("NOT_RUN Kotlin/JUnit/Compose/Gradle/APK/device checks")
    return 0


if __name__ == "__main__":
    sys.exit(main())
