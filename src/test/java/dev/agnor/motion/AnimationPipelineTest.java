package dev.agnor.motion;
import org.junit.jupiter.api.Test;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import static org.junit.jupiter.api.Assertions.*;

class AnimationPipelineTest {
    final RigDescriptor rig=new RigDescriptor("test-v1",List.of(
        new RigDescriptor.Joint("root",-1,JointTransform.IDENTITY),
        new RigDescriptor.Joint("arm",0,new JointTransform(0,1,0,0,0,0,1,1,1,1))));
    JointTransform moved(float x,float angle){var q=new Quaternionf().rotateZ(angle);return new JointTransform(x,1,0,q.x,q.y,q.z,q.w,1,1,1);}
    AnimationClip clip(){return new AnimationClip(2,List.of(new AnimationClip.Track(1,AnimationClip.Interpolation.LINEAR,List.of(
        new AnimationClip.Key(0,moved(0,0)),new AnimationClip.Key(2,moved(2,(float)Math.PI/2))))));}
    @Test void sampleClampLoopAndShortestQuaternion(){
        assertEquals(1,clip().sample(rig,1,false).get(1).x(),1e-6);
        assertEquals(2,clip().sample(rig,20,false).get(1).x(),1e-6);
        assertEquals(0,clip().sample(rig,2,true).get(1).x(),1e-6);
        var negative=new JointTransform(0,0,0,0,0,0,-1,1,1,1);
        assertEquals(1,Math.abs(JointTransform.mix(JointTransform.IDENTITY,negative,.5f).qw()),1e-6);
    }
    @Test void maskedCrossfadeDoesNotLeakOrMutate(){
        var sample=clip().sample(rig,2,false);var original=rig.rest();
        var mixed=PoseMixer.mix(rig,original,List.of(new PoseMixer.Layer(sample,original,Set.of(1),.5f,PoseMixer.Mode.OVERRIDE)));
        assertEquals(original.getFirst(),mixed.getFirst());assertEquals(1,mixed.get(1).x(),1e-6);
        assertEquals(0,original.get(1).x());assertThrows(UnsupportedOperationException.class,()->mixed.clear());
    }
    @Test void additiveIsRelativeToReferenceAndIndependentOfBase(){
        var reference=moved(5,0);var sample=moved(7,(float)Math.PI/2);var base=moved(10,0);
        var result=JointTransform.additive(base,sample,reference,.5f);
        assertEquals(11,result.x(),1e-6);
        var actual=result.rotation().transform(new Vector3f(1,0,0));
        assertEquals(Math.sqrt(.5),actual.x,1e-5);assertEquals(Math.sqrt(.5),actual.y,1e-5);
        assertEquals(base,JointTransform.additive(base,sample,reference,0));
    }
    @Test void hierarchyUsesParentOrderAndInverseBindRestCancels(){
        var unordered=new RigDescriptor("unordered",List.of(new RigDescriptor.Joint("child",1,moved(0,0)),new RigDescriptor.Joint("parent",-1,JointTransform.IDENTITY)));
        assertEquals(1,unordered.globals(unordered.rest())[0].m31(),1e-6);
        var globals=rig.globals(rig.rest());assertTrue(new Matrix4f(globals[1]).mul(new Matrix4f(globals[1]).invert()).equals(new Matrix4f(),1e-5f));
    }
    @Test void invalidInputFailsAtBoundary(){
        assertThrows(IllegalArgumentException.class,()->clip().sample(rig,Float.NaN,false));
        assertThrows(IllegalArgumentException.class,()->JointTransform.mix(moved(0,0),moved(0,0),-1));
        assertThrows(IllegalArgumentException.class,()->new RigDescriptor("cycle",List.of(new RigDescriptor.Joint("a",1,JointTransform.IDENTITY),new RigDescriptor.Joint("b",0,JointTransform.IDENTITY))));
        assertThrows(IllegalArgumentException.class,()->new AnimationClip.Track(0,AnimationClip.Interpolation.LINEAR,List.of(new AnimationClip.Key(1,moved(0,0)),new AnimationClip.Key(1,moved(0,0)))));
        assertThrows(IllegalArgumentException.class,()->JointTransform.fromMatrix(new Matrix4f().m10(.5f)));
    }
}
