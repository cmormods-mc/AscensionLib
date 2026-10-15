package com.ascensionlib.client;

import com.ascensionlib.fusion.FusionPayloads;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Fusion: two chambers, the host (left, kept) and the donor (right, consumed), with the result preview between them. Clicking a chamber's
 * Change opens {@link FusionPickerScreen} for that chamber; the screen opens with the host already in its chamber. Once both are filled it
 * asks the server what the pairing would make, shows only the text the server sent (the name, the Uniques it was fused from, the signature,
 * the twist, the rider, the shares after harmony and the price), and confirms on a second step that says plainly the donor is lost for good.
 * A confirmation is a request the server may refuse. Closing costs nothing.
 */
final class FusionScreen extends Screen {
    private enum Phase { BROWSE, REVIEW, WAITING }
    private record Line(FormattedCharSequence text, int color) {}

    private static final int W = 440, H = 244, CHAMBER_W = 112;
    private static final int INK = 0xFF2B3345, MUTED = 0xFF6A7488, CRIMSON = 0xFF8E1426, GOLD = 0xFF8A6A12, GOOD = 0xFF2E6B3B;

    private final Screen parent;
    private FusionPayloads.Entry host;
    private String hostBlock;
    private FusionPayloads.Entry donor;
    private FusionPayloads.Detail detail;
    private Phase phase = Phase.BROWSE;
    private String pendingOperation = "";
    private long waitingSince;
    private String banner = "";
    private boolean bannerOk;
    private int left, top, pw, ph, textScroll;
    private long previewSentAt;
    private List<Line> lines = List.of();
    private int textWidth;
    private final Map<String, CardArt> art = new HashMap<>();

    FusionScreen(Screen parent, FusionPayloads.Candidates candidates) {
        super(Component.literal("Fusion"));
        this.parent = parent;
        this.host = candidates.host();
        this.hostBlock = candidates.hostBlock();
    }

    String hostId() { return host.id(); }

    /** A fresh answer to Open for this host (after a fusion, to show its new state). */
    void accept(FusionPayloads.Candidates next) {
        host = next.host();
        hostBlock = next.hostBlock();
        rebuildWidgets();
    }

    /** The picker chose a Pokemon for a chamber. */
    void picked(boolean hostRole, FusionPayloads.Entry entry) {
        if (hostRole) { host = entry; hostBlock = ""; } else donor = entry;
        banner = "";
        askPreview();
    }

    /** Asks what the pairing would make, when both chambers are filled and the host can host. */
    private void askPreview() {
        detail = null;
        lines = List.of();
        textScroll = 0;
        phase = Phase.BROWSE;
        if (donor != null && hostBlock.isEmpty() && ClientPlayNetworking.canSend(FusionPayloads.Preview.TYPE)) {
            previewSentAt = System.currentTimeMillis();
            ClientPlayNetworking.send(new FusionPayloads.Preview(host.id(), donor.id()));
        }
        rebuildWidgets();
    }

    void detail(FusionPayloads.Detail next) {
        if (donor == null || !next.hostId().equals(host.id()) || !next.donorId().equals(donor.id())) return;
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
            banner = host.name() + " became " + result.name() + ".";
            // The donor is gone: empty its chamber and refresh the host from the server.
            donor = null;
            detail = null;
            lines = List.of();
            if (ClientPlayNetworking.canSend(FusionPayloads.Open.TYPE)) ClientPlayNetworking.send(new FusionPayloads.Open(host.id()));
        } else {
            banner = result.message();
        }
        rebuildWidgets();
    }

    // ---- layout -------------------------------------------------------------------------------------------------------------

    private int middleWidth() { return pw - 16 - 2 * CHAMBER_W - 12; }

    private void layoutText() {
        var out = new ArrayList<Line>();
        if (detail == null) { lines = out; return; }
        textWidth = middleWidth() - 12;
        if (detail.name().isEmpty()) {   // a refusal with only a reason
            add(out, detail.block(), CRIMSON);
            lines = out;
            return;
        }
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

    private int panelTop() { return top + 8 + 24; }

    private int panelHeight() { return ph - 8 - 24 - 8 - 24; }

    /** How many text lines fit in the middle panel; one answer for drawing and for scrolling. */
    private int visibleLines() { return Math.max(1, (panelHeight() - 12 - 24) / 10); }

    /** Whether the two Pokemon can trade chambers: the donor would be a valid host (the host is one already, or it could not be here). */
    private boolean canSwap() {
        return phase == Phase.BROWSE && donor != null && donor.canHost() && hostBlock.isEmpty();
    }

    // ---- widgets ------------------------------------------------------------------------------------------------------------

    @Override protected void init() {
        pw = Math.min(W, width - 8);
        ph = Math.min(H, height - 8);
        left = (width - pw) / 2;
        top = (height - ph) / 2;
        layoutText();
        int y = panelTop(), h = panelHeight();
        boolean idle = phase == Phase.BROWSE;
        var changeHost = new PixelButton(left + 8 + 6, y + h - 24, CHAMBER_W - 12, 18, Component.literal("Change host"), b -> openPicker(true));
        changeHost.active = idle;
        addRenderableWidget(changeHost);
        var changeDonor = new PixelButton(left + pw - 8 - CHAMBER_W + 6, y + h - 24, CHAMBER_W - 12, 18, Component.literal(donor == null ? "Choose donor" : "Change donor"),
                b -> openPicker(false));
        changeDonor.active = idle;
        if (donor == null && idle) changeDonor.primary();
        addRenderableWidget(changeDonor);
        var swap = new PixelButton(left + 8 + CHAMBER_W + 6 + (middleWidth() - 80) / 2, y + h - 24, 80, 18, Component.literal("<-> Swap"), b -> swap());
        swap.active = canSwap();
        if (!swap.active && phase == Phase.BROWSE && donor != null)
            swap.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Only a Mythic with a Unique and no fusion can trade places with the host.")));
        addRenderableWidget(swap);
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

    private void openPicker(boolean hostRole) {
        if (phase != Phase.BROWSE || minecraft == null) return;
        var picker = new FusionPickerScreen(this, hostRole, host.id(), donor == null ? "" : donor.id());
        minecraft.setScreen(picker);
        picker.ask();
    }

    private void swap() {
        if (!canSwap()) return;
        var oldHost = host;
        host = donor;
        hostBlock = "";
        donor = oldHost;
        banner = "";
        askPreview();
    }

    private void confirm() {
        if (phase != Phase.REVIEW || detail == null || !detail.block().isEmpty() || donor == null) return;
        if (!ClientPlayNetworking.canSend(FusionPayloads.Confirm.TYPE)) { banner = "This server cannot do that."; bannerOk = false; phase = Phase.BROWSE; rebuildWidgets(); return; }
        pendingOperation = UUID.randomUUID().toString();
        phase = Phase.WAITING;
        waitingSince = System.currentTimeMillis();
        ClientPlayNetworking.send(new FusionPayloads.Confirm(pendingOperation, host.id(), donor.id(),
                detail.hostRevision(), detail.donorRevision(), detail.walletRevision()));
        rebuildWidgets();
    }

    @Override public void tick() {
        // A preview the server never answered (a dropped packet) must not leave the panel on "Reading the pairing" for good.
        if (phase == Phase.BROWSE && donor != null && hostBlock.isEmpty() && detail == null && previewSentAt != 0 && System.currentTimeMillis() - previewSentAt > 8_000L) {
            banner = "No answer from the server. Choose the donor again.";
            bannerOk = false;
            donor = null;
            previewSentAt = 0;
            rebuildWidgets();
        }
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
        int visible = visibleLines();
        textScroll = Math.max(0, Math.min(Math.max(0, lines.size() - visible), textScroll - (int) Math.signum(vertical)));
        return true;
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------------

    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderBackground(g, mouseX, mouseY, delta);
        PixelArt.qpanel(g, left, top, pw, ph);
        g.fill(left + 2, top + 2, left + pw - 2, top + 8 + 18, PixelArt.Q_HEAD);
        g.drawString(font, "Fusion: the host is kept, the donor is consumed", left + 8, top + 8, PixelArt.Q_INK, false);
    }

    private void chamber(GuiGraphics g, int x, int y, int h, String title, FusionPayloads.Entry entry, int mouseX, int mouseY, float delta) {
        PixelArt.qpanel(g, x, y, CHAMBER_W, h);
        g.drawString(font, title, x + 6, y + 6, MUTED, false);
        int wx = x + 6, wy = y + 18, ww = CHAMBER_W - 12, wh = 66;
        g.fill(wx, wy, wx + ww, wy + wh, PixelArt.Q_WELL);
        if (entry == null) {
            g.drawCenteredString(font, "empty", wx + ww / 2, wy + wh / 2 - 4, MUTED);
            return;
        }
        int accent = entry.rarityId().isEmpty() ? PixelArt.Q_EDGE : PixelArt.rarity(entry.rarityId());
        PixelArt.ring(g, wx, wy, ww, wh, accent, 1);
        art.computeIfAbsent(entry.id(), id -> new CardArt(entry.speciesId(), List.of())).draw(g, font, wx + 1, wy + 1, ww - 2, wh - 2, mouseX, mouseY, delta, entry.name(), 2);
        g.drawString(font, font.plainSubstrByWidth(entry.name(), CHAMBER_W - 12), x + 6, y + 18 + wh + 4, INK, false);
        g.drawString(font, font.plainSubstrByWidth(capitalize(entry.rarityId()), CHAMBER_W - 12), x + 6, y + 18 + wh + 14,
                entry.rarityId().isEmpty() ? MUTED : PixelArt.rarityOnParchment(entry.rarityId()), false);
        g.drawString(font, font.plainSubstrByWidth(entry.uniqueName(), CHAMBER_W - 12), x + 6, y + 18 + wh + 24, MUTED, false);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        int y = panelTop(), h = panelHeight();
        chamber(g, left + 8, y, h, "HOST (kept)", host, mouseX, mouseY, delta);
        chamber(g, left + pw - 8 - CHAMBER_W, y, h, "DONOR (consumed)", donor, mouseX, mouseY, delta);
        int x = left + 8 + CHAMBER_W + 6, w = middleWidth();
        PixelArt.qpanel(g, x, y, w, h);
        if (!hostBlock.isEmpty()) {
            g.drawWordWrap(font, Component.literal(hostBlock + " Choose a different host."), x + 6, y + 6, w - 12, CRIMSON);
        } else if (donor == null) {
            g.drawWordWrap(font, Component.literal("Choose a donor: Epic or better, with a Unique, and not a Transcendent."), x + 6, y + 6, w - 12, MUTED);
        } else if (phase == Phase.REVIEW || phase == Phase.WAITING) {
            g.drawWordWrap(font, Component.literal("Fuse " + donor.name() + " into " + host.name() + "? " + donor.name()
                    + " is consumed and cannot be recovered. " + host.name() + " keeps its own modifiers and becomes "
                    + (detail == null ? "a Transcendent" : detail.name()) + ", once, for good."), x + 6, y + 6, w - 12, INK);
            if (phase == Phase.WAITING) g.drawString(font, "Waiting for the server…", x + 6, y + h - 40, MUTED, false);
        } else if (detail == null) {
            g.drawString(font, "Reading the pairing…", x + 6, y + 6, MUTED, false);
        } else {
            int visible = visibleLines();
            for (int i = 0; i < visible && textScroll + i < lines.size(); i++) {
                var line = lines.get(textScroll + i);
                g.drawString(font, line.text(), x + 6, y + 6 + i * 10, line.color(), false);
            }
            if (lines.size() > visible) g.drawString(font, "▼ scroll", x + w - 46, y + h - 36, MUTED, false);
        }
        if (!banner.isEmpty()) g.drawString(font, font.plainSubstrByWidth(banner, pw - 16 - 180), left + 8, top + ph - 8 - 14, bannerOk ? GOOD : CRIMSON, false);
        else if (donor != null) g.drawString(font, "The donor is lost for good.", left + 8, top + ph - 8 - 14, MUTED, false);
    }

    @Override public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
