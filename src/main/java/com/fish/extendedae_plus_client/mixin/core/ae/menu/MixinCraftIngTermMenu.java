package com.fish.extendedae_plus_client.mixin.core.ae.menu;

import appeng.menu.me.items.CraftingTermMenu;
import com.fish.extendedae_plus_client.AutoCraftingWatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingTermMenu.class)
public class MixinCraftIngTermMenu {
    @Inject(method = "clearCraftingGrid", at = @At("HEAD"))
    private static void onClearCraftingGrid(CallbackInfo ci) {
        AutoCraftingWatcher.INSTANCE.clear();
    }

    @Inject(method = "clearToPlayerInventory", at = @At("HEAD"))
    private static void onClearToPlayerInventory(CallbackInfo ci) {
        AutoCraftingWatcher.INSTANCE.clear();
    }

    @Inject(method = "<init>*", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        AutoCraftingWatcher.INSTANCE.onOpen((CraftingTermMenu) (Object) this);
    }

}
