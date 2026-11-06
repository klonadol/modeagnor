package dev.agnor.check;

import dev.agnor.motion.*;
import com.google.gson.*;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/** Dedicated-only fixture: no GemRender class referenced by host code. */
public final class P33Host implements ModInitializer {
    public void onInitialize(){
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            if(!server.isDedicated())return;
            try{ bake();server.stop(false); }catch(Exception e){throw new RuntimeException(e);}
        });
    }
    private static float[] floats(JsonArray array){float[] v=new float[array.size()];for(int i=0;i<v.length;i++)v[i]=array.get(i).getAsFloat();return v;}
    static void bake() throws Exception {
        var asset=JsonParser.parseString(Files.readString(Path.of(System.getProperty("agnor.p33.asset")))).getAsJsonObject();
        var nodes=asset.getAsJsonArray("nodes");int[] parents=new int[nodes.size()];Arrays.fill(parents,-1);
        for(int i=0;i<nodes.size();i++)if(nodes.get(i).getAsJsonObject().has("children"))for(var c:nodes.get(i).getAsJsonObject().getAsJsonArray("children"))parents[c.getAsInt()]=i;
        var joints=new ArrayList<RigDescriptor.Joint>();
        for(int i=0;i<nodes.size();i++){
            var n=nodes.get(i).getAsJsonObject();var matrix=new Matrix4f();
            if(n.has("matrix"))matrix.set(floats(n.getAsJsonArray("matrix")));
            else {
                float[] p=n.has("translation")?floats(n.getAsJsonArray("translation")):new float[]{0,0,0};
                float[] q=n.has("rotation")?floats(n.getAsJsonArray("rotation")):new float[]{0,0,0,1};
                float[] s=n.has("scale")?floats(n.getAsJsonArray("scale")):new float[]{1,1,1};
                matrix.translationRotateScale(p[0],p[1],p[2],q[0],q[1],q[2],q[3],s[0],s[1],s[2]);
            }
            joints.add(new RigDescriptor.Joint(n.get("name").getAsString(),parents[i],JointTransform.fromMatrix(matrix)));
        }
        var rig=new RigDescriptor(asset.get("sha256").getAsString(),joints);
        String stage=System.getProperty("agnor.p33.stage","a");
        if(stage.equals("d")){bakeFace(asset,rig);return;}
        int head=mapped(asset,rig,"head","Head_06"), arm=mapped(asset,rig,"leftUpperArm","Left arm_059");
        if(head<0||arm<0)throw new IllegalStateException("Missing fixture rig mapping");
        var headClip=clip(rig,head,0,.65f,0);var armClip=clip(rig,arm,0,0,-.65f);
        var target=headClip.sample(rig,1,false);var armPose=armClip.sample(rig,1,false);
        var masked=PoseMixer.mix(rig,target,List.of(new PoseMixer.Layer(armPose,rig.rest(),Set.of(arm),1,PoseMixer.Mode.OVERRIDE)));
        var additive=PoseMixer.mix(rig,target,List.of(new PoseMixer.Layer(armPose,rig.rest(),Set.of(arm),.5f,PoseMixer.Mode.ADDITIVE)));
        List<List<JointTransform>> stages=List.of(rig.rest(),PoseMixer.mix(rig,rig.rest(),List.of(new PoseMixer.Layer(target,rig.rest(),Set.of(head),.5f,PoseMixer.Mode.OVERRIDE))),masked,additive);
        String[] labels={"Rest","Crossfade .5","Masked arm","Additive .5"};
        var targets=new JsonArray();for(int i=0;i<4;i++)targets.add(JsonNull.INSTANCE);
        if(stage.equals("b")){
            int elbow=mapped(asset,rig,"leftLowerArm","Left elbow_061"),wrist=mapped(asset,rig,"leftHand","Left wrist_063");
            var globals=rig.globals(rig.rest());
            var root=globals[arm].getTranslation(new org.joml.Vector3f());var hand=globals[wrist].getTranslation(new org.joml.Vector3f());
            float forward=asset.getAsJsonObject("vrm").has("humanoid")?-1:1;
            var goal=reachGoal(root,hand,forward);
            float length=root.distance(hand);var pole=new TwoBoneIk.Point(root.x,root.y-length,root.z+forward*length*.3f);
            var reach=IkPoseSolver.reach(rig,rig.rest(),arm,elbow,wrist,goal,pole,new TwoBoneIk.BendLimit(.02f,2.6f));
            var limited=IkPoseSolver.reach(rig,rig.rest(),arm,elbow,wrist,goal,pole,new TwoBoneIk.BendLimit(.3f,.8f));
            var farVector=new org.joml.Vector3f(hand).sub(root).normalize().mul(3).add(root);
            var far=new TwoBoneIk.Point(farVector.x,farVector.y,farVector.z);
            var unreachable=IkPoseSolver.reach(rig,rig.rest(),arm,elbow,wrist,far,pole,new TwoBoneIk.BendLimit(.02f,2.6f));
            if(reach.actualError()>.0001f)throw new IllegalStateException("Real rig reach error="+reach.actualError());
            stages=List.of(rig.rest(),reach.pose(),limited.pose(),unreachable.pose());
            labels=new String[]{"Rest","Reach","Bend limit .8","Unreachable"};
            targets=new JsonArray();targets.add(JsonNull.INSTANCE);
            for(var point:List.of(goal,goal,far)){var xyz=new JsonArray();xyz.add(point.x());xyz.add(point.y());xyz.add(point.z());targets.add(xyz);}
            System.out.println("P33B_IK reachableError="+reach.actualError()+" limitedError="+limited.actualError()+" farError="+unreachable.actualError());
        }
        if(stage.equals("c")){
            var globals=rig.globals(rig.rest());
            int knee=mapped(asset,rig,"leftLowerLeg","Left knee_0121"),ankle=mapped(asset,rig,"leftFoot","Left ankle_0122"),leg=mapped(asset,rig,"leftUpperLeg","Left leg_0120");
            var headPosition=globals[head].getTranslation(new org.joml.Vector3f());
            float forward=asset.getAsJsonObject("vrm").has("humanoid")?-1:1;
            var localForward=globals[head].getUnnormalizedRotation(new Quaternionf()).normalize().invert().transform(new org.joml.Vector3f(0,0,forward));
            var controller=new WholeBodyController(Map.of(
                MotionTask.Intent.REACH_LEFT,new WholeBodyController.Chain(arm,mapped(asset,rig,"leftLowerArm","Left elbow_061"),mapped(asset,rig,"leftHand","Left wrist_063"),new TwoBoneIk.BendLimit(.02f,2.6f)),
                MotionTask.Intent.FOOT_LEFT,new WholeBodyController.Chain(leg,knee,ankle,new TwoBoneIk.BendLimit(.01f,2.4f))),
                new WholeBodyController.Look(head,new TwoBoneIk.Point(localForward.x,localForward.y,localForward.z),.6f));
            var hand=globals[mapped(asset,rig,"leftHand","Left wrist_063")].getTranslation(new org.joml.Vector3f());
            var foot=globals[ankle].getTranslation(new org.joml.Vector3f());
            var footGoal=new TwoBoneIk.Point(foot.x,foot.y+.12f,foot.z);
            var armRoot=globals[arm].getTranslation(new org.joml.Vector3f());var handGoal=reachGoal(armRoot,hand,forward);
            float armLength=armRoot.distance(hand);var elbowPole=new TwoBoneIk.Point(armRoot.x,armRoot.y-armLength,armRoot.z+forward*armLength*.3f);
            controller.submit(new MotionTask(new UUID(0,1),MotionTask.Intent.FOOT_LEFT,100,100,footGoal,new TwoBoneIk.Point(foot.x,foot.y+.3f,foot.z+forward*.7f)));
            controller.submit(new MotionTask(new UUID(0,2),MotionTask.Intent.REACH_LEFT,50,100,handGoal,elbowPole));
            controller.submit(new MotionTask(new UUID(0,3),MotionTask.Intent.LOOK,20,100,new TwoBoneIk.Point(headPosition.x+.6f,headPosition.y+.1f,headPosition.z+forward),new TwoBoneIk.Point(0,1,0)));
            controller.submit(new MotionTask(new UUID(0,4),MotionTask.Intent.REACH_LEFT,10,100,new TwoBoneIk.Point(hand.x+.1f,hand.y-.05f,hand.z),new TwoBoneIk.Point(hand.x,hand.y-.3f,hand.z-.7f)));
            var combined=controller.evaluate(rig,rig.rest(),1);
            if(combined.outcomes().stream().noneMatch(o->o.status()==WholeBodyController.Status.PREEMPTED))throw new IllegalStateException("Missing arbitration outcome");
            controller.cancel(new UUID(0,2));var cancelled=controller.evaluate(rig,rig.rest(),2);var expired=controller.evaluate(rig,rig.rest(),100);
            if(!expired.pose().equals(rig.rest()))throw new IllegalStateException("Expiry did not restore base");
            stages=List.of(rig.rest(),combined.pose(),cancelled.pose(),expired.pose());
            labels=new String[]{"Rest","Foot/look/reach","Cancel + fallback","Expired"};
            targets=new JsonArray();targets.add(JsonNull.INSTANCE);
            for(int i=0;i<2;i++){var xyz=new JsonArray();xyz.add(footGoal.x());xyz.add(footGoal.y());xyz.add(footGoal.z());targets.add(xyz);}targets.add(JsonNull.INSTANCE);
            System.out.println("P33C_BODY_PASS combined="+combined.outcomes()+" cancelled="+cancelled.outcomes()+" expired="+expired.outcomes());
        }
        if(stage.equals("e")||stage.equals("f")){
            var gait=stage.equals("e")?P34Scenes.gait(asset,rig):P34Scenes.secondary(asset,rig);stages=gait.poses();labels=gait.labels();
            targets=new JsonArray();for(int i=0;i<stages.size();i++)targets.add(JsonNull.INSTANCE);
        }
        var output=new JsonObject();output.addProperty("rigRevision",rig.revision());output.addProperty("stage",stage);var frames=new JsonArray();
        var originalJoints=asset.getAsJsonObject("skin").getAsJsonArray("joints");var ibm=asset.getAsJsonArray("inverseBindMatrices");
        for(var pose:stages){
            var global=rig.globals(pose);var parts=new JsonArray();
            for(var partValue:asset.getAsJsonArray("parts")){
                var part=partValue.getAsJsonObject();var ids=part.getAsJsonArray("sourceJointIndices");var palette=new JsonArray();
                for(var id:ids)palette.add(json(global[originalJoints.get(id.getAsInt()).getAsInt()]));
                palette.add(json(global[part.get("sourceMeshNode").getAsInt()]));
                for(var id:ids){int j=id.getAsInt();palette.add(json(new Matrix4f(global[originalJoints.get(j).getAsInt()]).mul(new Matrix4f().set(floats(ibm.get(j).getAsJsonArray())))));}
                if(palette.size()!=part.get("slots").getAsInt())throw new IllegalStateException("Palette mapping size");parts.add(palette);
            }
            frames.add(parts);
        }
        output.add("frames",frames);output.add("targets",targets);
        if(asset.getAsJsonArray("morphTargetNames").size()>0){
            var morphs=new JsonArray();for(int f=0;f<stages.size();f++){var values=new JsonArray();for(int i=0;i<asset.getAsJsonArray("morphTargetNames").size();i++)values.add(0);morphs.add(values);}output.add("morphs",morphs);
            output.addProperty("resourcePrefix","models/face");output.addProperty("viewYaw",Math.PI);output.addProperty("unlit",true);
        }
        var labelJson=new JsonArray();for(String label:labels)labelJson.add(label);output.add("labels",labelJson);
        writeFrames(stage,output);
        System.out.println("P33_HOST_PASS stage="+stage+" rig="+rig.revision()+" stages="+stages.size()+" parts="+asset.getAsJsonArray("parts").size());
    }
    static AnimationClip clip(RigDescriptor rig,int joint,float x,float y,float z){
        var rest=rig.joint(joint).rest();var q=rest.rotation().mul(new Quaternionf().rotationXYZ(x,y,z)).normalize();
        var target=new JointTransform(rest.x(),rest.y(),rest.z(),q.x,q.y,q.z,q.w,rest.sx(),rest.sy(),rest.sz());
        return new AnimationClip(1,List.of(new AnimationClip.Track(joint,AnimationClip.Interpolation.LINEAR,List.of(new AnimationClip.Key(0,rest),new AnimationClip.Key(1,target)))));
    }
    static JsonArray json(Matrix4f m){var result=new JsonArray();for(float v:m.get(new float[16]))result.add(v);return result;}
    static TwoBoneIk.Point reachGoal(org.joml.Vector3f root,org.joml.Vector3f hand,float forward){
        var goal=new org.joml.Vector3f(root).lerp(hand,.65f);float length=root.distance(hand);goal.y+=length*.10f;goal.z+=forward*length*.35f;
        return new TwoBoneIk.Point(goal.x,goal.y,goal.z);
    }
    static void writeFrames(String stage,JsonObject data) throws Exception {
        var target=Path.of(System.getProperty("agnor.p33.root"),"pose-frames-"+stage+".json");
        var temp=target.resolveSibling(target.getFileName()+".tmp");Files.writeString(temp,data.toString());
        Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    static int mapped(JsonObject asset,RigDescriptor rig,String semantic,String fallback){
        var vrm=asset.getAsJsonObject("vrm");
        if(vrm!=null&&vrm.has("humanoid"))for(var value:vrm.getAsJsonObject("humanoid").getAsJsonArray("humanBones")){
            var bone=value.getAsJsonObject();if(bone.get("bone").getAsString().equals(semantic))return bone.get("node").getAsInt();
        }
        return rig.slot(fallback);
    }
    static void bakeFace(JsonObject asset,RigDescriptor rig) throws Exception {
        var eyes=new ArrayList<FaceController.Eye>();var rest=rig.globals(rig.rest());
        for(String name:List.of("Eye_L","Eye_R","LeftEye","RightEye")){
            int slot=rig.slot(name);if(slot<0)throw new IllegalStateException("Missing eye "+name);
            var axis=rest[slot].getUnnormalizedRotation(new Quaternionf()).normalize().invert().transform(new org.joml.Vector3f(0,0,-1));
            eyes.add(new FaceController.Eye(slot,new TwoBoneIk.Point(axis.x,axis.y,axis.z),.22f));
        }
        var controller=new FaceController(70,13,14,33,Map.of(
            FaceController.Expression.SMILE,List.of(new FaceController.Binding(6,1,FaceController.Domain.BROWS),new FaceController.Binding(11,.8f,FaceController.Domain.EYES),new FaceController.Binding(56,1,FaceController.Domain.MOUTH_CORNER)),
            FaceController.Expression.SURPRISE,List.of(new FaceController.Binding(8,1,FaceController.Domain.BROWS),new FaceController.Binding(29,1,FaceController.Domain.EYES))),eyes);
        var head=rest[rig.slot("Head")].getTranslation(new org.joml.Vector3f());
        var neutral=new FaceController.Input(FaceController.Expression.NEUTRAL,0,0,0,0,null);
        var inputs=List.of(neutral,new FaceController.Input(FaceController.Expression.NEUTRAL,0,1,1,0,null),
            new FaceController.Input(FaceController.Expression.SMILE,1,0,0,0,null),new FaceController.Input(FaceController.Expression.SMILE,1,0,0,.8f,null),
            new FaceController.Input(FaceController.Expression.SURPRISE,1,0,0,1,null),
            new FaceController.Input(FaceController.Expression.NEUTRAL,0,0,0,0,new TwoBoneIk.Point(head.x-.8f,head.y,head.z-2)),
            new FaceController.Input(FaceController.Expression.NEUTRAL,0,0,0,0,new TwoBoneIk.Point(head.x+.8f,head.y,head.z-2)),neutral);
        var frames=new JsonArray();var weights=new JsonArray();
        var originalJoints=asset.getAsJsonObject("skin").getAsJsonArray("joints");var ibm=asset.getAsJsonArray("inverseBindMatrices");
        for(var input:inputs){
            var result=controller.evaluate(rig,rig.rest(),input);if(!result.missing().isEmpty())throw new IllegalStateException("Missing face channels "+result.missing());
            var global=rig.globals(result.pose());var parts=new JsonArray();
            for(var partValue:asset.getAsJsonArray("parts")){
                var part=partValue.getAsJsonObject();var ids=part.getAsJsonArray("sourceJointIndices");var palette=new JsonArray();
                for(var id:ids)palette.add(json(global[originalJoints.get(id.getAsInt()).getAsInt()]));
                palette.add(json(global[part.get("sourceMeshNode").getAsInt()]));
                for(var id:ids){int j=id.getAsInt();palette.add(json(new Matrix4f(global[originalJoints.get(j).getAsInt()]).mul(new Matrix4f().set(floats(ibm.get(j).getAsJsonArray())))));}
                parts.add(palette);
            }
            frames.add(parts);var values=new JsonArray();for(float w:result.weights())values.add(w);weights.add(values);
        }
        if(!weights.get(0).equals(weights.get(7)))throw new IllegalStateException("Neutral face reset mismatch");
        var data=new JsonObject();data.addProperty("rigRevision",rig.revision());data.addProperty("stage","d");data.addProperty("resourcePrefix","models/face");
        data.add("frames",frames);data.add("morphs",weights);
        var labels=new JsonArray();for(String label:List.of("Neutral","Blink","Smile","Smile + mouth","Surprise","Look left","Look right","Neutral reset"))labels.add(label);data.add("labels",labels);
        var targets=new JsonArray();for(int i=0;i<8;i++)targets.add(JsonNull.INSTANCE);data.add("targets",targets);
        var center=new JsonArray();center.add(head.x);center.add(head.y+.08f);center.add(head.z);data.add("faceCenter",center);
        writeFrames("d",data);
        System.out.println("P33D_HOST_PASS eyes=4 morphs=70 frames=8 parts="+asset.getAsJsonArray("parts").size());
    }
}
