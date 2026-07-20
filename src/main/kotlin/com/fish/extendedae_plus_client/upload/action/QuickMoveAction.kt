package com.fish.extendedae_plus_client.upload.action

import appeng.api.crafting.IPatternDetails
import appeng.api.implementations.blockentities.PatternContainerGroup
import com.fish.extendedae_plus_client.upload.EncodingTerminalCtx
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.ItemStack
import java.util.concurrent.CompletableFuture

/**
 * Action used by `autoUploadMode == WHEN_OPEN`.
 *
 * Mirrors the historic behaviour from `MixinEncodingTerminal.eaep$makePatternAuto`: simulates
 * a Shift+Click QUICK_MOVE on `encodedPatternSlot`, dispatching the encoded pattern into the
 * player's inventory so that the existing `HelperPatternMoving.movePattern` queue can later
 * push it into a Pattern Access terminal once the player opens one.
 *
 * The pre-step `CacheProvider.markPattern(pattern, group)` is performed by the orchestrating
 * [com.fish.extendedae_plus_client.upload.flow.ClientLocalEncodeFlow] before this action runs;
 * subsequent state cleanup (unmark/markAlready/incMark) happens later in the
 * `HelperPatternMoving.movePattern` queue when the player opens a PA terminal.
 */
object QuickMoveAction : UploadAction {
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
        return CompletableFuture.completedFuture(UploadResult.Sent)
    }
}
