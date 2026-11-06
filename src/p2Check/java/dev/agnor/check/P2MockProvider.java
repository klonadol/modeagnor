package dev.agnor.check;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import com.google.gson.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
/** Controlled negative scenarios only; never presented as real inference. */
final class P2MockProvider{
    static final AtomicInteger timeouts=new AtomicInteger();
    static void start(){try{
        var http=HttpServer.create(new InetSocketAddress("127.0.0.1",11435),0);var pool=Executors.newFixedThreadPool(4,r->{var t=new Thread(r,"p2-mock");t.setDaemon(true);return t;});http.setExecutor(pool);
        http.createContext("/api/show",e->{byte[] b="{\"capabilities\":[\"completion\"]}".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});
        http.createContext("/api/chat",e->{
            var body=JsonParser.parseString(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();var messages=body.getAsJsonArray("messages");var text=messages.get(messages.size()-1).getAsJsonObject().get("content").getAsString();
            try{if(text.contains("timeout")){timeouts.incrementAndGet();Thread.sleep(6000);}else Thread.sleep(1000);
                byte[] b="{\"done\":true,\"message\":{\"role\":\"assistant\",\"content\":\"LATE_RESPONSE_SENTINEL\"}}".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);
            }catch(Exception ignored){}finally{e.close();}
        });http.start();ServerLifecycleEvents.SERVER_STOPPED.register(server->{http.stop(0);pool.shutdownNow();});
    }catch(Exception e){throw new IllegalStateException("Mock fixture unavailable",e);}}
}
