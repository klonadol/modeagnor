package dev.agnor.motion;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.joml.Vector3f;
import static org.junit.jupiter.api.Assertions.*;

class SecondaryPhysicsTest {
    final RigDescriptor rig=new RigDescriptor("secondary",List.of(
        new RigDescriptor.Joint("body",-1,JointTransform.IDENTITY),
        new RigDescriptor.Joint("hair",0,ProceduralLocomotionTest.at(0,1,0)),
        new RigDescriptor.Joint("tip",1,ProceduralLocomotionTest.at(0,-.5f,0)),
        new RigDescriptor.Joint("cloth",0,ProceduralLocomotionTest.at(1,1,0)),
        new RigDescriptor.Joint("hem",3,ProceduralLocomotionTest.at(0,-.5f,0))));
    final SecondaryPhysics.Root root=new SecondaryPhysics.Root(0,0,0,0);
    SecondaryPhysics.Chain chain(String id,int a,int b){return new SecondaryPhysics.Chain(id,List.of(a,b),120,15,.7f,.01f,new TwoBoneIk.Point(.7f,-.3f,0));}
    @Test void fixedStepsPreserveLengthAndConeAndPartitionTime(){
        var a=new SecondaryPhysics(rig,List.of(chain("hair",1,2)),List.of());var b=new SecondaryPhysics(rig,List.of(chain("hair",1,2)),List.of());
        SecondaryPhysics.Frame fa=null,fb=null;
        for(int i=0;i<100;i++)fa=a.step(rig.rest(),root,.05f,false);
        for(int i=0;i<500;i++)fb=b.step(rig.rest(),root,.01f,false);
        var ga=rig.globals(fa.pose());var gb=rig.globals(fb.pose());
        var tip=ga[2].getTranslation(new Vector3f());assertEquals(.5,tip.distance(ga[1].getTranslation(new Vector3f())),1e-6);
        assertTrue(tip.x>0);assertTrue(tip.distance(gb[2].getTranslation(new Vector3f()))<1e-5);
        assertEquals(SecondaryPhysics.Status.ACTIVE,fa.outcomes().getFirst().status());assertEquals(6,fa.substeps());
    }
    @Test void collisionDeflectsTipAndConflictsResetOnlyOneChain(){
        var proxy=new SecondaryPhysics.Sphere(0,new TwoBoneIk.Point(.03f,.5f,0),.10f);
        var physics=new SecondaryPhysics(rig,List.of(chain("hair",1,2)),List.of(proxy));
        var f=physics.step(rig.rest(),root,.05f,false);var tip=rig.globals(f.pose())[2].getTranslation(new Vector3f());
        assertEquals(SecondaryPhysics.Status.ACTIVE,f.outcomes().getFirst().status());assertTrue(f.outcomes().getFirst().contacts()>0);
        assertTrue(tip.distance(proxy.offset().vector())>=.1099f);
        var impossible=new SecondaryPhysics.Sphere(0,new TwoBoneIk.Point(0,1,0),.7f);
        var c=new SecondaryPhysics(rig,List.of(chain("bad",1,2),chain("good",3,4)),List.of(impossible));
        var result=c.step(rig.rest(),root,.05f,false);
        assertEquals(SecondaryPhysics.Status.CONSTRAINT_CONFLICT,result.outcomes().get(0).status());assertEquals(rig.rest().get(1),result.pose().get(1));
        assertEquals(SecondaryPhysics.Status.ACTIVE,result.outcomes().get(1).status());assertNotEquals(rig.rest().get(3),result.pose().get(3));
    }
    @Test void pauseResumeTeleportAndDegenerateChainRecovery(){
        var p=new SecondaryPhysics(rig,List.of(chain("hair",1,2),chain("cloth",3,4)),List.of());
        for(int i=0;i<10;i++)p.step(rig.rest(),root,.05f,false);
        var paused=p.step(rig.rest(),root,.05f,true);assertEquals(rig.rest(),paused.pose());assertEquals(0,paused.substeps());
        var resumed=p.step(rig.rest(),root,.05f,false);var fresh=new SecondaryPhysics(rig,List.of(chain("hair",1,2),chain("cloth",3,4)),List.of()).step(rig.rest(),root,.05f,false);
        assertEquals(fresh.pose(),resumed.pose());
        var teleported=p.step(rig.rest(),new SecondaryPhysics.Root(10,0,0,0),.05f,false);assertEquals(SecondaryPhysics.Status.RESET_TELEPORT,teleported.outcomes().getFirst().status());
        var bad=new ArrayList<>(rig.rest());bad.set(2,JointTransform.IDENTITY);
        var f=p.step(bad,root,.05f,false);assertEquals(SecondaryPhysics.Status.RESET_INVALID,f.outcomes().getFirst().status());assertEquals(SecondaryPhysics.Status.RESET_TELEPORT,f.outcomes().get(1).status());
        assertEquals(SecondaryPhysics.Status.ACTIVE,p.step(rig.rest(),root,.05f,false).outcomes().getFirst().status());
    }
    @Test void mappingAndWorkAreBounded(){
        assertThrows(IllegalArgumentException.class,()->new SecondaryPhysics(rig,List.of(chain("a",1,2),chain("b",1,2)),List.of()));
        assertThrows(IllegalArgumentException.class,()->new SecondaryPhysics(rig,List.of(chain("a",1,2)),List.of(new SecondaryPhysics.Sphere(1,new TwoBoneIk.Point(0,0,0),.1f))));
        var p=new SecondaryPhysics(rig,List.of(chain("a",1,2)),List.of());
        assertThrows(IllegalArgumentException.class,()->p.step(rig.rest(),root,1,false));
        assertThrows(IllegalArgumentException.class,()->new SecondaryPhysics.Root(Float.NaN,0,0,0));
        assertEquals(12,p.step(rig.rest(),root,.1f,false).substeps());
    }
    @Test void artisticInertiaCompensationReducesRootTranslationShock(){
        var zero=new TwoBoneIk.Point(0,0,0);
        var a=new SecondaryPhysics(rig,List.of(new SecondaryPhysics.Chain("hair",List.of(1,2),180,18,.7f,.01f,zero,0)),List.of());
        var b=new SecondaryPhysics(rig,List.of(new SecondaryPhysics.Chain("hair",List.of(1,2),180,18,.7f,.01f,zero,1)),List.of());
        a.step(rig.rest(),root,.05f,false);b.step(rig.rest(),root,.05f,false);
        var moved=new SecondaryPhysics.Root(.1f,0,0,0);
        var compensated=rig.globals(a.step(rig.rest(),moved,.05f,false).pose())[2].getTranslation(new Vector3f());
        var inertial=rig.globals(b.step(rig.rest(),moved,.05f,false).pose())[2].getTranslation(new Vector3f());
        assertTrue(Math.abs(compensated.x)<1e-5);assertTrue(Math.abs(inertial.x)>.01);
    }
    @Test void intersectingProxiesChooseFeasibleCircleIntersectionInsteadOfBouncing(){
        var proxies=List.of(new SecondaryPhysics.Sphere(0,new TwoBoneIk.Point(.03f,.5f,0),.10f),
            new SecondaryPhysics.Sphere(0,new TwoBoneIk.Point(-.03f,.5f,0),.10f),
            new SecondaryPhysics.Sphere(0,new TwoBoneIk.Point(0,.5f,.08f),.10f));
        var p=new SecondaryPhysics(rig,List.of(chain("hair",1,2)),proxies);
        var f=p.step(rig.rest(),root,.05f,false);assertEquals(SecondaryPhysics.Status.ACTIVE,f.outcomes().getFirst().status());
        var tip=rig.globals(f.pose())[2].getTranslation(new Vector3f());
        for(var proxy:proxies)assertTrue(tip.distance(proxy.offset().vector())>=.1099f);
        assertTrue(tip.z<0);assertEquals(.5,tip.distance(new Vector3f(0,1,0)),1e-6);
    }
}
