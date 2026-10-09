package com.ascensionlib.client;

import com.ascensionlib.fusion.FusionPayloads;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Fusion: the host (left slot, kept) is the Pokemon this was opened for; the player picks a donor (right slot, consumed) from the list,
 * reads what the pairing would make, and confirms on a second step that says plainly the donor is lost for good. The screen shows only
 * text the server sent (the name, the Uniques it was fused from, the signature, the twist, the rider, the shares after harmony and the
 * price), and a confirmation is a request the server may refuse. Closing costs nothing.
 */
final class FusionScreen extends Screen {
    private enum Phase { BROWSE, REVIEW, WAITING }
    private record Line(FormattedCharSequence text, int color) {}

    private static final int W = 440, H = 244, LIST_W = 150, ROW_H = 22, ROWS = 8;
    private static final int INK = 0xFF2B3345, MUTED = 0xFF6A7488, CRIMSON = 0xFF8E1426, GOLD = 0xFF8A6A12, GOOD = 0xFF2E6B3B;

    private final Screen parent;
    private FusionPayloads.Candidates candidates;
    private FusionPayloads.Detail detail;
    private String selected = "";
    private Phase phase = Phase.BROWSE;
    private String pendingOperation = "";
    private long waitingSince;
    private String banner = "";
    private boolean bannerOk;
    private int left, top, pw, ph, listScroll, textScroll;
    private List<Line> lines = List.of();
    private int textWidth;

    FusionScreen(Screen parent, FusionPayloads.Candidates candidates) {
        super(Component.literal("Fusion"));
        this.parent = parent;
        this.candidates = candidates;
    }

    String hostId() { return candidates.hostId(); }

    void accept(FusionPayloads.Candidates next) {
        candidates = next;
        if (candidates.donors().stream().noneMatch(c -> c.id().equals(selected))) { selected = ""; detail = null; lines = List.of(); }
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, candidates.donors().size() - ROWS)));
        rebuildWidgets();
    }

    void detail(FusionPayloads.Detail next) {
        if (!next.hostId().equals(candidates.hostId()) || !next.donorId().equals(selected)) return;
        detail = next;
        textScroll = 0;
        layoutText();
        rebuildWidgets();
    }

    void result(FusionPayloads.Result result) {
        if (!result.operationId().equals(pendingOperation)) return;
        pendingOperation = "";
        phase = Phase.BROWSE;
        bannerOk = result.ok();
        if (result.ok()) {
            banner = candidates.hostName() + " became " + result.name() + ".";
            // The donor is gone: refresh the list from the server.
            selected = "";
            detail = null;
            lines = List.of();
            if (ClientPlayNetworking.canSend(FusionPayloads.Open.TYPE)) ClientPlayNetworking.send(new FusionPayloads.Open(candidates.hostId()));
        } else {
            banner = result.message();
        }
        rebuildWidgets();
    }

    // ---- layout -------------------------------------------------------------------------------------------------------------

    private void layoutText() {
        var out = new ArrayList<Line>();
        if (detail == null) { lines = out; return; }
        textWidth = pw - 16 - LIST_W - 12 - 8;
        add(out, detail.name(), CRIMSON);
        add(out, "Fused from: " + detail.fromUniques(), INK);
        add(out, capitalize(detail.harmony()) + " harmony: " + detail.benefit() + "% of the benefits, " + detail.drawback() + "% of the drawbacks", MUTED);
        if (!detail.blurb().isEmpty()) add(out, detail.blurb(), MUTED);
        out.add(new Line(FormattedCharSequence.EMPTY, INK));
        add(out, "Core: " + detail.core(), INK);
        add(out, "Clutch: " + detail.clutch(), INK);
        add(out, "Drawback: " + detail.drawbackText(), CRIMSON);
        add(out, "Twist, " + detail.twistName() + ": " + detail.twistText(), INK);
        add(out, "Rider: " + detail.riderText(), INK);
        String types = (detail.hostType().isEmpty() ? "" : "+10% " + capitalize(detail.hostType()) + " moves") +
                (detail.hostType().isEmpty() || detail.donorType().isEmpty() ? "" : "; ") +
                (detail.donorType().isEmpty() ? "" : "-10% damage from " + capitalize(detail.donorType()) + " moves");
        if (!types.isEmpty()) add(out, "Types: " + types, INK);
        out.add(new Line(FormattedCharSequence.EMPTY, INK));
        long[] p = detail.price(), b = detail.balances();
        add(out, "Price: " + p[0] + " Resonance Dust, " + p[1] + " Facets, " + p[2] + " Cores, " + p[3] + " Catalyst", GOLD);
        add(out, "You have: " + b[0] + " / " + b[1] + " / " + b[2] + " / " + b[3], MUTED);
        if (!detail.block().isEmpty()) add(out, detail.block(), CRIMSON);
        lines = out;
    }

    private void add(List<Line> out, String text, int color) {
        for (var piece : font.split(Component.literal(text), Math.max(40, textWidth))) out.add(new Line(piece, color));
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1).toLowerCase(Locale.ROOT);
    }

    private FusionPayloads.Candidate current() {
        for (var c : candidates.donors()) if (c.id().equals(selected)) return c;
        return null;
    }

    // ---- widgets ------------------------------------------------------------------------------------------------------------

    @Override protected void init() {
        pw = Math.min(W, width - 8);
        ph = Math.min(H, height - 8);
        left = (width - pw) / 2;
        top = (height - ph) / 2;
        layoutText();
        int x = left + 8, y = top + 8 + 24;
        if (phase == Phase.BROWSE) {
            var donors = candidates.donors();
            for (int i = 0; i < ROWS && listScroll + i < donors.size(); i++) {
                var donor = donors.get(listScroll + i);
                var row = new PixelButton(x, y + i * ROW_H, LIST_W, ROW_H - 2, Component.literal(donor.name() + " · " + donor.uniqueName()), b -> {
                    selected = donor.id();
                    detail = null;
                    lines = List.of();
                    banner = "";
                    ClientPlayNetworking.send(new FusionPayloads.Preview(candidates.hostId(), donor.id()));
                    rebuildWidgets();
                });
                if (donor.id().equals(selected)) row.primary();
                row.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                        donor.name() + " (" + donor.rarityId() + ", " + donor.uniqueName() + ")" + (donor.block().isEmpty() ? "" : "\n" + donor.block()))));
                addRenderableWidget(row);
            }
        }
        int by = top + ph - 8 - 19;
        addRenderableWidget(new PixelButton(left + pw - 8 - 70, by, 70, 18, Component.literal(phase == Phase.BROWSE ? "Close" : "Cancel"), b -> {
            if (phase == Phase.BROWSE) onClose(); else { phase = Phase.BROWSE; rebuildWidgets(); }
        }));
        if (phase == Phase.BROWSE) {
            var fuse = new PixelButton(left + pw - 8 - 70 - 4 - 90, by, 90, 18, Component.literal("Review fusion"), b -> { phase = Phase.REVIEW; rebuildWidgets(); }).primary();
            fuse.active = detail != null && detail.block().isEmpty();
            addRenderableWidget(fuse);
        } else if (phase == Phase.REVIEW) {
            addRenderableWidget(new PixelButton(left + pw - 8 - 70 - 4 - 110, by, 110, 18, Component.literal("Fuse for good"), b -> confirm()).primary());
        }
    }

    private void confirm() {
        if (phase != Phase.REVIEW || detail == null || !detail.block().isEmpty()) return;
        if (!ClientPlayNetworking.canSend(FusionPayloads.Confirm.TYPE)) { banner = "This server cannot do that."; bannerOk = false; phase = Phase.BROWSE; rebuildWidgets(); return; }
        pendingOperation = UUID.randomUUID().toString();
        phase = Phase.WAITING;
        waitingSince = System.currentTimeMillis();
        ClientPlayNetworking.send(new FusionPayloads.Confirm(pendingOperation, candidates.hostId(), detail.donorId(),
                detail.hostRevision(), detail.donorRevision(), detail.walletRevision()));
        rebuildWidgets();
    }

    @Override public void tick() {
        // An answer that never comes (the server dropped it) must not freeze the screen.
        if (phase == Phase.WAITING && System.currentTimeMillis() - waitingSince > 15_000L) {
            phase = Phase.BROWSE;
            pendingOperation = "";
            banner = "No answer from the server. Nothing was spent unless it shows in your wallet.";
            bannerOk = false;
            rebuildWidgets();
        }
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (phase != Phase.BROWSE) return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
        if (mouseX < left + 8 + LIST_W + 6) {
            int max = Math.max(0, candidates.donors().size() - ROWS);
            int next = Math.max(0, Math.min(max, listScroll - (int) Math.signum(vertical)));
            if (next != listScroll) { listScroll = next; rebuildWidgets(); }
        } else {
            int visible = (ph - 8 - 24 - 8 - 26 - 14) / 10;
            textScroll = Math.max(0, Math.min(Math.max(0, lines.size() - visible), textScroll - (int) Math.signum(vertical)));
        }
        return true;
    }

    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderBackground(g, mouseX, mouseY, delta);
        PixelArt.qpanel(g, left, top, pw, ph);
        g.fill(left + 2, top + 2, left + pw - 2, top + 8 + 18, PixelArt.Q_HEAD);
        g.drawString(font, "Fusion: " + candidates.hostName() + " (kept) absorbs a donor (consumed)", left + 8, top + 8, PixelArt.Q_INK, false);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        int x = left + 8 + LIST_W + 6, y = top + 8 + 24, w = pw - 16 - LIST_W - 6, h = ph - 8 - 24 - 8 - 24;
        PixelArt.qpanel(g, x, y, w, h);
        if (!candidates.hostBlock().isEmpty()) {
            g.drawWordWrap(font, Component.literal(candidates.hostBlock()), x + 6, y + 6, w - 12, CRIMSON);
        } else if (candidates.donors().isEmpty()) {
            g.drawWordWrap(font, Component.literal("No Pokémon you own can be the donor: it must be Epic or better, hold a Unique, and not be a Transcendent."),
                    x + 6, y + 6, w - 12, MUTED);
        } else if (phase == Phase.REVIEW || phase == Phase.WAITING) {
            var donor = current();
            String donorName = donor == null ? "the donor" : donor.name();
            g.drawWordWrap(font, Component.literal("Fuse " + donorName + " into " + candidates.hostName() + "? " + donorName
                    + " is consumed and cannot be recovered. " + candidates.hostName() + " keeps its own modifiers and becomes "
                    + (detail == null ? "a Transcendent" : detail.name()) + ", once, for good."), x + 6, y + 6, w - 12, INK);
            if (phase == Phase.WAITING) g.drawString(font, "Waiting for the server…", x + 6, y + h - 14, MUTED, false);
        } else if (detail == null) {
            g.drawString(font, selected.isEmpty() ? "Pick a donor from the list." : "Reading the pairing…", x + 6, y + 6, MUTED, false);
        } else {
            int visible = (h - 8) / 10;
            for (int i = 0; i < visible && textScroll + i < lines.size(); i++) {
                var line = lines.get(textScroll + i);
                g.drawString(font, line.text(), x + 6, y + 6 + i * 10, line.color(), false);
            }
            if (lines.size() > visible) g.drawString(font, "▼ scroll", x + w - 46, y + h - 12, MUTED, false);
        }
        if (!banner.isEmpty()) g.drawString(font, font.plainSubstrByWidth(banner, pw - 16 - 180), left + 8, top + ph - 8 - 14, bannerOk ? GOOD : CRIMSON, false);
    }

    @Override public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
