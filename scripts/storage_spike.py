"""P1.1 disposable backend experiment; not a Storage Facade or mod dependency.

Run: python scripts/storage_spike.py
Uses fresh synthetic saves under build/p1.1-evidence on every run.
"""
import copy
import json
import os
from pathlib import Path
import platform
import sqlite3
import subprocess
import sys
import tempfile

NPC = "00000000-0000-0000-0000-000000000001"
PLAYERS = ["00000000-0000-0000-0000-000000000002",
           "00000000-0000-0000-0000-000000000003"]


def initial(world):
    return {"schema": 1, "world": world,
            "npc": {"id": NPC, "definition": "agnor:dev", "x": 0},
            "relationships": {p: i for i, p in enumerate(PLAYERS)}}


def connect(path):
    db = sqlite3.connect(path, isolation_level=None)
    db.execute("PRAGMA foreign_keys=ON")
    assert db.execute("PRAGMA journal_mode=DELETE").fetchone()[0] == "delete"
    db.execute("PRAGMA synchronous=FULL")
    return db


def create(kind, path, world):
    path.parent.mkdir(parents=True)
    if kind == "json":
        path.write_text(json.dumps(initial(world)), encoding="utf-8")
        return
    db = connect(path)
    try:
        db.executescript("""
            BEGIN IMMEDIATE;
            CREATE TABLE metadata(world TEXT PRIMARY KEY);
            CREATE TABLE npc(id TEXT PRIMARY KEY, definition TEXT NOT NULL, x INTEGER NOT NULL);
            CREATE TABLE relationship(npc TEXT REFERENCES npc(id), player TEXT NOT NULL,
                affinity INTEGER NOT NULL, PRIMARY KEY(npc, player));
            PRAGMA user_version=1;
        """)
        db.execute("INSERT INTO metadata VALUES (?)", (world,))
        db.execute("INSERT INTO npc VALUES (?, 'agnor:dev', 0)", (NPC,))
        db.executemany("INSERT INTO relationship VALUES (?, ?, ?)",
                       [(NPC, p, i) for i, p in enumerate(PLAYERS)])
        db.execute("COMMIT")
    finally:
        db.close()


def read(kind, path):
    if kind == "json":
        return json.loads(path.read_text(encoding="utf-8"))
    db = connect(path)
    try:
        version = db.execute("PRAGMA user_version").fetchone()[0]
        row = db.execute("SELECT * FROM npc").fetchone()
        result = {"schema": version, "world": db.execute("SELECT world FROM metadata").fetchone()[0],
                  "npc": dict(zip(["id", "definition", "x", "revision"], row)),
                  "relationships": dict(db.execute("SELECT player, affinity FROM relationship"))}
        assert db.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
        assert not db.execute("PRAGMA foreign_key_check").fetchall()
        return result
    finally:
        db.close()


def change(kind, path, migration=False, failure="none"):
    if kind == "json":
        value = read(kind, path)
        if migration:
            value["npc"]["revision"] = 0
            value["schema"] = 2
        else:
            value["npc"]["x"] = 10
            value["relationships"][PLAYERS[0]] = 7
            value["relationships"][PLAYERS[1]] = 9
        temp = path.with_suffix(".tmp")
        with temp.open("w", encoding="utf-8") as stream:
            json.dump(value, stream)
            stream.flush()
            os.fsync(stream.fileno())
        if failure == "crash":
            os._exit(73)  # durable temp exists, canonical snapshot unchanged
        if failure == "rollback":
            temp.unlink()
            return
        os.replace(temp, path)
        return
    db = connect(path)
    try:
        db.execute("BEGIN IMMEDIATE")
        if migration:
            db.execute("ALTER TABLE npc ADD COLUMN revision INTEGER NOT NULL DEFAULT 0")
            db.execute("PRAGMA user_version=2")
        else:
            db.execute("UPDATE npc SET x=10 WHERE id=?", (NPC,))
            db.execute("UPDATE relationship SET affinity=7 WHERE player=?", (PLAYERS[0],))
            if failure == "crash":
                os._exit(73)  # deliberately leave partial transaction uncommitted
            db.execute("UPDATE relationship SET affinity=9 WHERE player=?", (PLAYERS[1],))
        if failure == "crash":
            os._exit(73)
        db.execute("ROLLBACK" if failure == "rollback" else "COMMIT")
    finally:
        db.close()


def child(*args, expected=0):
    result = subprocess.run([sys.executable, str(Path(__file__).resolve()), *map(str, args)],
                            capture_output=True, text=True, encoding="utf-8")
    assert result.returncode == expected, (result.returncode, result.stdout, result.stderr)
    return json.loads(result.stdout) if result.stdout else None


def experiment():
    evidence = Path(__file__).resolve().parents[1] / "build/p1.1-evidence"
    evidence.mkdir(parents=True, exist_ok=True)
    run = Path(tempfile.mkdtemp(prefix="storage-", dir=evidence))
    report = {"python": platform.python_version(), "sqlite": sqlite3.sqlite_version,
              "platform": platform.platform(), "fixture_root": str(run), "results": {}}
    for kind in ("sqlite", "json"):
        a = run / kind / "save A/agnor" / ("state.db" if kind == "sqlite" else "state.json")
        b = run / kind / "save B/agnor" / a.name
        create(kind, a, "00000000-0000-0000-0000-00000000000a")
        create(kind, b, "00000000-0000-0000-0000-00000000000b")
        before = child("read", kind, a)
        other = child("read", kind, b)
        checks = []
        assert before == initial(before["world"])
        checks.append("one NPC + two independent relationships")
        change(kind, a, failure="rollback")
        assert child("read", kind, a) == before
        checks.append("explicit rollback preserves entire snapshot")
        child("crash", kind, a, expected=73)
        assert child("read", kind, a) == before
        checks.append("process exit before commit preserves entire snapshot on reopen")
        change(kind, a)
        expected = copy.deepcopy(before)
        expected["npc"]["x"] = 10
        expected["relationships"] = dict(zip(PLAYERS, (7, 9)))
        assert child("read", kind, a) == expected
        checks.append("atomic update and fresh-process restart")
        change(kind, a, migration=True, failure="rollback")
        assert child("read", kind, a) == expected
        child("migration-crash", kind, a, expected=73)
        assert child("read", kind, a) == expected
        checks.append("failed migration keeps old schema and data")
        change(kind, a, migration=True)
        expected["schema"] = 2
        expected["npc"]["revision"] = 0
        assert child("read", kind, a) == expected
        checks.append("v1 to v2 migration and fresh-process restart")
        assert child("read", kind, b) == other
        checks.append("second save unchanged with identical NPC/player IDs")
        report["results"][kind] = checks
    output = json.dumps(report, indent=2)
    (run / "results.json").write_text(output + "\n", encoding="utf-8")
    print(output)


if __name__ == "__main__":
    if len(sys.argv) == 1:
        experiment()
    else:
        action, backend, filename = sys.argv[1:]
        if action == "read":
            print(json.dumps(read(backend, Path(filename))))
        elif action in ("crash", "migration-crash"):
            change(backend, Path(filename), migration=action == "migration-crash", failure="crash")
        else:
            raise ValueError(action)
