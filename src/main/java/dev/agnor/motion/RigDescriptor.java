package dev.agnor.motion;

import java.util.*;
import org.joml.Matrix4f;

/** Renderer-independent hierarchy. Revision is supplied by the validated asset adapter. */
public final class RigDescriptor {
    public record Joint(String name,int parent,JointTransform rest) {
        public Joint { if(name==null||name.isBlank()||name.length()>256||rest==null)throw new IllegalArgumentException("Invalid joint"); }
    }
    private final String revision;
    private final List<Joint> joints;
    private final int[] order;
    public RigDescriptor(String revision,List<Joint> joints){
        if(revision==null||revision.isBlank()||revision.length()>128||joints.isEmpty()||joints.size()>4096)throw new IllegalArgumentException("Rig bounds");
        this.revision=revision;this.joints=List.copyOf(joints);var seen=new HashSet<String>();
        for(var j:joints)if(!seen.add(j.name())||j.parent() < -1||j.parent()>=joints.size())throw new IllegalArgumentException("Duplicate name/parent bounds");
        var sorted=new ArrayList<Integer>();var marks=new byte[joints.size()];
        for(int i=0;i<joints.size();i++)visit(i,marks,sorted);
        order=sorted.stream().mapToInt(Integer::intValue).toArray();
    }
    private void visit(int i,byte[] marks,List<Integer> sorted){
        if(marks[i]==2)return;if(marks[i]==1)throw new IllegalArgumentException("Rig cycle");marks[i]=1;
        int parent=joints.get(i).parent();if(parent>=0)visit(parent,marks,sorted);marks[i]=2;sorted.add(i);
    }
    public String revision(){return revision;}
    public int size(){return joints.size();}
    public Joint joint(int i){return joints.get(i);}
    public int slot(String name){for(int i=0;i<size();i++)if(joints.get(i).name().equals(name))return i;return -1;}
    public List<JointTransform> rest(){return joints.stream().map(Joint::rest).toList();}
    public Matrix4f[] globals(List<JointTransform> pose){
        if(pose.size()!=size())throw new IllegalArgumentException("Pose size");
        var matrices=new Matrix4f[size()];
        for(int i:order){int p=joint(i).parent();matrices[i]=p<0?pose.get(i).matrix():new Matrix4f(matrices[p]).mul(pose.get(i).matrix());}
        return matrices;
    }
}
