package com.ascensionlib.scout;

import java.util.List;

/**
 * The outcome of a capture, shown to the recipient only, after the profile is committed. The stable revealId deduplicates delivery.
 * Read-only: closing or skipping cannot reroll, grant, remove or duplicate anything; the card can open the existing owner inspector.
 */
public record CaptureRevealPayload(String pokemonId, String name, String speciesId, List<String> aspects, ScoutPayloads.Detail detail,
                                   String revealId) {}
