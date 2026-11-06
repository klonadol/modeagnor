package dev.agnor.content;

import dev.agnor.lore.*;
import dev.agnor.storage.*;
import dev.agnor.character.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LoreBookTest {
    @TempDir Path root;
    final UUID owner=UUID.randomUUID(),other=UUID.randomUUID(),npc=UUID.randomUUID();
    LoreEntry entry(String text){return new LoreEntry("facts:gate","Gate",text,LoreEntry.Mode.KEYWORD,List.of("ворота"),1,"system",true,"*","*");}
    void npc(WorldStorage storage)throws Exception {new NpcRepository(storage,DefinitionRegistry.builtIn()).create(storage.worldId(),UUID.randomUUID(),new RuntimeSnapshot(npc,owner,"agnor:dev",1,"minecraft:overworld",0,80,0,0,0,0,false));}
    @Test void crudRevisionDedupeAndTombstonesSurviveRestart()throws Exception {
        try(var storage=WorldStorage.open(root)){npc(storage);var repo=new LoreRepository(storage);var scope=new LoreRepository.Scope(storage.worldId(),npc,owner);
            var first=repo.put(scope,owner,false,entry("A"),0);assertEquals(first,repo.put(scope,owner,false,entry("A"),0));
            assertThrows(SQLException.class,()->repo.put(scope,owner,false,entry("B"),0));
            assertEquals(2,repo.put(scope,owner,false,entry("B"),1).revision());repo.delete(scope,owner,false,"facts:gate",2);repo.delete(scope,owner,false,"facts:gate",2);
            assertTrue(repo.list(scope,owner).isEmpty());assertTrue(repo.get(scope,owner,"facts:gate").deleted());assertThrows(SQLException.class,()->repo.put(scope,owner,false,entry("A"),0));
        }
        try(var storage=WorldStorage.open(root)){var repo=new LoreRepository(storage);var scope=new LoreRepository.Scope(storage.worldId(),npc,owner);assertEquals(3,repo.get(scope,owner,"facts:gate").revision());assertEquals(4,repo.put(scope,owner,false,entry("Restored"),3).revision());}
    }
    @Test void privatePlayerWorldNpcAndSharedWriteAreEnforcedInTransactions()throws Exception {
        try(var storage=WorldStorage.open(root);var foreign=WorldStorage.open(root.resolve("other"))){npc(storage);var repo=new LoreRepository(storage);var mine=new LoreRepository.Scope(storage.worldId(),npc,owner);var shared=new LoreRepository.Scope(storage.worldId(),npc,LoreRepository.SHARED);
            repo.put(mine,owner,false,entry("PRIVATE"),0);assertThrows(SQLException.class,()->repo.list(mine,other));assertThrows(SQLException.class,()->repo.put(mine,other,true,entry("attack"),1));
            assertThrows(SQLException.class,()->repo.put(shared,other,false,entry("attack"),0));repo.put(shared,owner,false,entry("shared"),0);assertEquals("shared",repo.list(shared,other).getFirst().entry().content());
            assertThrows(SQLException.class,()->repo.list(new LoreRepository.Scope(foreign.worldId(),npc,owner),owner));
            assertThrows(SQLException.class,()->repo.list(new LoreRepository.Scope(storage.worldId(),UUID.randomUUID(),owner),owner));
        }
    }
    @Test void activationIsUnicodeNormalizedBoundedAndConstraintAware() {
        var exact=new LoreEntry("facts:x","x","x",LoreEntry.Mode.EXACT,List.of("HELLO WORLD"),0,"assistant",true,"agnor:dev","minecraft:overworld");
        assertTrue(exact.matches("  hello   world ","agnor:dev","minecraft:overworld"));assertFalse(exact.matches("hello world!","agnor:dev","minecraft:overworld"));
        assertTrue(entry("x").matches("Где ВОРОТА?","agnor:dev","minecraft:overworld"));assertFalse(entry("x").matches("воротами","agnor:dev","minecraft:overworld"));
        assertFalse(exact.matches("hello world","other:npc","minecraft:overworld"));assertFalse(exact.matches("hello world","agnor:dev","minecraft:the_nether"));
    }
    @Test void typedPackSourceAndDisabledLocalOverrideHaveStablePrecedence()throws Exception {
        var manifest=PackLoaderTest.manifest("facts");var doc=new com.google.gson.JsonObject();doc.addProperty("type","agnor:lorebook");doc.addProperty("schemaVersion",1);var entries=new com.google.gson.JsonArray();var json=LoreJson.json(entry("pack"));json.addProperty("id","facts:gate");entries.add(json);doc.add("entries",entries);byte[] bytes=doc.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        PackLoaderTest.asset(manifest,"knowledge","lore.json","json",bytes);var dir=root.resolve("facts");Files.createDirectories(dir);PackLoaderTest.write(dir,manifest);Files.write(dir.resolve("lore.json"),bytes);
        var pack=new PackLoader().load(dir);var generation=PackGeneration.stage(List.of(pack));assertEquals("pack",generation.lorebook().get("facts:gate").entry().content());
        var disabled=new LoreEntry("facts:gate","off","off",LoreEntry.Mode.ALWAYS,List.of(),99,"user",false,"*","*");
        var rows=PackLore.effective(generation.lorebook(),List.of(new LoreRecord(entry("shared"),1,1,"shared",false)),List.of(new LoreRecord(disabled,2,2,"private",false)));
        assertEquals(1,rows.size());assertFalse(rows.getFirst().entry().enabled());assertFalse(rows.getFirst().entry().matches("ворота","agnor:dev","minecraft:overworld"));
    }
    @Test void v3MigrationBacksUpPreservesIdentityAndRejectsConflictingTable()throws Exception {
        UUID world;try(var storage=WorldStorage.open(root)){npc(storage);world=storage.worldId();}
        Path db=root.resolve("agnor/state.db");try(var c=DriverManager.getConnection("jdbc:sqlite:"+db);var s=c.createStatement()){s.execute("DROP TABLE memory_preferences");s.execute("DROP TABLE lore_entry");s.execute("PRAGMA user_version=3");s.execute("CREATE TABLE lore_entry(sentinel TEXT)");s.execute("INSERT INTO lore_entry VALUES('preserve')");}
        assertThrows(SQLException.class,()->WorldStorage.open(root));
        try(var c=DriverManager.getConnection("jdbc:sqlite:"+db);var s=c.createStatement()){try(var r=s.executeQuery("SELECT sentinel FROM lore_entry")){r.next();assertEquals("preserve",r.getString(1));}s.execute("DROP TABLE lore_entry");}
        try(var storage=WorldStorage.open(root)){assertEquals(world,storage.worldId());assertEquals(npc,new NpcRepository(storage,DefinitionRegistry.builtIn()).active(world).getFirst().id());}
        try(var paths=Files.list(root.resolve("agnor"))){assertTrue(paths.anyMatch(p->p.getFileName().toString().startsWith("state-v3-before-v"+WorldStorage.SCHEMA_VERSION+"-")));}
    }
}
