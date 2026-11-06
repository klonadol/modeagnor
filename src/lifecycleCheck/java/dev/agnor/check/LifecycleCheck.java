package dev.agnor.check;

import dev.agnor.Agnor;
import dev.agnor.character.*;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

/** Dedicated, opt-in lifecycle fixture. Never part of the production JAR. */
public final class LifecycleCheck implements ModInitializer {
    private static final UUID CREATE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID DELETE = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final UUID PLAYER = UUID.fromString("10000000-0000-0000-0000-000000000004");
    private static final Path ID = Path.of("npc-id.txt");
    private boolean started;
    @Override public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (started) return;
            started = true;
            var world = server.getOverworld(); world.getChunk(0, 0);
            var runtime = Agnor.runtime(server);
            String stage = System.getProperty("agnor.lifecycle.stage");
            CompletableFuture<?> check;
            if ("1".equals(stage)) {
                check = runtime.create(CREATE, OWNER, DefinitionRegistry.DEV_ID, world, 0, 80, 0)
                    .thenComposeAsync(snapshot -> {
                        require(world.getEntity(snapshot.id()) != null, "created entity missing");
                        try { Files.writeString(ID, snapshot.id().toString()); } catch (Exception e) { throw new CompletionException(e); }
                        return runtime.create(CREATE, OWNER, DefinitionRegistry.DEV_ID, world, 0, 80, 0)
                            .thenComposeAsync(replay -> {
                                require(replay.id().equals(snapshot.id()), "duplicate create ID");
                                world.getEntity(snapshot.id()).refreshPositionAndAngles(3, 81, 4, 12, 2);
                                return runtime.unload(snapshot.id()).thenComposeAsync(nothing -> {
                                    require(world.getEntity(snapshot.id()) == null, "unload left entity");
                                    return runtime.load(snapshot.id());
                                }, server);
                            }, server);
                    }, server).thenComposeAsync(snapshot -> {
                        require(snapshot.x() == 3 && snapshot.y() == 81 && snapshot.z() == 4, "position lost on reload");
                        return runtime.relationship(snapshot.id(), OWNER, 21)
                            .thenComposeAsync(v -> runtime.relationship(snapshot.id(), PLAYER, -7), server);
                    }, server);
            } else {
                UUID id;
                try { id = UUID.fromString(Files.readString(ID).trim()); } catch (Exception e) { throw new IllegalStateException(e); }
                check = runtime.load(id).thenComposeAsync(snapshot -> {
                    require(snapshot.x() == 3 && snapshot.y() == 81 && snapshot.z() == 4, "restart lost position");
                    require(world.getEntity(id) != null, "restart entity missing");
                    return runtime.relationship(id, OWNER);
                }, server).thenComposeAsync(value -> {
                    require(value == 21, "owner relationship lost"); return runtime.relationship(id, PLAYER);
                }, server).thenComposeAsync(value -> {
                    require(value == -7, "player relationship lost"); return runtime.delete(DELETE, id);
                }, server).thenComposeAsync(v -> runtime.delete(DELETE, id), server)
                  .thenComposeAsync(v -> {
                      require(world.getEntity(id) == null, "delete left entity");
                      return runtime.load(id).handle((snapshot, error) -> { require(error != null, "deleted NPC loaded"); return null; });
                  }, server).thenComposeAsync(v -> runtime.create(CREATE, OWNER, DefinitionRegistry.DEV_ID, world, 0, 80, 0), server)
                  .thenAcceptAsync(snapshot -> {
                      require(snapshot.deleted(), "create replay resurrected tombstone");
                      require(world.getEntity(id) == null, "create replay spawned deleted NPC");
                  }, server);
            }
            check.orTimeout(60, TimeUnit.SECONDS).whenComplete((value, error) -> server.execute(() -> {
                if (error != null) {
                    Agnor.LOGGER.error("LIFECYCLE CHECK FAILED stage={}", stage, error);
                    try { Files.writeString(Path.of("FAILED-" + stage), error.toString()); } catch (Exception ignored) {}
                } else {
                    Agnor.LOGGER.info("LIFECYCLE CHECK PASS stage={}", stage);
                    try { Files.writeString(Path.of("PASS-" + stage), "PASS"); } catch (Exception e) { throw new IllegalStateException(e); }
                }
                server.stop(false);
            }));
        });
    }
    private static void require(boolean condition, String reason) { if (!condition) throw new IllegalStateException(reason); }
}
