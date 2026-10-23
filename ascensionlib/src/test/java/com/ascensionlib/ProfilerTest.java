package com.ascensionlib;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProfilerTest {
    @BeforeEach void freshWindow() { Profiler.drain(); }

    @Test void timingsAreReportedInMillisecondsAndSizesAsTheyAre() {
        for (int ms = 1; ms <= 100; ms++) Profiler.record("t.page", ms * 1_000_000L);
        Profiler.sample("s.chars", 500);
        Profiler.sample("s.chars", 1500);
        var all = Profiler.drain();
        var t = all.stream().filter(s -> s.label().equals("t.page")).findFirst().orElseThrow();
        assertEquals("ms", t.unit());
        assertEquals(100, t.count());
        assertEquals(50.5, t.avg(), 0.001);
        assertEquals(95.0, t.p95(), 0.001);
        assertEquals(100.0, t.max(), 0.001);
        var s = all.stream().filter(x -> x.label().equals("s.chars")).findFirst().orElseThrow();
        assertEquals("n", s.unit());
        assertEquals(1000.0, s.avg(), 0.001);
        assertEquals(1500.0, s.max(), 0.001);
    }

    @Test void drainStartsAFreshWindowAndBusiestLabelComesFirst() {
        Profiler.record("rare", 1);
        Profiler.record("busy", 1);
        Profiler.record("busy", 1);
        assertEquals("busy", Profiler.drain().get(0).label());
        assertTrue(Profiler.drain().isEmpty());
    }

    @Test void percentileSpansOnlyTheRecentSamplesButCountCoversAll() {
        for (int i = 0; i < 2000; i++) Profiler.record("long", i < 1900 ? 1_000_000L : 9_000_000L);
        var s = Profiler.drain().get(0);
        assertEquals(2000, s.count());
        assertEquals(9.0, s.max(), 0.001);
        assertEquals(9.0, s.p95(), 0.001, "the newest 512 samples are mostly the slow ones");
    }

    @Test void stopMeasuresFromStart() throws Exception {
        long t = Profiler.start();
        Thread.sleep(5);
        Profiler.stop("slept", t);
        assertTrue(Profiler.drain().get(0).max() >= 4.0);
    }
}
