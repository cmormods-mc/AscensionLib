package com.ascensionlib.client;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Finds CobblemonCards' 48x32 Pokemon sprite for a species, when that mod is installed (its assets are read at runtime
 * through the resource manager: AscensionLib neither bundles nor depends on them). The layout there is
 * {@code entity_icon/<dex, 4 digits>_<id without underscores>/<id>[_<form>][_shiny].png}. A species with no sprite, or a
 * client without the mod, gets {@code null} and the caller falls back to Cobblemon's own model.
 */
final class CardSprites {
    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();
    private static final ResourceLocation MISSING = ResourceLocation.fromNamespaceAndPath("ascensionlib", "missing");

    private CardSprites() {}

    static ResourceLocation find(String speciesId, List<String> aspects) {
        if (!FabricLoader.getInstance().isModLoaded("cobblemon-cards")) return null;
        String key = speciesId + "|" + String.join(",", aspects);
        var cached = CACHE.get(key);
        if (cached != null) return cached == MISSING ? null : cached;
        ResourceLocation found = null;
        try {
            var species = PokemonSpecies.INSTANCE.getByIdentifier(ResourceLocation.parse(speciesId));
            if (species != null) {
                String path = species.getResourceIdentifier().getPath();
                String folder = String.format("%04d_%s", species.getNationalPokedexNumber(), path.replace("_", ""));
                boolean shiny = aspects.contains("shiny");
                List<String> names = new ArrayList<>();
                for (String aspect : aspects) {
                    if (aspect.equals("shiny") || aspect.equals("male") || aspect.equals("female")) continue;
                    if (shiny) names.add(path + "_" + aspect + "_shiny");
                    names.add(path + "_" + aspect);
                }
                if (shiny) names.add(path + "_shiny");
                names.add(path);
                var resources = Minecraft.getInstance().getResourceManager();
                for (String name : names) {
                    var rl = ResourceLocation.fromNamespaceAndPath("cobblemon-cards",
                            "textures/item/cards/pokemon/entity_icon/" + folder + "/" + name + ".png");
                    if (resources.getResource(rl).isPresent()) { found = rl; break; }
                }
            }
        } catch (RuntimeException | LinkageError e) {
            org.slf4j.LoggerFactory.getLogger("ascensionlib").warn("Card sprite lookup failed for {}", speciesId, e);
        }
        CACHE.put(key, found == null ? MISSING : found);
        return found;
    }

    static void clear() { CACHE.clear(); }
}
