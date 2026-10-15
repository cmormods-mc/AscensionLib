package com.ascensionlib.scout;

import com.ascensionlib.net.RateLimit;
import com.ascensionlib.web.Views;
import com.ascensionlib.web.WebUi;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The inspection screen ({@code inspect.html}): one of the player's own Pokemon (full detail, with an Upgrade button) or the enemies of
 * the encounter they are in (with the Scouter). The page names what it wants to see; the server decides what it may show: an owned
 * Pokemon only after {@link OwnedInspectNet#findForInspection} confirms it is theirs, an enemy only as far as the player has scouted it.
 */
public final class InspectWeb {
    public enum Kind { OWNED, ENEMY }

    private record Target(Kind kind, String id) {}

    /** What each player's inspect page should show once it reports that it has loaded. */
    private static final Map<UUID, Target> PENDING = new ConcurrentHashMap<>();

    private InspectWeb() {}

    public static void register() {
        WebUi.listen("ascension_inspect_ready", (player, o) -> {
            if (RateLimit.allow(player.getUUID(), "inspect.owned", 8, 4)) sendCurrent(player);
        });
        WebUi.listen("ascension_inspect_open", (player, o) -> {
            if (!RateLimit.allow(player.getUUID(), "inspect.open", 4, 2)) return;
            open(player, WebUi.str(o, "kind", 8).equals("enemy") ? Kind.ENEMY : Kind.OWNED, WebUi.id(o, "id"));
        });
        WebUi.listen("ascension_inspect_use", (player, o) -> {
            if (!RateLimit.allow(player.getUUID(), "scout.use", 4, 2)) return;
            String message = ScoutEncounters.use(player.getUUID(), WebUi.str(o, "encounterId", 100), WebUi.str(o, "subjectId", 100));
            player.displayClientMessage(Component.literal("[Scout] " + message), true);
        });
    }

    /** Opens the inspect page. {@code id} is a Pokemon id for {@link Kind#OWNED} and ignored for {@link Kind#ENEMY}. */
    public static boolean open(ServerPlayer player, Kind kind, String id) {
        PENDING.put(player.getUUID(), new Target(kind, kind == Kind.OWNED ? id : ""));
        return WebUi.open(player, "inspect.html");
    }

    private static void sendCurrent(ServerPlayer player) {
        var target = PENDING.get(player.getUUID());
        if (target == null) return;
        if (target.kind() == Kind.OWNED) WebUi.emit(player, "inspectOwned", Views.owned(OwnedInspectNet.resultFor(player, target.id())));
        else WebUi.emit(player, "inspectScout", Views.state(ScoutEncounters.stateFor(player.getUUID())));
    }

    /** A scouting change for this player: refreshes the enemy view when that is what their inspect page shows. */
    public static void onScout(ServerPlayer player, ScoutPayloads.State state) {
        var target = PENDING.get(player.getUUID());
        if (target != null && target.kind() == Kind.ENEMY) WebUi.emit(player, "inspectScout", Views.state(state));
    }

    /** After an upgrade the owner's page asks again so it shows the new rolls. */
    public static void forget(UUID player) { PENDING.remove(player); }
}
