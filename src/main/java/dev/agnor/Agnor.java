package dev.agnor;

import dev.agnor.network.ProbeProtocol;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Agnor implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("agnor");

    @Override public void onInitialize() {
        ProbeProtocol.register();
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            try {
                var roots = DataRoots.prepareLayout(FabricLoader.getInstance().getGameDir());
                LOGGER.info("AGNOR data directories ready at {}", roots.root);
            } catch (java.io.IOException e) {
                throw new IllegalStateException("AGNOR catalog unavailable; check game directory permissions", e);
            }
        });
        LOGGER.info("AGNOR initialized; protocol {}", ProbeProtocol.VERSION);
    }
}
