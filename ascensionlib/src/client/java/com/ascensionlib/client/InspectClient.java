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
    private InspectClient() {}

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

    public static void openOwned(Screen parent, Pokemon pokemon) {
        var screen = new AscendInspectScreen(parent, pokemon.getUuid().toString(), pokemon.getDisplayName(false).getString());
        Minecraft.getInstance().setScreen(screen);
        if (ClientPlayNetworking.canSend(OwnedInspectPayload.Request.TYPE))
            ClientPlayNetworking.send(new OwnedInspectPayload.Request(pokemon.getUuid().toString()));
    }

    public static void beginTiles() { HITS.clear(); }
    public static void tile(GuiGraphics g, ActiveClientBattlePokemon active, boolean left, int index, boolean compact) {
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
        if (button != 0) return false;
        for (Hit hit : HITS) if (x >= hit.x && x < hit.x + 12 && y >= hit.y && y < hit.y + 12) {
            var pokemon = hit.pokemon.getBattlePokemon();
            if (pokemon == null) return false;
            var battle = CobblemonClient.INSTANCE.getBattle();
            boolean pvp = battle != null && java.util.Arrays.stream(battle.getSides()).allMatch(side ->
                    side.getActors().stream().anyMatch(actor -> actor.getType()
                            == com.cobblemon.mod.common.api.battles.model.actor.ActorType.PLAYER));
            if (pvp) {
                Minecraft.getInstance().setScreen(new AscendInspectScreen(parent, null, pokemon.getDisplayName().getString(), true));
                return true;
            }
            for (Pokemon owned : CobblemonClient.INSTANCE.getStorage().getParty()) {
                if (owned != null && owned.getUuid().equals(pokemon.getUuid())) { openOwned(parent, owned); return true; }
            }
            if (ClientPlayNetworking.canSend(ScoutPayloads.Request.TYPE)) ClientPlayNetworking.send(new ScoutPayloads.Request());
            Minecraft.getInstance().setScreen(new AscendInspectScreen(parent, null, pokemon.getDisplayName().getString()));
            return true;
        }
        return false;
    }
}
