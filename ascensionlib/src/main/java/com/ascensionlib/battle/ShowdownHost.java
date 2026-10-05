package com.ascensionlib.battle;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Where AscensionLib's simulator code is installed and where it gets each battle's format fields. Plain {@code java.*},
 * because it runs from a pre-launch entrypoint, before Minecraft's classes are usable.
 *
 * <p>Today the one implementation is {@link RaidsShowdownHost}, which reaches CobbleRaids' extension API (the single
 * installer of Showdown files, for the reasons in docs/BATTLE-ADAPTER-DESIGN.md section 2). The design moves that host
 * into this library next; the rest of the adapter talks only to this interface, so that move replaces one class.
 */
public interface ShowdownHost {

    /** Supplies fields for one battle's {@code format} object; return an empty map for a battle that is not yours. */
    @FunctionalInterface
    interface FieldProvider {
        Map<String, String> fieldsFor(UUID battleId, List<UUID> playerIds);
    }

    /** A short name for the log. */
    String name();

    /** Installs a JavaScript module beside the simulator as {@code ext-<id>.js}. */
    void registerModule(String id, Supplier<InputStream> source);

    /** Adds fields to the {@code >start} format of every battle. */
    void registerFormatFields(FieldProvider provider);
}
