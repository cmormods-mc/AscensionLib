package com.ascensionlib.reward;

import com.ascensionlib.web.WebUi;
import com.cobbleascend.domain.v1.CurrencyItems;
import com.cobbleascend.domain.v1.MaterialId;
import com.cobbleascend.domain.v1.TowerCompletionBands;
import com.cobbleascend.domain.v1.TowerCompletionBands.Crown;
import com.cobbleascend.domain.v1.TowerCompletionBands.Tier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The reveal for a Tower completion ({@code completion.html}): a tier banner, the guaranteed pile counted up line by line, then the crown rolled one at a
 * time. The library knows exactly what it paid, so it shows this itself, independent of CobbleTowers' own item reveal. The page only displays; the numbers
 * are computed by {@link TowerCompletionBands} and already paid before this is shown. Without WebGUI on the client the same facts go out as chat lines.
 */
public final class CompletionReveal {
    private CompletionReveal() {}

    /** One counted line of the pile. {@code color} is a CSS colour. */
    public record Line(String id, String name, String color, long amount) {}

    /** One crown roll. {@code id} is the material whose icon to show, or "" for an item CobbleTowers hands over itself. */
    public record CrownView(String kind, String id, String label, String color, long amount, boolean big) {}

    public record View(String tier, String tierName, int score, int cycle, int cards, boolean flawless, List<Line> pile, List<CrownView> crowns,
                       int attunement, boolean preview) {}

    private static final Map<UUID, View> PENDING = new ConcurrentHashMap<>();

    public static void register() {
        WebUi.listen("ascension_completion_ready", (player, o) -> {
            var view = PENDING.get(player.getUUID());
            if (view != null) WebUi.emit(player, "completionView", view);
        });
    }

    public static String tierName(Tier tier) { return tier == Tier.MYTHIC ? "MYTHIC" : tier.name(); }

    /** The facts of one player's completion, ready for the page. */
    public static View view(TowerCompletionBands.Completion completion, int cycle, int cards, boolean flawless, int attunement, boolean preview) {
        var pile = new ArrayList<Line>();
        for (var material : List.of(MaterialId.RESONANCE_DUST, MaterialId.FACET, MaterialId.UNIQUE_FRAGMENT, MaterialId.SCOUTER, MaterialId.TOWER_MARK)) {
            long amount = completion.pile().getOrDefault(material, 0L);
            if (amount > 0) pile.add(line(material, amount));
        }
        var crowns = new ArrayList<CrownView>();
        for (var crown : completion.crowns()) crowns.add(crownView(crown));
        return new View(completion.tier().name().toLowerCase(java.util.Locale.ROOT), tierName(completion.tier()), completion.score(), cycle, cards, flawless,
                pile, crowns, attunement, preview);
    }

    private static Line line(MaterialId material, long amount) {
        var spec = CurrencyItems.spec(material);
        return new Line(material.id(), spec.name(), String.format("#%06x", spec.color()), amount);
    }

    private static CrownView crownView(Crown crown) {
        return switch (crown) {
            case CORE -> crownOf(crown, MaterialId.ASCENSION_CORE, 1, true);
            case SIGIL -> crownOf(crown, MaterialId.ASCENSION_SIGIL, 1, true);
            case CATALYST -> crownOf(crown, MaterialId.UNIQUE_CATALYST, 1, true);
            case FRAGMENT_BUNDLE -> new CrownView("fragments", MaterialId.UNIQUE_FRAGMENT.id(), "Unique Fragment bundle",
                    String.format("#%06x", CurrencyItems.spec(MaterialId.UNIQUE_FRAGMENT).color()), com.cobbleascend.domain.v1.TowerCompletionBands.DEFAULTS.fragmentBundle(), false);
            case ARMOR -> new CrownView("armor", "", "Tower armor piece", "#c9d4e0", 1, false);
            case TICKET -> new CrownView("ticket", "", "CobbleCards ticket", "#e8c46a", 1, false);
        };
    }

    private static CrownView crownOf(Crown crown, MaterialId material, long amount, boolean big) {
        var spec = CurrencyItems.spec(material);
        return new CrownView(crown.name().toLowerCase(java.util.Locale.ROOT), material.id(), spec.name(), String.format("#%06x", spec.color()), amount, big);
    }

    /** Shows the reveal to a player (or, for a client without WebGUI, a chat summary). Server thread. */
    public static void show(ServerPlayer player, View view) {
        PENDING.put(player.getUUID(), view);
        if (!WebUi.open(player, "completion.html")) {
            // WebUi.open already told the player why; give the facts anyway.
            var text = new StringBuilder("[Tower] " + view.tierName() + " completion:");
            view.pile().forEach(l -> text.append(' ').append('+').append(l.amount()).append(' ').append(l.name()).append(';'));
            view.crowns().forEach(c -> text.append(" crown: ").append(c.label()).append(c.amount() > 1 ? " x" + c.amount() : "").append(';'));
            player.sendSystemMessage(Component.literal(text.toString()));
        }
    }

    /** The party line for a completion worth announcing (a Core, a Catalyst or a Mythic tier), or empty. */
    public static String announcement(String name, View view) {
        boolean core = view.crowns().stream().anyMatch(c -> c.kind().equals("core"));
        boolean catalyst = view.crowns().stream().anyMatch(c -> c.kind().equals("catalyst"));
        if (!core && !catalyst && !view.tier().equals("mythic")) return "";
        var what = new ArrayList<String>();
        if (view.tier().equals("mythic")) what.add("a MYTHIC completion");
        if (catalyst) what.add("a Unique Catalyst");
        if (core) what.add("an Ascension Core");
        return "[Tower] " + name + " earned " + String.join(", ", what) + "!";
    }
}
