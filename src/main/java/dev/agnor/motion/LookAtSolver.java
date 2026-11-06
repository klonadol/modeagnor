package dev.agnor.motion;

import java.util.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class LookAtSolver {
    public static List<JointTransform> solve(RigDescriptor rig,List<JointTransform> pose,int joint,TwoBoneIk.Point target,
                                             TwoBoneIk.Point localForward,float maxRadians){
        if(joint<0||joint>=rig.size()||!Float.isFinite(maxRadians)||maxRadians<0||maxRadians>(float)Math.PI)throw new IllegalArgumentException("Look bounds");
        var global=rig.globals(pose);var q=global[joint].getUnnormalizedRotation(new Quaternionf()).normalize();
        var from=q.transform(localForward.vector());if(from.lengthSquared()<1e-10f)throw new IllegalArgumentException("Look forward axis");from.normalize();
        var to=target.vector().sub(global[joint].getTranslation(new Vector3f()));
        if(to.lengthSquared()<1e-10f)return List.copyOf(pose);to.normalize();
        float angle=(float)Math.acos(Math.max(-1,Math.min(1,from.dot(to))));
        var delta=new Quaternionf().rotationTo(from,to);
        if(angle>maxRadians)delta=new Quaternionf().slerp(delta,maxRadians/angle);
        int parent=rig.joint(joint).parent();var parentRotation=parent<0?new Quaternionf():global[parent].getUnnormalizedRotation(new Quaternionf()).normalize();
        var old=pose.get(joint);var result=new Quaternionf(parentRotation).invert().mul(delta).mul(parentRotation).mul(old.rotation()).normalize();
        var output=new ArrayList<>(pose);output.set(joint,new JointTransform(old.x(),old.y(),old.z(),result.x,result.y,result.z,result.w,old.sx(),old.sy(),old.sz()));
        return List.copyOf(output);
    }
}
