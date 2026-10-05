package com.cobbleascend.domain.v1;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Decides which Pokemon of a battle act with which ranked effects (docs/BATTLE-ADAPTER-DESIGN.md, section 1). Pure: the
 * Cobblemon battle is described by {@link Actor}s, so every rule is a unit test with no Minecraft in reach.
 *
 * <ul>
 *   <li><b>PvP</b>: nothing acts, ever.</li>
 *   <li><b>A player's Pokemon</b> act from their stored profile; a Pokemon with no profile does not act.</li>
 *   <li><b>An armed encounter</b> (a tower opponent or floor boss, a raid boss) assigns its declared enemy snapshots, in
 *       order, to the non-player Pokemon, so what a Scouter revealed is what fights.</li>
 *   <li><b>A wild Pokemon</b> (no armed encounter, not a raid) acts from its derived rating, the one a Scouter previews and
 *       a capture awards.</li>
 *   <li><b>Trainers and NPCs</b> are native unless an encounter is armed.</li>
 * </ul>
 */
public final class BattleFxPlanner {
    private BattleFxPlanner() {}

    public enum Kind { PLAYER, WILD, NPC }

    /**
     * One Pokemon in a battle. {@code effectedId} is the uuid Cobblemon packs into the team (what the simulator sees, a
     * clone's in a raid); {@code originalId} is the real Pokemon, whose profile or wild rating applies.
     */
    public record Combatant(String effectedId, UUID originalId) {}

    public record Actor(Kind kind, List<Combatant> pokemon) {
        public Actor { pokemon = List.copyOf(pokemon); }
    }

    /**
     * @param actors         every actor in battle order
     * @param pvp            two or more players fighting each other
     * @param raid           a raid-format battle, whose boss is never rated as a wild Pokemon
     * @param armed          the declared enemies of an encounter armed for this battle, or {@code null} when none is armed;
     *                       an armed encounter with no enemies means "explicitly native"
     * @param playerSnapshot a player's Pokemon's stored profile as a snapshot, by original uuid
     * @param wildSnapshot   a wild Pokemon's derived rating as a snapshot, empty when it cannot be rated
     * @return effects per packed uuid; Pokemon with none are absent
     */
    public static Map<String, List<BattleFx.Effect>> plan(List<Actor> actors, boolean pvp, boolean raid,
                                                          List<CombatSnapshot> armed,
                                                          Function<UUID, Optional<CombatSnapshot>> playerSnapshot,
                                                          Function<UUID, Optional<CombatSnapshot>> wildSnapshot) {
        var result = new LinkedHashMap<String, List<BattleFx.Effect>>();
        if (pvp) return result;
        var enemies = new ArrayList<Combatant>();
        var wild = new ArrayList<Combatant>();
        for (var actor : actors) {
            for (var combatant : actor.pokemon()) {
                switch (actor.kind()) {
                    case PLAYER -> playerSnapshot.apply(combatant.originalId())
                            .ifPresent(snapshot -> put(result, combatant, snapshot));
                    case WILD -> { enemies.add(combatant); wild.add(combatant); }
                    case NPC -> enemies.add(combatant);
                }
            }
        }
        if (armed != null) {
            for (int i = 0; i < Math.min(armed.size(), enemies.size()); i++) put(result, enemies.get(i), armed.get(i));
        } else if (!raid) {
            for (var combatant : wild) wildSnapshot.apply(combatant.originalId()).ifPresent(snapshot -> put(result, combatant, snapshot));
        }
        return result;
    }

    private static void put(Map<String, List<BattleFx.Effect>> result, Combatant combatant, CombatSnapshot snapshot) {
        var effects = BattleFx.effectsOf(snapshot);
        if (!effects.isEmpty()) result.put(combatant.effectedId(), effects);
    }
}
