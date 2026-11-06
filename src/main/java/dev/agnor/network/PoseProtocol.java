package dev.agnor.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** No C2S pose path: clients cannot publish authoritative motion. */
public final class PoseProtocol {
    public record Snapshot(PoseWire.Frame value) implements CustomPayload {
        public static final Id<Snapshot> ID=new Id<>(Identifier.of("agnor","pose_snapshot"));
        public static final PacketCodec<RegistryByteBuf,Snapshot> CODEC=PacketCodec.of((p,b)->b.writeBytes(PoseWire.encode(p.value)),b->{
            if(b.readableBytes()>PoseWire.MAX_BYTES)throw new IllegalArgumentException("Pose payload size");var bytes=new byte[b.readableBytes()];b.readBytes(bytes);return new Snapshot(PoseWire.decode(bytes));
        });
        public Id<Snapshot> getId(){return ID;}
    }
    public static void register(){PayloadTypeRegistry.playS2C().register(Snapshot.ID,Snapshot.CODEC);}
    private PoseProtocol(){}
}
