package com.ascensionlib.web;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/** A page is code the player can edit: what it reports about itself must be bounded before it reaches the performance log. */
class UiMetricsTest {
    private static com.google.gson.JsonArray array(String json) { return JsonParser.parseString(json).getAsJsonArray(); }

    @Test void numbersAreClampedAndNonNumbersSkipped() {
        var out = UiMetrics.samples(array("[1.5, -4, 999999, \"x\", null, {\"a\":1}, 7]"));
        assertArrayEquals(new double[] {1.5, 0, UiMetrics.MAX_MS, 7}, out, 0.0001);
    }

    @Test void aLongArrayIsCutToTheSampleLimit() {
        var many = new StringBuilder("[");
        for (int i = 0; i < 500; i++) many.append(i == 0 ? "1" : ",1");
        assertEquals(UiMetrics.MAX_SAMPLES, UiMetrics.samples(array(many.append("]").toString())).length);
    }
}
