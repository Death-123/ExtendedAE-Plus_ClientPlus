package com.fish.extendedae_plus_client.mixin.core.ae.accessor;

import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.RestrictedInputSlot;
import com.fish.extendedae_plus_client.mixin.impl.helper.HelperPatternEncodingTermMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PatternEncodingTermMenu.class)
public interface AccessorPatternEncodingTermMenu extends HelperPatternEncodingTermMenu {
    @Override
    @Accessor("encodedPatternSlot")
    RestrictedInputSlot eaep$getEncodedPatternSlot();

    /**
     * Exposes the private {@code encodePattern()} so callers (notably
     * {@code EncodingTerminalCtx.preEncode} in commit 2 onward) can produce a candidate
     * encoded {@link ItemStack} without consuming a blank pattern from the menu.
     *
     * <p>Returns {@code null} when the current menu state does not produce a valid pattern
     * (matches AE2's own contract).
     */
    @Invoker("encodePattern")
    ItemStack eaep$encodePattern();
}
