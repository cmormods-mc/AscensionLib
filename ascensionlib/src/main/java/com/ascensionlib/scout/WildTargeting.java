package com.ascensionlib.scout;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import java.util.List;
import java.util.function.Function;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Which Pokemon a player is looking at: the nearest one whose (slightly enlarged) box the player's line of sight crosses. */
public final class WildTargeting {
    /** How far a player can scout, in blocks. */
    public static final double RANGE = 32.0;

    private WildTargeting() {}

    /** The nearest Pokemon in view within {@link #RANGE}, or null. Server thread. */
    public static PokemonEntity lookedAt(ServerPlayer player) {
        var from = player.getEyePosition();
        var to = from.add(player.getViewVector(1.0F).scale(RANGE));
        var candidates = player.level().getEntitiesOfClass(PokemonEntity.class, player.getBoundingBox().expandTowards(to.subtract(from)).inflate(1.0));
        return nearest(from, to, candidates, PokemonEntity::getBoundingBox);
    }

    /** The candidate whose box, inflated by 0.3, the segment from-to crosses first; null when none. Pure geometry, so it can be tested. */
    public static <T> T nearest(Vec3 from, Vec3 to, List<T> candidates, Function<T, AABB> box) {
        T best = null;
        double bestDistance = Double.MAX_VALUE;
        for (T candidate : candidates) {
            var hit = box.apply(candidate).inflate(0.3).clip(from, to);
            if (hit.isEmpty()) continue;
            double distance = from.distanceToSqr(hit.get());
            if (distance < bestDistance) { best = candidate; bestDistance = distance; }
        }
        return best;
    }
}
