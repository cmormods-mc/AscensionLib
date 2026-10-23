package com.ascensionlib.battle;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class BattleEffectTrackerTest {
    @Test void momentumStacksRiseAndFade() {
        var t = new BattleEffectTracker();
        t.feed("|-message|Arcanine's Momentum rises! (stack 1)");
        t.feed("|-message|Arcanine's Momentum rises! (stack 2)");
        assertEquals(List.of(new BattleEffectTracker.Momentum("Arcanine", 2)), t.momentum());
        assertTrue(t.takeChanged());
        assertFalse(t.takeChanged(), "reading it clears the flag");
        t.feed("|-message|Arcanine's Momentum faded.");
        assertTrue(t.momentum().isEmpty());
        assertTrue(t.idle());
        assertTrue(t.takeChanged());
    }

    @Test void aProcIsReportedOnceAndMomentumIsNotAProc() {
        var t = new BattleEffectTracker();
        t.feed("|turn|2\n|-message|Growlithe's Wardstone turned the condition aside!\n|-message|Houndoom was ensnared!");
        assertEquals(List.of(new BattleEffectTracker.Proc("Growlithe", "wardstone"), new BattleEffectTracker.Proc("Houndoom", "ensnaring")), t.takeProcs());
        assertTrue(t.takeProcs().isEmpty(), "taken, not repeated");
        assertTrue(t.idle(), "a flash leaves nothing standing");
    }

    @Test void theEndOfABattleClearsMomentumAndAnUnrelatedLineChangesNothing() {
        var t = new BattleEffectTracker();
        t.feed("|-message|Mew's Momentum rises! (stack 3)");
        t.takeChanged();
        t.feed("|-message|Mew stubbornly held on!\n|move|p1a: Mew|Psychic|p2a: Eevee");
        assertFalse(t.takeChanged());
        t.feed("|win|Ash");
        assertTrue(t.idle());
        assertTrue(t.takeChanged());
    }

    @Test void namesWithApostrophesAndSpacesParse() {
        var t = new BattleEffectTracker();
        t.feed("|-message|Farfetch'd was ensnared!");
        assertEquals("Farfetch'd", t.takeProcs().get(0).name());
        t.feed("|-message|Mr. Mime's Momentum rises! (stack 1)");
        assertEquals("Mr. Mime", t.momentum().get(0).name());
    }
}
