package com.ascensionlib.client;

import com.ascensionlib.scout.OwnedInspectPayload;
import com.ascensionlib.scout.ScoutPayloads;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class InspectClient {
    private record Hit(int x, int y, ActiveClientBattlePokemon pokemon) {}
    private static final List<Hit> HITS = new ArrayList<>();
    private static int failures;
    private InspectClient() {}

    /** After a few failures the hooks switch themselves off for the session; the J key still opens the inspector. */
    private static boolean broken() { return failures >= 3; }

    private static void failed(String where, Throwable error) {
        failures++;
        org.slf4j.LoggerFactory.getLogger("ascensionlib").warn("Inspect hook {} failed ({} of 3 before it is switched off): {}", where, failures, error.toString());
    }

    /** Forgets the last battle's tiles (called on disconnect, so no battle object outlives the session). */
    public static void clear() { HITS.clear(); }

    public static void summaryButton(Screen screen, java.util.function.Supplier<Pokemon> selected) {
        // Fit to Cobblemon's own party panel (found by class, so it follows the screen if Cobblemon moves it): in the panel's
        // header strip, left of its swap button. Falls back to the summary frame's top-right corner if the widget is not there.
        int x = Math.min(screen.width - 22, (screen.width + 331) / 2 - 20), y = Math.max(4, (screen.height - 161) / 2 - 20);
        for (var child : screen.children()) {
            if (child.getClass().getSimpleName().equals("PartyWidget") && child instanceof net.minecraft.client.gui.components.AbstractWidget w) {
                x = w.getX() + 56;
                y = w.getY() - 13;
                break;
            }
        }
        var button = new InspectButton(x, y, b -> {
            Pokemon pokemon = selected.get();
            if (pokemon != null) openOwned(screen, pokemon);
        });
        net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(screen).add(button);
    }

    /** Opens the owned inspector for a Pokemon the server told us about (it may be in the PC, which the client does not sync). */
    public static void openOwnedById(Screen parent, String pokemonId, String name) {
        Minecraft.getInstance().setScreen(new AscendInspectScreen(parent, pokemonId, name));
        if (ClientPlayNetworking.canSend(OwnedInspectPayload.Request.TYPE))
            ClientPlayNetworking.send(new OwnedInspectPayload.Request(pokemonId));
    }

    /**
     * Opens the owner's inspector for a tile on the player's own side of a battle. The id is the BATTLE Pokemon's, which is the
     * party Pokemon's id only in an ordinary battle: in a raid or an exhibition the team is made of copies with fresh ids, so the
     * server (which knows the original behind each copy) resolves it. Upgrading is not offered from inside a battle.
     */
    public static void openOwnedFromBattle(Screen parent, String battlePokemonId, String name) {
        Minecraft.getInstance().setScreen(new AscendInspectScreen(parent, battlePokemonId, name, false, true));
        if (ClientPlayNetworking.canSend(OwnedInspectPayload.Request.TYPE))
            ClientPlayNetworking.send(new OwnedInspectPayload.Request(battlePokemonId));
    }

    public static void openOwned(Screen parent, Pokemon pokemon) {
        var screen = new AscendInspectScreen(parent, pokemon.getUuid().toString(), pokemon.getDisplayName(false).getString());
        Minecraft.getInstance().setScreen(screen);
        if (ClientPlayNetworking.canSend(OwnedInspectPayload.Request.TYPE))
            ClientPlayNetworking.send(new OwnedInspectPayload.Request(pokemon.getUuid().toString()));
    }

    public static void beginTiles() { HITS.clear(); }
    public static void tile(GuiGraphics g, ActiveClientBattlePokemon active, boolean left, int index, boolean compact) {
        // Runs inside Cobblemon's per-frame overlay render: an exception here would be a crash report, so it must never escape.
        if (broken()) return;
        try {
            drawTileButton(g, active, left, index, compact);
        } catch (RuntimeException | LinkageError error) {
            failed("tile", error);
        }
    }

    private static void drawTileButton(GuiGraphics g, ActiveClientBattlePokemon active, boolean left, int index, boolean compact) {
        var battle = CobblemonClient.INSTANCE.getBattle();
        if (battle == null || active.getBattlePokemon() == null) return;
        int group = (Character.digit(active.getActorShowdownId().charAt(1), 10) - 1) / 2 * 10;
        int y = 10 + index * (compact ? 30 : 40) + (left ? group :
                (battle.getBattleFormat().getBattleType().getActorsPerSide() - 1) * 10 - group);
        // Hanging off the portrait's bottom inner corner (the side facing the arena): its right edge on the player's tile,
        // its left edge on the opponent's, centred on the corner. Measured against Cobblemon 1.8.1's tile at GUI scale 3.
        int disp = Math.round(active.getXDisplacement());
        int x = left ? disp + 31 : disp + 99;
        int by = y + (compact ? 24 : 34);
        if (x < 0 || x + 12 > Minecraft.getInstance().getWindow().getGuiScaledWidth()) return;
        HITS.add(new Hit(x, by, active));
        InspectButton.glyph(g, x, by, 12, false);
    }

    public static boolean click(Screen parent, double x, double y, int button) {
        if (button != 0 || broken()) return false;
        try {
            return handleClick(parent, x, y);
        } catch (RuntimeException | LinkageError error) {
            failed("click", error);
            return false;
        }
    }

    private static boolean handleClick(Screen parent, double x, double y) {
        for (Hit hit : HITS) if (x >= hit.x && x < hit.x + 12 && y >= hit.y && y < hit.y + 12) {
            var pokemon = hit.pokemon.getBattlePokemon();
            if (pokemon == null) return false;
            // A tile is mine when its actor is me: the very rule Cobblemon's overlay uses to draw my side on the left. Matching the
            // Pokemon's id against my party is wrong in a raid or an exhibition (copies with new ids): it fell through to the scouting
            // view, which shows the opposing encounter, so my own Pokemon opened the opponent's inspection.
            var self = Minecraft.getInstance().player;
            var tileActor = hit.pokemon.getActor();
            if (self != null && tileActor != null && self.getUUID().equals(tileActor.getUuid())) {
                openOwnedFromBattle(parent, pokemon.getUuid().toString(), pokemon.getDisplayName().getString());
                return true;
            }
            var battle = CobblemonClient.INSTANCE.getBattle();
            boolean pvp = battle != null && java.util.Arrays.stream(battle.getSides()).allMatch(side ->
                    side.getActors().stream().anyMatch(actor -> actor.getType()
                            == com.cobblemon.mod.common.api.battles.model.actor.ActorType.PLAYER));
            if (pvp) {
                Minecraft.getInstance().setScreen(new AscendInspectScreen(parent, null, pokemon.getDisplayName().getString(), true));
                return true;
            }
            if (ClientPlayNetworking.canSend(ScoutPayloads.Request.TYPE)) ClientPlayNetworking.send(new ScoutPayloads.Request());
            Minecraft.getInstance().setScreen(new AscendInspectScreen(parent, null, pokemon.getDisplayName().getString()));
            return true;
        }
        return false;
    }
}
