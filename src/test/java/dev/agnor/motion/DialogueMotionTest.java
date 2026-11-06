package dev.agnor.motion;

import dev.agnor.network.PoseWire;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DialogueMotionTest {
    final DialogueMotion.Token a=new DialogueMotion.Token(new UUID(0,1),new UUID(0,10));
    @Test void acceptedTurnTransitionsAndDuplicateReplyCannotRestartGesture(){
        var d=new DialogueMotion();assertTrue(d.start(a,10));assertFalse(d.start(a,11));
        assertEquals(PoseWire.State.LISTENING,d.sample(15,p->true).state());assertEquals(PoseWire.State.THINKING,d.sample(16,p->true).state());
        assertTrue(d.finish(a,20));assertFalse(d.finish(a,21));var speaking=d.sample(21,p->true);assertEquals(PoseWire.State.SPEAKING,speaking.state());assertEquals(DialogueMotion.Gesture.NOD,speaking.gesture());
        assertEquals(PoseWire.State.NEUTRAL,d.sample(60,p->true).state());assertFalse(d.start(a,61));assertFalse(d.finish(a,62));
    }
    @Test void requestIdentityIncludesPlayerAndPublicAvatarArbitrationIsDeterministic(){
        var d=new DialogueMotion();var b=new DialogueMotion.Token(new UUID(0,2),a.request());assertTrue(d.start(b,10));assertTrue(d.start(a,10));assertEquals(a,d.sample(12,p->true).token());
        d.finish(b,15);assertEquals(b,d.sample(16,p->true).token());d.disconnect(b.player());assertEquals(a,d.sample(17,p->true).token());
        assertEquals(PoseWire.State.NEUTRAL,d.sample(18,p->false).state());
    }
    @Test void cancelExpiryAndAllowlistHaveTerminalBehavior(){
        var d=new DialogueMotion();d.start(a,0);assertEquals(PoseWire.State.NEUTRAL,d.sample(1200,p->true).state());assertFalse(d.finish(a,1201));
        d.clear();assertTrue(d.start(a,1202));d.cancel(a);assertEquals(PoseWire.State.NEUTRAL,d.sample(1203,p->true).state());
        assertThrows(IllegalArgumentException.class,()->new DialogueMotion.Intent(PoseWire.State.SPEAKING,DialogueMotion.Gesture.NOD,FaceController.Expression.ANGRY,.2f,a,0));
        assertThrows(IllegalArgumentException.class,()->new DialogueMotion.Intent(PoseWire.State.SPEAKING,DialogueMotion.Gesture.NOD,FaceController.Expression.SMILE,2,a,0));
    }
}
