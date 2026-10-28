"""Audit a stopped, read-only run-as archive with SQLite semantic fingerprints."""

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import sqlite3
import tarfile
import tempfile

from android_common import digest, file_sha, write_json


def sql_name(value):
    return '"' + value.replace('"', '""') + '"'


def encoded_value(value):
    if value is None:
        return ["null"]
    if isinstance(value, bytes):
        return ["blob", value.hex()]
    if isinstance(value, int):
        return ["integer", str(value)]
    if isinstance(value, float):
        return ["real", value.hex()]
    return ["text", value]


def sqlite_fingerprint(path):
    # SQLite operates only on the private extracted copy. Device DBs and
    # journals are never opened by SQLite or altered by this helper.
    # A stopped WAL-mode database can have no -wal/-shm left. SQLite needs to
    # recreate those coordination files even for SELECT. Permit that only in
    # this disposable local copy; retain the raw archive and forbid SQL writes.
    with sqlite3.connect(path.resolve().as_uri() + "?mode=rw", uri=True) as connection:
        connection.execute("PRAGMA query_only=ON")
        schema = connection.execute("SELECT type,name,tbl_name,sql FROM sqlite_master ORDER BY type,name").fetchall()
        value = {"userVersion": connection.execute("PRAGMA user_version").fetchone()[0],
                 "applicationId": connection.execute("PRAGMA application_id").fetchone()[0],
                 "schemaSha256": digest(json.dumps(schema, ensure_ascii=False, separators=(",", ":")).encode()),
                 "tables": {}}
        table_kinds = {item[1]: item for item in connection.execute("PRAGMA table_list")}
        for name, in connection.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"):
            declared = {item[1].lower() for item in connection.execute("PRAGMA table_xinfo(" + sql_name(name) + ")")}
            if name not in table_kinds:
                raise RuntimeError("SQLite table metadata is unavailable; no partial semantic proof is accepted")
            rowid = next((item for item in ("_rowid_", "rowid", "oid") if item not in declared), None)
            without_rowid = bool(table_kinds[name][4])
            if not without_rowid and rowid is None:
                raise RuntimeError("All SQLite row identity aliases are shadowed; preservation needs a specific adapter")
            selector = "*" if without_rowid else rowid + ", *"
            cursor = connection.execute("SELECT " + selector + " FROM " + sql_name(name))
            rows = []
            for row in cursor:
                encoded = json.dumps([encoded_value(item) for item in row], ensure_ascii=False, separators=(",", ":")).encode()
                rows.append(hashlib.sha256(encoded).digest())
            combined = hashlib.sha256()
            for item in sorted(rows):
                combined.update(item)
            value["tables"][name] = {"rows": len(rows), "columns": [item[0] for item in cursor.description],
                                     "rowsSha256": combined.hexdigest()}
        return value


def audit_archive(archive_path):
    with tempfile.TemporaryDirectory(prefix="reader-android-data-") as temporary:
        root = Path(temporary)
        with tarfile.open(archive_path) as archive:
            seen = set()
            for member in archive.getmembers():
                path = PurePosixPath(member.name)
                if path.is_absolute() or ".." in path.parts or not (member.isfile() or member.isdir()):
                    raise RuntimeError("Unexpected app-data archive entry; preservation cannot be established")
                normalized = str(path)
                if normalized in seen:
                    raise RuntimeError("Duplicate app-data archive path")
                seen.add(normalized)
            archive.extractall(root)
        files = sorted(path for path in root.rglob("*") if path.is_file())
        databases = {}
        for path in files:
            with path.open("rb") as stream:
                if stream.read(16) == b"SQLite format 3\x00":
                    databases[path.relative_to(root).as_posix()] = sqlite_fingerprint(path)
        families = {name + suffix for name in databases for suffix in ("", "-wal", "-shm", "-journal")}
        regular = {path.relative_to(root).as_posix(): {"sha256": file_sha(path), "bytes": path.stat().st_size}
                   for path in files if path.relative_to(root).as_posix() not in families}
        return {"schemaVersion": 1, "archiveSha256": file_sha(archive_path), "files": regular,
                "sqlite": databases, "directories": sorted(path.relative_to(root).as_posix()
                                                             for path in root.rglob("*") if path.is_dir())}


def compare(before, after):
    problems = []
    for category in ("files", "sqlite"):
        for name, original in before[category].items():
            if after[category].get(name) != original:
                problems.append(category + ":" + name)
    for name in before["directories"]:
        if name not in after["directories"]:
            problems.append("directory:" + name)
    return {"result": "PASS" if not problems else "FAIL", "changedOrMissingProtectedEntries": problems,
            "beforeProtectedFiles": len(before["files"]), "afterProtectedFiles": len(after["files"]),
            "beforeDatabases": len(before["sqlite"]), "afterDatabases": len(after["sqlite"]),
            "newEntriesPermitted": True, "deviceDataMutatedByAudit": False}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    write_json(args.output, audit_archive(args.archive))
