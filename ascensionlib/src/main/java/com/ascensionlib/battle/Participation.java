package com.ascensionlib.battle;

import com.cobblemon.mod.common.battles.BattleRegistry;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Which of a player's Pokemon actually took part in battle, so attunement goes to the ones that fought and not to the whole party. Twice a second
 * it notes every Pokemon of the player's own actor that is on the field, as the original Pokemon (a CobbleRaids or Tower battle fights clones with
 * fresh ids). {@link #consume} hands the set over when a victory is settled and starts it afresh, so the next boss counts only what fights in it.
 * Reading only; nothing here touches the battle.
 */
public final class Participation {
    private static final int PERIOD_TICKS = 10;
    private static final Map<UUID, Set<UUID>> FOUGHT = new ConcurrentHashMap<>();
    private static int ticks;

    private Participation() {}

    public static void tick(MinecraftServer server) {
        if (++ticks < PERIOD_TICKS) return;
        ticks = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                var battle = BattleRegistry.INSTANCE.getBattleByParticipatingPlayer(player);
                if (battle == null) continue;
                for (var actor : battle.getActors()) {
                    if (!player.getUUID().equals(actor.getUuid())) continue;
                    for (var active : actor.getActivePokemon()) {
                        var member = active.getBattlePokemon();
                        if (member == null) continue;
                        note(player.getUUID(), member.getOriginalPokemon().getUuid());
                    }
                }
            } catch (RuntimeException ignored) {
                // A battle that is mid-change is read again next time.
            }
        }
    }

    static void note(UUID player, UUID pokemon) {
        FOUGHT.computeIfAbsent(player, id -> ConcurrentHashMap.newKeySet()).add(pokemon);
    }

    /** The Pokemon that fought since the last call (empty when none were seen, for example an award with no battle behind it), and starts the next count. */
    public static Set<UUID> consume(UUID player) {
        var seen = FOUGHT.remove(player);
        return seen == null ? Set.of() : new HashSet<>(seen);
    }

    public static void forget(UUID player) { FOUGHT.remove(player); }
}
