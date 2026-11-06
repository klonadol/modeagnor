package dev.agnor.check;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import java.nio.file.*;

/** Exits the dev client after the integrated server fixture completes and saves. */
public final class LifecycleClient implements ClientModInitializer {
    private int ticks, finishing;
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++ticks > 3600) throw new IllegalStateException("Integrated lifecycle check timed out");
            String stage = System.getProperty("agnor.lifecycle.stage");
            if (Files.exists(Path.of("PASS-" + stage)) || Files.exists(Path.of("FAILED-" + stage))) {
                if (++finishing > 100) client.scheduleStop();
            }
        });
    }
}
