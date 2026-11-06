package dev.agnor.motion;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Immutable local TRS. Quaternion components use glTF/JOML XYZW order. */
public record JointTransform(float x,float y,float z,float qx,float qy,float qz,float qw,float sx,float sy,float sz) {
    public static final JointTransform IDENTITY=new JointTransform(0,0,0,0,0,0,1,1,1,1);
    public JointTransform {
        for(float v:new float[]{x,y,z,qx,qy,qz,qw,sx,sy,sz})if(!Float.isFinite(v))throw new IllegalArgumentException("Non-finite TRS");
        float n=qx*qx+qy*qy+qz*qz+qw*qw;
        if(Math.abs(n-1)>0.001f || sx<=0 || sy<=0 || sz<=0)throw new IllegalArgumentException("Invalid quaternion/scale");
    }
    public Quaternionf rotation(){return new Quaternionf(qx,qy,qz,qw);}
    public Matrix4f matrix(){return new Matrix4f().translationRotateScale(x,y,z,qx,qy,qz,qw,sx,sy,sz);}
    public static JointTransform fromMatrix(Matrix4fc m){
        var p=m.getTranslation(new Vector3f());var s=m.getScale(new Vector3f());var q=m.getUnnormalizedRotation(new Quaternionf()).normalize();
        var result=new JointTransform(p.x,p.y,p.z,q.x,q.y,q.z,q.w,s.x,s.y,s.z);
        if(!result.matrix().equals(m,0.0001f))throw new IllegalArgumentException("Shear/reflection not supported as TRS");
        return result;
    }
    public static JointTransform mix(JointTransform a,JointTransform b,float w){
        weight(w);var q=a.rotation().slerp(b.rotation(),w).normalize();
        return new JointTransform(lerp(a.x,b.x,w),lerp(a.y,b.y,w),lerp(a.z,b.z,w),q.x,q.y,q.z,q.w,
            lerp(a.sx,b.sx,w),lerp(a.sy,b.sy,w),lerp(a.sz,b.sz,w));
    }
    public static JointTransform additive(JointTransform base,JointTransform sample,JointTransform reference,float w){
        weight(w);
        var delta=reference.rotation().invert().mul(sample.rotation()).normalize();
        var q=base.rotation().mul(new Quaternionf().slerp(delta,w)).normalize();
        return new JointTransform(base.x+(sample.x-reference.x)*w,base.y+(sample.y-reference.y)*w,base.z+(sample.z-reference.z)*w,
            q.x,q.y,q.z,q.w,base.sx*lerp(1,sample.sx/reference.sx,w),base.sy*lerp(1,sample.sy/reference.sy,w),base.sz*lerp(1,sample.sz/reference.sz,w));
    }
    static void weight(float w){if(!Float.isFinite(w)||w<0||w>1)throw new IllegalArgumentException("Weight outside [0,1]");}
    private static float lerp(float a,float b,float w){return a+(b-a)*w;}
}
