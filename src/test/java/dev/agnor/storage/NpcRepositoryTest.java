package dev.agnor.storage;

import dev.agnor.character.*;
import java.nio.file.*;
import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class NpcRepositoryTest {
    @TempDir Path temporary;
    private RuntimeSnapshot initial() {
        return new RuntimeSnapshot(UUID.randomUUID(), UUID.randomUUID(), "agnor:dev", 1,
                "minecraft:overworld", 0, 80, 0, 0, 0, 0, false);
    }
    @Test void lifecycleRelationshipsAndTombstoneSurviveReopen() throws Exception {
        var snapshot = initial(); var operation = UUID.randomUUID(); var player = UUID.randomUUID();
        try (var storage = WorldStorage.open(temporary)) {
            var repo = new NpcRepository(storage, DefinitionRegistry.builtIn()); var world = storage.worldId();
            assertEquals(snapshot, repo.create(world, operation, snapshot));
            assertEquals(snapshot.id(), repo.create(world, operation, snapshot).id());
            var saved = repo.save(world, snapshot.at("minecraft:overworld", 3, 81, 4, 12, 2));
            assertEquals(1, saved.revision());
            assertThrows(SQLException.class, () -> repo.save(world, snapshot));
            repo.relationship(world, snapshot.id(), snapshot.owner(), 21);
            repo.relationship(world, snapshot.id(), player, -7);
            assertThrows(SQLException.class, () -> repo.active(UUID.randomUUID()));
        }
        try (var storage = WorldStorage.open(temporary)) {
            var repo = new NpcRepository(storage, DefinitionRegistry.builtIn()); var world = storage.worldId();
            assertEquals(3, repo.find(world, snapshot.id()).x());
            assertEquals(21, repo.relationship(world, snapshot.id(), snapshot.owner()));
            assertEquals(-7, repo.relationship(world, snapshot.id(), player));
            var deletion = UUID.randomUUID(); repo.delete(world, deletion, snapshot.id()); repo.delete(world, deletion, snapshot.id());
            assertTrue(repo.active(world).isEmpty());
            assertTrue(repo.create(world, operation, snapshot).deleted());
            assertThrows(SQLException.class, () -> repo.save(world, snapshot));
            assertThrows(SQLException.class, () -> repo.relationship(world, snapshot.id(), player, 0));
        }
    }
    @Test void conflictingOperationCannotCreateAnotherNpcAndWorldsStayIsolated() throws Exception {
        var snapshot = initial(); var operation = UUID.randomUUID();
        try (var storage = WorldStorage.open(temporary.resolve("a")); var other = WorldStorage.open(temporary.resolve("b"))) {
            var repo = new NpcRepository(storage, DefinitionRegistry.builtIn());
            repo.create(storage.worldId(), operation, snapshot);
            assertThrows(SQLException.class, () -> repo.create(storage.worldId(), operation, initial()));
            assertTrue(new NpcRepository(other, DefinitionRegistry.builtIn()).active(other.worldId()).isEmpty());
        }
    }

    @Test void ownerLimitDoesNotBreakCreateReplay() throws Exception {
        var first = initial(); var operation = UUID.randomUUID();
        try (var storage = WorldStorage.open(temporary)) {
            var repo = new NpcRepository(storage, DefinitionRegistry.builtIn());
            repo.create(storage.worldId(), operation, first);
            for (int i=1;i<8;i++) repo.create(storage.worldId(), UUID.randomUUID(), new RuntimeSnapshot(UUID.randomUUID(),first.owner(),
                    first.definition(),1,first.dimension(),0,80,0,0,0,0,false));
            assertThrows(SQLException.class, () -> repo.create(storage.worldId(), UUID.randomUUID(), new RuntimeSnapshot(UUID.randomUUID(),first.owner(),
                    first.definition(),1,first.dimension(),0,80,0,0,0,0,false)));
            assertEquals(first.id(), repo.create(storage.worldId(),operation,first).id());
            assertEquals(8,repo.active(storage.worldId()).size());
        }
    }
    @Test void migratesV1WithBackupAndPreservesWorldId() throws Exception {
        var directory = temporary.resolve("agnor"); Files.createDirectories(directory); var id = UUID.randomUUID();
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("state.db")); var s = c.createStatement()) {
            s.execute("CREATE TABLE agnor_metadata(key TEXT PRIMARY KEY,value TEXT NOT NULL)");
            s.execute("INSERT INTO agnor_metadata VALUES ('world_id','" + id + "')"); s.execute("PRAGMA user_version=1");
        }
        try (var storage = WorldStorage.open(temporary)) {
            assertEquals(id, storage.worldId());
            assertTrue(new NpcRepository(storage, DefinitionRegistry.builtIn()).active(id).isEmpty());
            try (var paths = Files.list(directory)) { assertEquals(1, paths.filter(p -> p.getFileName().toString().startsWith("state-v1-before-v2-")).count()); }
        }
    }

    @Test void failedMigrationRollsBackSchemaAndPreservesIdentity() throws Exception {
        var directory = temporary.resolve("agnor"); Files.createDirectories(directory); var id = UUID.randomUUID();
        Path database = directory.resolve("state.db");
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + database); var s = c.createStatement()) {
            s.execute("CREATE TABLE agnor_metadata(key TEXT PRIMARY KEY,value TEXT NOT NULL)");
            s.execute("INSERT INTO agnor_metadata VALUES ('world_id','" + id + "')");
            s.execute("CREATE TABLE relationship(sentinel TEXT)");
            s.execute("INSERT INTO relationship VALUES ('keep')");
            s.execute("PRAGMA user_version=1");
        }
        assertThrows(SQLException.class, () -> WorldStorage.open(temporary));
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + database); var s = c.createStatement()) {
            try (var rs = s.executeQuery("PRAGMA user_version")) { rs.next(); assertEquals(1, rs.getInt(1)); }
            try (var rs = s.executeQuery("SELECT sentinel FROM relationship")) { rs.next(); assertEquals("keep", rs.getString(1)); }
            try (var rs = s.executeQuery("SELECT value FROM agnor_metadata")) { rs.next(); assertEquals(id.toString(), rs.getString(1)); }
            try (var rs = s.executeQuery("SELECT count(*) FROM sqlite_master WHERE name='npc'")) { rs.next(); assertEquals(0, rs.getInt(1)); }
        }
    }
}
