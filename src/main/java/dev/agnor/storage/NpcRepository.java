package dev.agnor.storage;

import dev.agnor.character.DefinitionRegistry;
import dev.agnor.character.RuntimeSnapshot;
import java.sql.*;
import java.util.*;

/** Typed persistence boundary; gameplay services never construct SQL. */
public final class NpcRepository {
    private final WorldStorage storage;
    private final DefinitionRegistry definitions;
    public NpcRepository(WorldStorage storage, DefinitionRegistry definitions) {
        this.storage = storage; this.definitions = definitions;
    }

    public RuntimeSnapshot create(UUID world, UUID operation, RuntimeSnapshot initial) throws SQLException {
        var definition = definitions.require(initial.definition());
        if (definition.revision() != initial.definitionRevision() || initial.deleted() || initial.revision() != 0)
            throw new IllegalArgumentException("Invalid initial NPC snapshot");
        String payload = initial.owner() + "|" + initial.definition() + "|" + initial.definitionRevision() + "|"
                + initial.dimension() + "|" + initial.x() + "|" + initial.y() + "|" + initial.z() + "|" + initial.yaw() + "|" + initial.pitch();
        RuntimeSnapshot[] result = new RuntimeSnapshot[1];
        storage.transaction(world, c -> {
            UUID replay = replay(c, operation, "create", payload);
            if (replay != null) { result[0] = require(c, replay); return; }
            try (var count = c.prepareStatement("SELECT count(*),coalesce(sum(owner=?),0) FROM npc WHERE deleted=0")) {
                count.setString(1, initial.owner().toString());
                try (var rs = count.executeQuery()) {
                    rs.next(); if (rs.getInt(1) >= 64 || rs.getInt(2) >= 8) throw new SQLException("NPC limit reached");
                }
            }
            try (var insert = c.prepareStatement("INSERT INTO npc VALUES (?,?,?,?,1,?,?,?,?,?,?,0,0)")) {
                insert.setString(1, initial.id().toString()); insert.setString(2, initial.owner().toString());
                insert.setString(3, initial.definition()); insert.setInt(4, initial.definitionRevision());
                position(insert, 5, initial); insert.executeUpdate();
            }
            record(c, operation, "create", initial.id(), payload);
            result[0] = initial;
        });
        return result[0];
    }

    public List<RuntimeSnapshot> active(UUID world) throws SQLException {
        List<RuntimeSnapshot> result = new ArrayList<>();
        storage.transaction(world, c -> {
            try (var statement = c.createStatement(); var rs = statement.executeQuery("SELECT * FROM npc WHERE deleted=0 ORDER BY id")) {
                while (rs.next()) result.add(read(rs));
            }
        });
        return List.copyOf(result);
    }

    public RuntimeSnapshot find(UUID world, UUID id) throws SQLException {
        RuntimeSnapshot[] result = new RuntimeSnapshot[1];
        storage.transaction(world, c -> result[0] = require(c, id));
        return result[0];
    }

    public RuntimeSnapshot save(UUID world, RuntimeSnapshot snapshot) throws SQLException {
        storage.transaction(world, c -> {
            try (var update = c.prepareStatement("UPDATE npc SET dimension=?,x=?,y=?,z=?,yaw=?,pitch=?,revision=revision+1 WHERE id=? AND revision=? AND deleted=0")) {
                position(update, 1, snapshot); update.setString(7, snapshot.id().toString()); update.setLong(8, snapshot.revision());
                if (snapshot.deleted() || update.executeUpdate() != 1) throw new SQLException("Stale or deleted NPC snapshot");
            }
        });
        return new RuntimeSnapshot(snapshot.id(), snapshot.owner(), snapshot.definition(), snapshot.definitionRevision(),
                snapshot.dimension(), snapshot.x(), snapshot.y(), snapshot.z(), snapshot.yaw(), snapshot.pitch(), snapshot.revision()+1, false);
    }

    public void delete(UUID world, UUID operation, UUID id) throws SQLException {
        storage.transaction(world, c -> {
            UUID replay = replay(c, operation, "delete", id.toString());
            if (replay != null) return;
            require(c, id);
            try (var update = c.prepareStatement("UPDATE npc SET deleted=1,revision=revision+1 WHERE id=? AND deleted=0")) {
                update.setString(1, id.toString()); update.executeUpdate();
            }
            record(c, operation, "delete", id, id.toString());
        });
    }
    /** Idempotent assignment; updates neither identity nor relationship/history ownership. */
    public RuntimeSnapshot redefine(UUID world, UUID id, String definitionId) throws SQLException {
        var definition=definitions.require(definitionId);RuntimeSnapshot[] result=new RuntimeSnapshot[1];
        storage.transaction(world,c->{
            var before=require(c,id);if(before.deleted())throw new SQLException("NPC deleted");
            if(before.definition().equals(definition.id()) && before.definitionRevision()==definition.revision()){result[0]=before;return;}
            try(var update=c.prepareStatement("UPDATE npc SET definition=?,definition_revision=?,revision=revision+1 WHERE id=? AND deleted=0")) {
                update.setString(1,definition.id());update.setInt(2,definition.revision());update.setString(3,id.toString());update.executeUpdate();
            }
            result[0]=require(c,id);
        });return result[0];
    }

    public void relationship(UUID world, UUID npc, UUID player, int affinity) throws SQLException {
        if (affinity < -100 || affinity > 100) throw new IllegalArgumentException("Invalid affinity");
        storage.transaction(world, c -> {
            if (require(c, npc).deleted()) throw new SQLException("NPC deleted");
            try (var update = c.prepareStatement("INSERT INTO relationship VALUES (?,?,1,?) ON CONFLICT(npc,player) DO UPDATE SET affinity=excluded.affinity")) {
                update.setString(1, npc.toString()); update.setString(2, player.toString()); update.setInt(3, affinity); update.executeUpdate();
            }
        });
    }

    public int relationship(UUID world, UUID npc, UUID player) throws SQLException {
        int[] result = {0};
        storage.transaction(world, c -> {
            if (require(c, npc).deleted()) throw new SQLException("NPC deleted");
            try (var select = c.prepareStatement("SELECT affinity FROM relationship WHERE npc=? AND player=?")) {
                select.setString(1, npc.toString()); select.setString(2, player.toString());
                try (var rs = select.executeQuery()) { if (rs.next()) result[0] = rs.getInt(1); }
            }
        });
        return result[0];
    }

    private static RuntimeSnapshot require(Connection c, UUID id) throws SQLException {
        try (var select = c.prepareStatement("SELECT * FROM npc WHERE id=?")) {
            select.setString(1, id.toString());
            try (var rs = select.executeQuery()) { if (!rs.next()) throw new SQLException("NPC missing"); return read(rs); }
        }
    }
    private static RuntimeSnapshot read(ResultSet rs) throws SQLException {
        if (rs.getInt("snapshot_version") != 1) throw new SQLException("Unsupported runtime snapshot");
        return new RuntimeSnapshot(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("owner")),
                rs.getString("definition"), rs.getInt("definition_revision"), rs.getString("dimension"),
                rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch"),
                rs.getLong("revision"), rs.getBoolean("deleted"));
    }
    private static void position(PreparedStatement s, int start, RuntimeSnapshot snapshot) throws SQLException {
        s.setString(start, snapshot.dimension()); s.setDouble(start+1, snapshot.x()); s.setDouble(start+2, snapshot.y());
        s.setDouble(start+3, snapshot.z()); s.setFloat(start+4, snapshot.yaw()); s.setFloat(start+5, snapshot.pitch());
    }
    private static UUID replay(Connection c, UUID operation, String kind, String payload) throws SQLException {
        try (var select = c.prepareStatement("SELECT kind,npc,payload FROM npc_operation WHERE operation=?")) {
            select.setString(1, operation.toString());
            try (var rs = select.executeQuery()) {
                if (!rs.next()) return null;
                if (!kind.equals(rs.getString(1)) || !payload.equals(rs.getString(3))) throw new SQLException("Operation ID conflict");
                return UUID.fromString(rs.getString(2));
            }
        }
    }
    private static void record(Connection c, UUID operation, String kind, UUID npc, String payload) throws SQLException {
        try (var insert = c.prepareStatement("INSERT INTO npc_operation VALUES (?,?,?,?)")) {
            insert.setString(1, operation.toString()); insert.setString(2, kind); insert.setString(3, npc.toString());
            insert.setString(4, payload); insert.executeUpdate();
        }
    }
}
