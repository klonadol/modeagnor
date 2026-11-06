package dev.agnor.motion;

import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Analytic two-segment solve in world/model space, with an explicit bend plane. */
public final class TwoBoneIk {
    public record BendLimit(float min,float max){
        public BendLimit {if(!Float.isFinite(min)||!Float.isFinite(max)||min<0||max>(float)Math.PI||min>max)throw new IllegalArgumentException("Bend limits");}
    }
    public record Point(float x,float y,float z){
        public Point {if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))throw new IllegalArgumentException("Non-finite IK point");}
        public Vector3f vector(){return new Vector3f(x,y,z);}
        static Point of(Vector3fc v){return new Point(v.x(),v.y(),v.z());}
    }
    public record Solution(Point joint,Point end,float bend,float targetError,boolean constrained){}
    public static Solution solve(Point root,Point target,Point pole,float upper,float lower,BendLimit limit){
        if(!Float.isFinite(upper)||!Float.isFinite(lower)||upper<=0||lower<=0)throw new IllegalArgumentException("Segment length");
        var origin=root.vector();var direction=target.vector().sub(origin);float requested=direction.length();
        if(!Float.isFinite(requested))throw new IllegalArgumentException("IK range overflow");
        if(requested<1e-7f)direction.set(1,0,0);else direction.div(requested);
        double max=Math.sqrt(upper*upper+lower*lower+2.0*upper*lower*Math.cos(limit.min()));
        double min=Math.sqrt(Math.max(0,upper*upper+lower*lower+2.0*upper*lower*Math.cos(limit.max())));
        float distance=(float)Math.max(Math.max(min,(upper+lower)*1e-6),Math.min(max,requested));
        var perpendicular=pole.vector().sub(origin);perpendicular.fma(-perpendicular.dot(direction),direction);
        if(perpendicular.lengthSquared()<1e-10f){
            perpendicular.set(Math.abs(direction.y)<.9f?new Vector3f(0,1,0):new Vector3f(1,0,0));
            perpendicular.fma(-perpendicular.dot(direction),direction);
        }
        perpendicular.normalize();
        float projection=(upper*upper-lower*lower+distance*distance)/(2*distance);
        float height=(float)Math.sqrt(Math.max(0,upper*upper-projection*projection));
        var joint=new Vector3f(origin).fma(projection,direction).fma(height,perpendicular);
        var end=new Vector3f(origin).fma(distance,direction);
        float cosine=(distance*distance-upper*upper-lower*lower)/(2*upper*lower);
        float bend=(float)Math.acos(Math.max(-1,Math.min(1,cosine)));
        return new Solution(Point.of(joint),Point.of(end),bend,end.distance(target.vector()),Math.abs(requested-distance)>1e-5f);
    }
}
