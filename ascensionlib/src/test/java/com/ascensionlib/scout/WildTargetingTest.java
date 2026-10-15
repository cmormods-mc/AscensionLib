package com.ascensionlib.scout;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class WildTargetingTest {
    private static AABB box(double x, double y, double z) { return new AABB(x - 0.5, y, z - 0.5, x + 0.5, y + 1, z + 0.5); }

    private static final Vec3 EYE = new Vec3(0, 1.6, 0);
    private static final Vec3 FAR = new Vec3(0, 1.6, 32);

    @Test void theNearestPokemonOnTheLineOfSightIsChosen() {
        var near = box(0, 1, 6);
        var far = box(0, 1, 20);
        assertSame(near, WildTargeting.nearest(EYE, FAR, List.of(far, near), b -> b));
    }

    @Test void aPokemonOffToTheSideOrBeyondRangeIsNotChosen() {
        assertNull(WildTargeting.nearest(EYE, FAR, List.of(box(5, 1, 10)), b -> b), "Five blocks off the line");
        assertNull(WildTargeting.nearest(EYE, FAR, List.of(box(0, 1, 40)), b -> b), "Past the end of the line");
        assertNull(WildTargeting.nearest(EYE, FAR, List.<AABB>of(), b -> b));
    }

    @Test void aBoxJustAroundTheLineStillCountsThroughTheSmallInflation() {
        assertNotNull(WildTargeting.nearest(EYE, FAR, List.of(box(0.7, 1, 10)), b -> b), "0.2 blocks outside the box is within the 0.3 allowance");
        assertNull(WildTargeting.nearest(EYE, FAR, List.of(box(1.2, 1, 10)), b -> b));
    }
}
