package dev.agnor.check;

import dev.agnor.Agnor;
import dev.agnor.character.*;
import dev.agnor.storage.LoreRepository;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import java.util.*;
import java.util.concurrent.*;
import com.google.gson.*;

public final class P5Host implements ModInitializer {
    int ticks,stage;boolean busy;UUID npc,owner;final Set<UUID> placed=new HashSet<>();String failure;
    public void onInitialize(){if(Set.of("manage","integrated","restart").contains(P5Files.STAGE)){new P5ManageHost().install();return;}if(P5Files.STAGE.equals("context")){new P5ContextHost().install();return;}ServerTickEvents.END_SERVER_TICK.register(server->{
        if(++ticks>5000||P5Files.read("abort.json")!=null){finish(server,"deadline/abort");return;}if(failure!=null){finish(server,failure);return;}if(busy||server.getPlayerManager().getCurrentPlayerCount()<2)return;
        var runtime=Agnor.runtime(server);var world=server.getOverworld();
        for(var p:server.getPlayerManager().getPlayerList())if(placed.add(p.getUuid())){p.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);p.teleport(world,0,80,-3,0,7);world.getChunk(0,0);world.getChunk(-1,-1);world.setTimeOfDay(6000);for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)world.setBlockState(new net.minecraft.util.math.BlockPos(x,79,z),net.minecraft.block.Blocks.STONE.getDefaultState());}
        if(stage==0){busy=true;owner=server.getPlayerManager().getPlayerList().stream().filter(p->p.getName().getString().equals("P5A")).findFirst().orElseThrow().getUuid();
            var old=runtime.activeEntities();var initial=old.isEmpty()?runtime.create(UUID.randomUUID(),owner,"agnor:dev",world,0,80,2):runtime.inspect(old.getFirst().getUuid());
            initial.thenComposeAsync(s->{npc=s.id();return Agnor.content(server).enable("facts");},server).whenCompleteAsync((g,e)->{busy=false;if(e!=null)failure="setup "+e;else{stage=1;var state=new JsonObject();state.addProperty("npc",npc.toString());state.addProperty("world",runtime.worldId().toString());P5Files.write("state.json",state);}},server);
        }else if(stage==1&&P5Files.read("A-result.json")!=null&&P5Files.read("B-result.json")!=null){busy=true;stage=2;
            var other=server.getPlayerManager().getPlayerList().stream().filter(p->p.getName().getString().equals("P5B")).findFirst().orElseThrow().getUuid();
            runtime.lore(repo->{
                var mine=new LoreRepository.Scope(runtime.worldId(),npc,owner);var shared=new LoreRepository.Scope(runtime.worldId(),npc,LoreRepository.SHARED);
                var gate=repo.get(mine,owner,"facts:gate");if(gate==null||gate.revision()!=2||!gate.entry().content().contains("COBALT-732"))throw new IllegalStateException("CRUD update/revision");
                var temp=repo.get(mine,owner,"facts:temp");if(temp==null||!temp.deleted()||temp.revision()!=2)throw new IllegalStateException("Delete/tombstone");
                if(repo.get(shared,owner,"facts:evil")!=null||repo.get(shared,owner,"facts:shared")==null)throw new IllegalStateException("Shared permissions");
                if(repo.get(mine,owner,"facts:secret")!=null)throw new IllegalStateException("Private isolation");
                if(repo.get(new LoreRepository.Scope(runtime.worldId(),npc,other),other,"facts:secret")==null)throw new IllegalStateException("B private writer");
                return null;
            }).whenCompleteAsync((v,e)->finish(server,e==null?null:"validation "+e),server);
        }
    });}
    void finish(net.minecraft.server.MinecraftServer server,String error){var result=new JsonObject();result.addProperty("status",error==null?"PASS":"FAIL");result.addProperty("failure",error);result.addProperty("npc",String.valueOf(npc));P5Files.write("server-result.json",result);System.out.println("P5_HOST_RESULT "+result);server.stop(false);}
}
