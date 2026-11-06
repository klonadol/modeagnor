package dev.agnor.check;
import dev.agnor.client.*;
import dev.agnor.character.NpcEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.util.ScreenshotRecorder;
import java.util.*;

public final class P4Client implements ClientModInitializer {
    int ticks,phaseTicks;String phase="";long previousRendered,previousAccepted;boolean done;
    public void onInitializeClient(){if(System.getProperty("agnor.p4.stage","").startsWith("sync")){new P4SyncClient().install();return;}ClientTickEvents.END_CLIENT_TICK.register(client->{
        if(P4Files.read("server-result.json")!=null){client.scheduleStop();return;}
        if(client.world==null||client.player==null||done)return;
        if(++ticks>3500){P4Files.mark("A-result.json","FAIL");done=true;return;}
        var state=P4Files.read("state.json");if(state==null)return;
        String next=state.get("phase").getAsString();if(!next.equals(phase)){phase=next;phaseTicks=0;previousRendered=NpcMotionClient.metrics().rendered();previousAccepted=NpcMotionClient.metrics().accepted();}phaseTicks++;
        var entity=client.world.getEntities().iterator();NpcEntity npc=null;UUID id=UUID.fromString(state.get("npc").getAsString());
        while(entity.hasNext()){var value=entity.next();if(value instanceof NpcEntity n&&n.getUuid().equals(id)){npc=n;break;}}
        if(npc==null)return;
        boolean expected=phase.equals("disabled")?npc.content().equals("missing")&&ContentClient.binding(npc)==null:
            ContentClient.binding(npc)!=null&&NpcMotionClient.latest(id)!=null&&NpcMotionClient.metrics().rendered()>previousRendered+20&&NpcMotionClient.metrics().accepted()>previousAccepted+20;
        if(expected&&phaseTicks>70&&P4Files.read("A-"+phase+".json")==null){
            try(var image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())){image.writeTo(P4Files.ROOT.resolve("A-"+phase+".png"));}catch(Exception e){throw new RuntimeException(e);}
            P4Files.mark("A-"+phase+".json","PASS");if(phase.equals("rollback")||phase.equals("restored")){P4Files.mark("A-result.json","PASS");done=true;}
        }
    });}
}
