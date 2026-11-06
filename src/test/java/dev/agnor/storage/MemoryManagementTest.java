package dev.agnor.storage;

import dev.agnor.character.*;
import dev.agnor.dialogue.*;
import dev.agnor.lore.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MemoryManagementTest {
    @TempDir Path root;
    final UUID npc=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID();
    void create(WorldStorage storage)throws Exception{new NpcRepository(storage,DefinitionRegistry.builtIn()).create(storage.worldId(),UUID.randomUUID(),new RuntimeSnapshot(npc,a,"agnor:dev",1,"minecraft:overworld",0,80,0,0,0,0,false));}
    void turn(HistoryRepository repo,ConversationScope scope)throws Exception{var id=UUID.randomUUID();repo.accept(scope,id,"user");repo.claim(scope,id);repo.finalizeReply(scope,id,"assistant","fixture");}
    @Test void preferencesPersistPerPlayerAndDoNotDeleteHistory()throws Exception {
        try(var storage=WorldStorage.open(root)){create(storage);var pa=new ConversationScope(storage.worldId(),npc,a);var pb=new ConversationScope(storage.worldId(),npc,b);var prefs=new MemoryPreferences(storage);var history=new HistoryRepository(storage);turn(history,pa);turn(history,pa);
            prefs.set(pa,new MemoryPreferences.Value(1,2));assertEquals(1,prefs.get(pa,10).recentN());assertEquals(20,prefs.get(pb,20).recentN());assertEquals(4,history.page(pa,Long.MAX_VALUE,20).size());
        }
        try(var storage=WorldStorage.open(root)){assertEquals(new MemoryPreferences.Value(1,2),new MemoryPreferences(storage).get(new ConversationScope(storage.worldId(),npc,a),10));}
    }
    @Test void stableHistoryPagesAndRetentionProtectAllUnconsumedSources()throws Exception {
        try(var storage=WorldStorage.open(root)){create(storage);var scope=new ConversationScope(storage.worldId(),npc,a);var history=new HistoryRepository(storage);for(int i=0;i<4;i++)turn(history,scope);
            var latest=history.page(scope,Long.MAX_VALUE,3);assertEquals(List.of(6L,7L,8L),latest.stream().map(HistoryMessage::sequence).toList());var older=history.page(scope,6,3);turn(history,scope);assertEquals(older,history.page(scope,6,3));
            var result=history.retention(scope,2,true);assertEquals(10,result.total());assertEquals(8,result.protectedSources());assertEquals(0,result.deleted());assertEquals(10,history.events(scope,0,100).size());assertEquals(10,history.page(scope,Long.MAX_VALUE,20).size());
            assertTrue(history.page(new ConversationScope(storage.worldId(),npc,b),Long.MAX_VALUE,20).isEmpty());
        }
    }
    @Test void backupRestoreIncludesLoreHistoryPreferencesAndRejectsForeignWorld()throws Exception {
        Path backup=root.resolve("snapshot.db");
        try(var storage=WorldStorage.open(root.resolve("save"));var other=WorldStorage.open(root.resolve("other"))) {
            create(storage);var scope=new ConversationScope(storage.worldId(),npc,a);var lore=new LoreRepository(storage);var ls=new LoreRepository.Scope(storage.worldId(),npc,a);var entry=new LoreEntry("facts:x","x","ORIGINAL",LoreEntry.Mode.ALWAYS,List.of(),0,"user",true,"*","*");
            lore.put(ls,a,false,entry,0);var prefs=new MemoryPreferences(storage);prefs.set(scope,new MemoryPreferences.Value(3,10));var history=new HistoryRepository(storage);turn(history,scope);storage.backup(backup);
            prefs.set(scope,new MemoryPreferences.Value(99,100));lore.delete(ls,a,false,"facts:x",1);turn(history,scope);storage.restore(backup);
            assertEquals(3,prefs.get(scope,10).recentN());assertFalse(lore.get(ls,a,"facts:x").deleted());assertEquals(2,history.recent(scope,100).size());assertThrows(SQLException.class,()->other.restore(backup));
        }
    }
    @Test void v4UpgradePreservesExistingLoreHistoryAndMakesPreBackup()throws Exception {
        UUID world;try(var storage=WorldStorage.open(root)){create(storage);world=storage.worldId();turn(new HistoryRepository(storage),new ConversationScope(world,npc,a));}
        try(var c=DriverManager.getConnection("jdbc:sqlite:"+root.resolve("agnor/state.db"));var s=c.createStatement()){s.execute("DROP TABLE memory_preferences");s.execute("PRAGMA user_version=4");}
        try(var storage=WorldStorage.open(root)){assertEquals(world,storage.worldId());assertEquals(2,new HistoryRepository(storage).recent(new ConversationScope(world,npc,a),100).size());assertEquals(10,new MemoryPreferences(storage).get(new ConversationScope(world,npc,a),10).recentN());}
        try(var paths=Files.list(root.resolve("agnor"))){assertTrue(paths.anyMatch(p->p.getFileName().toString().startsWith("state-v4-before-v5-")));}
    }
}
