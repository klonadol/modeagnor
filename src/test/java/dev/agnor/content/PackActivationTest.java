package dev.agnor.content;

import dev.agnor.character.*;
import dev.agnor.dialogue.*;
import dev.agnor.storage.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class PackActivationTest {
    @TempDir Path temp;
    final BlockingQueue<Runnable> callbacks=new LinkedBlockingQueue<>();
    <T> T await(CompletableFuture<T> future) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(!future.isDone()&&System.nanoTime()<until){var work=callbacks.poll(100,TimeUnit.MILLISECONDS);if(work!=null)work.run();}
        return future.get(1,TimeUnit.SECONDS);
    }
    Path fixture() throws Exception {
        var dir=temp.resolve("packs/base");Files.createDirectories(dir);
        PackLoaderTest.write(dir,PackLoaderTest.manifest("base"));return dir;
    }
    @Test void enableFailedReloadDisableRollbackAndRestartAreAtomic() throws Exception {
        Path source=fixture(), privateRoot=temp.resolve("world/agnor/content");
        try(var service=new ContentService(source.getParent(),privateRoot,callbacks::add)) {
            var registry=service.registry();assertThrows(IllegalArgumentException.class,()->registry.require("example:npc"));
            var enabled=await(service.enable("base"));assertEquals("Be helpful.",registry.require("example:npc").prompt());
            Files.writeString(source.resolve("manifest.json"),"corrupt");
            assertThrows(ExecutionException.class,()->await(service.enable("base")));
            assertSame(enabled,service.active());assertEquals(1,service.epoch());
            await(service.disable("base"));assertThrows(IllegalArgumentException.class,()->registry.require("example:npc"));
            assertEquals("Unavailable character",registry.available("example:npc",1).displayName());
            await(service.rollback());assertEquals(enabled.packs().get("base").digest(),service.active().packs().get("base").digest());
        }
        try(var service=new ContentService(source.getParent(),privateRoot,callbacks::add)) {
            assertEquals("External NPC",service.registry().require("example:npc").displayName());
            assertEquals(1,service.active().packs().size());
        }
    }
    @Test void busyDependencyAndInvalidPathsCannotPublish() throws Exception {
        var source=fixture();try(var service=new ContentService(source.getParent(),temp.resolve("cache"),callbacks::add)) {
            var first=service.enable("base");var busy=service.disable("base");assertTrue(busy.isCompletedExceptionally());await(first);
            var m=PackLoaderTest.manifest("base");m.getAsJsonObject("dependencies").addProperty("missing","1.0.0");PackLoaderTest.write(source,m);
            assertThrows(ExecutionException.class,()->await(service.enable("base")));assertEquals(1,service.epoch());
            assertThrows(ExecutionException.class,()->await(service.enable("../escape")));
            assertThrows(ExecutionException.class,()->await(service.disable("missing")));
        }
    }
    @Test void malformedRigAndUnsafeGeometryNeverReachPublication() throws Exception {
        var source=fixture();try(var service=new ContentService(source.getParent(),temp.resolve("cache"),callbacks::add)) {
            await(service.enable("base"));
            var m=PackLoaderTest.manifest("base");byte[] body=PackLoaderTest.glb("{\"asset\":{\"version\":\"2.0\"}}");byte[] rig="{}".getBytes();
            PackLoaderTest.asset(m,"model","models/part-000.glb","glb",body);PackLoaderTest.asset(m,"rig","rig.json","json",rig);
            var d=m.getAsJsonArray("definitions").get(0).getAsJsonObject();d.addProperty("model","base:model");d.addProperty("rig","base:rig");
            Files.createDirectories(source.resolve("models"));Files.write(source.resolve("models/part-000.glb"),body);Files.write(source.resolve("rig.json"),rig);PackLoaderTest.write(source,m);
            assertThrows(ExecutionException.class,()->await(service.enable("base")));assertEquals(1,service.epoch());
            var directory=temp.resolve("bad-glb");Files.createDirectories(directory);
            for(String json:List.of("{\"nodes\":[{\"children\":[0]}]}","{\"accessors\":[{\"count\":2000001,\"componentType\":5126,\"type\":\"VEC3\"}]}")) {
                var object=com.google.gson.JsonParser.parseString(json).getAsJsonObject();object.add("asset",com.google.gson.JsonParser.parseString("{\"version\":\"2.0\"}"));
                byte[] bytes=PackLoaderTest.glb(object.toString());var manifest=PackLoaderTest.manifest("unsafe");PackLoaderTest.asset(manifest,"model","model.glb","glb",bytes);
                PackLoaderTest.write(directory,manifest);Files.write(directory.resolve("model.glb"),bytes);assertThrows(IOException.class,()->new PackLoader().load(directory));
            }
        }
    }
    @Test void failedSnapshotWritePreservesPreviousGeneration() throws Exception {
        var source=fixture();Path root=temp.resolve("cache");try(var service=new ContentService(source.getParent(),root,callbacks::add)) {
            var good=await(service.enable("base"));var m=PackLoaderTest.manifest("base");m.addProperty("version","1.0.1");PackLoaderTest.write(source,m);
            // Deterministic filesystem failure at the commit point, without requiring platform ACLs.
            Files.delete(root.resolve("current.txt"));Files.createDirectory(root.resolve("current.txt"));
            assertThrows(ExecutionException.class,()->await(service.enable("base")));assertSame(good,service.active());assertEquals(1,service.epoch());
        }
    }
    @Test void assignmentKeepsIdentityRelationshipsAndHistoryAcrossRestart() throws Exception {
        Path source=fixture();var pack=new PackLoader().load(source);var generation=PackGeneration.stage(List.of(pack));
        UUID id=UUID.randomUUID(),owner=UUID.randomUUID(),request=UUID.randomUUID();
        try(var storage=WorldStorage.open(temp.resolve("save"))) {
            var registry=DefinitionRegistry.builtIn();registry.replace(generation.registry());var repo=new NpcRepository(storage,registry);
            var initial=new RuntimeSnapshot(id,owner,DefinitionRegistry.DEV_ID,1,"minecraft:overworld",1,80,2,0,0,0,false);
            repo.create(storage.worldId(),UUID.randomUUID(),initial);repo.relationship(storage.worldId(),id,owner,17);
            var scope=new ConversationScope(storage.worldId(),id,owner);var history=new HistoryRepository(storage);
            history.accept(scope,request,"hello");var assigned=repo.redefine(storage.worldId(),id,"example:npc");
            assertEquals(id,assigned.id());assertEquals(owner,assigned.owner());assertEquals(17,repo.relationship(storage.worldId(),id,owner));
            assertEquals(assigned,repo.redefine(storage.worldId(),id,"example:npc"));
            var current=history.find(scope,request).inbound();var context=new ContextBuilder().build(scope,request,registry.require("example:npc"),assigned,17,List.of(current),current,ContextBuilder.Policy.DEFAULT);
            assertTrue(context.request().messages().getFirst().content().contains("Be helpful."));
            registry.replace(DefinitionRegistry.builtIn());assertEquals(id,repo.find(storage.worldId(),id).id());
        }
        try(var storage=WorldStorage.open(temp.resolve("save"))) {
            var repo=new NpcRepository(storage,generation.registry());assertEquals("example:npc",repo.find(storage.worldId(),id).definition());
            assertEquals(17,repo.relationship(storage.worldId(),id,owner));
            assertNotNull(new HistoryRepository(storage).find(new ConversationScope(storage.worldId(),id,owner),request).inbound());
        }
    }
    @Test void renderResourcesStripUntrustedImporterHooks() throws Exception {
        var m=PackLoaderTest.manifest("base");byte[] bytes=PackLoaderTest.glb("{\"asset\":{\"version\":\"2.0\"},\"buffers\":[{\"byteLength\":0,\"extras\":{\"resourceLocation\":\"secret:private.bin\"}}]}");
        PackLoaderTest.asset(m,"model","model.glb","glb",bytes);var dir=temp.resolve("resources");Files.createDirectories(dir);PackLoaderTest.write(dir,m);Files.write(dir.resolve("model.glb"),bytes);
        var resources=PackResources.build(List.of(new PackLoader().load(dir)));
        assertFalse(new String(resources.get("content/base/model.glb"),java.nio.charset.StandardCharsets.UTF_8).contains("secret:private"));
    }
}
