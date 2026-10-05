package com.ascensionlib.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * A quartz pixel button. Secondary (the default) is white with a slate edge and ink text; primary is crimson with a white edge and
 * white text, for the one action a screen wants you to take. Same widget behaviour as vanilla (keyboard focus, narration, click
 * sound); pressing draws the face one pixel lower for a moment; a disabled button is flat grey.
 */
final class PixelButton extends Button {
    private static final int CRIMSON = 0xFF8E1426, CRIMSON_HOVER = 0xFFA51A30, CRIMSON_PRESSED = 0xFF7A0F20, CRIMSON_SHADE = 0xFF620512;
    private long pressedAt;
    private boolean primary;

    PixelButton(int x, int y, int w, int h, Component label, OnPress press) {
        super(x, y, w, h, label, press, DEFAULT_NARRATION);
    }

    /** Marks this as the screen's main action (crimson). */
    PixelButton primary() {
        this.primary = true;
        return this;
    }

    @Override public void onPress() {
        pressedAt = System.nanoTime();
        super.onPress();
    }

    @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
        var font = Minecraft.getInstance().font;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hover = active && isHoveredOrFocused();
        boolean pressed = active && pressedAt != 0 && System.nanoTime() - pressedAt < 100_000_000L;
        int edge, fill, shade, text;
        if (!active) {
            edge = PixelArt.Q_LINE; fill = PixelArt.Q_HEAD; shade = PixelArt.Q_LINE; text = 0xFF9AA4B6;
        } else if (primary) {
            edge = PixelArt.Q_WHITE; fill = pressed ? CRIMSON_PRESSED : hover ? CRIMSON_HOVER : CRIMSON; shade = CRIMSON_SHADE; text = 0xFFFFFFFF;
        } else {
            edge = PixelArt.Q_EDGE; fill = pressed ? PixelArt.Q_HEAD : hover ? 0xFFFFFFFF : PixelArt.Q_ROW; shade = PixelArt.Q_WELL; text = PixelArt.Q_INK;
        }
        g.fill(x, y, x + w, y + h, edge);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        if (active && !pressed) {
            g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, shade);                       // soft shade along the bottom
            g.fill(x + 1, y + 1, x + w - 1, y + 2, primary ? 0x33FFFFFF : 0xFFFFFFFF);  // highlight along the top
        }
        if (isFocused() && active) PixelArt.ring(g, x - 1, y - 1, w + 2, h + 2, primary ? PixelArt.Q_WHITE : CRIMSON, 1);
        String label = font.plainSubstrByWidth(getMessage().getString(), Math.max(1, w - 10));
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2 + (pressed ? 1 : 0), text, false);
    }
}
