package com.fish.extendedae_plus_client

import com.fish.extendedae_plus_client.config.EAEPCConfig
import com.fish.extendedae_plus_client.integration.ContextModLoaded
import com.fish.extendedae_plus_client.network.EAEPCNetworking
import net.minecraft.resources.ResourceLocation
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod


@Mod(ExtendedAEPlusClient.MODID, dist = [Dist.CLIENT])
class ExtendedAEPlusClient(eventBus : IEventBus, modContainer: ModContainer) {
    init {
        EAEPCConfig.init(modContainer)
        ContextModLoaded.init()
        eventBus.addListener(EAEPCNetworking::registerPackets)
    }

    @Mod(value = MODID, dist = [Dist.DEDICATED_SERVER])
    class ExtendedAEPlusServer(eventBus: IEventBus, modContainer: ModContainer) {
        init {
            eventBus.addListener(EAEPCNetworking::registerPackets)
        }
    }

    companion object {
        const val MODID = "extendedae_plus_client"

        @JvmStatic
        fun getLocation(path : String) : ResourceLocation =
            ResourceLocation.fromNamespaceAndPath(MODID, path)
    }
}