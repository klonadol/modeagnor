package dev.agnor.motion;

import dev.agnor.network.PoseWire;
import java.util.*;

/** Pure presentation buffer; it never evaluates host motion or extrapolates gameplay. */
public final class PoseBuffer {
    public enum Acceptance { APPLIED, STALE, RIG_MISMATCH, IDENTITY_MISMATCH, INVALID_CHANNELS }
    public record Sample(List<JointTransform> pose,List<Float> morphs,double x,double y,double z,float yaw,PoseWire.State state,long sequence){
        public Sample {pose=List.copyOf(pose);morphs=List.copyOf(morphs);}
    }
    private record Entry(PoseWire.Frame frame,List<JointTransform> pose,long received) {}
    private final RigDescriptor rig;private final int morphCount;private final ArrayDeque<Entry> frames=new ArrayDeque<>();
    private PoseWire.Frame guard;private long lastReceived;
    private static final long DELAY=100_000_000L,STALE_AFTER=750_000_000L;
    public PoseBuffer(RigDescriptor rig,int morphCount){this.rig=Objects.requireNonNull(rig);if(morphCount<0||morphCount>PoseWire.MAX_MORPHS)throw new IllegalArgumentException("Morph count");this.morphCount=morphCount;}
    public void clear(){frames.clear();guard=null;}
    public void invalidate(){frames.clear();}
    public Acceptance accept(PoseWire.Frame f,long received){
        if(guard!=null){
            var p=guard;
            if(!p.world().equals(f.world())||!p.npc().equals(f.npc())||p.entityId()!=f.entityId()||!p.dimension().equals(f.dimension()))return Acceptance.IDENTITY_MISMATCH;
            if(f.sequence()<=p.sequence()||f.tick()<=p.tick()||received<lastReceived)return Acceptance.STALE;
        }
        if(!rig.revision().equals(f.rigHash()))return Acceptance.RIG_MISMATCH;
        if(f.morphs().size()!=morphCount||f.joints().stream().anyMatch(j->j.slot()>=rig.size()))return Acceptance.INVALID_CHANNELS;
        if(guard!=null){var p=guard;
            double dx=f.x()-p.x(),dy=f.y()-p.y(),dz=f.z()-p.z();float angle=(float)Math.atan2(Math.sin(f.yaw()-p.yaw()),Math.cos(f.yaw()-p.yaw()));
            if(dx*dx+dy*dy+dz*dz>4||Math.abs(angle)>1.5f||received-lastReceived>500_000_000L)frames.clear();
        }
        var pose=new ArrayList<>(rig.rest());for(var j:f.joints())pose.set(j.slot(),j.applyScale(rig.joint(j.slot()).rest()));
        guard=f;lastReceived=received;frames.addLast(new Entry(f,List.copyOf(pose),received));while(frames.size()>4)frames.removeFirst();return Acceptance.APPLIED;
    }
    public Optional<Sample> sample(long now){
        if(frames.isEmpty()||now-frames.getLast().received>STALE_AFTER)return Optional.empty();
        long target=now-DELAY;Entry left=frames.getFirst(),right=left;
        for(var f:frames){right=f;if(f.received>=target)break;left=f;}
        float alpha=right.received==left.received?0:Math.max(0,Math.min(1,(float)(target-left.received)/(right.received-left.received)));
        var pose=new ArrayList<JointTransform>(rig.size());for(int i=0;i<rig.size();i++)pose.add(JointTransform.mix(left.pose.get(i),right.pose.get(i),alpha));
        var morphs=new ArrayList<Float>(morphCount);for(int i=0;i<morphCount;i++)morphs.add(left.frame.morphs().get(i)+(right.frame.morphs().get(i)-left.frame.morphs().get(i))*alpha);
        float dy=right.frame.yaw()-left.frame.yaw();dy=(float)Math.atan2(Math.sin(dy),Math.cos(dy));
        return Optional.of(new Sample(pose,morphs,mix(left.frame.x(),right.frame.x(),alpha),mix(left.frame.y(),right.frame.y(),alpha),mix(left.frame.z(),right.frame.z(),alpha),left.frame.yaw()+dy*alpha,
            alpha<.5f?left.frame.state():right.frame.state(),right.frame.sequence()));
    }
    public PoseWire.Frame latest(){return frames.isEmpty()?null:frames.getLast().frame;}
    private static double mix(double a,double b,float t){return a+(b-a)*t;}
}
