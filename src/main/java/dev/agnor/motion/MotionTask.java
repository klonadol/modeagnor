package dev.agnor.motion;

import java.util.UUID;

/** Host semantic task. Targets are rig/model space, never gameplay teleport instructions. */
public record MotionTask(UUID id,Intent intent,int priority,long expiresAtTick,TwoBoneIk.Point target,TwoBoneIk.Point pole){
    public enum Intent { LOOK, REACH_LEFT, REACH_RIGHT, FOOT_LEFT, FOOT_RIGHT }
    public MotionTask {
        if(id==null||intent==null||target==null||pole==null||priority < -100||priority>100||expiresAtTick<0)throw new IllegalArgumentException("Motion task bounds");
    }
}
