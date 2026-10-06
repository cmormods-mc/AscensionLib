package com.ascensionlib;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * CobbleTowers and CobbleRaids reach these methods by reflection (the library is optional for them), so a changed name
 * or parameter list compiles here and fails silently there. This pins what they look up; change a signature only by
 * adding a new method.
 */
class ContractSignaturesTest {

    private static void assertContract(Class<?> owner, String name, Class<?> returns, Class<?>... parameters)
            throws NoSuchMethodException {
        var method = owner.getMethod(name, parameters);
        assertEquals(true, Modifier.isStatic(method.getModifiers()), name + " must stay static");
        assertEquals(returns, method.getReturnType(), name + " return type");
    }

    @Test void rewardContractsMatchWhatTowersLooksUp() throws Exception {
        assertContract(AscensionRewards.class, "settleTowerBoss", Map.class,
                UUID.class, String.class, int.class, int.class, Collection.class);
        assertContract(AscensionRewards.class, "settleScouterDrops", Map.class,
                UUID.class, String.class, boolean.class, Collection.class);
        assertContract(AscensionRewards.class, "settle", Map.class,
                UUID.class, String.class, String.class, Map.class);
    }

    @Test void trialContractMatchesWhatTowersLooksUp() throws Exception {
        assertContract(AscensionRewards.class, "settleTrial", Map.class,
                UUID.class, String.class, int.class, Collection.class);
    }

    @Test void itemQuantityContractMatchesWhatRaidsAndTowersLookUp() throws Exception {
        assertContract(AscensionRewards.class, "scaleItemQuantity", int.class, UUID.class, int.class, String.class);
    }

    @Test void raidAttunementContractMatchesWhatRaidsLooksUp() throws Exception {
        assertContract(AscensionRewards.class, "settleRaidAttunement", int.class,
                String.class, String.class, Collection.class);
    }

    @Test void scoutingContractMatchesWhatTowersLooksUp() throws Exception {
        assertContract(AscensionEncounters.class, "declareEnemy", String.class,
                String.class, Collection.class, int.class, String.class, boolean.class, String.class, String.class,
                int.class);
        assertContract(AscensionEncounters.class, "end", void.class, String.class);
        assertContract(AscensionEncounters.class, "armBattle", void.class, Collection.class, String.class);
        assertContract(AscensionEncounters.class, "disarmBattle", void.class, Collection.class);
    }
}
