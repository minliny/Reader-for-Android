"""Materialize the exact Core archive and Java wrapper without touching Core WIP."""

import argparse
import io
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import tarfile
import tempfile

from android_common import ROOT, config, core_repository, digest, git, write_json


def check_binding():
    expected = config()["files"]
    actual = {str(path.relative_to(ROOT)) for path in (ROOT / "app/src/core/java").rglob("*") if path.is_file()}
    if actual != {item["destination"] for item in expected}:
        raise RuntimeError("Unexpected or missing Java wrapper files")
    for item in expected:
        if digest((ROOT / item["destination"]).read_bytes()) != item["sha256"]:
            raise RuntimeError("Java binding differs from the pinned Core: " + item["destination"])


def sync_binding():
    repo = core_repository()
    for item in config()["files"]:
        data = git(repo, "show", config()["commit"] + ":" + item["source"])
        if digest(data) != item["sha256"]:
            raise RuntimeError("Pinned Java binding hash does not match dependency metadata")
        path = ROOT / item["destination"]
        if not path.exists() or path.read_bytes() != data:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
    check_binding()


def check_snapshot(path):
    if git(path, "rev-parse", "HEAD").decode().strip() != config()["commit"]:
        raise RuntimeError("Private Core snapshot HEAD differs from the fixed dependency")
    if git(path, "status", "--porcelain", "--untracked-files=all").strip():
        raise RuntimeError("Private Core archive has changed; preserve it and diagnose before building")


def snapshot():
    repo, commit = core_repository(), config()["commit"]
    destination = ROOT / ".build/core-source" / commit
    if not destination.exists():
        destination.parent.mkdir(parents=True, exist_ok=True)
        temporary = Path(tempfile.mkdtemp(dir=destination.parent, prefix=".archive-"))
        try:
            archive = git(repo, "archive", "--format=tar", commit)
            with tarfile.open(fileobj=io.BytesIO(archive), mode="r:") as stream:
                for member in stream.getmembers():
                    relative = PurePosixPath(member.name)
                    if relative.is_absolute() or ".." in relative.parts or member.isdev() or member.islnk():
                        raise RuntimeError("Unexpected archive member")
                    if member.issym() and (PurePosixPath(member.linkname).is_absolute() or ".." in PurePosixPath(member.linkname).parts):
                        raise RuntimeError("Unexpected archive symlink")
                stream.extractall(temporary)
            # build.rs reads Git identity. Only metadata is initialized; source
            # bytes are the exact archive, not a checkout of the live worktree.
            subprocess.run(["git", "init", "--quiet", str(temporary)], check=True)
            common = Path(git(repo, "rev-parse", "--git-common-dir").decode().strip())
            if not common.is_absolute():
                common = (repo / common).resolve()
            (temporary / ".git/objects/info/alternates").write_text(str(common / "objects") + "\n")
            (temporary / ".git/HEAD").write_text(commit + "\n")
            subprocess.run(["git", "-C", str(temporary), "read-tree", commit], check=True)
            check_snapshot(temporary)
            temporary.rename(destination)
            write_json(destination.with_suffix(".archive.json"), {
                "schemaVersion": 1, "commit": commit, "gitTree": git(repo, "rev-parse", commit + "^{tree}").decode().strip(),
                "archiveSha256": digest(archive), "method": "git archive; private Git metadata with read-only object alternates",
                "sourceWorktreeCopied": False})
        finally:
            if temporary.exists():
                shutil.rmtree(temporary)
    check_snapshot(destination)
    return destination


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("sync", "check", "snapshot"))
    args = parser.parse_args()
    if args.action == "sync":
        sync_binding()
    elif args.action == "check":
        check_binding()
    else:
        print(snapshot())
