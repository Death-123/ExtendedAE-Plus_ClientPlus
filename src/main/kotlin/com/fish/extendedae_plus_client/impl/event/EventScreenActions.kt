package com.fish.extendedae_plus_client.impl.event

import appeng.api.stacks.AEItemKey
import appeng.api.stacks.AEKey
import appeng.api.stacks.GenericStack
import appeng.client.gui.AEBaseScreen
import appeng.core.AEConfig
import appeng.helpers.InventoryAction
import appeng.menu.me.common.MEStorageMenu
import com.fish.extendedae_plus_client.ExtendedAEPlusClient
import com.fish.extendedae_plus_client.config.EAEPCKeyMapping
import com.fish.extendedae_plus_client.integration.ContextModLoaded
import com.fish.extendedae_plus_client.integration.recipeViewer.HelperRecipeViewer.hoveredStacks
import com.fish.extendedae_plus_client.integration.recipeViewer.HelperRecipeViewer.isCheatMode
import com.fish.extendedae_plus_client.integration.recipeViewer.HelperRecipeViewer.matchesKey
import com.fish.extendedae_plus_client.integration.recipeViewer.HelperRecipeViewer.setSearchText
import com.fish.extendedae_plus_client.mixin.impl.helper.HelperSearchField
import com.mojang.datafixers.util.Pair
import de.mari_023.ae2wtlib.networking.PickBlockPacket
import net.minecraft.client.Minecraft
import net.minecraft.world.inventory.Slot
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.InputEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.network.PacketDistributor
import org.lwjgl.glfw.GLFW

@EventBusSubscriber(modid = ExtendedAEPlusClient.MODID, value = [Dist.CLIENT])
object EventScreenActions {
    private var isPulled = false

    @SubscribeEvent
    fun onMouseButtonPre(event: InputEvent.MouseButton.Pre) {
        if (Minecraft.getInstance().player == null) return
        if (Minecraft.getInstance().screen == null) return

        if (isCheatMode) return
        if (event.action != GLFW.GLFW_PRESS) {
            if (isPulled) event.setCanceled(true)
            isPulled = false
            return
        }
        val pulled: Pair<Boolean, Boolean>? = matchesKey(event.button)
        if (event.button != GLFW.GLFW_MOUSE_BUTTON_MIDDLE && pulled == null) return
        val menu = Minecraft.getInstance().player!!.containerMenu
        if (menu is MEStorageMenu) {
            val infoStack = findHoveredStack(menu) ?: return

            if (pulled != null) {
                menu.handleInteraction(
                    infoStack.getSecond(), getAction(infoStack, pulled)
                )
                isPulled = true
                return
            }

            if (event.button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                menu.handleInteraction(infoStack.getSecond(), InventoryAction.AUTO_CRAFT)
                event.setCanceled(true)
            }
        } else if (ContextModLoaded.ae2wtlib.isLoaded){
            val item = hoveredStacks.firstOrNull()?.what
            if (item !is AEItemKey) return
            PacketDistributor.sendToServer(PickBlockPacket(item.toStack()))
            isPulled = true
        }
    }

    @SubscribeEvent
    fun onKeyPressedPre(event: ScreenEvent.KeyPressed.Pre) {
        if (Minecraft.getInstance().player == null) return
        if (EAEPCKeyMapping.fillToSearchField.get().matches(event.keyCode, event.scanCode)) {
            // 增强功能, 现在可以检测所有EMIIngredient和screen里的ItemStack了
            // 大概会在一格有多个(?)stack的时候出bug, 但是真的会有那种时候吗?
            var stack: GenericStack? = null
            val stacks = hoveredStacks
            if (!stacks.isEmpty()) stack = stacks[0]
            if (stack == null && Minecraft.getInstance().screen is AEBaseScreen<*>) {
                val screen = Minecraft.getInstance().screen as AEBaseScreen<*>
                val slot: Slot = screen.slotUnderMouse ?: return
                stack = GenericStack.fromItemStack(slot.item)
            }
            if (stack == null) return
            val name = stack.what().getDisplayName().string

            // 写入 AE2 终端的搜索框
            if (AEConfig.instance().isUseExternalSearch) {
                setSearchText(name)
            } else if (Minecraft.getInstance().screen is HelperSearchField) {
                val screen = Minecraft.getInstance().screen as HelperSearchField
                screen.getSearchField().value = name
                screen.`eaep$setSearchText`(name)
            }
            event.setCanceled(true)
        }
    }

    private fun findHoveredStack(menu: MEStorageMenu): Pair<AEKey, Long>? {
        if (menu.clientRepo == null) return null

        val stacks = hoveredStacks
        var stack: GenericStack? = if (stacks.isEmpty()) null else stacks[0]
        if (stack == null) return null

        for (entry in menu.clientRepo!!.allEntries) {
            if (stack!!.what() != entry.what) {
                if (stack.what() !is AEItemKey) continue

                val unwrapped = GenericStack.unwrapItemStack((stack.what as AEItemKey).toStack())
                if (unwrapped == null || unwrapped.what() != entry.what) continue
                stack = unwrapped
            }

            return Pair<AEKey, Long>(stack.what(), entry.serial)
        }
        return null
    }

    private fun getAction(
        infoStack: Pair<AEKey, Long>, pulled: Pair<Boolean, Boolean>
    ): InventoryAction {
        return if (infoStack.getFirst() is AEItemKey) {
            if (pulled.getFirst() && pulled.getSecond()) InventoryAction.SHIFT_CLICK
            else if (pulled.getFirst()) InventoryAction.PICKUP_OR_SET_DOWN
            else if (pulled.getSecond())  // 这里没有对应的 action
                InventoryAction.SHIFT_CLICK
            else InventoryAction.PICKUP_SINGLE
        } else {
            if (pulled.getFirst() && pulled.getSecond()) InventoryAction.FILL_ENTIRE_ITEM_MOVE_TO_PLAYER
            else if (pulled.getFirst()) InventoryAction.FILL_ENTIRE_ITEM
            else if (pulled.getSecond()) InventoryAction.FILL_ITEM_MOVE_TO_PLAYER
            else InventoryAction.FILL_ITEM
        }
    }
}
