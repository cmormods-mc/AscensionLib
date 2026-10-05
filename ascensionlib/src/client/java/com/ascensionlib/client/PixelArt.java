package com.ascensionlib.client;

import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The warm pixel look (oak, aged bronze, parchment, chocolate, burgundy) for AscensionLib's own screens: nine-slice frames
 * and 16x16 tiles from {@code textures/gui/pixel}, drawn at integer positions and integer scale, nearest sampled. No blur,
 * shader or fractional scaling. Corners are drawn at 8 GUI px (half the 16 px source) and never stretch.
 */
final class PixelArt {
    static final int OUTLINE = 0xFF211510, OAK = 0xFF38271F, BRONZE = 0xFFA77B46, BRONZE_LIGHT = 0xFFE3BD7F, BRONZE_SHADOW = 0xFF67452C,
            PARCHMENT = 0xFFE5CCA1, INK = 0xFF40291E, CREAM = 0xFFF0DFBF, MUTED = 0xFFC6AC87, BURGUNDY = 0xFF773C38,
            BURGUNDY_LIGHT = 0xFFAF6A55, SAGE = 0xFFA9B781, ECHO = 0xFFB6A1C5, WELL = 0xFF2B1D17, PARCHMENT_DARK = 0xFFC9AE82;
    /** The quartz (white Pokedex) palette shared by the capture reveal and the inspection screen. */
    static final int Q_PANEL = 0xFFE4E9F0, Q_HEAD = 0xFFCBD3DF, Q_ROW = 0xFFF3F6FA, Q_LINE = 0xFFCDD4DF, Q_EDGE = 0xFF8E99AD,
            Q_INK = 0xFF2B3345, Q_MUTED = 0xFF66718A, Q_WELL = 0xFFC5CDD9, Q_WHITE = 0xFFEEF2F7;

    /** A quartz panel with a pixel bevel: slate edge, white highlight top-left, soft shade bottom-right. */
    static void qpanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, Q_EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, Q_PANEL);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFFFFFFFF);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, Q_WELL);
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, Q_WELL);
    }

    /** A rectangle outline {@code t} pixels thick. */
    static void ring(GuiGraphics g, int x, int y, int w, int h, int color, int t) {
        g.fill(x, y, x + w, y + t, color);
        g.fill(x, y + h - t, x + w, y + h, color);
        g.fill(x, y + t, x + t, y + h - t, color);
        g.fill(x + w - t, y + t, x + w, y + h - t, color);
    }

    private static final java.util.Map<String, Integer> TYPE_COLORS = java.util.Map.ofEntries(
            java.util.Map.entry("normal", 0xFF8A8A6E), java.util.Map.entry("fire", 0xFFC6523A), java.util.Map.entry("water", 0xFF4A7DB8),
            java.util.Map.entry("electric", 0xFFB8921F), java.util.Map.entry("grass", 0xFF5E9A4B), java.util.Map.entry("ice", 0xFF5FA3B4),
            java.util.Map.entry("fighting", 0xFFA8402F), java.util.Map.entry("poison", 0xFF8A4F9E), java.util.Map.entry("ground", 0xFFA8853F),
            java.util.Map.entry("flying", 0xFF7283C4), java.util.Map.entry("psychic", 0xFFC8506F), java.util.Map.entry("bug", 0xFF84962B),
            java.util.Map.entry("rock", 0xFF9A8638), java.util.Map.entry("ghost", 0xFF6A5A94), java.util.Map.entry("dragon", 0xFF5A4FC4),
            java.util.Map.entry("dark", 0xFF5A4A40), java.util.Map.entry("steel", 0xFF7F869B), java.util.Map.entry("fairy", 0xFFC97FA8));
    static int typeColor(String type) { return TYPE_COLORS.getOrDefault(type.toLowerCase(Locale.ROOT), 0xFF7F869B); }

    /** Pale quartz behind card art: a cool near-white with a slightly darker floor and a soft contact shadow. */
    static final int QUARTZ = 0xFFDDE3EC, QUARTZ_SHADE = 0xFFCFD6E2, QUARTZ_SHADOW = 0xFFB4BECF;
    /** Muted text readable on parchment (the cream MUTED is not). */
    static final int INK_MUTED = 0xFF7A5A3E;

    enum Frame {
        DARK("frame_dark"), PARCHMENT("frame_parchment"), BUTTON("button_normal"), BUTTON_HOVER("button_hover"),
        BUTTON_PRESSED("button_pressed"), BUTTON_DISABLED("button_disabled"), SECONDARY("button_secondary"), TAB("tab_active");
        final ResourceLocation texture;
        Frame(String name) { texture = ResourceLocation.fromNamespaceAndPath("ascensionlib", "textures/gui/pixel/" + name + ".png"); }
    }

    /** Tiles drawn by {@link #tile}; the enum keeps the texture paths in one place. */
    static final ResourceLocation BURGUNDY_TILE = ResourceLocation.fromNamespaceAndPath("ascensionlib", "textures/gui/pixel/tile_cloth_burgundy.png");

    /** The owner's rarity palette (design/rarity-colors.json): accent, trim and a dark panel for each rarity. */
    record RarityStyle(int accent, int trim, int panel) {}
    private static final RarityStyle[] STYLES = {
        new RarityStyle(0xFFC1C5CF, 0xFFDDDDDF, 0xFF293547),   // common: iron and slate
        new RarityStyle(0xFF1BD7E8, 0xFFA8E9F1, 0xFF073442),   // uncommon: cyan crystal
        new RarityStyle(0xFF38CCEF, 0xFF38D8F6, 0xFF103E79),   // rare: blue and cyan
        new RarityStyle(0xFFAF69FF, 0xFF9160E7, 0xFF2A1263),   // epic: indigo and violet
        new RarityStyle(0xFFFFD437, 0xFFFFEF9F, 0xFF25305B),   // legendary: gold and navy
        new RarityStyle(0xFFFF3448, 0xFFE1E2E8, 0xFF620512)};  // mythic: crimson, silver and white
    static int rarityIndex(String id) {
        return switch (id) { case "uncommon" -> 1; case "rare" -> 2; case "epic" -> 3; case "legendary" -> 4; case "mythical", "mythic" -> 5; default -> 0; };
    }
    static RarityStyle style(String id) { return STYLES[rarityIndex(id)]; }

    static void tile(GuiGraphics g, ResourceLocation texture, int x, int y, int w, int h) {
        if (w < 1 || h < 1) return;
        g.enableScissor(x, y, x + w, y + h);
        for (int ty = y; ty < y + h; ty += 16) for (int tx = x; tx < x + w; tx += 16) g.blit(texture, tx, ty, 0, 0, 16, 16, 16, 16);
        g.disableScissor();
    }

    private static final ResourceLocation OAK_TILE = ResourceLocation.fromNamespaceAndPath("ascensionlib", "textures/gui/pixel/tile_oak.png");
    private static final int SRC = 48, SRC_CORNER = 16;

    private PixelArt() {}

    static void frame(GuiGraphics g, Frame frame, int x, int y, int w, int h) {
        if (w < 2 || h < 2) return;
        int c = Math.max(1, Math.min(8, Math.min(w, h) / 2));
        int mw = w - 2 * c, mh = h - 2 * c, m = SRC - 2 * SRC_CORNER;
        var t = frame.texture;
        g.blit(t, x, y, c, c, 0, 0, SRC_CORNER, SRC_CORNER, SRC, SRC);
        g.blit(t, x + w - c, y, c, c, SRC - SRC_CORNER, 0, SRC_CORNER, SRC_CORNER, SRC, SRC);
        g.blit(t, x, y + h - c, c, c, 0, SRC - SRC_CORNER, SRC_CORNER, SRC_CORNER, SRC, SRC);
        g.blit(t, x + w - c, y + h - c, c, c, SRC - SRC_CORNER, SRC - SRC_CORNER, SRC_CORNER, SRC_CORNER, SRC, SRC);
        if (mw > 0) {
            g.blit(t, x + c, y, mw, c, SRC_CORNER, 0, m, SRC_CORNER, SRC, SRC);
            g.blit(t, x + c, y + h - c, mw, c, SRC_CORNER, SRC - SRC_CORNER, m, SRC_CORNER, SRC, SRC);
        }
        if (mh > 0) {
            g.blit(t, x, y + c, c, mh, 0, SRC_CORNER, SRC_CORNER, m, SRC, SRC);
            g.blit(t, x + w - c, y + c, c, mh, SRC - SRC_CORNER, SRC_CORNER, SRC_CORNER, m, SRC, SRC);
        }
        if (mw > 0 && mh > 0) g.blit(t, x + c, y + c, mw, mh, SRC_CORNER, SRC_CORNER, m, m, SRC, SRC);
    }

    /** A flat inset well: outline, then fill. Used for bars and unknown rows. */
    static void well(GuiGraphics g, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + h, OUTLINE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    static void oak(GuiGraphics g, int width, int height) {
        for (int ty = 0; ty < height; ty += 16) for (int tx = 0; tx < width; tx += 16) g.blit(OAK_TILE, tx, ty, 0, 0, 16, 16, 16, 16);
        g.fill(0, 0, width, height, 0x88000000);
    }

    /** Four bronze corner brackets: the selection / focus mark. */
    static void brackets(GuiGraphics g, int x, int y, int w, int h, int color) {
        int a = Math.min(5, Math.min(w, h) / 3);
        for (int cx : new int[] {x, x + w - a}) for (int cy : new int[] {y, y + h - 1}) g.fill(cx, cy, cx + a, cy + 1, color);
        for (int cx : new int[] {x, x + w - 1}) for (int cy : new int[] {y, y + h - a}) g.fill(cx, cy, cx + 1, cy + a, color);
    }

    /** Rarity accent, readable on both parchment and chocolate. */
    static int rarity(String id) { return style(id).accent(); }

    /** 7x7 pixel icons for modifier plates: '#' is drawn, anything else is left alone. */
    static final String[] ICON_SWORD = {"......#", ".....#.", "....#..", ".#.#...", "..#....", ".#.#...", "#......"};
    static final String[] ICON_SHIELD = {"#######", "#######", "#######", "#######", ".#####.", "..###..", "...#..."};
    static final String[] ICON_STAR = {"...#...", "...#...", "#######", ".#####.", "..###..", ".##.##.", ".#...#."};

    static void icon(GuiGraphics g, String[] rows, int x, int y, int color) { icon(g, rows, x, y, color, color); }

    /** As above with a second colour: '#' main, '+' a lighter main (a highlight), '*' the accent. */
    static void icon(GuiGraphics g, String[] rows, int x, int y, int main, int accent) {
        int light = lighten(main);
        for (int r = 0; r < rows.length; r++)
            for (int c = 0; c < rows[r].length(); c++) {
                char ch = rows[r].charAt(c);
                int color = ch == '#' ? main : ch == '+' ? light : ch == '*' ? accent : 0;
                if (color != 0) g.fill(x + c, y + r, x + c + 1, y + r + 1, color);
            }
    }

    private static int lighten(int argb) {
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        return 0xFF000000 | ((r + (255 - r) * 45 / 100) << 16) | ((g + (255 - g) * 45 / 100) << 8) | (b + (255 - b) * 45 / 100);
    }

    /** The six base stats in order: HP, Attack, Defence, Sp. Atk, Sp. Def, Speed. */
    static final String[] STAT_NAMES = {"HP", "Attack", "Defense", "Sp. Atk", "Sp. Def", "Speed"};
    private static final String[][] STAT_ICONS = {
        {".##.##.", "#+#####", "#######", "#######", ".#####.", "..###..", "...#..."},                 // HP: heart
        ICON_SWORD,                                                                                  // Attack: sword
        {"#######", "#+#####", "#+#####", "#######", ".#####.", "..###..", "...#..."},                 // Defense: kite shield
        {"...#...", "...#...", "..###..", "#######", "..###..", "...#...", "...#..."},                 // Sp. Atk: sparkle
        {"#######", "##*####", "#***###", "##*####", ".#####.", "..###..", "...#..."},                 // Sp. Def: kite shield, star upper left
        {"....##.", "...##..", "..##...", ".#####.", "...##..", "..##...", ".##...."}};                // Speed: lightning bolt
    private static final int[] STAT_COLORS = {0xFFD2483C, 0xFFB5482E, 0xFF4A7DB8, 0xFF8A4F9E, 0xFF4A7DB8, 0xFFC9A227};

    /** One stat's 7x7 icon at (x, y). */
    static void statIcon(GuiGraphics g, int index, int x, int y) {
        icon(g, STAT_ICONS[index], x, y, STAT_COLORS[index], 0xFFF2C230);
    }

    /** Same hues, darker, for text on parchment. */
    static int rarityOnParchment(String id) {
        return switch (id) {
            case "uncommon" -> 0xFF5C6B35;
            case "rare" -> 0xFF3F6486;
            case "epic" -> 0xFF6E4F86;
            case "legendary" -> 0xFF8A5A14;
            case "mythical", "mythic" -> BURGUNDY;
            default -> INK_MUTED;
        };
    }
}
