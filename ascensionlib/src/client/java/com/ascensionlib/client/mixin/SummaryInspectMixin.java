package com.ascensionlib.client.mixin;

import com.ascensionlib.client.InspectClient;
import com.cobblemon.mod.common.client.gui.summary.Summary;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Summary.class)
public abstract class SummaryInspectMixin {
    @Inject(method = "init", at = @At("TAIL"))
    private void ascensionlib$inspect(CallbackInfo ci) {
        Summary summary = (Summary) (Object) this;
        InspectClient.summaryButton((Screen) (Object) this, summary::getSelectedPokemon$common);
    }
}
