package com.cobbleascend.domain.v1;

import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * One effect of a Transcendent's twist, fired on its signature's pulse (docs/TRANSCENDENT-POWERS.md). The vocabulary is fixed (16
 * operations) so that the 25 motif twists and the 80 bespoke recipe twists are data. {@code args} holds the operation's own fields
 * (whole numbers and short strings only); everything is validated here and again by the battle module.
 */
public record PulseOp(String op, Map<String, Object> args) {
    private static final Set<String> STATS = Set.of("atk", "def", "spa", "spd", "spe");
    private static final Set<String> FOE_STATS = Set.of("atk", "def", "spa", "spd", "spe", "accuracy");

    public PulseOp {
        args = java.util.Collections.unmodifiableMap(new TreeMap<>(args));
        switch (op) {
            case "status" -> {
                only(args, "status", "chance");
                oneOf(args, "status", Set.of("brn", "psn", "par", "confusion"));
                range(args, "chance", 1, 100);
            }
            case "foeStage" -> {
                only(args, "stat", "delta");
                oneOf(args, "stat", FOE_STATS);
                range(args, "delta", -2, -1);
            }
            case "selfStage" -> {
                only(args, "stat", "delta", "cap");
                oneOf(args, "stat", STATS);
                range(args, "delta", 1, 2);
                range(args, "cap", 1, 6);
            }
            case "chip", "drain", "heal" -> {
                only(args, "pct");
                range(args, "pct", 1, 10);
            }
            case "cleanse", "ward", "mimic", "strip", "unresistedNext" -> only(args);
            case "shield", "boostNext" -> {
                only(args, "pct");
                range(args, "pct", 1, 50);
            }
            case "healBoost" -> {
                only(args, "pct", "turns");
                range(args, "pct", 1, 100);
                range(args, "turns", 1, 5);
            }
            case "hide" -> {
                only(args, "pct", "turns");
                range(args, "pct", 1, 30);
                range(args, "turns", 1, 5);
            }
            case "refine" -> {
                only(args, "pct", "turns");
                range(args, "pct", 1, 50);
                range(args, "turns", 1, 5);
            }
            default -> throw new IllegalArgumentException("Unknown pulse operation: " + op);
        }
    }

    private static void only(Map<String, Object> args, String... fields) {
        if (!args.keySet().equals(Set.copyOf(Arrays.asList(fields))))
            throw new IllegalArgumentException("Fields " + args.keySet() + " do not match " + Arrays.toString(fields));
    }

    private static void oneOf(Map<String, Object> args, String field, Set<String> allowed) {
        if (!(args.get(field) instanceof String value) || !allowed.contains(value))
            throw new IllegalArgumentException("Invalid " + field + ": " + args.get(field));
    }

    private static void range(Map<String, Object> args, String field, int low, int high) {
        if (!(args.get(field) instanceof Integer value) || value < low || value > high)
            throw new IllegalArgumentException("Invalid " + field + ": " + args.get(field));
    }

    /** Reads an operation from its JSON form: {@code {"op":"status","status":"brn","chance":30}}. */
    public static PulseOp fromJson(JsonObject json) {
        var args = new TreeMap<String, Object>();
        String op = null;
        for (var entry : json.entrySet()) {
            if (entry.getKey().equals("op")) {
                op = Json.string(entry.getValue());
            } else if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString()) {
                args.put(entry.getKey(), entry.getValue().getAsString());
            } else {
                args.put(entry.getKey(), Json.integer(entry.getValue()));
            }
        }
        if (op == null) throw new IllegalArgumentException("A pulse operation needs an op");
        return new PulseOp(op, args);
    }

    /** The JSON form, as sent in the battle payload. */
    public JsonObject toJson() {
        var json = new JsonObject();
        json.addProperty("op", op);
        for (var entry : args.entrySet()) {
            if (entry.getValue() instanceof Integer number) json.addProperty(entry.getKey(), number);
            else json.addProperty(entry.getKey(), (String) entry.getValue());
        }
        return json;
    }
}
