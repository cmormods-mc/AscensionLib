package com.ascensionlib.client;

import com.ascensionlib.craft.CraftPayloads;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Upgrade, refine and reforge: one Pokemon's modifiers in the same white quartz style as the inspector, laid out for choosing.
 * Left, the Pokemon's affixes to pick from; right, what the chosen action does to the chosen affix (the current roll and the next
 * range, a refine's band, or a reforge's pool), its cost, and a Review button that opens a plain confirmation. The screen only previews:
 * the server computes every range and cost, and a confirmation is a request the server may refuse (stale, unaffordable, not yours).
 * Closing, cancelling or "Decide later" costs nothing. Buttons lock while a request is in flight, and a duplicate confirmation cannot
 * double-charge because each carries one operation id the server commits once.
 */
final class CraftScreen extends Screen {
    private enum Mode { UPGRADE, REFINE, REFORGE, PROMOTE, UNIQUE }
    private enum Phase { BROWSE, REVIEW, WAITING }
    private static final int W = 496, H = 252, ROW_H = 20, UNIQUE_ROW_H = 18, LEFT_W = 176;
    private static final int SIDE_W = 96;   // the wallet column on the right, gap included
    private static final int ASSEMBLE_W = 104, ASSEMBLE_Y = 114;   // the Assemble button's width and its offset below the right panel's top
    private static final int REVIEW_W = 58;
    private static final int TABS = 5, TAB_W = 56;
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V"};

    private final Screen parent;
    private CraftPayloads.View view;
    private Mode mode = Mode.UPGRADE;
    private Phase phase = Phase.BROWSE;
    private String selectedSlot = "";
    private String selectedUnique = "";
    private String pendingOperation = "";
    private long waitingSince;
    private String bannerText = "";
    private boolean bannerOk;
    private CardArt art;
    private String artKey = "";
    private int left, top, pw, ph, mainW;

    CraftScreen(Screen parent, CraftPayloads.View view) {
        super(Component.translatable("screen.ascensionlib.craft"));
        this.parent = parent;
        this.view = view;
        if (!view.message().isEmpty()) { bannerText = view.message(); bannerOk = false; }
        this.mode = view.pending() > 0 ? Mode.UPGRADE : Mode.REFINE;
        if (!view.slots().isEmpty()) selectedSlot = view.slots().get(0).slotId();
        selectedUnique = validUnique("");
    }

    String pokemonId() { return view.pokemonId(); }

    void accept(CraftPayloads.View next) {
        view = next;
        selectedUnique = validUnique(selectedUnique);
        if (view.slots().stream().noneMatch(s -> s.slotId().equals(selectedSlot))) selectedSlot = view.slots().isEmpty() ? "" : view.slots().get(0).slotId();
        if (!next.message().isEmpty() && bannerText.isEmpty()) { bannerText = next.message(); bannerOk = false; }
        rebuildWidgets();
    }

    void done(CraftPayloads.Done result) {
        if (!result.operationId().equals(pendingOperation)) return;
        pendingOperation = "";
        phase = Phase.BROWSE;
        bannerOk = result.ok();
        if (result.ok()) {
            String was = ROMAN[Math.max(1, Math.min(5, result.oldRank()))] + " +" + result.oldValue() + "%";
            String now = ROMAN[Math.max(1, Math.min(5, result.newRank()))] + " +" + result.newValue() + "%";
            bannerText = switch (mode) {
                case UPGRADE -> result.newName() + " → " + now;
                case REFINE -> "Refined: +" + result.oldValue() + "% → +" + result.newValue() + "%";
                case REFORGE -> "Now " + result.newName() + " +" + result.newValue() + "%";
                case PROMOTE -> "Promoted to " + rarityName(result.newName()) + ": a new modifier slot opened";
                case UNIQUE -> result.newName().equals("Unique Catalyst") ? "Assembled a Unique Catalyst" : "Unique set: " + result.newName();
            };
            if (result.replayed()) bannerText += " (already done)";
            if (AscensionClientSettings.sounds && minecraft != null)
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_LOOM_TAKE_RESULT, 1.0f, 0.6f));
        } else bannerText = result.message();
        rebuildWidgets();
    }

    private CraftPayloads.SlotView slot() {
        for (var s : view.slots()) if (s.slotId().equals(selectedSlot)) return s;
        return null;
    }

    private String blockFor(CraftPayloads.SlotView s) {
        return switch (mode) {
            case UPGRADE -> s.upgradeBlock();
            case REFINE -> s.refineBlock();
            case REFORGE -> s.reforgeBlock();
            case PROMOTE -> view.promotion().block();
            case UNIQUE -> view.unique().block();
        };
    }

    /** The selected Unique if the server still offers it, else the one held, else the first offered. */
    private String validUnique(String wanted) {
        var options = view.unique().options();
        if (options.stream().anyMatch(o -> o.id().equals(wanted))) return wanted;
        for (var o : options) if (o.current()) return o.id();
        return options.isEmpty() ? "" : options.get(0).id();
    }

    private CraftPayloads.UniqueOption uniqueOption() {
        for (var o : view.unique().options()) if (o.id().equals(selectedUnique)) return o;
        return null;
    }

    private boolean uniqueReady() {
        return !selectedUnique.isEmpty() && view.unique().block().isEmpty() && !selectedUnique.equals(view.unique().currentId());
    }

    private static String rarityName(String id) {
        return id.isEmpty() ? "" : id.equals("mythical") ? "Mythic" : Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }

    private CardArt art() {
        String key = view.speciesId() + "|" + String.join(",", view.aspects());
        if (art == null || !key.equals(artKey)) { art = new CardArt(view.speciesId(), view.aspects()); artKey = key; }
        return art;
    }

    // ---- widgets ------------------------------------------------------------------------------------------------------------

    @Override protected void init() {
        long profiled = com.ascensionlib.Profiler.start();
        buildWidgets();
        com.ascensionlib.Profiler.stop("craft.init", profiled);
    }

    private void buildWidgets() {
        pw = Math.min(W, width - 8);
        ph = Math.min(H, height - 8);
        left = (width - pw) / 2;
        top = (height - ph) / 2;
        mainW = pw - 16 - SIDE_W;
        int x = left + 8;
        if (phase == Phase.BROWSE) {
            String[] names = {"Upgrade", "Refine", "Reforge", "Promote", "Unique"};
            for (int i = 0; i < TABS; i++) {
                Mode m = Mode.values()[i];
                addRenderableWidget(new TabButton(x + i * TAB_W, top + 8 + 36 + 3, TAB_W - 2, 16, names[i], m == mode, () -> { mode = m; bannerText = ""; rebuildWidgets(); }));
            }
            int rowY = top + 8 + 36 + 3 + 16 + 17 + 2;
            if (mode == Mode.UNIQUE) {
                for (var option : view.unique().options()) {
                    addRenderableWidget(new UniqueRow(x + 2, rowY, LEFT_W - 4, UNIQUE_ROW_H - 1, option, option.id().equals(selectedUnique),
                            () -> { selectedUnique = option.id(); rebuildWidgets(); }));
                    rowY += UNIQUE_ROW_H;
                }
            }
            for (var s : mode == Mode.UNIQUE ? java.util.List.<CraftPayloads.SlotView>of() : view.slots()) {
                boolean selected = s.slotId().equals(selectedSlot);
                addRenderableWidget(new RowButton(x + 2, rowY, LEFT_W - 4, ROW_H - 1, s, selected, mode == Mode.PROMOTE ? "" : blockFor(s), () -> { selectedSlot = s.slotId(); rebuildWidgets(); }));
                rowY += ROW_H;
            }
            int fy = top + ph - 8 - 19;
            addRenderableWidget(new PixelButton(left + pw - 8 - 86, fy, 86, 18, Component.literal("Decide later"), b -> onClose()));
            // Only a Mythic Pokemon can be a fusion host; the server explains anything else that is missing.
            if (view.rarityId().equals("mythical"))
                addRenderableWidget(new PixelButton(left + pw - 8 - 86 - 4 - 56, fy, 56, 18, Component.literal("Fuse"), b -> FusionClient.open(this, view.pokemonId())));
            var current = slot();
            if (current != null || mode == Mode.PROMOTE || mode == Mode.UNIQUE) {
                int rx = x + LEFT_W + 6, rw = x + mainW - rx, panelBottom = top + ph - 8 - 24;
                var review = new PixelButton(rx + rw - 6 - REVIEW_W, panelBottom - 2 - 17, REVIEW_W, 16,
                        Component.literal("Review"), b -> { phase = Phase.REVIEW; rebuildWidgets(); }).primary();
                review.active = mode == Mode.PROMOTE ? view.promotion().block().isEmpty() && !view.promotion().toRarity().isEmpty()
                        : mode == Mode.UNIQUE ? uniqueReady() : blockFor(current).isEmpty();
                addRenderableWidget(review);
                if (mode == Mode.UNIQUE) {
                    var assemble = new PixelButton(rx + rw - 6 - ASSEMBLE_W, top + 8 + 36 + 3 + 17 + ASSEMBLE_Y, ASSEMBLE_W, 14,
                            Component.literal("Assemble " + Math.min(view.unique().fragments(), view.unique().fragmentsNeeded()) + "/" + view.unique().fragmentsNeeded()), b -> assemble());
                    assemble.active = view.unique().fragments() >= view.unique().fragmentsNeeded();
                    addRenderableWidget(assemble);
                }
            }
        } else if (phase == Phase.REVIEW) {
            int mx = left + pw / 2, my = top + ph / 2;
            addRenderableWidget(new PixelButton(mx - 118, my + 28, 112, 18, Component.literal("Confirm " + mode.name().toLowerCase(Locale.ROOT)), b -> confirm()).primary());
            addRenderableWidget(new PixelButton(mx + 6, my + 28, 112, 18, Component.literal("Cancel"), b -> { phase = Phase.BROWSE; rebuildWidgets(); }));
        }
    }

    private void confirm() {
        var current = slot();
        boolean promoting = mode == Mode.PROMOTE, uniqueMode = mode == Mode.UNIQUE;
        if (phase != Phase.REVIEW) return;
        if (promoting ? !view.promotion().block().isEmpty() || view.promotion().toRarity().isEmpty()
                : uniqueMode ? !uniqueReady() : current == null || !blockFor(current).isEmpty()) return;
        if (!ClientPlayNetworking.canSend(CraftPayloads.Confirm.TYPE)) { bannerText = "This server cannot do that."; bannerOk = false; phase = Phase.BROWSE; rebuildWidgets(); return; }
        pendingOperation = UUID.randomUUID().toString();
        phase = Phase.WAITING;
        waitingSince = System.currentTimeMillis();
        ClientPlayNetworking.send(new CraftPayloads.Confirm(pendingOperation, view.pokemonId(), mode.name().toLowerCase(Locale.ROOT), promoting ? "" : uniqueMode ? selectedUnique : current.slotId(),
                view.profileRevision(), view.walletRevision()));
        rebuildWidgets();
    }

    /** Turns Unique Fragments into a Catalyst: one click, no review (it only converts materials). The server decides. */
    private void assemble() {
        if (phase != Phase.BROWSE || mode != Mode.UNIQUE || view.unique().fragments() < view.unique().fragmentsNeeded()) return;
        if (!ClientPlayNetworking.canSend(CraftPayloads.Confirm.TYPE)) { bannerText = "This server cannot do that."; bannerOk = false; rebuildWidgets(); return; }
        pendingOperation = UUID.randomUUID().toString();
        phase = Phase.WAITING;
        waitingSince = System.currentTimeMillis();
        ClientPlayNetworking.send(new CraftPayloads.Confirm(pendingOperation, view.pokemonId(), "assemble", "", view.profileRevision(), view.walletRevision()));
        rebuildWidgets();
    }

    @Override public void tick() {
        if (phase == Phase.WAITING && System.currentTimeMillis() - waitingSince > 8000) {
            phase = Phase.BROWSE;
            pendingOperation = "";
            bannerText = "No answer from the server. Check your materials and try again.";
            bannerOk = false;
            rebuildWidgets();
        }
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && phase == Phase.REVIEW) { phase = Phase.BROWSE; rebuildWidgets(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override public void onClose() {
        if (phase == Phase.WAITING) return;
        com.ascensionlib.Profiler.flush();       // the answer is on its way; do not abandon a request in flight
        minecraft.setScreen(parent);
        if (parent instanceof AscendInspectScreen inspect) inspect.refreshOwned();
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------------

    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        long profiled = com.ascensionlib.Profiler.start();
        drawBackground(g, mouseX, mouseY);
        com.ascensionlib.Profiler.stop("craft.background", profiled);
    }

    private void drawBackground(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(0, 0, width, height, 0xB0140D09);
        var style = PixelArt.style(view.rarityId());
        PixelArt.qpanel(g, left, top, pw, ph);
        PixelArt.ring(g, left + 4, top + 4, pw - 8, ph - 8, style.accent(), 2);
        PixelArt.ring(g, left + 6, top + 6, pw - 12, ph - 12, 0xFF1B120D, 1);
        mainW = pw - 16 - SIDE_W;
        int x = left + 8, y = top + 8, innerW = pw - 16;

        // ---- header: the Pokemon, its rarity, how many upgrades wait --------------------------------------------------------
        PixelArt.qpanel(g, x, y, innerW, 36);
        g.fill(x + 4, y + 3, x + 4 + 60, y + 3 + 30, PixelArt.Q_EDGE);
        g.fill(x + 5, y + 4, x + 3 + 60, y + 2 + 30, PixelArt.Q_ROW);
        art().draw(g, font, x + 5, y + 4, 58, 28, mouseX, mouseY, 0f, "?", 1);
        g.drawString(font, font.plainSubstrByWidth(view.name(), innerW - 160), x + 72, y + 8, PixelArt.Q_INK, false);
        g.drawString(font, "Lv " + view.level(), x + 72, y + 21, PixelArt.Q_MUTED, false);
        String grade = (view.rarityId().equals("mythical") ? "MYTHIC" : view.rarityId().toUpperCase(Locale.ROOT)) + (view.uniqueName().isEmpty() ? "" : " · UNIQUE");
        int gx = x + 72 + font.width("Lv " + view.level()) + 8, gw = font.width(grade) + 8;
        g.fill(gx, y + 19, gx + gw, y + 31, 0xFF1B120D);
        g.fill(gx + 1, y + 20, gx + gw - 1, y + 30, style.panel());
        g.drawString(font, grade, gx + 4, y + 21, style.accent(), false);
        String pending = String.valueOf(view.pending());
        g.pose().pushPose();
        g.pose().translate(x + innerW - 10 - font.width(pending) * 2, y + 6, 0);
        g.pose().scale(2f, 2f, 1f);
        g.drawString(font, pending, 0, 0, view.pending() > 0 ? PixelArt.Q_INK : PixelArt.Q_MUTED, false);
        g.pose().popPose();
        String upgrades = view.pending() == 1 ? "upgrade available" : "upgrades available";
        g.drawString(font, upgrades, x + innerW - 10 - font.width(upgrades), y + 24, PixelArt.Q_MUTED, false);

        // ---- tab row: line and the result banner -----------------------------------------------------------------------------
        int tabY = y + 36 + 3;
        g.fill(x, tabY + 16, x + mainW, tabY + 17, PixelArt.Q_EDGE);
        if (!bannerText.isEmpty()) {
            int bx = x + TABS * TAB_W + 4, bw = mainW - TABS * TAB_W - 4;
            g.fill(bx, tabY + 1, bx + bw, tabY + 15, bannerOk ? 0xFFDDEBD0 : 0xFFF2D8D2);
            g.fill(bx, tabY + 1, bx + 2, tabY + 15, bannerOk ? 0xFF6F9A52 : 0xFFB5482E);
            g.drawString(font, font.plainSubstrByWidth(bannerText, bw - 10), bx + 6, tabY + 4, PixelArt.Q_INK, false);
        }

        // ---- left: the affixes -----------------------------------------------------------------------------------------------
        int panelTop = tabY + 17, panelBottom = top + ph - 8 - 24;
        PixelArt.qpanel(g, x, panelTop, LEFT_W, panelBottom - panelTop);
        g.fill(x + 2, panelTop + 2, x + LEFT_W - 2, panelTop + 16, PixelArt.Q_HEAD);
        boolean uniqueTab = mode == Mode.UNIQUE;
        g.drawString(font, uniqueTab ? "Unique powers" : "Your affixes", x + 6, panelTop + 5, PixelArt.Q_INK, false);
        String rightHead = uniqueTab ? "Held" : "Rank / roll";
        g.drawString(font, rightHead, x + LEFT_W - 6 - font.width(rightHead), panelTop + 5, PixelArt.Q_MUTED, false);
        if (!uniqueTab && view.slots().isEmpty()) g.drawString(font, "No affixes to change.", x + 8, panelTop + 24, PixelArt.Q_MUTED, false);

        // ---- right: the chosen action on the chosen affix -----------------------------------------------------------------------
        int rx = x + LEFT_W + 6, rw = x + mainW - rx, t = panelTop;
        PixelArt.qpanel(g, rx, t, rw, panelBottom - t);
        var s = slot();
        if (mode == Mode.PROMOTE) {
            drawPromote(g, rx, t, rw, panelBottom);
        } else if (mode == Mode.UNIQUE) {
            drawUnique(g, rx, t, rw, panelBottom);
        } else if (s == null) {
            g.drawString(font, "Choose an affix on the left.", rx + 8, t + 10, PixelArt.Q_MUTED, false);
        } else {
            boolean prefix = s.category().equals("prefix");
            String kind = mode.name() + " · " + (prefix ? "OFFENSE" : "DEFENSE");
            g.drawString(font, kind, rx + 8, t + 5, PixelArt.Q_MUTED, false);
            g.drawString(font, font.plainSubstrByWidth(s.name(), rw - 16), rx + 8, t + 16, PixelArt.Q_INK, false);
            String effect = s.condition().isEmpty() ? (prefix ? "Boosts your damage." : "Protects or heals you.") : s.condition();
            var lines = font.split(Component.literal(effect), rw - 16);
            for (int i = 0; i < Math.min(3, lines.size()); i++) g.drawString(font, lines.get(i), rx + 8, t + 27 + i * 9, PixelArt.Q_INK, false);
            int boxY = t + 58, boxW = (rw - 12 - 18) / 2, boxH = 34;
            String curLabel = "Current · " + ROMAN[Math.max(1, Math.min(5, s.rank()))];
            String curBig = "+" + s.value() + "%";
            String curSmall = "Range " + s.bandMin() + "–" + s.bandMax() + "%";
            String nextLabel, nextBig, nextSmall;
            switch (mode) {
                case UPGRADE -> {
                    boolean top = s.nextMin() < 0;
                    nextLabel = top ? "Top rank" : "Next · " + ROMAN[s.rank() + 1];
                    nextBig = top ? "—" : s.nextMin() + "–" + s.nextMax() + "%";
                    nextSmall = top ? "Cannot rise further" : "New roll range";
                }
                case REFINE -> {
                    nextLabel = "Reroll";
                    nextBig = s.bandMin() + "–" + s.bandMax() + "%";
                    nextSmall = "Same rank, new value";
                }
                case PROMOTE, UNIQUE -> throw new IllegalStateException("This tab has its own panel");
                default -> {
                    curBig = font.plainSubstrByWidth(s.name(), boxW - 8);
                    curSmall = "+" + s.value() + "% · rank " + ROMAN[Math.max(1, Math.min(5, s.rank()))];
                    nextLabel = "New affix";
                    nextBig = "?";
                    nextSmall = s.reforgePool() + " eligible";
                }
            }
            valueBox(g, rx + 6, boxY, boxW, boxH, curLabel, curBig, curSmall, false, mode == Mode.REFORGE);
            g.drawString(font, "→", rx + 6 + boxW + 5, boxY + 15, PixelArt.Q_MUTED, false);
            valueBox(g, rx + 6 + boxW + 18, boxY, boxW, boxH, nextLabel, nextBig, nextSmall, true, false);
            // Rank bar: filled to the current rank, the next rank lighter.
            var style2 = PixelArt.style(view.rarityId());
            int barY = boxY + boxH + 5, segW = (rw - 12 - 4 * 3) / 5;
            for (int i = 0; i < 5; i++) {
                int sx = rx + 6 + i * (segW + 3);
                g.fill(sx, barY, sx + segW, barY + 5, PixelArt.Q_EDGE);
                boolean filled = i < s.rank();
                boolean next = mode == Mode.UPGRADE && i == s.rank() && s.nextMin() >= 0;
                g.fill(sx + 1, barY + 1, sx + segW - 1, barY + 4, filled ? style2.panel() : next ? 0xFFA9B7D4 : PixelArt.Q_WELL);
                if (filled) g.fill(sx + 1, barY + 1, sx + segW - 1, barY + 2, style2.accent());
            }
            String what = switch (mode) {
                case UPGRADE -> "Raises this affix one rank and rerolls its value. Never fails.";
                case REFINE -> "Rerolls the value inside this rank's range. It can go lower.";
                default -> "Swaps this affix for a different one. Rank stays, value rerolls.";
            };
            var explain = font.split(Component.literal(what), rw - 16);
            for (int i = 0; i < Math.min(2, explain.size()); i++) g.drawString(font, explain.get(i), rx + 8, barY + 10 + i * 9, PixelArt.Q_MUTED, false);
            // Bottom row: the cost on the left, the Review button (a widget) on the right; the reason replaces the cost when it cannot be done.
            int costY = panelBottom - 2 - 19;
            g.fill(rx + 6, costY - 3, rx + rw - 6, costY - 2, PixelArt.Q_LINE);
            String block = blockFor(s);
            int costW = rw - 12 - REVIEW_W - 8;
            g.drawString(font, "Cost", rx + 8, costY + 1, PixelArt.Q_MUTED, false);
            if (block.isEmpty()) g.drawString(font, font.plainSubstrByWidth(shortCost(), costW), rx + 8, costY + 10, PixelArt.Q_INK, false);
            else g.drawString(font, font.plainSubstrByWidth(block, costW), rx + 8, costY + 10, 0xFF8A2E22, false);
        }

        // ---- footer: progress and wallet ---------------------------------------------------------------------------------------
        int fy = top + ph - 8 - 20;
        String next = view.nextMilestone() > 0 ? "Next upgrade at Lv " + view.nextMilestone() : "All level milestones reached";
        g.drawString(font, font.plainSubstrByWidth(next, innerW - 86 - 12), x + 2, fy + 6, PixelArt.Q_INK, false);

        drawWallet(g, x + mainW + 6, tabY, SIDE_W - 6, panelBottom - tabY, mouseX, mouseY);

        if (phase != Phase.BROWSE) review(g);
    }

    private record Coin(String name, String[] sprite, int color, long amount) {}

    private List<Coin> coins() {
        var u = view.unique();
        return List.of(
                new Coin("Resonance Dust", ICON_DUST, 0xFFD9A441, view.dust()),
                new Coin("Facets", ICON_FACET, 0xFF4F9AD9, view.facets()),
                new Coin("Ascension Cores", ICON_CORE, 0xFFC9694F, view.cores()),
                new Coin("Scouters", ICON_EYE, 0xFF6F9A52, view.scouters()),
                new Coin("Unique Fragments", ICON_SHARD, 0xFF8C63C7, u.fragments()),
                new Coin("Unique Catalysts", ICON_FLASK, 0xFFB04FA0, u.catalysts()));
    }

    private static final int COIN_ROW_H = 18;
    private static final String[] ICON_DUST = {"...#...", "..#+#..", ".#+*+#.", "#+***+#", ".#+*+#.", "..#+#..", "...#..."};
    private static final String[] ICON_FACET = {"..###..", ".#+++#.", "#+*++*#", "#+++++#", ".#+++#.", "..#+#..", "...#..."};
    private static final String[] ICON_CORE = {"..###..", ".#+++#.", "#+***+#", "#+***+#", "#+***+#", ".#+++#.", "..###.."};
    private static final String[] ICON_EYE = {".......", "..###..", ".#+++#.", "#+*#*+#", ".#+++#.", "..###..", "......."};
    private static final String[] ICON_SHARD = {"...#...", "..##...", "..#+#..", ".#++#..", "..#+#..", "..##...", "...#..."};
    private static final String[] ICON_FLASK = {"..###..", "...#...", "..#+#..", ".#+++#.", "#+***+#", "#+++++#", ".#####."};

    /** Whole numbers with thousands separators; millions shorten so the column never overflows. */
    private static String amount(long n) {
        if (n >= 10_000_000L) return String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0);
        return String.format(Locale.ROOT, "%,d", n);
    }

    /** The wallet column: one row per material, its sprite and the amount held. The name appears on hover (see {@link #render}). */
    private void drawWallet(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY) {
        PixelArt.qpanel(g, x, y, w, h);
        g.fill(x + 2, y + 2, x + w - 2, y + 16, PixelArt.Q_HEAD);
        g.drawString(font, "Wallet", x + 6, y + 5, PixelArt.Q_INK, false);
        int ry = y + 18;
        for (var coin : coins()) {
            boolean hover = phase == Phase.BROWSE && mouseX >= x + 2 && mouseX < x + w - 2 && mouseY >= ry && mouseY < ry + COIN_ROW_H - 1;
            g.fill(x + 2, ry, x + w - 2, ry + COIN_ROW_H - 1, hover ? 0xFFFFFFFF : PixelArt.Q_PANEL);
            g.fill(x + 2, ry + COIN_ROW_H - 1, x + w - 2, ry + COIN_ROW_H, PixelArt.Q_LINE);
            g.fill(x + 5, ry + 3, x + 16, ry + 14, 0xFF1B120D);
            g.fill(x + 6, ry + 4, x + 15, ry + 13, coin.color());
            PixelArt.icon(g, coin.sprite(), x + 7, ry + 5, 0xFFFFFFFF, 0xFF1B120D);
            String text = amount(coin.amount());
            g.drawString(font, font.plainSubstrByWidth(text, w - 24 - 6), x + 21, ry + 5, coin.amount() > 0 ? PixelArt.Q_INK : PixelArt.Q_MUTED, false);
            ry += COIN_ROW_H;
        }
    }

    private static boolean calibrated;

    /** Profiling only, once per session: what one drawing operation costs in time and garbage, so the per-frame total can be explained. */
    private void calibrate(GuiGraphics g) {
        calibrated = true;
        String sample = "Burns you inflict deal 50% more damage.";
        record Op(String name, int n, Runnable run) {}
        var ops = new java.util.ArrayList<Op>();
        ops.add(new Op("fill", 2000, () -> g.fill(-80, -80, -70, -70, 0xFF336699)));
        ops.add(new Op("drawString(40 chars)", 400, () -> g.drawString(font, sample, -400, -40, 0xFF000000, false)));
        ops.add(new Op("drawString(8 chars)", 400, () -> g.drawString(font, "Facets", -400, -40, 0xFF000000, false)));
        ops.add(new Op("font.split(40 chars)", 400, () -> font.split(Component.literal(sample), 186)));
        ops.add(new Op("font.plainSubstrByWidth", 400, () -> font.plainSubstrByWidth(sample, 120)));
        ops.add(new Op("font.width", 400, () -> font.width(sample)));
        ops.add(new Op("PixelArt.icon(shield)", 400, () -> PixelArt.icon(g, PixelArt.ICON_SHIELD, -80, -80, 0xFFFFFFFF)));
        ops.add(new Op("qpanel", 400, () -> PixelArt.qpanel(g, -300, -300, 100, 60)));
        ops.add(new Op("String.format(%,d)", 400, () -> String.format(Locale.ROOT, "%,d", 956056L)));
        for (var op : ops) {
            for (int i = 0; i < 50; i++) op.run().run();   // warm up
            long bytes = com.ascensionlib.Profiler.allocatedBytes(), t0 = System.nanoTime();
            for (int i = 0; i < op.n(); i++) op.run().run();
            long nanos = System.nanoTime() - t0, allocated = com.ascensionlib.Profiler.allocatedBytes() - bytes;
            org.slf4j.LoggerFactory.getLogger("ascensionlib").info("[profile] calibrate {}: {} bytes/op, {} us/op", op.name(),
                    allocated / op.n(), String.format(Locale.ROOT, "%.2f", nanos / 1000.0 / op.n()));
        }
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (com.ascensionlib.Profiler.on && !calibrated) calibrate(g);
        long profiled = com.ascensionlib.Profiler.start(), allocated = com.ascensionlib.Profiler.startAlloc();
        com.ascensionlib.Profiler.frame("craft.frameGap");
        super.render(g, mouseX, mouseY, delta);
        com.ascensionlib.Profiler.stop("craft.render", profiled);
        com.ascensionlib.Profiler.stopAlloc("craft.render", allocated);
        if (phase != Phase.BROWSE) return;
        int x = left + 8 + mainW + 6, w = SIDE_W - 6, ry = top + 8 + 36 + 3 + 18;
        for (var coin : coins()) {
            if (mouseX >= x + 2 && mouseX < x + w - 2 && mouseY >= ry && mouseY < ry + COIN_ROW_H - 1) {
                g.renderTooltip(font, Component.literal(coin.name()), mouseX, mouseY);
                break;
            }
            ry += COIN_ROW_H;
        }
    }

    /** The Unique tab's right panel: the chosen power, what it gives and what it costs, the Catalyst balance and the cost row. */
    private void drawUnique(GuiGraphics g, int rx, int t, int rw, int panelBottom) {
        var o = uniqueOption();
        if (o == null) {
            g.drawString(font, "No Unique powers are on offer.", rx + 8, t + 8, PixelArt.Q_MUTED, false);
            return;
        }
        String held = o.current() ? "held" : "";
        g.drawString(font, font.plainSubstrByWidth(o.name(), rw - 16 - font.width(held) - 8), rx + 8, t + 6, PixelArt.Q_INK, false);
        if (!held.isEmpty()) g.drawString(font, held, rx + rw - 8 - font.width(held), t + 6, 0xFF7A2E22, false);
        g.drawString(font, "Benefit", rx + 8, t + 22, PixelArt.Q_MUTED, false);
        int y = t + 32;
        for (var line : font.split(Component.literal(o.benefit()), rw - 16)) { if (y > t + 50) break; g.drawString(font, line, rx + 8, y, PixelArt.Q_INK, false); y += 9; }
        g.drawString(font, "Drawback", rx + 8, t + 66, PixelArt.Q_MUTED, false);
        y = t + 76;
        for (var line : font.split(Component.literal(o.drawback()), rw - 16)) { if (y > t + 94) break; g.drawString(font, line, rx + 8, y, 0xFF8A2E22, false); y += 9; }
        var u = view.unique();
        g.drawString(font, font.plainSubstrByWidth("Catalysts: " + u.catalysts(), rw - 16 - ASSEMBLE_W - 6), rx + 8, t + ASSEMBLE_Y + 3, PixelArt.Q_MUTED, false);
        int costY = panelBottom - 2 - 19;
        g.fill(rx + 6, costY - 3, rx + rw - 6, costY - 2, PixelArt.Q_LINE);
        int costW = rw - 12 - REVIEW_W - 8;
        g.drawString(font, "Cost", rx + 8, costY + 1, PixelArt.Q_MUTED, false);
        String reason = !u.block().isEmpty() ? u.block() : o.current() ? "Already held" : "";
        if (reason.isEmpty()) g.drawString(font, font.plainSubstrByWidth(shortCost(), costW), rx + 8, costY + 10, PixelArt.Q_INK, false);
        else g.drawString(font, font.plainSubstrByWidth(reason, costW), rx + 8, costY + 10, 0xFF8A2E22, false);
    }

    private String costText() {
        return switch (mode) {
            case UPGRADE -> "1 pending upgrade (you have " + view.pending() + ")";
            case REFINE -> price(view.refine());
            case REFORGE -> price(view.reforge());
            case PROMOTE -> price(view.promotion().price());
            case UNIQUE -> "1 Unique Catalyst (you have " + view.unique().catalysts() + ")";
        };
    }

    private String shortCost() {
        return switch (mode) {
            case UPGRADE -> "1 upgrade";
            case REFINE -> shortPrice(view.refine());
            case REFORGE -> shortPrice(view.reforge());
            case PROMOTE -> shortPrice(view.promotion().price());
            case UNIQUE -> "1 Catalyst";
        };
    }

    /** Promotion: the rarity step, the attunement progress toward it, and the cost row, in the same panel style as the other actions. */
    private void drawPromote(GuiGraphics g, int rx, int t, int rw, int panelBottom) {
        var p = view.promotion();
        g.drawString(font, "PROMOTE", rx + 8, t + 5, PixelArt.Q_MUTED, false);
        if (p.toRarity().isEmpty()) {
            g.drawString(font, "Already the highest rarity.", rx + 8, t + 16, PixelArt.Q_INK, false);
            return;
        }
        g.drawString(font, font.plainSubstrByWidth(rarityName(view.rarityId()) + " → " + rarityName(p.toRarity()), rw - 16), rx + 8, t + 16,
                PixelArt.Q_INK, false);
        int boxY = t + 30, boxW = (rw - 12 - 18) / 2, boxH = 34;
        valueBox(g, rx + 6, boxY, boxW, boxH, "Now", rarityName(view.rarityId()), "keeps all modifiers", false, true);
        g.drawString(font, "→", rx + 6 + boxW + 5, boxY + 15, PixelArt.Q_MUTED, false);
        valueBox(g, rx + 6 + boxW + 18, boxY, boxW, boxH, "Promote to", rarityName(p.toRarity()), "+1 modifier slot", true, true);
        int barY = boxY + boxH + 18;
        g.drawString(font, "Attunement " + p.attunement() + " / " + p.attunementNeeded(), rx + 8, barY - 11, PixelArt.Q_INK, false);
        int barW = rw - 16;
        g.fill(rx + 8, barY, rx + 8 + barW, barY + 6, PixelArt.Q_EDGE);
        g.fill(rx + 9, barY + 1, rx + 7 + barW, barY + 5, PixelArt.Q_WELL);
        int filled = p.attunementNeeded() <= 0 ? barW - 2 : (int) Math.min(barW - 2L, (long) (barW - 2) * p.attunement() / p.attunementNeeded());
        if (filled > 0) g.fill(rx + 9, barY + 1, rx + 9 + filled, barY + 5, PixelArt.style(view.rarityId()).panel());
        var explain = font.split(Component.literal("Attunement grows when this Pokémon is in your party for a Tower boss clear."), rw - 16);
        for (int i = 0; i < Math.min(2, explain.size()); i++) g.drawString(font, explain.get(i), rx + 8, barY + 10 + i * 9, PixelArt.Q_MUTED, false);
        int costY = panelBottom - 2 - 19;
        g.fill(rx + 6, costY - 3, rx + rw - 6, costY - 2, PixelArt.Q_LINE);
        int costW = rw - 12 - REVIEW_W - 8;
        g.drawString(font, "Cost", rx + 8, costY + 1, PixelArt.Q_MUTED, false);
        if (p.block().isEmpty()) g.drawString(font, font.plainSubstrByWidth(shortCost(), costW), rx + 8, costY + 10, PixelArt.Q_INK, false);
        else g.drawString(font, font.plainSubstrByWidth(p.block(), costW), rx + 8, costY + 10, 0xFF8A2E22, false);
    }

    private static String shortPrice(CraftPayloads.Price p) {
        StringBuilder text = new StringBuilder();
        if (p.dust() > 0) text.append(p.dust()).append(" Dust");
        if (p.facets() > 0) text.append(text.length() > 0 ? " + " : "").append(p.facets()).append(p.facets() == 1 ? " Facet" : " Facets");
        if (p.cores() > 0) text.append(text.length() > 0 ? " + " : "").append(p.cores()).append(p.cores() == 1 ? " Core" : " Cores");
        return text.length() == 0 ? "Free" : text.toString();
    }

    private String price(CraftPayloads.Price p) {
        StringBuilder text = new StringBuilder();
        if (p.dust() > 0) text.append(p.dust()).append(" Dust (have ").append(view.dust()).append(')');
        if (p.facets() > 0) text.append(text.length() > 0 ? " + " : "").append(p.facets()).append(p.facets() == 1 ? " Facet" : " Facets").append(" (have ").append(view.facets()).append(')');
        if (p.cores() > 0) text.append(text.length() > 0 ? " + " : "").append(p.cores()).append(p.cores() == 1 ? " Core" : " Cores").append(" (have ").append(view.cores()).append(')');
        return text.length() == 0 ? "Free" : text.toString();
    }

    private void valueBox(GuiGraphics g, int x, int y, int w, int h, String label, String big, String small, boolean next, boolean smallBig) {
        g.fill(x, y, x + w, y + h, next ? 0xFFC3D2E8 : PixelArt.Q_HEAD);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, next ? 0xFFDCE6F4 : PixelArt.Q_PANEL);
        g.drawString(font, font.plainSubstrByWidth(label, w - 8), x + (w - Math.min(font.width(label), w - 8)) / 2, y + 2, PixelArt.Q_MUTED, false);
        if (smallBig) {
            g.drawString(font, big, x + (w - font.width(big)) / 2, y + 13, PixelArt.Q_INK, false);
        } else {
            g.pose().pushPose();
            float scale = font.width(big) * 2 <= w - 6 ? 2f : 1f;
            g.pose().translate(x + (w - font.width(big) * scale) / 2, y + (scale == 2f ? 10 : 13), 0);
            g.pose().scale(scale, scale, 1f);
            g.drawString(font, big, 0, 0, PixelArt.Q_INK, false);
            g.pose().popPose();
        }
        g.drawString(font, font.plainSubstrByWidth(small, w - 6), x + (w - Math.min(font.width(small), w - 6)) / 2, y + h - 9, PixelArt.Q_MUTED, false);
    }

    /** The confirmation (or the wait for the answer) over a dimmed screen. */
    private void review(GuiGraphics g) {
        var s = slot();
        g.fill(0, 0, width, height, 0xB0140D09);
        int mx = left + pw / 2, my = top + ph / 2, bw = 270, bh = 120, bx = mx - bw / 2, by = my - 52;
        PixelArt.qpanel(g, bx, by, bw, bh);
        PixelArt.ring(g, bx + 3, by + 3, bw - 6, bh - 6, PixelArt.style(view.rarityId()).accent(), 1);
        if (s == null && mode != Mode.PROMOTE && mode != Mode.UNIQUE) return;
        String title = phase == Phase.WAITING ? "Working…" : "Confirm " + mode.name().toLowerCase(Locale.ROOT);
        g.drawString(font, title, bx + 10, by + 9, PixelArt.Q_INK, false);
        g.fill(bx + 8, by + 21, bx + bw - 8, by + 22, PixelArt.Q_LINE);
        String change = switch (mode) {
            case UPGRADE -> s.name() + " " + ROMAN[s.rank()] + " +" + s.value() + "%  →  " + ROMAN[Math.min(5, s.rank() + 1)] + ", rolls " + s.nextMin() + "–" + s.nextMax() + "%";
            case REFINE -> s.name() + " +" + s.value() + "%  →  a new value in " + s.bandMin() + "–" + s.bandMax() + "%";
            case PROMOTE -> rarityName(view.rarityId()) + "  →  " + rarityName(view.promotion().toRarity()) + ", and one new rank I modifier";
            case UNIQUE -> { var o = uniqueOption(); yield o == null ? "" : (view.unique().currentId().isEmpty() ? "Install " : "Replace with ") + o.name() + ". Drawback: " + o.drawback(); }
            default -> s.name() + " +" + s.value() + "%  →  another affix (" + s.reforgePool() + " eligible), rank " + ROMAN[s.rank()];
        };
        int ly = by + 28;
        for (var line : font.split(Component.literal(change), bw - 20)) { g.drawString(font, line, bx + 10, ly, PixelArt.Q_INK, false); ly += 10; }
        g.drawString(font, "Cost: " + costText(), bx + 10, ly + 2, PixelArt.Q_INK, false);
        String note = switch (mode) {
            case UPGRADE -> "No failure chance. This spends one pending upgrade.";
            case REFINE -> "The result can be lower than now. It cannot be undone.";
            case PROMOTE -> "No failure chance. Existing modifiers are kept. It cannot be undone.";
            case UNIQUE -> "Uses one Catalyst. A Unique already set is lost. It cannot be undone.";
            default -> "The result is random and cannot be undone.";
        };
        g.drawString(font, font.plainSubstrByWidth(note, bw - 20), bx + 10, ly + 14, PixelArt.Q_MUTED, false);
        if (phase == Phase.WAITING) g.drawString(font, "Waiting for the server…", bx + 10, by + bh - 16, PixelArt.Q_MUTED, false);
    }

    // ---- widgets ------------------------------------------------------------------------------------------------------------

    private static final class TabButton extends Button {
        private final String label;
        private final boolean selected;
        TabButton(int x, int y, int w, int h, String label, boolean selected, Runnable action) {
            super(x, y, w, h, Component.literal(label), b -> action.run(), DEFAULT_NARRATION);
            this.label = label;
            this.selected = selected;
        }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float dt) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            boolean hover = active && isHoveredOrFocused();
            g.fill(x, y, x + w, y + h + (selected ? 1 : 0), PixelArt.Q_EDGE);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1 + (selected ? 1 : 0), selected ? PixelArt.Q_ROW : hover ? PixelArt.Q_PANEL : PixelArt.Q_HEAD);
            if (selected) g.fill(x + 1, y + 1, x + w - 1, y + 3, PixelArt.BURGUNDY);
            var font = net.minecraft.client.Minecraft.getInstance().font;
            g.drawString(font, label, x + (w - font.width(label)) / 2, y + 5, selected ? PixelArt.Q_INK : PixelArt.Q_MUTED, false);
        }
    }

    private final class UniqueRow extends Button {
        private final CraftPayloads.UniqueOption option;
        private final boolean selected;
        UniqueRow(int x, int y, int w, int h, CraftPayloads.UniqueOption option, boolean selected, Runnable action) {
            super(x, y, w, h, Component.literal(option.name()), b -> action.run(), DEFAULT_NARRATION);
            this.option = option;
            this.selected = selected;
        }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float dt) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            boolean hover = active && isHoveredOrFocused();
            g.fill(x, y, x + w, y + h, selected ? 0xFFFFFFFF : hover ? PixelArt.Q_ROW : PixelArt.Q_PANEL);
            g.fill(x, y + h, x + w, y + h + 1, PixelArt.Q_LINE);
            if (selected) PixelArt.ring(g, x, y, w, h, PixelArt.style(view.rarityId()).accent(), 1);
            g.fill(x + 3, y + 4, x + 14, y + 15, 0xFF1B120D);
            g.fill(x + 4, y + 5, x + 13, y + 14, PixelArt.ECHO);
            PixelArt.icon(g, PixelArt.ICON_STAR, x + 5, y + 6, 0xFFFFFFFF);
            var font = net.minecraft.client.Minecraft.getInstance().font;
            String held = option.current() ? "held" : "";
            g.drawString(font, font.plainSubstrByWidth(option.name(), w - 24 - font.width(held) - 8), x + 19, y + 6, PixelArt.Q_INK, false);
            if (!held.isEmpty()) g.drawString(font, held, x + w - 5 - font.width(held), y + 6, 0xFF7A2E22, false);
        }
    }

    private final class RowButton extends Button {
        private final CraftPayloads.SlotView slot;
        private final boolean selected;
        private final String block;
        RowButton(int x, int y, int w, int h, CraftPayloads.SlotView slot, boolean selected, String block, Runnable action) {
            super(x, y, w, h, Component.literal(slot.name()), b -> action.run(), DEFAULT_NARRATION);
            this.slot = slot;
            this.selected = selected;
            this.block = block;
        }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float dt) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            boolean prefix = slot.category().equals("prefix");
            boolean hover = active && isHoveredOrFocused();
            g.fill(x, y, x + w, y + h, selected ? 0xFFFFFFFF : hover ? PixelArt.Q_ROW : PixelArt.Q_PANEL);
            g.fill(x, y + h, x + w, y + h + 1, PixelArt.Q_LINE);
            if (selected) PixelArt.ring(g, x, y, w, h, PixelArt.style(view.rarityId()).accent(), 1);
            int tint = prefix ? 0xFFC9694F : 0xFF6F9A52;
            g.fill(x + 3, y + 4, x + 14, y + 15, 0xFF1B120D);
            g.fill(x + 4, y + 5, x + 13, y + 14, tint);
            PixelArt.icon(g, prefix ? PixelArt.ICON_SWORD : PixelArt.ICON_SHIELD, x + 5, y + 6, 0xFFFFFFFF);
            int dim = block.isEmpty() ? PixelArt.Q_INK : PixelArt.Q_MUTED;
            var font = net.minecraft.client.Minecraft.getInstance().font;
            String value = "+" + slot.value() + "%";
            g.drawString(font, font.plainSubstrByWidth(slot.name(), w - 24 - font.width(value) - 8), x + 19, y + 2, dim, false);
            g.drawString(font, (prefix ? "Offense" : "Defense") + " · " + ROMAN[Math.max(1, Math.min(5, slot.rank()))], x + 19, y + 10, PixelArt.Q_MUTED, false);
            g.drawString(font, value, x + w - 5 - font.width(value), y + 6, selected ? 0xFF7A2E22 : dim, false);
        }
    }
}
