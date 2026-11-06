package dev.agnor.check;
import dev.agnor.Agnor;
import dev.agnor.character.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import com.google.gson.*;
import java.util.*;

/** Actual gameplay entity/producer/packet fixture; no GPU class is referenced by the host. */
public final class P35Host implements ModInitializer {
    UUID npc;boolean setup,creating;int ticks,playTicks;final Set<UUID> placed=new HashSet<>();
    public void onInitialize(){
        if(!P35Files.STAGE.equals("smoke")){new P35ScenarioHost().install();return;}
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(Agnor.motion(server)==null)throw new IllegalStateException("Motion producer did not initialize before first tick");
            ticks++;var world=server.getOverworld();var completed=P35Files.read("A-result.json");
            if(completed!=null){var data=new JsonObject();data.addProperty("status",completed.get("status").getAsString());data.addProperty("metrics",Agnor.motion(server).metrics().toString());P35Files.write("server-result.json",data);System.out.println("P35_HOST_RESULT "+data);server.stop(false);return;}
            if(server.getPlayerManager().getCurrentPlayerCount()==0){if(ticks>4800)server.stop(false);return;}
            playTicks++;
            if(!setup){setup=true;world.getChunk(0,0);world.getChunk(-1,-1);world.setTimeOfDay(6000);for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)world.setBlockState(new net.minecraft.util.math.BlockPos(x,79,z),net.minecraft.block.Blocks.STONE.getDefaultState());}
            for(var p:server.getPlayerManager().getPlayerList())if(placed.add(p.getUuid())){p.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);p.teleport(world,0,80,-3,0,7);}
            if(!creating){creating=true;var owner=server.getPlayerManager().getPlayerList().getFirst();
                for(var old:Agnor.runtime(server).activeEntities())Agnor.runtime(server).delete(UUID.randomUUID(),old.getUuid());
                Agnor.runtime(server).create(UUID.randomUUID(),owner.getUuid(),DefinitionRegistry.DEV_ID,world,0,80,2).thenAccept(s->{npc=s.id();System.out.println("P35_CREATED "+npc);});
            }
            if(npc!=null){
                var entity=world.getEntity(npc);if(entity!=null){entity.setCustomNameVisible(false);float offset=playTicks<100?0:playTicks<180?(playTicks-100)*.01f:.8f;entity.refreshPositionAndAngles(offset,80,2,180,0);}
                if(playTicks%10==0){var data=new JsonObject();data.addProperty("npc",npc.toString());data.addProperty("world",Agnor.runtime(server).worldId().toString());data.addProperty("tick",playTicks);data.addProperty("phase","smoke");P35Files.write("state.json",data);}
            }
            if(playTicks>1200){var data=new JsonObject();data.addProperty("status","FAIL");data.addProperty("reason","client deadline");P35Files.write("server-result.json",data);server.stop(false);}
        });
    }
}
