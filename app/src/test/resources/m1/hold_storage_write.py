"""Hold a write fence in the disposable host test library until stdin is signaled."""
import sqlite3
import sys

with sqlite3.connect(sys.argv[1], timeout=3) as db:
    db.execute("BEGIN IMMEDIATE")
    print("locked", flush=True)
    if not sys.stdin.readline():
        raise RuntimeError("probe stopped before releasing the write fence")
    db.rollback()
