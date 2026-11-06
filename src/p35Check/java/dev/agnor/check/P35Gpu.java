package dev.agnor.check;
import com.google.gson.*;
import org.lwjgl.opengl.*;
import java.util.*;
/** Async whole-world-pass timer, not a claim of skinning-only cost. Never waits for the GPU. */
final class P35Gpu {
    final Map<String,List<Long>> gpu=new HashMap<>(),cpu=new HashMap<>();final int[] queries=new int[4];final boolean[] pending=new boolean[4];final String[] labels=new String[4];
    boolean initialized,available;String renderer="",phase;int active=-1,depth;long started;
    void begin(String label){
        if(++depth!=1)return;
        if(!initialized){initialized=true;available=GL.getCapabilities().OpenGL33||GL.getCapabilities().GL_ARB_timer_query;renderer=GL11C.glGetString(GL11C.GL_RENDERER);if(available)for(int i=0;i<4;i++)queries[i]=GL15C.glGenQueries();}
        if(available)for(int i=0;i<4;i++)if(pending[i]&&GL15C.glGetQueryObjecti(queries[i],GL15C.GL_QUERY_RESULT_AVAILABLE)!=0){add(gpu,labels[i],GL33C.glGetQueryObjectui64(queries[i],GL15C.GL_QUERY_RESULT));pending[i]=false;}
        phase=label;started=label==null?0:System.nanoTime();active=-1;
        if(label!=null&&available&&GL15C.glGetQueryi(GL33C.GL_TIME_ELAPSED,GL15C.GL_CURRENT_QUERY)==0)for(int i=0;i<4;i++)if(!pending[i]){active=i;GL15C.glBeginQuery(GL33C.GL_TIME_ELAPSED,queries[i]);break;}
    }
    void end(){if(depth<=0||--depth!=0)return;if(started!=0)add(cpu,phase,System.nanoTime()-started);if(active>=0){GL15C.glEndQuery(GL33C.GL_TIME_ELAPSED);pending[active]=true;labels[active]=phase;active=-1;}}
    private void add(Map<String,List<Long>> map,String label,long value){var list=map.computeIfAbsent(label,k->new ArrayList<>());if(list.size()<2000)list.add(value);}
    JsonObject report(){var result=new JsonObject();result.addProperty("available",available);result.addProperty("renderer",renderer);var phases=new JsonObject();for(String p:List.of("BASELINE","ONE","FIVE")){var data=new JsonObject();data.add("worldGpu",P35Stats.nanos(gpu.getOrDefault(p,List.of())));data.add("worldCpu",P35Stats.nanos(cpu.getOrDefault(p,List.of())));phases.add(p,data);}result.add("phases",phases);return result;}
    void close(){if(available){if(active>=0)GL15C.glEndQuery(GL33C.GL_TIME_ELAPSED);for(int id:queries)if(id!=0)GL15C.glDeleteQueries(id);}active=-1;}
}
