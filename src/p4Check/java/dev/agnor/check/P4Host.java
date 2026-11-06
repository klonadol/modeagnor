package dev.agnor.check;
import dev.agnor.Agnor;
import dev.agnor.character.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import java.util.*;
import com.google.gson.*;
import java.util.concurrent.*;

public final class P4Host implements ModInitializer {
    int ticks,stage;boolean busy;UUID npc,owner;final Set<UUID> placed=new HashSet<>();String failure;
    public void onInitialize(){if(System.getProperty("agnor.p4.stage","").startsWith("sync")){new P4SyncHost().install();return;}ServerTickEvents.END_SERVER_TICK.register(server->{
        if(++ticks>4000){failure="deadline";finish(server);return;}
        if(failure!=null){finish(server);return;}
        if(server.getPlayerManager().getCurrentPlayerCount()==0||busy)return;
        var world=server.getOverworld();var runtime=Agnor.runtime(server);var content=Agnor.content(server);
        for(var player:server.getPlayerManager().getPlayerList())if(placed.add(player.getUuid())) {
            player.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);player.teleport(world,0,80,-3,0,7);
            world.getChunk(0,0);world.getChunk(-1,-1);world.setTimeOfDay(6000);
            for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)world.setBlockState(new net.minecraft.util.math.BlockPos(x,79,z),net.minecraft.block.Blocks.STONE.getDefaultState());
        }
        if(stage==0){
            busy=true;owner=server.getPlayerManager().getPlayerList().getFirst().getUuid();
            if(System.getProperty("agnor.p4.stage","").equals("restart")) {
                if(runtime.activeEntities().size()!=1||!content.active().packs().containsKey("firefly")){failure="restore unavailable";busy=false;return;}
                npc=runtime.activeEntities().getFirst().getUuid();
                var prior=P4Files.read("prior.json");
                if(prior==null||!npc.toString().equals(prior.get("npc").getAsString())){failure="restore identity";busy=false;return;}
                runtime.inspect(npc).thenCombine(runtime.relationship(npc,owner),(s,affinity)->{if(!s.owner().equals(owner)||affinity!=23)throw new IllegalStateException("restore ownership/relationship");return s;})
                    .whenCompleteAsync((s,e)->{if(e!=null)failure="restore "+e;else{stage=4;state("restored",content.active().packs().get("firefly").digest());}busy=false;},server);return;
            }
            CompletableFuture<RuntimeSnapshot> created;
            if(runtime.activeEntities().isEmpty())created=runtime.create(UUID.randomUUID(),owner,DefinitionRegistry.DEV_ID,world,0,80,2);
            else created=runtime.inspect(runtime.activeEntities().getFirst().getUuid());
            created.thenComposeAsync(snapshot->{npc=snapshot.id();return content.enable("firefly");},server)
                .thenComposeAsync(g->runtime.redefine(npc,"firefly:companion"),server).thenComposeAsync(s->runtime.relationship(npc,owner,23),server)
                .whenCompleteAsync((v,e)->{if(e!=null)failure="activation "+e;else{stage=1;state("enabled",content.active().packs().get("firefly").digest());}busy=false;},server);
        }else if(stage==1&&P4Files.read("A-enabled.json")!=null){
            busy=true;String digest=content.active().packs().get("firefly").digest();
            var path=net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("mods/agnor/packs/firefly/manifest.json");
            CompletableFuture.runAsync(()->{try{java.nio.file.Files.writeString(path,"corrupt");}catch(Exception e){throw new CompletionException(e);}})
                .thenComposeAsync(v->content.enable("firefly"),server).whenCompleteAsync((v,e)->{
                    if(e==null||!content.active().packs().get("firefly").digest().equals(digest)){failure="bad reload replaced active";busy=false;return;}
                    content.disable("firefly").whenCompleteAsync((g,error)->{if(error!=null)failure="disable "+error;else{stage=2;state("disabled","");}busy=false;},server);
                },server);
        }else if(stage==2&&P4Files.read("A-disabled.json")!=null){
            busy=true;content.rollback().whenCompleteAsync((g,e)->{if(e!=null)failure="rollback "+e;else{stage=3;state("rollback",g.packs().get("firefly").digest());}busy=false;},server);
        }else if((stage==3&&P4Files.read("A-rollback.json")!=null)||(stage==4&&P4Files.read("A-restored.json")!=null)){
            busy=true;runtime.inspect(npc).thenCombine(runtime.relationship(npc,owner),(s,affinity)->{
                if(!s.id().equals(npc)||!s.owner().equals(owner)||affinity!=23||!s.definition().equals("firefly:companion"))throw new IllegalStateException("Identity/relationship changed");return s;
            }).whenCompleteAsync((s,e)->{if(e!=null)failure="identity "+e;finish(server);},server);
        }
    });}
    void state(String phase,String digest){var value=new JsonObject();value.addProperty("phase",phase);value.addProperty("npc",npc.toString());value.addProperty("digest",digest);P4Files.write("state.json",value);}
    void finish(net.minecraft.server.MinecraftServer server){var value=new JsonObject();value.addProperty("status",failure==null?"PASS":"FAIL");value.addProperty("failure",failure);value.addProperty("npc",String.valueOf(npc));value.addProperty("metrics",String.valueOf(Agnor.motion(server).metrics()));P4Files.write("server-result.json",value);System.out.println("P4_HOST_RESULT "+value);server.stop(false);}
}
