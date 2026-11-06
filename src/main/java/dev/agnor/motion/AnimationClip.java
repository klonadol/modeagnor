package dev.agnor.motion;

import java.util.*;

/** Immutable local-transform tracks; STEP/LINEAR, no IO or renderer dependency. */
public final class AnimationClip {
    public enum Interpolation { STEP, LINEAR }
    public record Key(float seconds,JointTransform value){
        public Key { if(!Float.isFinite(seconds)||seconds<0||value==null)throw new IllegalArgumentException("Invalid key"); }
    }
    public record Track(int joint,Interpolation interpolation,List<Key> keys){
        public Track {
            keys=List.copyOf(keys);if(joint<0||interpolation==null||keys.isEmpty())throw new IllegalArgumentException("Invalid track");
            for(int i=1;i<keys.size();i++)if(keys.get(i).seconds()<=keys.get(i-1).seconds())throw new IllegalArgumentException("Keys not strictly increasing");
        }
        JointTransform sample(float t){
            if(t<=keys.getFirst().seconds())return keys.getFirst().value();
            for(int i=1;i<keys.size();i++)if(t<keys.get(i).seconds()){
                var a=keys.get(i-1);var b=keys.get(i);
                return interpolation==Interpolation.STEP?a.value():JointTransform.mix(a.value(),b.value(),(t-a.seconds())/(b.seconds()-a.seconds()));
            }
            return keys.getLast().value();
        }
    }
    private final float duration;private final List<Track> tracks;
    public AnimationClip(float duration,List<Track> tracks){
        if(!Float.isFinite(duration)||duration<=0)throw new IllegalArgumentException("Duration");
        this.duration=duration;this.tracks=List.copyOf(tracks);var seen=new HashSet<Integer>();
        for(var t:tracks)if(!seen.add(t.joint())||t.keys().getLast().seconds()>duration)throw new IllegalArgumentException("Track bounds/duplicate");
    }
    public List<JointTransform> sample(RigDescriptor rig,float seconds,boolean loop){
        if(!Float.isFinite(seconds)||seconds<0)throw new IllegalArgumentException("Clip clock");
        float t=loop?seconds%duration:Math.min(seconds,duration);
        var pose=new ArrayList<>(rig.rest());
        for(var track:tracks){if(track.joint()>=rig.size())throw new IllegalArgumentException("Track rig mismatch");pose.set(track.joint(),track.sample(t));}
        return List.copyOf(pose);
    }
}
