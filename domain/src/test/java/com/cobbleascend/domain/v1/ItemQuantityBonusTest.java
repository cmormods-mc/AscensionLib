package com.cobbleascend.domain.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ItemQuantityBonusTest {
    @Test void wholeBonusesAreExact() {
        assertEquals(6, ItemQuantityBonus.scale(5, "a"));
        assertEquals(12, ItemQuantityBonus.scale(10, "a"));
        assertEquals(60, ItemQuantityBonus.scale(50, "a"));
    }

    @Test void aFractionIsRolledOnceAndStaysTheSame() {
        for (int i = 0; i < 200; i++) {
            int first = ItemQuantityBonus.scale(1, "reward-" + i);
            assertTrue(first == 1 || first == 2);
            assertEquals(first, ItemQuantityBonus.scale(1, "reward-" + i), "the same reward rolls the same way");
        }
    }

    @Test void averagesTwentyPercentOverMany() {
        long total = 0;
        int n = 50_000;
        for (int i = 0; i < n; i++) total += ItemQuantityBonus.scale(3, "r" + i);
        assertEquals(3.6, total / (double) n, 0.01);
    }

    @Test void nothingToScaleIsUnchanged() {
        assertEquals(0, ItemQuantityBonus.scale(0, "a"));
    }
}
