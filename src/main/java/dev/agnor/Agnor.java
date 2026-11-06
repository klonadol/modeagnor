package dev.agnor;

import dev.agnor.network.ProbeProtocol;
import dev.agnor.storage.WorldConfig;
import dev.agnor.storage.WorldStorage;
import dev.agnor.character.NpcRuntime;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Agnor implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("agnor");
    private final Map<net.minecraft.server.MinecraftServer, WorldStorage> storages = new ConcurrentHashMap<>();
    private static final Map<net.minecraft.server.MinecraftServer, NpcRuntime> runtimes = new ConcurrentHashMap<>();
    private static final Map<net.minecraft.server.MinecraftServer, dev.agnor.provider.ProviderGateway> providers = new ConcurrentHashMap<>();
    private static final Map<net.minecraft.server.MinecraftServer, dev.agnor.dialogue.DialogueService> dialogues = new ConcurrentHashMap<>();
    private static final Map<net.minecraft.server.MinecraftServer, dev.agnor.motion.MotionService> motions = new ConcurrentHashMap<>();
    private static final Map<net.minecraft.server.MinecraftServer, dev.agnor.content.ContentService> contents = new ConcurrentHashMap<>();
    public static dev.agnor.content.ContentService content(net.minecraft.server.MinecraftServer server){return contents.get(server);}
    public static dev.agnor.motion.MotionService motion(net.minecraft.server.MinecraftServer server){return motions.get(server);}
    public static dev.agnor.dialogue.DialogueService dialogue(net.minecraft.server.MinecraftServer server) {
        var service=dialogues.get(server);if(service==null)throw new IllegalStateException("Dialogue unavailable");return service;
    }
    public static void dialogueDisconnect(net.minecraft.server.MinecraftServer server,net.minecraft.server.network.ServerPlayNetworkHandler handler){var service=dialogues.get(server);if(service!=null)service.disconnect(handler);var motion=motions.get(server);if(motion!=null)motion.disconnect(handler.player.getUuid());}
    public static dev.agnor.provider.ProviderGateway provider(net.minecraft.server.MinecraftServer server) {
        var provider = providers.get(server);
        if (provider == null) throw new IllegalStateException("Main Dialogue unavailable");
        return provider;
    }
    public static NpcRuntime runtime(net.minecraft.server.MinecraftServer server) {
        NpcRuntime runtime = runtimes.get(server);
        if (runtime == null) throw new IllegalStateException("NPC runtime unavailable");
        return runtime;
    }

    @Override public void onInitialize() {
        dev.agnor.character.NpcEntity.register();
        ProbeProtocol.register();
        dev.agnor.network.NpcProtocol.register();
        dev.agnor.network.NpcManagement.register();
        dev.agnor.network.DialogueProtocol.register();
        dev.agnor.network.PoseProtocol.register();
        dev.agnor.network.DialogueCommands.register();
        dev.agnor.network.PackProtocol.register();
        dev.agnor.network.PackSync.register();
        dev.agnor.network.PackCommands.register();
        dev.agnor.network.LoreCommands.register();
        dev.agnor.network.MemoryCommands.register();
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            try {
                var roots = DataRoots.prepareLayout(FabricLoader.getInstance().getGameDir());
                LOGGER.info("AGNOR data directories ready at {}", roots.root);
                var saveRoot = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT);
                contents.put(server,new dev.agnor.content.ContentService(roots.packs,saveRoot.resolve("agnor/content"),server));
                dev.agnor.network.PackSync.start(server,saveRoot);
                var config = WorldConfig.load(saveRoot);
                storages.put(server, WorldStorage.open(saveRoot));
                LOGGER.info("AGNOR world storage ready: worldId={}, enabled={}, autosaveSeconds={}",
                        storages.get(server).worldId(), config.enabled(), config.autosaveSeconds());
            } catch (java.io.IOException | java.sql.SQLException e) {
                throw new IllegalStateException("AGNOR world storage unavailable; server save was not modified by fallback", e);
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                var preset = dev.agnor.provider.DialoguePreset.load(server.getSavePath(net.minecraft.util.WorldSavePath.ROOT));
                if (preset.enabled()) providers.put(server, new dev.agnor.provider.ProviderGateway(preset));
            } catch (java.io.IOException e) {
                LOGGER.warn("AGNOR Main Dialogue disabled: invalid configuration");
            }
            try {
                var config = WorldConfig.load(server.getSavePath(net.minecraft.util.WorldSavePath.ROOT));
                if (config.enabled()) {
                    var runtime = new NpcRuntime(server, storages.get(server), config);
                    runtimes.put(server, runtime);
                    contents.get(server).onChanged(()->{var dialogue=dialogues.get(server);if(dialogue!=null)dialogue.contentChanged(null);runtime.refreshContent();dev.agnor.network.PackSync.changed(server);});
                    runtime.start().exceptionally(error -> {
                        LOGGER.error("AGNOR NPC restore failed; stopping server", error);
                        server.execute(() -> server.stop(false));
                        return null;
                    });
                }
                var policy=dev.agnor.dialogue.ContextBuilder.Policy.load(server.getSavePath(net.minecraft.util.WorldSavePath.ROOT));
                dialogues.put(server,new dev.agnor.dialogue.DialogueService(server,runtimes.get(server),providers.get(server),policy));
            } catch (java.io.IOException e) {
                LOGGER.warn("AGNOR dialogue context disabled: invalid configuration");
                dialogues.put(server,new dev.agnor.dialogue.DialogueService(server,runtimes.get(server),null,dev.agnor.dialogue.ContextBuilder.Policy.DEFAULT));
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            try{var rig=dev.agnor.motion.MotionRig.configured();var runtime=runtimes.get(server);if(runtime!=null)motions.put(server,new dev.agnor.motion.MotionService(server,runtime,rig));}
            catch(Exception error){LOGGER.warn("AGNOR motion disabled: invalid/unsupported local rig metadata ({})",error.getClass().getSimpleName());}
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var runtime = runtimes.get(server);
            if (runtime != null) runtime.tick();
            var motion=motions.get(server);if(motion!=null)motion.tick();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            try {
                var dialogue=dialogues.remove(server);
                if(dialogue!=null)dialogue.close();
                var motion=motions.remove(server);if(motion!=null)motion.close();
                var provider = providers.remove(server);
                if (provider != null) provider.close();
                NpcRuntime runtime = runtimes.remove(server);
                if (runtime != null) runtime.close();
                var content=contents.remove(server);if(content!=null)content.close();
            } finally {
                WorldStorage storage = storages.remove(server);
                if (storage != null) storage.close();
            }
        });
        LOGGER.info("AGNOR initialized; protocol {}", ProbeProtocol.VERSION);
    }
}
