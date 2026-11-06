package dev.agnor.check;

import dev.agnor.Agnor;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.GameMode;

public final class P1ServerCheck implements ModInitializer {
    private final Set<UUID> initialized = new HashSet<>();
    private boolean adminGranted, finished;
    private int ticks;
    @Override public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (finished) return;
            boolean integrated = !server.isDedicated();
            server.getOverworld().getChunk(0,0);
            for (var player : server.getPlayerManager().getPlayerList()) {
                if (initialized.add(player.getUuid())) {
                    server.getPlayerManager().removeFromOperators(player.getGameProfile());
                    player.changeGameMode(GameMode.SPECTATOR);
                    player.refreshPositionAndAngles(0,80,0,0,0);
                    Agnor.LOGGER.info("P1 CHECK connected name={} uuid={}", player.getName().getString(), player.getUuid());
                }
            }
            if (!adminGranted && P1Files.exists(P1Files.key("admin-request"))) {
                var player = server.getPlayerManager().getPlayer("P1B");
                if (player != null) {
                    server.getPlayerManager().addToOperators(player.getGameProfile());
                    adminGranted = true; P1Files.write(P1Files.key("admin-granted"),"yes");
                }
            }
            boolean failed = P1Files.exists(P1Files.key("FAILED-A")) || P1Files.exists(P1Files.key("FAILED-B"));
            boolean success = P1Files.exists(P1Files.key("done-A")) && (integrated || P1Files.exists(P1Files.key("done-B")));
            if (failed || success || ++ticks > 7200) {
                finished = true;
                boolean pass = success && !failed;
                P1Files.write(P1Files.key(pass ? "SERVER-PASS" : "SERVER-FAIL"),"done");
                Agnor.LOGGER.info("P1 CHECK {} world={} stage={} integrated={}", pass ? "PASS" : "FAIL",P1Files.WORLD,P1Files.STAGE,integrated);
                server.stop(false);
            }
        });
    }
}
