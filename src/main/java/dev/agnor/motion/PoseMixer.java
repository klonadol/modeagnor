package dev.agnor.motion;

import java.util.*;

/** Stable layer order is explicit. Additive uses an explicit reference pose. */
public final class PoseMixer {
    public enum Mode { OVERRIDE, ADDITIVE }
    public record Layer(List<JointTransform> pose,List<JointTransform> reference,Set<Integer> mask,float weight,Mode mode){
        public Layer {
            pose=List.copyOf(pose);reference=List.copyOf(reference);mask=Set.copyOf(mask);JointTransform.weight(weight);
            if(mode==null)throw new IllegalArgumentException("Missing layer mode");
        }
    }
    public static List<JointTransform> mix(RigDescriptor rig,List<JointTransform> base,List<Layer> layers){
        if(base.size()!=rig.size())throw new IllegalArgumentException("Base rig mismatch");
        var result=new ArrayList<>(base);
        for(var layer:layers){
            if(layer.pose().size()!=rig.size()||layer.reference().size()!=rig.size())throw new IllegalArgumentException("Layer rig mismatch");
            for(int joint:layer.mask()){
                if(joint<0||joint>=rig.size())throw new IllegalArgumentException("Mask slot");
                var value=layer.mode()==Mode.OVERRIDE?JointTransform.mix(result.get(joint),layer.pose().get(joint),layer.weight()):
                    JointTransform.additive(result.get(joint),layer.pose().get(joint),layer.reference().get(joint),layer.weight());
                result.set(joint,value);
            }
        }
        return List.copyOf(result);
    }
}
