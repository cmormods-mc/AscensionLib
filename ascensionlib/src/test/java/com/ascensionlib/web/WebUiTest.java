package com.ascensionlib.web;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class WebUiTest {
    @Test void aBaseSpriteMapsToItsSpeciesId() {
        assertEquals("mewtwo", WebUi.spriteSpecies("0150_mewtwo", "mewtwo.png"));
        assertEquals("mr_mime", WebUi.spriteSpecies("0122_mrmime", "mr_mime.png"), "Underscores in the id are not in the folder name");
    }

    @Test void formsShiniesAndStrangeNamesAreSkipped() {
        assertNull(WebUi.spriteSpecies("0150_mewtwo", "mewtwo_shiny.png"));
        assertNull(WebUi.spriteSpecies("0150_mewtwo", "mewtwo_mega_x.png"));
        assertNull(WebUi.spriteSpecies("0150_mewtwo", "mewtwo.txt"));
        assertNull(WebUi.spriteSpecies("0150_mewtwo", "../mewtwo.png"));
        assertNull(WebUi.spriteSpecies("mewtwo", "mewtwo.png"));
    }

    @Test void fieldReadersBoundAndDefaultTheUntrustedJson() {
        var o = JsonParser.parseString("{\"a\":\"short\",\"b\":\"" + "x".repeat(50) + "\",\"n\":7,\"s\":\"7\",\"t\":true,\"obj\":{}}").getAsJsonObject();
        assertEquals("short", WebUi.str(o, "a", 36));
        assertEquals("", WebUi.str(o, "b", 36), "Too long is treated as absent");
        assertEquals("", WebUi.str(o, "obj", 36));
        assertEquals(7, WebUi.num(o, "n"));
        assertEquals(-1, WebUi.num(o, "s"), "A string is not a number");
        assertEquals(-1, WebUi.num(o, "missing"));
        assertTrue(WebUi.bool(o, "t"));
        assertFalse(WebUi.bool(o, "n"));
    }
}
