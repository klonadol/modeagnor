package dev.agnor.network;

import dev.agnor.Agnor;
import dev.agnor.character.*;
import java.util.*;
import java.util.concurrent.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.command.CommandManager;
import net.minecraft.command.argument.UuidArgumentType;
import net.minecraft.text.Text;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import static dev.agnor.network.NpcWire.*;

/** One server-authoritative ingress for commands and packets. Never logs client input or SQL errors. */
public final class NpcManagement {
    private static final Map<ServerPlayNetworkHandler, Gate> gates = new HashMap<>();
    private NpcManagement() {}
    public static UUID selected(ServerPlayerEntity player) {
        var gate = gates.get(player.networkHandler); return gate == null ? null : gate.selected;
    }
    public static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> gates.remove(handler));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> gates.keySet().removeIf(h -> h.player.getServer() == server));
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
            dispatcher.register(CommandManager.literal("agnor")
                .then(CommandManager.literal("spawn").executes(context -> command(context.getSource().getPlayerOrThrow(), SPAWN, NIL, 0)))
                .then(CommandManager.literal("select").executes(context -> command(context.getSource().getPlayerOrThrow(), SELECT, NIL, 0))
                    .then(CommandManager.argument("npc", UuidArgumentType.uuid())
                    .executes(context -> command(context.getSource().getPlayerOrThrow(), SELECT, UuidArgumentType.getUuid(context,"npc"), 0))))
                .then(CommandManager.literal("delete").executes(context -> command(context.getSource().getPlayerOrThrow(), DELETE, NIL, 0))
                    .then(CommandManager.argument("npc", UuidArgumentType.uuid())
                    .executes(context -> command(context.getSource().getPlayerOrThrow(), DELETE, UuidArgumentType.getUuid(context,"npc"), 0))))
                .then(CommandManager.literal("relationship").then(CommandManager.argument("npc", UuidArgumentType.uuid())
                    .then(CommandManager.argument("affinity", IntegerArgumentType.integer(-100,100))
                        .executes(context -> command(context.getSource().getPlayerOrThrow(), RELATIONSHIP,
                            UuidArgumentType.getUuid(context,"npc"), IntegerArgumentType.getInteger(context,"affinity")))))));
        });
    }
    private static int command(ServerPlayerEntity player, int action, UUID id, int affinity) {
        try {
            var runtime = Agnor.runtime(player.getServer());
            if (id.equals(NIL) && (action == SELECT || action == DELETE)) {
                Gate gate = gates.get(player.networkHandler);
                if (gate == null || gate.selected == null) {
                    player.sendMessage(Text.literal("AGNOR: сначала выберите NPC через /agnor select <UUID>."), false);
                    return 0;
                }
                id = gate.selected;
            }
            execute(player, new Request(VERSION, 0, action, runtime.worldId(), id, UUID.randomUUID(), affinity));
        } catch (IllegalStateException e) { player.sendMessage(Text.literal("AGNOR unavailable"), false); }
        return 1;
    }

    public static void execute(ServerPlayerEntity player, Request request) {
        var server = player.getServer();
        if (!server.isOnThread()) throw new IllegalStateException("NPC management requires server thread");
        int validation = validate(request);
        if (validation != OK) { reply(player, status(request.sequence(), validation, NIL)); return; }
        NpcRuntime runtime;
        try { runtime = Agnor.runtime(server); }
        catch (IllegalStateException e) { reply(player, status(request.sequence(), UNAVAILABLE, NIL)); return; }
        if (request.action() != INFO && !request.world().equals(runtime.worldId())) {
            reply(player, status(request.sequence(), WORLD_MISMATCH, NIL)); return;
        }
        if (request.action() == SPAWN && player.getWorld().getRegistryKey().getValue().toString().length() > MAX_ID) {
            reply(player, status(request.sequence(), INVALID, NIL)); return;
        }
        Gate gate = gates.computeIfAbsent(player.networkHandler, ignored -> new Gate());
        if (gate.busy || server.getTicks() - gate.lastTick < 10) { reply(player, status(request.sequence(), BUSY, NIL)); return; }
        gate.busy = true; gate.lastTick = server.getTicks();
        CompletableFuture<Snapshot> future;
        if (request.action() == INFO) future = CompletableFuture.completedFuture(status(request.sequence(), OK, runtime.worldId()));
        else if (request.action() == SPAWN) {
            future = runtime.create(request.operation(), player.getUuid(), DefinitionRegistry.DEV_ID,
                    player.getServerWorld(), player.getX(), player.getY(), player.getZ())
                .thenApplyAsync(snapshot -> view(request.sequence(), runtime.worldId(), player, snapshot, 0), server);
        } else {
            future = runtime.inspect(request.npc()).thenComposeAsync(snapshot -> {
                if (!current(player, runtime)) return CompletableFuture.completedFuture(status(request.sequence(), UNAVAILABLE, NIL));
                boolean administrator = player.hasPermissionLevel(2);
                if (request.action() == DELETE) {
                    if (!NpcAccess.canManage(player.getUuid(), administrator, snapshot))
                        return CompletableFuture.completedFuture(status(request.sequence(), DENIED, NIL));
                    return runtime.delete(request.operation(), snapshot.id()).thenApply(v -> status(request.sequence(), OK, runtime.worldId()));
                }
                if (snapshot.deleted()) return CompletableFuture.completedFuture(status(request.sequence(), DELETED, NIL));
                if (!NpcAccess.nearby(player.getWorld().getRegistryKey().getValue().toString(), player.getX(), player.getY(), player.getZ(), snapshot))
                    return CompletableFuture.completedFuture(status(request.sequence(), DENIED, NIL));
                CompletableFuture<?> update = request.action() == RELATIONSHIP
                    ? runtime.relationship(snapshot.id(), player.getUuid(), request.affinity()) : CompletableFuture.completedFuture(null);
                return update.thenComposeAsync(v -> runtime.relationship(snapshot.id(), player.getUuid()), server)
                    .thenApplyAsync(affinity -> view(request.sequence(), runtime.worldId(), player, snapshot, affinity), server);
            }, server);
        }
        future.whenCompleteAsync((snapshot, error) -> {
            gate.busy = false;
            if (!current(player, runtime)) return;
            if (error != null) {
                // Deliberately omit exception text: JDBC/provider paths or future private data may appear in it.
                Agnor.LOGGER.warn("AGNOR NPC operation failed; action={}, status={}", request.action(), UNAVAILABLE);
                reply(player, status(request.sequence(), UNAVAILABLE, NIL));
            } else {
                if (snapshot.status() == OK && !snapshot.npc().equals(NIL)) gate.selected = snapshot.npc();
                if ((snapshot.status() == DELETED || (snapshot.status() == OK && request.action() == DELETE))
                        && request.npc().equals(gate.selected)) gate.selected = null;
                reply(player, snapshot);
            }
        }, server);
    }
    private static boolean current(ServerPlayerEntity player, NpcRuntime runtime) {
        var server = player.getServer();
        if (server.getPlayerManager().getPlayer(player.getUuid()) != player) return false;
        try { return Agnor.runtime(server) == runtime; } catch (IllegalStateException e) { return false; }
    }
    private static Snapshot view(long sequence, UUID world, ServerPlayerEntity player, RuntimeSnapshot snapshot, int affinity) {
        if (snapshot.deleted()) return status(sequence, DELETED, NIL);
        if (snapshot.definition().length() > MAX_ID || snapshot.dimension().length() > MAX_ID) return status(sequence, UNAVAILABLE, NIL);
        return new Snapshot(VERSION, sequence, snapshot.deleted() ? DELETED : OK, world, snapshot.id(),
                snapshot.owner().equals(player.getUuid()), affinity, snapshot.definition(), snapshot.dimension(), snapshot.x(), snapshot.y(), snapshot.z(),
                snapshot.yaw(), snapshot.pitch(), snapshot.revision(), snapshot.deleted());
    }
    private static void reply(ServerPlayerEntity player, Snapshot snapshot) {
        if (ServerPlayNetworking.canSend(player, NpcProtocol.Snapshot.ID)) ServerPlayNetworking.send(player, new NpcProtocol.Snapshot(snapshot));
        else player.sendMessage(Text.literal(NpcFeedback.message(snapshot)), false);
    }
    private static final class Gate { boolean busy; int lastTick = Integer.MIN_VALUE / 2; UUID selected; }
}
