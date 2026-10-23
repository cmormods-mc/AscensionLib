package com.ascensionlib.telemetry;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TelemetryWriterTest {
    @TempDir Path dir;

    private static long at(String date) { return LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() + 1000; }

    @Test void linesGoToTheFileOfTheirUtcDayAndSurviveClose() throws Exception {
        var clock = new AtomicLong(at("2026-10-22"));
        try (var writer = new TelemetryWriter(dir, clock::get, 30)) {
            assertTrue(writer.offer("{\"n\":1}"));
            assertTrue(writer.offer("{\"n\":2}"));
            Thread.sleep(700);       // the writer has written both lines under the first day
            clock.set(at("2026-10-23"));
            assertTrue(writer.offer("{\"n\":3}"));
        }
        assertEquals(List.of("{\"n\":1}", "{\"n\":2}"), Files.readAllLines(dir.resolve("events-2026-10-22.jsonl"), StandardCharsets.UTF_8));
        assertEquals(List.of("{\"n\":3}"), Files.readAllLines(dir.resolve("events-2026-10-23.jsonl"), StandardCharsets.UTF_8));
    }

    @Test void aClosedWriterRefusesAndCountsTheLine() throws Exception {
        var writer = new TelemetryWriter(dir, () -> at("2026-10-22"), 30);
        writer.close();
        assertFalse(writer.offer("late"));
        assertEquals(1, writer.dropped());
    }

    @Test void oldFilesAreDeletedOnOpenAndStrangersAreLeftAlone() throws Exception {
        Files.writeString(dir.resolve("events-2026-08-01.jsonl"), "old\n");
        Files.writeString(dir.resolve("events-2026-10-20.jsonl"), "recent\n");
        Files.writeString(dir.resolve("events-notadate.jsonl"), "x\n");
        Files.writeString(dir.resolve("notes.txt"), "x\n");
        new TelemetryWriter(dir, () -> at("2026-10-22"), 30).close();
        assertFalse(Files.exists(dir.resolve("events-2026-08-01.jsonl")));
        assertTrue(Files.exists(dir.resolve("events-2026-10-20.jsonl")));
        assertTrue(Files.exists(dir.resolve("events-notadate.jsonl")));
        assertTrue(Files.exists(dir.resolve("notes.txt")));
    }

    @Test void aSecondRunAppendsToTheDaysFile() throws Exception {
        for (int run = 1; run <= 2; run++) {
            try (var writer = new TelemetryWriter(dir, () -> at("2026-10-22"), 30)) { writer.offer("{\"run\":" + run + "}"); }
        }
        assertEquals(List.of("{\"run\":1}", "{\"run\":2}"), Files.readAllLines(dir.resolve("events-2026-10-22.jsonl")));
    }
}
