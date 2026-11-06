package dev.agnor.content;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;

/** Strict, bounded JSON: duplicate fields and malformed UTF-8 are errors. */
public final class PackJson {
    public static JsonElement read(byte[] bytes) throws IOException {
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            try (var reader = new JsonReader(new StringReader(text))) {
                reader.setLenient(false);
                var result = value(reader, 0);
                if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Trailing JSON data");
                return result;
            }
        } catch (RuntimeException e) { throw new IOException("Invalid pack JSON", e); }
    }
    private static JsonElement value(JsonReader reader, int depth) throws IOException {
        if (depth > 32) throw new IOException("Pack JSON nesting limit");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                var object = new JsonObject(); reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (object.has(key)) throw new IOException("Duplicate JSON field");
                    object.add(key, value(reader, depth + 1));
                }
                reader.endObject(); yield object;
            }
            case BEGIN_ARRAY -> {
                var array = new JsonArray(); reader.beginArray();
                while (reader.hasNext()) array.add(value(reader, depth + 1));
                reader.endArray(); yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new java.math.BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw new IOException("Invalid JSON token");
        };
    }
}
