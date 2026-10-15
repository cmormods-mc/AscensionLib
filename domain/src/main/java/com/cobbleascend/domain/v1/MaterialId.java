package com.cobbleascend.domain.v1;

import com.cobbleascend.domain.Cost;
import java.util.EnumMap;
import java.util.Map;

/** Stable wallet material identifiers (specification section 4.2). The wallet itself is a later work package. */
public enum MaterialId {
    RESONANCE_DUST("resonance_dust"),
    FACET("facet"),
    ASCENSION_CORE("ascension_core"),
    UNIQUE_FRAGMENT("unique_fragment"),
    UNIQUE_CATALYST("unique_catalyst"),
    /** One use reveals one enemy for one encounter. Only granted as a CobbleTowers reward for now. */
    SCOUTER("scouter"),
    /** One use gives a Pokemon with no profile a profile with a rolled rarity, and is consumed. Rare loot from tower bosses. */
    ASCENSION_SIGIL("ascension_sigil"),
    /** Proof of a personal Tower clear. Never withdrawn as a voucher (design/currency-items.json, "tradable": false); spent on the top-end crafts. */
    TOWER_MARK("tower_mark"),
    /** The Hourglass of Undoing: resets one Pokemon's ranks and gives its upgrade credits back (docs/RESPEC-DESIGN.md). */
    RANK_RESPEC("rank_respec"),
    /** The Unbinding Prism: swaps a Pokemon's Unique for another without a Catalyst or a Mark. */
    UNIQUE_RESPEC("unique_respec");

    private final String id;
    MaterialId(String id) { this.id = id; }
    public String id() { return id; }

    public static MaterialId fromId(String id) {
        for (var material : values()) if (material.id.equals(id)) return material;
        throw new IllegalArgumentException("Unknown material: " + id);
    }

    /** Converts a prototype cost into nonzero material entries. */
    public static Map<MaterialId, Long> of(Cost cost) {
        var result = new EnumMap<MaterialId, Long>(MaterialId.class);
        if (cost.dust() > 0) result.put(RESONANCE_DUST, (long) cost.dust());
        if (cost.facets() > 0) result.put(FACET, (long) cost.facets());
        if (cost.cores() > 0) result.put(ASCENSION_CORE, (long) cost.cores());
        return java.util.Collections.unmodifiableMap(result);
    }
}
