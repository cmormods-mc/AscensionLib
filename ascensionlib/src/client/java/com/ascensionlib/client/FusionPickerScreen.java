package com.ascensionlib.client;

import com.ascensionlib.fusion.FusionPayloads;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The window a fusion chamber opens to choose its Pokemon: Party and PC (first page only) tabs, a search box and a sort. Everything shown
 * is what the server sent; a Pokemon that cannot take the role is greyed with the server's reason on hover. Picking one hands it to the
 * fusion screen and closes; closing costs nothing.
 */
final class FusionPickerScreen extends Screen {
    private enum Sort { RARITY, NAME }

    private static final int W = 352, H = 250, COLS = 6, TILE_W = 52, TILE_H = 46, GAP = 4;
    private static final int INK = 0xFF2B3345, MUTED = 0xFF6A7488, CRIMSON = 0xFF8E1426;

    private final FusionScreen parent;
    private final boolean hostRole;
    private final String hostId, donorId;
    private List<FusionPayloads.Entry> entries;   // null until the server answers
    private final Map<String, CardArt> art = new HashMap<>();
    private boolean pcTab;
    private Sort sort = Sort.RARITY;
    private String search = "";
    private EditBox searchBox;
    private int left, top, pw, ph, scroll, gridX, gridY, gridH;
    private long askedAt;
    private List<FusionPayloads.Entry> shown = List.of();

    FusionPickerScreen(FusionScreen parent, boolean hostRole, String hostId, String donorId) {
        super(Component.literal(hostRole ? "Choose the host" : "Choose the donor"));
        this.parent = parent;
        this.hostRole = hostRole;
        this.hostId = hostId;
        this.donorId = donorId;
    }

    boolean hostRole() { return hostRole; }

    /** Sends the request; the list arrives through {@link #accept}. */
    void ask() {
        askedAt = System.currentTimeMillis();
        if (ClientPlayNetworking.canSend(FusionPayloads.Pick.TYPE)) ClientPlayNetworking.send(new FusionPayloads.Pick(hostRole, hostId, donorId));
    }

    void accept(FusionPayloads.PickList list) {
        if (list.hostRole() != hostRole) return;
        entries = list.entries();
        scroll = 0;
        refilter();
    }

    private void refilter() {
        if (entries == null) { shown = List.of(); return; }
        String needle = search.toLowerCase(Locale.ROOT).trim();
        var out = new ArrayList<FusionPayloads.Entry>();
        for (var e : entries) {
            if (e.inParty() == pcTab) continue;
            if (!needle.isEmpty() && !e.name().toLowerCase(Locale.ROOT).contains(needle) && !e.speciesId().toLowerCase(Locale.ROOT).contains(needle)
                    && !e.uniqueName().toLowerCase(Locale.ROOT).contains(needle)) continue;
            out.add(e);
        }
        // Usable first, then by the chosen order (rarity high to low, or name).
        Comparator<FusionPayloads.Entry> order = sort == Sort.RARITY
                ? Comparator.comparingInt((FusionPayloads.Entry e) -> -PixelArt.rarityIndex(e.rarityId())).thenComparing(FusionPayloads.Entry::name, String.CASE_INSENSITIVE_ORDER)
                : Comparator.comparing(FusionPayloads.Entry::name, String.CASE_INSENSITIVE_ORDER);
        out.sort(Comparator.comparing((FusionPayloads.Entry e) -> !e.block().isEmpty()).thenComparing(order));
        shown = out;
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    private int visibleRows() { return Math.max(1, gridH / (TILE_H + GAP)); }

    private int maxScroll() { return Math.max(0, (shown.size() + COLS - 1) / COLS - visibleRows()); }

    @Override protected void init() {
        pw = Math.min(W, width - 8);
        ph = Math.min(H, height - 8);
        left = (width - pw) / 2;
        top = (height - ph) / 2;
        gridX = left + (pw - (COLS * (TILE_W + GAP) - GAP)) / 2;
        gridY = top + 8 + 14 + 22 + 22 + 4;
        gridH = ph - (gridY - top) - 8 - 22 - 12;
        int x = left + 8, y = top + 8 + 14;
        var party = new PixelButton(x, y, 56, 18, Component.literal("Party"), b -> { pcTab = false; scroll = 0; refilter(); rebuildWidgets(); });
        var pc = new PixelButton(x + 60, y, 56, 18, Component.literal("PC"), b -> { pcTab = true; scroll = 0; refilter(); rebuildWidgets(); });
        (pcTab ? pc : party).primary();
        addRenderableWidget(party);
        addRenderableWidget(pc);
        addRenderableWidget(new PixelButton(left + pw - 8 - 96, y, 96, 18, Component.literal("Sort: " + (sort == Sort.RARITY ? "Rarity" : "Name")), b -> {
            sort = sort == Sort.RARITY ? Sort.NAME : Sort.RARITY;
            refilter();
            rebuildWidgets();
        }));
        searchBox = new EditBox(font, x, y + 22, pw - 16, 16, Component.literal("Search"));
        searchBox.setHint(Component.literal("Search name, species or Unique"));
        searchBox.setMaxLength(40);
        searchBox.setValue(search);
        searchBox.setResponder(text -> { search = text; scroll = 0; refilter(); });
        addRenderableWidget(searchBox);
        addRenderableWidget(new PixelButton(left + pw - 8 - 70, top + ph - 8 - 19, 70, 18, Component.literal("Cancel"), b -> onClose()));
    }

    private FusionPayloads.Entry tileAt(double mouseX, double mouseY) {
        if (mouseX < gridX || mouseY < gridY || mouseY >= gridY + visibleRows() * (TILE_H + GAP)) return null;
        int col = (int) ((mouseX - gridX) / (TILE_W + GAP)), row = (int) ((mouseY - gridY) / (TILE_H + GAP));
        if (col >= COLS || (mouseX - gridX) % (TILE_W + GAP) >= TILE_W || (mouseY - gridY) % (TILE_H + GAP) >= TILE_H) return null;
        int index = (scroll + row) * COLS + col;
        return index >= 0 && index < shown.size() ? shown.get(index) : null;
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        var tile = tileAt(mouseX, mouseY);
        if (tile == null || !tile.block().isEmpty()) return false;
        parent.picked(hostRole, tile);
        onClose();
        return true;
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(vertical)));
        return true;
    }

    @Override public void tick() {
        if (entries == null && System.currentTimeMillis() - askedAt > 8_000L) entries = List.of();
    }

    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderBackground(g, mouseX, mouseY, delta);
        PixelArt.qpanel(g, left, top, pw, ph);
        g.fill(left + 2, top + 2, left + pw - 2, top + 8 + 12, PixelArt.Q_HEAD);
        g.drawString(font, hostRole ? "Choose the host (kept): Mythic with a Unique" : "Choose the donor (consumed)", left + 8, top + 7, PixelArt.Q_INK, false);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        if (entries == null) { g.drawString(font, "Reading your Pokémon…", gridX, gridY + 4, MUTED, false); return; }
        if (shown.isEmpty()) {
            String none = hostRole ? "No Pokémon here can be the host: it must be a Mythic with a Unique and not a Transcendent."
                    : pcTab ? "Nothing in the first page of your PC matches." : "Nothing in your party matches.";
            g.drawWordWrap(font, Component.literal(none), gridX, gridY + 4, pw - 24, MUTED);
        }
        FusionPayloads.Entry hovered = null;
        int rows = visibleRows();
        for (int i = 0; i < rows * COLS; i++) {
            int index = scroll * COLS + i;
            if (index >= shown.size()) break;
            var e = shown.get(index);
            int x = gridX + (i % COLS) * (TILE_W + GAP), y = gridY + (i / COLS) * (TILE_H + GAP);
            boolean usable = e.block().isEmpty();
            boolean over = mouseX >= x && mouseX < x + TILE_W && mouseY >= y && mouseY < y + TILE_H;
            if (over) hovered = e;
            int edge = e.rarityId().isEmpty() ? PixelArt.Q_EDGE : PixelArt.rarity(e.rarityId());
            g.fill(x, y, x + TILE_W, y + TILE_H, usable && over ? CRIMSON : PixelArt.Q_EDGE);
            g.fill(x + 1, y + 1, x + TILE_W - 1, y + TILE_H - 1, usable ? PixelArt.Q_ROW : PixelArt.Q_HEAD);
            g.fill(x + 1, y + 1, x + TILE_W - 1, y + 3, edge);
            art.computeIfAbsent(e.id(), id -> new CardArt(e.speciesId(), List.of())).draw(g, font, x + 2, y + 4, TILE_W - 4, 28, mouseX, mouseY, delta, e.name(), 1);
            String label = font.plainSubstrByWidth(e.name(), TILE_W - 4);
            g.drawString(font, label, x + (TILE_W - font.width(label)) / 2, y + TILE_H - 11, usable ? INK : MUTED, false);
            if (!usable) g.fill(x + 1, y + 1, x + TILE_W - 1, y + TILE_H - 1, 0x88CBD3DF);
        }
        if (maxScroll() > 0) g.drawString(font, "▼ scroll", left + 8, top + ph - 8 - 14, MUTED, false);
        if (hovered != null) {
            var text = new ArrayList<Component>();
            text.add(Component.literal(hovered.name()));
            text.add(Component.literal(hovered.rarityId().isEmpty() ? "No ascension profile" : hovered.rarityId() + (hovered.uniqueName().isEmpty() ? "" : " · " + hovered.uniqueName())));
            if (!hovered.block().isEmpty()) text.add(Component.literal(hovered.block()));
            g.renderComponentTooltip(font, text, mouseX, mouseY);
        }
    }

    @Override public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
