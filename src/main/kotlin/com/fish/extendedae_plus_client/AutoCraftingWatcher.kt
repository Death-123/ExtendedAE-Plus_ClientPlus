package com.fish.extendedae_plus_client

import appeng.api.implementations.menuobjects.ItemMenuHost
import appeng.client.gui.me.common.MEStorageScreen
import appeng.helpers.InventoryAction
import appeng.menu.SlotSemantics
import appeng.menu.me.common.GridInventoryEntry
import appeng.menu.me.common.IClientRepo
import appeng.menu.me.items.CraftingTermMenu
import com.fish.extendedae_plus_client.config.EAEPCConfig.autoCraftingFill
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.math.Axis
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.crafting.Ingredient
import kotlin.math.cos
import kotlin.math.sin

class AutoCraftingWatcher {
    // crafting slot index (0-9) to ingredient being crafted / waiting
    private val pendingSlots = HashMap<Int, Ingredient>()

    private var active = false
    private var startDelay = 0
    private var craftingSlotsOffset = -1
    var host = ""
    private var shouldTick = false


    companion object {
        @JvmField
        val INSTANCE: AutoCraftingWatcher = AutoCraftingWatcher()
    }

    fun onOpen(menu: CraftingTermMenu) {
        if (!isAutoInsertEnabled) return
        this.craftingSlotsOffset = menu.getSlots(SlotSemantics.CRAFTING_GRID).first().index
        shouldTick = getHost(menu) == host
    }

    fun getHost(menu: CraftingTermMenu): String {
        return if (menu.host is ItemMenuHost<*>) {
            (menu.host as ItemMenuHost<*>).item.toString()
        } else {
            menu.host.toString()
        }
    }

    fun setPending(recipeMap: MutableMap<Int, Ingredient>, slotsToWatch: MutableSet<Int>, menu: CraftingTermMenu) {
        this.pendingSlots.clear()
        host = getHost(menu)
        for (slotIndex in slotsToWatch) {
            val ing = recipeMap[slotIndex]
            if (ing != null && !ing.isEmpty) {
                this.pendingSlots[slotIndex] = ing
            }
        }

        if (!this.pendingSlots.isEmpty()) {
            startDelay = 15
            active = true
        }
    }

    fun clear() {
        this.pendingSlots.clear()
        this.active = false
        this.startDelay = 0
    }

    fun onTick(screen: MEStorageScreen<*>) {
        if (!isAutoInsertEnabled || !active || pendingSlots.isEmpty()) return
        if (startDelay > 0) {
            startDelay--
            return
        }
        val menu = screen.getMenu()
        if (menu !is CraftingTermMenu || !shouldTick) return
        val repo: IClientRepo = menu.clientRepo ?: return
        val craftingSlots: MutableList<Slot> = menu.getSlots(SlotSemantics.CRAFTING_GRID)

        val it: MutableIterator<MutableMap.MutableEntry<Int, Ingredient>> = pendingSlots.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            val slotIndex: Int = entry.key
            val ingredient = entry.value

            // if slot has been filled otherwise (e.g. by user)
            if (craftingSlots[slotIndex].hasItem()) {
                it.remove()
                continue
            }

            // check if system has this ingredient
            val entries = repo.getByIngredient(ingredient)

            for (potential in entries) {
                // Check if we have at least 1 stored
                if (potential.storedAmount > 0) {
                    menu.handleInteraction(potential.serial, InventoryAction.PICKUP_SINGLE)
                    val player = Minecraft.getInstance().player ?: return
                    player.connection.send(
                        ServerboundContainerClickPacket(
                            menu.containerId, 1, slotIndex + craftingSlotsOffset, 0, ClickType.PICKUP, menu.carried, Int2ObjectOpenHashMap()
                        )
                    )
                    repo.handleUpdate(
                        false, listOf(
                            GridInventoryEntry(potential.serial, potential.what, potential.storedAmount - 1, potential.requestableAmount, potential.isCraftable)
                        )
                    )
                    it.remove()
                    startDelay = 1
                    break
                }
            }
        }

        if (pendingSlots.isEmpty()) {
            active = false
        }
    }


    fun renderGhosts(guiGraphics: GuiGraphics, slot: Slot) {
        if (!active || !pendingSlots.containsKey(slot.index - craftingSlotsOffset)) return
        if (!shouldTick) return

        if (slot.hasItem()) return

        val ingredient: Ingredient = pendingSlots[slot.index - craftingSlotsOffset] ?: return
        val stacks = ingredient.items
        if (stacks.isEmpty()) return


        // Cycle items based on time
        val level = Minecraft.getInstance().level ?: return
        val time = level.gameTime / 30
        val stackToRender = stacks[(time % stacks.size).toInt()]


        // Render Ghost Logic
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(0f, 0f, 50f)


        // 1. Render the item
        guiGraphics.renderFakeItem(stackToRender, slot.x, slot.y)

        guiGraphics.pose().pushPose()

        guiGraphics.pose().translate(0f, 0f, 250f) // Draw on top of slot

        RenderSystem.disableDepthTest()
        guiGraphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x608B8B8B)
        RenderSystem.enableDepthTest()

        drawSpinner(guiGraphics, slot.x + 8, slot.y + 8)

        guiGraphics.pose().popPose()

        guiGraphics.pose().popPose()
    }

    private fun drawSpinner(guiGraphics: GuiGraphics, x: Int, y: Int) {
        val millis = System.currentTimeMillis()
        // Rotation speed: one full turn every 1000ms
        val angle = (millis % 1000) / 1000f * 360f

        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(x.toFloat(), y.toFloat(), 0f)
        guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(angle))


        // Draw 6 dots in a circle
        val dots = 6
        val radius = 5
        val color = -0x1 // White

        for (i in 0..<dots) {
            // Calculate alpha to create a "fade trail" effect
            // The leading dot is opaque, trailing dots fade out
            val alpha = 255 - (i * (200 / dots))
            val dotColor = (alpha shl 24) or (color and 0x00FFFFFF)

            // Position based on fixed circle, but we rotate the whole pose so simple math works
            val rad = Math.toRadians(((360f / dots) * i).toDouble())
            val dx = (cos(rad) * radius).toInt()
            val dy = (sin(rad) * radius).toInt()

            // Draw a small 2x2 or 1.5x1.5 dot
            // We use -1 offsets to center the dot on the calculated point
            guiGraphics.fill(dx - 1, dy - 1, dx + 1, dy + 1, dotColor)
        }

        guiGraphics.pose().popPose()
    }

    val isAutoInsertEnabled: Boolean
        get() = autoCraftingFill.get()

    fun toggleAutoInsert() {
        autoCraftingFill.set(!this.isAutoInsertEnabled)
        autoCraftingFill.save()
    }
}