package com.fish.extendedae_plus_client.impl.event

import appeng.client.gui.implementations.QuartzKnifeScreen
import appeng.client.gui.me.common.MEStorageScreen
import appeng.core.network.serverbound.SwitchGuisPacket
import appeng.menu.me.crafting.CraftingStatusMenu
import com.fish.extendedae_plus_client.ExtendedAEPlusClient
import com.fish.extendedae_plus_client.impl.cache.CacheCrafting
import com.fish.extendedae_plus_client.impl.cache.CacheCuttingKnife
import net.minecraft.client.Minecraft
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.network.PacketDistributor

@EventBusSubscriber(modid = ExtendedAEPlusClient.MODID, value = [Dist.CLIENT])
object EventScreenSwitching {
    @SubscribeEvent
    private fun onGuiOpening(event: ScreenEvent.Opening) {
        when (event.newScreen) {
            is MEStorageScreen<*> -> handleMEStorageScreen(event)
            is QuartzKnifeScreen -> handleCuttingKnifeScreen(event)
        }
    }

    private fun handleMEStorageScreen(event: ScreenEvent.Opening) {
        if (event.currentScreen != null) return
        if (CacheCrafting.isEmpty) return
        CacheCrafting.isOpening = true
        PacketDistributor.sendToServer(SwitchGuisPacket.openSubMenu(CraftingStatusMenu.TYPE))
    }

    private fun handleCuttingKnifeScreen(event: ScreenEvent.Opening) {
        if (!CacheCuttingKnife.isHandlingBlockCopies) return
        event.setCanceled(true)
        Minecraft.getInstance().player?.closeContainer()
    }
}
