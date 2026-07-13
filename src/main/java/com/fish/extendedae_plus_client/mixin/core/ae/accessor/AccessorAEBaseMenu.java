package com.fish.extendedae_plus_client.mixin.core.ae.accessor;

import appeng.api.networking.security.IActionHost;
import appeng.menu.AEBaseMenu;
import com.fish.extendedae_plus_client.mixin.impl.helper.HelperAEBaseMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AEBaseMenu.class)
public interface AccessorAEBaseMenu extends HelperAEBaseMenu {
    @Invoker("getActionHost")
    IActionHost eaep$getActionHost();

    /**
     * Exposes the protected {@code sendClientAction(String)} so that
     * {@link com.fish.extendedae_plus_client.upload.flow.ServerByGroupEncodeFlow} can
     * directly trigger AE2's server-side encode (RPC #2 first half) without going through
     * {@code PatternEncodingTermMenu.encode()} — which would re-enter our own
     * {@code MixinEncodingTerminal.onEncode} inject and infinite-loop.
     *
     * <p>Used via {@code EncodingTerminalCtx.triggerAE2Encode} in commit 3 onward.
     */
    @Invoker("sendClientAction")
    void eaep$sendClientAction(String action);
}
