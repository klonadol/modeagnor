package dev.agnor.check;

import dev.agnor.Agnor;
import dev.agnor.character.*;
import dev.agnor.network.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.server.MinecraftServer;
import java.util.*;
import java.util.concurrent.*;
import com.google.gson.*;

final class P4SyncHost {
    final boolean integrated=System.getProperty("agnor.p4.stage","").equals("syncIntegrated");
    int ticks,stage;boolean busy;UUID npc,owner;String failure;final Set<Object> placed=new HashSet<>();
    void install(){ServerTickEvents.END_SERVER_TICK.register(this::tick);}
    void tick(MinecraftServer server) {
        if(P4Files.read("abort.json")!=null){finish(server,"orchestration abort");return;}
        if(++ticks>6000){finish(server,"deadline");return;}
        for(String role:List.of("A","B")){var result=P4Files.read(role+"-result.json");if(result!=null&&result.get("status").getAsString().equals("FAIL")){finish(server,"client "+role+" failure");return;}}
        if(failure!=null){finish(server,failure);return;}if(busy)return;
        var runtime=Agnor.runtime(server);var content=Agnor.content(server);var world=server.getOverworld();
        for(var player:server.getPlayerManager().getPlayerList())if(placed.add(player.networkHandler)) {
            boolean b=player.getName().getString().equals("P4B");player.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);player.teleport(world,b?1:0,80,-3,b?10:0,7);
            if(!b)server.getPlayerManager().addToOperators(player.getGameProfile());
            world.getChunk(0,0);world.getChunk(-1,-1);world.setTimeOfDay(6000);
            for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)world.setBlockState(new net.minecraft.util.math.BlockPos(x,79,z),net.minecraft.block.Blocks.STONE.getDefaultState());
        }
        if(stage==0) {
            if(server.getPlayerManager().getCurrentPlayerCount()<(integrated?1:2))return;
            busy=true;var a=server.getPlayerManager().getPlayerList().stream().filter(p->!p.getName().getString().equals("P4B")).findFirst().orElseThrow();owner=a.getUuid();
            var old=runtime.activeEntities();
            var create=old.isEmpty()?runtime.create(UUID.randomUUID(),owner,DefinitionRegistry.DEV_ID,world,0,80,2):runtime.inspect(old.getFirst().getUuid());
            create.thenComposeAsync(s->{npc=s.id();if(!s.owner().equals(owner))throw new IllegalStateException("fixture owner mismatch");return content.enable("firefly");},server)
                .thenComposeAsync(g->content.enable("public"),server).thenComposeAsync(g->runtime.redefine(npc,"firefly:companion"),server)
                .thenComposeAsync(s->PackSync.share(server,"public",false),server)
                .whenCompleteAsync((g,e)->{busy=false;if(e!=null)failure="setup "+e;else{stage=1;state(server,"local");}},server);
        } else if(stage==1&&marked("local")) {stage=2;state(server,"deny");}
        else if(stage==2&&marked("deny")) {
            if(PackSync.bytesSent(server,"firefly")!=0||PackSync.bytesSent(server,"public")!=0||content.active().packs().size()!=2){finish(server,"denial or permissions violated");return;}
            busy=true;PackSync.share(server,"public",true).whenCompleteAsync((v,e)->{busy=false;if(e!=null)failure="opt-in "+e;else{stage=3;state(server,"download");}},server);
        } else if(stage==3&&marked("download")) {
            stage=4;state(server,"dialogue");
        } else if(stage==4&&P4Files.read("A-dialogue.json")!=null) {
            if(!integrated&&P4Files.read("B-private.json")==null)return;
            var data=P4Files.read("A-dialogue.json");UUID request=UUID.fromString(data.get("request").getAsString());
            busy=true;var scope=new dev.agnor.dialogue.ConversationScope(runtime.worldId(),npc,owner);
            runtime.history(h->{var turn=h.find(scope,request);if(turn.state()!=dev.agnor.storage.HistoryRepository.State.FINALIZED||turn.reply().text().isBlank())throw new IllegalStateException("final History not committed");return turn;})
                .whenCompleteAsync((v,e)->{busy=false;if(e!=null)failure="dialogue "+e;else{stage=5;state(server,integrated?"recover":"reconnect");}},server);
        } else if(stage==5&&(!integrated&&P4Files.read("B-reconnect.json")!=null||integrated)) {stage=6;state(server,"recover");}
        else if(stage==6&&marked("recover")) {
            busy=true;runtime.inspect(npc).whenCompleteAsync((s,e)->{
                if(e!=null||!s.id().equals(npc)||!s.owner().equals(owner)||!s.definition().equals("firefly:companion")){finish(server,"identity changed");return;}
                if(PackSync.bytesSent(server,"firefly")!=0||(!integrated&&PackSync.bytesSent(server,"public")<=PackTransferMinimum())){finish(server,"wrong transfer accounting");return;}
                finish(server,null);
            },server);
        }
    }
    int PackTransferMinimum(){return dev.agnor.content.PackTransfer.CHUNK_BYTES;}
    boolean marked(String phase){return P4Files.read("A-"+phase+".json")!=null&&(integrated||P4Files.read("B-"+phase+".json")!=null);}
    void state(MinecraftServer server,String phase) {var value=new JsonObject();value.addProperty("phase",phase);value.addProperty("npc",npc.toString());value.addProperty("world",Agnor.runtime(server).worldId().toString());P4Files.write("state.json",value);System.out.println("P4_SYNC_PHASE "+phase);}
    void finish(MinecraftServer server,String error){var result=new JsonObject();result.addProperty("status",error==null?"PASS":"FAIL");result.addProperty("failure",error);result.addProperty("npc",String.valueOf(npc));result.addProperty("fireflyBytes",PackSync.bytesSent(server,"firefly"));result.addProperty("publicBytes",PackSync.bytesSent(server,"public"));P4Files.write("server-result.json",result);System.out.println("P4_SYNC_RESULT "+result);server.stop(false);}
}
