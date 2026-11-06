package dev.agnor.storage;

import dev.agnor.lore.*;
import java.sql.*;
import java.util.*;

/** World transaction + trusted actor scope. No private-player override can be read through another actor. */
public final class LoreRepository {
    public static final UUID SHARED=new UUID(0,0);
    public record Scope(UUID world,UUID npc,UUID player) {public Scope{Objects.requireNonNull(world);Objects.requireNonNull(npc);Objects.requireNonNull(player);}}
    private final WorldStorage storage;
    public LoreRepository(WorldStorage storage){this.storage=storage;}
    static void create(Statement s)throws SQLException {
        s.execute("""
            CREATE TABLE lore_entry(npc TEXT NOT NULL REFERENCES npc(id),player TEXT NOT NULL,id TEXT NOT NULL,
              revision INTEGER NOT NULL CHECK(revision>0),payload TEXT NOT NULL,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,
              deleted INTEGER NOT NULL CHECK(deleted IN (0,1)),schema_version INTEGER NOT NULL DEFAULT 1 CHECK(schema_version=1),PRIMARY KEY(npc,player,id))
            """);
    }
    public LoreRecord put(Scope scope,UUID actor,boolean admin,LoreEntry entry,long expected)throws SQLException {
        if(expected<0)throw new IllegalArgumentException("Lore revision");LoreRecord[] result=new LoreRecord[1];
        storage.transaction(scope.world(),c->{
            access(c,scope,actor,admin,true);var before=find(c,scope,entry.id());
            if(before!=null&&!before.deleted()&&before.revision()==expected+1&&before.entry().equals(entry)){result[0]=before;return;}
            if((before==null?0:before.revision())!=expected)throw new SQLException("Lore revision conflict");
            if(before==null) {
                try(var s=c.prepareStatement("SELECT (SELECT count(*) FROM lore_entry WHERE npc=? AND player=?),(SELECT count(*) FROM lore_entry)")) {
                    s.setString(1,scope.npc().toString());s.setString(2,scope.player().toString());try(var r=s.executeQuery()){r.next();if(r.getInt(1)>=256||r.getInt(2)>=4096)throw new SQLException("Lore capacity reached");}
                }
            }
            long now=System.currentTimeMillis();
            try(var s=c.prepareStatement("INSERT INTO lore_entry(npc,player,id,revision,payload,created_at,updated_at,deleted) VALUES(?,?,?,?,?,?,?,0) ON CONFLICT(npc,player,id) DO UPDATE SET revision=excluded.revision,payload=excluded.payload,updated_at=excluded.updated_at,deleted=0")) {
                key(s,scope,entry.id());s.setLong(4,expected+1);s.setString(5,LoreJson.json(entry).toString());s.setLong(6,now);s.setLong(7,now);s.executeUpdate();
            }
            result[0]=find(c,scope,entry.id());
        });return result[0];
    }
    public void delete(Scope scope,UUID actor,boolean admin,String id,long expected)throws SQLException {
        storage.transaction(scope.world(),c->{access(c,scope,actor,admin,true);var before=find(c,scope,id);
            if(before!=null&&before.deleted()&&before.revision()==expected+1)return;
            if(before==null||before.revision()!=expected||before.deleted())throw new SQLException("Lore revision conflict");
            try(var s=c.prepareStatement("UPDATE lore_entry SET deleted=1,revision=revision+1,updated_at=? WHERE npc=? AND player=? AND id=?")) {
                s.setLong(1,System.currentTimeMillis());s.setString(2,scope.npc().toString());s.setString(3,scope.player().toString());s.setString(4,id);s.executeUpdate();
            }
        });
    }
    public LoreRecord get(Scope scope,UUID actor,String id)throws SQLException {LoreRecord[] result=new LoreRecord[1];storage.transaction(scope.world(),c->{access(c,scope,actor,false,false);result[0]=find(c,scope,id);});return result[0];}
    public List<LoreRecord> list(Scope scope,UUID actor)throws SQLException {
        var result=new ArrayList<LoreRecord>();storage.transaction(scope.world(),c->{access(c,scope,actor,false,false);
            try(var s=c.prepareStatement("SELECT * FROM lore_entry WHERE npc=? AND player=? AND deleted=0 ORDER BY id")) {
                s.setString(1,scope.npc().toString());s.setString(2,scope.player().toString());try(var r=s.executeQuery()){while(r.next())result.add(read(r,scope));}
            }
        });return List.copyOf(result);
    }
    private static void access(Connection c,Scope scope,UUID actor,boolean admin,boolean write)throws SQLException {
        if(actor==null||!scope.player().equals(SHARED)&&!scope.player().equals(actor))throw new SQLException("Lore scope denied");
        try(var s=c.prepareStatement("SELECT owner,deleted FROM npc WHERE id=?")){s.setString(1,scope.npc().toString());try(var r=s.executeQuery()){
            if(!r.next()||r.getBoolean(2)||write&&scope.player().equals(SHARED)&&!admin&&!r.getString(1).equals(actor.toString()))throw new SQLException("Lore access denied");
        }}
    }
    private static LoreRecord find(Connection c,Scope scope,String id)throws SQLException {
        try(var s=c.prepareStatement("SELECT * FROM lore_entry WHERE npc=? AND player=? AND id=?")){key(s,scope,id);try(var r=s.executeQuery()){return r.next()?read(r,scope):null;}}
    }
    private static LoreRecord read(ResultSet r,Scope scope)throws SQLException {
        try{return new LoreRecord(LoreJson.read(r.getString("id"),r.getString("payload")),r.getLong("revision"),r.getLong("updated_at"),"local:"+scope.npc()+"/"+scope.player()+"/"+r.getString("id")+"@"+r.getLong("revision"),r.getBoolean("deleted"));}
        catch(Exception e){throw new SQLException("Invalid stored Lore entry",e);}
    }
    private static void key(PreparedStatement s,Scope scope,String id)throws SQLException{s.setString(1,scope.npc().toString());s.setString(2,scope.player().toString());s.setString(3,id);}
}
