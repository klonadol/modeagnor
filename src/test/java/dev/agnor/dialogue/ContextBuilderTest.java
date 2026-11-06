package dev.agnor.dialogue;

import dev.agnor.character.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContextBuilderTest {
    @Test void rolesScopeSingleCurrentBudgetAndFutureSources() {
        var scope=new ConversationScope(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());var id=UUID.randomUUID();
        var runtime=new RuntimeSnapshot(scope.npc(),UUID.randomUUID(),DefinitionRegistry.DEV_ID,1,"minecraft:overworld",1,80,2,0,0,4,false);
        var definition=DefinitionRegistry.builtIn().require(DefinitionRegistry.DEV_ID);
        List<HistoryMessage> history=new ArrayList<>();
        for(int i=1;i<20;i++) history.add(new HistoryMessage(scope,UUID.randomUUID(),UUID.randomUUID(),i,i%2==0?"assistant":"user","x".repeat(1000),0,""));
        var current=new HistoryMessage(scope,UUID.randomUUID(),id,20,"user","current",0,"");history.add(current);
        var context=new ContextBuilder().build(scope,id,definition,runtime,17,history,current,new ContextBuilder.Policy(10,6000));
        assertEquals("system",context.request().messages().getFirst().role());
        assertTrue(context.request().messages().getFirst().content().contains("affinity=17"));
        assertEquals("current",context.request().messages().getLast().content());
        assertEquals(1,Collections.frequency(context.historyIds(),current.id()));assertTrue(context.budgetUsed()<=6000);assertTrue(context.historyIds().size()<10);
        assertEquals(ContextBuilder.Status.NOT_YET_ENABLED,context.sources().get("diary"));
        var foreign=new HistoryMessage(new ConversationScope(scope.world(),scope.npc(),UUID.randomUUID()),UUID.randomUUID(),id,1,"user","foreign",0,"");
        assertThrows(IllegalArgumentException.class,()->new ContextBuilder().build(scope,id,definition,runtime,0,List.of(foreign),current,ContextBuilder.Policy.DEFAULT));
    }
}
