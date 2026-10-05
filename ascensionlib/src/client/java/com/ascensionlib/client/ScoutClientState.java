package com.ascensionlib.client;

import com.ascensionlib.scout.ScoutPayloads;

/** The latest scouting state the server sent. Only ever touched on the client thread. */
final class ScoutClientState {
    private static ScoutPayloads.State state = ScoutPayloads.State.none(0);

    private ScoutClientState() {}

    static ScoutPayloads.State get() {
        return state;
    }

    static void set(ScoutPayloads.State next) {
        state = next;
    }

    static void clear() {
        state = ScoutPayloads.State.none(0);
    }
}
