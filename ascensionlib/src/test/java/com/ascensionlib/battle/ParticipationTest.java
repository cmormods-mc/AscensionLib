package com.ascensionlib.battle;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ParticipationTest {
    @Test void onlyThePokemonSeenOnTheFieldAreHandedOverAndTheCountStartsAfresh() {
        var player = UUID.randomUUID();
        var lead = UUID.randomUUID();
        var second = UUID.randomUUID();
        Participation.note(player, lead);
        Participation.note(player, second);
        Participation.note(player, lead);
        assertEquals(Set.of(lead, second), Participation.consume(player));
        assertEquals(Set.of(), Participation.consume(player), "the next boss counts only what fights in it");
    }

    @Test void playersAreCountedApart() {
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        var mon = UUID.randomUUID();
        Participation.note(a, mon);
        assertEquals(Set.of(), Participation.consume(b));
        assertEquals(Set.of(mon), Participation.consume(a));
    }

    @Test void aPlayerWhoLeftIsForgotten() {
        var player = UUID.randomUUID();
        Participation.note(player, UUID.randomUUID());
        Participation.forget(player);
        assertEquals(Set.of(), Participation.consume(player));
    }
}
