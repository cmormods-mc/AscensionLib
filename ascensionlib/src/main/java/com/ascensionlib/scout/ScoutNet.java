package com.ascensionlib.scout;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;

/** Registers the scouting channel on both sides and answers the client on the server. */
public final class ScoutNet {
    private ScoutNet() {}

    /** Payload types must be registered wherever they are encoded or decoded, so this runs from the common initializer. */
    public static void register() {
        OwnedInspectNet.register();
        CaptureRevealNet.register();
        com.ascensionlib.craft.CraftNet.register();
        com.ascensionlib.fusion.FusionNet.register();
        PayloadTypeRegistry.playS2C().register(ScoutPayloads.State.TYPE, ScoutPayloads.State.CODEC);
        PayloadTypeRegistry.playC2S().register(ScoutPayloads.Request.TYPE, ScoutPayloads.Request.CODEC);
        PayloadTypeRegistry.playC2S().register(ScoutPayloads.Use.TYPE, ScoutPayloads.Use.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ScoutPayloads.Request.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    if (com.ascensionlib.net.RateLimit.allow(context.player().getUUID(), "scout.request", 6, 3))
                        ScoutEncounters.push(context.player().getUUID());
                }));
        ServerPlayNetworking.registerGlobalReceiver(ScoutPayloads.Use.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    var player = context.player();
                    if (!com.ascensionlib.net.RateLimit.allow(player.getUUID(), "scout.use", 4, 2)) return;
                    String message = ScoutEncounters.use(player.getUUID(), payload.encounterId(), payload.subjectId());
                    player.displayClientMessage(Component.literal("[Scout] " + message), true);
                }));
    }
}
