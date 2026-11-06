package dev.agnor.provider;
import java.net.URI;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;
class CloudLiveCheck {
    @Test void selectedOpenRouterCredential()throws Exception{
        assumeTrue(Boolean.getBoolean("agnor.cloudLive"));
        var preset=new DialoguePreset(1,true,URI.create("https://openrouter.ai"),"google/gemini-2.5-flash-lite",Duration.ofSeconds(90),1,8,64,65536,"",
                DialoguePreset.Adapter.OPENROUTER,Path.of(System.getProperty("user.home"),"Desktop","silly","API (1).txt").toString(),"OpenRouter");
        try(var gateway=new ProviderGateway(preset)){
            var r=gateway.submit(new ProviderRequest(UUID.randomUUID(),List.of(new ProviderRequest.Message("user","Reply with exactly: CLOUD_READY")))).result().toCompletableFuture().get(95,TimeUnit.SECONDS);
            Files.createDirectories(Path.of("build/p2-evidence"));Files.writeString(Path.of("build/p2-evidence/cloud-gateway.txt"),"provider=OpenRouter\nmodel="+preset.model()+"\n"+r+"\nnonempty="+!r.text().isBlank());
            assertEquals(ProviderResult.Error.NONE,r.error());assertTrue(r.text().contains("CLOUD_READY"));
        }
    }
}
