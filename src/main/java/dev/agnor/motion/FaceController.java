package dev.agnor.motion;

import java.util.*;

/** Independent host face lanes; no audio, random mouth motion or renderer state. */
public final class FaceController {
    public enum Expression { NEUTRAL, SMILE, SURPRISE, ANGRY, SAD }
    public enum Domain { EYES, BROWS, MOUTH_CORNER }
    public enum Missing { EXPRESSION, BLINK_LEFT, BLINK_RIGHT, MOUTH, GAZE }
    public record Binding(int target,float gain,Domain domain){
        public Binding {if(target<0||domain==null)throw new IllegalArgumentException("Face binding");JointTransform.weight(gain);}
    }
    public record Eye(int joint,TwoBoneIk.Point forward,float cone){
        public Eye {if(joint<0||forward==null||forward.vector().lengthSquared()<1e-10||!Float.isFinite(cone)||cone<0||cone>.6f)throw new IllegalArgumentException("Eye binding");}
    }
    public record Input(Expression expression,float intensity,float blinkLeft,float blinkRight,float mouthOpen,TwoBoneIk.Point gazeTarget){
        public Input {if(expression==null)throw new IllegalArgumentException("Expression");for(float w:new float[]{intensity,blinkLeft,blinkRight,mouthOpen})JointTransform.weight(w);}
    }
    public record Result(List<JointTransform> pose,List<Float> weights,Set<Missing> missing){
        public Result {pose=List.copyOf(pose);weights=List.copyOf(weights);missing=Set.copyOf(missing);}
    }
    private final int targets,left,right,mouth;
    private final Map<Expression,List<Binding>> expressions;
    private final List<Eye> eyes;
    public FaceController(int targets,int left,int right,int mouth,Map<Expression,List<Binding>> expressions,List<Eye> eyes){
        if(targets<0||targets>512)throw new IllegalArgumentException("Morph count");
        for(int slot:new int[]{left,right,mouth})if(slot < -1||slot>=targets)throw new IllegalArgumentException("Morph slot");
        this.targets=targets;this.left=left;this.right=right;this.mouth=mouth;
        var copy=new EnumMap<Expression,List<Binding>>(Expression.class);
        for(var entry:expressions.entrySet()){
            for(var b:entry.getValue())if(b.target()>=targets||b.target()==left||b.target()==right||b.target()==mouth)throw new IllegalArgumentException("Expression overlaps independent lane");
            copy.put(entry.getKey(),List.copyOf(entry.getValue()));
        }
        this.expressions=Map.copyOf(copy);this.eyes=List.copyOf(eyes);
    }
    public Result evaluate(RigDescriptor rig,List<JointTransform> body,Input input){
        if(body.size()!=rig.size())throw new IllegalArgumentException("Face rig mismatch");
        float[] weights=new float[targets];var missing=EnumSet.noneOf(Missing.class);
        var bindings=expressions.get(input.expression());
        if(bindings==null&&input.expression()!=Expression.NEUTRAL&&input.intensity()>0)missing.add(Missing.EXPRESSION);
        if(bindings!=null)for(var b:bindings){
            float closure=b.domain()==Domain.EYES?1-Math.max(input.blinkLeft(),input.blinkRight()):1;
            weights[b.target()]=Math.max(weights[b.target()],input.intensity()*b.gain()*closure);
        }
        lane(weights,left,input.blinkLeft(),Missing.BLINK_LEFT,missing);
        lane(weights,right,input.blinkRight(),Missing.BLINK_RIGHT,missing);
        lane(weights,mouth,input.mouthOpen(),Missing.MOUTH,missing);
        List<JointTransform> pose=List.copyOf(body);
        if(input.gazeTarget()!=null){
            if(eyes.isEmpty())missing.add(Missing.GAZE);
            for(var eye:eyes){
                if(eye.joint()>=rig.size()){missing.add(Missing.GAZE);continue;}
                pose=LookAtSolver.solve(rig,pose,eye.joint(),input.gazeTarget(),eye.forward(),eye.cone());
            }
        }
        var values=new ArrayList<Float>(targets);for(float w:weights)values.add(w);
        return new Result(pose,values,missing);
    }
    private static void lane(float[] values,int slot,float w,Missing channel,Set<Missing> missing){
        if(slot<0){if(w>0)missing.add(channel);}else values[slot]=Math.max(values[slot],w);
    }
    /** Exponential smoothing is host-owned, with explicit dt and neutral reset supplied by its owner. */
    public static List<Float> smooth(List<Float> previous,List<Float> target,float dt,float tau){
        if(previous.size()!=target.size()||!Float.isFinite(dt)||dt<0||dt>.25f||!Float.isFinite(tau)||tau<=0)throw new IllegalArgumentException("Face smoothing clock");
        float alpha=(float)(1-Math.exp(-dt/tau));var result=new ArrayList<Float>(target.size());
        for(int i=0;i<target.size();i++){JointTransform.weight(previous.get(i));JointTransform.weight(target.get(i));result.add(previous.get(i)+(target.get(i)-previous.get(i))*alpha);}
        return List.copyOf(result);
    }
}
