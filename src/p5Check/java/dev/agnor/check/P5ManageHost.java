package dev.agnor.check;

import dev.agnor.Agnor;
import dev.agnor.character.*;
import dev.agnor.dialogue.*;
import dev.agnor.lore.*;
import dev.agnor.storage.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.server.MinecraftServer;
import java.util.*;
import java.util.concurrent.*;
import com.google.gson.*;

final class P5ManageHost {
    final boolean integrated=P5Files.STAGE.equals("integrated"),restart=P5Files.STAGE.equals("restart");
    int ticks,stage,age;boolean busy;UUID npc,owner,other,pending;String backup,failure;final Set<Object> placed=new HashSet<>();final Map<UUID,Integer> counts=new HashMap<>();
    void install(){ServerTickEvents.END_SERVER_TICK.register(this::tick);}
    void tick(MinecraftServer server){
        if(++ticks>6500||P5Files.read("abort.json")!=null){finish(server,"deadline/abort");return;}if(failure!=null){finish(server,failure);return;}
        for(String role:integrated?List.of("A"):List.of("A","B")){var result=P5Files.read(role+"-result.json");if(result!=null&&result.get("status").getAsString().equals("FAIL")){finish(server,"client "+role+" failure");return;}}
        if(busy||server.getPlayerManager().getCurrentPlayerCount()<(integrated?1:2))return;age++;
        var runtime=Agnor.runtime(server);var world=server.getOverworld();
        for(var p:server.getPlayerManager().getPlayerList())if(placed.add(p.networkHandler)){p.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);p.teleport(world,0,80,-3,0,7);if(p.getName().getString().equals("P5A"))server.getPlayerManager().addToOperators(p.getGameProfile());world.getChunk(0,0);world.getChunk(-1,-1);for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)world.setBlockState(new net.minecraft.util.math.BlockPos(x,79,z),net.minecraft.block.Blocks.STONE.getDefaultState());}
        if(stage==0){busy=true;owner=server.getPlayerManager().getPlayerList().stream().filter(p->p.getName().getString().equals("P5A")).findFirst().orElseThrow().getUuid();other=integrated?owner:server.getPlayerManager().getPlayerList().stream().filter(p->p.getName().getString().equals("P5B")).findFirst().orElseThrow().getUuid();
            var entities=runtime.activeEntities();var initial=entities.isEmpty()?runtime.create(UUID.randomUUID(),owner,"agnor:dev",world,0,80,2):runtime.inspect(entities.getFirst().getUuid());
            initial.thenComposeAsync(s->{npc=s.id();return Agnor.content(server).enable("facts");},server).thenComposeAsync(g->runtime.lore(repo->{
                var scope=new LoreRepository.Scope(runtime.worldId(),npc,owner);var row=repo.get(scope,owner,"facts:gate");
                if(restart){if(row==null||!row.entry().content().contains("COBALT-732"))throw new IllegalStateException("restart knowledge missing");}
                else{var entry=new LoreEntry("facts:gate","Gate","Код ворот COBALT-732.",LoreEntry.Mode.KEYWORD,List.of("ворота"),1,"system",true,"*","*");repo.put(scope,owner,false,entry,row==null?0:row.revision());}return null;
            }),server).whenCompleteAsync((v,e)->{busy=false;if(e!=null)failure="setup "+e;else{stage=1;state(server,restart?"final":"settings");}},server);
        }else if(stage==1&&restart&&marked("final")){validateTraces(server);finish(server,failure);}
        else if(stage==1&&!restart&&marked("settings")){
            busy=true;runtime.preferences(scope(runtime,owner),10).thenCombine(runtime.preferences(scope(runtime,other),10),(a,b)->{if(a.recentN()!=2||(!integrated&&b.recentN()!=1))throw new IllegalStateException("settings isolation");return a;})
                .whenCompleteAsync((v,e)->{busy=false;if(e!=null)failure="settings "+e;else{stage=2;state(server,"dialogue");}},server);
        }else if(stage==2&&marked("dialogue")){
            validateTraces(server);if(failure!=null)return;busy=true;pending=UUID.randomUUID();
            runtime.history(h->{h.accept(scope(runtime,other),pending,"PENDING_SOURCE_P5");h.claim(scope(runtime,other),pending);for(UUID actor:integrated?List.of(owner):List.of(owner,other)){var s=scope(runtime,actor);counts.put(actor,h.page(s,Long.MAX_VALUE,20).size());var report=h.retention(s,2,true);if(report.deleted()!=0||report.protectedSources()!=Math.max(0,report.total()-2))throw new IllegalStateException("pending retention loss");}return null;})
                .thenComposeAsync(v->runtime.backupMemory(),server).thenComposeAsync(name->{backup=name;return runtime.lore(repo->{var s=new LoreRepository.Scope(runtime.worldId(),npc,owner);var row=repo.get(s,owner,"facts:gate");repo.put(s,owner,false,new LoreEntry("facts:gate","x","CORRUPTED_AFTER_BACKUP",LoreEntry.Mode.ALWAYS,List.of(),1,"user",true,"*","*"),row.revision());return null;});},server)
                .thenComposeAsync(v->runtime.preferences(scope(runtime,owner),new MemoryPreferences.Value(99,999)),server)
                .whenCompleteAsync((v,e)->{busy=false;if(e!=null)failure="backup/mutate "+e;else{stage=3;state(server,"restore");}},server);
        }else if(stage==3&&marked("restore")&&age>100){busy=true;
            runtime.preferences(scope(runtime,owner),10).thenComposeAsync(value->{if(value.recentN()!=2)throw new IllegalStateException("preferences not restored");return runtime.lore(repo->{var row=repo.get(new LoreRepository.Scope(runtime.worldId(),npc,owner),owner,"facts:gate");if(!row.entry().content().contains("COBALT-732"))throw new IllegalStateException("Lore not restored");return null;});},server)
                .thenComposeAsync(v->runtime.history(h->{var turn=h.find(scope(runtime,other),pending);if(turn==null||turn.state()!=HistoryRepository.State.INTERRUPTED||turn.inbound()==null||turn.reply()!=null)throw new IllegalStateException("Pending source recovery");for(var e:counts.entrySet())if(h.page(scope(runtime,e.getKey()),Long.MAX_VALUE,20).size()!=e.getValue())throw new IllegalStateException("History snapshot mismatch");return null;}),server)
                .whenCompleteAsync((v,e)->{busy=false;if(e!=null)failure="restore validation "+e;else{stage=4;state(server,"final");}},server);
        }else if(stage==4&&marked("final")){validateTraces(server);finish(server,failure);}
    }
    ConversationScope scope(NpcRuntime runtime,UUID player){return new ConversationScope(runtime.worldId(),npc,player);}
    boolean marked(String phase){return P5Files.read("A-"+phase+".json")!=null&&(integrated||P5Files.read("B-"+phase+".json")!=null);}
    void validateTraces(MinecraftServer server){for(var p:server.getPlayerManager().getPlayerList()){var trace=Agnor.dialogue(server).trace(p);boolean b=p.getName().getString().equals("P5B");if(trace==null||trace.historyMessages()!=(b?1:2)||trace.loreIds().stream().noneMatch(id->id.contains("facts:gate"))||!b&&trace.loreIds().stream().anyMatch(id->id.contains("facts:secret")))failure="context window/source after settings/restore";}}
    void state(MinecraftServer server,String phase){age=0;var value=new JsonObject();value.addProperty("phase",phase);value.addProperty("npc",npc.toString());value.addProperty("world",Agnor.runtime(server).worldId().toString());value.addProperty("backup",backup);P5Files.write("state.json",value);System.out.println("P5_MEMORY_PHASE "+phase);}
    void finish(MinecraftServer server,String error){var value=new JsonObject();value.addProperty("status",error==null?"PASS":"FAIL");value.addProperty("failure",error);value.addProperty("npc",String.valueOf(npc));value.addProperty("pending",String.valueOf(pending));value.addProperty("backup",backup);P5Files.write("server-result.json",value);System.out.println("P5_MEMORY_HOST "+value);server.stop(false);}
}
