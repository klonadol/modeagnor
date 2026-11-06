package dev.agnor.storage;

import java.sql.*;

final class HistorySchema {
    static void create(Statement s) throws SQLException {
        s.execute("""
            CREATE TABLE conversation(id TEXT PRIMARY KEY,npc TEXT NOT NULL REFERENCES npc(id),player TEXT NOT NULL,
                next_sequence INTEGER NOT NULL DEFAULT 1 CHECK(next_sequence>0),UNIQUE(npc,player))
            """);
        s.execute("""
            CREATE TABLE dialogue_turn(conversation TEXT NOT NULL REFERENCES conversation(id),request TEXT NOT NULL,
                state TEXT NOT NULL CHECK(state IN ('ACCEPTED','RUNNING','FINALIZED','FAILED','CANCELLED','INTERRUPTED')),
                error TEXT NOT NULL DEFAULT '',PRIMARY KEY(conversation,request))
            """);
        s.execute("""
            CREATE TABLE history_message(id TEXT PRIMARY KEY,conversation TEXT NOT NULL,request TEXT NOT NULL,
                sequence INTEGER NOT NULL CHECK(sequence>0),role TEXT NOT NULL CHECK(role IN ('user','assistant')),
                text TEXT NOT NULL CHECK(length(text) BETWEEN 1 AND 4096),created_at INTEGER NOT NULL,model TEXT NOT NULL,
                schema_version INTEGER NOT NULL DEFAULT 1 CHECK(schema_version=1),
                FOREIGN KEY(conversation,request) REFERENCES dialogue_turn(conversation,request),
                UNIQUE(conversation,sequence),UNIQUE(conversation,request,role))
            """);
        // Required by the History commit invariant, not a scheduler or consumer delivery table.
        s.execute("""
            CREATE TABLE dialogue_event(event_sequence INTEGER PRIMARY KEY AUTOINCREMENT,id TEXT NOT NULL UNIQUE,
                message TEXT NOT NULL UNIQUE REFERENCES history_message(id),conversation TEXT NOT NULL REFERENCES conversation(id),
                message_sequence INTEGER NOT NULL,kind TEXT NOT NULL CHECK(kind IN ('INBOUND_ACCEPTED','REPLY_FINALIZED')),
                schema_version INTEGER NOT NULL DEFAULT 1 CHECK(schema_version=1),UNIQUE(conversation,message_sequence))
            """);
        s.execute("CREATE INDEX dialogue_event_scope ON dialogue_event(conversation,event_sequence)");
    }
}
