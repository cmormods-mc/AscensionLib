package com.ascensionlib.web;

import com.ascensionlib.net.RateLimit;
import com.cobbleascend.domain.v1.GuideFacts;
import com.cobbleascend.domain.v1.Tunables;
import java.util.Map;

/**
 * Gives the guide pages the live values of the numbers a server owner can tune ({@link Tunables}): the daily budget, the fee, the Hourglass price, the chests and the drop
 * rates, and the completion-tier table. The page asks once when it loads ({@code ascension_guide_facts}) and replaces the defaults it shipped with.
 */
public final class GuideWeb {
    private GuideWeb() {}

    public record Live(Map<String, String> facts, Map<String, Object> tables) {}

    public static void register() {
        WebUi.listen("ascension_guide_facts", (player, o) -> {
            if (!RateLimit.allow(player.getUUID(), "guide.facts", 4, 2)) return;
            var tunables = Tunables.current();
            WebUi.emit(player, "guideFacts", new Live(GuideFacts.facts(tunables), Map.of("tower_tier_table", GuideFacts.towerTierTable(tunables))));
        });
    }
}
