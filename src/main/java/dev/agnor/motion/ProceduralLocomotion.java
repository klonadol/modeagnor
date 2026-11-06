package dev.agnor.motion;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** Host-owned flat-ground gait. Root transforms are observations in a stable local simulation origin,
 * never navigation commands. Rebase with reset() before exceeding the +/-1024m coordinate budget. */
public final class ProceduralLocomotion {
    public record Limb(int upper,int lower,int end) {}
    public record Profile(int hips,Limb leftLeg,Limb rightLeg,Limb leftArm,Limb rightArm,float forward) {
        public Profile {if(forward!=1&&forward!=-1)throw new IllegalArgumentException("Forward must be +/-Z");}
    }
    public record Input(float rootX,float rootZ,float yaw,float velocityX,float velocityZ,float groundY) {
        public Input {for(float f:new float[]{rootX,rootZ,yaw,velocityX,velocityZ,groundY})if(!Float.isFinite(f)||Math.abs(f)>1024)throw new IllegalArgumentException("Invalid/local-origin gait input");}
    }
    public record Frame(List<JointTransform> pose,boolean running,float activity,float phase,
                        TwoBoneIk.Point leftContact,TwoBoneIk.Point rightContact,boolean leftStance,boolean rightStance,float maxFootError) {
        public Frame {pose=List.copyOf(pose);}
    }
    private static final class Foot {Vector3f anchor,start,end;boolean swing,ready=true;float progress,duration,yaw,startYaw,endYaw;}
    private final RigDescriptor rig;private final Profile profile;private final Vector3f[] restFeet;private final Quaternionf[] restFootRotations;
    private final Foot[] feet={new Foot(),new Foot()};private final float height;
    private float phase,activity,pelvisSupport,lastX,lastZ,lastYaw;private boolean initialized;
    public ProceduralLocomotion(RigDescriptor rig,Profile profile){
        this.rig=Objects.requireNonNull(rig);this.profile=Objects.requireNonNull(profile);
        if(profile.hips<0||profile.hips>=rig.size())throw new IllegalArgumentException("Missing hips");
        var mapped=new HashSet<Integer>();
        for(var limb:List.of(profile.leftLeg,profile.rightLeg,profile.leftArm,profile.rightArm)){
            if(limb.upper<0||limb.lower<0||limb.end<0||limb.upper>=rig.size()||limb.lower>=rig.size()||limb.end>=rig.size()
                ||rig.joint(limb.lower).parent()!=limb.upper||rig.joint(limb.end).parent()!=limb.lower)throw new IllegalArgumentException("Missing/direct limb mapping");
            for(int n:new int[]{limb.upper,limb.lower,limb.end})if(n==profile.hips||!mapped.add(n))throw new IllegalArgumentException("Overlapping gait limbs");
        }
        var g=rig.globals(rig.rest());restFeet=new Vector3f[]{position(g,profile.leftLeg.end),position(g,profile.rightLeg.end)};
        restFootRotations=new Quaternionf[]{g[profile.leftLeg.end].getUnnormalizedRotation(new Quaternionf()).normalize(),g[profile.rightLeg.end].getUnnormalizedRotation(new Quaternionf()).normalize()};
        height=position(g,profile.hips).y-Math.min(restFeet[0].y,restFeet[1].y);
        if(height<.05f||height>10)throw new IllegalArgumentException("Invalid normalized rig height");
    }
    public void reset(){initialized=false;phase=0;activity=0;pelvisSupport=0;for(var f:feet){f.swing=false;f.ready=true;}}
    public Frame step(Input input,float dt){
        if(!Float.isFinite(dt)||dt<=0||dt>.05f)throw new IllegalArgumentException("Gait requires 0<dt<=.05");
        float speed=(float)Math.hypot(input.velocityX,input.velocityZ);
        if(speed>height*12)throw new IllegalArgumentException("Velocity outside gait budget");
        float turn=initialized?Math.abs(angle(input.yaw-lastYaw))/dt:0;
        if(!initialized||Math.hypot(input.rootX-lastX,input.rootZ-lastZ)>height*2){
            reset();for(int i=0;i<2;i++){feet[i].anchor=footWorld(restFeet[i],input);feet[i].yaw=input.yaw;}
            initialized=true;turn=0;
        }
        float target=Math.min(1,Math.max(speed/(height*.5f),turn*.3f));
        activity+=(target-activity)*(1-(float)Math.exp(-dt*9));
        boolean running=speed>height*2.2f;
        boolean[] replant=new boolean[2];
        for(int i=0;i<2;i++)replant[i]=new Vector3f(feet[i].anchor).sub(footWorld(restFeet[i],input)).length()>height*.015f;
        boolean settling=feet[0].swing||feet[1].swing||(target<.02f&&(replant[0]||replant[1]));
        float frequency=Math.max(speed/(height*(running?1.05f:.65f)),turn*.5f);
        if(target>.02f||settling)phase=(phase+dt*Math.max(.8f,Math.min(3.5f,frequency)))%1;
        float stance=running?.43f:.62f;
        var pose=new ArrayList<>(rig.rest());var hips=pose.get(profile.hips);
        float support=Math.max(activity,settling?.9f:0);
        pelvisSupport+=(support-pelvisSupport)*(1-(float)Math.exp(-dt*12));
        float bob=pelvisSupport*height*((running?.025f:.012f)*(float)Math.cos(phase*Math.PI*4)-(running?.14f:.09f));
        float sway=activity*height*.012f*(float)Math.sin(phase*Math.PI*2);
        // Convert model-space sway/drop through the hips parent basis.
        var g=rig.globals(pose);int hp=rig.joint(profile.hips).parent();var offset=new Vector3f(sway,bob,0);
        if(hp>=0)g[hp].invert(new Matrix4f()).transformDirection(offset);
        pose.set(profile.hips,new JointTransform(hips.x()+offset.x,hips.y()+offset.y,hips.z()+offset.z,hips.qx(),hips.qy(),hips.qz(),hips.qw(),hips.sx(),hips.sy(),hips.sz()));
        var contacts=new Vector3f[2];
        for(int i=0;i<2;i++){
            var foot=feet[i];float p=(phase+i*.5f)%1;if(p<stance&&!foot.swing)foot.ready=true;
            if(!foot.swing&&foot.ready&&p>=stance&&(target>.02f||replant[i])){
                foot.start=new Vector3f(foot.anchor);foot.end=footWorld(restFeet[i],input);
                float lead=(1-stance*.5f)/Math.max(.8f,frequency);
                foot.end.add(input.velocityX*lead,0,input.velocityZ*lead);foot.end.y=input.groundY+restFeet[i].y;
                foot.swing=true;foot.ready=false;foot.progress=0;foot.duration=1-stance;
                foot.startYaw=foot.yaw;foot.endYaw=input.yaw;
            }
            Vector3f contact;
            if(foot.swing){
                if(target<.02f)foot.end=footWorld(restFeet[i],input);
                foot.progress=Math.min(1,foot.progress+dt*Math.max(.8f,Math.min(3.5f,frequency))/foot.duration);
                float t=foot.progress,smooth=t*t*(3-2*t);
                foot.yaw=foot.startYaw+angle(foot.endYaw-foot.startYaw)*smooth;
                contact=new Vector3f(foot.start).lerp(foot.end,smooth);contact.y+=height*(running?.16f:.10f)*(float)Math.sin(t*Math.PI);
                if(t>=1){foot.anchor=new Vector3f(foot.end);foot.swing=false;}
            }else{
                contact=new Vector3f(foot.anchor);
            }
            contacts[i]=model(contact,input);
        }
        // Lower the shared pelvis before either leg is solved, so one IK pass cannot undo the other.
        g=rig.globals(pose);float lower=0;
        for(int i=0;i<2;i++){
            var limb=i==0?profile.leftLeg:profile.rightLeg;var root=position(g,limb.upper);var knee=position(g,limb.lower);var ankle=position(g,limb.end);
            float length=(root.distance(knee)+knee.distance(ankle))*.997f;
            float horizontal=new Vector3f(root).sub(contacts[i]).mul(1,0,1).lengthSquared();
            float allowed=(float)Math.sqrt(Math.max(0,length*length-horizontal));
            lower=Math.max(lower,root.y-contacts[i].y-allowed);
        }
        lower=Math.min(height*.25f,lower);
        if(lower>0){
            var drop=new Vector3f(0,-lower,0);if(hp>=0)g[hp].invert(new Matrix4f()).transformDirection(drop);
            var old=pose.get(profile.hips);pose.set(profile.hips,new JointTransform(old.x()+drop.x,old.y()+drop.y,old.z()+drop.z,old.qx(),old.qy(),old.qz(),old.qw(),old.sx(),old.sy(),old.sz()));
        }
        float error=0;
        for(int i=0;i<2;i++){
            var local=contacts[i];var limb=i==0?profile.leftLeg:profile.rightLeg;
            var pole=new TwoBoneIk.Point(local.x,local.y+height*.4f,local.z+profile.forward*height);
            var before=new Quaternionf().rotationY(feet[i].yaw-input.yaw).mul(restFootRotations[i]).normalize();
            var solved=IkPoseSolver.reach(rig,pose,limb.upper,limb.lower,limb.end,TwoBoneIk.Point.of(local),pole,new TwoBoneIk.BendLimit(.015f,2.6f));
            pose=new ArrayList<>(solved.pose());error=Math.max(error,solved.actualError());
            preserveRotation(rig,pose,limb.end,before);
        }
        relaxArm(pose,profile.leftArm,phase);relaxArm(pose,profile.rightArm,(phase+.5f)%1);
        lastX=input.rootX;lastZ=input.rootZ;lastYaw=input.yaw;
        return new Frame(pose,running,activity,phase,TwoBoneIk.Point.of(feet[0].anchor),TwoBoneIk.Point.of(feet[1].anchor),!feet[0].swing,!feet[1].swing,error);
    }
    private void relaxArm(List<JointTransform> pose,Limb limb,float p){
        var globals=rig.globals(pose);var a=position(globals,limb.upper);var b=position(globals,limb.lower);
        float swing=activity*(float)Math.sin(p*Math.PI*2)*.4f;
        float outward=Math.copySign(.15f,a.x-position(globals,profile.hips).x);
        var direction=new Vector3f(outward,-1,profile.forward*swing).normalize();
        rotateWorld(rig,pose,limb.upper,new Quaternionf().rotationTo(b.sub(a).normalize(),direction));
    }
    static void rotateWorld(RigDescriptor rig,List<JointTransform> pose,int joint,Quaternionf delta){
        var globals=rig.globals(pose);int parent=rig.joint(joint).parent();
        var pq=parent<0?new Quaternionf():globals[parent].getUnnormalizedRotation(new Quaternionf()).normalize();
        var q=new Quaternionf(pq).invert().mul(delta).mul(pq).mul(pose.get(joint).rotation()).normalize();setRotation(pose,joint,q);
    }
    static void preserveRotation(RigDescriptor rig,List<JointTransform> pose,int joint,Quaternionf world){
        int parent=rig.joint(joint).parent();var globals=rig.globals(pose);
        var q=parent<0?new Quaternionf(world):globals[parent].getUnnormalizedRotation(new Quaternionf()).normalize().invert().mul(world).normalize();setRotation(pose,joint,q);
    }
    private static void setRotation(List<JointTransform> pose,int i,Quaternionf q){var t=pose.get(i);pose.set(i,new JointTransform(t.x(),t.y(),t.z(),q.x,q.y,q.z,q.w,t.sx(),t.sy(),t.sz()));}
    private static Vector3f position(Matrix4f[] g,int i){return g[i].getTranslation(new Vector3f());}
    private static Vector3f world(Vector3f v,Input i){return new Quaternionf().rotationY(i.yaw).transform(new Vector3f(v)).add(i.rootX,0,i.rootZ);}
    private static Vector3f footWorld(Vector3f v,Input i){return world(v,i).add(0,i.groundY,0);}
    private static Vector3f model(Vector3f v,Input i){return new Quaternionf().rotationY(-i.yaw).transform(new Vector3f(v).sub(i.rootX,0,i.rootZ));}
    private static float angle(float v){return (float)Math.atan2(Math.sin(v),Math.cos(v));}
}
