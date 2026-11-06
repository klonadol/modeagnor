package dev.agnor.storage;

import dev.agnor.dialogue.ConversationScope;
import java.sql.*;

/** Per NPC/player display/context window; retention target is separate and cannot release outbox sources. */
public final class MemoryPreferences {
    public record Value(int recentN,int retentionKeep) {public Value{if(recentN<1||recentN>100||retentionKeep<2||retentionKeep>100_000)throw new IllegalArgumentException("Memory preferences");}}
    private final WorldStorage storage;
    public MemoryPreferences(WorldStorage storage){this.storage=storage;}
    static void create(Statement s)throws SQLException {s.execute("CREATE TABLE memory_preferences(npc TEXT NOT NULL REFERENCES npc(id),player TEXT NOT NULL,recent_n INTEGER NOT NULL CHECK(recent_n BETWEEN 1 AND 100),retention_keep INTEGER NOT NULL CHECK(retention_keep BETWEEN 2 AND 100000),PRIMARY KEY(npc,player))");}
    public Value get(ConversationScope scope,int fallbackRecent)throws SQLException {
        Value[] result={new Value(fallbackRecent,1000)};storage.transaction(scope.world(),c->{requireNpc(c,scope);
            try(var s=c.prepareStatement("SELECT recent_n,retention_keep FROM memory_preferences WHERE npc=? AND player=?")){s.setString(1,scope.npc().toString());s.setString(2,scope.player().toString());try(var r=s.executeQuery()){if(r.next())result[0]=new Value(r.getInt(1),r.getInt(2));}}
        });return result[0];
    }
    public void set(ConversationScope scope,Value value)throws SQLException {storage.transaction(scope.world(),c->{requireNpc(c,scope);
        try(var s=c.prepareStatement("INSERT INTO memory_preferences VALUES(?,?,?,?) ON CONFLICT(npc,player) DO UPDATE SET recent_n=excluded.recent_n,retention_keep=excluded.retention_keep")){s.setString(1,scope.npc().toString());s.setString(2,scope.player().toString());s.setInt(3,value.recentN());s.setInt(4,value.retentionKeep());s.executeUpdate();}
    });}
    private static void requireNpc(Connection c,ConversationScope scope)throws SQLException {try(var s=c.prepareStatement("SELECT deleted FROM npc WHERE id=?")){s.setString(1,scope.npc().toString());try(var r=s.executeQuery()){if(!r.next()||r.getBoolean(1))throw new SQLException("NPC unavailable");}}}
}
