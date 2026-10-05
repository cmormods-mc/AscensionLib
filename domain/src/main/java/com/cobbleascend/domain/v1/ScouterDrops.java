package com.cobbleascend.domain.v1;

import java.util.random.RandomGenerator;

/**
 * Chance that clearing a tower floor drops one Scouter (percent, provisional). A floor with the Keen Eye
 * modifier uses the raised chance for that floor's reward only. CobbleTowers rolls this once per cleared floor
 * with its own random source and passes the result to {@code ScoutingService.grantScouters}.
 */
public record ScouterDrops(int basePercent, int keenEyeFloorPercent) {
    public static final ScouterDrops DEFAULTS = new ScouterDrops(5, 15);

    public ScouterDrops {
        if (basePercent < 0 || basePercent > 100 || keenEyeFloorPercent < basePercent || keenEyeFloorPercent > 100)
            throw new IllegalArgumentException("Drop chances must be 0-100 and Keen Eye must not lower the base chance");
    }

    public int percent(boolean keenEyeFloor) { return keenEyeFloor ? keenEyeFloorPercent : basePercent; }

    public boolean roll(boolean keenEyeFloor, RandomGenerator random) { return random.nextInt(100) < percent(keenEyeFloor); }

    /**
     * The roll for one player's cleared floor, seeded by (encounter, player) so a retried settlement recomputes the
     * same answer. The library rolls this itself when it pays a tower boss victory.
     */
    public boolean rollFor(boolean keenEyeFloor, String encounterId, java.util.UUID player) {
        return roll(keenEyeFloor, new java.util.Random(RewardSeed.of("scouter-drop", encounterId, player)));
    }
}
