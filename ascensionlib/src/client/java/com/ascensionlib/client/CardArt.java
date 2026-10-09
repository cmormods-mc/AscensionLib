package com.ascensionlib.client;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * A Pokemon's picture for a card well: CobblemonCards' 48x32 sprite at exactly 2x when that mod is installed and has one,
 * otherwise Cobblemon's own model, otherwise a plain label. Resolves from a species identifier and aspects (owned Pokemon) or
 * from a display name (a scouted enemy, whose form is not known, so the base form is used).
 */
final class CardArt {
    private final String speciesId;
    private final ResourceLocation sprite;
    private ModelWidget model;
    private boolean modelTried;
    private final List<String> aspects;
    final int dex;

    CardArt(String speciesId, List<String> aspects) {
        this.speciesId = speciesId == null ? "" : speciesId;
        this.aspects = aspects;
        this.sprite = this.speciesId.isEmpty() ? null : CardSprites.find(this.speciesId, aspects);
        this.dex = dexOf(this.speciesId);
    }

    /** The species identifier for a display name, or {@code ""} when it is not a species name (a nickname). */
    static String idForName(String name) {
        try {
            var species = PokemonSpecies.INSTANCE.getByName(name.toLowerCase(Locale.ROOT).replace(' ', '_'));
            return species == null ? "" : species.getResourceIdentifier().toString();
        } catch (RuntimeException | LinkageError e) {
            return "";
        }
    }

    static int dexOf(String speciesId) {
        if (speciesId.isEmpty()) return 0;
        try {
            var species = PokemonSpecies.INSTANCE.getByIdentifier(ResourceLocation.parse(speciesId));
            return species == null ? 0 : species.getNationalPokedexNumber();
        } catch (RuntimeException | LinkageError e) {
            return 0;
        }
    }

    /** Draws into the box (x, y, w, h); the caller has already painted the well. */
    void draw(GuiGraphics g, Font font, int x, int y, int w, int h, int mouseX, int mouseY, float delta, String fallback) {
        draw(g, font, x, y, w, h, mouseX, mouseY, delta, fallback, 2);
    }

    /** As above with the sprite at a whole-number {@code scale} (1 for a small header well, 2 for a card). */
    void draw(GuiGraphics g, Font font, int x, int y, int w, int h, int mouseX, int mouseY, float delta, String fallback, int scale) {
        if (sprite != null) {
            g.blit(sprite, x + (w - 48 * scale) / 2, y + (h - 32 * scale) / 2, 48 * scale, 32 * scale, 0, 0, 48, 32, 48, 32);
            return;
        }
        if (!modelTried) {
            modelTried = true;
            try {
                var species = speciesId.isEmpty() ? null : PokemonSpecies.INSTANCE.getByIdentifier(ResourceLocation.parse(speciesId));
                if (species != null) {
                    var renderable = new RenderablePokemon(species, new HashSet<>(aspects), ItemStack.EMPTY);
                    model = new ModelWidget(x, y, w, h, renderable, 2.7f, 325f, -10.0, false, false, 13);
                }
            } catch (RuntimeException | LinkageError e) {
                org.slf4j.LoggerFactory.getLogger("ascensionlib").warn("Card art model unavailable for {}", speciesId, e);
            }
        }
        if (model != null) {
            boolean scissored = false;
            try {
                model.setX(x);
                model.setY(y);
                g.enableScissor(x, y, x + w, y + h);
                scissored = true;
                model.render(g, mouseX, mouseY, delta);
                return;
            } catch (RuntimeException | LinkageError e) {
                org.slf4j.LoggerFactory.getLogger("ascensionlib").warn("Card art model failed; using label", e);
                model = null;
            } finally {
                // Only pop a scissor this call pushed: popping an empty stack throws and would hide the original failure.
                if (scissored) g.disableScissor();
            }
        }
        g.drawString(font, fallback, x + (w - font.width(fallback)) / 2, y + (h - 8) / 2, PixelArt.Q_MUTED, false);
    }
}
