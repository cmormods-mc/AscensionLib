package com.ascensionlib.web;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The bridge to WebGUI, the client mod that shows this server's web pages: opening a page for a player, pushing JSON events to it and
 * receiving the messages it posts. Every screen of this mod is a page under {@code web/ascensionlib/} in the jar, copied to WebGUI's
 * {@code config/webgui/web/} folder at start so WebGUI ships it to clients over the connection they already have; no client mod of ours.
 *
 * <p>WebGUI is optional at runtime. Nothing here touches its classes unless {@link #available()}. A page is code the player can edit,
 * so every message is bounded, parsed defensively and re-checked by the server exactly as a native packet would have been.
 */
public final class WebUi {
    private static final Logger LOG = LoggerFactory.getLogger("ascensionlib");
    private static final Gson GSON = new Gson();
    private static final int MAX_MESSAGE = 4096;
    private static boolean registered;

    private WebUi() {}

    public static boolean available() { return FabricLoader.getInstance().isModLoaded("webgui"); }

    /** Publishes the bundled pages when the server has started. Call once at startup; a no-op without WebGUI. */
    public static synchronized void init() {
        if (registered) return;
        registered = true;
        if (!available()) {
            LOG.info("WebGUI is not installed: AscensionLib's screens (craft, fusion, inspect, capture card) are unavailable. "
                    + "Install the WebGUI mod on the server and, with Rinku, on each client.");
            return;
        }
        ServerLifecycleEvents.SERVER_STARTED.register(server -> publish());
    }

    /**
     * Opens {@code page} (a file under web/ascensionlib/) for the player. Returns false and tells the player why when they cannot see it.
     * A page that needs data asks for it with its own "ready" message once it has loaded.
     */
    public static boolean open(ServerPlayer player, String page) {
        if (!available()) {
            player.sendSystemMessage(Component.literal("This screen needs the WebGUI mod on the server."));
            return false;
        }
        if (land.webgui.WebviewNetworking.clientKind(player) != land.webgui.WebviewNetworking.ClientKind.CURRENT) {
            player.sendSystemMessage(Component.literal("This screen is a web page: install the WebGUI mod (and Rinku) on your client."));
            return false;
        }
        land.webgui.api.WebviewApi.openGui(player, "webgui://ascensionlib/" + page);
        return true;
    }

    /** Opens a transparent overlay (HUD) page; the player can still play. */
    public static boolean openHud(ServerPlayer player, String page) {
        if (!available() || land.webgui.WebviewNetworking.clientKind(player) != land.webgui.WebviewNetworking.ClientKind.CURRENT) return false;
        land.webgui.api.WebviewApi.openHud(player, "webgui://ascensionlib/" + page);
        return true;
    }

    /** Whether this player's client can show our pages. */
    public static boolean canShow(ServerPlayer player) {
        return available() && land.webgui.WebviewNetworking.clientKind(player) == land.webgui.WebviewNetworking.ClientKind.CURRENT;
    }

    /** Sends one JSON event to the player's open page. */
    public static void emit(ServerPlayer player, String event, Object payload) {
        if (available()) land.webgui.api.WebviewApi.emitToPage(player, event, GSON.toJson(payload));
    }

    /** What a page's message handler receives: the player and the parsed JSON object the page posted. */
    public interface PageHandler { void handle(ServerPlayer player, JsonObject payload); }

    /**
     * Routes the page message with this {@code channel} to the handler on the server thread. A malformed or oversized message is dropped;
     * a failure in the handler is logged and never reaches the page.
     */
    public static void listen(String channel, PageHandler handler) {
        if (!available()) return;
        land.webgui.api.WebviewApi.onPageEvent(channel, (player, raw) -> {
            var server = player.getServer();
            if (server == null || raw == null || raw.length() > MAX_MESSAGE) return;
            server.execute(() -> {
                try {
                    JsonElement parsed = JsonParser.parseString(raw);
                    if (parsed.isJsonObject()) handler.handle(player, parsed.getAsJsonObject());
                } catch (JsonParseException | IllegalStateException | UnsupportedOperationException ignored) {
                    // A malformed message from a page is dropped, never an error for the server.
                } catch (RuntimeException exception) {
                    LOG.error("Page request {} failed for {}", channel, player.getGameProfile().getName(), exception);
                }
            });
        });
    }

    /** A bounded string field, or "" when absent or too long. */
    public static String str(JsonObject o, String key, int max) {
        var value = o.get(key);
        if (value == null || !value.isJsonPrimitive()) return "";
        String text = value.getAsString();
        return text.length() > max ? "" : text;
    }

    /** An id field (a UUID or a short slot id). */
    public static String id(JsonObject o, String key) { return str(o, key, 36); }

    /** A numeric field, or -1 when absent or not a number. */
    public static long num(JsonObject o, String key) {
        var value = o.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return -1;
        return value.getAsLong();
    }

    public static boolean bool(JsonObject o, String key) {
        var value = o.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean();
    }

    /** Copies every file under the jar's web/ascensionlib/ to WebGUI's web folder, then asks WebGUI to rescan. */
    private static void publish() {
        try {
            var container = FabricLoader.getInstance().getModContainer("ascensionlib").orElse(null);
            if (container == null) return;
            var source = container.findPath("web/ascensionlib").orElse(null);
            if (source == null) return;
            Path target = FabricLoader.getInstance().getConfigDir().resolve("webgui").resolve("web").resolve("ascensionlib");
            Files.createDirectories(target);
            int copied = 0;
            try (var files = Files.walk(source)) {
                for (Path file : (Iterable<Path>) files::iterator) {
                    if (!Files.isRegularFile(file)) continue;
                    Path out = target.resolve(source.relativize(file).toString());
                    byte[] bytes = Files.readAllBytes(file);
                    if (Files.exists(out) && java.util.Arrays.equals(Files.readAllBytes(out), bytes)) continue;
                    Files.createDirectories(out.getParent());
                    Files.write(out, bytes);
                    copied++;
                }
            }
            int sprites = publishSprites(target.resolve("sprites"));
            land.webgui.server.WebviewAssets.reload();
            LOG.info("Published the AscensionLib pages to {} ({} updated, {} card sprites)", target, copied, sprites);
        } catch (IOException | RuntimeException | LinkageError exception) {
            LOG.warn("Could not publish the AscensionLib web pages; the screens will not open", exception);
        }
    }

    private static final String CARD_SPRITES = "assets/cobblemon-cards/textures/item/cards/pokemon/entity_icon";

    /**
     * CobblemonCards (on the server too) holds a 48x32 sprite per species; pages cannot read a resource pack, so the base form of each
     * species is copied to {@code sprites/<species>.png} and the pages show it (and fall back to text without it). Returns how many exist.
     */
    private static int publishSprites(Path target) throws IOException {
        var container = FabricLoader.getInstance().getModContainer("cobblemon-cards").orElse(null);
        if (container == null) return 0;
        var root = container.findPath(CARD_SPRITES).orElse(null);
        if (root == null) {
            LOG.info("CobblemonCards is installed but has no sprites at {}; the pages show text instead of pictures", CARD_SPRITES);
            return 0;
        }
        Files.createDirectories(target);
        int count = 0;
        try (var folders = Files.list(root)) {
            for (Path folder : (Iterable<Path>) folders::iterator) {
                if (!Files.isDirectory(folder)) continue;
                try (var files = Files.list(folder)) {
                    for (Path file : (Iterable<Path>) files::iterator) {
                        String species = spriteSpecies(folder.getFileName().toString(), file.getFileName().toString());
                        if (species == null) continue;
                        Path out = target.resolve(species + ".png");
                        byte[] bytes = Files.readAllBytes(file);
                        if (!Files.exists(out) || !java.util.Arrays.equals(Files.readAllBytes(out), bytes)) Files.write(out, bytes);
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * The species id for a base-form sprite, or null for a form or shiny file. The folder is {@code <dex, 4 digits>_<id without
     * underscores>} and the base file is {@code <id>.png}, so it is the file whose name without underscores is the folder's suffix.
     */
    static String spriteSpecies(String folder, String file) {
        if (!file.endsWith(".png") || folder.length() < 6 || folder.charAt(4) != '_') return null;
        String id = file.substring(0, file.length() - 4);
        if (!id.matches("[a-z0-9_]+")) return null;
        return id.replace("_", "").equals(folder.substring(5)) ? id : null;
    }
}
