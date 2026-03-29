package com.fish.extendedae_plus_client.mixin.impl.helper;

import net.minecraft.network.chat.Component;

import java.util.List;

public interface HelperProvidersListS2CPacket {
    List<Long> getIds();

    List<Component> getNames();

    List<Integer> getEmptySlots();
}
