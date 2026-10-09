package com.cobbleascend.domain.v1;

import com.cobbleascend.domain.Rules;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the Showdown module needs to make a frozen {@link CombatSnapshot} act in battle: for each Pokemon a flat list of
 * (affix id, rolled percent, optional type). The module owns every condition and the arithmetic; this only says what
 * a Pokemon holds. Pure and deterministic, so a battle's payload is a function of its snapshots.
 *
 * <p>The payload is compact on purpose (it travels as one format field, limited to 8192 characters):
 * <pre>{"v":1,"caps":{"out":100,"inc":50,"heal":50,"res":100},"mons":{"&lt;uuid&gt;":[{"i":"type_focus","p":8,"t":"Water"}]}}</pre>
 * A Unique is sent as one more effect whose percent is a plain "on" flag (1): its tuning is fixed inside the module.
 */
public final class BattleFx {
    /** What fits in one format field, with room to spare for the field's own quoting. */
    public static final int MAX_PAYLOAD_CHARS = 8000;

    public record Effect(String affixId, int percent, String type) {}

    private BattleFx() {}

    /** The ordinary slots of a snapshot as effects, in slot order (rolled values after {@link Resonance}), then its Unique if it has one. */
    public static List<Effect> effectsOf(CombatSnapshot snapshot) {
        var effects = new ArrayList<Effect>();
        var pieces = Resonance.pieces(snapshot.slots());
        for (var slot : snapshot.slots()) {
            if (slot.rolledValue() <= 0) continue;
            effects.add(new Effect(slot.affixId(), Resonance.apply(slot.affixId(), slot.rolledValue(), pieces), showdownType(slot.type())));
        }
        if (snapshot.uniqueId() != null) effects.add(new Effect(snapshot.uniqueId(), 1, null));
        return effects;
    }

    /** Showdown's move types are capitalised ("Water"); the catalog's are lower case. */
    static String showdownType(String type) {
        if (type == null || type.isEmpty()) return null;
        return type.substring(0, 1).toUpperCase(Locale.ROOT) + type.substring(1).toLowerCase(Locale.ROOT);
    }

    /**
     * The {@code ascensionFx} payload, or {@code null} when no Pokemon holds an effect (the battle is then left alone).
     *
     * @param monsByUuid effects per Pokemon, keyed by the uuid Cobblemon packs into the team
     * @throws IllegalStateException when the result would not fit one format field
     */
    public static String payload(Map<String, List<Effect>> monsByUuid, Rules rules) {
        var mons = new JsonObject();
        for (var entry : monsByUuid.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            var list = new JsonArray();
            for (var effect : entry.getValue()) {
                var json = new JsonObject();
                json.addProperty("i", effect.affixId());
                json.addProperty("p", effect.percent());
                if (effect.type() != null) json.addProperty("t", effect.type());
                list.add(json);
            }
            mons.add(entry.getKey(), list);
        }
        if (mons.size() == 0) return null;
        var caps = new JsonObject();
        caps.addProperty("out", rules.cap("outgoingDamage"));
        caps.addProperty("inc", rules.cap("incomingReduction"));
        caps.addProperty("heal", rules.cap("healing"));
        caps.addProperty("res", rules.cap("residual"));
        var root = new JsonObject();
        root.addProperty("v", 1);
        root.add("caps", caps);
        root.add("mons", mons);
        String text = root.toString();
        if (text.length() > MAX_PAYLOAD_CHARS)
            throw new IllegalStateException("Battle effects payload is " + text.length() + " characters");
        return text;
    }
}
