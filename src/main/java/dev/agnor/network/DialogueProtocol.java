package dev.agnor.network;

import dev.agnor.Agnor;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class DialogueProtocol {
    public record Request(DialogueWire.Request value) implements CustomPayload {
        public static final Id<Request> ID=new Id<>(Identifier.of("agnor","dialogue_request"));
        public static final PacketCodec<RegistryByteBuf,Request> CODEC=PacketCodec.of((value,buf)->buf.writeBytes(DialogueWire.encode(value.value())),buf->{
            if(buf.readableBytes()>DialogueWire.MAX_REQUEST)throw new IllegalArgumentException("Dialogue request size");byte[] bytes=new byte[buf.readableBytes()];buf.readBytes(bytes);return new Request(DialogueWire.request(bytes));});
        @Override public Id<Request>getId(){return ID;}
    }
    public record Response(DialogueWire.Response value) implements CustomPayload {
        public static final Id<Response> ID=new Id<>(Identifier.of("agnor","dialogue_response"));
        public static final PacketCodec<RegistryByteBuf,Response> CODEC=PacketCodec.of((value,buf)->buf.writeBytes(DialogueWire.encode(value.value())),buf->{
            if(buf.readableBytes()>DialogueWire.MAX_RESPONSE)throw new IllegalArgumentException("Dialogue response size");byte[] bytes=new byte[buf.readableBytes()];buf.readBytes(bytes);return new Response(DialogueWire.response(bytes));});
        @Override public Id<Response>getId(){return ID;}
    }
    public static void register(){
        PayloadTypeRegistry.playC2S().register(Request.ID,Request.CODEC);PayloadTypeRegistry.playS2C().register(Response.ID,Response.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Request.ID,(payload,ctx)->Agnor.dialogue(ctx.server()).handle(ctx.player(),payload.value()));
    }
}
