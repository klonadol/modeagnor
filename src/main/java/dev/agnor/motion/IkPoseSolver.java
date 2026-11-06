package dev.agnor.motion;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** Applies a two-bone solution to local rotations; preserves local offsets/scales. */
public final class IkPoseSolver {
    public record Result(List<JointTransform> pose,TwoBoneIk.Solution solution,float actualError) {
        public Result {pose=List.copyOf(pose);}
    }
    public static Result reach(RigDescriptor rig,List<JointTransform> pose,int root,int mid,int end,
                               TwoBoneIk.Point target,TwoBoneIk.Point pole,TwoBoneIk.BendLimit limit){
        if(root<0||mid<0||end<0||root>=rig.size()||mid>=rig.size()||end>=rig.size()||rig.joint(mid).parent()!=root||rig.joint(end).parent()!=mid)
            throw new IllegalArgumentException("IK requires a direct two-bone chain");
        var globals=rig.globals(pose);
        for(int i:new int[]{root,mid,end}){
            var s=globals[i].getScale(new Vector3f());
            float tolerance=Math.max(s.x,Math.max(s.y,s.z))*1e-4f;
            if(Math.abs(s.x-s.y)>tolerance||Math.abs(s.x-s.z)>tolerance)throw new IllegalArgumentException("IK requires uniform chain scale");
        }
        var a=globals[root].getTranslation(new Vector3f());var b=globals[mid].getTranslation(new Vector3f());var c=globals[end].getTranslation(new Vector3f());
        var solution=TwoBoneIk.solve(TwoBoneIk.Point.of(a),target,pole,a.distance(b),b.distance(c),limit);
        var result=new ArrayList<>(pose);
        aim(rig,result,root,b.sub(a,new Vector3f()),solution.joint().vector().sub(a),globals);
        globals=rig.globals(result);b=globals[mid].getTranslation(new Vector3f());c=globals[end].getTranslation(new Vector3f());
        aim(rig,result,mid,c.sub(b,new Vector3f()),solution.end().vector().sub(b),globals);
        float error=rig.globals(result)[end].getTranslation(new Vector3f()).distance(target.vector());
        return new Result(result,solution,error);
    }
    private static void aim(RigDescriptor rig,List<JointTransform> pose,int joint,Vector3f from,Vector3f to,Matrix4f[] globals){
        if(from.lengthSquared()<1e-12f||to.lengthSquared()<1e-12f)throw new IllegalArgumentException("Degenerate IK chain");
        var worldDelta=new Quaternionf().rotationTo(from.normalize(),to.normalize());
        int parent=rig.joint(joint).parent();var parentRotation=parent<0?new Quaternionf():globals[parent].getUnnormalizedRotation(new Quaternionf()).normalize();
        var localDelta=new Quaternionf(parentRotation).invert().mul(worldDelta).mul(parentRotation);
        var old=pose.get(joint);var q=localDelta.mul(old.rotation()).normalize();
        pose.set(joint,new JointTransform(old.x(),old.y(),old.z(),q.x,q.y,q.z,q.w,old.sx(),old.sy(),old.sz()));
    }
}
