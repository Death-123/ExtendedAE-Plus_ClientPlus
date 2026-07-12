package com.fish.extendedae_plus_client.mixin.core.ae.screen;

import appeng.client.gui.me.common.MEStorageScreen;
import com.fish.extendedae_plus_client.AutoCraftingWatcher;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MEStorageScreen.class)
public class MixinMEStorageScreen {

    @Inject(method = "containerTick", at = @At("RETURN"))
    private void onContainerTick(CallbackInfo ci) {
        MEStorageScreen<?> screen = (MEStorageScreen<?>) (Object) this;
        AutoCraftingWatcher.INSTANCE.onTick(screen);
    }

    @Inject(method = "renderSlot", at = @At("HEAD"))
    private void onRenderSlot(GuiGraphics guiGraphics, Slot slot, CallbackInfo ci) {
        AutoCraftingWatcher.INSTANCE.renderGhosts(guiGraphics, slot);
    }
}