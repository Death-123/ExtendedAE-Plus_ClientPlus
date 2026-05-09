package com.fish.extendedae_plus_client.mixin.core.eaep;

import appeng.client.gui.Icon;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ActionButton;
import appeng.menu.me.items.PatternEncodingTermMenu;
import com.fish.extendedae_plus_client.config.EAEPCConfig;
import com.fish.extendedae_plus_client.config.enums.AutoUploadMode;
import com.fish.extendedae_plus_client.mixin.core.ae.accessor.AccessorActionButton;
import com.fish.extendedae_plus_client.mixin.impl.helper.HelperButtonOnPressModifier;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PatternEncodingTermScreen.class)
public abstract class MixinUploadCoreOverride<C extends PatternEncodingTermMenu>
        extends MEStorageScreen<C> {

    @Unique
    private Button.OnPress eaep$originalOnPress = null;

    public MixinUploadCoreOverride(C menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void eaep$afterInit(CallbackInfo ci) {
        if (!ModList.get().isLoaded("extendedae_plus")) return;
        for (var child : this.children()) {
            if (!(child instanceof ActionButton btn)) continue;
            if (((AccessorActionButton) btn).eaep$getIcon() != Icon.WHITE_ARROW_DOWN) continue;

            var helper = (HelperButtonOnPressModifier) btn;
            if (eaep$originalOnPress == null) {
                eaep$originalOnPress = helper.eaep$getOnPress();
            }
            var original = eaep$originalOnPress;
            helper.eaep$setOnPress(button -> {
                if (EAEPCConfig.autoUploadMode.get() == AutoUploadMode.EAEP_BY_NAME) {
                    this.getMenu().encode();
                } else {
                    original.onPress(button);
                }
            });
            return;
        }
    }
}
