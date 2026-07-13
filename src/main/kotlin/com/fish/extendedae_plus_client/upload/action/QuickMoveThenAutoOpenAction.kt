package com.fish.extendedae_plus_client.upload.action

import appeng.api.crafting.IPatternDetails
import appeng.api.implementations.blockentities.PatternContainerGroup
import com.fish.extendedae_plus_client.mixin.impl.helper.WTLibHelper
import com.fish.extendedae_plus_client.upload.EncodingTerminalCtx
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.ItemStack
import java.util.concurrent.CompletableFuture

/**
 * Action used by `autoUploadMode == AUTO_OPEN`.
 *
 * QUICK_MOVE the encoded pattern into the player's inventory and then immediately try to open
 * a WTLib pattern-access terminal (cycles between `PATTERN_ACCESS` and `EX_PATTERN_ACCESS`).
 * Once that terminal opens, the existing `HelperPatternMoving.movePattern` queue takes over
 * and pushes patterns into providers.
 *
 * Mirrors the historic behaviour from `MixinEncodingTerminal.eaep$makePatternAuto`.
 * `markPattern` is performed by the orchestrating
 * [com.fish.extendedae_plus_client.upload.flow.ClientLocalEncodeFlow] before this action.
 */
object QuickMoveThenAutoOpenAction : UploadAction {
    override fun supportsGroupSelection(): Boolean = true

    override fun upload(
        encoded: ItemStack,
        pattern: IPatternDetails,
        group: PatternContainerGroup?,
        ctx: EncodingTerminalCtx
    ): CompletableFuture<UploadResult> {
        val player = Minecraft.getInstance().player
            ?: return CompletableFuture.completedFuture(
                UploadResult.Error("player is null")
            )
        player.connection.send(
            ServerboundContainerClickPacket(
                ctx.containerId,
                1,
                ctx.encodedPatternSlot.index,
                0,
                ClickType.QUICK_MOVE,
                ctx.menu.carried,
                Int2ObjectOpenHashMap()
            )
        )
        if (!WTLibHelper.openTerminalCyc(WTLibHelper.PATTERN_ACCESS)) {
            WTLibHelper.openTerminalCyc(WTLibHelper.EX_PATTERN_ACCESS)
        }
        return CompletableFuture.completedFuture(UploadResult.Sent)
    }
}
