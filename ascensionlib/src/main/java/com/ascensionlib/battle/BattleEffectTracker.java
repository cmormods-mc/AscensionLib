package com.ascensionlib.battle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Follows the three effects that otherwise leave no sign on screen from the Showdown protocol lines (the same source as {@link BleedTracker}). The wording
 * is the contract with {@code ascension-fx.js}; a line that does not match is ignored, so a wording change on either side shows no badge rather than a wrong one:
 * <ul>
 * <li>Momentum stacks last until the holder leaves the field: {@code X's Momentum rises! (stack 2)} and {@code X's Momentum faded.} (a standing badge);</li>
 * <li>Wardstone ({@code X's Wardstone turned the condition aside!}) and Ensnaring ({@code X was ensnared!}) happen once (a flash).</li>
 * </ul>
 * Pure text in, pure data out.
 */
public final class BattleEffectTracker {
    private static final Pattern RISES = Pattern.compile("^\\|-message\\|(.+?)'s Momentum rises! \\(stack (\\d+)\\)$");
    private static final Pattern FADED = Pattern.compile("^\\|-message\\|(.+?)'s Momentum faded\\.$");
    private static final Pattern WARD = Pattern.compile("^\\|-message\\|(.+?)'s Wardstone turned the condition aside!$");
    private static final Pattern SNARE = Pattern.compile("^\\|-message\\|(.+?) was ensnared!$");

    /** A one-time event: the Pokemon it happened to and {@code wardstone} or {@code ensnaring}. */
    public record Proc(String name, String kind) {}

    /** A Pokemon holding Momentum and how many stacks. */
    public record Momentum(String name, int stacks) {}

    private final Map<String, Integer> momentum = new LinkedHashMap<>();
    private final List<Proc> procs = new ArrayList<>();
    private boolean changed;

    public void feed(String chunk) {
        if (chunk == null) return;
        for (String raw : chunk.split("\n")) {
            String line = raw.strip();
            if (!line.startsWith("|")) line = "|" + line;
            var rises = RISES.matcher(line);
            if (rises.matches()) {
                momentum.put(rises.group(1), Integer.parseInt(rises.group(2)));
                changed = true;
                continue;
            }
            var faded = FADED.matcher(line);
            if (faded.matches()) {
                if (momentum.remove(faded.group(1)) != null) changed = true;
                continue;
            }
            var ward = WARD.matcher(line);
            if (ward.matches()) { procs.add(new Proc(ward.group(1), "wardstone")); changed = true; continue; }
            var snare = SNARE.matcher(line);
            if (snare.matches()) { procs.add(new Proc(snare.group(1), "ensnaring")); changed = true; continue; }
            if (line.startsWith("|win|") || line.startsWith("|tie")) {
                if (!momentum.isEmpty()) changed = true;
                momentum.clear();
            }
        }
    }

    public List<Momentum> momentum() {
        var out = new ArrayList<Momentum>();
        momentum.forEach((name, stacks) -> out.add(new Momentum(name, stacks)));
        return out;
    }

    /** The one-time events since the last call; they are not repeated. */
    public List<Proc> takeProcs() {
        var out = List.copyOf(procs);
        procs.clear();
        return out;
    }

    /** Whether anything shown would differ since the last call; clears the flag. */
    public boolean takeChanged() {
        boolean was = changed;
        changed = false;
        return was;
    }

    public boolean idle() { return momentum.isEmpty(); }
}
