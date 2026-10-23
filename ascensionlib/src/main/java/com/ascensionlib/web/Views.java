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
                            OwnedInspectPayload.Extra extra, long sigils) {}

    /** One party member in the HUD's team list. */
    public record TeamView(String id, String name, String speciesId, String rarityId, int level) {}

    /** One bleeding Pokemon on the overlay: {@code side} is "ally" or "foe". */
    public record BleedView(String name, String side, int stacks, int turnsLeft) {}

    /** A Pokemon holding Momentum stacks on the overlay ({@code side} as for a bleed). */
    public record MomentumView(String name, String side, int stacks) {}

    /** A one-time Wardstone or Ensnaring effect to flash: {@code kind} is {@code wardstone} or {@code ensnaring}. */
    public record ProcView(String name, String side, String kind) {}

    /** What the overlay's effect strip shows: the standing Momentum stacks, and the one-time effects since the last push. */
    public record EffectsView(java.util.List<MomentumView> momentum, java.util.List<ProcView> procs) {}

    public static StateView state(ScoutPayloads.State state) {
        return new StateView(state.encounterId(), state.scouters(),
                state.entries().stream().map(e -> new EntryView(e.subjectId(), e.name(), e.detail().orElse(null), e.speciesId())).toList());
    }

    public static OwnedView owned(OwnedInspectPayload.Result result, long sigils) {
        return new OwnedView(result.pokemonId(), result.name(), result.species(), result.pending(), result.attunement(),
                result.detail().orElse(null), result.extra(), sigils);
    }
}
