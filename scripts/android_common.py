"""Shared local Android build helpers. No device operations occur on import."""

from contextlib import contextmanager
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import signal
import subprocess
import tempfile
import time

ROOT = Path(__file__).resolve().parents[1]
CONFIG_PATH = ROOT / "scripts/core-dependency.json"


def config():
    return json.loads(CONFIG_PATH.read_text())


def utc_now():
    return datetime.now(timezone.utc).isoformat()


def digest(data):
    return hashlib.sha256(data).hexdigest()


def file_sha(path):
    value = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


def write_json(path, document):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=path.parent, prefix=".write-", delete=False) as stream:
        temporary = Path(stream.name)
        stream.write((json.dumps(document, indent=2, ensure_ascii=False) + "\n").encode())
    try:
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def git(repo, *args):
    return subprocess.check_output(
        ["git", "--no-optional-locks", "-C", str(repo), *args], stderr=subprocess.PIPE
    )


def core_repository():
    configured = os.environ.get("READER_CORE_ROOT")
    candidates = [Path(configured)] if configured else [parent / "Reader-Core" for parent in ROOT.parents]
    for candidate in candidates:
        if not candidate.is_dir():
            continue
        try:
            actual = git(candidate, "rev-parse", config()["commit"] + "^{commit}").decode().strip()
        except subprocess.CalledProcessError:
            continue
        if actual == config()["commit"]:
            return candidate.resolve()
    raise RuntimeError("Pinned Core commit is unavailable; set READER_CORE_ROOT to its local repository")


def android_sdk():
    configured = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if configured:
        return Path(configured).expanduser().resolve()
    local = ROOT / "local.properties"
    if local.is_file():
        for line in local.read_text().splitlines():
            if line.startswith("sdk.dir="):
                return Path(line.split("=", 1)[1].replace("\\:", ":").replace("\\ ", " ")).expanduser().resolve()
    raise RuntimeError("Set ANDROID_HOME or sdk.dir in ignored local.properties; no SDK is auto-installed")


def bounded_run(command, *, log, timeout, cwd=ROOT, env=None):
    """Bound and stop only this subprocess family; no automatic retry."""
    started = time.monotonic()
    with Path(log).open("wb") as output:
        child = subprocess.Popen(command, cwd=cwd, env=env, stdin=subprocess.DEVNULL,
                                 stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
        timed_out = False
        try:
            status = child.wait(timeout=timeout)
        except (subprocess.TimeoutExpired, KeyboardInterrupt):
            timed_out = True
            os.killpg(child.pid, signal.SIGTERM)
            try:
                status = child.wait(timeout=5)
            except subprocess.TimeoutExpired:
                os.killpg(child.pid, signal.SIGKILL)
                status = child.wait(timeout=5)
    return {"exitCode": status, "timedOut": timed_out,
            "durationSeconds": round(time.monotonic() - started, 3), "retries": 0}


def freeze_directory(directory):
    for path in sorted(Path(directory).rglob("*"), reverse=True):
        if not path.is_symlink():
            path.chmod(path.stat().st_mode & ~0o222)
    Path(directory).chmod(Path(directory).stat().st_mode & ~0o222)


def git_identity():
    return {"commit": git(ROOT, "rev-parse", "HEAD").decode().strip(),
            "dirty": bool(git(ROOT, "status", "--porcelain", "--untracked-files=normal").strip())}
