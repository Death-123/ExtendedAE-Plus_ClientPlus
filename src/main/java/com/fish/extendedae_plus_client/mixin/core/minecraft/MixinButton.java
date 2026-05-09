package com.fish.extendedae_plus_client.mixin.core.minecraft;

import com.fish.extendedae_plus_client.mixin.impl.helper.HelperButtonOnPressModifier;
import net.minecraft.client.gui.components.Button;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Button.class)
public class MixinButton implements HelperButtonOnPressModifier {
    @Shadow
    @Final
    @Mutable
    protected Button.OnPress onPress;

    @Override
    public Button.OnPress eaep$getOnPress() {
        return this.onPress;
    }

    @Override
    public void eaep$setOnPress(Button.OnPress onPress) {
        this.onPress = onPress;
    }
}
