package com.ascensionlib.battle;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class BleedTrackerTest {
    @Test void aBleedAppearsWithItsStacksAndTurns() {
        var t = new BleedTracker();
        t.feed("|turn|1");
        t.feed("|-message|Growlithe is bleeding! (stack 1, 3 turns)");
        assertEquals(List.of(new BleedTracker.Bleed("Growlithe", 1, 3)), t.bleeds());
        assertTrue(t.takeChanged());
        assertFalse(t.takeChanged(), "asking again changes nothing");
    }

    @Test void turnsCountDownWithEachTurnLineAndExpireAtZero() {
        var t = new BleedTracker();
        t.feed("|turn|1\n|-message|Pikachu is bleeding! (stack 2, 3 turns)");
        t.feed("|turn|2");
        assertEquals(2, t.bleeds().get(0).turnsLeft());
        t.feed("|turn|3");
        assertEquals(1, t.bleeds().get(0).turnsLeft());
        t.feed("|turn|4");
        assertTrue(t.bleeds().isEmpty(), "a bleed whose stop line never arrived is dropped at zero");
    }

    @Test void aRefreshRestartsTheCountAndRaisesTheStacks() {
        var t = new BleedTracker();
        t.feed("|turn|1\n|-message|Eevee is bleeding! (stack 1, 3 turns)");
        t.feed("|turn|2");
        t.feed("|-message|Eevee is still bleeding! (stack 1, 3 turns)");
        assertEquals(3, t.bleeds().get(0).turnsLeft());
        t.feed("|-message|Eevee is bleeding! (stack 2, 3 turns)");
        assertEquals(2, t.bleeds().get(0).stacks());
    }

    @Test void theStopLineClearsOnlyThatPokemon() {
        var t = new BleedTracker();
        t.feed("|-message|Eevee is bleeding! (stack 1, 3 turns)");
        t.feed("|-message|Mew is bleeding! (stack 1, 3 turns)");
        t.feed("|-message|Eevee's bleeding stopped.");
        assertEquals(List.of("Mew"), t.bleeds().stream().map(BleedTracker.Bleed::name).toList());
    }

    @Test void theEndOfTheBattleClearsEverythingAndOtherLinesAreIgnored() {
        var t = new BleedTracker();
        t.feed("|-damage|p1a: Mew|50/100");
        t.feed("|-message|Mew is bleeding! (stack 1, 3 turns)");
        t.feed("|-message|Mew is on fire, not bleeding!");
        assertEquals(1, t.bleeds().size());
        t.feed("|win|Alice");
        assertTrue(t.idle());
    }

    @Test void aNicknameWithSpacesAndPunctuationIsKeptWhole() {
        var t = new BleedTracker();
        t.feed("|-message|Mr. Mime Jr.'s Pal is bleeding! (stack 3, 2 turns)");
        assertEquals("Mr. Mime Jr.'s Pal", t.bleeds().get(0).name());
    }
}
