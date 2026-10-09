package com.ascensionlib.battle;

import com.ascensionlib.AscensionApi;
import com.ascensionlib.AscensionLib;
import com.ascensionlib.scout.ScoutEncounters;
import com.cobbleascend.domain.v1.BattleFx;
import com.cobbleascend.domain.v1.BattleFxPlanner;
import com.cobbleascend.domain.v1.CombatSnapshot;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds the {@code ascensionFx} format field for a battle that is about to start. Called by the Showdown host on the server
 * thread for every battle, just before it is handed to the simulator.
 *
 * <p>Fails closed for ascension and open for battles: whatever goes wrong (no running world, an unreadable profile, an
 * oversized payload, any exception) the battle starts with no effects and the log says why. Which Pokemon act, and from
 * what, is {@link BattleFxPlanner}; this class only describes the Cobblemon battle to it and writes the answer.
 */
public final class AscensionBattles {
    private static final Logger LOG = LoggerFactory.getLogger(AscensionLib.MOD_ID);
    static final String FIELD = "ascensionFx";

    private AscensionBattles() {}

    public static Map<String, String> fieldsFor(UUID battleId, List<UUID> playerIds) {
        try {
            var service = AscensionApi.service();
            if (service.isEmpty()) return Map.of();
            var battle = BattleRegistry.getBattle(battleId);
            if (battle == null) return Map.of();

            var actors = new ArrayList<BattleFxPlanner.Actor>();
            // A wild Pokemon is rated from the Pokemon itself (its types and level), so keep it by its real uuid.
            var wildByUuid = new HashMap<UUID, Pokemon>();
            for (var actor : battle.getActors()) {
                var kind = actor.getType() == ActorType.PLAYER ? BattleFxPlanner.Kind.PLAYER
                        : actor.getType() == ActorType.WILD ? BattleFxPlanner.Kind.WILD : BattleFxPlanner.Kind.NPC;
                var combatants = new ArrayList<BattleFxPlanner.Combatant>();
                for (var member : actor.getPokemonList()) {
                    Pokemon original = member.getOriginalPokemon();
                    combatants.add(new BattleFxPlanner.Combatant(member.getUuid().toString(), original.getUuid()));
                    if (kind == BattleFxPlanner.Kind.WILD) wildByUuid.put(original.getUuid(), original);
                }
                actors.add(new BattleFxPlanner.Actor(kind, combatants));
            }

            boolean raid = "raid".equals(battle.getFormat().getBattleType().getName());
            var rules = service.get().rules();
            var plan = BattleFxPlanner.plan(actors, battle.isPvP(), raid, ScoutEncounters.armedEnemies(playerIds),
                    pokemonId -> AscensionApi.store().flatMap(store -> store.profile(pokemonId)).map(CombatSnapshot::ofProfile),
                    pokemonId -> Optional.ofNullable(wildByUuid.get(pokemonId)).map(wild -> {
                        var rating = service.get().previewWild(wild);
                        return new CombatSnapshot(CombatSnapshot.Source.ENEMY, pokemonId.toString(), rating.rarity(),
                                rules.catalogVersion(), rating.slots(), null);
                    }));

            String payload = BattleFx.payload(plan, rules.base());
            if (payload == null) return Map.of();
            LOG.debug("Battle {}: ascension effects for {} Pokemon", battleId, plan.size());
            return Map.of(FIELD, payload);
        } catch (RuntimeException | LinkageError ex) {
            // Not Throwable: an OutOfMemoryError must not be swallowed here as if the battle could simply start without effects.
            LOG.error("Could not build ascension effects for battle {}; it starts without them", battleId, ex);
            return Map.of();
        }
    }
}
