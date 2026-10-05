package com.ascensionlib.client;

import com.ascensionlib.scout.OwnedInspectPayload;
import com.ascensionlib.scout.ScoutPayloads;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Native inspection surface in the same white Pokedex style as the capture reveal. All ascension fields come from
 * permission-filtered server packets.
 *
 * <p>Left: dex number and type chip, the Pokemon's picture (CobblemonCards sprite at 2x, else Cobblemon's model), name,
 * level, all six base stats with values, and a rarity footer. Right: every modifier with its rolled value, the band its rank
 * rolls in and five rank pips, plus a strip that spells out what the hovered or clicked modifier does. A bottom bar carries
 * where the Pokemon came from, how far it has come and its pending upgrades. The frame's border ring takes the rarity colour.
 *
 * <p>Laid out at 1:1 GUI pixels for a 400x236 panel (fits a 240 px GUI height). Everything is drawn in
 * {@link #renderBackground}: in 1.21.1 {@code Screen.render} calls it first and applies the menu blur there, so anything
 * drawn before {@code super.render} would be blurred along with the world.
 */
public class AscendInspectScreen extends Screen {
    private static final int W = 400, H = 236, ROW_H = 19, ROW_GAP = 1;
    private static final String[] STATS = {"HP", "Atk", "Def", "SpA", "SpD", "Spe"};
    private final Screen parent;
    private final String ownedId;
    private final boolean pvp;
    private String title;
    private OwnedInspectPayload.Result owned;
    private ScoutPayloads.State shown = ScoutPayloads.State.none(0);
    private int selected;
    private int left, top, pw, ph;
    private PixelButton scout;
    private boolean waiting;
    private long sentAt;
    private CardArt art;
    private String artKey = "";
    private int selectedRow;
    private final List<int[]> rowRects = new ArrayList<>();   // x, y, w, h of each drawn modifier row

    private record Row(String kind, String name, String value, int rank, int min, int max, String condition, boolean unique, boolean prefix) {}

    public AscendInspectScreen(Screen parent, String ownedId, String title) {
        this(parent, ownedId, title, false);
    }

    public AscendInspectScreen(Screen parent, String ownedId, String title, boolean pvp) {
        super(Component.translatable("screen.ascensionlib.inspect"));
        this.parent = parent;
        this.ownedId = ownedId;
        this.pvp = pvp;
        this.title = title;
    }

    /** Asks the server for this Pokemon again (after an upgrade changed it). */
    void refreshOwned() {
        if (ownedId != null && ClientPlayNetworking.canSend(OwnedInspectPayload.Request.TYPE))
            ClientPlayNetworking.send(new OwnedInspectPayload.Request(ownedId));
    }

    public void acceptOwned(OwnedInspectPayload.Result value) {
        if (!value.pokemonId().equals(ownedId)) return;
        owned = value;
        if (!value.name().isEmpty()) title = value.name();
        rebuildWidgets();
    }

    @Override protected void init() {
        pw = Math.min(W, width - 8);
        ph = Math.min(H, height - 8);
        left = (width - pw) / 2;
        top = (height - ph) / 2;
        shown = ScoutClientState.get();
        selected = Math.min(selected, Math.max(0, shown.entries().size() - 1));
        int by = top + ph - 8 - 19;
        addRenderableWidget(new PixelButton(left + pw - 8 - 60, by, 60, 18, Component.literal("Close"), b -> onClose()));
        if (owned != null && owned.detail().isPresent()) {
            String label = owned.pending() > 0 ? "Upgrade (" + owned.pending() + ")" : "Upgrade";
            addRenderableWidget(new PixelButton(left + pw - 8 - 60 - 4 - 84, by, 84, 18, Component.literal(label), b -> CraftClient.open(this, ownedId)).primary());
        }
        if (ownedId == null && !pvp) {
            scout = new PixelButton(left + pw / 2 - 47, by, 94, 18, Component.translatable("button.ascensionlib.scout"), b -> {
                if (!canScout()) return;
                waiting = true;
                sentAt = System.currentTimeMillis();
                ClientPlayNetworking.send(new ScoutPayloads.Use(shown.encounterId(), entry().subjectId()));
                b.active = false;
            });
            scout.active = canScout();
            scout.primary();
            addRenderableWidget(scout);
            if (shown.entries().size() > 1) {
                addRenderableWidget(new PixelButton(left + 10, by, 18, 18, Component.literal("<"),
                        b -> { selected = Math.floorMod(selected - 1, shown.entries().size()); selectedRow = 0; rebuildWidgets(); }));
                addRenderableWidget(new PixelButton(left + 32, by, 18, 18, Component.literal(">"),
                        b -> { selected = (selected + 1) % shown.entries().size(); selectedRow = 0; rebuildWidgets(); }));
            }
        }
    }

    private ScoutPayloads.Entry entry() { return shown.entries().isEmpty() ? null : shown.entries().get(selected); }
    private boolean canScout() { return entry() != null && entry().detail().isEmpty() && shown.scouters() > 0
            && !waiting && ClientPlayNetworking.canSend(ScoutPayloads.Use.TYPE); }
    private ScoutPayloads.Detail detail() { return pvp ? null : ownedId != null ? owned == null ? null : owned.detail().orElse(null)
            : entry() == null ? null : entry().detail().orElse(null); }

    @Override public void tick() {
        if (ownedId == null && ScoutClientState.get() != shown) { waiting = false; rebuildWidgets(); }
        if (waiting && System.currentTimeMillis() - sentAt > 3000) { waiting = false; if (scout != null) scout.active = canScout(); }
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(parent); }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) return true;
        if (button == 0) for (int i = 0; i < rowRects.size(); i++) {
            int[] r = rowRects.get(i);
            if (x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3]) { selectedRow = i; return true; }
        }
        return false;
    }

    /** The picture source: the owner's species and aspects when known, else the enemy's name (base form). */
    private CardArt art() {
        String id, key;
        List<String> aspects = List.of();
        if (owned != null && !owned.extra().speciesId().isEmpty()) {
            id = owned.extra().speciesId();
            aspects = owned.extra().aspects();
        } else {
            String name = ownedId != null ? title : entry() == null ? title : entry().name();
            id = CardArt.idForName(name);
        }
        key = id + "|" + String.join(",", aspects);
        if (art == null || !key.equals(artKey)) { art = new CardArt(id, aspects); artKey = key; }
        return art;
    }

    private List<Row> rows(ScoutPayloads.Detail d) {
        List<Row> rows = new ArrayList<>();
        if (d == null) return rows;
        if (!d.uniqueName().isEmpty())
            rows.add(new Row("UNIQUE", d.uniqueName(), "", 0, -1, -1, "A Unique power: fixed tuning, no ordinary rank.", true, false));
        for (var slot : d.slots())
            rows.add(new Row(slot.category(), slot.name(), slot.value() + "%", slot.rank(), slot.min(), slot.max(), slot.condition(), false,
                    slot.category().equals("prefix")));
        return rows;
    }

    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // A plain warm dim over the world: no blur, so the panel and its text stay crisp.
        g.fill(0, 0, width, height, 0xB0140D09);
        int x = left, y = top;
        var d = detail();
        var style = d == null ? null : PixelArt.style(d.rarityId());
        PixelArt.qpanel(g, x, y, pw, ph);
        // The border ring takes the rarity colour (bronze while it is not known).
        PixelArt.ring(g, x + 4, y + 4, pw - 8, ph - 8, style == null ? PixelArt.BRONZE : style.accent(), 2);
        PixelArt.ring(g, x + 6, y + 6, pw - 12, ph - 12, 0xFF1B120D, 1);

        int lw = Math.min(150, pw * 38 / 100), px = x + 8, pTop = y + 8, pBottom = y + ph - 8 - 24;
        String name = pvp || ownedId != null ? title : entry() == null ? title : entry().name();

        // ---- left panel ---------------------------------------------------------------------------------------------
        PixelArt.qpanel(g, px, pTop, lw, pBottom - pTop);
        g.fill(px + 2, pTop + 2, px + lw - 2, pTop + 16, PixelArt.Q_HEAD);
        g.fill(px + 2, pTop + 16, px + lw - 2, pTop + 17, PixelArt.Q_LINE);
        var picture = art();
        g.drawString(font, picture.dex > 0 ? String.format("No. %04d", picture.dex) : "No. ----", px + 6, pTop + 5, PixelArt.Q_MUTED, false);
        if (d != null && !d.types().isEmpty()) {
            String label = d.types().get(0).toUpperCase(Locale.ROOT);
            int cw = font.width(label) + 6, cx = px + lw - 5 - cw;
            g.fill(cx, pTop + 4, cx + cw, pTop + 14, 0xFF1B120D);
            g.fill(cx + 1, pTop + 5, cx + cw - 1, pTop + 13, PixelArt.typeColor(d.types().get(0)));
            g.drawString(font, label, cx + 3, pTop + 5, 0xFFFFFFFF, false);
        }
        int ax = px + 2, ay = pTop + 17, aw = lw - 4, ah = 66;
        g.fill(ax, ay, ax + aw, ay + ah, PixelArt.Q_ROW);
        g.fill(ax, ay + ah - 20, ax + aw, ay + ah, PixelArt.Q_PANEL);
        int sw = Math.min(aw - 20, 60), sy = ay + (ah + 64) / 2 - 6, sx = ax + (aw - sw) / 2;
        g.fill(sx + 6, sy, sx + sw - 6, sy + 1, PixelArt.Q_LINE);
        g.fill(sx, sy + 1, sx + sw, sy + 3, PixelArt.Q_LINE);
        g.fill(sx + 6, sy + 3, sx + sw - 6, sy + 4, PixelArt.Q_LINE);
        picture.draw(g, font, ax, ay, aw, ah, mouseX, mouseY, 0f, "?");
        int cy = ay + ah;
        g.fill(px + 2, cy, px + lw - 2, cy + 1, PixelArt.Q_LINE);
        cy += 4;
        String shownName = font.plainSubstrByWidth(name, lw - 8);
        g.drawString(font, shownName, px + (lw - font.width(shownName)) / 2, cy, PixelArt.Q_INK, false);
        cy += 11;
        String sub = d == null ? (pvp ? "Ascension inactive in PvP" : ownedId != null ? owned == null ? "Loading server data…" : "No profile available"
                : "Details not revealed")
                : "Lv " + d.level() + " · " + String.join(" / ", d.types());
        sub = font.plainSubstrByWidth(sub, lw - 8);
        g.drawString(font, sub, px + (lw - font.width(sub)) / 2, cy, PixelArt.Q_MUTED, false);
        cy += 12;
        for (int i = 0; i < 6; i++) {
            int sty = cy + i * 10, bx = px + 30, bw = lw - 6 - 30 - 24;
            PixelArt.statIcon(g, i, px + 9, sty + 1);
            if (mouseX >= px + 7 && mouseX < px + 20 && mouseY >= sty && mouseY < sty + 10)
                g.renderTooltip(font, Component.literal(PixelArt.STAT_NAMES[i]), mouseX, mouseY);
            g.fill(bx, sty + 2, bx + bw, sty + 8, PixelArt.Q_EDGE);
            g.fill(bx + 1, sty + 3, bx + bw - 1, sty + 7, PixelArt.Q_WELL);
            if (d != null && i < d.baseStats().size()) {
                int v = d.baseStats().get(i);
                g.fill(bx + 1, sty + 3, bx + 1 + Math.max(1, Math.min(bw - 2, v * (bw - 2) / 255)), sty + 7, style.panel());
                String vs = String.valueOf(v);
                g.drawString(font, vs, px + lw - 6 - font.width(vs), sty, PixelArt.Q_INK, false);
            } else g.drawString(font, "?", px + lw - 6 - font.width("?"), sty, PixelArt.Q_MUTED, false);
        }
        int footerY = pBottom - 2 - 16;
        g.fill(px + 2, footerY, px + lw - 2, pBottom - 2, style == null ? 0xFF46526A : style.panel());
        g.fill(px + 2, footerY, px + lw - 2, footerY + 1, style == null ? PixelArt.Q_EDGE : style.accent());
        String grade = d == null ? "RARITY ?" : (d.rarityId().equals("mythical") ? "MYTHIC" : d.rarityId().toUpperCase(Locale.ROOT))
                + (d.uniqueName().isEmpty() ? "" : " · UNIQUE");
        grade = font.plainSubstrByWidth(grade, lw - 30);
        int gx = px + (lw - font.width(grade)) / 2;
        int gc = style == null ? PixelArt.Q_ROW : style.accent();
        g.drawString(font, grade, gx, footerY + 4, gc, false);
        g.fill(gx - 7, footerY + 7, gx - 4, footerY + 10, style == null ? PixelArt.Q_LINE : style.trim());
        g.fill(gx + font.width(grade) + 4, footerY + 7, gx + font.width(grade) + 7, footerY + 10, style == null ? PixelArt.Q_LINE : style.trim());

        // ---- right panel: modifiers, then what the chosen one does ---------------------------------------------------
        int rx = px + lw + 6, rw = x + pw - 8 - rx;
        PixelArt.qpanel(g, rx, pTop, rw, pBottom - pTop);
        g.fill(rx + 2, pTop + 2, rx + rw - 2, pTop + 16, PixelArt.Q_HEAD);
        g.fill(rx + 2, pTop + 16, rx + rw - 2, pTop + 17, PixelArt.Q_LINE);
        g.drawString(font, "Affixes", rx + 6, pTop + 5, PixelArt.Q_INK, false);
        var rows = rows(d);
        String count;
        if (owned != null && d != null) {
            int prefixes = (int) d.slots().stream().filter(s -> s.category().equals("prefix")).count();
            int suffixes = d.slots().size() - prefixes;
            count = "Prefix " + prefixes + "/" + owned.extra().prefixCap() + " · Suffix " + suffixes + "/" + owned.extra().suffixCap();
        } else count = d == null ? "? / ?" : rows.size() + " / " + rows.size();
        g.drawString(font, count, rx + rw - 6 - font.width(count), pTop + 5, PixelArt.Q_MUTED, false);
        int ry = pTop + 19, stripH = 22, stripY = pBottom - 2 - stripH;
        rowRects.clear();
        int capacity = Math.max(0, (stripY - 3 - ry) / (ROW_H + ROW_GAP));
        int hover = -1;
        if (d == null) {
            for (int i = 0; i < 3; i++) {
                g.fill(rx + 2, ry, rx + rw - 2, ry + ROW_H, PixelArt.Q_ROW);
                g.fill(rx + 2, ry + ROW_H, rx + rw - 2, ry + ROW_H + 1, PixelArt.Q_LINE);
                g.drawString(font, "?   ?   ?   ?", rx + 22, ry + 5, PixelArt.Q_MUTED, false);
                g.drawString(font, "??%", rx + rw - 8 - font.width("??%"), ry + 5, PixelArt.Q_MUTED, false);
                ry += ROW_H + ROW_GAP;
            }
            fitString(g, ownedId != null ? owned == null ? "Loading server data…" : "This Pokémon has no ascension profile."
                    : pvp ? "Ascension is inactive in PvP." : "Scout to reveal rarity and modifiers.", rx + 6, ry + 4, rw - 12, PixelArt.Q_MUTED);
        } else {
            int shownRows = Math.min(rows.size(), capacity);
            int pointed = -1;
            for (int i = 0; i < shownRows; i++) {
                int rowY = ry + i * (ROW_H + ROW_GAP);
                if (mouseX >= rx + 2 && mouseX < rx + rw - 2 && mouseY >= rowY && mouseY < rowY + ROW_H) pointed = i;
            }
            for (int i = 0; i < shownRows; i++) {
                int[] rect = {rx + 2, ry, rw - 4, ROW_H};
                rowRects.add(rect);
                boolean over = mouseX >= rect[0] && mouseX < rect[0] + rect[2] && mouseY >= rect[1] && mouseY < rect[1] + rect[3];
                if (over) hover = i;
                affixRow(g, rect[0], rect[1], rect[2], rows.get(i), style, pointed >= 0 ? i == pointed : i == selectedRow);
                ry += ROW_H + ROW_GAP;
            }
            if (rows.size() > shownRows) fitString(g, "+" + (rows.size() - shownRows) + " more", rx + 6, ry + 4, rw - 12, PixelArt.Q_MUTED);
            if (rows.isEmpty()) fitString(g, "No modifiers rolled.", rx + 6, ry + 4, rw - 12, PixelArt.Q_MUTED);
        }
        // The strip: the hovered row, else the clicked one.
        g.fill(rx + 2, stripY, rx + rw - 2, stripY + stripH, PixelArt.Q_HEAD);
        g.fill(rx + 2, stripY, rx + rw - 2, stripY + 1, PixelArt.Q_EDGE);
        if (d != null && !rows.isEmpty() && !rowRects.isEmpty()) {
            var row = rows.get(Math.max(0, Math.min(hover >= 0 ? hover : selectedRow, rowRects.size() - 1)));
            // What it does: the catalog's condition, up to two lines.
            String text = row.condition().isEmpty() ? (row.prefix() ? "An offensive modifier." : "A defensive or recovery modifier.") : row.condition();
            var lines = font.split(Component.literal(text), rw - 12);
            for (int i = 0; i < Math.min(2, lines.size()); i++) g.drawString(font, lines.get(i), rx + 6, stripY + 3 + i * 9, PixelArt.Q_INK, false);
        } else fitString(g, "Hover or click a modifier to see what it does.", rx + 6, stripY + 7, rw - 12, PixelArt.Q_MUTED);

        // ---- bottom bar: provenance, progress, pending upgrades -----------------------------------------------------
        int bx = x + 8, bby = y + ph - 8 - 20;
        if (owned != null && d != null) {
            var e = owned.extra();
            List<String> bits = new ArrayList<>();
            // Most important first; trailing items are dropped rather than clipped mid-word.
            bits.add(owned.pending() + " upgrade" + (owned.pending() == 1 ? "" : "s") + " pending");
            bits.add("attunement " + owned.attunement());
            bits.add(originLabel(e.origin()));
            if (!e.initialRarity().isEmpty() && !e.initialRarity().equals(d.rarityId())) bits.add("promoted from " + capitalize(e.initialRarity()));
            bits.add(e.milestones() + " milestone" + (e.milestones() == 1 ? "" : "s"));
            if (e.highestLevel() > 0) bits.add("best Lv " + e.highestLevel());
            String line = "";
            for (String bit : bits) {
                String next = line.isEmpty() ? bit : line + " · " + bit;
                if (font.width(next) > pw - 16 - 76 - 88) break;
                line = next;
            }
            g.drawString(font, line, bx + 2, bby + 6, PixelArt.Q_INK, false);
        } else if (ownedId == null && !pvp) {
            String s = "Scouters: " + shown.scouters();
            g.drawString(font, s, left + pw - 8 - 68 - 8 - font.width(s), bby + 6, PixelArt.Q_INK, false);
        } else if (pvp) fitString(g, "Base stats only · inspection spends no upgrades", bx + 2, bby + 6, pw - 16 - 68, PixelArt.Q_MUTED);
    }

    /** One modifier on the quartz list: icon square, name, value, rank pips under the name, roll band beside them. */
    private void affixRow(GuiGraphics g, int x, int y, int w, Row row, PixelArt.RarityStyle style, boolean active) {
        g.fill(x, y, x + w, y + ROW_H, row.unique() ? 0xFFE9E1F4 : PixelArt.Q_ROW);
        g.fill(x, y + ROW_H, x + w, y + ROW_H + 1, row.unique() ? PixelArt.ECHO : PixelArt.Q_LINE);
        if (active) PixelArt.ring(g, x, y, w, ROW_H, style.accent(), 1);
        int tint = row.unique() ? PixelArt.ECHO : row.prefix() ? 0xFFC9694F : 0xFF6F9A52;
        g.fill(x + 3, y + 4, x + 14, y + 15, 0xFF1B120D);
        g.fill(x + 4, y + 5, x + 13, y + 14, tint);
        PixelArt.icon(g, row.unique() ? PixelArt.ICON_STAR : row.prefix() ? PixelArt.ICON_SWORD : PixelArt.ICON_SHIELD, x + 5, y + 6, 0xFFFFFFFF);
        int vw = row.value().isEmpty() ? 0 : font.width("+" + row.value()) + 8;
        fitString(g, row.unique() ? "UNIQUE  " + row.name() : row.name(), x + 19, row.unique() ? y + 6 : y + 2, w - 24 - vw, PixelArt.Q_INK);
        if (!row.value().isEmpty()) g.drawString(font, "+" + row.value(), x + w - 5 - font.width("+" + row.value()), y + 2, 0xFF7A2E22, false);
        if (!row.unique()) {
            for (int i = 0; i < 5; i++) {
                int px = x + 19 + i * 7;
                g.fill(px, y + 11, px + 5, y + 16, PixelArt.Q_EDGE);
                g.fill(px + 1, y + 12, px + 4, y + 15, i < row.rank() ? style.panel() : PixelArt.Q_WELL);
                if (i < row.rank()) g.fill(px + 1, y + 12, px + 4, y + 13, style.accent());
            }
            if (row.min() >= 0) g.drawString(font, "rolls " + row.min() + "–" + row.max() + "%", x + 19 + 5 * 7 + 4, y + 10, PixelArt.Q_MUTED, false);
        }
    }

    private static String originLabel(String kind) {
        if (kind == null || kind.isEmpty()) return "Origin unknown";
        return switch (kind) {
            case "wild_capture" -> "Wild capture";
            case "hatch" -> "Hatched";
            case "admin" -> "Operator-registered";
            default -> capitalize(kind.replace('_', ' '));
        };
    }

    private void fitString(GuiGraphics g, String value, int x, int y, int max, int color) {
        g.drawString(font, font.plainSubstrByWidth(value, Math.max(1, max)), x, y, color, false);
    }

    private static String capitalize(String s) { return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1); }

    /** Kept for the few callers that draw the old portrait label; the inspector itself now uses {@link CardArt}. */
    static void portrait(GuiGraphics g, net.minecraft.client.gui.Font font, String sprite, int x, int y, int w, int h) {
        g.drawString(font, "POKÉMON", x + (w - font.width("POKÉMON")) / 2, y + (h - 8) / 2, PixelArt.CREAM, false);
    }
}
