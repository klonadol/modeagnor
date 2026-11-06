package dev.agnor.motion;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TwoBoneIkTest {
    TwoBoneIk.Point p(float x,float y,float z){return new TwoBoneIk.Point(x,y,z);}
    final TwoBoneIk.BendLimit free=new TwoBoneIk.BendLimit(.01f,3.1f);
    @Test void reachableTargetPreservesBothLengths(){
        var s=TwoBoneIk.solve(p(0,0,0),p(1,1,0),p(0,0,1),1,1,free);
        assertEquals(1,s.joint().vector().length(),1e-5);assertEquals(1,s.end().vector().distance(s.joint().vector()),1e-5);
        assertEquals(0,s.targetError(),1e-5);assertFalse(s.constrained());
    }
    @Test void unreachableAndBendLimitsClamp(){
        var s=TwoBoneIk.solve(p(0,0,0),p(10,0,0),p(0,1,0),1,1,new TwoBoneIk.BendLimit(.5f,1));
        assertTrue(s.constrained());assertEquals(.5f,s.bend(),1e-5);assertTrue(s.targetError()>8);
        var folded=TwoBoneIk.solve(p(0,0,0),p(0,0,0),p(0,1,0),1,1,new TwoBoneIk.BendLimit(.5f,1));
        assertEquals(1,folded.bend(),1e-5);
    }
    @Test void collinearPoleAndZeroTargetAreFiniteAndRepeatable(){
        var s=TwoBoneIk.solve(p(0,0,0),p(0,0,0),p(0,0,0),1,1,free);
        assertEquals(s,TwoBoneIk.solve(p(0,0,0),p(0,0,0),p(0,0,0),1,1,free));
        assertTrue(Float.isFinite(s.joint().x()));
    }
    @Test void solverRotationsActuallyReachAndDoNotMoveRoot(){
        var t=new JointTransform(1,0,0,0,0,0,1,1,1,1);
        var rig=new RigDescriptor("arm",List.of(new RigDescriptor.Joint("root",-1,JointTransform.IDENTITY),new RigDescriptor.Joint("mid",0,t),new RigDescriptor.Joint("end",1,t)));
        var result=IkPoseSolver.reach(rig,rig.rest(),0,1,2,p(1,1,0),p(0,0,1),free);
        assertTrue(result.actualError()<1e-5);
        var root=result.pose().getFirst();assertEquals(0,root.x());assertEquals(0,root.y());assertEquals(0,root.z());assertEquals(1,root.sx());
        assertEquals(t,rig.rest().get(1));
    }
    @Test void invalidLengthsAndLimitsFail(){
        assertThrows(IllegalArgumentException.class,()->TwoBoneIk.solve(p(0,0,0),p(1,0,0),p(0,1,0),0,1,free));
        assertThrows(IllegalArgumentException.class,()->new TwoBoneIk.BendLimit(2,1));
    }
}
