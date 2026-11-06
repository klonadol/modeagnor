package dev.agnor.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.UUID;

/** Minimal SQLite handle bound to one Minecraft save and its immutable WorldId. */
public final class WorldStorage implements AutoCloseable {
    public static final int SCHEMA_VERSION = 5;
    private final Path database;
    private final UUID worldId;
    private boolean closed;

    private WorldStorage(Path database, UUID worldId) {
        this.database = database;
        this.worldId = worldId;
    }

    public static WorldStorage open(Path serverSaveRoot) throws IOException, SQLException {
        Path directory = serverSaveRoot.toAbsolutePath().normalize().resolve("agnor");
        Files.createDirectories(directory);
        Path database = directory.resolve("state.db");
        boolean existed = Files.exists(database);
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
        try (connection; Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA journal_mode=DELETE");
            statement.execute("PRAGMA synchronous=FULL");
            int sourceVersion;
            try (var rs = statement.executeQuery("PRAGMA user_version")) { rs.next(); sourceVersion = rs.getInt(1); }
            if (sourceVersion > SCHEMA_VERSION) throw new SQLException("Unsupported newer AGNOR schema: " + sourceVersion);
            if (sourceVersion > 0 && sourceVersion < SCHEMA_VERSION) {
                try (var rs = statement.executeQuery("SELECT value FROM agnor_metadata WHERE key='world_id'")) {
                    if (!rs.next()) throw new SQLException("Missing AGNOR WorldId");
                    UUID.fromString(rs.getString(1));
                }
                Path backup = directory.resolve("state-v" + sourceVersion + "-before-v" + (sourceVersion == 1 ? 2 : SCHEMA_VERSION) + "-" + UUID.randomUUID() + ".db");
                statement.execute("VACUUM INTO '" + backup.toString().replace("'", "''") + "'");
            }
            statement.execute("BEGIN IMMEDIATE");
            try {
                int version;
                try (var rs = statement.executeQuery("PRAGMA user_version")) { rs.next(); version = rs.getInt(1); }
                if (version > SCHEMA_VERSION) throw new SQLException("Unsupported newer AGNOR schema: " + version);
                if (version == 0) {
                    if (existed) throw new SQLException("Existing database has no supported schema; refusing reset");
                    try (var tables = statement.executeQuery("SELECT count(*) FROM sqlite_master WHERE type='table'")) {
                        if (!tables.next() || tables.getInt(1) != 0) throw new SQLException("Unversioned nonempty database");
                    }
                    statement.execute("CREATE TABLE agnor_metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
                    statement.execute("INSERT INTO agnor_metadata VALUES ('world_id','" + UUID.randomUUID() + "')");
                }
                if (version < 2) NpcSchema.create(statement);
                if (version < 3) HistorySchema.create(statement);
                if (version < 4) LoreRepository.create(statement);
                if (version < 5) MemoryPreferences.create(statement);
                statement.execute("PRAGMA user_version=" + SCHEMA_VERSION);
                try (var rs = statement.executeQuery("SELECT value FROM agnor_metadata WHERE key='world_id'")) {
                    if (!rs.next()) throw new SQLException("Missing AGNOR WorldId");
                    UUID worldId = UUID.fromString(rs.getString(1));
                    statement.execute("COMMIT");
                    return new WorldStorage(database, worldId);
                }
            } catch (Exception e) {
                try { statement.execute("ROLLBACK"); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                if (e instanceof SQLException sql) throw sql;
                throw new SQLException("Could not initialize AGNOR world storage", e);
            }
        }
    }

    public UUID worldId() { return worldId; }
    public Path databasePath() { return database; }

    /** Infrastructure-only JDBC boundary; gameplay uses typed repositories. */
    synchronized void transaction(UUID expectedWorldId, TransactionWork work) throws SQLException {
        ensureOpen();
        if (!worldId.equals(Objects.requireNonNull(expectedWorldId))) throw new SQLException("World scope mismatch");
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                work.run(connection);
                connection.commit();
            } catch (Exception e) {
                try { connection.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                if (e instanceof SQLException sql) throw sql;
                throw new SQLException("AGNOR transaction rolled back", e);
            }
        }
    }

    /** Creates a consistent SQLite snapshot; the destination must not already exist. */
    public synchronized void backup(Path destination) throws SQLException, IOException {
        ensureOpen();
        Path target = destination.toAbsolutePath().normalize();
        if (target.equals(database) || Files.exists(target)) throw new IOException("Backup destination must be a new file");
        Files.createDirectories(target.getParent());
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            String escaped = target.toString().replace("'", "''");
            try { statement.execute("VACUUM INTO '" + escaped + "'"); validateSnapshot(target); }
            catch (Exception e) { Files.deleteIfExists(target); if (e instanceof IOException io) throw io; throw new SQLException("Backup validation failed", e); }
        }
    }

    /** Restores only a valid snapshot from this same world and current schema. */
    public synchronized void restore(Path backup) throws SQLException, IOException {
        ensureOpen();
        Path source = backup.toAbsolutePath().normalize();
        if (source.equals(database)) throw new IOException("Restore source is the active database");
        validateSnapshot(source);
        try (Connection check = DriverManager.getConnection("jdbc:sqlite:" + source)) {
            try (var rs = check.createStatement().executeQuery("SELECT value FROM agnor_metadata WHERE key='world_id'")) {
                if (!rs.next() || !worldId.toString().equals(rs.getString(1))) throw new SQLException("Backup belongs to another world");
            }
        }
        Path staged = Files.createTempFile(database.getParent(), "restore-", ".db");
        try {
            Files.copy(source, staged, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            validateSnapshot(staged);
            Files.move(staged, database, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(staged); }
    }

    private static void validateSnapshot(Path path) throws SQLException {
        if (!Files.isRegularFile(path)) throw new SQLException("Snapshot file does not exist");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path)) {
            var expected=new java.util.HashSet<>(java.util.Set.of("agnor_metadata","npc","relationship","npc_operation","conversation","dialogue_turn","history_message","dialogue_event","lore_entry","memory_preferences"));
            try(var rs=connection.createStatement().executeQuery("SELECT name FROM sqlite_master WHERE type='table'")){while(rs.next())expected.remove(rs.getString(1));}
            if(!expected.isEmpty())throw new SQLException("Snapshot is missing required memory tables");
            try (var rs = connection.createStatement().executeQuery("PRAGMA integrity_check")) {
                if (!rs.next() || !"ok".equals(rs.getString(1))) throw new SQLException("SQLite integrity check failed");
            }
            try (var rs = connection.createStatement().executeQuery("PRAGMA user_version")) {
                if (!rs.next() || rs.getInt(1) != SCHEMA_VERSION) throw new SQLException("Backup schema version mismatch");
            }
            try(var rs=connection.createStatement().executeQuery("PRAGMA foreign_key_check")){if(rs.next())throw new SQLException("Backup foreign key check failed");}
        }
    }

    private Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA synchronous=FULL");
            statement.execute("PRAGMA busy_timeout=1000");
            return connection;
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
    }
    private void ensureOpen() throws SQLException { if (closed) throw new SQLException("World storage is closed"); }
    @Override public synchronized void close() { closed = true; }

    @FunctionalInterface interface TransactionWork { void run(Connection connection) throws Exception; }
}
