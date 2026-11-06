package dev.agnor.provider;

/** Terminal, sanitized result. No provider payloads, headers or exception causes in failures. */
public record ProviderResult(Error error, String text, long elapsedMillis) {
    public enum Error { NONE, CONFIGURATION, AUTHENTICATION, AUTHORIZATION, RATE_LIMIT, TIMEOUT,
        NETWORK, UNAVAILABLE, UNSUPPORTED_CAPABILITY, MALFORMED_RESPONSE, SCHEMA_VIOLATION, CANCELLATION, BUSY }
    public ProviderResult {
        if (error == null || text == null || (error != Error.NONE && !text.isEmpty())) throw new IllegalArgumentException();
    }
    @Override public String toString() { return "ProviderResult[error=" + error + ",elapsedMillis=" + elapsedMillis + "]"; }
}
