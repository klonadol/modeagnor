package dev.agnor.storage;

import java.sql.SQLException;
import java.sql.Statement;

final class NpcSchema {
    private NpcSchema() {}
    static void create(Statement statement) throws SQLException {
        statement.execute("""
            CREATE TABLE npc (
                id TEXT PRIMARY KEY, owner TEXT NOT NULL, definition TEXT NOT NULL, definition_revision INTEGER NOT NULL,
                snapshot_version INTEGER NOT NULL CHECK(snapshot_version=1), dimension TEXT NOT NULL,
                x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, yaw REAL NOT NULL, pitch REAL NOT NULL,
                revision INTEGER NOT NULL CHECK(revision>=0), deleted INTEGER NOT NULL CHECK(deleted IN (0,1)))
            """);
        statement.execute("""
            CREATE TABLE relationship (
                npc TEXT NOT NULL REFERENCES npc(id), player TEXT NOT NULL,
                schema_version INTEGER NOT NULL CHECK(schema_version=1), affinity INTEGER NOT NULL CHECK(affinity BETWEEN -100 AND 100),
                PRIMARY KEY(npc,player))
            """);
        statement.execute("""
            CREATE TABLE npc_operation (
                operation TEXT PRIMARY KEY, kind TEXT NOT NULL CHECK(kind IN ('create','delete')),
                npc TEXT NOT NULL REFERENCES npc(id), payload TEXT NOT NULL)
            """);
    }
}
