package dev.agnor.provider;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static dev.agnor.provider.ProviderResult.Error.*;

class ProviderGatewayTest {
    HttpServer server;
    volatile int status = 200;
    volatile String payload = "{\"message\":{\"role\":\"assistant\",\"content\":\"Hello\"},\"done\":true}";
    volatile String received = "";
    volatile String auth = "";
    final java.util.concurrent.atomic.AtomicInteger chats = new java.util.concurrent.atomic.AtomicInteger();
    volatile long delay;
    volatile String info = "{\"capabilities\":[\"completion\",\"thinking\"],\"thinking\":{\"values\":[true,false]}}";
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/show", exchange -> {
            received = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = info.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.createContext("/api/chat", exchange -> {
            chats.incrementAndGet();
            received = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            auth = exchange.getRequestHeaders().getFirst("Authorization");
            try { Thread.sleep(delay); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            try { exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes); }
            finally { exchange.close(); }
        });
        server.start();
    }
    @AfterEach void stop() { server.stop(0); }
    DialoguePreset preset(long timeout) {
        return new DialoguePreset(1, true, URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                "test", Duration.ofMillis(timeout), 1, 1, 64, 4096, "AGNOR_TEST_KEY");
    }
    ProviderRequest request() { return new ProviderRequest(UUID.randomUUID(), List.of(new ProviderRequest.Message("user", "private"))); }
    ProviderResult await(ProviderGateway.Call call) throws Exception { return call.result().toCompletableFuture().get(5, TimeUnit.SECONDS); }
    @Test void realHttpContractDisablesReasoningAndRedacts() throws Exception {
        try (var gateway = new ProviderGateway(preset(3000), env -> "secret-fixture")) {
            var result = await(gateway.submit(request()));
            assertEquals(NONE, result.error()); assertEquals("Hello", result.text());
            assertTrue(received.contains("\"think\":false")); assertTrue(received.contains("\"stream\":false"));
            assertEquals("Bearer secret-fixture", auth);
            assertFalse(result.toString().contains("Hello")); assertFalse(request().toString().contains("private"));
        }
    }
    @Test void finiteErrorsNeverReturnServerPayloadOrRetry() throws Exception {
        for (var entry : Map.of(401, AUTHENTICATION, 403, AUTHORIZATION, 429, RATE_LIMIT, 500, UNAVAILABLE, 404, CONFIGURATION).entrySet()) {
            int before = chats.get();
            status = entry.getKey(); payload = "secret-private-provider-error";
            try (var gateway = new ProviderGateway(preset(3000), env -> "test")) {
                var result = await(gateway.submit(request()));
                assertEquals(entry.getValue(), result.error()); assertEquals("", result.text());
                assertEquals(before + 1, chats.get());
            }
        }
    }
    @Test void circuitBreakerStopsRepeatedTransientCalls() throws Exception {
        status = 503;
        try (var gateway = new ProviderGateway(preset(3000), env -> "test")) {
            for (int i = 0; i < 4; i++) assertEquals(UNAVAILABLE, await(gateway.submit(request())).error());
            assertEquals(3, chats.get());
        }
    }
    @Test void refusedEndpointIsNetworkFailure() throws Exception {
        var config = preset(3000); server.stop(0);
        try (var gateway = new ProviderGateway(config, env -> "test")) {
            assertEquals(NETWORK, await(gateway.submit(request())).error());
        }
    }
    @Test void malformedIncompleteOversizedAndReasoningAreRejected() throws Exception {
        for (String invalid : List.of("{}", "[]", "not-json", "x".repeat(5000),
                "{\"done\":false,\"message\":{\"role\":\"assistant\",\"content\":\"partial\"}}",
                "{\"done\":true,\"message\":{\"role\":\"assistant\",\"content\":\"answer\",\"thinking\":\"private\"}}")) {
            payload = invalid;
            try (var gateway = new ProviderGateway(preset(3000), env -> "test")) {
                assertNotEquals(NONE, await(gateway.submit(request())).error());
            }
        }
    }
    @Test void queueCancellationDeadlineAndCloseAreBounded() throws Exception {
        delay = 1000;
        try (var gateway = new ProviderGateway(preset(300), env -> "test")) {
            var first = gateway.submit(request()); var queued = gateway.submit(request());
            assertEquals(BUSY, await(gateway.submit(request())).error());
            queued.cancel(); assertEquals(CANCELLATION, await(queued).error());
            assertEquals(TIMEOUT, await(first).error());
            var next = gateway.submit(request()); gateway.close();
            assertEquals(CANCELLATION, await(next).error());
            assertEquals(CONFIGURATION, await(gateway.submit(request())).error());
        }
    }
    @Test void unsupportedThinkingDoesNotSendPrivateChat() throws Exception {
        info = "{\"capabilities\":[\"completion\",\"thinking\"],\"thinking\":{\"values\":[\"high\"]}}";
        try (var gateway = new ProviderGateway(preset(3000), env -> "test")) {
            assertEquals(UNSUPPORTED_CAPABILITY, await(gateway.submit(request())).error());
            assertFalse(received.contains("private"));
        }
    }
    @Test void configurationValidationAndDisabledDefault(@TempDir Path root) throws Exception {
        assertFalse(DialoguePreset.load(root).enabled());
        Files.createDirectories(root.resolve("agnor"));
        Files.writeString(root.resolve("agnor/main-dialogue.properties"), "endpoint=https://secret@example.com/?key=secret\nenabled=true");
        var error = assertThrows(java.io.IOException.class, () -> DialoguePreset.load(root));
        assertFalse(error.toString().contains("secret")); assertNull(error.getCause());
        try (var gateway = new ProviderGateway(preset(3000), env -> null)) {
            assertEquals(CONFIGURATION, await(gateway.submit(request())).error());
        }
    }
}
