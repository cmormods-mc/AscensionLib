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

    private static final Map<String, String> STAT_NAMES = Map.of("atk", "Attack", "def", "Defense", "spa", "Sp. Atk", "spd", "Sp. Def",
            "spe", "Speed", "accuracy", "accuracy");
    private static final Map<String, String> STATUS_NAMES = Map.of("brn", "a burn", "psn", "poison", "par", "paralysis", "confusion", "confusion");

    /** The effect in a plain sentence fragment, with the numbers a holder at this benefit share (1 to 100) actually gets. */
    public String describe(int benefitPercent) {
        java.util.function.IntUnaryOperator scaled = value -> Math.max(1, Math.round(value * benefitPercent / 100f));
        return switch (op) {
            case "status" -> scaled.applyAsInt((Integer) args.get("chance")) + "% chance to inflict " + STATUS_NAMES.get(args.get("status"));
            case "foeStage" -> "lowers the foe's " + STAT_NAMES.get(args.get("stat")) + " by " + -(Integer) args.get("delta");
            case "selfStage" -> "raises its own " + STAT_NAMES.get(args.get("stat")) + " by " + args.get("delta") + " (up to +" + args.get("cap") + ")";
            case "chip" -> "deals " + scaled.applyAsInt((Integer) args.get("pct")) + "% of the foe's max HP";
            case "drain" -> "drains " + scaled.applyAsInt((Integer) args.get("pct")) + "% of the foe's max HP";
            case "heal" -> "heals " + scaled.applyAsInt((Integer) args.get("pct")) + "% of its max HP";
            case "cleanse" -> "cures its own status condition";
            case "ward" -> "blocks the next status condition a foe inflicts";
            case "mimic" -> "copies the foe's highest raised stat (up to +2)";
            case "strip" -> "strips the foe's raised Defense and Sp. Def";
            case "unresistedNext" -> "its next move ignores type resistance";
            case "shield" -> "the next hit it takes is " + scaled.applyAsInt((Integer) args.get("pct")) + "% softer";
            case "boostNext" -> "its next move deals " + scaled.applyAsInt((Integer) args.get("pct")) + "% more";
            case "healBoost" -> "its healing moves heal " + scaled.applyAsInt((Integer) args.get("pct")) + "% more for " + args.get("turns") + " turns";
            case "hide" -> "takes " + scaled.applyAsInt((Integer) args.get("pct")) + "% less damage for " + args.get("turns") + " turns";
            case "refine" -> "its drawback is " + scaled.applyAsInt((Integer) args.get("pct")) + "% weaker for " + args.get("turns") + " turns";
            default -> op;
        };
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
