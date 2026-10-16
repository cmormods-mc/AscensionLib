package com.ascensionlib;

import com.cobbleascend.domain.v1.Tunables;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads {@code config/ascensionlib.json} into {@link Tunables} at server start and on {@code /ascend admin reload}. A new install gets a file that already holds every
 * default, so there is something to edit. The file is never rewritten once it exists, and a file that cannot be read leaves the defaults in force (the problem is logged),
 * so a typo can never stop the server or break the economy.
 */
public final class TunablesConfig {
    private static final Logger LOG = LoggerFactory.getLogger(AscensionLib.MOD_ID);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private TunablesConfig() {}

    public static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("ascensionlib.json"); }

    /** The outcome of a load: what is now in force and anything the file got wrong. */
    public record Result(Tunables tunables, List<String> problems) {}

    /** Loads {@code file} (writing the default file first if it does not exist) and makes the result current. */
    public static Result loadAndUse(Path file) {
        var result = read(file);
        Tunables.use(result.tunables());
        for (var problem : result.problems()) LOG.warn("config/ascensionlib.json: {}", problem);
        return result;
    }

    /** Reads without making anything current: the file, or the defaults when it is missing or unreadable. */
    public static Result read(Path file) {
        var problems = new ArrayList<String>();
        if (!Files.exists(file)) {
            writeTemplate(file);
            return new Result(Tunables.defaults(), problems);
        }
        try {
            JsonObject root = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
            if (root == null) { problems.add("the file is empty (using the defaults)"); return new Result(Tunables.defaults(), problems); }
            return new Result(Tunables.parse(root, problems), problems);
        } catch (IOException | JsonParseException | IllegalStateException unreadable) {
            problems.add("could not read it: " + unreadable.getMessage() + " (using the defaults; the file was left as it is)");
            return new Result(Tunables.defaults(), problems);
        }
    }

    private static void writeTemplate(Path file) {
        try {
            var root = new JsonObject();
            root.addProperty("_about", "AscensionLib tunables: the economy's dials, never its rules. Edit a number and run /ascend admin reload (or restart). A missing or invalid value uses the default and is named in the log. See docs/INSTALL.md.");
            var defaults = Tunables.defaults().toJson();
            for (var entry : defaults.entrySet()) root.add(entry.getKey(), entry.getValue());
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
            LOG.info("Wrote the default configuration to {}", file);
        } catch (IOException exception) {
            LOG.warn("Could not write the default configuration to {}: {}", file, exception.toString());
        }
    }

    /** The settings that differ between two configurations, as {@code path: old -> new} lines (empty when they are the same). */
    public static List<String> differences(Tunables before, Tunables after) {
        var out = new ArrayList<String>();
        diff("", before.toJson(), after.toJson(), out);
        return out;
    }

    private static void diff(String path, JsonObject a, JsonObject b, List<String> out) {
        for (var key : b.keySet()) {
            var x = a.get(key);
            var y = b.get(key);
            String where = path.isEmpty() ? key : path + "." + key;
            if (y.isJsonObject() && x != null && x.isJsonObject()) diff(where, x.getAsJsonObject(), y.getAsJsonObject(), out);
            else if (x == null || !x.equals(y)) out.add(where + ": " + x + " -> " + y);
        }
    }
}
