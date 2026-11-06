package dev.agnor.motion;

import dev.agnor.Agnor;
import dev.agnor.character.*;
import dev.agnor.network.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.MinecraftServer;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** One world-session host producer. Runtime/Storage retain NPC identity and root ownership. */
public final class MotionService implements AutoCloseable {
    public record Metrics(long steps,long nanos,long frames,long deliveries,long bytes,long failures,long chainFallbacks) {}
    private final MinecraftServer server;private final NpcRuntime runtime;private final MotionRig asset;
    private final Map<UUID,State> states=new HashMap<>();private final Map<UUID,DialogueMotion> dialogue=new HashMap<>();private final Map<UUID,PoseWire.Frame> latest=new HashMap<>();
    private final Map<UUID,Long> sequences=new HashMap<>();
    private long steps,nanos,frames,deliveries,bytes,failures,chainFallbacks,lastTickNanos;private boolean closed;
    private final class State {
        final MotionRig asset;
        final ProceduralLocomotion gait;final SecondaryPhysics secondary;final FaceController face;
        List<Float> weights;double ox,oy,oz,px,py,pz;float yaw;long seq;boolean sleeping,disabled;long lastConstraintLog=-1000;
        State(NpcEntity entity,MotionRig asset){this.asset=asset;gait=asset.locomotion();secondary=asset.secondary();face=asset.face();reset(entity);}
        void reset(NpcEntity entity){gait.reset();secondary.reset();weights=Collections.nCopies(asset.morphCount(),0f);ox=px=entity.getX();oy=py=entity.getY();oz=pz=entity.getZ();yaw=modelYaw(entity.getYaw());sleeping=false;}
    }
    public MotionService(MinecraftServer server,NpcRuntime runtime,MotionRig asset){
        this.server=server;this.runtime=runtime;this.asset=asset;
        // Resolve unsupported profile once at startup, before accepting tick work.
        if(asset!=null){asset.locomotion();asset.secondary();asset.face();
        Agnor.LOGGER.info("AGNOR host motion enabled; rig={}, joints={}, parts={}",asset.rig().revision(),asset.rig().size(),asset.parts().size());}
    }
    private void thread(){if(!server.isOnThread())throw new IllegalStateException("Motion requires server thread");}
    public void begin(UUID npc,UUID player,UUID request){
        thread();if(closed||dialogue.size()>=64&&!dialogue.containsKey(npc))return;
        dialogue.computeIfAbsent(npc,id->new DialogueMotion()).start(new DialogueMotion.Token(player,request),server.getTicks());
    }
    public void finish(UUID npc,UUID player,UUID request){thread();var d=dialogue.get(npc);if(d!=null)d.finish(new DialogueMotion.Token(player,request),server.getTicks());}
    public void cancel(UUID npc,UUID player,UUID request){thread();var d=dialogue.get(npc);if(d!=null)d.cancel(new DialogueMotion.Token(player,request));}
    public void disconnect(UUID player){thread();for(var d:dialogue.values())d.disconnect(player);}
    public void tick(){
        thread();if(closed)return;long tickStart=System.nanoTime();var active=runtime.activeEntities().stream().filter(e->!e.isRemoved()).sorted(Comparator.comparing(NpcEntity::getUuid)).limit(16).toList();var ids=new HashSet<UUID>();
        for(var entity:active){
            UUID id=entity.getUuid();ids.add(id);var tracking=PlayerLookup.tracking(entity).stream().filter(p->ServerPlayNetworking.canSend(p,PoseProtocol.Snapshot.ID)).toList();
            var binding=runtime.binding(id);MotionRig asset=binding!=null?(binding.capability()==dev.agnor.content.PackGeneration.Capability.MOTION?binding.rig():null):runtime.external(id)?null:this.asset;
            if(asset==null){states.remove(id);latest.remove(id);continue;}
            var state=states.get(id);
            if(state==null||state.asset!=asset){long sequence=state==null?0:state.seq;state=new State(entity,asset);state.seq=sequence;states.put(id,state);latest.remove(id);}
            if(state.disabled)continue;
            if(tracking.isEmpty()){state.sleeping=true;continue;}if(state.sleeping)state.reset(entity);
            long start=System.nanoTime();
            try{
                double dx=entity.getX()-state.px,dy=entity.getY()-state.py,dz=entity.getZ()-state.pz;float yaw=modelYaw(entity.getYaw());
                float yawDelta=(float)Math.atan2(Math.sin(yaw-state.yaw),Math.cos(yaw-state.yaw));
                if(Math.sqrt(dx*dx+dy*dy+dz*dz)>.5||Math.abs(entity.getX()-state.ox)>32||Math.abs(entity.getY()-state.oy)>32||Math.abs(entity.getZ()-state.oz)>32||Math.abs(yawDelta)>1.5f){state.reset(entity);dx=dy=dz=0;}
                float x=(float)(entity.getX()-state.ox),y=(float)(entity.getY()-state.oy),z=(float)(entity.getZ()-state.oz);
                var d=dialogue.computeIfAbsent(id,key->new DialogueMotion());
                var intent=d.sample(server.getTicks(),player->{var p=server.getPlayerManager().getPlayer(player);return p!=null&&p.getWorld()==entity.getWorld()&&p.squaredDistanceTo(entity)<=16*16;});
                if(intent.token()!=null&&Math.hypot(dx,dz)*20<.05){
                    // Trusted dialogue intent may turn a stationary body; navigation retains moving root ownership.
                    var player=server.getPlayerManager().getPlayer(intent.token().player());
                    float wanted=(float)Math.toDegrees(Math.atan2(-(player.getX()-entity.getX()),player.getZ()-entity.getZ()));
                    float change=(float)Math.IEEEremainder(wanted-entity.getYaw(),360);entity.setYaw(entity.getYaw()+Math.max(-4,Math.min(4,change)));yaw=modelYaw(entity.getYaw());
                }
                var body=state.gait.step(new ProceduralLocomotion.Input(x,z,yaw,(float)(dx*20),(float)(dz*20),0),.05f).pose();
                TwoBoneIk.Point target=null;
                if(intent.token()!=null){
                    var player=server.getPlayerManager().getPlayer(intent.token().player());var eye=player.getEyePos();var local=new Quaternionf().rotationY(-yaw).transform(new Vector3f((float)(eye.x-entity.getX()),(float)(eye.y-entity.getY()),(float)(eye.z-entity.getZ())));target=TwoBoneIk.Point.of(local);
                    int head=asset.bone("head");if(head>=0){var rest=asset.rig().globals(body);var forward=rest[head].getUnnormalizedRotation(new Quaternionf()).normalize().invert().transform(new Vector3f(0,0,-1));body=LookAtSolver.solve(asset.rig(),body,head,target,TwoBoneIk.Point.of(forward),.5f);
                        if(intent.gesture()==DialogueMotion.Gesture.NOD){body=new ArrayList<>(body);float nod=(float)Math.sin((server.getTicks()-intent.phaseStart())*.35)*.04f;ProceduralLocomotion.rotateWorld(asset.rig(),body,head,new Quaternionf().rotationX(nod));}
                    }
                }
                int blinkTick=(int)Math.floorMod((long)server.getTicks()+id.getLeastSignificantBits(),100);float blink=blinkTick<4?1-Math.abs(blinkTick-2)/2f:0;
                // Mouth opening is a fixed semantic cue; no audio/phoneme timing is asserted before P8.
                var face=state.face.evaluate(asset.rig(),body,new FaceController.Input(intent.expression(),intent.strength(),blink,blink,intent.state()==PoseWire.State.SPEAKING?.15f:0,target));
                state.weights=FaceController.smooth(state.weights,face.weights(),.05f,.04f);
                var physics=state.secondary.step(face.pose(),new SecondaryPhysics.Root(x,y,z,yaw),.05f,false);
                chainFallbacks+=physics.outcomes().stream().filter(o->o.status()==SecondaryPhysics.Status.CONSTRAINT_CONFLICT||o.status()==SecondaryPhysics.Status.RESET_INVALID).count();
                if(physics.outcomes().stream().anyMatch(o->o.status()==SecondaryPhysics.Status.CONSTRAINT_CONFLICT||o.status()==SecondaryPhysics.Status.RESET_INVALID)&&server.getTicks()-state.lastConstraintLog>=100){
                    state.lastConstraintLog=server.getTicks();Agnor.LOGGER.warn("AGNOR secondary chain fallback; npc={}, statuses={}",id,physics.outcomes().stream().filter(o->o.status()!=SecondaryPhysics.Status.ACTIVE).map(o->o.id()+":"+o.status()).toList());
                }
                state.px=entity.getX();state.py=entity.getY();state.pz=entity.getZ();state.yaw=yaw;
                if(server.getTicks()%2==0){
                    var frame=PoseWire.fromPose(runtime.worldId(),id,entity.getId(),sequences.merge(id,1L,Long::sum),server.getTicks(),entity.getWorld().getRegistryKey().getValue().toString(),asset.rig(),physics.pose(),state.weights,entity.getX(),entity.getY(),entity.getZ(),yaw,intent.state());
                    int size=PoseWire.encode(frame).length;latest.put(id,frame);frames++;
                    for(var p:tracking){ServerPlayNetworking.send(p,new PoseProtocol.Snapshot(frame));deliveries++;bytes+=size;}
                }
            }catch(RuntimeException error){state.disabled=true;failures++;latest.remove(id);Agnor.LOGGER.warn("AGNOR motion disabled for NPC {}; neutral fallback ({})",id,error.getClass().getSimpleName());}
            finally{steps++;nanos+=System.nanoTime()-start;}
        }
        states.keySet().removeIf(id->!ids.contains(id));dialogue.keySet().removeIf(id->!ids.contains(id));latest.keySet().removeIf(id->!ids.contains(id));
        sequences.keySet().removeIf(id->!ids.contains(id));
        lastTickNanos=System.nanoTime()-tickStart;
    }
    public Metrics metrics(){thread();return new Metrics(steps,nanos,frames,deliveries,bytes,failures,chainFallbacks);}
    public long lastTickNanos(){thread();return lastTickNanos;}
    public PoseWire.Frame latest(UUID npc){thread();return latest.get(npc);}
    public MotionRig asset(){return asset;}
    private static float modelYaw(float yaw){return (float)Math.PI-(float)Math.toRadians(Math.IEEEremainder(yaw,360));}
    public void close(){thread();closed=true;states.clear();dialogue.clear();latest.clear();}
}
