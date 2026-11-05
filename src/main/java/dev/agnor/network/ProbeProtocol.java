package dev.agnor.network;

import java.util.HashMap;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Phase 0 read-only probe. No client-supplied identity or writable gameplay state. */
public final class ProbeProtocol {
    public static final int VERSION = 1;
    public static final int OK = 0, VERSION_MISMATCH = 1, INVALID_SEQUENCE = 2;
    private ProbeProtocol() {}

    public record Request(int version, long sequence) implements CustomPayload {
        public static final Id<Request> ID = new Id<>(Identifier.of("agnor", "probe_request"));
        public static final PacketCodec<RegistryByteBuf, Request> CODEC = PacketCodec.of(
            (value, buf) -> { buf.writeInt(value.version); buf.writeLong(value.sequence); },
            buf -> new Request(buf.readInt(), buf.readLong()));
        @Override public Id<Request> getId() { return ID; }
    }

    public record Snapshot(int version, long sequence, int status, int serverTick) implements CustomPayload {
        public static final Id<Snapshot> ID = new Id<>(Identifier.of("agnor", "probe_snapshot"));
        public static final PacketCodec<RegistryByteBuf, Snapshot> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeInt(value.version); buf.writeLong(value.sequence);
                buf.writeInt(value.status); buf.writeInt(value.serverTick);
            }, buf -> new Snapshot(buf.readInt(), buf.readLong(), buf.readInt(), buf.readInt()));
        @Override public Id<Snapshot> getId() { return ID; }
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(Request.ID, Request.CODEC);
        PayloadTypeRegistry.playS2C().register(Snapshot.ID, Snapshot.CODEC);
        // Server thread owns this connection-local probe bookkeeping; not persistent storage.
        var lastTicks = new HashMap<UUID, Integer>();
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> lastTicks.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> lastTicks.clear());
        ServerPlayNetworking.registerGlobalReceiver(Request.ID, (request, context) -> {
            int tick = context.server().getTicks();
            UUID sender = context.player().getUuid();
            Integer previous = lastTicks.get(sender);
            if (previous != null && tick - previous < 20) return;
            lastTicks.put(sender, tick);
            int status = request.version != VERSION ? VERSION_MISMATCH
                : request.sequence < 0 ? INVALID_SEQUENCE : OK;
            ServerPlayNetworking.send(context.player(), new Snapshot(VERSION, request.sequence, status, status == OK ? tick : 0));
        });
    }
}
