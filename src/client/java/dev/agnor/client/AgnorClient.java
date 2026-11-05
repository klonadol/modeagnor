package dev.agnor.client;

import dev.agnor.Agnor;
import dev.agnor.DataRoots;
import dev.agnor.network.ProbeProtocol;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class AgnorClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        // Remote-server clients never fire SERVER_STARTING in their own process.
        try {
            var roots = DataRoots.prepareLayout(FabricLoader.getInstance().getGameDir());
            Agnor.LOGGER.info("AGNOR client resource directories ready at {}", roots.root);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("AGNOR client catalog unavailable; check game directory permissions", e);
        }
        ClientPlayNetworking.registerGlobalReceiver(ProbeProtocol.Snapshot.ID, (snapshot, context) -> {
            if (snapshot.version() != ProbeProtocol.VERSION || snapshot.sequence() != 0) return;
            Agnor.LOGGER.info("AGNOR probe response: status={}, authoritative server tick={}", snapshot.status(), snapshot.serverTick());
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (ClientPlayNetworking.canSend(ProbeProtocol.Request.ID)) {
                ClientPlayNetworking.send(new ProbeProtocol.Request(ProbeProtocol.VERSION, 0));
            }
        });
    }
}
