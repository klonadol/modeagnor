package dev.agnor.motion;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProceduralLocomotionTest {
    static JointTransform at(float x,float y,float z){return new JointTransform(x,y,z,0,0,0,1,1,1,1);}
    static RigDescriptor rig(){return new RigDescriptor("gait",List.of(
        new RigDescriptor.Joint("hips",-1,at(0,.95f,0)),
        new RigDescriptor.Joint("leftLeg",0,at(.12f,0,0)),new RigDescriptor.Joint("leftKnee",1,at(0,-.45f,.01f)),new RigDescriptor.Joint("leftFoot",2,at(0,-.45f,-.01f)),
        new RigDescriptor.Joint("rightLeg",0,at(-.12f,0,0)),new RigDescriptor.Joint("rightKnee",4,at(0,-.45f,.01f)),new RigDescriptor.Joint("rightFoot",5,at(0,-.45f,-.01f)),
        new RigDescriptor.Joint("leftArm",0,at(.25f,.4f,0)),new RigDescriptor.Joint("leftElbow",7,at(.3f,0,0)),new RigDescriptor.Joint("leftHand",8,at(.25f,0,0)),
        new RigDescriptor.Joint("rightArm",0,at(-.25f,.4f,0)),new RigDescriptor.Joint("rightElbow",10,at(-.3f,0,0)),new RigDescriptor.Joint("rightHand",11,at(-.25f,0,0)))) ;}
    static ProceduralLocomotion controller(){return new ProceduralLocomotion(rig(),new ProceduralLocomotion.Profile(0,new ProceduralLocomotion.Limb(1,2,3),new ProceduralLocomotion.Limb(4,5,6),new ProceduralLocomotion.Limb(7,8,9),new ProceduralLocomotion.Limb(10,11,12),-1));}
    @Test void scriptedTravelPlantsFeetAndSettles(){
        var c=controller();float z=0;ProceduralLocomotion.Frame prev=null;boolean run=false,swing=false;
        for(int i=0;i<180;i++){
            float v=i<4||i>100?0:i<50?.7f:2.1f;z-=v*.05f;
            var f=c.step(new ProceduralLocomotion.Input(0,z,0,0,-v,0),.05f);
            if(prev!=null){if(prev.leftStance()&&f.leftStance())assertEquals(prev.leftContact(),f.leftContact());if(prev.rightStance()&&f.rightStance())assertEquals(prev.rightContact(),f.rightContact());}
            assertTrue(f.maxFootError()<.03f,"tick="+i+" error="+f.maxFootError());run|=f.running();swing|=!f.leftStance();prev=f;
        }
        assertTrue(run&&swing);assertTrue(prev.activity()<.001f);assertTrue(prev.leftStance()&&prev.rightStance());
    }
    @Test void resetAndTeleportDiscardOldAnchors(){
        var c=controller();c.step(new ProceduralLocomotion.Input(0,0,0,0,0,0),.05f);
        var teleported=c.step(new ProceduralLocomotion.Input(100,20,0,0,0,0),.05f);
        assertTrue(teleported.leftContact().x()>99);assertTrue(teleported.maxFootError()<.001f);
        c.reset();var reset=c.step(new ProceduralLocomotion.Input(0,0,0,0,0,0),.05f);assertEquals(0,reset.activity());
    }
    @Test void turnInPlaceAndInputBounds(){
        var c=controller();org.joml.Quaternionf previous=null;boolean stance=false;
        for(int i=0;i<100;i++){
            float yaw=i*.02f;var f=c.step(new ProceduralLocomotion.Input(0,0,yaw,0,0,0),.05f);assertTrue(f.maxFootError()<.04f);
            var world=new org.joml.Quaternionf().rotationY(yaw).mul(rig().globals(f.pose())[3].getUnnormalizedRotation(new org.joml.Quaternionf()).normalize());
            if(stance&&f.leftStance())assertTrue(Math.abs(previous.dot(world))>.99999f,"Stance yaw slipped");
            previous=world;stance=f.leftStance();
        }
        assertThrows(IllegalArgumentException.class,()->c.step(new ProceduralLocomotion.Input(0,0,0,0,0,0),1));
        assertThrows(IllegalArgumentException.class,()->new ProceduralLocomotion.Input(Float.NaN,0,0,0,0,0));
        assertThrows(IllegalArgumentException.class,()->c.step(new ProceduralLocomotion.Input(0,0,0,100,0,0),.05f));
    }
    @Test void replayIsDeterministicAndRaisedFloorDoesNotCauseEndlessStepping(){
        var a=controller();var b=controller();ProceduralLocomotion.Frame f=null;
        for(int i=0;i<100;i++){
            var input=new ProceduralLocomotion.Input(0,0,0,0,0,.05f);
            f=a.step(input,.05f);assertEquals(f,b.step(input,.05f));assertTrue(f.maxFootError()<.001f);
        }
        assertTrue(f.leftStance()&&f.rightStance());assertEquals(0,f.activity());
    }
}
