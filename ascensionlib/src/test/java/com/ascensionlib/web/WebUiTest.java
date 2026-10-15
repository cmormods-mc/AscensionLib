package com.ascensionlib.web;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class WebUiTest {
    @Test void aSpriteIsFoundByDexNumberAndFlatSpeciesName() {
        assertEquals("0150_mewtwo/mewtwo.png", WebSprites.fileFor(150, "mewtwo"));
        assertEquals("0122_mrmime/mr_mime.png", WebSprites.fileFor(122, "mr_mime"), "Underscores are in the file name but not the folder name");
        assertEquals("1025_pecharunt/pecharunt.png", WebSprites.fileFor(1025, "pecharunt"));
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
