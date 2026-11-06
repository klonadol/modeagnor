package dev.agnor.dialogue;

import dev.agnor.character.*;
import dev.agnor.storage.*;
import dev.agnor.provider.*;
import java.net.URI;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

class HistoryContextLiveCheck {
    @TempDir Path root;
    @Test void savedContextAffectsRealNextReply() throws Exception {
        assumeTrue(Boolean.getBoolean("agnor.historyLive"));
        var preset=new DialoguePreset(1,true,URI.create("http://127.0.0.1:11434"),"ternary-bonsai-text-20k:latest",Duration.ofSeconds(90),1,8,128,65536,"");
        ConversationScope scope;RuntimeSnapshot npc;
        try(var storage=WorldStorage.open(root)) {
            npc=new NpcRepository(storage,DefinitionRegistry.builtIn()).create(storage.worldId(),UUID.randomUUID(),new RuntimeSnapshot(UUID.randomUUID(),UUID.randomUUID(),DefinitionRegistry.DEV_ID,1,"minecraft:overworld",0,80,0,0,0,0,false));
            scope=new ConversationScope(storage.worldId(),npc.id(),UUID.randomUUID());
            exchange(storage,scope,npc,preset,"My nickname is Cedar47. Remember it and greet me briefly.");
        }
        try(var storage=WorldStorage.open(root)) {
            String reply=exchange(storage,scope,npc,preset,"What nickname did I tell you? Reply only with that nickname.");
            assertTrue(reply.contains("Cedar47"));assertEquals(4,new HistoryRepository(storage).recent(scope,10).size());
            Files.createDirectories(Path.of("build/p2-evidence"));Files.writeString(Path.of("build/p2-evidence/history-context-live.txt"),"PASS saved History -> Context -> real Ollama after reopen; reply="+reply);
        }
    }
    private String exchange(WorldStorage storage,ConversationScope scope,RuntimeSnapshot npc,DialoguePreset preset,String text)throws Exception {
        var history=new HistoryRepository(storage);var request=UUID.randomUUID();var turn=history.accept(scope,request,text);assertTrue(history.claim(scope,request));
        var context=new ContextBuilder().build(scope,request,DefinitionRegistry.builtIn().require(npc.definition()),npc,7,history.recent(scope,10),turn.inbound(),ContextBuilder.Policy.DEFAULT);
        try(var gateway=new ProviderGateway(preset)) {
            var reply=gateway.submit(context.request()).result().toCompletableFuture().get(95,TimeUnit.SECONDS);assertEquals(ProviderResult.Error.NONE,reply.error());
            history.finalizeReply(scope,request,reply.text(),preset.model());return reply.text();
        }
    }
}
