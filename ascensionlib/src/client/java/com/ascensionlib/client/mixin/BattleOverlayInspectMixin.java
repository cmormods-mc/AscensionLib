package com.ascensionlib.client.mixin;

import com.ascensionlib.client.InspectClient;
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.gui.battle.BattleOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BattleOverlay.class)
public abstract class BattleOverlayInspectMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void ascensionlib$clear(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) { InspectClient.beginTiles(); }
    @Inject(method = "drawTile", at = @At("TAIL"), remap = false)
    private void ascensionlib$tile(GuiGraphics g, float delta, ActiveClientBattlePokemon pokemon, boolean left,
                                   int index, PokedexEntryProgress dex, boolean a, boolean b, boolean compact, CallbackInfo ci) {
        InspectClient.tile(g, pokemon, left, index, compact);
    }
}
