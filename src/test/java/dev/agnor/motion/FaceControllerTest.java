package dev.agnor.motion;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class FaceControllerTest {
    final RigDescriptor rig=new RigDescriptor("face",List.of(new RigDescriptor.Joint("head",-1,JointTransform.IDENTITY)));
    FaceController controller(){return new FaceController(6,0,1,2,Map.of(FaceController.Expression.SMILE,List.of(new FaceController.Binding(3,.8f,FaceController.Domain.EYES),new FaceController.Binding(4,1,FaceController.Domain.BROWS),new FaceController.Binding(5,1,FaceController.Domain.MOUTH_CORNER))),List.of());}
    FaceController.Input input(float blink,float mouth){return new FaceController.Input(FaceController.Expression.SMILE,1,blink,blink,mouth,null);}
    @Test void mouthDoesNotEraseExpressionAndBlinkDoesNotDoubleCloseEyes(){
        var c=controller();var smile=c.evaluate(rig,rig.rest(),input(0,0));var speaking=c.evaluate(rig,rig.rest(),input(0,1));
        assertEquals(smile.weights().subList(3,6),speaking.weights().subList(3,6));assertEquals(1,speaking.weights().get(2));
        var blink=c.evaluate(rig,rig.rest(),input(1,1));assertEquals(0,blink.weights().get(3));assertEquals(1,blink.weights().get(4));assertEquals(1,blink.weights().get(5));
    }
    @Test void neutralResetHasNoStaleWeightsAndInstancesAreIndependent(){
        var c=controller();var smile=c.evaluate(rig,rig.rest(),input(0,1));
        var neutral=c.evaluate(rig,rig.rest(),new FaceController.Input(FaceController.Expression.NEUTRAL,0,0,0,0,null));
        assertTrue(neutral.weights().stream().allMatch(w->w==0));assertEquals(1,smile.weights().get(2));
        assertThrows(UnsupportedOperationException.class,()->smile.weights().clear());
    }
    @Test void unsupportedChannelsHaveExplicitFallback(){
        var c=new FaceController(0,-1,-1,-1,Map.of(),List.of());
        var r=c.evaluate(rig,rig.rest(),new FaceController.Input(FaceController.Expression.SMILE,1,1,1,1,new TwoBoneIk.Point(0,0,1)));
        assertEquals(EnumSet.allOf(FaceController.Missing.class),r.missing());assertEquals(rig.rest(),r.pose());
    }
    @Test void smoothingAndInputBounds(){
        var full=FaceController.smooth(List.of(0f),List.of(1f),.1f,.1f);
        assertEquals(1-Math.exp(-1),full.getFirst(),1e-6);
        var half=FaceController.smooth(FaceController.smooth(List.of(0f),List.of(1f),.05f,.1f),List.of(1f),.05f,.1f);
        assertEquals(full.getFirst(),half.getFirst(),1e-6);
        assertThrows(IllegalArgumentException.class,()->FaceController.smooth(List.of(0f),List.of(1f),1,.1f));
        assertThrows(IllegalArgumentException.class,()->input(Float.NaN,0));
    }
}
