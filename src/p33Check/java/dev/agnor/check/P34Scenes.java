package dev.agnor.check;

import dev.agnor.motion.*;
import com.google.gson.*;
import java.util.*;
import org.joml.Vector3f;

/** Sequential P3.4 flat-ground scripted-root acceptance, using P3.3 palette transport only. */
final class P34Scenes {
    record Scene(List<List<JointTransform>> poses,String[] labels) {}
    static int bone(JsonObject asset,String semantic){
        for(var b:asset.getAsJsonObject("vrm").getAsJsonObject("humanoid").getAsJsonArray("humanBones")){
            var o=b.getAsJsonObject();if(o.get("bone").getAsString().equals(semantic))return o.get("node").getAsInt();
        }throw new IllegalArgumentException("Missing humanoid "+semantic);
    }
    static ProceduralLocomotion.Limb limb(JsonObject asset,String side,String type,String end){
        return new ProceduralLocomotion.Limb(bone(asset,side+"Upper"+type),bone(asset,side+"Lower"+type),bone(asset,side+end));
    }
    static ProceduralLocomotion.Profile profile(JsonObject asset){return new ProceduralLocomotion.Profile(bone(asset,"hips"),limb(asset,"left","Leg","Foot"),limb(asset,"right","Leg","Foot"),limb(asset,"left","Arm","Hand"),limb(asset,"right","Arm","Hand"),-1);}
    static Scene gait(JsonObject asset,RigDescriptor rig){
        var controller=new ProceduralLocomotion(rig,profile(asset));var poses=new ArrayList<List<JointTransform>>();
        float x=0,z=0,yaw=0,maxError=0,slide=0;ProceduralLocomotion.Frame previous=null;var metrics=new JsonArray();
        int[] capture={0,6,32,67,90,150};
        for(int tick=0;tick<=160;tick++){
            float speed=tick<4?0:tick<45?.7f:tick<80?2.1f:tick<110?.65f:0;
            if(tick>=80&&tick<110)yaw+=.035f;
            float vx=-(float)Math.sin(yaw)*speed,vz=-(float)Math.cos(yaw)*speed;x+=vx*.05f;z+=vz*.05f;
            var input=new ProceduralLocomotion.Input(x,z,yaw,vx,vz,0);var f=controller.step(input,.05f);maxError=Math.max(maxError,f.maxFootError());
            if(previous!=null){
                if(previous.leftStance()&&f.leftStance())slide=Math.max(slide,previous.leftContact().vector().distance(f.leftContact().vector()));
                if(previous.rightStance()&&f.rightStance())slide=Math.max(slide,previous.rightContact().vector().distance(f.rightContact().vector()));
            }
            for(int t:capture)if(tick==t)poses.add(f.pose());
            previous=f;var metric=new JsonObject();metric.addProperty("tick",tick);metric.addProperty("phase",f.phase());metric.addProperty("activity",f.activity());metric.addProperty("running",f.running());metric.addProperty("footError",f.maxFootError());metrics.add(metric);
        }
        try{java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("agnor.p33.root"),"gait-metrics.json"),metrics.toString());}catch(Exception e){throw new RuntimeException(e);}
        if(slide>1e-6f||maxError>.025f||previous.activity()>.001f)throw new IllegalStateException("Gait acceptance error="+maxError+" slide="+slide+" stop="+previous.activity());
        System.out.println("P34_GAIT_PASS ticks=161 maxFootError="+maxError+" stanceSlide="+slide+" stopped="+previous.activity());
        return new Scene(poses,new String[]{"Idle","Start","Walk","Run","Turn","Stopped"});
    }
    static Scene secondary(JsonObject asset,RigDescriptor rig){
        var chains=new ArrayList<SecondaryPhysics.Chain>();
        // Explicit bounded profile: the full 28-chain experiment has incompatible contacts on Qz_0_17.
        // Other VRM chains remain animated/base; this is not a claim of complete cloth collision.
        var roots=List.of("Qz_0_0","Qz_0_3","Qz_0_6","Qz_0_9","Qz_0_12","Qz_0_15","Hf_0_1","Hf_0_3","Hf_0_6","Hf_0_8");
        for(String name:roots){
            int node=rig.slot(name);var nodes=new ArrayList<Integer>();nodes.add(node);
            while(nodes.size()<12){int child=-1;for(int i=0;i<rig.size();i++)if(rig.joint(i).parent()==node){if(child!=-1){child=-2;break;}child=i;}if(child<0)break;nodes.add(child);node=child;}
            boolean hair=name.startsWith("Hf");
            chains.add(new SecondaryPhysics.Chain(name,nodes,180,18,hair?.9f:.8f,.008f,new TwoBoneIk.Point(0,-.4f,0),hair?0:.25f));
        }
        var colliders=List.of(new SecondaryPhysics.Sphere(bone(asset,"head"),new TwoBoneIk.Point(0,.06f,0),.065f),
            new SecondaryPhysics.Sphere(bone(asset,"chest"),new TwoBoneIk.Point(0,.02f,0),.10f),
            new SecondaryPhysics.Sphere(bone(asset,"leftUpperLeg"),new TwoBoneIk.Point(0,-.16f,0),.045f),
            new SecondaryPhysics.Sphere(bone(asset,"rightUpperLeg"),new TwoBoneIk.Point(0,-.16f,0),.045f));
        var physics=new SecondaryPhysics(rig,chains,colliders);var gait=new ProceduralLocomotion(rig,profile(asset));
        var poses=new ArrayList<List<JointTransform>>();var metrics=new JsonArray();int totalContacts=0;float maxPenetration=0,maxAngle=0;double totalMs=0;
        float x=0,z=0,yaw=0;int head=bone(asset,"head");int[] captures={0,32,67,90,150};
        for(int tick=0;tick<=160;tick++){
            float speed=tick<4?0:tick<45?.7f:tick<80?2.1f:tick<110?.65f:0;
            if(tick>=80&&tick<110)yaw+=.035f;
            float vx=-(float)Math.sin(yaw)*speed,vz=-(float)Math.cos(yaw)*speed;x+=vx*.05f;z+=vz*.05f;
            var base=new ArrayList<>(gait.step(new ProceduralLocomotion.Input(x,z,yaw,vx,vz,0),.05f).pose());
            var h=base.get(head);var q=h.rotation().mul(new org.joml.Quaternionf().rotationY(tick<110?(float)Math.sin(tick*.08)*.4f:0)).normalize();
            base.set(head,new JointTransform(h.x(),h.y(),h.z(),q.x,q.y,q.z,q.w,h.sx(),h.sy(),h.sz()));
            long start=System.nanoTime();var f=physics.step(base,new SecondaryPhysics.Root(x,0,z,yaw),.05f,false);double ms=(System.nanoTime()-start)/1e6;totalMs+=ms;
            var metric=new JsonObject();metric.addProperty("tick",tick);metric.addProperty("milliseconds",ms);var outcomes=new JsonArray();
            for(var o:f.outcomes()){
                totalContacts+=o.contacts();maxPenetration=Math.max(maxPenetration,o.maxPenetration());
                if(o.status()!=SecondaryPhysics.Status.ACTIVE)throw new IllegalStateException("Secondary fixture tick="+tick+" "+o);
                outcomes.add(o.toString());
            }
            metric.add("outcomes",outcomes);metrics.add(metric);
            for(var c:chains)for(int j=0;j<c.nodes().size()-1;j++){
                int n=c.nodes().get(j);float dot=Math.abs(base.get(n).rotation().dot(f.pose().get(n).rotation()));
                float angle=2*(float)Math.acos(Math.min(1,dot));maxAngle=Math.max(maxAngle,angle);
                if(angle>c.cone()+.001f)throw new IllegalStateException("Secondary angle exceeded "+n+" angle="+angle);
            }
            for(int t:captures)if(tick==t)poses.add(f.pose());
        }
        var neutral=poses.get(4);
        var paused=physics.step(neutral,new SecondaryPhysics.Root(x,0,z,yaw),.05f,true);if(!paused.pose().equals(neutral))throw new IllegalStateException("Pause failed");
        physics.step(neutral,new SecondaryPhysics.Root(x,0,z,yaw),.05f,false);
        var teleport=physics.step(neutral,new SecondaryPhysics.Root(x+10,0,z,yaw),.05f,false);
        if(teleport.outcomes().stream().anyMatch(o->o.status()!=SecondaryPhysics.Status.RESET_TELEPORT))throw new IllegalStateException("Teleport failed");poses.add(teleport.pose());
        try{java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("agnor.p33.root"),"secondary-metrics.json"),metrics.toString());}catch(Exception e){throw new RuntimeException(e);}
        System.out.println("P34_SECONDARY_PASS chains="+chains.size()+" segments="+chains.stream().mapToInt(c->c.nodes().size()-1).sum()+" contacts="+totalContacts+" penetration="+maxPenetration+" maxAngle="+maxAngle+" meanMs="+totalMs/161);
        return new Scene(poses,new String[]{"Spring idle","Walk + spring","Run + spring","Turn + spring","Settled","Teleport reset"});
    }
}
