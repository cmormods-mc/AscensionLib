package com.cobbleascend.domain.v1;

import com.google.gson.*;
import java.util.Set;
import java.util.UUID;

/** Strict JSON accessors. Every failure is an IllegalArgumentException so callers can preserve the source. */
final class Json {
    private Json() {}

    static JsonObject parseObject(String text) {
        try {
            var element = JsonParser.parseString(text);
            if (!element.isJsonObject()) throw new IllegalArgumentException("Expected a JSON object");
            return element.getAsJsonObject();
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("Malformed JSON", exception);
        }
    }

    static void keys(JsonObject object, Set<String> required, Set<String> optional) {
        for (String key : required) if (!object.has(key) || object.get(key).isJsonNull())
            throw new IllegalArgumentException("Missing field: " + key);
        for (String key : object.keySet()) if (!required.contains(key) && !optional.contains(key))
            throw new IllegalArgumentException("Unknown field: " + key);
    }

    static int integer(JsonElement element) {
        try {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())
                throw new IllegalArgumentException("Expected an integer");
            return element.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Expected an integer", exception);
        }
    }

    static long longValue(JsonElement element) {
        try {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())
                throw new IllegalArgumentException("Expected an integer");
            return element.getAsBigDecimal().longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Expected an integer", exception);
        }
    }

    static String string(JsonElement element) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Expected a string");
        return element.getAsString();
    }

    static UUID uuid(JsonElement element) { return UUID.fromString(string(element)); }

    static JsonObject object(JsonElement element) {
        if (!element.isJsonObject()) throw new IllegalArgumentException("Expected an object");
        return element.getAsJsonObject();
    }

    static JsonArray array(JsonElement element) {
        if (!element.isJsonArray()) throw new IllegalArgumentException("Expected an array");
        return element.getAsJsonArray();
    }
}
