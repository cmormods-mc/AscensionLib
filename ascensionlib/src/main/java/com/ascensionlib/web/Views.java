package com.ascensionlib.web;

import com.ascensionlib.scout.OwnedInspectPayload;
import com.ascensionlib.scout.ScoutPayloads;
import java.util.List;

/** JSON shapes for the pages: the plain records, with the {@code Optional}s the server uses turned into nullable fields (Gson cannot read an Optional). */
public final class Views {
    private Views() {}

    public record EntryView(String subjectId, String name, ScoutPayloads.Detail detail, String speciesId) {}

    public record StateView(String encounterId, int scouters, List<EntryView> entries) {}

    public record OwnedView(String pokemonId, String name, String species, int pending, int attunement, ScoutPayloads.Detail detail,
                            OwnedInspectPayload.Extra extra) {}

    /** One party member in the HUD's team list. */
    public record TeamView(String id, String name, String speciesId, String rarityId, int level) {}

    /** One bleeding Pokemon on the overlay: {@code side} is "ally" or "foe". */
    public record BleedView(String name, String side, int stacks, int turnsLeft) {}

    public static StateView state(ScoutPayloads.State state) {
        return new StateView(state.encounterId(), state.scouters(),
                state.entries().stream().map(e -> new EntryView(e.subjectId(), e.name(), e.detail().orElse(null), e.speciesId())).toList());
    }

    public static OwnedView owned(OwnedInspectPayload.Result result) {
        return new OwnedView(result.pokemonId(), result.name(), result.species(), result.pending(), result.attunement(),
                result.detail().orElse(null), result.extra());
    }
}
