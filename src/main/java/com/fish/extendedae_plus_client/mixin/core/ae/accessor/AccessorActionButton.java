package com.fish.extendedae_plus_client.mixin.core.ae.accessor;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.ActionButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ActionButton.class)
public interface AccessorActionButton {
    @Accessor("icon")
    Icon eaep$getIcon();
}
