package com.fish.extendedae_plus_client.mixin.impl.helper;

import net.minecraft.client.gui.components.Button;

public interface HelperButtonOnPressModifier {
    Button.OnPress eaep$getOnPress();
    void eaep$setOnPress(Button.OnPress onPress);
}
