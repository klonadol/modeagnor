package dev.agnor.check;
import dev.agnor.client.NpcMotionClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.util.ScreenshotRecorder;
import com.google.gson.*;
import java.util.*;

public final class P35Client implements ClientModInitializer {
    final String role=System.getProperty("agnor.p35.role","A");int ticks;float peakBlink;double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY;boolean finished,captured;
    public void onInitializeClient(){if(!P35Files.STAGE.equals("smoke")){new P35ScenarioClient().install();return;}ClientTickEvents.END_CLIENT_TICK.register(client->{
        if(finished){if(P35Files.read("server-result.json")!=null)client.scheduleStop();return;}
        if(client.world==null||client.player==null)return;ticks++;client.options.hudHidden=true;
        var state=P35Files.read("state.json");if(state==null)return;var frame=NpcMotionClient.latest(UUID.fromString(state.get("npc").getAsString()));
        if(frame!=null){minX=Math.min(minX,frame.x());maxX=Math.max(maxX,frame.x());if(frame.morphs().size()>14)peakBlink=Math.max(peakBlink,frame.morphs().get(13));}
        if(ticks>=150&&!captured&&NpcMotionClient.metrics().rendered()>20){try(var image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())){image.writeTo(P35Files.ROOT.resolve(role+"-motion.png"));captured=true;}catch(Exception e){throw new RuntimeException(e);}}
        if(ticks>=240){
            finished=true;var metrics=NpcMotionClient.metrics();var result=new JsonObject();boolean pass=captured&&frame!=null&&metrics.accepted()>30&&metrics.rendered()>50&&peakBlink>.5f&&maxX-minX>.2;
            result.addProperty("status",pass?"PASS":"FAIL");result.addProperty("metrics",metrics.toString());result.addProperty("peakBlink",peakBlink);result.addProperty("rootTravel",maxX-minX);result.addProperty("lastSequence",frame==null?0:frame.sequence());P35Files.write(role+"-result.json",result);System.out.println("P35_CLIENT_RESULT "+role+" "+result);
        }
    });}
}
