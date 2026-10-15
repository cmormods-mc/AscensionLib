package com.ascensionlib.scout;

import java.util.List;

/**
 * The outcome of a capture, shown to the recipient only, after the profile is committed. Read-only; there is no reply, so closing or
 * skipping the reveal cannot reroll, grant, remove or duplicate anything.
 */
public record CaptureRevealPayload(String pokemonId, String name, String speciesId, List<String> aspects, ScoutPayloads.Detail detail) {}
