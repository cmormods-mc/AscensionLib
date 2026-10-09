package com.cobbleascend.domain.v1;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The researched lore behind fusion (docs/FUSION-DESIGN.md): what every Cobblemon species is based on, reduced to up to three
 * {@link Motif}s, and how motifs relate. Data only; {@link Transcendence} turns it into a result. Loaded from
 * {@code design/lore/species-lore.json} and {@code design/lore/motifs.json}.
 */
public final class LoreCatalog {
    /** A theme a species can carry. {@code adjective} and {@code noun} build Transcendent names. */
    public record Motif(String id, String adjective, String noun, String summary) {}

    /** One species: its Cobblemon id, evolutionary family, types, motifs (strongest first) and what it is based on. */
    public record SpeciesLore(int dex, String species, String name, int family, List<String> types, List<String> motifs, String basis) {
        public SpeciesLore {
            types = List.copyOf(types);
            motifs = List.copyOf(motifs);
            if (types.isEmpty() || motifs.isEmpty() || motifs.size() > 3) throw new IllegalArgumentException("Invalid lore for " + species);
        }

        public String primaryMotif() { return motifs.get(0); }
        public String primaryType() { return types.get(0); }
    }

    private final Map<String, Motif> motifs;
    private final Map<String, SpeciesLore> species;
    private final Set<String> kindred;
    private final Set<String> opposed;

    private LoreCatalog(Map<String, Motif> motifs, Map<String, SpeciesLore> species, Set<String> kindred, Set<String> opposed) {
        this.motifs = Collections.unmodifiableMap(motifs);
        this.species = Collections.unmodifiableMap(species);
        this.kindred = Set.copyOf(kindred);
        this.opposed = Set.copyOf(opposed);
        for (var lore : species.values()) {
            for (var motif : lore.motifs()) {
                if (!motifs.containsKey(motif)) throw new IllegalArgumentException(lore.species() + " has unknown motif " + motif);
            }
        }
        for (var relation : Set.of(this.kindred, this.opposed)) {
            for (var pair : relation) {
                var parts = pair.split("-");
                if (parts.length != 2 || !motifs.containsKey(parts[0]) || !motifs.containsKey(parts[1]))
                    throw new IllegalArgumentException("Unknown motif relation " + pair);
            }
        }
    }

    public static LoreCatalog defaults() {
        return parse(resource("lore/species-lore.json"), resource("lore/motifs.json"));
    }

    public static LoreCatalog parse(JsonObject speciesJson, JsonObject motifsJson) {
        if (Json.integer(speciesJson.get("schemaVersion")) != 1 || Json.integer(motifsJson.get("schemaVersion")) != 1)
            throw new IllegalArgumentException("Unknown lore schema");
        var motifMap = new LinkedHashMap<String, Motif>();
        for (var element : Json.array(motifsJson.get("motifs"))) {
            var object = Json.object(element);
            var motif = new Motif(Json.string(object.get("id")), Json.string(object.get("adjective")),
                    Json.string(object.get("noun")), Json.string(object.get("summary")));
            if (motifMap.put(motif.id(), motif) != null) throw new IllegalArgumentException("Duplicate motif " + motif.id());
        }
        var speciesMap = new LinkedHashMap<String, SpeciesLore>();
        for (var element : Json.array(speciesJson.get("species"))) {
            var object = Json.object(element);
            var lore = new SpeciesLore(Json.integer(object.get("dex")), Json.string(object.get("species")), Json.string(object.get("name")),
                    Json.integer(object.get("family")), strings(object.get("types")), strings(object.get("motifs")),
                    Json.string(object.get("basis")));
            if (speciesMap.put(lore.species(), lore) != null) throw new IllegalArgumentException("Duplicate species " + lore.species());
        }
        return new LoreCatalog(motifMap, speciesMap, new java.util.HashSet<>(strings(motifsJson.get("kindred"))),
                new java.util.HashSet<>(strings(motifsJson.get("opposed"))));
    }

    private static List<String> strings(com.google.gson.JsonElement element) {
        var list = new ArrayList<String>();
        for (var item : Json.array(element)) list.add(Json.string(item));
        return list;
    }

    private static JsonObject resource(String name) {
        var stream = LoreCatalog.class.getResourceAsStream("/cobbleascend/" + name);
        if (stream == null) throw new IllegalStateException("Missing lore resource: " + name);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read lore resource", exception);
        }
    }

    public Optional<SpeciesLore> species(String speciesId) { return Optional.ofNullable(species.get(speciesId)); }
    public Map<String, SpeciesLore> allSpecies() { return species; }
    public Map<String, Motif> motifs() { return motifs; }
    public Motif motif(String id) { return motifs.get(id); }

    private static String key(String a, String b) { return a.compareTo(b) <= 0 ? a + "-" + b : b + "-" + a; }

    public boolean isKindred(String a, String b) { return kindred.contains(key(a, b)); }
    public boolean isOpposed(String a, String b) { return opposed.contains(key(a, b)); }
}
