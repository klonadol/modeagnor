package dev.agnor.motion;

import dev.agnor.network.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PoseBufferTest {
    final UUID world=UUID.randomUUID(),npc=UUID.randomUUID();final String hash="b".repeat(64);
    final RigDescriptor rig=new RigDescriptor(hash,List.of(new RigDescriptor.Joint("root",-1,JointTransform.IDENTITY)));
    PoseWire.Frame frame(long seq,String rigHash,UUID id,float x,float yaw,List<PoseWire.Joint> joints){return new PoseWire.Frame(1,world,id,12,seq,seq*2,"minecraft:overworld",rigHash,x,0,0,yaw,PoseWire.State.NEUTRAL,joints,List.of(x/10));}
    @Test void sparseKeyframesRecoverAfterPacketLossAndRestIsNotPreviousPose(){
        var b=new PoseBuffer(rig,1);var j=new PoseWire.Joint(0,1,0,0,0,0,0,1);
        assertEquals(PoseBuffer.Acceptance.APPLIED,b.accept(frame(1,hash,npc,0,0,List.of(j)),0));
        b.accept(frame(3,hash,npc,0,0,List.of()),200_000_000);
        assertEquals(rig.rest(),b.sample(300_000_000).orElseThrow().pose());
        assertEquals(PoseBuffer.Acceptance.STALE,b.accept(frame(2,hash,npc,0,0,List.of()),300_000_000));
    }
    @Test void interpolationUsesSameClockForRootPoseMorphAndShortestYaw(){
        var b=new PoseBuffer(rig,1);b.accept(frame(1,hash,npc,0,3.1f,List.of()),0);
        b.accept(frame(2,hash,npc,2,-3.1f,List.of(new PoseWire.Joint(0,2,0,0,0,0,0,1))),100_000_000);
        var s=b.sample(150_000_000).orElseThrow();assertEquals(1,s.x(),1e-6);assertEquals(1,s.pose().getFirst().x(),1e-6);assertEquals(.1,s.morphs().getFirst(),1e-6);assertEquals(Math.PI,s.yaw(),1e-5);
        assertTrue(b.sample(900_000_000).isEmpty());
    }
    @Test void wrongRigIdentityChannelsAndReconnectAreExplicit(){
        var b=new PoseBuffer(rig,1);b.accept(frame(10,hash,npc,0,0,List.of()),0);
        assertEquals(PoseBuffer.Acceptance.RIG_MISMATCH,b.accept(frame(11,"c".repeat(64),npc,0,0,List.of()),10));
        assertEquals(PoseBuffer.Acceptance.IDENTITY_MISMATCH,b.accept(frame(11,hash,UUID.randomUUID(),0,0,List.of()),10));
        assertEquals(PoseBuffer.Acceptance.INVALID_CHANNELS,b.accept(frame(11,hash,npc,0,0,List.of(new PoseWire.Joint(4,0,0,0,0,0,0,1))),10));
        b.clear();assertTrue(b.sample(20).isEmpty());assertEquals(PoseBuffer.Acceptance.APPLIED,b.accept(frame(1,hash,npc,0,0,List.of()),30));
    }
    @Test void teleportSnapsInsteadOfInterpolatingThroughTheWorld(){
        var b=new PoseBuffer(rig,1);b.accept(frame(1,hash,npc,0,0,List.of()),0);b.accept(frame(2,hash,npc,8,0,List.of()),100_000_000);
        assertEquals(8,b.sample(110_000_000).orElseThrow().x());
    }
    @Test void invalidatedPresentationRetainsAntiReplayGuardUntilReconnect(){
        var b=new PoseBuffer(rig,1);b.accept(frame(10,hash,npc,0,0,List.of()),0);b.invalidate();assertTrue(b.sample(1).isEmpty());
        assertEquals(PoseBuffer.Acceptance.STALE,b.accept(frame(9,"c".repeat(64),npc,0,0,List.of()),5));
        assertEquals(PoseBuffer.Acceptance.STALE,b.accept(frame(9,hash,npc,0,0,List.of()),10));assertEquals(PoseBuffer.Acceptance.APPLIED,b.accept(frame(11,hash,npc,0,0,List.of()),20));
        b.clear();assertEquals(PoseBuffer.Acceptance.APPLIED,b.accept(frame(1,hash,npc,0,0,List.of()),30));
    }
}
