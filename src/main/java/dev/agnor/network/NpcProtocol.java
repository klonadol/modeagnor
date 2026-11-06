package dev.agnor.network;

import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class NpcProtocol {
    private NpcProtocol() {}
    public record Request(NpcWire.Request value) implements CustomPayload {
        public static final Id<Request> ID = new Id<>(Identifier.of("agnor", "npc_request"));
        public static final PacketCodec<RegistryByteBuf, Request> CODEC = PacketCodec.of(
                (value, buf) -> buf.writeBytes(NpcWire.encode(value.value)),
                buf -> {
                    if (buf.readableBytes() != NpcWire.REQUEST_BYTES) throw new IllegalArgumentException("NPC request size");
                    byte[] bytes = new byte[NpcWire.REQUEST_BYTES]; buf.readBytes(bytes); return new Request(NpcWire.request(bytes));
                });
        @Override public Id<Request> getId() { return ID; }
    }
    public record Snapshot(NpcWire.Snapshot value) implements CustomPayload {
        public static final Id<Snapshot> ID = new Id<>(Identifier.of("agnor", "npc_snapshot"));
        public static final PacketCodec<RegistryByteBuf, Snapshot> CODEC = PacketCodec.of(
                (value, buf) -> buf.writeBytes(NpcWire.encode(value.value)),
                buf -> {
                    if (buf.readableBytes() > NpcWire.MAX_SNAPSHOT_BYTES) throw new IllegalArgumentException("NPC snapshot size");
                    byte[] bytes = new byte[buf.readableBytes()]; buf.readBytes(bytes); return new Snapshot(NpcWire.snapshot(bytes));
                });
        @Override public Id<Snapshot> getId() { return ID; }
    }
    public static void register() {
        PayloadTypeRegistry.playC2S().register(Request.ID, Request.CODEC);
        PayloadTypeRegistry.playS2C().register(Snapshot.ID, Snapshot.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Request.ID, (request, context) ->
                NpcManagement.execute(context.player(), request.value()));
    }
}
