package dev.agnor.motion;

import com.google.gson.*;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Local developer rig metadata adapter for the accepted model. Does not load/distribute model assets.
 * Content-pack discovery/installation/schema/security policy remains P4. */
public final class MotionRig {
    public record Part(List<Integer> skinIndices,int meshNode,int slots){public Part{skinIndices=List.copyOf(skinIndices);}}
    private final RigDescriptor rig;private final List<Part> parts;private final List<Integer> skin;private final List<Matrix4f> inverseBind;
    private final Map<String,Integer> humanoid;private final List<String> morphNames;private final String prefix;
    private List<JointTransform> neutral;
    private MotionRig(RigDescriptor rig,List<Part> parts,List<Integer> skin,List<Matrix4f> inverseBind,Map<String,Integer> humanoid,List<String> morphNames,String prefix){
        this.rig=rig;this.parts=List.copyOf(parts);this.skin=List.copyOf(skin);this.inverseBind=inverseBind.stream().map(Matrix4f::new).toList();this.humanoid=Map.copyOf(humanoid);this.morphNames=List.copyOf(morphNames);this.prefix=prefix;
    }
    public static MotionRig configured()throws IOException{
        String path=System.getProperty("agnor.motionAsset","");return path.isBlank()?null:read(Path.of(path),System.getProperty("agnor.motionPrefix","models/face"));
    }
    public static MotionRig read(Path path,String prefix)throws IOException{
        if(!prefix.matches("[a-z0-9_./-]+")||prefix.startsWith("/")||prefix.contains("//")||prefix.contains(".."))throw new IllegalArgumentException("Rig resource prefix");
        long size=Files.size(path);if(size<=0||size>4_194_304)throw new IllegalArgumentException("Rig metadata size");
        return read(Files.readAllBytes(path),prefix);
    }
    public static MotionRig read(byte[] bytes,String prefix)throws IOException{
        if(bytes.length==0||bytes.length>4_194_304||!prefix.matches("[a-z0-9_./-]+")||prefix.startsWith("/")||prefix.contains("//")||prefix.contains(".."))throw new IllegalArgumentException("Rig metadata/prefix");
        var asset=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();String revision=asset.get("sha256").getAsString();if(!revision.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Rig hash");
        var nodes=asset.getAsJsonArray("nodes");if(nodes.isEmpty()||nodes.size()>4096)throw new IllegalArgumentException("Rig nodes");
        int[] parents=new int[nodes.size()];Arrays.fill(parents,-1);
        for(int i=0;i<nodes.size();i++)if(nodes.get(i).getAsJsonObject().has("children"))for(var c:nodes.get(i).getAsJsonObject().getAsJsonArray("children")){
            int child=c.getAsInt();if(child<0||child>=nodes.size()||parents[child]!=-1)throw new IllegalArgumentException("Rig child/parent bounds");parents[child]=i;
        }
        var joints=new ArrayList<RigDescriptor.Joint>();
        for(int i=0;i<nodes.size();i++){
            var n=nodes.get(i).getAsJsonObject();Matrix4f matrix;
            if(n.has("matrix"))matrix=matrix(n.getAsJsonArray("matrix"));
            else {
                var p=n.has("translation")?floats(n.getAsJsonArray("translation"),3):new float[]{0,0,0};var q=n.has("rotation")?floats(n.getAsJsonArray("rotation"),4):new float[]{0,0,0,1};
                var s=n.has("scale")?floats(n.getAsJsonArray("scale"),3):new float[]{1,1,1};matrix=new Matrix4f().translationRotateScale(p[0],p[1],p[2],q[0],q[1],q[2],q[3],s[0],s[1],s[2]);
            }
            joints.add(new RigDescriptor.Joint(n.get("name").getAsString(),parents[i],JointTransform.fromMatrix(matrix)));
        }
        var rig=new RigDescriptor(revision,joints);var skin=new ArrayList<Integer>();var seen=new HashSet<Integer>();
        for(var n:asset.getAsJsonObject("skin").getAsJsonArray("joints")){int id=n.getAsInt();if(id<0||id>=rig.size()||!seen.add(id))throw new IllegalArgumentException("Rig skin mapping");skin.add(id);}
        if(skin.isEmpty())throw new IllegalArgumentException("Empty skin");var inverse=new ArrayList<Matrix4f>();for(var m:asset.getAsJsonArray("inverseBindMatrices"))inverse.add(matrix(m.getAsJsonArray()));
        if(inverse.size()!=skin.size())throw new IllegalArgumentException("Inverse bind count");var parts=new ArrayList<Part>();
        for(var value:asset.getAsJsonArray("parts")){
            var p=value.getAsJsonObject();int mesh=p.get("sourceMeshNode").getAsInt(),slots=p.get("slots").getAsInt();var ids=new ArrayList<Integer>();var unique=new HashSet<Integer>();
            for(var id:p.getAsJsonArray("sourceJointIndices")){int index=id.getAsInt();if(index<0||index>=skin.size()||!unique.add(index))throw new IllegalArgumentException("Part skin mapping");ids.add(index);}
            if(mesh<0||mesh>=rig.size()||slots!=ids.size()*2+1||slots>256||ids.isEmpty())throw new IllegalArgumentException("Part palette bounds");
            if(!p.get("file").getAsString().equals("part-%03d.glb".formatted(parts.size())))throw new IllegalArgumentException("Part resource order");parts.add(new Part(ids,mesh,slots));
        }
        if(parts.isEmpty()||parts.size()>64)throw new IllegalArgumentException("Part count");var humanoid=new HashMap<String,Integer>();
        if(asset.has("vrm")&&asset.get("vrm").isJsonObject()&&asset.getAsJsonObject("vrm").has("humanoid"))for(var b:asset.getAsJsonObject("vrm").getAsJsonObject("humanoid").getAsJsonArray("humanBones")){
            var bone=b.getAsJsonObject();int id=bone.get("node").getAsInt();if(id<0||id>=rig.size()||humanoid.put(bone.get("bone").getAsString(),id)!=null)throw new IllegalArgumentException("Humanoid mapping");
        }
        var names=new ArrayList<String>();for(var n:asset.getAsJsonArray("morphTargetNames"))names.add(n.getAsString());if(names.size()>128)throw new IllegalArgumentException("Morph budget");
        for(var p:asset.getAsJsonArray("parts"))if(p.getAsJsonObject().get("morphCount").getAsInt()!=names.size())throw new IllegalArgumentException("Per-part morph layout mismatch");
        return new MotionRig(rig,parts,skin,inverse,humanoid,names,prefix);
    }
    private static float[] floats(JsonArray a,int count){if(a.size()!=count)throw new IllegalArgumentException("Rig vector length");float[] values=new float[count];for(int i=0;i<count;i++){values[i]=a.get(i).getAsFloat();if(!Float.isFinite(values[i]))throw new IllegalArgumentException("Non-finite rig metadata");}return values;}
    private static Matrix4f matrix(JsonArray a){var m=new Matrix4f().set(floats(a,16));if(Math.abs(m.m03())>1e-5||Math.abs(m.m13())>1e-5||Math.abs(m.m23())>1e-5||Math.abs(m.m33()-1)>1e-5||Math.abs(m.determinant())<1e-8)throw new IllegalArgumentException("Invalid affine rig matrix");return m;}
    public RigDescriptor rig(){return rig;}public List<Part> parts(){return parts;}public int morphCount(){return morphNames.size();}public String prefix(){return prefix;}
    /** Static load-time fallback preset, never a client gameplay simulation loop. */
    public List<JointTransform> neutral(){if(neutral==null){try{neutral=locomotion().step(new ProceduralLocomotion.Input(0,0,0,0,0,0),.05f).pose();}catch(IllegalArgumentException unsupported){neutral=rig.rest();}}return neutral;}
    public int bone(String semantic){return humanoid.getOrDefault(semantic,-1);}
    private int require(String semantic){int i=bone(semantic);if(i<0)throw new IllegalArgumentException("Missing humanoid "+semantic);return i;}
    private ProceduralLocomotion.Limb limb(String side,String type,String end){return new ProceduralLocomotion.Limb(require(side+"Upper"+type),require(side+"Lower"+type),require(side+end));}
    public ProceduralLocomotion locomotion(){return new ProceduralLocomotion(rig,new ProceduralLocomotion.Profile(require("hips"),limb("left","Leg","Foot"),limb("right","Leg","Foot"),limb("left","Arm","Hand"),limb("right","Arm","Hand"),-1));}
    public SecondaryPhysics secondary(){
        var chains=new ArrayList<SecondaryPhysics.Chain>();
        for(String name:List.of("Qz_0_0","Qz_0_3","Qz_0_6","Qz_0_9","Qz_0_12","Qz_0_15","Hf_0_1","Hf_0_3","Hf_0_6","Hf_0_8")){
            int node=rig.slot(name);if(node<0)continue;var nodes=new ArrayList<Integer>();nodes.add(node);
            while(nodes.size()<12){int child=-1;for(int i=0;i<rig.size();i++)if(rig.joint(i).parent()==node){if(child!=-1){child=-2;break;}child=i;}if(child<0)break;nodes.add(child);node=child;}
            if(nodes.size()>1){boolean hair=name.startsWith("Hf");chains.add(new SecondaryPhysics.Chain(name,nodes,180,18,hair?.9f:.8f,.008f,new TwoBoneIk.Point(0,-.4f,0),hair?0:.25f));}
        }
        if(chains.isEmpty())return new SecondaryPhysics(rig,List.of(),List.of());
        return new SecondaryPhysics(rig,chains,List.of(new SecondaryPhysics.Sphere(require("head"),new TwoBoneIk.Point(0,.06f,0),.065f),new SecondaryPhysics.Sphere(require("chest"),new TwoBoneIk.Point(0,.02f,0),.10f),new SecondaryPhysics.Sphere(require("leftUpperLeg"),new TwoBoneIk.Point(0,-.16f,0),.045f),new SecondaryPhysics.Sphere(require("rightUpperLeg"),new TwoBoneIk.Point(0,-.16f,0),.045f)));
    }
    public FaceController face(){
        var eyes=new ArrayList<FaceController.Eye>();var rest=rig.globals(rig.rest());
        for(String name:List.of("Eye_L","Eye_R","LeftEye","RightEye")){
            int slot=rig.slot(name);if(slot<0)continue;var axis=rest[slot].getUnnormalizedRotation(new Quaternionf()).normalize().invert().transform(new Vector3f(0,0,-1));eyes.add(new FaceController.Eye(slot,TwoBoneIk.Point.of(axis),.22f));
        }
        var smile=new ArrayList<FaceController.Binding>();binding(smile,"にこり",1,FaceController.Domain.BROWS);binding(smile,"笑い",.8f,FaceController.Domain.EYES);binding(smile,"口角上げ",1,FaceController.Domain.MOUTH_CORNER);
        return new FaceController(morphCount(),morphNames.indexOf("ウィンク"),morphNames.indexOf("ウィンク右"),morphNames.indexOf("あ"),smile.isEmpty()?Map.of():Map.of(FaceController.Expression.SMILE,smile),eyes);
    }
    private void binding(List<FaceController.Binding> out,String name,float gain,FaceController.Domain domain){int index=morphNames.indexOf(name);if(index>=0)out.add(new FaceController.Binding(index,gain,domain));}
    public Matrix4f[][] palettes(List<JointTransform> pose){
        var globals=rig.globals(pose);var result=new Matrix4f[parts.size()][];
        for(int p=0;p<parts.size();p++){
            var part=parts.get(p);var palette=new Matrix4f[part.slots];int cursor=0;
            for(int id:part.skinIndices)palette[cursor++]=new Matrix4f(globals[skin.get(id)]);
            palette[cursor++]=new Matrix4f(globals[part.meshNode]);for(int id:part.skinIndices)palette[cursor++]=new Matrix4f(globals[skin.get(id)]).mul(inverseBind.get(id));result[p]=palette;
        }return result;
    }
}
