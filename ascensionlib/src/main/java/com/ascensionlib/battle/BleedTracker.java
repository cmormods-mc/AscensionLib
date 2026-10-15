package com.ascensionlib.battle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Follows the bleed on each Pokemon of one battle from the Showdown protocol lines Cobblemon keeps ({@code PokemonBattle.getShowdownMessages}).
 * The simulator code ({@code ascension-fx.js}, {@code inflictBleed} and the residual step) writes plain {@code -message} lines whose wording is
 * the contract this reads: {@code X is bleeding! (stack 2, 3 turns)}, {@code X is still bleeding! (stack 3, 3 turns)} and
 * {@code X's bleeding stopped.}. Cobblemon has no status icon for a custom condition, so this is what lets the overlay show one. Pure text in,
 * pure data out; a line that does not match is ignored, so a wording change on either side shows no badge rather than a wrong one.
 */
public final class BleedTracker {
    private static final Pattern START = Pattern.compile("^\\|-message\\|(.+?) is (?:still )?bleeding! \\(stack (\\d+), (\\d+) turns?\\)$");
    private static final Pattern STOP = Pattern.compile("^\\|-message\\|(.+?)'s bleeding stopped\\.$");
    private static final Pattern TURN = Pattern.compile("^\\|turn\\|(\\d+)$");

    /** One bleeding Pokemon: its name as the simulator printed it, the stacks and the turns still to tick. */
    public record Bleed(String name, int stacks, int turnsLeft) {}

    private static final class State {
        int stacks;
        int turns;
        int appliedTurn;
    }

    private final Map<String, State> bleeding = new LinkedHashMap<>();
    private int turn;
    private boolean changed;

    /** Reads one protocol chunk (Cobblemon may hand over several lines at once). */
    public void feed(String chunk) {
        if (chunk == null) return;
        for (String raw : chunk.split("\n")) {
            String line = raw.strip();
            if (!line.startsWith("|")) line = "|" + line;
            var turnMatch = TURN.matcher(line);
            if (turnMatch.matches()) {
                int next = Integer.parseInt(turnMatch.group(1));
                if (next != turn) changed = true;
                turn = next;
                continue;
            }
            var start = START.matcher(line);
            if (start.matches()) {
                var state = bleeding.computeIfAbsent(start.group(1), name -> new State());
                state.stacks = Integer.parseInt(start.group(2));
                state.turns = Integer.parseInt(start.group(3));
                state.appliedTurn = turn;
                changed = true;
                continue;
            }
            var stop = STOP.matcher(line);
            if (stop.matches() && bleeding.remove(stop.group(1)) != null) changed = true;
            if (line.startsWith("|win|") || line.startsWith("|tie")) {
                if (!bleeding.isEmpty()) changed = true;
                bleeding.clear();
            }
        }
    }

    /**
     * The current bleeds. Turns left count down with each {@code |turn|} line: a bleed applied on turn T with 3 turns ticks at the end of T, T+1 and
     * T+2, so at turn U it has {@code 3 - (U - T)} left; one the stop line never arrived for is dropped once it reaches zero.
     */
    public List<Bleed> bleeds() {
        var out = new ArrayList<Bleed>();
        var expired = new ArrayList<String>();
        for (var entry : bleeding.entrySet()) {
            var state = entry.getValue();
            int left = state.turns - Math.max(0, turn - state.appliedTurn);
            if (left <= 0) expired.add(entry.getKey());
            else out.add(new Bleed(entry.getKey(), state.stacks, left));
        }
        expired.forEach(bleeding::remove);
        return out;
    }

    /** Whether anything shown would differ since the last call; clears the flag. */
    public boolean takeChanged() {
        boolean was = changed;
        changed = false;
        return was;
    }

    public boolean idle() { return bleeding.isEmpty(); }
}
