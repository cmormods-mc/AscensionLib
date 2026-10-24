package com.ascensionlib.wallet;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adds AscensionLib's item art to the server resource pack Polymer builds, so nobody has to unzip a pack by hand or merge model files.
 * Polymer is optional: this class is only touched when {@code polymer-resource-pack} is installed ({@link #register()} is guarded by the
 * caller), and without Polymer the mod behaves as before (an owner can still add {@code AscensionLib-resourcepack.zip} to Polymer by hand).
 *
 * <p>Two parts. The models and textures under {@code ascensionlib-pack/} in the jar are copied into the pack. The vanilla item model
 * files that map each custom model number to them are MERGED ({@link ItemModelOverrides}) when Polymer writes the pack, whichever other
 * pack also ships that base item, because Polymer would otherwise keep only one pack's file.
 */
public final class PolymerPack {
    private static final Logger LOG = LoggerFactory.getLogger("ascensionlib");
    static final String JAR_FOLDER = "ascensionlib-pack";

    private PolymerPack() {}

    /** Call only when {@code polymer-resource-pack} is loaded (the caller checks, so this class is never loaded without Polymer). */
    public static void register() {
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(PolymerPack::build);
        LOG.info("Polymer found: AscensionLib's item art is added to the server resource pack.");
    }

    private static void build(ResourcePackBuilder builder) {
        var required = ItemModelOverrides.required();
        var root = FabricLoader.getInstance().getModContainer("ascensionlib").flatMap(mod -> mod.findPath(JAR_FOLDER));
        if (root.isEmpty()) {
            LOG.error("The item art is missing from the AscensionLib jar ({} folder); the server pack gets no AscensionLib item art.", JAR_FOLDER);
            return;
        }
        builder.copyFromPath(root.get(), "", true);
        // Put our entries into every base item's model: into the file already in the pack, or a new one when none is.
        for (var entry : required.entrySet()) {
            var path = ItemModelOverrides.path(entry.getKey());
            var merged = mergeOrKeep(entry.getKey(), builder.getData(path), entry.getValue());
            if (merged != null) builder.addData(path, merged);
        }
        // Polymer writes files in its own order, and a pack added after this point could replace a model file whole. Merge again at write
        // time into whatever the file holds then (merging is idempotent), so the order of packs no longer matters.
        builder.addWriteConverter((path, data) -> {
            var base = ItemModelOverrides.baseOf(path);
            var wanted = base == null ? null : required.get(base);
            return wanted == null ? data : mergeOrKeep(base, data, wanted);
        });
        LOG.info("AscensionLib item art added to the Polymer pack ({} base items merged).", required.size());
    }

    private static byte[] mergeOrKeep(String base, byte[] existing, com.google.gson.JsonArray ours) {
        try {
            return ItemModelOverrides.merge(base, existing, ours);
        } catch (RuntimeException exception) {
            // A clashing number (or an unreadable file) must not stop Polymer from building the pack: leave that file as it is and say why.
            LOG.error("Could not add AscensionLib's art to {}: {}. Those items keep their vanilla look.", base, exception.getMessage());
            return existing;
        }
    }
}
