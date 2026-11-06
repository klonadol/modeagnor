package dev.agnor.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class WorldStorageTest {
    @TempDir Path temporary;

    @Test void initializesSchemaAndRejectsWrongWorldScope() throws Exception {
        try (WorldStorage storage = WorldStorage.open(temporary.resolve("save"))) {
            assertEquals(WorldStorage.SCHEMA_VERSION, schemaVersion(storage));
            assertThrows(java.sql.SQLException.class, () -> storage.transaction(UUID.randomUUID(), c -> {}));
            assertEquals(0, rowCount(storage));
        }
    }

    @Test void missingIdentityIsNeverRegeneratedAndMissingBackupIsNotCreated() throws Exception {
        Path save = temporary.resolve("damaged");
        try (WorldStorage storage = WorldStorage.open(save)) {
            storage.transaction(storage.worldId(), c -> c.createStatement().execute("DELETE FROM agnor_metadata"));
            Path absent = temporary.resolve("missing.db");
            assertThrows(java.sql.SQLException.class, () -> storage.restore(absent));
            assertFalse(Files.exists(absent));
        }
        assertThrows(java.sql.SQLException.class, () -> WorldStorage.open(save));
    }

    @Test void existingEmptyDatabaseIsNotSilentlyReset() throws Exception {
        Files.createDirectories(temporary.resolve("agnor"));
        Path database = temporary.resolve("agnor/state.db"); Files.createFile(database);
        assertThrows(java.sql.SQLException.class, () -> WorldStorage.open(temporary));
        assertEquals(0, Files.size(database));
    }

    @Test void transactionRollbackAndWorldIdentitySurviveReopen() throws Exception {
        UUID id;
        try (WorldStorage storage = WorldStorage.open(temporary.resolve("save"))) {
            id = storage.worldId();
            assertThrows(java.sql.SQLException.class, () -> storage.transaction(id, c -> {
                c.createStatement().executeUpdate("CREATE TABLE example(value TEXT)");
                throw new IllegalStateException("fixture rollback");
            }));
            assertEquals(0, rowCount(storage));
        }
        try (WorldStorage reopened = WorldStorage.open(temporary.resolve("save"))) {
            assertEquals(id, reopened.worldId());
            assertEquals(0, rowCount(reopened));
        }
    }

    @Test void backupAndRestoreStayWithinSameSchemaAndWorld() throws Exception {
        Path save = temporary.resolve("save");
        Path backup = temporary.resolve("backup.db");
        try (WorldStorage storage = WorldStorage.open(save)) {
            UUID id = storage.worldId();
            storage.transaction(id, c -> c.createStatement().executeUpdate("CREATE TABLE example(value TEXT)"));
            storage.backup(backup);
            storage.transaction(id, c -> c.createStatement().executeUpdate("DROP TABLE example"));
            storage.restore(backup);
            assertEquals(WorldStorage.SCHEMA_VERSION, schemaVersion(storage));
            assertEquals(1, tableCount(storage, "example"));
            assertThrows(java.io.IOException.class, () -> storage.backup(backup));
        }
        Path foreignSave = temporary.resolve("foreign");
        try (WorldStorage foreign = WorldStorage.open(foreignSave)) {
            assertThrows(java.sql.SQLException.class, () -> foreign.restore(backup));
            assertEquals(0, tableCount(foreign, "example"));
        }
    }

    @Test void configSavesReloadsAndRefusesInvalidValues() throws Exception {
        Path save = temporary.resolve("save");
        assertEquals(WorldConfig.DEFAULT, WorldConfig.load(save));
        WorldConfig value = new WorldConfig(false, 45);
        value.save(save);
        assertEquals(value, WorldConfig.load(save));
        Files.writeString(save.resolve("agnor/config.properties"), "version=99\nenabled=true\nautosaveSeconds=30\n");
        assertThrows(java.io.IOException.class, () -> WorldConfig.load(save));
        Files.writeString(save.resolve("agnor/config.properties"), "version=1\nenabled=maybe\nautosaveSeconds=30\n");
        assertThrows(java.io.IOException.class, () -> WorldConfig.load(save));
        assertThrows(IllegalArgumentException.class, () -> new WorldConfig(true, 1));
    }

    @Test void everyTransactionEnforcesForeignKeys() throws Exception {
        try (WorldStorage storage = WorldStorage.open(temporary.resolve("save"))) {
            storage.transaction(storage.worldId(), c -> {
                c.createStatement().execute("CREATE TABLE parent(id INTEGER PRIMARY KEY)");
                c.createStatement().execute("CREATE TABLE child(parent INTEGER REFERENCES parent(id))");
            });
            assertThrows(java.sql.SQLException.class, () -> storage.transaction(storage.worldId(), c ->
                c.createStatement().execute("INSERT INTO child VALUES (99)")));
        }
    }

    @Test void sqliteFullPreservesPreviouslyCommittedState() throws Exception {
        Path save = temporary.resolve("full");
        UUID id;
        try (WorldStorage storage = WorldStorage.open(save)) {
            id = storage.worldId();
            storage.transaction(id, c -> {
                c.createStatement().execute("CREATE TABLE payload(value BLOB)");
                c.createStatement().execute("INSERT INTO payload VALUES (X'01')");
            });
            var error = assertThrows(java.sql.SQLException.class, () -> storage.transaction(id, c -> {
                int pages;
                try (var rs = c.createStatement().executeQuery("PRAGMA page_count")) { rs.next(); pages = rs.getInt(1); }
                c.createStatement().execute("PRAGMA max_page_count=" + pages);
                c.createStatement().execute("UPDATE payload SET value=zeroblob(1048576)");
            }));
            assertEquals(13, error.getErrorCode()); // SQLITE_FULL from the actual engine
        }
        try (WorldStorage storage = WorldStorage.open(save)) {
            assertEquals(id, storage.worldId());
            storage.transaction(id, c -> {
                try (var rs = c.createStatement().executeQuery("SELECT hex(value) FROM payload")) {
                    assertTrue(rs.next()); assertEquals("01", rs.getString(1)); assertFalse(rs.next());
                }
            });
        }
    }

    private static int schemaVersion(WorldStorage storage) throws Exception {
        try (var c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + storage.databasePath());
             var rs = c.createStatement().executeQuery("PRAGMA user_version")) { rs.next(); return rs.getInt(1); }
    }
    private static int rowCount(WorldStorage storage) throws Exception {
        try (var c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + storage.databasePath());
             var rs = c.createStatement().executeQuery("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='example'")) { rs.next(); return rs.getInt(1); }
    }
    private static int tableCount(WorldStorage storage, String table) throws Exception {
        try (var c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + storage.databasePath());
             var rs = c.createStatement().executeQuery("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='" + table + "'")) { rs.next(); return rs.getInt(1); }
    }
}
