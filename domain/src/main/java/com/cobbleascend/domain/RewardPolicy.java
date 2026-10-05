package com.cobbleascend.domain;

import java.util.Map;
import java.util.HashMap;
import java.util.random.RandomGenerator;

/** Pure payout calculation. Caller must commit entitlement, budget and pity together exactly once. */
public final class RewardPolicy {
    public record Band(int rank, int dust, int facets, double coreChance) {
        public Band {
            if (rank < 1 || dust < 1 || facets < 0 || !Double.isFinite(coreChance)
                    || coreChance < 0 || coreChance > 1) throw new IllegalArgumentException("Invalid reward band");
        }
    }
    public record Payout(Cost materials, int nextCoreMisses) {}
    private final Map<Integer, Band> bands;
    private final int guarantee;
    private final double overflowMultiplier;

    public RewardPolicy(Map<Integer, Band> bands, int guarantee, double overflowMultiplier) {
        this.bands = Map.copyOf(bands);
        this.guarantee = guarantee;
        this.overflowMultiplier = overflowMultiplier;
        if (guarantee < 1 || guarantee > 100 || !Double.isFinite(overflowMultiplier)
                || overflowMultiplier < 0 || overflowMultiplier > 1)
            throw new IllegalArgumentException("Invalid reward policy");
        if (bands.entrySet().stream().anyMatch(e -> e.getKey() != e.getValue().rank()))
            throw new IllegalArgumentException("Reward band identity mismatch");
    }

    public static RewardPolicy defaults() {
        var balance = Rules.resource("balance.json");
        var bands = new HashMap<Integer, Band>();
        balance.getAsJsonArray("trialRewards").forEach(element -> {
            var r = element.getAsJsonObject();
            var band = new Band(r.get("rank").getAsInt(), r.get("dust").getAsInt(),
                    r.get("facets").getAsInt(), r.get("coreChance").getAsDouble());
            if (bands.put(band.rank(), band) != null) throw new IllegalArgumentException("Duplicate reward band");
        });
        return new RewardPolicy(bands, balance.get("coreGuaranteedByEligibleWin").getAsInt(),
                balance.get("overflowDustMultiplier").getAsDouble());
    }

    public Payout victory(int rank, boolean fullReward, int coreMisses, RandomGenerator random) {
        if (coreMisses < 0 || coreMisses >= guarantee) throw new IllegalArgumentException("Invalid Core pity state");
        var band = bands.get(rank);
        if (band == null) throw new IllegalArgumentException("Unknown reward rank");
        if (!fullReward) return new Payout(new Cost(Math.max(1, (int) Math.floor(band.dust() * overflowMultiplier)), 0, 0), coreMisses);
        if (band.coreChance() == 0) return new Payout(new Cost(band.dust(), band.facets(), 0), coreMisses);
        boolean core = coreMisses == guarantee - 1 || random.nextDouble() < band.coreChance();
        return new Payout(new Cost(band.dust(), band.facets(), core ? 1 : 0), core ? 0 : coreMisses + 1);
    }
}
