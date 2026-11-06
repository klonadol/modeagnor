package dev.agnor.motion;

import dev.agnor.network.PoseWire;
import java.util.*;
import java.util.function.Predicate;

/** Ephemeral public presentation only: no text, memory, provider credentials or persisted dialogue. */
public final class DialogueMotion {
    public enum Gesture { NONE, NOD }
    public record Token(UUID player,UUID request){public Token{if(player==null||request==null||player.equals(new UUID(0,0))||request.equals(new UUID(0,0)))throw new IllegalArgumentException("Dialogue motion token");}}
    public record Intent(PoseWire.State state,Gesture gesture,FaceController.Expression expression,float strength,Token token,long phaseStart){
        public Intent {Objects.requireNonNull(state);Objects.requireNonNull(gesture);if(expression!=FaceController.Expression.NEUTRAL&&expression!=FaceController.Expression.SMILE)throw new IllegalArgumentException("Expression not allowed by dialogue adapter");JointTransform.weight(strength);if(phaseStart<0)throw new IllegalArgumentException("Phase tick");}
    }
    private record Turn(Token token,long start,long speaking){boolean finished(){return speaking>=0;}}
    private final Map<Token,Turn> active=new HashMap<>();private final LinkedHashSet<Token> completed=new LinkedHashSet<>();
    public boolean start(Token token,long tick){
        if(tick<0)throw new IllegalArgumentException("Tick");if(active.containsKey(token)||completed.contains(token))return false;
        if(active.size()>=16)return false;active.put(token,new Turn(token,tick,-1));return true;
    }
    public boolean finish(Token token,long tick){
        if(tick<0)throw new IllegalArgumentException("Tick");var t=active.get(token);if(t==null||t.finished()||tick<t.start)return false;
        active.put(token,new Turn(token,t.start,tick));return true;
    }
    public void cancel(Token token){active.remove(token);remember(token);}
    public void disconnect(UUID player){for(var token:List.copyOf(active.keySet()))if(token.player.equals(player))cancel(token);}
    public void clear(){active.clear();completed.clear();}
    public Intent sample(long tick,Predicate<UUID> available){
        if(tick<0)throw new IllegalArgumentException("Tick");
        for(var t:List.copyOf(active.values()))if(!available.test(t.token.player)||(t.finished()?tick-t.speaking>=40:tick-t.start>=1200))cancel(t.token);
        Turn selected=null;int priority=-1;
        for(var t:active.values()){
            int p=t.finished()?3:tick-t.start>=6?2:1;
            if(selected==null||p>priority||(p==priority&&(t.start<selected.start||(t.start==selected.start&&compare(t.token,selected.token)<0)))){selected=t;priority=p;}
        }
        if(selected==null)return new Intent(PoseWire.State.NEUTRAL,Gesture.NONE,FaceController.Expression.NEUTRAL,0,null,tick);
        if(selected.finished())return new Intent(PoseWire.State.SPEAKING,Gesture.NOD,FaceController.Expression.SMILE,.25f,selected.token,selected.speaking);
        return new Intent(priority==2?PoseWire.State.THINKING:PoseWire.State.LISTENING,Gesture.NONE,FaceController.Expression.NEUTRAL,0,selected.token,selected.start+(priority==2?6:0));
    }
    private void remember(Token token){completed.add(token);while(completed.size()>128)completed.remove(completed.iterator().next());}
    private static int compare(Token a,Token b){int c=a.player.compareTo(b.player);return c==0?a.request.compareTo(b.request):c;}
}
