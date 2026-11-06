package dev.agnor.network;

import dev.agnor.motion.JointTransform;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** S2C keyframes: sparse absolute local transforms relative to the agreed rig's rest defaults.
 * No packet depends on receiving an earlier delta. Scale always comes from the local rig. */
public final class PoseWire {
    public static final int VERSION=1,MAX_BYTES=16_384,MAX_JOINTS=512,MAX_MORPHS=128;
    public enum State { NEUTRAL, LISTENING, THINKING, SPEAKING }
    public record Joint(int slot,float x,float y,float z,float qx,float qy,float qz,float qw){
        public Joint {
            if(slot<0||slot>=4096||Math.abs(x)>16||Math.abs(y)>16||Math.abs(z)>16)throw new IllegalArgumentException("Pose joint bounds");
            new JointTransform(x,y,z,qx,qy,qz,qw,1,1,1);
            float norm=(float)Math.sqrt(qx*qx+qy*qy+qz*qz+qw*qw);qx/=norm;qy/=norm;qz/=norm;qw/=norm;
        }
        public static Joint of(int slot,JointTransform t){return new Joint(slot,t.x(),t.y(),t.z(),t.qx(),t.qy(),t.qz(),t.qw());}
        public JointTransform applyScale(JointTransform rest){return new JointTransform(x,y,z,qx,qy,qz,qw,rest.sx(),rest.sy(),rest.sz());}
    }
    public record Frame(int version,UUID world,UUID npc,int entityId,long sequence,long tick,String dimension,String rigHash,
                        double x,double y,double z,float yaw,State state,List<Joint> joints,List<Float> morphs){
        public Frame {
            Objects.requireNonNull(world);Objects.requireNonNull(npc);Objects.requireNonNull(state);
            joints=List.copyOf(joints);morphs=List.copyOf(morphs);
            if(version!=VERSION||world.equals(NpcWire.NIL)||npc.equals(NpcWire.NIL)||entityId<0||sequence<=0||tick<0
                ||dimension==null||dimension.length()>128||!dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                ||rigHash==null||!rigHash.matches("[0-9a-f]{64}")||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)
                ||Math.abs(x)>30_000_000||Math.abs(z)>30_000_000||Math.abs(y)>4096||!Float.isFinite(yaw)||Math.abs(yaw)>16
                ||joints.size()>MAX_JOINTS||morphs.size()>MAX_MORPHS)throw new IllegalArgumentException("Pose frame bounds/version");
            int previous=-1;for(var joint:joints){if(joint.slot<=previous)throw new IllegalArgumentException("Pose slots must be sorted and unique");previous=joint.slot;}
            for(float f:morphs)if(!Float.isFinite(f)||f<0||f>1)throw new IllegalArgumentException("Morph weight bounds");
        }
    }
    public static byte[] encode(Frame f){
        try{
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeByte(f.version);uuid(out,f.world);uuid(out,f.npc);
            out.writeInt(f.entityId);out.writeLong(f.sequence);out.writeLong(f.tick);var dimension=f.dimension.getBytes(StandardCharsets.US_ASCII);out.writeByte(dimension.length);out.write(dimension);
            out.write(HexFormat.of().parseHex(f.rigHash));out.writeDouble(f.x);out.writeDouble(f.y);out.writeDouble(f.z);out.writeFloat(f.yaw);out.writeByte(f.state.ordinal());out.writeShort(f.joints.size());
            for(var j:f.joints){out.writeShort(j.slot);out.writeFloat(j.x);out.writeFloat(j.y);out.writeFloat(j.z);for(float v:new float[]{j.qx,j.qy,j.qz,j.qw})out.writeShort(Math.round(v*32767));}
            out.writeByte(f.morphs.size());for(float v:f.morphs)out.writeByte(Math.round(v*255));out.flush();
            if(bytes.size()>MAX_BYTES)throw new IllegalArgumentException("Pose packet too large");return bytes.toByteArray();
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    public static Frame decode(byte[] bytes){
        if(bytes.length>MAX_BYTES||bytes.length<64)throw new IllegalArgumentException("Pose packet size");
        try(var in=new DataInputStream(new ByteArrayInputStream(bytes))){
            int version=in.readUnsignedByte();var world=uuid(in);var npc=uuid(in);int entity=in.readInt();long seq=in.readLong(),tick=in.readLong();
            int size=in.readUnsignedByte();if(size==0||size>128)throw new IllegalArgumentException("Pose dimension length");var dimension=in.readNBytes(size);if(dimension.length!=size)throw new EOFException();
            var hash=in.readNBytes(32);if(hash.length!=32)throw new EOFException();double x=in.readDouble(),y=in.readDouble(),z=in.readDouble();float yaw=in.readFloat();int state=in.readUnsignedByte();
            if(state>=State.values().length)throw new IllegalArgumentException("Pose state");int count=in.readUnsignedShort();if(count>MAX_JOINTS)throw new IllegalArgumentException("Pose joint count");
            var joints=new ArrayList<Joint>();
            for(int i=0;i<count;i++){
                int slot=in.readUnsignedShort();float px=in.readFloat(),py=in.readFloat(),pz=in.readFloat();var q=new float[4];float norm=0;
                for(int j=0;j<4;j++){short v=in.readShort();if(v==Short.MIN_VALUE)throw new IllegalArgumentException("Invalid quantized quaternion");q[j]=v/32767f;norm+=q[j]*q[j];}
                if(Math.abs(norm-1)>.001f)throw new IllegalArgumentException("Invalid quaternion norm");float inverse=1/(float)Math.sqrt(norm);
                joints.add(new Joint(slot,px,py,pz,q[0]*inverse,q[1]*inverse,q[2]*inverse,q[3]*inverse));
            }
            int morphCount=in.readUnsignedByte();if(morphCount>MAX_MORPHS)throw new IllegalArgumentException("Pose morph count");var morphs=new ArrayList<Float>();for(int i=0;i<morphCount;i++)morphs.add(in.readUnsignedByte()/255f);
            if(in.available()!=0)throw new IllegalArgumentException("Trailing pose bytes");
            return new Frame(version,world,npc,entity,seq,tick,new String(dimension,StandardCharsets.US_ASCII),HexFormat.of().formatHex(hash),x,y,z,yaw,State.values()[state],joints,morphs);
        }catch(IOException e){throw new IllegalArgumentException("Truncated pose packet",e);}
    }
    public static Frame fromPose(UUID world,UUID npc,int entity,long seq,long tick,String dimension,dev.agnor.motion.RigDescriptor rig,List<JointTransform> pose,List<Float> morphs,double x,double y,double z,float yaw,State state){
        if(pose.size()!=rig.size())throw new IllegalArgumentException("Pose size");var joints=new ArrayList<Joint>();
        for(int i=0;i<rig.size();i++){
            var t=pose.get(i);var r=rig.joint(i).rest();
            if(Math.abs(t.sx()-r.sx())>1e-5||Math.abs(t.sy()-r.sy())>1e-5||Math.abs(t.sz()-r.sz())>1e-5)throw new IllegalArgumentException("Animated scale unsupported by pose v1");
            float position=Math.abs(t.x()-r.x())+Math.abs(t.y()-r.y())+Math.abs(t.z()-r.z());float dot=Math.abs(t.rotation().dot(r.rotation()));
            if(position>1e-5f||dot<.9999999f)joints.add(Joint.of(i,t));
        }
        return new Frame(VERSION,world,npc,entity,seq,tick,dimension,rig.revision(),x,y,z,yaw,state,joints,morphs);
    }
    private static void uuid(DataOutputStream out,UUID id)throws IOException{out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
    private static UUID uuid(DataInputStream in)throws IOException{return new UUID(in.readLong(),in.readLong());}
    private PoseWire(){}
}
