package dev.agnor.motion;
import org.junit.jupiter.api.Test;
import org.joml.Vector3f;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class WholeBodyControllerTest {
    final JointTransform offset=new JointTransform(1,0,0,0,0,0,1,1,1,1);
    final RigDescriptor rig=new RigDescriptor("body",List.of(new RigDescriptor.Joint("root",-1,JointTransform.IDENTITY),new RigDescriptor.Joint("mid",0,offset),new RigDescriptor.Joint("hand",1,offset),new RigDescriptor.Joint("head",-1,JointTransform.IDENTITY)));
    TwoBoneIk.Point p(float x,float y,float z){return new TwoBoneIk.Point(x,y,z);}
    WholeBodyController controller(){return new WholeBodyController(Map.of(MotionTask.Intent.REACH_LEFT,new WholeBodyController.Chain(0,1,2,new TwoBoneIk.BendLimit(.01f,3.1f))),new WholeBodyController.Look(3,p(0,0,1),.5f));}
    MotionTask task(long id,int priority,long expiry){return new MotionTask(new UUID(0,id),MotionTask.Intent.REACH_LEFT,priority,expiry,p(1,1,0),p(0,0,1));}
    @Test void priorityExpiryCancelAndTieAreDeterministic(){
        var c=controller();c.submit(task(2,10,100));c.submit(task(1,10,100));c.submit(task(3,99,0));
        var r=c.evaluate(rig,rig.rest(),1);
        assertEquals(List.of(WholeBodyController.Status.EXPIRED,WholeBodyController.Status.APPLIED,WholeBodyController.Status.PREEMPTED),r.outcomes().stream().map(WholeBodyController.Outcome::status).toList());
        assertEquals(new UUID(0,1),r.outcomes().get(1).task());assertTrue(c.cancel(new UUID(0,1)));assertFalse(c.cancel(new UUID(0,1)));
        assertEquals(WholeBodyController.Status.APPLIED,c.evaluate(rig,rig.rest(),2).outcomes().getFirst().status());
    }
    @Test void headAndReachCoexistAndLookConeClamps(){
        var c=controller();c.submit(task(1,10,100));c.submit(new MotionTask(new UUID(0,2),MotionTask.Intent.LOOK,20,100,p(10,0,0),p(0,1,0)));
        var r=c.evaluate(rig,rig.rest(),1);assertEquals(WholeBodyController.Status.CONSTRAINED,r.outcomes().getFirst().status());assertEquals(WholeBodyController.Status.APPLIED,r.outcomes().getLast().status());
        var direction=r.pose().get(3).rotation().transform(new Vector3f(0,0,1));assertEquals(.5,Math.acos(direction.z),1e-5);
        assertTrue(rig.globals(r.pose())[2].getTranslation(new Vector3f()).distance(1,1,0)<1e-5);
    }
    @Test void unsupportedAndBoundedCapacityAreObservable(){
        var c=controller();c.submit(new MotionTask(new UUID(0,1),MotionTask.Intent.FOOT_LEFT,0,10,p(0,0,0),p(0,1,0)));
        assertEquals(WholeBodyController.Status.UNSUPPORTED,c.evaluate(rig,rig.rest(),0).outcomes().getFirst().status());
        for(int i=2;i<=32;i++)c.submit(task(i,0,10));assertThrows(IllegalStateException.class,()->c.submit(task(33,0,10)));
    }
    @Test void badChainProducesFiniteDiagnosticOutcome(){
        var c=new WholeBodyController(Map.of(MotionTask.Intent.REACH_LEFT,new WholeBodyController.Chain(0,2,1,new TwoBoneIk.BendLimit(0,3))),null);
        c.submit(task(1,10,100));var r=c.evaluate(rig,rig.rest(),1);
        assertEquals(WholeBodyController.Status.FAILED,r.outcomes().getFirst().status());
        assertTrue(r.outcomes().getFirst().detail().contains("direct two-bone"));assertEquals(rig.rest(),r.pose());
    }
    @Test void flatFootContactPreservesEndOrientation(){
        var c=new WholeBodyController(Map.of(MotionTask.Intent.FOOT_LEFT,new WholeBodyController.Chain(0,1,2,new TwoBoneIk.BendLimit(.01f,3.1f))),null);
        c.submit(new MotionTask(new UUID(0,1),MotionTask.Intent.FOOT_LEFT,10,100,p(1,1,0),p(0,0,1)));
        var result=c.evaluate(rig,rig.rest(),1);var globals=rig.globals(result.pose());
        assertTrue(globals[2].getTranslation(new Vector3f()).distance(1,1,0)<1e-5);
        assertEquals(1,Math.abs(globals[2].getUnnormalizedRotation(new org.joml.Quaternionf()).normalize().w),1e-5);
    }
}
