package dev.agnor.check;

import dev.agnor.network.*;
import dev.agnor.client.DialogueClient;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.util.ScreenshotRecorder;
import com.google.gson.*;
import java.util.*;

final class P5ContextClient {
    final String role=System.getProperty("agnor.p5.role","A");String phase="";boolean sent,finished;int ticks,age;UUID request;String failure="";
    void install(){
        ClientPlayNetworking.unregisterGlobalReceiver(DialogueProtocol.Response.ID.id());
        ClientPlayNetworking.registerGlobalReceiver(DialogueProtocol.Response.ID,(p,c)->c.client().execute(()->{
            var response=p.value();DialogueClient.receive(c.client(),response);
            if(request==null||!request.equals(response.request())){failure="foreign private response";return;}
            if(response.status()==DialogueWire.Status.FINAL){
                boolean correct=!phase.equals("match")||response.text().contains(role.equals("A")?"COBALT-732":"PACK-AMBER");
                if(!correct){failure="real reply did not use matching fact";P5Files.mark(role+"-result.json","FAIL");return;}
                var json=new JsonObject();json.addProperty("status","PASS");json.addProperty("request",request.toString());json.addProperty("usedExpectedFact",correct);P5Files.write(role+"-"+phase+".json",json);
                if(phase.equals("match")){P5Files.mark(role+"-result.json","PASS");finished=true;}
            }else if(response.status()!=DialogueWire.Status.ACCEPTED&&response.status()!=DialogueWire.Status.PENDING)failure="terminal "+response.status()+":"+response.error();
        }));
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(P5Files.read("server-result.json")!=null){client.scheduleStop();return;}if(!failure.isEmpty()||++ticks>5700){P5Files.mark(role+"-result.json","FAIL");return;}if(client.world==null||client.player==null||finished)return;
            var state=P5Files.read("state.json");if(state==null)return;String next=state.get("phase").getAsString();if(!next.equals(phase)){phase=next;age=0;sent=false;}age++;
            if(!sent&&age>30&&(!role.equals("B")||phase.equals("match"))) {
                sent=true;request=UUID.randomUUID();String query=phase.equals("match")?"Тема: ворота. Назови только код ворот из знаний, без объяснений.":"Кратко поздоровайся, без обсуждения ворот или кодов.";
                ClientPlayNetworking.send(new DialogueProtocol.Request(new DialogueWire.Request(DialogueWire.VERSION,0,DialogueWire.Action.SAY,UUID.fromString(state.get("world").getAsString()),UUID.fromString(state.get("npc").getAsString()),request,query)));
            }
        });
    }
}
