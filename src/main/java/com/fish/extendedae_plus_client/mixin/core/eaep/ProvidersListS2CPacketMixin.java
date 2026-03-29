package com.fish.extendedae_plus_client.mixin.core.eaep;

import com.extendedae_plus.network.ProvidersListS2CPacket;
import com.fish.extendedae_plus_client.mixin.impl.helper.HelperPatternMoving;
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

    @Inject(method = "handleClient",at = @At("HEAD"), cancellable = true)
    private static void handle(ProvidersListS2CPacket msg, CallbackInfo ci){
        if(HelperPatternMoving.eaepPendingGroup!=null){
            HelperPatternMoving.eaepPacketHandler(msg);
            ci.cancel();
        }
    }
}
