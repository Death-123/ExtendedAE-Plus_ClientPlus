package com.fish.extendedae_plus_client.mixin.core.ae.accessor;

import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.RestrictedInputSlot;
import com.fish.extendedae_plus_client.mixin.impl.helper.HelperPatternEncodingTermMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PatternEncodingTermMenu.class)
public interface AccessorPatternEncodingTermMenu extends HelperPatternEncodingTermMenu {
    @Override
    @Accessor("encodedPatternSlot")
    RestrictedInputSlot eaep$getEncodedPatternSlot();
}
