package com.fish.extendedae_plus_client.upload.action

import appeng.api.crafting.IPatternDetails
import appeng.api.implementations.blockentities.PatternContainerGroup
import com.fish.extendedae_plus_client.upload.EncodingTerminalCtx
import net.minecraft.world.item.ItemStack
import java.util.concurrent.CompletableFuture

/**
 * Action used by `autoUploadMode == NONE`.
 *
 * Mirrors the historic behaviour: `MixinEncodingTerminal.eaep$makePatternAuto` simply has
 * no `if` branch for `NONE`, so the upload step is a no-op. The pre-step
 * `CacheProvider.markPattern` is still invoked (by `ClientLocalEncodeFlow` before
 * dispatching to this action), and the encoded pattern stays in `encodedPatternSlot` for
 * the player to pick up.
 *
 * Returns `true` from [supportsGroupSelection] because the legacy code path always reaches
 * `eaep$makePattern` regardless of mode, which means PROCESSING patterns still pop the
 * `ScreenProviderList` UI and CRAFTING patterns still auto-pick the assembler — even under
 * `NONE`. This action only no-ops the *upload* step itself.
 */
object NoOpAction : UploadAction {
    override fun supportsGroupSelection(): Boolean = true

    override fun upload(
        encoded: ItemStack,
        pattern: IPatternDetails,
        group: PatternContainerGroup?,
        ctx: EncodingTerminalCtx
    ): CompletableFuture<UploadResult> = CompletableFuture.completedFuture(UploadResult.Sent)
}
