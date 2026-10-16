package com.cobbleascend.domain.v1;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The few numbers in the in-game guide that a server owner can change ({@link Tunables}): the daily budget, the withdrawal fee, the Hourglass price, attunement per
 * victory, the mini chest, the completion chest and how often the respec items drop. The guide text is generated with these as {@code {placeholders}} and the page asks
 * the server for the live values, so a tuned server never shows a stale number. {@code tools/build_guide.py} builds the same values for the defaults and a test checks the
 * two agree, so the generator and this class cannot drift apart.
 */
public final class GuideFacts {
    private GuideFacts() {}

    private static String plain(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    /** Half up, the rounding the payouts use ({@code Math.round}). */
    private static long pct(int base, int percent) { return Math.round(base * percent / 100.0); }

    /** The placeholder values, by the key the guide text uses. */
    public static Map<String, String> facts(Tunables t) {
        var f = new LinkedHashMap<String, String>();
        f.put("budget.full", Integer.toString(t.budget().fullPerDay()));
        f.put("budget.percent", Integer.toString(t.budget().reducedDustPercent()));
        f.put("fee.percent", Integer.toString(t.withdrawalFeePercent()));
        f.put("respec.price", Integer.toString(t.respecMarksPrice()));
        f.put("attunement.perVictory", Integer.toString(t.attunementPerVictory()));
        var mini = t.miniChest();
        f.put("mini.dust", Integer.toString(mini.dust()));
        f.put("mini.facets", Integer.toString(mini.facets()));
        f.put("mini.fragments", Integer.toString(mini.fragments()));
        f.put("mini.scouters", Integer.toString(mini.scouters()));
        f.put("mini.marks", Integer.toString(mini.marks()));
        f.put("mini.corePercent", Integer.toString(mini.corePermille() / 10));
        f.put("tower.bundle", Integer.toString(t.towerCompletion().fragmentBundle()));
        double rank = t.towerCompletion().expectedPerCompletion(TowerCompletionBands.Crown.RESPEC_RANK, TowerCompletionBands.APPROVED_MIX);
        double unique = t.towerCompletion().expectedPerCompletion(TowerCompletionBands.Crown.RESPEC_UNIQUE, TowerCompletionBands.APPROVED_MIX);
        f.put("respec.rankOneIn", rank > 0 ? Long.toString(Math.round(1 / rank)) : "many");
        f.put("respec.uniqueOneIn", unique > 0 ? Long.toString(Math.round(1 / unique)) : "many");
        return f;
    }

    /** The completion-tier table: header and rows of text, exactly as the generated guide draws it. */
    public static Map<String, Object> towerTierTable(Tunables t) {
        var bands = t.towerCompletion();
        var rows = new ArrayList<List<String>>();
        for (var spec : bands.tiers()) {
            int marks = bands.marks() + (spec.tier().ordinal() >= TowerCompletionBands.Tier.GOLD.ordinal() ? bands.goldMarkBonus() : 0)
                    + (spec.tier() == TowerCompletionBands.Tier.MYTHIC ? bands.mythicMarkBonus() : 0);
            int total = 0;
            for (int w : spec.weights()) total += w;
            long corePercent = Math.round(spec.weights()[TowerCompletionBands.Crown.CORE.ordinal()] * 100.0 / total);
            String name = spec.tier().name().substring(0, 1) + spec.tier().name().substring(1).toLowerCase(java.util.Locale.ROOT);
            rows.add(List.of(name, "score " + spec.minScore() + "+", "x" + plain(spec.multiplierPercent() / 100.0),
                    pct(bands.dust(), spec.multiplierPercent()) + " dust, " + pct(bands.facets(), spec.multiplierPercent()) + " facets, "
                            + pct(bands.fragments(), spec.multiplierPercent()) + " fragments, " + bands.scouters() + " scouters, " + marks + " Marks",
                    spec.crownSlots() + " crown roll" + (spec.crownSlots() > 1 ? "s" : "") + " (" + corePercent + "% Core each)"));
        }
        var table = new LinkedHashMap<String, Object>();
        table.put("head", List.of("Tier", "Reached at", "Pile", "Guaranteed pile", "Crown"));
        table.put("rows", rows);
        return table;
    }
}
