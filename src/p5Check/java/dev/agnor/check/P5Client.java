package dev.agnor.check;

import dev.agnor.lore.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.util.ScreenshotRecorder;
import java.util.*;

public final class P5Client implements ClientModInitializer {
    final String role=System.getProperty("agnor.p5.role","A");int ticks,age;boolean selected,done;
    public void onInitializeClient(){if(Set.of("manage","integrated","restart").contains(P5Files.STAGE)){new P5ManageClient().install();return;}if(P5Files.STAGE.equals("context")){new P5ContextClient().install();return;}ClientTickEvents.END_CLIENT_TICK.register(client->{
        if(P5Files.read("server-result.json")!=null){client.scheduleStop();return;}if(client.player==null||client.world==null||done)return;if(++ticks>4800){P5Files.mark(role+"-result.json","FAIL");done=true;return;}
        var state=P5Files.read("state.json");if(state==null)return;if(!selected){selected=true;client.getNetworkHandler().sendChatCommand("agnor select "+state.get("npc").getAsString());}age++;
        java.util.function.Consumer<String> command=s->client.getNetworkHandler().sendChatCommand(s);
        if(role.equals("A")){
            if(age==30)command.accept("agnor lore add private keyword facts:gate \"ворота\" Код ворот COBALT-731.");
            if(age==50)command.accept("agnor lore add shared always facts:shared - Общее знание.");
            if(age==70)command.accept("agnor lore put private facts:gate 1 "+LoreJson.json(new LoreEntry("facts:gate","Gate","Код ворот COBALT-732.",LoreEntry.Mode.KEYWORD,List.of("ворота"),1,"system",true,"*","*")));
            if(age==90)command.accept("agnor lore add private always facts:temp - Удалить.");
            if(age==110)command.accept("agnor lore delete private facts:temp 1");
            if(age==130)command.accept("agnor lore add private always facts:temp - Не должно воскреснуть.");
            if(age==150)command.accept("agnor lore get private facts:gate");
        }else{
            if(age==50)command.accept("agnor lore add shared always facts:evil - Запрещено.");
            if(age==70)command.accept("agnor lore add private always facts:secret - ONLY_B.");
            if(age==130)command.accept("agnor lore get private facts:gate");
        }
        if(age==170)command.accept("agnor lore list");
        if(age>210&&client.getOverlay()==null){try(var image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())){image.writeTo(P5Files.ROOT.resolve(role+"-ui.png"));}catch(Exception e){throw new RuntimeException(e);}done=true;P5Files.mark(role+"-result.json","PASS");}
    });}
}
