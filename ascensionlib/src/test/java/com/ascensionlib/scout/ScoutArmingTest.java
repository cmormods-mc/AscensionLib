package com.ascensionlib.scout;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The "armed battle" table: a player armed for an encounter must not stay armed once it is over or they have left. */
class ScoutArmingTest {
    private final UUID player = UUID.randomUUID();

    @AfterEach void reset() { ScoutEncounters.detach(); }

    @Test void anArmedEncounterThatWasNeverDeclaredIsExplicitlyNative() {
        ScoutEncounters.armBattle(List.of(player), "tower-7");
        var armed = ScoutEncounters.armedEnemies(List.of(player));
        assertNotNull(armed, "armed: the battle is not rated by its own rules");
        assertTrue(armed.isEmpty(), "and nothing was declared, so it fights natively");
    }

    @Test void endingTheEncounterDisarmsEveryPlayerArmedForIt() {
        var other = UUID.randomUUID();
        ScoutEncounters.armBattle(List.of(player), "tower-7");
        ScoutEncounters.armBattle(List.of(other), "tower-8");
        ScoutEncounters.end("tower-7");
        assertNull(ScoutEncounters.armedEnemies(List.of(player)),
                "an unreported disarm must not leave the player's later battles native for good");
        assertNotNull(ScoutEncounters.armedEnemies(List.of(other)), "a different encounter is untouched");
    }

    @Test void disarmingForgetsThePlayer() {
        ScoutEncounters.armBattle(List.of(player), "tower-7");
        ScoutEncounters.disarmBattle(List.of(player));
        assertNull(ScoutEncounters.armedEnemies(List.of(player)));
    }

    @Test void anEmptyIdMeansExplicitlyNativeAndIsNotRemovedByAnEndCall() {
        ScoutEncounters.armBattle(List.of(player), "");
        ScoutEncounters.end("tower-7");
        assertNotNull(ScoutEncounters.armedEnemies(List.of(player)), "an exhibition stays native until its owner disarms it");
    }
}
