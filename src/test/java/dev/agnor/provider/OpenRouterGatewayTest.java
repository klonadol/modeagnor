package dev.agnor.provider;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static dev.agnor.provider.ProviderResult.Error.*;

class OpenRouterGatewayTest{
    @Test void cloudContractNoStreamingAndSafeFailures()throws Exception{
        var http=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        String[] output={"{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"hello\"}}]}"},input={""};
        http.createContext("/api/v1/models/test/model/endpoints",e->{assertEquals("GET",e.getRequestMethod());byte[] bytes="{\"data\":{\"id\":\"test/model\",\"architecture\":{\"input_modalities\":[\"text\"],\"output_modalities\":[\"text\"]},\"endpoints\":[{}]}}".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);e.close();});
        http.createContext("/api/v1/chat/completions",e->{input[0]=new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);byte[] bytes=output[0].getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);e.close();});http.start();
        var preset=new DialoguePreset(1,true,URI.create("http://127.0.0.1:"+http.getAddress().getPort()),"test/model",Duration.ofSeconds(3),1,1,64,4096,"TEST_KEY",DialoguePreset.Adapter.OPENROUTER,"","");
        try(var gateway=new ProviderGateway(preset,env->"fixture-credential")){
            var request=new ProviderRequest(UUID.randomUUID(),List.of(new ProviderRequest.Message("user","private")));
            assertEquals(NONE,gateway.submit(request).result().toCompletableFuture().get(5,TimeUnit.SECONDS).error());
            assertTrue(input[0].contains("\"enabled\":false"));assertTrue(input[0].contains("\"stream\":false"));assertTrue(input[0].contains("\"max_tokens\":64"));
            output[0]=output[0].replace("hello","fixture-credential");var result=gateway.submit(request).result().toCompletableFuture().get(5,TimeUnit.SECONDS);
            assertEquals(SCHEMA_VIOLATION,result.error());assertTrue(result.text().isEmpty());assertFalse(result.toString().contains("fixture-credential"));
            output[0]="{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"role\":\"assistant\",\"content\":\"partial\"}}]}";
            assertEquals(SCHEMA_VIOLATION,gateway.submit(request).result().toCompletableFuture().get(5,TimeUnit.SECONDS).error());
        }finally{http.stop(0);}
    }
    @Test void namedHostSectionOnlyAndNoCredentialCopy(@TempDir Path root)throws Exception{
        var file=root.resolve("keys.txt");String contents="DeepSeek:\nother-fixture-key\nOpenRouter:\nsk-or-v1-"+"x".repeat(32)+"\nGroq:\nother-unused-key\n";Files.writeString(file,contents);
        var p=new DialoguePreset(1,true,URI.create("https://openrouter.ai"),"test/model",Duration.ofSeconds(3),1,1,64,4096,"",DialoguePreset.Adapter.OPENROUTER,file.toString(),"OpenRouter");
        assertEquals("sk-or-v1-"+"x".repeat(32),HostSecrets.resolve(p,""));assertEquals(contents,Files.readString(file));assertFalse(p.toString().contains("keys.txt"));
        var wrong=new DialoguePreset(1,true,URI.create("https://example.com"),"test/model",Duration.ofSeconds(3),1,1,64,4096,"",DialoguePreset.Adapter.OPENROUTER,file.toString(),"OpenRouter");
        assertThrows(IllegalArgumentException.class,()->HostSecrets.resolve(wrong,""));
    }
}
