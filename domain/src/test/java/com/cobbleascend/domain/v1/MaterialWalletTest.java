package com.cobbleascend.domain.v1;

import static org.junit.jupiter.api.Assertions.*;

import com.cobbleascend.domain.v1.CraftException.Reason;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MaterialWalletTest {
    private static Reason reasonOf(Runnable action) { return assertThrows(CraftException.class, action::run).reason(); }

    @Test void newWalletHoldsEveryMaterialAtZero() {
        assertEquals(0, MaterialWallet.EMPTY.revision());
        for (var id : MaterialId.values()) assertEquals(0, MaterialWallet.EMPTY.balance(id));
        assertEquals(6, MaterialWallet.EMPTY.balances().size());
        assertEquals(MaterialId.SCOUTER, MaterialId.fromId("scouter"));
        assertEquals(MaterialId.UNIQUE_CATALYST, MaterialId.fromId("unique_catalyst"));
        assertThrows(IllegalArgumentException.class, () -> MaterialId.fromId("gold"));
    }

    @Test void creditAndDebitMoveBalancesAndRevisionsExactly() {
        var funded = MaterialWallet.EMPTY.credit(Map.of(MaterialId.RESONANCE_DUST, 50L, MaterialId.FACET, 3L));
        assertEquals(1, funded.revision());
        assertEquals(50, funded.balance(MaterialId.RESONANCE_DUST));
        var spent = funded.debit(Map.of(MaterialId.RESONANCE_DUST, 24L, MaterialId.FACET, 1L));
        assertEquals(2, spent.revision());
        assertEquals(26, spent.balance(MaterialId.RESONANCE_DUST));
        assertEquals(2, spent.balance(MaterialId.FACET));
        assertEquals(50, funded.balance(MaterialId.RESONANCE_DUST), "Wallets are immutable");
    }

    @Test void zeroOrEmptyChangesReturnTheSameWalletAndRevision() {
        var funded = MaterialWallet.EMPTY.credit(Map.of(MaterialId.FACET, 1L));
        assertSame(funded, funded.debit(Map.of()));
        assertSame(funded, funded.debit(Map.of(MaterialId.FACET, 0L)));
        assertSame(funded, funded.credit(Map.of(MaterialId.FACET, 0L)));
    }

    @Test void refusalsLeaveTheWalletUntouchedAndAreStructured() {
        var funded = MaterialWallet.EMPTY.credit(Map.of(MaterialId.RESONANCE_DUST, 10L));
        assertEquals(Reason.INSUFFICIENT_FUNDS, reasonOf(() -> funded.debit(Map.of(MaterialId.RESONANCE_DUST, 11L))));
        assertEquals(Reason.INSUFFICIENT_FUNDS, reasonOf(() -> funded.debit(
                Map.of(MaterialId.RESONANCE_DUST, 1L, MaterialId.FACET, 1L))), "All-or-nothing across materials");
        assertEquals(Reason.INVALID_AMOUNT, reasonOf(() -> funded.debit(Map.of(MaterialId.FACET, -1L))));
        assertEquals(Reason.INVALID_AMOUNT, reasonOf(() -> funded.credit(Map.of(MaterialId.FACET, -1L))));
        assertFalse(funded.canAfford(Map.of(MaterialId.RESONANCE_DUST, 11L)));
        assertTrue(funded.canAfford(Map.of(MaterialId.RESONANCE_DUST, 10L)));
        assertEquals(10, funded.balance(MaterialId.RESONANCE_DUST));
    }

    @Test void overflowIsRejectedNotWrapped() {
        var rich = new MaterialWallet(1, Map.of(MaterialId.ASCENSION_CORE, Long.MAX_VALUE));
        assertEquals(Reason.WALLET_OVERFLOW, reasonOf(() -> rich.credit(Map.of(MaterialId.ASCENSION_CORE, 1L))));
        assertThrows(IllegalArgumentException.class, () -> new MaterialWallet(-1, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new MaterialWallet(0, Map.of(MaterialId.FACET, -5L)));
    }

    @Test void catalystAssemblyConsumesTheThresholdAndKeepsTheRemainder() {
        var wallet = MaterialWallet.EMPTY.credit(Map.of(MaterialId.UNIQUE_FRAGMENT, 250L));
        var assembled = wallet.assembleCatalyst(100);
        assertEquals(150, assembled.balance(MaterialId.UNIQUE_FRAGMENT));
        assertEquals(1, assembled.balance(MaterialId.UNIQUE_CATALYST));
        assertEquals(wallet.revision() + 1, assembled.revision());
        assertEquals(Reason.INSUFFICIENT_FUNDS, reasonOf(() -> wallet.assembleCatalyst(251)));
        assertThrows(IllegalArgumentException.class, () -> wallet.assembleCatalyst(0));
    }
}
