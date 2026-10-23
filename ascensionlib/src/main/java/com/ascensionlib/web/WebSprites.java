package com.ascensionlib.web;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pokemon pictures for the pages. CobblemonCards (installed on the server too) holds a 48x32 sprite per species in its jar; a dedicated
 * server cannot render, but it can read those files. They go to a page as data URLs in batches under WebGUI's 32 KB event limit, once per
 * open page, and the page keeps them. Nothing is copied into our jar or into WebGUI's page folder (which has a shared cap on files).
 */
public final class WebSprites {
    private static final Logger LOG = LoggerFactory.getLogger("ascensionlib");
    private static final String CARDS_MOD = "cobblemon-cards";
    private static final String CARDS_ROOT = "assets/cobblemon-cards/textures/item/cards/pokemon/entity_icon/";
    /** Comfortably under WebGUI's 32 KB event limit once JSON escaping is counted. */
    private static final int BATCH_CHARS = 24_000;

    private static final int MAX_CACHED = 2048;
    private static final Map<String, Optional<String>> CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<String>> SENT = new ConcurrentHashMap<>();

    private WebSprites() {}

    /** The card sprite's path under the entity_icon folder for a species: {@code <dex, 4 digits>_<id without underscores>/<id>.png}. */
    static String fileFor(int dex, String speciesPath) {
        return String.format("%04d_%s/%s.png", dex, speciesPath.replace("_", ""), speciesPath);
    }

    /** The data URL for a species (its bare path, e.g. {@code mewtwo}), or empty when no installed mod provides it. */
    public static Optional<String> dataUrl(String speciesPath) {
        if (!speciesPath.matches("[a-z0-9_]+")) return Optional.empty();
        if (CACHE.size() >= MAX_CACHED) CACHE.clear();    // far more than the species that exist; a cap only so a stray id can never grow it without limit
        return CACHE.computeIfAbsent(speciesPath, WebSprites::load);
    }

    private static Optional<String> load(String speciesPath) {
        try {
            var cards = FabricLoader.getInstance().getModContainer(CARDS_MOD).orElse(null);
            if (cards == null) return Optional.empty();
            var species = PokemonSpecies.INSTANCE.getByIdentifier(ResourceLocation.fromNamespaceAndPath("cobblemon", speciesPath));
            if (species == null) return Optional.empty();
            var file = cards.findPath(CARDS_ROOT + fileFor(species.getNationalPokedexNumber(), speciesPath)).orElse(null);
            if (file == null) return Optional.empty();
            return Optional.of("data:image/png;base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(file)));
        } catch (IOException | RuntimeException | LinkageError exception) {
            LOG.warn("Could not load the sprite for {}: {}", speciesPath, exception.toString());
            return Optional.empty();
        }
    }

    /** A page for this player has just opened and holds no pictures yet. */
    public static void reset(UUID player) { SENT.put(player, new HashSet<>()); }

    public static void forget(UUID player) { SENT.remove(player); }

    /** Sends whichever of these species (full ids such as {@code cobblemon:mewtwo}) the player's open page does not hold yet. */
    public static void feed(ServerPlayer player, Collection<String> speciesIds) {
        Set<String> held = SENT.computeIfAbsent(player.getUUID(), id -> new HashSet<>());
        JsonObject batch = new JsonObject();
        int size = 0;
        for (String id : speciesIds) {
            if (id == null) continue;
            String path = id.substring(id.indexOf(':') + 1);
            if (held.contains(path)) continue;
            String url = dataUrl(path).orElse(null);
            held.add(path);                      // a miss is remembered too: one failed lookup per page, not one per message
            if (url == null) continue;
            if (size > 0 && size + url.length() > BATCH_CHARS) {
                WebUi.emitRaw(player, "sprites", batch.toString());
                batch = new JsonObject();
                size = 0;
            }
            batch.addProperty(path, url);
            size += url.length() + path.length();
        }
        if (size > 0) WebUi.emitRaw(player, "sprites", batch.toString());
    }
}
