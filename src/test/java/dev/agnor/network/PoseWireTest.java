package dev.agnor.network;

import dev.agnor.motion.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PoseWireTest {
    final String hash="a".repeat(64);final UUID world=UUID.randomUUID(),npc=UUID.randomUUID();
    PoseWire.Frame frame(List<PoseWire.Joint> joints,List<Float> weights){return new PoseWire.Frame(1,world,npc,12,1,10,"minecraft:overworld",hash,30_000_000,80,-30_000_000,3.14f,PoseWire.State.SPEAKING,joints,weights);}
    @Test void roundTripKeepsIdentityRootAndBoundedQuaternionMorphError(){
        var q=new org.joml.Quaternionf().rotationXYZ(.4f,-.5f,.3f);var f=frame(List.of(new PoseWire.Joint(10,0,1,0,q.x,q.y,q.z,q.w)),List.of(.3f,.8f));
        var decoded=PoseWire.decode(PoseWire.encode(f));assertEquals(f.world(),decoded.world());assertEquals(f.npc(),decoded.npc());assertEquals(f.x(),decoded.x());assertEquals(f.sequence(),decoded.sequence());
        var j=decoded.joints().getFirst();assertTrue(Math.abs(q.dot(new org.joml.Quaternionf(j.qx(),j.qy(),j.qz(),j.qw())))>.999999f);
        assertEquals(.3,decoded.morphs().getFirst(),1.0/255);assertThrows(UnsupportedOperationException.class,()->decoded.morphs().add(1f));
    }
    @Test void truncationTrailingVersionAndPayloadBoundsFailClosed(){
        var bytes=PoseWire.encode(frame(List.of(),List.of()));
        for(int n:new int[]{0,20,bytes.length-1})assertThrows(IllegalArgumentException.class,()->PoseWire.decode(Arrays.copyOf(bytes,n)));
        assertThrows(IllegalArgumentException.class,()->PoseWire.decode(Arrays.copyOf(bytes,bytes.length+1)));
        bytes[0]=2;assertThrows(IllegalArgumentException.class,()->PoseWire.decode(bytes));
        assertThrows(IllegalArgumentException.class,()->PoseWire.decode(new byte[PoseWire.MAX_BYTES+1]));
    }
    @Test void invalidSlotsNonfiniteAndCountsAreRejected(){
        var joint=new PoseWire.Joint(2,0,0,0,0,0,0,1);
        assertThrows(IllegalArgumentException.class,()->frame(List.of(joint,joint),List.of()));
        assertThrows(IllegalArgumentException.class,()->new PoseWire.Joint(2,Float.NaN,0,0,0,0,0,1));
        assertThrows(IllegalArgumentException.class,()->frame(List.of(),Collections.nCopies(129,0f)));
        assertThrows(IllegalArgumentException.class,()->frame(List.of(),List.of(Float.NaN)));
    }
    @Test void everyPacketIsIndependentSparseRestKeyframeAndScaleIsExplicitlyUnsupported(){
        var r=new RigDescriptor(hash,List.of(new RigDescriptor.Joint("root",-1,JointTransform.IDENTITY)));
        var moved=new JointTransform(.1f,0,0,0,0,0,1,1,1,1);
        var f=PoseWire.fromPose(world,npc,12,2,20,"minecraft:overworld",r,List.of(moved),List.of(),0,0,0,0,PoseWire.State.NEUTRAL);
        assertEquals(1,f.joints().size());var neutral=PoseWire.fromPose(world,npc,12,3,22,"minecraft:overworld",r,r.rest(),List.of(),0,0,0,0,PoseWire.State.NEUTRAL);
        assertTrue(PoseWire.decode(PoseWire.encode(neutral)).joints().isEmpty());
        assertThrows(IllegalArgumentException.class,()->PoseWire.fromPose(world,npc,12,4,24,"minecraft:overworld",r,List.of(new JointTransform(0,0,0,0,0,0,1,2,2,2)),List.of(),0,0,0,0,PoseWire.State.NEUTRAL));
    }
    @Test void maximumPayloadAndNearUnitQuaternionAreEncodable(){
        var joints=new ArrayList<PoseWire.Joint>();for(int i=0;i<PoseWire.MAX_JOINTS;i++)joints.add(new PoseWire.Joint(i,0,0,0,0,0,0,1.0002f));
        var bytes=PoseWire.encode(frame(joints,Collections.nCopies(128,.5f)));assertTrue(bytes.length<PoseWire.MAX_BYTES);
        assertEquals(512,PoseWire.decode(bytes).joints().size());
    }
}
