package dev.agnor.check;
import dev.agnor.Agnor;
import dev.agnor.character.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import java.nio.file.*;
import java.util.*;
public final class P32Server implements ModInitializer {
    boolean setup; int ticks;
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var world=server.getOverworld();world.getChunk(0,0);
            for(var player:server.getPlayerManager().getPlayerList()) {
                if(!setup) {
                    setup=true;
                    player.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
                    player.teleport(world,0,80,0,0,0);
                    for(int x=-4;x<5;x++)for(int z=-2;z<7;z++)world.setBlockState(new net.minecraft.util.math.BlockPos(x,79,z),net.minecraft.block.Blocks.STONE.getDefaultState());
                    boolean existing=false;
                    for(var entity:world.iterateEntities())if(entity instanceof NpcEntity) { existing=true; System.out.println("P32_RESTORED "+entity.getUuid()); }
                    if(!existing) Agnor.runtime(server).create(UUID.randomUUID(),player.getUuid(),DefinitionRegistry.DEV_ID,world,0,80,3)
                        .thenAccept(s -> System.out.println("P32_CREATED "+s.id()));
                    world.setTimeOfDay(6000);
                }
            }
            if(++ticks>2400 || Files.exists(Path.of(System.getProperty("agnor.p32.root"),"client-done"))) server.stop(false);
        });
    }
}
