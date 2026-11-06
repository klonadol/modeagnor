package dev.agnor.check;

import dev.agnor.Agnor;
import dev.agnor.dialogue.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.server.MinecraftServer;
import com.google.gson.*;
import java.util.*;

final class P5ContextHost {
    int ticks,stage;UUID npc;final Set<Object> placed=new HashSet<>();
    void install(){ServerTickEvents.END_SERVER_TICK.register(this::tick);}
    void tick(MinecraftServer server){
        if(++ticks>6000||P5Files.read("abort.json")!=null){finish(server,"deadline/abort");return;}
        for(String role:List.of("A","B")){var r=P5Files.read(role+"-result.json");if(r!=null&&r.get("status").getAsString().equals("FAIL")){finish(server,"client "+role+" failure");return;}}
        if(server.getPlayerManager().getCurrentPlayerCount()<2)return;var runtime=Agnor.runtime(server);var world=server.getOverworld();
        for(var p:server.getPlayerManager().getPlayerList())if(placed.add(p.networkHandler)){p.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);p.teleport(world,0,80,-3,0,7);world.getChunk(0,0);world.getChunk(-1,-1);}
        if(stage==0){if(runtime.activeEntities().size()!=1||Agnor.content(server).active().lorebook().size()!=2){finish(server,"restore missing NPC/pack source");return;}npc=runtime.activeEntities().getFirst().getUuid();stage=1;state(runtime.worldId(),"no-match");}
        else if(stage==1&&P5Files.read("A-no-match.json")!=null){
            var a=server.getPlayerManager().getPlayerList().stream().filter(p->p.getName().getString().equals("P5A")).findFirst().orElseThrow();var trace=Agnor.dialogue(server).trace(a);
            if(trace==null||trace.loreIds().stream().anyMatch(s->s.contains("facts:gate")||s.contains("facts:secret"))||trace.sources().get("semantic_search")!=ContextBuilder.Status.NOT_YET_ENABLED){finish(server,"nonmatch/source privacy");return;}
            stage=2;state(runtime.worldId(),"match");
        }else if(stage==2&&P5Files.read("A-match.json")!=null&&P5Files.read("B-match.json")!=null){
            for(var p:server.getPlayerManager().getPlayerList()) {
                var trace=Agnor.dialogue(server).trace(p);boolean b=p.getName().getString().equals("P5B");
                if(trace==null||trace.budget()>12000||trace.sources().get("lorebook")!=ContextBuilder.Status.AVAILABLE_WITH_DATA||trace.loreIds().stream().noneMatch(s->s.contains("facts:gate"))||(!b&&trace.loreIds().stream().anyMatch(s->s.contains("facts:secret")))||b&&trace.loreIds().stream().noneMatch(s->s.startsWith("pack:")&&s.contains("facts:gate"))){finish(server,"trace source/budget isolation");return;}
                var json=new JsonObject();json.addProperty("budget",trace.budget());json.addProperty("sources",trace.sources().toString());json.addProperty("ids",trace.loreIds().toString());P5Files.write((b?"B":"A")+"-trace.json",json);
            }
            finish(server,null);
        }
    }
    void state(UUID world,String phase){var json=new JsonObject();json.addProperty("npc",npc.toString());json.addProperty("world",world.toString());json.addProperty("phase",phase);P5Files.write("state.json",json);}
    void finish(MinecraftServer server,String error){var json=new JsonObject();json.addProperty("status",error==null?"PASS":"FAIL");json.addProperty("failure",error);json.addProperty("npc",String.valueOf(npc));P5Files.write("server-result.json",json);System.out.println("P5_CONTEXT_HOST "+json);server.stop(false);}
}
