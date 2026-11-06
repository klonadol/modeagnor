package dev.agnor.dialogue;

import dev.agnor.character.*;
import dev.agnor.lore.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LoreContextTest {
    final ConversationScope scope=new ConversationScope(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());final UUID request=UUID.randomUUID();
    final RuntimeSnapshot runtime=new RuntimeSnapshot(scope.npc(),scope.player(),"agnor:dev",1,"minecraft:overworld",0,80,0,0,0,0,false);
    LoreRecord row(String id,String content,LoreEntry.Mode mode,List<String> triggers,int priority){return new LoreRecord(new LoreEntry(id,"Knowledge",content,mode,triggers,priority,"system",true,"*","*"),2,0,id+"@2",false);}
    ContextBuilder.Context build(String query,ContextSourceRegistry.Snapshot snapshot,ContextSourceRegistry.Profile profile){var current=new HistoryMessage(scope,UUID.randomUUID(),request,1,"user",query,0,"");return new ContextBuilder().build(scope,request,DefinitionRegistry.builtIn().require("agnor:dev"),runtime,0,List.of(current),current,new ContextBuilder.Policy(10,6000),snapshot,profile);}
    @Test void activatedKnowledgeHasVersionedSourcesAndUntrustedRoleWithoutChangingHistory() {
        var rows=List.of(row("facts:gate","COBALT-732",LoreEntry.Mode.KEYWORD,List.of("ворота"),1));
        var context=build("Где ворота?",ContextSourceRegistry.Snapshot.available(scope,rows),ContextSourceRegistry.Profile.mainDialogue());
        assertEquals(List.of("facts:gate@2"),context.loreSourceIds());assertEquals(1,context.historyIds().size());assertTrue(context.request().messages().get(1).content().contains("COBALT-732"));
        assertEquals("user",context.request().messages().get(1).role());assertTrue(context.request().messages().get(1).content().contains("\"role\":\"system\""));
        assertEquals(ContextBuilder.Status.NOT_YET_ENABLED,context.sources().get("semantic_search"));
        assertTrue(build("Погода?",ContextSourceRegistry.Snapshot.available(scope,rows),ContextSourceRegistry.Profile.mainDialogue()).loreSourceIds().isEmpty());
    }
    @Test void scopeAllowlistFreshnessAndUnavailableAreExplicit() {
        var rows=List.of(row("facts:gate","PRIVATE",LoreEntry.Mode.ALWAYS,List.of(),0));var other=new ConversationScope(scope.world(),scope.npc(),UUID.randomUUID());
        var foreign=build("q",ContextSourceRegistry.Snapshot.available(other,rows),ContextSourceRegistry.Profile.mainDialogue());assertEquals(ContextBuilder.Status.FORBIDDEN,foreign.sources().get("lorebook"));assertTrue(foreign.loreSourceIds().isEmpty());
        var forbidden=build("q",ContextSourceRegistry.Snapshot.available(scope,rows),new ContextSourceRegistry.Profile(Set.of("definition","runtime","relationship","history"),1000,30_000));assertEquals(ContextBuilder.Status.FORBIDDEN,forbidden.sources().get("lorebook"));
        var stale=build("q",new ContextSourceRegistry.Snapshot(scope,ContextBuilder.Status.AVAILABLE_WITH_DATA,0,rows),ContextSourceRegistry.Profile.mainDialogue());assertEquals(ContextBuilder.Status.TEMPORARILY_UNAVAILABLE,stale.sources().get("lorebook"));
        var empty=build("q",ContextSourceRegistry.Snapshot.available(scope,List.of()),ContextSourceRegistry.Profile.mainDialogue());assertEquals(ContextBuilder.Status.AVAILABLE_EMPTY,empty.sources().get("lorebook"));
    }
    @Test void priorityAndUtf8TruncationRespectBudgetAndPreservePolicyAndCurrentMessage() {
        var rows=List.of(row("facts:low","LOW",LoreEntry.Mode.ALWAYS,List.of(),0),row("facts:high","星🌟".repeat(1300),LoreEntry.Mode.ALWAYS,List.of(),100));
        var context=build("CURRENT",ContextSourceRegistry.Snapshot.available(scope,rows),new ContextSourceRegistry.Profile(Set.of("definition","runtime","relationship","history","lorebook"),700,30_000));
        assertEquals("facts:high@2",context.loreSourceIds().getFirst());assertTrue(context.truncatedLoreIds().contains("facts:high@2"));assertTrue(context.budgetUsed()<=6000);
        assertEquals("system",context.request().messages().getFirst().role());assertEquals("CURRENT",context.request().messages().getLast().content());assertFalse(context.request().messages().stream().anyMatch(m->m.content().contains("\ufffd")));
    }
}
