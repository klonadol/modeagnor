package dev.agnor.check;

import dev.agnor.network.*;
import dev.agnor.client.DialogueClient;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.util.ScreenshotRecorder;
import java.util.*;

final class P5ManageClient {
    final String role=System.getProperty("agnor.p5.role","A");String phase="",failure="";int ticks,age;boolean sent,done;UUID request;
    void install(){
        ClientPlayNetworking.unregisterGlobalReceiver(DialogueProtocol.Response.ID.id());ClientPlayNetworking.registerGlobalReceiver(DialogueProtocol.Response.ID,(p,c)->c.client().execute(()->{
            var response=p.value();DialogueClient.receive(c.client(),response);
            if(response.status()==DialogueWire.Status.HISTORY)return;
            if(request==null||!request.equals(response.request())){failure="foreign reply";return;}
            if(response.status()==DialogueWire.Status.FINAL){if(!response.text().contains(role.equals("A")?"COBALT-732":"PACK-AMBER")){failure="wrong real knowledge reply";return;}P5Files.mark(role+"-"+phase+".json","PASS");if(phase.equals("final")){done=true;P5Files.mark(role+"-result.json","PASS");}}
            else if(response.status()!=DialogueWire.Status.ACCEPTED&&response.status()!=DialogueWire.Status.PENDING)failure="terminal "+response.status()+":"+response.error();
        }));
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(P5Files.read("server-result.json")!=null){client.scheduleStop();return;}if(!failure.isEmpty()||++ticks>6200){P5Files.mark(role+"-result.json","FAIL");return;}if(done||client.world==null||client.player==null)return;
            var state=P5Files.read("state.json");if(state==null)return;String next=state.get("phase").getAsString();
            if(!phase.equals(next)){phase=next;age=0;sent=false;client.getNetworkHandler().sendChatCommand("agnor select "+state.get("npc").getAsString());}age++;
            java.util.function.Consumer<String> command=s->client.getNetworkHandler().sendChatCommand(s);
            if(phase.equals("settings")) {
                if(age==30)command.accept("agnor memory recent "+(role.equals("A")?2:1));if(age==50)command.accept("agnor memory keep 2");if(age==70)command.accept("agnor memory history");if(age==90)command.accept("agnor memory retention apply");
                if(age==100){if(role.equals("A"))command.accept("agnor memory backup");else command.accept("agnor memory restore nonexistent.db");}
                if(age>140){P5Files.mark(role+"-settings.json","PASS");}
            }else if(phase.equals("restore")) {
                if(!sent&&age>30){sent=true;if(role.equals("A"))command.accept("agnor memory restore "+state.get("backup").getAsString());}
                if(age>80)P5Files.mark(role+"-restore.json","PASS");
            }else if((phase.equals("dialogue")||phase.equals("final"))&&!sent&&age>30){sent=true;request=UUID.randomUUID();ClientPlayNetworking.send(new DialogueProtocol.Request(new DialogueWire.Request(DialogueWire.VERSION,0,DialogueWire.Action.SAY,UUID.fromString(state.get("world").getAsString()),UUID.fromString(state.get("npc").getAsString()),request,"Тема: ворота. Напомни только код из знаний.")));}
        });
    }
}
