package com.fish.extendedae_plus_client.mixin.core.eaep;

import com.extendedae_plus.network.ProvidersListS2CPacket;
import com.fish.extendedae_plus_client.upload.routing.EaepResponseRouter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ProvidersListS2CPacket.class)
public class ProvidersListS2CPacketMixin {
    @Shadow
    @Final
    private List<Long> ids;
    @Shadow
    @Final
    private List<String> names;
    @Shadow
    @Final
    private List<Integer> emptySlots;

    /**
     * Routes EAEP's {@code ProvidersListS2CPacket} into our own {@link EaepResponseRouter}.
     *
     * <p>{@link EaepResponseRouter#dispatch} unconditionally refreshes
     * {@code EaepSlotsOverlay} and {@code EaepUploadAddressBook} on every packet so that the
     * group-selection UI's slot counts stay fresh and subsequent EAEP_BY_NAME uploads can
     * skip the {@code RequestProvidersListC2SPacket} round-trip via the address-book cache.
     *
     * <p>It returns {@code true} only when at least one pending upload was actually resolved
     * by this packet — in that case we {@code ci.cancel()} to suppress EAEP's own UI from
     * also reacting to a packet that was effectively sent in reply to our own
     * {@code RequestProvidersListC2SPacket}.
     */
    @Inject(method = "handleClient", at = @At("HEAD"), cancellable = true)
    private static void handle(ProvidersListS2CPacket msg, CallbackInfo ci) {
        if (EaepResponseRouter.dispatch(msg)) {
            ci.cancel();
        }
    }
}
