package com.ascensionlib.client.mixin;

import com.ascensionlib.client.InspectClient;
import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BattleGUI.class)
public abstract class BattleGuiInspectMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void ascensionlib$click(double x, double y, int button, CallbackInfoReturnable<Boolean> ci) {
        if (InspectClient.click((Screen) (Object) this, x, y, button)) ci.setReturnValue(true);
    }
}
