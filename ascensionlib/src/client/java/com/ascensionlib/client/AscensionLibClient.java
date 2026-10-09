package com.ascensionlib.client;

import com.ascensionlib.scout.ScoutPayloads;
import com.ascensionlib.scout.OwnedInspectPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.ascensionlib.scout.CaptureRevealPayload;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Client half of scouting: keeps the latest state the server sent and opens {@link ScoutScreen} on a keybind. The
 * keybind is the always-available entry point; buttons on Cobblemon's own screens come later and may break when
 * Cobblemon changes them, so this one must keep working on its own.
 */
public final class AscensionLibClient implements ClientModInitializer {
    private static KeyMapping openScout;

    @Override public void onInitializeClient() {
        AscensionClientSettings.load();
        ClientPlayNetworking.registerGlobalReceiver(com.ascensionlib.craft.CraftPayloads.View.TYPE, (payload, context) ->
                context.client().execute(() -> CraftClient.onView(payload)));
        ClientPlayNetworking.registerGlobalReceiver(com.ascensionlib.craft.CraftPayloads.Done.TYPE, (payload, context) ->
                context.client().execute(() -> CraftClient.onDone(payload)));
        ClientPlayNetworking.registerGlobalReceiver(CaptureRevealPayload.TYPE, (payload, context) ->
                context.client().execute(() -> CaptureReveals.accept(payload)));
        // /ascendui reveal <full|compact|off>, /ascendui motion <full|reduced>, /ascendui sounds <on|off>: presentation only.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(ClientCommandManager.literal("ascendui")
                .then(ClientCommandManager.literal("reveal").then(ClientCommandManager.argument("mode", StringArgumentType.word())
                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.List.of("full", "compact", "off"), b))
                        .executes(c -> {
                            try { AscensionClientSettings.reveal = AscensionClientSettings.RevealMode.valueOf(
                                    StringArgumentType.getString(c, "mode").toUpperCase(java.util.Locale.ROOT)); }
                            catch (IllegalArgumentException e) { c.getSource().sendError(Component.literal("Use full, compact or off.")); return 0; }
                            AscensionClientSettings.save();
                            c.getSource().sendFeedback(Component.literal("Capture reveal: " + AscensionClientSettings.reveal.name().toLowerCase(java.util.Locale.ROOT)));
                            return 1;
                        })))
                .then(ClientCommandManager.literal("motion").then(ClientCommandManager.argument("mode", StringArgumentType.word())
                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.List.of("full", "reduced"), b))
                        .executes(c -> {
                            AscensionClientSettings.reducedMotion = StringArgumentType.getString(c, "mode").equalsIgnoreCase("reduced");
                            AscensionClientSettings.save();
                            c.getSource().sendFeedback(Component.literal("Capture reveal motion: " + (AscensionClientSettings.reducedMotion ? "reduced" : "full")));
                            return 1;
                        })))
                .then(ClientCommandManager.literal("sounds").then(ClientCommandManager.argument("mode", StringArgumentType.word())
                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.List.of("on", "off"), b))
                        .executes(c -> {
                            AscensionClientSettings.sounds = StringArgumentType.getString(c, "mode").equalsIgnoreCase("on");
                            AscensionClientSettings.save();
                            c.getSource().sendFeedback(Component.literal("Capture reveal sounds: " + (AscensionClientSettings.sounds ? "on" : "off")));
                            return 1;
                        })))));
        openScout = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.ascensionlib.scout",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.ascensionlib"));

        ClientPlayNetworking.registerGlobalReceiver(ScoutPayloads.State.TYPE, (payload, context) ->
                context.client().execute(() -> ScoutClientState.set(payload)));
        ClientPlayNetworking.registerGlobalReceiver(OwnedInspectPayload.Result.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().screen instanceof AscendInspectScreen screen) screen.acceptOwned(payload);
                }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ScoutClientState.clear();
            CaptureReveals.clear();
            CraftClient.clear();
            InspectClient.clear();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            CaptureReveals.tick(client);
            while (openScout.consumeClick()) {
                if (client.player == null || client.screen != null) continue;
                // Ask first so the screen opens on fresh data; it redraws when the answer arrives.
                if (ClientPlayNetworking.canSend(ScoutPayloads.Request.TYPE)) {
                    ClientPlayNetworking.send(new ScoutPayloads.Request());
                }
                client.setScreen(new AscendInspectScreen(null, null, "Inspection"));
            }
        });
    }
}
