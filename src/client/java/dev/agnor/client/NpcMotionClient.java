package dev.agnor.client;

import dev.agnor.Agnor;
import dev.agnor.character.NpcEntity;
import dev.agnor.motion.*;
import dev.agnor.network.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import java.util.*;
import org.joml.Matrix4f;

/** Local asset binding and presentation cache; never accepts client-authored bone/motion inputs. */
public final class NpcMotionClient {
    public record Metrics(long accepted,long dropped,long rigMismatch,long bytes,long rendered,long submissionNanos,long stale) {}
    private record Bound(NpcEntity entity,MotionRig rig,String key,PoseBuffer buffer) {}
    private static final Map<UUID,Bound> buffers=new HashMap<>();private static MotionRig asset;private static Matrix4f[][] neutral;private static ClientWorld world;
    private static long accepted,dropped,rigMismatch,bytes,rendered,submissionNanos,stale;
    public static void install(){
        try{asset=MotionRig.configured();if(asset!=null)neutral=asset.palettes(asset.neutral());}
        catch(Exception error){asset=null;Agnor.LOGGER.warn("AGNOR client motion disabled: invalid local rig metadata ({})",error.getClass().getSimpleName());}
        ClientPlayNetworking.registerGlobalReceiver(PoseProtocol.Snapshot.ID,(p,c)->c.client().execute(()->receive(c.client(),p.value())));
        ClientPlayConnectionEvents.JOIN.register((h,s,c)->clear());ClientPlayConnectionEvents.DISCONNECT.register((h,c)->clear());
        ClientTickEvents.END_CLIENT_TICK.register(client->{if(world!=client.world){clear();world=client.world;}if(client.world!=null)buffers.values().removeIf(b->b.entity.isRemoved()||b.rig!=asset(b.entity)||!b.key.equals(b.entity.content()));});
    }
    private static void receive(MinecraftClient client,PoseWire.Frame f){
        bytes+=PoseWire.encode(f).length;
        if(client.world==null||!client.world.getRegistryKey().getValue().toString().equals(f.dimension())||!(client.world.getEntityById(f.entityId()) instanceof NpcEntity entity)||!entity.getUuid().equals(f.npc())){dropped++;return;}
        var asset=asset(entity);if(asset==null){dropped++;return;}
        var b=buffers.get(f.npc());
        if(b==null||b.entity!=entity||b.rig!=asset||!b.key.equals(entity.content())){if(buffers.size()>=64)buffers.remove(buffers.keySet().iterator().next());b=new Bound(entity,asset,entity.content(),new PoseBuffer(asset.rig(),asset.morphCount()));buffers.put(f.npc(),b);}
        var result=b.buffer.accept(f,System.nanoTime());
        if(result==PoseBuffer.Acceptance.APPLIED)accepted++;else{dropped++;if(result==PoseBuffer.Acceptance.STALE)stale++;if(result==PoseBuffer.Acceptance.RIG_MISMATCH){b.buffer.invalidate();rigMismatch++;}}
    }
    public static void clear(){buffers.clear();world=null;}
    public static MotionRig asset(){return asset;}static Matrix4f[][] neutral(){return neutral;}
    public static MotionRig asset(NpcEntity entity){var binding=ContentClient.binding(entity);return entity.content().isEmpty()?asset:binding==null?null:binding.rig();}
    public static Optional<PoseBuffer.Sample> sample(NpcEntity entity){var b=buffers.get(entity.getUuid());return b==null||b.entity!=entity||b.rig!=asset(entity)||!b.key.equals(entity.content())?Optional.empty():b.buffer.sample(System.nanoTime());}
    public static PoseWire.Frame latest(UUID npc){var b=buffers.get(npc);return b==null||b.rig!=asset(b.entity)||!b.key.equals(b.entity.content())?null:b.buffer.latest();}
    static void rendered(long nanos){rendered++;submissionNanos+=nanos;}
    public static Metrics metrics(){return new Metrics(accepted,dropped,rigMismatch,bytes,rendered,submissionNanos,stale);}
    private NpcMotionClient(){}
}
