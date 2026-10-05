package com.cobbleascend.domain;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class RewardPolicyTest {
    private final RewardPolicy policy = RewardPolicy.defaults();
    private static Random alwaysMiss() { return new Random() { @Override public double nextDouble() { return 0.99; } }; }

    @Test void fourthEligibleWinGuaranteesOneCoreAndResetsPity() {
        int misses = 0;
        for (int win = 1; win <= 4; win++) {
            var result = policy.victory(3, true, misses, alwaysMiss());
            assertEquals(new Cost(16, 2, win == 4 ? 1 : 0), result.materials());
            misses = result.nextCoreMisses();
            assertEquals(win == 4 ? 0 : win, misses);
        }
    }

    @Test void naturalCoreResetsPity() {
        var alwaysHit = new Random() { @Override public double nextDouble() { return 0; } };
        var result = policy.victory(3, true, 2, alwaysHit);
        assertEquals(1, result.materials().cores());
        assertEquals(0, result.nextCoreMisses());
    }

    @Test void overflowAndLowerBandsCannotAdvanceOrSpendCorePity() {
        for (int rank = 1; rank <= 3; rank++) {
            var payout = policy.victory(rank, false, 3, alwaysMiss());
            assertEquals(0, payout.materials().facets());
            assertEquals(0, payout.materials().cores());
            assertEquals(3, payout.nextCoreMisses());
        }
        assertEquals(3, policy.victory(1, true, 3, alwaysMiss()).nextCoreMisses());
        assertEquals(3, policy.victory(2, true, 3, alwaysMiss()).nextCoreMisses());
        assertEquals(new Cost(4, 0, 0), policy.victory(3, false, 3, alwaysMiss()).materials());
    }

    @Test void invalidPityAndRanksAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> policy.victory(3, true, -1, alwaysMiss()));
        assertThrows(IllegalArgumentException.class, () -> policy.victory(3, true, 4, alwaysMiss()));
        assertThrows(IllegalArgumentException.class, () -> policy.victory(4, true, 0, alwaysMiss()));
    }

    @Test void walletNeverPartiallyDebitsOrWrapsOnOverflow() {
        var wallet = new Wallet(30, 1, 0);
        assertEquals(new Wallet(6, 0, 0), wallet.debit(new Cost(24, 1, 0)));
        assertThrows(IllegalArgumentException.class, () -> wallet.debit(new Cost(20, 2, 0)));
        assertEquals(new Wallet(30, 1, 0), wallet);
        assertThrows(ArithmeticException.class, () -> new Wallet(Integer.MAX_VALUE, 0, 0).credit(new Cost(1, 0, 0)));
    }
}
