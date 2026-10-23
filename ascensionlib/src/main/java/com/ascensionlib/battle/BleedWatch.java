package com.ascensionlib.battle;

import com.ascensionlib.web.HudWeb;
import com.ascensionlib.web.Views;
import com.cobblemon.mod.common.battles.BattleRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shows bleed, Momentum stacks and Wardstone/Ensnaring procs ({@link BattleEffectTracker}) on the overlay. Cobblemon draws icons only for the native statuses, so a bleed (a custom condition inside the simulator) would
 * otherwise be a chat line and nothing else. Each second this reads the new protocol lines of every in-battle player's battle into a
 * {@link BleedTracker}, resolves each name to a side from the battle's actors, and pushes the list to the player's overlay when it changed.
 * Reading only: nothing here touches the battle. Without WebGUI on the client there is nothing to draw, so it does nothing.
 */
public final class BleedWatch {
    private static final Logger LOG = LoggerFactory.getLogger("ascensionlib");
    private static final int PERIOD_TICKS = 10;

    private record Watch(UUID battleId, BleedTracker tracker, BattleEffectTracker effects, int read) {}

    private static final Map<UUID, Watch> WATCHES = new HashMap<>();
    private static int ticks;

    private BleedWatch() {}

    public static void tick(MinecraftServer server) {
        if (++ticks < PERIOD_TICKS) return;
        ticks = 0;
        try {
            for (var player : server.getPlayerList().getPlayers()) watch(player);
        } catch (RuntimeException exception) {
            LOG.warn("Bleed watch failed once: {}", exception.toString());
        }
    }

    public static void forget(UUID player) { WATCHES.remove(player); }

    private static void watch(ServerPlayer player) {
        var battle = BattleRegistry.INSTANCE.getBattleByParticipatingPlayer(player);
        var existing = WATCHES.get(player.getUUID());
        if (battle == null) {
            // The battle ended: clear the badge once, then stop looking.
            if (existing != null) {
                WATCHES.remove(player.getUUID());
                if (!existing.tracker().idle()) HudWeb.pushBleed(player, List.of());
                if (!existing.effects().idle()) HudWeb.pushEffects(player, new Views.EffectsView(List.of(), List.of()));
            }
            return;
        }
        if (existing == null || !existing.battleId().equals(battle.getBattleId())) existing = new Watch(battle.getBattleId(), new BleedTracker(), new BattleEffectTracker(), 0);
        var messages = battle.getShowdownMessages();
        int size = messages.size();
        for (int i = existing.read(); i < size; i++) {
            existing.tracker().feed(messages.get(i));
            existing.effects().feed(messages.get(i));
        }
        WATCHES.put(player.getUUID(), new Watch(existing.battleId(), existing.tracker(), existing.effects(), size));
        if (existing.tracker().takeChanged()) {
            var views = new ArrayList<Views.BleedView>();
            for (var bleed : existing.tracker().bleeds()) views.add(new Views.BleedView(bleed.name(), sideOf(battle, player, bleed.name()), bleed.stacks(), bleed.turnsLeft()));
            HudWeb.pushBleed(player, views);
        }
        if (existing.effects().takeChanged()) {
            var momentum = new ArrayList<Views.MomentumView>();
            for (var m : existing.effects().momentum()) momentum.add(new Views.MomentumView(m.name(), sideOf(battle, player, m.name()), m.stacks()));
            var procs = new ArrayList<Views.ProcView>();
            for (var proc : existing.effects().takeProcs()) procs.add(new Views.ProcView(proc.name(), sideOf(battle, player, proc.name()), proc.kind()));
            HudWeb.pushEffects(player, new Views.EffectsView(momentum, procs));
        }
    }

    /** "ally" when a Pokemon of that name is on the player's side of the battle, otherwise "foe". */
    private static String sideOf(com.cobblemon.mod.common.api.battles.model.PokemonBattle battle, ServerPlayer player, String name) {
        com.cobblemon.mod.common.api.battles.model.actor.BattleActor mine = null;
        for (var actor : battle.getActors()) if (player.getUUID().equals(actor.getUuid())) mine = actor;
        for (var actor : battle.getActors()) {
            boolean ally = mine != null && actor.getSide() == mine.getSide();
            for (var member : actor.getPokemonList()) {
                var shown = member.getEffectedPokemon().getDisplayName(false).getString();
                if (shown.equals(name)) return ally ? "ally" : "foe";
            }
        }
        return "foe";
    }
}
