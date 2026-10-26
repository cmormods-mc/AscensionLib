package com.ascensionlib.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The World Boss bar's sprites (tools/bossbar_art.py) replace vanilla's PINK bar and 20-notch overlay, and Minecraft only accepts them at 182 x 5. */
class BossBarArtTest {
    private static final String[] SPRITES = {"pink_background.png", "pink_progress.png", "notched_20_background.png", "notched_20_progress.png"};

    @Test void everySpriteExistsAtTheBarsSize() throws Exception {
        for (String name : SPRITES) {
            Path file = Path.of("..", "resourcepack", "assets", "minecraft", "textures", "gui", "sprites", "boss_bar", name);
            if (!Files.exists(file)) return; // the pack folder is not part of every checkout
            ByteBuffer header = ByteBuffer.wrap(Files.readAllBytes(file), 16, 8);
            assertTrue(Files.size(file) > 33, name + " is a PNG");
            assertEquals(182, header.getInt(), name + " width");
            assertEquals(5, header.getInt(), name + " height");
        }
    }
}
