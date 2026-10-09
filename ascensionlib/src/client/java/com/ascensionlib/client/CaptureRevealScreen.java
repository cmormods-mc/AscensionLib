package com.ascensionlib.client;

import com.ascensionlib.scout.CaptureRevealPayload;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * The capture reveal: one centred card that flips to show the Pokemon, its ascension rarity and the modifiers it actually
 * rolled. Presentation only. The server saved the outcome before it sent it, and nothing here sends anything back, so
 * skipping, closing or reopening cannot reroll, grant, remove or duplicate a Pokemon.
 *
 * <p>A white Pokedex-style card in the same pixel frames as the rest of the mod, unique to this screen: the oak/bronze
 * frame carries a border ring in the rarity colour with a glow that grows with rarity; inside are pale quartz panels. The
 * left holds the dex number and type chip, the art (CobblemonCards' 48x32 sprite at exactly 2x when that mod is installed,
 * Cobblemon's own model otherwise), name, level and base stats, and a dark rarity footer; the right lists every rolled
 * modifier. Everything is laid out at 1:1 GUI pixels and the flip steps whole pixels, so no edge is resampled. The card
 * back shows no rarity. Reduced motion shows the finished card at once. A sound plays once per stage, never from the render
 * loop. Like the inspect screen it draws in {@link #renderBackground}, so the menu blur cannot reach it.
 */
final class CaptureRevealScreen extends Screen {
    private static final int FLIP_MS = 260;
    /** Cobblemon's own summary view places its model with this offset; the same value keeps whole models inside the portrait. */
    private static final double MODEL_OFFSET_Y = -10.0;
    private static final int ROW_H = 19, ROW_GAP = 1, LEFT_W = 112;
    // The quartz palette: a cool near-white with slate ink, used only on this screen.
    private static final int Q_PANEL = 0xFFE4E9F0, Q_HEAD = 0xFFCBD3DF, Q_ROW = 0xFFF3F6FA, Q_LINE = 0xFFCDD4DF, Q_EDGE = 0xFF8E99AD,
            Q_INK = 0xFF2B3345, Q_MUTED = 0xFF66718A, Q_WELL = 0xFFC5CDD9;
    private static final int CRIMSON = 0xFF8E1426, CRIMSON_DEEP = 0xFF7A0F20, Q_WHITE = 0xFFEEF2F7;
    private static final Map<String, Integer> TYPE_COLORS = Map.ofEntries(
            Map.entry("normal", 0xFF8A8A6E), Map.entry("fire", 0xFFC6523A), Map.entry("water", 0xFF4A7DB8), Map.entry("electric", 0xFFB8921F),
            Map.entry("grass", 0xFF5E9A4B), Map.entry("ice", 0xFF5FA3B4), Map.entry("fighting", 0xFFA8402F), Map.entry("poison", 0xFF8A4F9E),
            Map.entry("ground", 0xFFA8853F), Map.entry("flying", 0xFF7283C4), Map.entry("psychic", 0xFFC8506F), Map.entry("bug", 0xFF84962B),
            Map.entry("rock", 0xFF9A8638), Map.entry("ghost", 0xFF6A5A94), Map.entry("dragon", 0xFF5A4FC4), Map.entry("dark", 0xFF5A4A40),
            Map.entry("steel", 0xFF7F869B), Map.entry("fairy", 0xFFC97FA8));
    private final CaptureRevealPayload data;
    private final long opened = System.nanoTime();
    private final int rarity;
    private final PixelArt.RarityStyle style;
    private final ResourceLocation sprite;
    private final int dex;
    private long flipAt = -1;
    private boolean flipSound, doneSound;
    private ModelWidget model;
    private int cardW, cardH, cardX, cardY, portraitX, portraitY, portraitW, portraitH;
    private PixelButton inspect, cont;

    CaptureRevealScreen(CaptureRevealPayload data) {
        super(Component.translatable("screen.ascensionlib.capture"));
        this.data = data;
        this.style = PixelArt.style(data.detail().rarityId());
        this.rarity = PixelArt.rarityIndex(data.detail().rarityId());
        this.sprite = CardSprites.find(data.speciesId(), data.aspects());
        int number = 0;
        try {
            var species = PokemonSpecies.INSTANCE.getByIdentifier(ResourceLocation.parse(data.speciesId()));
            if (species != null) number = species.getNationalPokedexNumber();
        } catch (RuntimeException | LinkageError ignored) { }
        this.dex = number;
        if (AscensionClientSettings.reducedMotion) flipAt = 0;
    }

    /** Read once: the environment does not change while the game runs, and this is consulted every tick. */
    private static final boolean HOLD_BACK = System.getenv("ASCENSIONLIB_HOLD_BACK") != null;

    private long age() { return (System.nanoTime() - opened) / 1_000_000L; }
    /** Rarer outcomes wait a little longer before turning over by themselves; a click or Space turns it at once. */
    private long autoFlipMs() {
        if (HOLD_BACK) return Long.MAX_VALUE / 2;   // development only: hold the card face-down for screenshots
        return rarity >= 5 ? 1400 : rarity >= 4 ? 1000 : 700;
    }
    private float flip() {
        if (AscensionClientSettings.reducedMotion) return 1f;
        if (flipAt < 0) return 0f;
        return Math.min(1f, (age() - flipAt) / (float) FLIP_MS);
    }
    private boolean revealed() { return flip() >= 1f; }

    @Override protected void init() {
        cardW = Math.min(288, width - 8);
        cardH = Math.min(172, height - 40);
        cardX = (width - cardW) / 2;
        cardY = Math.max(4, (height - cardH - 26) / 2);
        portraitX = cardX + 8;
        portraitW = Math.min(LEFT_W, cardW / 2 - 12);
        portraitY = cardY + 6 + 16;
        portraitH = 66;
        int by = cardY + cardH + 6;
        inspect = new PixelButton(width / 2 - 62, by, 60, 18, Component.literal("Inspect"), b -> {
            if (revealed()) InspectClient.openOwnedById(this, data.pokemonId(), data.name());
        });
        cont = new PixelButton(width / 2 + 2, by, 60, 18, Component.literal("Continue"), b -> onClose()).primary();
        addRenderableWidget(inspect);
        addRenderableWidget(cont);
        model = null;
        if (sprite == null) {
            try {
                var species = PokemonSpecies.INSTANCE.getByIdentifier(ResourceLocation.parse(data.speciesId()));
                if (species != null) {
                    var renderable = new RenderablePokemon(species, new HashSet<>(data.aspects()), ItemStack.EMPTY);
                    model = new ModelWidget(portraitX, portraitY, portraitW, portraitH, renderable, 2.7f, 325f, MODEL_OFFSET_Y, false, false, 13);
                }
            } catch (RuntimeException | LinkageError e) {
                org.slf4j.LoggerFactory.getLogger("ascensionlib").warn("Capture reveal model unavailable for {}", data.speciesId(), e);
            }
        }
        if (AscensionClientSettings.reducedMotion) playDone();
    }

    private void advance() {
        if (flipAt < 0) { flipAt = age(); playFlip(); }
        else if (!revealed()) flipAt = age() - FLIP_MS;           // finish the turn at once
        else onClose();
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) return true;
        if (button == 0) { advance(); return true; }
        return false;
    }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { advance(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override public void tick() {
        if (flipAt < 0 && age() >= autoFlipMs()) { flipAt = age(); playFlip(); }
        if (revealed()) playDone();
        inspect.active = revealed();
    }

    private void playFlip() {
        if (flipSound) return;
        flipSound = true;
        play(SoundEvents.UI_LOOM_SELECT_PATTERN, 1.2f, 0.6f);
    }

    private void playDone() {
        if (doneSound) return;
        doneSound = true;
        switch (rarity) {
            case 0, 1 -> play(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0f, 0.5f);
            case 2 -> play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 0.7f);
            case 3 -> play(SoundEvents.PLAYER_LEVELUP, 1.2f, 0.4f);
            default -> play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.5f);
        }
    }

    private void play(SoundEvent event, float pitch, float volume) {
        if (!AscensionClientSettings.sounds || minecraft == null) return;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void onClose() {
        CaptureReveals.closed();
        minecraft.setScreen(null);
    }

    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        com.ascensionlib.Profiler.frame("reveal.frameGap");
        long profiled = com.ascensionlib.Profiler.start();
        drawBackground(g, mouseX, mouseY, delta);
        com.ascensionlib.Profiler.stop("reveal.background", profiled);
    }

    private void drawBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xA0140D09);
        float f = flip();
        long reveal = AscensionClientSettings.reducedMotion ? 10_000 : flipAt < 0 ? -1 : age() - flipAt - FLIP_MS;
        if (f >= 1f) halo(g, reveal);
        int w = f < .5f ? Math.round(cardW * (1 - 2 * f)) : Math.round(cardW * (2 * f - 1));
        if (w < 2) return;
        int x = cardX + (cardW - w) / 2;
        if (f < .5f) back(g, x, w);
        else if (f < 1f) {
            PixelArt.qpanel(g, x, cardY, w, cardH);
        } else front(g, mouseX, mouseY, delta);
    }

    /**
     * The glow around the border, in the rarity accent: every rarity has a little (Common one faint step), and it grows a
     * layer per rarity and a touch brighter. Stepped rectangles hugging the card, no flash and no full-screen fill. Epic and
     * above add drifting sparks; Mythic adds two slim light shafts.
     */
    private void halo(GuiGraphics g, long reveal) {
        float grow = Math.min(1f, Math.max(0f, reveal / 450f));
        int layers = 1 + rarity * 2;
        for (int k = layers; k >= 1; k--) {
            int pad = Math.round(k * 2 * grow);
            int alpha = Math.min(0x48, 0x14 + rarity * 5) - k * (Math.min(0x48, 0x14 + rarity * 5) / (layers + 1));
            if (pad < 1) continue;
            g.fill(cardX - pad, cardY - pad, cardX + cardW + pad, cardY + cardH + pad, (Math.max(0x0C, alpha) << 24) | (style.accent() & 0xFFFFFF));
        }
        if (rarity >= 3 && !AscensionClientSettings.reducedMotion) {
            int n = 8 + rarity * 3;
            for (int i = 0; i < n; i++) {
                long t = reveal + i * 211L;
                int sx = cardX - 10 + (i * 53) % (cardW + 20);
                int sy = cardY + cardH + 4 - (int) ((t / 14) % (cardH + 20));
                if (sy < cardY - 12) continue;
                int c = (i % 3 == 0 ? style.trim() : style.accent()) & 0xFFFFFF;
                g.fill(sx, sy, sx + 2, sy + 2, 0xFF000000 | c);
            }
        }
        if (rarity >= 5) {
            for (int side = 0; side < 2; side++) {
                int sx = side == 0 ? cardX - 14 : cardX + cardW + 11;
                g.fill(sx, cardY - 14, sx + 3, cardY + cardH + 14, 0x2A000000 | (style.trim() & 0xFFFFFF));
            }
        }
    }

    /**
     * The card back: a clean quartz-white border around a flat crimson field, one thin inner line, a small diamond and the
     * word ASCENSION. The same for every outcome, so it can never hint at the rarity. While the card turns it narrows to
     * nothing, so the lettering only shows once it is wide enough.
     */
    private void back(GuiGraphics g, int x, int w) {
        // White border: the quartz panel's own bevel, then a flat crimson field set well inside it.
        PixelArt.qpanel(g, x, cardY, w, cardH);
        if (w <= 24) return;
        int ix = x + 6, iy = cardY + 6, iw = w - 12, ih = cardH - 12;
        g.fill(ix, iy, ix + iw, iy + ih, Q_EDGE);
        g.fill(ix + 1, iy + 1, ix + iw - 1, iy + ih - 1, CRIMSON);
        if (iw > 24) ring(g, ix + 6, iy + 6, iw - 12, ih - 12, 0xCCEEF2F7, 1);   // one thin inner line
        if (iw < 150) return;
        int cx = x + w / 2, cy = cardY + cardH / 2;
        diamond(g, cx, cy - 24, 5, Q_WHITE);
        diamond(g, cx, cy - 24, 2, CRIMSON);
        String word = "ASCENSION";
        int tw = font.width(word) * 2;
        g.pose().pushPose();
        g.pose().translate(cx - tw / 2, cy - 7, 0);
        g.pose().scale(2f, 2f, 1f);
        g.drawString(font, word, 1, 1, 0xFF4A0814, false);
        g.drawString(font, word, 0, 0, Q_WHITE, false);
        g.pose().popPose();
    }

    /** A diamond of whole pixels, {@code r} pixels from centre to point. */
    private static void diamond(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int span = r - Math.abs(dy);
            g.fill(cx - span, cy + dy, cx + span + 1, cy + dy + 1, color);
        }
    }

    /** A quartz panel with a pixel bevel: slate edge, white highlight top-left, soft shade bottom-right. */
    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, Q_EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, Q_PANEL);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFFFFFFFF);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, Q_WELL);
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, Q_WELL);
    }

    private void front(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // A clean white quartz border (no wood): the rarity ring sits on it.
        PixelArt.qpanel(g, cardX, cardY, cardW, cardH);
        // The border ring in the rarity colour (a darker line under it, a trim-coloured highlight above it for the rarer tiers).
        int ring = style.accent();
        ring(g, cardX + 4, cardY + 4, cardW - 8, cardH - 8, 0xFF000000 | (ring & 0xFFFFFF), 2);
        ring(g, cardX + 6, cardY + 6, cardW - 12, cardH - 12, 0xFF1B120D, 1);
        if (rarity >= 3) ring(g, cardX + 3, cardY + 3, cardW - 6, cardH - 6, 0xCC000000 | (style.trim() & 0xFFFFFF), 1);
        var d = data.detail();
        int x0 = cardX + 8, lw = portraitW, top = cardY + 8, bottom = cardY + cardH - 8, y;

        // ---- left panel: dex number and type, art, name, level, base stats, rarity footer ---------------------------------
        panel(g, x0, top - 0, lw, bottom - top);
        g.fill(x0 + 2, top + 2, x0 + lw - 2, top + 16, Q_HEAD);
        g.fill(x0 + 2, top + 16, x0 + lw - 2, top + 17, Q_LINE);
        g.drawString(font, dex > 0 ? String.format("No. %04d", dex) : "No. ----", x0 + 6, top + 5, Q_MUTED, false);
        if (!d.types().isEmpty()) {
            String type = d.types().get(0).toLowerCase(Locale.ROOT);
            String label = type.toUpperCase(Locale.ROOT);
            int cw = font.width(label) + 6;
            int cx = x0 + lw - 5 - cw;
            g.fill(cx, top + 4, cx + cw, top + 14, 0xFF1B120D);
            g.fill(cx + 1, top + 5, cx + cw - 1, top + 13, TYPE_COLORS.getOrDefault(type, 0xFF7F869B));
            g.drawString(font, label, cx + 3, top + 5, 0xFFFFFFFF, false);
        }
        portraitX = x0 + 2;
        portraitY = top + 17;
        int artW = lw - 4, artH = portraitH;
        g.fill(portraitX, portraitY, portraitX + artW, portraitY + artH, Q_ROW);
        g.fill(portraitX, portraitY + artH - 20, portraitX + artW, portraitY + artH, Q_PANEL);
        int shadowW = Math.min(artW - 20, 60), shadowY = portraitY + (artH + 64) / 2 - 6, shadowX = portraitX + (artW - shadowW) / 2;
        g.fill(shadowX + 6, shadowY, shadowX + shadowW - 6, shadowY + 1, Q_LINE);
        g.fill(shadowX, shadowY + 1, shadowX + shadowW, shadowY + 3, Q_LINE);
        g.fill(shadowX + 6, shadowY + 3, shadowX + shadowW - 6, shadowY + 4, Q_LINE);
        drawArt(g, mouseX, mouseY, delta, artW, artH);
        y = portraitY + artH;
        g.fill(x0 + 2, y, x0 + lw - 2, y + 1, Q_LINE);
        y += 4;
        String name = font.plainSubstrByWidth(data.name(), lw - 8);
        g.drawString(font, name, x0 + (lw - font.width(name)) / 2, y, Q_INK, false);
        y += 11;
        String sub = font.plainSubstrByWidth("Lv " + d.level() + " · " + String.join(" / ", d.types()), lw - 8);
        g.drawString(font, sub, x0 + (lw - font.width(sub)) / 2, y, Q_MUTED, false);
        y += 12;
        int footerY = bottom - 2 - 16;
        if (d.baseStats().size() == 6 && footerY - y >= 27) {
            String[] names = {"HP", "Atk", "Def", "SpA", "SpD", "Spe"};
            int colW = (lw - 8) / 2;
            for (int i = 0; i < 6; i++) {
                int sx = x0 + 5 + (i % 2) * colW, sy = y + (i / 2) * 9, v = d.baseStats().get(i), bw = colW - 24;
                PixelArt.statIcon(g, i, sx + 4, sy + 1);
                g.fill(sx + 17, sy + 2, sx + 17 + bw, sy + 7, Q_EDGE);
                g.fill(sx + 18, sy + 3, sx + 16 + bw, sy + 6, Q_WELL);
                g.fill(sx + 18, sy + 3, sx + 18 + Math.max(1, Math.min(bw - 3, v * (bw - 3) / 255)), sy + 6, style.panel());
            }
        }
        // Rarity footer: dark band with the label between two dots, and a pip per tier.
        g.fill(x0 + 2, footerY, x0 + lw - 2, bottom - 2, style.panel());
        g.fill(x0 + 2, footerY, x0 + lw - 2, footerY + 1, style.accent());
        String label = (d.rarityId().equals("mythical") ? "MYTHIC" : d.rarityId().toUpperCase(Locale.ROOT)) + (d.uniqueName().isEmpty() ? "" : " · UNIQUE");
        label = font.plainSubstrByWidth(label, lw - 30);
        int lx = x0 + (lw - font.width(label)) / 2;
        g.drawString(font, label, lx, footerY + 4, style.accent(), false);
        g.fill(lx - 7, footerY + 7, lx - 4, footerY + 10, style.trim());
        g.fill(lx + font.width(label) + 4, footerY + 7, lx + font.width(label) + 7, footerY + 10, style.trim());

        // ---- right panel: every rolled modifier -------------------------------------------------------------------------
        int rx = x0 + lw + 6, rw = cardX + cardW - 8 - rx, ry = top;
        panel(g, rx, ry, rw, bottom - top);
        g.fill(rx + 2, ry + 2, rx + rw - 2, ry + 16, Q_HEAD);
        g.fill(rx + 2, ry + 16, rx + rw - 2, ry + 17, Q_LINE);
        g.drawString(font, "Affixes", rx + 6, ry + 5, Q_INK, false);
        int total = d.slots().size() + (d.uniqueName().isEmpty() ? 0 : 1);
        String count = total + " / " + total;
        g.drawString(font, count, rx + rw - 6 - font.width(count), ry + 5, Q_MUTED, false);
        ry += 19;
        int capacity = Math.max(0, (bottom - 4 - ry) / (ROW_H + ROW_GAP));
        int shown = total <= capacity ? total : Math.max(0, capacity - 1);
        int n = 0;
        if (!d.uniqueName().isEmpty() && shown > 0) {
            affixRow(g, rx + 2, ry, rw - 4, "UNIQUE  " + d.uniqueName(), "", 0, true, false);
            ry += ROW_H + ROW_GAP;
            n++;
        }
        for (var slot : d.slots()) {
            if (n >= shown) break;
            affixRow(g, rx + 2, ry, rw - 4, slot.name(), slot.value() + "%", slot.rank(), false, slot.category().equals("prefix"));
            ry += ROW_H + ROW_GAP;
            n++;
        }
        if (total > shown) g.drawString(font, "+" + (total - shown) + " more · Inspect", rx + 6, ry + 5, Q_MUTED, false);
        if (total == 0) g.drawString(font, "No modifiers rolled.", rx + 6, ry + 5, Q_MUTED, false);
    }

    private static void ring(GuiGraphics g, int x, int y, int w, int h, int color, int t) {
        g.fill(x, y, x + w, y + t, color);
        g.fill(x, y + h - t, x + w, y + h, color);
        g.fill(x, y + t, x + t, y + h - t, color);
        g.fill(x + w - t, y + t, x + w, y + h - t, color);
    }

    /**
     * One modifier on the quartz list, 19 px high: a small tinted icon square (sword for a prefix, shield for a suffix, star
     * for the Unique), the name, the rolled value on the right, a roman rank and five rank pips (filled in the rarity colour
     * up to the slot's rank) under the name, and a hairline between rows.
     */
    private void affixRow(GuiGraphics g, int x, int y, int w, String name, String value, int rank, boolean unique, boolean prefix) {
        g.fill(x, y, x + w, y + ROW_H, unique ? 0xFFE9E1F4 : Q_ROW);
        g.fill(x, y + ROW_H, x + w, y + ROW_H + 1, unique ? PixelArt.ECHO : Q_LINE);
        int tint = unique ? PixelArt.ECHO : prefix ? 0xFFC9694F : 0xFF6F9A52;
        g.fill(x + 3, y + 4, x + 14, y + 15, 0xFF1B120D);
        g.fill(x + 4, y + 5, x + 13, y + 14, tint);
        PixelArt.icon(g, unique ? PixelArt.ICON_STAR : prefix ? PixelArt.ICON_SWORD : PixelArt.ICON_SHIELD, x + 5, y + 6, 0xFFFFFFFF);
        int vw = value.isEmpty() ? 0 : font.width(value) + 6;
        g.drawString(font, font.plainSubstrByWidth(name, w - 24 - vw), x + 19, unique ? y + 6 : y + 2, Q_INK, false);
        if (!value.isEmpty()) g.drawString(font, "+" + value, x + w - 5 - font.width("+" + value), y + 2, 0xFF7A2E22, false);
        if (!unique) {
            for (int i = 0; i < 5; i++) {
                int px = x + 19 + i * 7;
                g.fill(px, y + 11, px + 5, y + 16, Q_EDGE);
                g.fill(px + 1, y + 12, px + 4, y + 15, i < rank ? style.panel() : Q_WELL);
                if (i < rank) g.fill(px + 1, y + 12, px + 4, y + 13, style.accent());
            }
        }
    }

    /** CobblemonCards' sprite at exactly 2x when that mod is installed; otherwise Cobblemon's own model, then a plain label. */
    private void drawArt(GuiGraphics g, int mouseX, int mouseY, float delta, int artW, int artH) {
        if (sprite != null) {
            g.blit(sprite, portraitX + (artW - 96) / 2, portraitY + (artH - 64) / 2, 96, 64, 0, 0, 48, 32, 48, 32);
            return;
        }
        if (model != null) {
            try {
                model.setX(portraitX);
                model.setY(portraitY);
                g.enableScissor(portraitX, portraitY, portraitX + artW, portraitY + artH);
                model.render(g, mouseX, mouseY, delta);
                return;
            } catch (RuntimeException | LinkageError e) {
                org.slf4j.LoggerFactory.getLogger("ascensionlib").warn("Capture reveal model failed; using fallback", e);
                model = null;
            } finally {
                g.disableScissor();
            }
        }
        g.drawString(font, "POKÉMON", portraitX + (artW - font.width("POKÉMON")) / 2, portraitY + (artH - 8) / 2, Q_MUTED, false);
    }
}
