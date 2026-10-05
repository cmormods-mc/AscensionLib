package com.ascensionlib.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Integer-pixel bronze magnifier with a native keyboard/narration target. */
final class InspectButton extends Button {
    static final int SIZE = 16;

    InspectButton(int x, int y, OnPress press) {
        super(x, y, SIZE, SIZE, Component.literal("Inspect ascension"), press, DEFAULT_NARRATION);
        setTooltip(Tooltip.create(getMessage()));
    }

    @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
        glyph(g, getX(), getY(), SIZE, isHoveredOrFocused());
    }

    /** A bronze plate with a lens and handle, drawn in whole pixels inside a {@code size} square (12 for battle tiles, 16 for Summary). */
    static void glyph(GuiGraphics g, int x, int y, int size, boolean hover) {
        g.fill(x, y, x + size, y + size, PixelArt.OUTLINE);
        g.fill(x + 1, y + 1, x + size - 1, y + size - 1, hover ? PixelArt.BRONZE_LIGHT : PixelArt.BRONZE);
        g.fill(x + 1, y + 1, x + size - 1, y + 2, hover ? 0xFFF3D9A6 : PixelArt.BRONZE_LIGHT);
        g.fill(x + 1, y + size - 2, x + size - 1, y + size - 1, PixelArt.BRONZE_SHADOW);
        int u = size >= 16 ? 2 : 1;
        int lx = x + 3 * u, ly = y + 3 * u, ls = size >= 16 ? 6 : 4;
        g.fill(lx, ly, lx + ls, ly + ls, PixelArt.INK);
        g.fill(lx + u, ly + u, lx + ls - u, ly + ls - u, PixelArt.CREAM);
        g.fill(lx + ls - u, ly + ls - u, lx + ls + u + (size >= 16 ? 1 : 0), ly + ls + u + (size >= 16 ? 1 : 0), PixelArt.INK);
    }
}
