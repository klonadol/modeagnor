package dev.agnor.motion;

import java.util.*;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Bounded host Verlet tips with length/cone constraints and artist-authored sphere proxies.
 * Coordinates use a stable local simulation origin; rebase with reset(), not large Minecraft world floats. */
public final class SecondaryPhysics {
    private static final double H=1.0/120;
    public record Root(float x,float y,float z,float yaw){
        public Root {for(float v:new float[]{x,y,z,yaw})if(!Float.isFinite(v)||Math.abs(v)>1024)throw new IllegalArgumentException("Invalid/local-origin physics root");}
        Matrix4f matrix(){return new Matrix4f().translation(x,y,z).rotateY(yaw);}
    }
    public record Chain(String id,List<Integer> nodes,float stiffness,float damping,float cone,float radius,TwoBoneIk.Point gravity,float inertia){
        public Chain(String id,List<Integer> nodes,float stiffness,float damping,float cone,float radius,TwoBoneIk.Point gravity){this(id,nodes,stiffness,damping,cone,radius,gravity,1);}
        public Chain {
            nodes=List.copyOf(nodes);
            if(id==null||id.isBlank()||id.length()>64||nodes.size()<2||nodes.size()>32||gravity==null)throw new IllegalArgumentException("Invalid chain");
            for(float v:new float[]{stiffness,damping,cone,radius,inertia})if(!Float.isFinite(v))throw new IllegalArgumentException("Non-finite chain");
            if(stiffness<0||stiffness>1000||damping<0||damping>100||cone<=0||cone>1.4f||radius<0||radius>.2f||gravity.vector().length()>20||inertia<0||inertia>1)throw new IllegalArgumentException("Chain limits");
        }
    }
    public record Sphere(int joint,TwoBoneIk.Point offset,float radius){
        public Sphere {if(offset==null||!Float.isFinite(radius)||radius<=0||radius>2)throw new IllegalArgumentException("Invalid proxy");}
    }
    public enum Status { ACTIVE, PAUSED, RESET_TELEPORT, CONSTRAINT_CONFLICT, RESET_INVALID }
    public record Outcome(String id,Status status,int contacts,float maxPenetration,String detail) {}
    public record Frame(List<JointTransform> pose,List<Outcome> outcomes,int substeps){public Frame{pose=List.copyOf(pose);outcomes=List.copyOf(outcomes);}}
    private static final class State {Vector3f[] tip,previous;Vector3f anchor;}
    private final RigDescriptor rig;private final List<Chain> chains;private final List<Sphere> proxies;private final State[] states;
    private Root previousRoot;private double accumulator;
    public SecondaryPhysics(RigDescriptor rig,List<Chain> chains,List<Sphere> proxies){
        this.rig=Objects.requireNonNull(rig);this.chains=List.copyOf(chains);this.proxies=List.copyOf(proxies);
        if(chains.size()>32||proxies.size()>32||chains.stream().mapToInt(c->c.nodes.size()-1).sum()>256)throw new IllegalArgumentException("Physics budget exceeded");
        var used=new HashSet<Integer>();var ids=new HashSet<String>();var rest=rig.globals(rig.rest());
        for(var c:chains){
            if(!ids.add(c.id))throw new IllegalArgumentException("Duplicate chain id");
            for(int i=0;i<c.nodes.size();i++){
                int n=c.nodes.get(i);if(n<0||n>=rig.size()||!used.add(n))throw new IllegalArgumentException("Invalid/overlapping chain node");
                if(i>0&&(rig.joint(n).parent()!=c.nodes.get(i-1)||rest[n].getTranslation(new Vector3f()).distance(rest[c.nodes.get(i-1)].getTranslation(new Vector3f()))<1e-5f))throw new IllegalArgumentException("Chain must have direct nonzero segments");
            }
        }
        // Separate chains cannot own ancestors of each other, including through unowned intermediate nodes.
        for(var c:chains){int p=rig.joint(c.nodes.getFirst()).parent();while(p>=0){if(used.contains(p))throw new IllegalArgumentException("Dependent chains must be merged");p=rig.joint(p).parent();}}
        for(var p:proxies){
            if(p.joint<0||p.joint>=rig.size())throw new IllegalArgumentException("Invalid proxy mapping");
            int n=p.joint;while(n>=0){if(used.contains(n))throw new IllegalArgumentException("Proxy cannot depend on secondary bones");n=rig.joint(n).parent();}
        }
        states=new State[chains.size()];for(int i=0;i<states.length;i++)states[i]=new State();
    }
    /** Call on unload/teleport; pause also discards velocities, so resume never catches up old time. */
    public void reset(){for(var s:states){s.tip=null;s.previous=null;}previousRoot=null;accumulator=0;}
    public Frame step(List<JointTransform> base,Root root,float dt,boolean paused){
        Objects.requireNonNull(root);
        if(!Float.isFinite(dt)||dt<=0||dt>.1f||base.size()!=rig.size())throw new IllegalArgumentException("Physics step bounds");
        // Validate before changing simulation state.
        var baseGlobals=rig.globals(base);
        for(var c:chains)for(int node:c.nodes){var scale=baseGlobals[node].getScale(new Vector3f());if(Math.abs(scale.x-scale.y)>1e-4f||Math.abs(scale.x-scale.z)>1e-4f)throw new IllegalArgumentException("Secondary chains require uniform scale");}
        if(paused){reset();return new Frame(base,chains.stream().map(c->new Outcome(c.id,Status.PAUSED,0,0,"velocity discarded")).toList(),0);}
        boolean teleport=previousRoot!=null&&(new Vector3f(root.x-previousRoot.x,root.y-previousRoot.y,root.z-previousRoot.z).length()>2||Math.abs(angle(root.yaw-previousRoot.yaw))>1.5f);
        if(teleport)reset();previousRoot=root;
        accumulator+=dt;int count=Math.min(12,(int)((accumulator+1e-8)/H));accumulator-=count*H;
        var result=new ArrayList<>(base);var outcomes=new ArrayList<Outcome>();var rootMatrix=root.matrix();
        var colliderCenters=new ArrayList<Vector3f>();var colliderRadii=new ArrayList<Float>();
        for(var proxy:proxies){var m=new Matrix4f(rootMatrix).mul(baseGlobals[proxy.joint]);colliderCenters.add(m.transformPosition(proxy.offset.vector()));colliderRadii.add(proxy.radius*m.getScale(new Vector3f()).x);}
        for(int ci=0;ci<chains.size();ci++){
            var c=chains.get(ci);var state=states[ci];int contacts=0;float penetration=0;Status status=teleport?Status.RESET_TELEPORT:Status.ACTIVE;String detail="";
            try{
                if(state.tip==null)initialize(state,c,baseGlobals,rootMatrix);
                var chainAnchor=new Matrix4f(rootMatrix).mul(baseGlobals[c.nodes.getFirst()]).getTranslation(new Vector3f());
                var carry=new Vector3f(chainAnchor).sub(state.anchor).mul(1-c.inertia);
                for(int j=0;j<state.tip.length;j++){state.tip[j].add(carry);state.previous[j].add(carry);}state.anchor.set(chainAnchor);
                // Rebuild from the current base each substep; only these chain rotations are overridden.
                for(int sub=0;sub<Math.max(1,count);sub++){
                    int parent=rig.joint(c.nodes.getFirst()).parent();var parentMatrix=parent<0?new Matrix4f(rootMatrix):new Matrix4f(rootMatrix).mul(baseGlobals[parent]);
                    for(int j=0;j<c.nodes.size()-1;j++){
                        int node=c.nodes.get(j),child=c.nodes.get(j+1);var current=new Matrix4f(parentMatrix).mul(base.get(node).matrix());
                        var anchor=current.getTranslation(new Vector3f());var t=base.get(child);var restTip=current.transformPosition(new Vector3f(t.x(),t.y(),t.z()));
                        float length=anchor.distance(restTip);if(!Float.isFinite(length)||length<1e-6f)throw new ArithmeticException("degenerate segment");
                        var restDir=new Vector3f(restTip).sub(anchor).normalize();var candidate=new Vector3f(state.tip[j]);
                        if(count>0){
                            var velocity=new Vector3f(state.tip[j]).sub(state.previous[j]).mul((float)Math.exp(-c.damping*H));
                            candidate.add(velocity).add(new Vector3f(restTip).sub(state.tip[j]).mul(c.stiffness*(float)(H*H))).add(c.gravity.vector().mul((float)(H*H)));
                        }
                        if(!candidate.isFinite())throw new ArithmeticException("non-finite tip");
                        candidate=constrain(candidate,anchor,restDir,length,c.cone);
                        boolean colliding=false;
                        for(int k=0;k<proxies.size();k++)if(candidate.distance(colliderCenters.get(k))<colliderRadii.get(k)+c.radius){contacts++;colliding=true;}
                        if(colliding)candidate=feasibleTip(candidate,anchor,restDir,length,c.cone,colliderCenters,colliderRadii,c.radius);
                        if(!candidate.isFinite())throw new ArithmeticException("non-finite constrained tip");
                        var direction=new Vector3f(candidate).sub(anchor).normalize();
                        if(direction.dot(restDir)<Math.cos(c.cone)-1e-4f)throw new ConstraintConflict("proxy and cone conflict at node "+node+" angle="+Math.acos(direction.dot(restDir))+" limit="+c.cone);
                        for(int k=0;k<proxies.size();k++)penetration=Math.max(penetration,colliderRadii.get(k)+c.radius-candidate.distance(colliderCenters.get(k)));
                        if(penetration>1e-4f)throw new ConstraintConflict("proxy penetration "+penetration);
                        if(count>0){state.previous[j].set(state.tip[j]);state.tip[j].set(candidate);}
                        var worldDelta=new Quaternionf().rotationTo(restDir,direction);
                        var pq=parentMatrix.getUnnormalizedRotation(new Quaternionf()).normalize();
                        var q=new Quaternionf(pq).invert().mul(worldDelta).mul(pq).mul(base.get(node).rotation()).normalize();
                        var old=base.get(node);result.set(node,new JointTransform(old.x(),old.y(),old.z(),q.x,q.y,q.z,q.w,old.sx(),old.sy(),old.sz()));
                        parentMatrix.mul(result.get(node).matrix());
                    }
                }
            }catch(ArithmeticException e){status=Status.RESET_INVALID;detail=e.getMessage();restore(result,base,c);initialize(state,c,baseGlobals,rootMatrix);}
            catch(ConstraintConflict e){status=Status.CONSTRAINT_CONFLICT;detail=e.getMessage();restore(result,base,c);initialize(state,c,baseGlobals,rootMatrix);}
            outcomes.add(new Outcome(c.id,status,contacts,Math.max(0,penetration),detail));
        }
        return new Frame(result,outcomes,count);
    }
    private void initialize(State s,Chain c,Matrix4f[] globals,Matrix4f root){
        s.anchor=new Matrix4f(root).mul(globals[c.nodes.getFirst()]).getTranslation(new Vector3f());
        s.tip=new Vector3f[c.nodes.size()-1];s.previous=new Vector3f[s.tip.length];
        for(int j=0;j<s.tip.length;j++){s.tip[j]=new Matrix4f(root).mul(globals[c.nodes.get(j+1)]).getTranslation(new Vector3f());s.previous[j]=new Vector3f(s.tip[j]);}
    }
    private static void restore(List<JointTransform> out,List<JointTransform> base,Chain c){for(int n:c.nodes)out.set(n,base.get(n));}
    private static Vector3f constrain(Vector3f tip,Vector3f anchor,Vector3f reference,float length,float cone){
        var direction=new Vector3f(tip).sub(anchor);if(direction.lengthSquared()<1e-12f)direction.set(reference);direction.normalize();
        float dot=Math.max(-1,Math.min(1,direction.dot(reference))),angle=(float)Math.acos(dot);
        if(angle>cone){var q=new Quaternionf().rotationTo(reference,direction);new Quaternionf().slerp(q,cone/angle).transform(reference,direction);direction.normalize();}
        return direction.mul(length).add(anchor);
    }
    private record Plane(Vector3f normal,float minimum) {}
    /** A length sphere turns cone/collider constraints into unit-direction half-spaces.
     * The nearest feasible direction is interior, on one boundary circle, or at a pair intersection.
     * Enumerating these bounded candidates avoids alternating-projection stalls at tangencies. */
    private static Vector3f feasibleTip(Vector3f tip,Vector3f anchor,Vector3f reference,float length,float cone,List<Vector3f> centers,List<Float> radii,float tipRadius){
        var planes=new ArrayList<Plane>();planes.add(new Plane(new Vector3f(reference),(float)Math.cos(cone)));
        for(int i=0;i<centers.size();i++){
            var n=new Vector3f(centers.get(i)).sub(anchor);float d=n.length(),radius=radii.get(i)+tipRadius+1e-5f;
            if(d<1e-7f){if(length<radius)throw new ConstraintConflict("proxy encloses entire segment");continue;}
            float bound=(length*length+d*d-radius*radius)/(2*length*d);
            if(bound < -1)throw new ConstraintConflict("proxy encloses entire segment");
            if(bound<1)planes.add(new Plane(n.div(-d),-bound));
        }
        var wanted=new Vector3f(tip).sub(anchor).normalize();Vector3f best=better(wanted,wanted,planes,null);
        for(int i=0;i<planes.size();i++){
            var p=planes.get(i);var tangent=new Vector3f(wanted).sub(new Vector3f(p.normal).mul(wanted.dot(p.normal)));
            if(tangent.lengthSquared()<1e-10f)tangent.set(Math.abs(p.normal.x)<.8f?1:0,Math.abs(p.normal.x)<.8f?0:1,0).cross(p.normal);
            var boundary=tangent.normalize().mul((float)Math.sqrt(Math.max(0,1-p.minimum*p.minimum))).add(new Vector3f(p.normal).mul(p.minimum));
            best=better(boundary,wanted,planes,best);
            for(int j=0;j<i;j++){
                var q=planes.get(j);float dot=p.normal.dot(q.normal),det=1-dot*dot;if(det<1e-6f)continue;
                var center=new Vector3f(p.normal).mul((p.minimum-dot*q.minimum)/det).add(new Vector3f(q.normal).mul((q.minimum-dot*p.minimum)/det));
                float remaining=1-center.lengthSquared();if(remaining < -1e-6f)continue;
                var axis=new Vector3f(p.normal).cross(q.normal).normalize().mul((float)Math.sqrt(Math.max(0,remaining)));
                best=better(new Vector3f(center).add(axis),wanted,planes,best);best=better(new Vector3f(center).sub(axis),wanted,planes,best);
            }
        }
        if(best==null)throw new ConstraintConflict("no feasible direction for cone/proxies");
        return best.normalize().mul(length).add(anchor);
    }
    private static Vector3f better(Vector3f candidate,Vector3f wanted,List<Plane> planes,Vector3f best){
        if(!candidate.isFinite()||Math.abs(candidate.lengthSquared()-1)>1e-4f)return best;
        for(var p:planes)if(candidate.dot(p.normal)<p.minimum-1e-6f)return best;
        return best==null||candidate.dot(wanted)>best.dot(wanted)?candidate:best;
    }
    private static float angle(float v){return (float)Math.atan2(Math.sin(v),Math.cos(v));}
    private static final class ConstraintConflict extends RuntimeException {ConstraintConflict(String message){super(message);}}
}
