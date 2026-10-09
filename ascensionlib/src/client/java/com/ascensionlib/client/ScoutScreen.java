package com.ascensionlib.client;

import com.ascensionlib.scout.ScoutPayloads;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Lists the enemies of the player's current encounter and reveals them one Scouter at a time. An unscouted enemy shows
 * its name and a "?" layout with three placeholder rows whatever its real slot count (the layout cannot leak a rarity);
 * a scouted one shows its rarity, Unique, level, types, base stats and modifiers, never IVs, EVs or moves.
 *
 * <p>Plain fills and text, no textures: the approved preview (design/inspect) is the target for a later art pass.
 * The server is the only authority: this sends a request and redraws from whatever state comes back.
 */
final class ScoutScreen extends Screen {
    private static final int PANEL_W = 340;
    private static final int PANEL_H = 230;
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V"};
    private static final String[] STAT_NAMES = {"HP", "Atk", "Def", "SpA", "SpD", "Spe"};
    /** Accent colours from design/rarity-colors.json. */
    private static final Map<String, Integer> RARITY = Map.of(
            "common", 0xC1C5CF, "uncommon", 0x1BD7E8, "rare", 0x38CCEF,
            "epic", 0xAF69FF, "legendary", 0xFFD437, "mythical", 0xFF3448);

    private ScoutPayloads.State shown;
    private int selected;
    private int left;
    private int top;

    ScoutScreen() {
        super(Component.translatable("screen.ascensionlib.scout"));
    }

    @Override protected void init() {
        shown = ScoutClientState.get();
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;
        if (selected >= shown.entries().size()) selected = 0;

        List<ScoutPayloads.Entry> entries = shown.entries();
        for (int i = 0; i < Math.min(entries.size(), 8); i++) {
            int index = i;
            ScoutPayloads.Entry entry = entries.get(i);
            String label = (entry.detail().isPresent() ? "✓ " : "? ") + entry.name();
            Button button = Button.builder(Component.literal(label), pressed -> {
                selected = index;
                rebuildWidgets();
            }).bounds(left + 8, top + 28 + i * 22, 112, 20).build();
            button.active = index != selected;
            addRenderableWidget(button);
        }

        boolean canUse = !entries.isEmpty() && entries.get(selected).detail().isEmpty() && shown.scouters() > 0;
        Button use = Button.builder(Component.literal("Use Scouter (" + shown.scouters() + ")"), pressed -> {
            if (shown.entries().isEmpty()) return;
            ClientPlayNetworking.send(new ScoutPayloads.Use(shown.encounterId(),
                    shown.entries().get(selected).subjectId()));
        }).bounds(left + 128, top + PANEL_H - 28, 120, 20).build();
        use.active = canUse;
        addRenderableWidget(use);
        addRenderableWidget(Button.builder(Component.literal("Close"), pressed -> onClose())
                .bounds(left + PANEL_W - 68, top + PANEL_H - 28, 60, 20).build());
    }

    @Override public void tick() {
        // The server answers a request or a reveal with a new state; rebuild when it differs from what is shown.
        if (ScoutClientState.get() != shown) rebuildWidgets();
    }

    @Override public boolean isPauseScreen() {
        return false;
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(left, top, left + PANEL_W, top + PANEL_H, 0xE0101826);
        graphics.fill(left, top, left + PANEL_W, top + 1, 0xFF3A4A66);
        graphics.fill(left, top + PANEL_H - 1, left + PANEL_W, top + PANEL_H, 0xFF3A4A66);
        graphics.fill(left, top, left + 1, top + PANEL_H, 0xFF3A4A66);
        graphics.fill(left + PANEL_W - 1, top, left + PANEL_W, top + PANEL_H, 0xFF3A4A66);
        graphics.fill(left + 124, top + 26, left + 125, top + PANEL_H - 34, 0xFF3A4A66);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        com.ascensionlib.Profiler.frame("scout.frameGap");
        long profiled = com.ascensionlib.Profiler.start();
        draw(graphics, mouseX, mouseY, partialTick);
        com.ascensionlib.Profiler.stop("scout.render", profiled);
    }

    private void draw(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, "Scout", left + 8, top + 8, 0xFFFFFF);
        List<ScoutPayloads.Entry> entries = shown.entries();
        if (entries.isEmpty()) {
            graphics.drawString(font, "No encounter to scout right now.", left + 8, top + 34, 0xA0A8B8);
            return;
        }
        ScoutPayloads.Entry entry = entries.get(Math.min(selected, entries.size() - 1));
        int x = left + 132;
        int y = top + 10;
        graphics.drawString(font, entry.name(), x, y, 0xFFFFFF);
        entry.detail().ifPresentOrElse(detail -> drawDetail(graphics, detail, x, y + 14), () -> drawUnscouted(graphics, x, y + 14));
    }

    private void drawUnscouted(GuiGraphics graphics, int x, int y) {
        graphics.drawString(font, "Rarity: ?", x, y, 0x8890A0);
        y += 16;
        // Always three rows, whatever the enemy really has, so the layout reveals nothing.
        for (int i = 0; i < 3; i++) {
            graphics.fill(x, y, x + 196, y + 12, 0x40303A50);
            graphics.drawString(font, "? ? ?", x + 4, y + 2, 0x6A7288);
            y += 16;
        }
        graphics.drawString(font, "Spend a Scouter to reveal this", x, y + 6, 0xA0A8B8);
        graphics.drawString(font, "enemy's rarity and modifiers.", x, y + 18, 0xA0A8B8);
    }

    private void drawDetail(GuiGraphics graphics, ScoutPayloads.Detail detail, int x, int y) {
        int color = 0xFF000000 | RARITY.getOrDefault(detail.rarityId(), 0xC1C5CF);
        String label = detail.rarityId().equals("mythical") ? "Mythic" : capitalize(detail.rarityId());
        graphics.drawString(font, label, x, y, color);
        graphics.drawString(font, "Lv. " + detail.level() + "  " + String.join("/", detail.types().stream()
                .map(ScoutScreen::capitalize).toList()), x + 70, y, 0xC8D0E0);
        y += 14;
        if (!detail.uniqueName().isEmpty()) {
            graphics.drawString(font, "Unique: " + detail.uniqueName(), x, y, 0xFFB347);
            y += 12;
        }
        for (ScoutPayloads.Slot slot : detail.slots()) {
            int rankColor = slot.category().equals("prefix") ? 0x7FD1FF : 0xFFA6D5;
            graphics.drawString(font, capitalize(slot.category()).substring(0, 3) + " " + slot.name(), x, y, rankColor);
            graphics.drawString(font, ROMAN[Math.max(1, Math.min(5, slot.rank()))] + "  " + slot.value() + "%",
                    x + 150, y, 0xC8D0E0);
            y += 11;
        }
        if (detail.baseStats().size() == 6) {
            y += 4;
            for (int i = 0; i < 6; i++) {
                int column = i / 3;
                int row = i % 3;
                int sx = x + column * 100;
                int sy = y + row * 11;
                int value = detail.baseStats().get(i);
                graphics.drawString(font, STAT_NAMES[i], sx, sy, 0x8890A0);
                graphics.fill(sx + 22, sy + 2, sx + 22 + 52, sy + 8, 0xFF283044);
                graphics.fill(sx + 22, sy + 2, sx + 22 + Math.min(52, value * 52 / 255), sy + 8, color);
                graphics.drawString(font, String.valueOf(value), sx + 78, sy, 0xC8D0E0);
            }
        }
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }
}
