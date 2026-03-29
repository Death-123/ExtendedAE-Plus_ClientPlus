package com.fish.extendedae_plus_client.mixin.core.ae.accessor;

import com.extendedae_plus.network.ProvidersListS2CPacket;
import com.fish.extendedae_plus_client.mixin.impl.helper.HelperProvidersListS2CPacket;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
@Mixin(ProvidersListS2CPacket.class)
public interface AccessorProvidersListS2CPacket extends HelperProvidersListS2CPacket {
    @Accessor("ids")
    List<Long> getIds();

    @Accessor("names")
    List<Component> getNames();

    @Accessor("emptySlots")
    List<Integer> getEmptySlots();
}
