#!/usr/bin/env python3
"""Focused offline tests of install data-protection evidence, never App tests."""

from pathlib import Path
import sqlite3
import tarfile
import tempfile
import unittest

from data_snapshot import audit_archive, compare


class DataProtectionTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="reader-android-protection-test-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.data = self.root / "data"
        (self.data / "databases").mkdir(parents=True)
        (self.data / "files").mkdir()
        (self.data / "files/original.txt").write_bytes(b"original bytes\n")
        self.database = self.data / "databases/reader.sqlite"
        with sqlite3.connect(self.database) as connection:
            connection.execute("CREATE TABLE progress(book TEXT PRIMARY KEY, chapter INTEGER, marker BLOB)")
            connection.execute("INSERT INTO progress VALUES ('book', 2, x'00ff')")

    def capture(self, name):
        path = self.root / (name + ".tar")
        with tarfile.open(path, "w") as archive:
            archive.add(self.data / "databases", arcname="databases")
            archive.add(self.data / "files", arcname="files")
        return audit_archive(path)

    def test_sqlite_progress_change_is_rejected(self):
        before = self.capture("before")
        with sqlite3.connect(self.database) as connection:
            connection.execute("UPDATE progress SET chapter=3")
        self.assertEqual(compare(before, self.capture("after"))["result"], "FAIL")

    def test_page_layout_change_keeps_semantic_proof(self):
        before = self.capture("before")
        with sqlite3.connect(self.database) as connection:
            connection.execute("PRAGMA page_size=8192")
            connection.execute("VACUUM")
        after = self.capture("after")
        self.assertNotEqual(before["archiveSha256"], after["archiveSha256"])
        self.assertEqual(compare(before, after)["result"], "PASS")

    def test_original_file_loss_is_rejected(self):
        before = self.capture("before")
        (self.data / "files/original.txt").unlink()
        self.assertEqual(compare(before, self.capture("after"))["result"], "FAIL")

    def test_wal_rows_are_included_in_semantic_proof(self):
        connection = sqlite3.connect(self.database)
        self.addCleanup(connection.close)
        connection.execute("PRAGMA journal_mode=WAL")
        before = self.capture("before")
        connection.execute("INSERT INTO progress VALUES ('second', 7, x'01')")
        connection.commit()
        after = self.capture("after")
        self.assertEqual(after["sqlite"]["databases/reader.sqlite"]["tables"]["progress"]["rows"], 2)
        self.assertEqual(compare(before, after)["result"], "FAIL")


if __name__ == "__main__":
    unittest.main()
