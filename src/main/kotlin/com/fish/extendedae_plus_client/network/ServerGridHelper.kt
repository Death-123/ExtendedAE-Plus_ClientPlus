package com.fish.extendedae_plus_client.network

import appeng.api.implementations.blockentities.PatternContainerGroup
import appeng.api.networking.IGrid
import appeng.helpers.patternprovider.PatternContainer
import com.fish.extendedae_plus_client.mixin.impl.helper.HelperAEBaseMenu
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.items.IItemHandler

/**
 * Server-side helper for grid access and pattern container enumeration.
 *
 * Provider scanning follows the same approach as EAEP:
 * iterate [IGrid.getMachineClasses], filter those assignable from
 * [PatternContainer], then call [IGrid.getActiveMachines] per class.
 */
object ServerGridHelper {

    fun getGridFromMenu(player: ServerPlayer): IGrid? {
        val menu = player.containerMenu
        val actionHost = (menu as? HelperAEBaseMenu)?.`eaep$getActionHost`() ?: return null
        val node = actionHost.actionableNode ?: return null
        return node.grid
    }

    /**
     * Iterate all online [PatternContainer]s on the grid.
     *
     * @param action receives (group, inventory). Return `true` to stop early.
     */
    inline fun forEachPatternContainer(
        grid: IGrid,
        action: (group: PatternContainerGroup, inv: IItemHandler) -> Boolean
    ) {
        for (machineClass in grid.machineClasses) {
            if (!PatternContainer::class.java.isAssignableFrom(machineClass)) continue
            for (machine in grid.getActiveMachines(machineClass)) {
                val container = machine as? PatternContainer ?: continue
                if (!container.isVisibleInTerminal) continue
                val group = container.terminalGroup ?: continue
                val inv = container.terminalPatternInventory?.toItemHandler() ?: continue
                if (action(group, inv)) return
            }
        }
    }
}
