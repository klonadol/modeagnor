package dev.agnor.motion;

import java.util.*;

/** Bounded priority arbitration over rig effectors; root gameplay transform stays outside this solver. */
public final class WholeBodyController {
    public record Chain(int root,int middle,int end,TwoBoneIk.BendLimit limit){
        public Chain {if(root<0||middle<0||end<0||root==middle||root==end||middle==end||limit==null)throw new IllegalArgumentException("Chain binding");}
    }
    public record Look(int joint,TwoBoneIk.Point forward,float coneRadians){
        public Look {if(joint<0||forward==null||forward.vector().lengthSquared()<1e-10f||!Float.isFinite(coneRadians)||coneRadians<0||coneRadians>(float)Math.PI)throw new IllegalArgumentException("Look binding");}
    }
    public enum Status { APPLIED, CONSTRAINED, PREEMPTED, EXPIRED, UNSUPPORTED, FAILED }
    /** Error is radians for LOOK, model-space distance for limb tasks, zero for non-executed tasks. */
    public record Outcome(UUID task,Status status,float error,String detail){
        public Outcome(UUID task,Status status,float error){this(task,status,error,"");}
        public Outcome {if(detail==null||detail.length()>256)throw new IllegalArgumentException("Outcome detail");}
    }
    public record Result(List<JointTransform> pose,List<Outcome> outcomes){
        public Result {pose=List.copyOf(pose);outcomes=List.copyOf(outcomes);}
    }
    private final Map<MotionTask.Intent,Chain> chains;
    private final Look look;
    private final Map<UUID,MotionTask> tasks=new HashMap<>();
    public WholeBodyController(Map<MotionTask.Intent,Chain> chains,Look look){
        if(chains.containsKey(MotionTask.Intent.LOOK))throw new IllegalArgumentException("LOOK needs a Look binding");
        this.chains=Map.copyOf(chains);this.look=look;
    }
    public void submit(MotionTask task){if(!tasks.containsKey(task.id())&&tasks.size()>=32)throw new IllegalStateException("Motion task capacity");tasks.put(task.id(),task);}
    public boolean cancel(UUID id){return tasks.remove(id)!=null;}
    public Result evaluate(RigDescriptor rig,List<JointTransform> base,long tick){
        if(tick<0||base.size()!=rig.size())throw new IllegalArgumentException("Motion clock/rig");
        var sorted=tasks.values().stream().sorted(Comparator.comparingInt(MotionTask::priority).reversed().thenComparing(t->t.id().toString())).toList();
        var claims=new HashSet<Integer>();var outcomes=new ArrayList<Outcome>();List<JointTransform> pose=List.copyOf(base);
        for(var task:sorted){
            if(task.expiresAtTick()<=tick){outcomes.add(new Outcome(task.id(),Status.EXPIRED,0));tasks.remove(task.id());continue;}
            Set<Integer> writes;var chain=chains.get(task.intent());
            if(task.intent()==MotionTask.Intent.LOOK&&look!=null)writes=Set.of(look.joint());
            else if(chain!=null)writes=Set.of(chain.root(),chain.middle());
            else {outcomes.add(new Outcome(task.id(),Status.UNSUPPORTED,0));continue;}
            // Parent rotation influences descendants too, so hierarchy conflicts must be explicit.
            var affected=new HashSet<Integer>();
            for(int i=0;i<rig.size();i++)for(int p=i;p>=0;p=rig.joint(p).parent())if(writes.contains(p)){affected.add(i);break;}
            if(affected.stream().anyMatch(claims::contains)){outcomes.add(new Outcome(task.id(),Status.PREEMPTED,0));continue;}
            var before=pose;
            try{
                float error=0;
                if(task.intent()==MotionTask.Intent.LOOK){
                    pose=LookAtSolver.solve(rig,pose,look.joint(),task.target(),look.forward(),look.coneRadians());
                    var matrix=rig.globals(pose)[look.joint()];
                    var desired=task.target().vector().sub(matrix.getTranslation(new org.joml.Vector3f()));
                    if(desired.lengthSquared()>1e-10f){
                        var actual=matrix.getUnnormalizedRotation(new org.joml.Quaternionf()).normalize().transform(look.forward().vector()).normalize();
                        error=(float)Math.acos(Math.max(-1,Math.min(1,desired.normalize().dot(actual))));
                    }
                }
                else {
                    boolean foot=task.intent()==MotionTask.Intent.FOOT_LEFT||task.intent()==MotionTask.Intent.FOOT_RIGHT;
                    var desiredFoot=foot?rig.globals(pose)[chain.end()].getUnnormalizedRotation(new org.joml.Quaternionf()).normalize():null;
                    var result=IkPoseSolver.reach(rig,pose,chain.root(),chain.middle(),chain.end(),task.target(),task.pole(),chain.limit());pose=result.pose();error=result.actualError();
                    if(foot){
                        var globals=rig.globals(pose);int parent=rig.joint(chain.end()).parent();
                        var q=globals[parent].getUnnormalizedRotation(new org.joml.Quaternionf()).normalize().invert().mul(desiredFoot).normalize();
                        var old=pose.get(chain.end());var adjusted=new ArrayList<>(pose);
                        adjusted.set(chain.end(),new JointTransform(old.x(),old.y(),old.z(),q.x,q.y,q.z,q.w,old.sx(),old.sy(),old.sz()));pose=List.copyOf(adjusted);
                    }
                }
                claims.addAll(affected);outcomes.add(new Outcome(task.id(),error>.0001f?Status.CONSTRAINED:Status.APPLIED,error));
            }catch(IllegalArgumentException e){pose=before;outcomes.add(new Outcome(task.id(),Status.FAILED,0,e.getMessage()));}
        }
        return new Result(pose,outcomes);
    }
}
