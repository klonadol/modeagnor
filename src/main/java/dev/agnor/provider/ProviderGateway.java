package dev.agnor.provider;

import com.google.gson.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.util.function.Function;
import static dev.agnor.provider.ProviderResult.Error.*;

/** Session-owned bounded Main Dialogue gateway. No automatic inference retries (uncertain effects/cost).
 * Ollama chat-only adapter: no streaming/tools/structured output claims. Preflight sends no private text.
 */
public final class ProviderGateway implements AutoCloseable {
    private final DialoguePreset preset;
    private final Function<String, String> secrets;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final ThreadPoolExecutor workers;
    private final ScheduledThreadPoolExecutor deadlines = new ScheduledThreadPoolExecutor(1, r -> daemon(r, "agnor-provider-deadline"));
    private final Set<Call> active = ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.atomic.AtomicInteger failures = new java.util.concurrent.atomic.AtomicInteger();
    private volatile long circuitUntil;
    private boolean closed;

    public ProviderGateway(DialoguePreset preset) { this(preset, ref -> HostSecrets.resolve(preset,ref)); }
    public ProviderGateway(DialoguePreset preset, Function<String, String> secrets) {
        this.preset = Objects.requireNonNull(preset); this.secrets = Objects.requireNonNull(secrets);
        workers = new ThreadPoolExecutor(preset.workers(), preset.workers(), 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(preset.queue()), r -> daemon(r, "agnor-main-dialogue"), new ThreadPoolExecutor.AbortPolicy());
        deadlines.setRemoveOnCancelPolicy(true);
    }
    private static Thread daemon(Runnable r, String name) { Thread t = new Thread(r, name); t.setDaemon(true); return t; }
    public String model() { return preset.model(); }

    public synchronized Call submit(ProviderRequest request) {
        Call call = new Call();
        if (closed || !preset.enabled()) { call.finish(CONFIGURATION, ""); return call; }
        if (circuitUntil != 0 && System.nanoTime() - circuitUntil < 0) {
            call.terminal = true;
            call.result.complete(new ProviderResult(UNAVAILABLE, "", 0)); return call;
        }
        active.add(call);
        call.task = new FutureTask<>(() -> { execute(call, request); return null; });
        try {
            workers.execute(call.task);
            call.attachDeadline(deadlines.schedule(() -> call.finish(TIMEOUT, ""), preset.timeout().toMillis(), TimeUnit.MILLISECONDS));
        } catch (RejectedExecutionException e) { call.finish(BUSY, ""); }
        return call;
    }
    public final class Call {
        private final long started = System.nanoTime();
        private final CompletableFuture<ProviderResult> result = new CompletableFuture<>();
        private boolean terminal;
        private volatile FutureTask<Void> task;
        private volatile CompletableFuture<?> transport;
        private volatile ScheduledFuture<?> deadline;
        public CompletionStage<ProviderResult> result() { return result.minimalCompletionStage(); }
        public void cancel() { finish(CANCELLATION, ""); }
        private void finish(ProviderResult.Error error, String text) {
            synchronized (this) {
                if (terminal) return;
                terminal = true;
                if (transport != null) transport.cancel(true);
                if (task != null) { task.cancel(true); workers.remove(task); }
                if (deadline != null) deadline.cancel(false);
                transport = null; task = null; deadline = null;
            }
            active.remove(this);
            if (error == NONE) { failures.set(0); circuitUntil = 0; }
            else if (Set.of(TIMEOUT, NETWORK, UNAVAILABLE, RATE_LIMIT).contains(error) && failures.incrementAndGet() >= 3) {
                circuitUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(30); failures.set(0);
            }
            result.complete(new ProviderResult(error, text, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)));
        }
        private synchronized boolean attach(CompletableFuture<?> next) {
            if (terminal) { next.cancel(true); return false; }
            transport = next; return true;
        }
        private synchronized void attachDeadline(ScheduledFuture<?> next) {
            if (terminal) next.cancel(false);
            else deadline = next;
        }
    }

    private void execute(Call call, ProviderRequest request) {
        try {
            if(preset.adapter()==DialoguePreset.Adapter.OPENROUTER){executeOpenRouter(call,request);return;}
            JsonObject show = new JsonObject(); show.addProperty("model", preset.model());
            JsonObject info = exchange(call, "/api/show", show);
            boolean chat = false, thinking = false, canDisable = false;
            for (var cap : info.getAsJsonArray("capabilities")) {
                chat |= cap.getAsString().equals("completion"); thinking |= cap.getAsString().equals("thinking");
            }
            // Older Ollama versions expose capability + template instead of thinking.values.
            if (info.has("thinking") && info.get("thinking").isJsonObject()) {
                var values = info.getAsJsonObject("thinking").getAsJsonArray("values");
                if (values != null) for (var value : values) canDisable |= value.isJsonPrimitive()
                        && value.getAsJsonPrimitive().isBoolean() && !value.getAsBoolean();
            } else if (info.has("template")) {
                String template = info.get("template").getAsString();
                canDisable = template.contains("enable_thinking") && template.contains("enable_thinking is false");
            }
            if (!chat || (thinking && !canDisable)) { call.finish(UNSUPPORTED_CAPABILITY, ""); return; }
            JsonObject body = new JsonObject(); body.addProperty("model", preset.model()); body.addProperty("stream", false);
            if (thinking || canDisable) body.addProperty("think", false);
            JsonArray messages = new JsonArray();
            for (var message : request.messages()) {
                JsonObject m = new JsonObject(); m.addProperty("role", message.role()); m.addProperty("content", message.content()); messages.add(m);
            }
            body.add("messages", messages);
            JsonObject options = new JsonObject(); options.addProperty("num_predict", preset.maxOutputTokens()); options.addProperty("num_ctx", 20000);
            body.add("options", options);
            JsonObject response = exchange(call, "/api/chat", body);
            JsonObject message = response.getAsJsonObject("message");
            if (!response.get("done").isJsonPrimitive() || !response.get("done").getAsJsonPrimitive().isBoolean()
                    || !response.get("done").getAsBoolean() || !string(message, "role").equals("assistant")
                    || (message.has("thinking") && !message.get("thinking").getAsString().isBlank())
                    || (message.has("tool_calls") && !message.getAsJsonArray("tool_calls").isEmpty())) {
                call.finish(SCHEMA_VIOLATION, ""); return;
            }
            finishText(call,string(message, "content"));
        } catch (TransportFailure e) { call.finish(e.error, ""); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); call.finish(CANCELLATION, ""); }
        catch (Exception e) { call.finish(MALFORMED_RESPONSE, ""); }
    }
    private void finishText(Call call,String value){
            String text = value.strip();
            if (text.isEmpty() || text.length() > 4096 || text.contains("<think>") || text.contains("</think>")
                    || text.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t')) {
                call.finish(SCHEMA_VIOLATION, ""); return;
            }
            call.finish(NONE, text);
    }
    private void executeOpenRouter(Call call,ProviderRequest request)throws Exception{
        JsonObject metadata=exchange(call,"/api/v1/models/"+preset.model()+"/endpoints",null).getAsJsonObject("data");
        JsonObject architecture=metadata.getAsJsonObject("architecture");
        if(!string(metadata,"id").equals(preset.model())||!contains(architecture,"input_modalities","text")
                ||!contains(architecture,"output_modalities","text")||metadata.getAsJsonArray("endpoints").isEmpty()){
            call.finish(UNSUPPORTED_CAPABILITY,"");return;
        }
        if(metadata.has("reasoning")&&metadata.get("reasoning").isJsonObject()&&metadata.getAsJsonObject("reasoning").has("mandatory")
                &&metadata.getAsJsonObject("reasoning").get("mandatory").getAsBoolean()){call.finish(UNSUPPORTED_CAPABILITY,"");return;}
        JsonObject body=new JsonObject();body.addProperty("model",preset.model());body.addProperty("stream",false);body.addProperty("max_tokens",preset.maxOutputTokens());
        JsonObject reasoning=new JsonObject();reasoning.addProperty("enabled",false);body.add("reasoning",reasoning);
        JsonArray messages=new JsonArray();for(var m:request.messages()){JsonObject item=new JsonObject();item.addProperty("role",m.role());item.addProperty("content",m.content());messages.add(item);}body.add("messages",messages);
        JsonObject response=exchange(call,"/api/v1/chat/completions",body);
        if(response.has("error")){call.finish(UNAVAILABLE,"");return;}
        JsonArray choices=response.getAsJsonArray("choices");if(choices.size()!=1){call.finish(SCHEMA_VIOLATION,"");return;}
        JsonObject choice=choices.get(0).getAsJsonObject(),message=choice.getAsJsonObject("message");
        if(!string(choice,"finish_reason").equals("stop")||!string(message,"role").equals("assistant")
                ||(message.has("tool_calls")&&!message.get("tool_calls").isJsonNull()&&!message.getAsJsonArray("tool_calls").isEmpty())
                ||(message.has("reasoning")&&!message.get("reasoning").isJsonNull()&&!message.get("reasoning").getAsString().isBlank())
                ||(message.has("reasoning_details")&&!message.get("reasoning_details").isJsonNull()&&!message.getAsJsonArray("reasoning_details").isEmpty())){
            call.finish(SCHEMA_VIOLATION,"");return;
        }
        finishText(call,string(message,"content"));
    }
    private static boolean contains(JsonObject object,String key,String value){var array=object.getAsJsonArray(key);return array!=null&&java.util.stream.StreamSupport.stream(array.spliterator(),false).anyMatch(v->v.isJsonPrimitive()&&v.getAsString().equals(value));}
    private static String string(JsonObject object, String name) {
        var value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new JsonParseException("Invalid string");
        return value.getAsString();
    }
    private JsonObject exchange(Call call, String path, JsonObject body) throws Exception {
        if (call.result.isDone()) throw new InterruptedException();
        var builder = HttpRequest.newBuilder(preset.endpoint().resolve(path)).timeout(preset.timeout()).header("Content-Type", "application/json");
        if(body==null)builder.GET();else builder.POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        String key=null;
        if (!preset.secretEnv().isEmpty()||!preset.secretFile().isEmpty()) {
            try { key = secrets.apply(preset.secretEnv()); }
            catch (RuntimeException e) { throw new TransportFailure(CONFIGURATION); }
            if (key == null || key.isBlank() || key.contains("\r") || key.contains("\n")) throw new TransportFailure(CONFIGURATION);
            builder.header("Authorization", "Bearer " + key);
        }
        var future = http.sendAsync(builder.build(), response -> new LimitedBody(preset.maxResponseBytes()));
        if (!call.attach(future)) throw new InterruptedException();
        HttpResponse<byte[]> response;
        try { response = future.get(); }
        catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof HttpTimeoutException) throw new TransportFailure(TIMEOUT);
            if (cause instanceof BodyLimit) throw new TransportFailure(MALFORMED_RESPONSE);
            throw new TransportFailure(NETWORK);
        }
        int status = response.statusCode();
        if (status != 200) throw new TransportFailure(switch (status) {
            case 401 -> AUTHENTICATION; case 403 -> AUTHORIZATION; case 429 -> RATE_LIMIT;
            case 404 -> CONFIGURATION; case 400, 422 -> UNSUPPORTED_CAPABILITY;
            default -> UNAVAILABLE;
        });
        String payload=new String(response.body(), StandardCharsets.UTF_8);
        if(key!=null&&payload.contains(key))throw new TransportFailure(SCHEMA_VIOLATION);
        return JsonParser.parseString(payload).getAsJsonObject();
    }
    private static final class TransportFailure extends Exception {
        final ProviderResult.Error error; TransportFailure(ProviderResult.Error error) { this.error = error; }
    }
    private static final class BodyLimit extends RuntimeException { }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        private final int limit; private Flow.Subscription subscription;
        LimitedBody(int limit) { this.limit = limit; }
        public CompletionStage<byte[]> getBody() { return body; }
        public void onSubscribe(Flow.Subscription subscription) { this.subscription = subscription; subscription.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer b : buffers) {
                if ((long) bytes.size() + b.remaining() > limit) { subscription.cancel(); body.completeExceptionally(new BodyLimit()); return; }
                byte[] part = new byte[b.remaining()]; b.get(part); bytes.writeBytes(part);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { body.completeExceptionally(error); }
        public void onComplete() { body.complete(bytes.toByteArray()); }
    }
    @Override public synchronized void close() {
        closed = true; List.copyOf(active).forEach(Call::cancel); workers.shutdownNow(); deadlines.shutdownNow(); http.shutdownNow();
    }
}
