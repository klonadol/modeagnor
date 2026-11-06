package dev.agnor.network;

import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class PackProtocol {
    public record Request(PackWire.Request value) implements CustomPayload {
        public static final Id<Request> ID=new Id<>(Identifier.of("agnor","pack_request"));
        public static final PacketCodec<RegistryByteBuf,Request> CODEC=PacketCodec.of((p,b)->b.writeBytes(PackWire.encode(p.value)),b->{if(b.readableBytes()>PackWire.MAX_REQUEST)throw new IllegalArgumentException("Pack request size");var bytes=new byte[b.readableBytes()];b.readBytes(bytes);return new Request(PackWire.request(bytes));});
        public Id<Request> getId(){return ID;}
    }
    public record Response(PackWire.Response value) implements CustomPayload {
        public static final Id<Response> ID=new Id<>(Identifier.of("agnor","pack_response"));
        public static final PacketCodec<RegistryByteBuf,Response> CODEC=PacketCodec.of((p,b)->b.writeBytes(PackWire.encode(p.value)),b->{if(b.readableBytes()>PackWire.MAX_RESPONSE)throw new IllegalArgumentException("Pack response size");var bytes=new byte[b.readableBytes()];b.readBytes(bytes);return new Response(PackWire.response(bytes));});
        public Id<Response> getId(){return ID;}
    }
    public static void register() {
        PayloadTypeRegistry.playC2S().register(Request.ID,Request.CODEC);PayloadTypeRegistry.playS2C().register(Response.ID,Response.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Request.ID,(p,c)->PackSync.handle(c.player(),p.value()));
    }
}
