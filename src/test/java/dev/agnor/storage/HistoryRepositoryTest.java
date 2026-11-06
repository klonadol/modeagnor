package dev.agnor.storage;

import dev.agnor.character.*;
import dev.agnor.dialogue.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HistoryRepositoryTest {
    @TempDir Path root;
    RuntimeSnapshot create(WorldStorage storage) throws Exception {
        return new NpcRepository(storage, DefinitionRegistry.builtIn()).create(storage.worldId(), UUID.randomUUID(),
                new RuntimeSnapshot(UUID.randomUUID(), UUID.randomUUID(), DefinitionRegistry.DEV_ID, 1, "minecraft:overworld", 0, 80, 0, 0, 0, 0, false));
    }
    @Test void acceptedFinalCountReplayScopeAndRestart() throws Exception {
        UUID npc, world, request = UUID.randomUUID(), player = UUID.randomUUID();
        try (var storage = WorldStorage.open(root)) {
            npc = create(storage).id(); world = storage.worldId(); var history = new HistoryRepository(storage);
            var a = new ConversationScope(world,npc,player); var b = new ConversationScope(world,npc,UUID.randomUUID());
            var accepted = history.accept(a,request,"A private"); assertEquals(1,accepted.inbound().sequence());
            assertEquals(accepted,history.accept(a,request,"A private"));
            assertThrows(SQLException.class, () -> history.accept(a,request,"changed"));
            assertThrows(SQLException.class, () -> history.accept(a,UUID.randomUUID(),"busy"));
            assertTrue(history.claim(a,request)); assertFalse(history.claim(a,request));
            var finalized = history.finalizeReply(a,request,"A reply","test:model");
            assertEquals(2,finalized.reply().sequence()); assertEquals(finalized,history.finalizeReply(a,request,"A reply","test:model"));
            assertThrows(SQLException.class, () -> history.finalizeReply(a,request,"different","test:model"));
            history.accept(b,request,"B private"); assertEquals("B private",history.recent(b,10).getFirst().text());
            assertEquals(2,history.recent(a,10).size()); assertEquals(2,history.events(a,0,100).size());
            assertEquals(1,history.events(a,history.events(a,0,100).getFirst().sequence(),100).size());
            assertThrows(SQLException.class, () -> history.recent(new ConversationScope(UUID.randomUUID(),npc,player),10));
        }
        try (var storage = WorldStorage.open(root)) {
            assertEquals(world,storage.worldId()); var history = new HistoryRepository(storage);
            history.recoverInterrupted(world);
            assertEquals(HistoryRepository.State.FINALIZED,history.find(new ConversationScope(world,npc,player),request).state());
        }
    }
    @Test void failedEventWriteRollsBackMessageSequenceAndTurn() throws Exception {
        try (var storage = WorldStorage.open(root)) {
            var scope = new ConversationScope(storage.worldId(),create(storage).id(),UUID.randomUUID()); var history = new HistoryRepository(storage);
            storage.transaction(storage.worldId(), c -> c.createStatement().execute("CREATE TRIGGER fail_event BEFORE INSERT ON dialogue_event BEGIN SELECT RAISE(ABORT,'fixture'); END"));
            assertThrows(SQLException.class, () -> history.accept(scope,UUID.randomUUID(),"rollback"));
            storage.transaction(storage.worldId(), c -> {
                try (var r = c.createStatement().executeQuery("SELECT count(*) FROM history_message")) { r.next(); assertEquals(0,r.getInt(1)); }
                c.createStatement().execute("DROP TRIGGER fail_event");
            });
            var id = UUID.randomUUID(); assertEquals(1,history.accept(scope,id,"accepted").inbound().sequence()); history.claim(scope,id);
            storage.transaction(storage.worldId(), c -> c.createStatement().execute("CREATE TRIGGER fail_event BEFORE INSERT ON dialogue_event BEGIN SELECT RAISE(ABORT,'fixture'); END"));
            assertThrows(SQLException.class, () -> history.finalizeReply(scope,id,"reply","model"));
            assertEquals(1,history.recent(scope,10).size()); assertEquals(HistoryRepository.State.RUNNING,history.find(scope,id).state());
        }
    }
    @Test void interruptedJobsKeepInboundAndCannotReplayInference() throws Exception {
        try (var storage = WorldStorage.open(root)) {
            var scope = new ConversationScope(storage.worldId(),create(storage).id(),UUID.randomUUID()); var history = new HistoryRepository(storage); var id=UUID.randomUUID();
            history.accept(scope,id,"saved before inference"); history.claim(scope,id); history.recoverInterrupted(scope.world());
            assertEquals(HistoryRepository.State.INTERRUPTED,history.accept(scope,id,"saved before inference").state());
            assertFalse(history.claim(scope,id)); assertThrows(SQLException.class, () -> history.finalizeReply(scope,id,"stale","model"));
            assertEquals(1,history.recent(scope,10).size()); assertEquals(1,history.events(scope,0,100).size());
        }
    }
    @Test void v2MigrationPreservesNpcAndBackupAndFailureRollsBack() throws Exception {
        UUID npc, world;
        try (var storage=WorldStorage.open(root)) { npc=create(storage).id(); world=storage.worldId(); }
        try (var c=DriverManager.getConnection("jdbc:sqlite:"+root.resolve("agnor/state.db"));var s=c.createStatement()) {
            s.execute("DROP TABLE memory_preferences");s.execute("DROP TABLE lore_entry");s.execute("DROP TABLE dialogue_event");s.execute("DROP TABLE history_message");s.execute("DROP TABLE dialogue_turn");s.execute("DROP TABLE conversation");s.execute("PRAGMA user_version=2");
            s.execute("CREATE TABLE conversation(sentinel TEXT)");s.execute("INSERT INTO conversation VALUES ('preserve')");
        }
        assertThrows(SQLException.class, () -> WorldStorage.open(root));
        try (var c=DriverManager.getConnection("jdbc:sqlite:"+root.resolve("agnor/state.db"));var s=c.createStatement()) {
            try(var r=s.executeQuery("PRAGMA user_version")) {r.next();assertEquals(2,r.getInt(1));}
            try(var r=s.executeQuery("SELECT sentinel FROM conversation")) {r.next();assertEquals("preserve",r.getString(1));}
            s.execute("DROP TABLE conversation");
        }
        try(var storage=WorldStorage.open(root)) {
            assertEquals(world,storage.worldId());assertEquals(npc,new NpcRepository(storage,DefinitionRegistry.builtIn()).active(world).getFirst().id());
        }
        try(var files=Files.list(root.resolve("agnor"))) {assertTrue(files.anyMatch(p->p.getFileName().toString().startsWith("state-v2-before-v"+WorldStorage.SCHEMA_VERSION+"-")));}
    }
}
