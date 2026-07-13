package com.fish.extendedae_plus_client.upload.action

import appeng.api.crafting.IPatternDetails
import appeng.api.implementations.blockentities.PatternContainerGroup
import com.fish.extendedae_plus_client.upload.EncodingTerminalCtx
import net.minecraft.world.item.ItemStack
import java.util.concurrent.CompletableFuture

/**
 * Strategy that knows how to actually deliver an encoded pattern to its destination.
 *
 * Used by `ClientLocalEncodeFlow`. `ServerByGroupEncodeFlow` does NOT consume this interface
 * because its upload step is bespoke (RPC #2 = AE2 ACTION_ENCODE + UploadPatternByGroupPacket).
 *
 * Threading: implementations may complete the future on any thread; callers MUST marshal
 * UI side-effects back to the render thread.
 */
interface UploadAction {

    /**
     * Whether this action requires the user to pick a target group:
     *  - `true` for [QuickMoveAction] / [QuickMoveThenAutoOpenAction]:
     *    `ClientLocalEncodeFlow` will pop `ScreenProviderList` (PROCESSING) or auto-pick the
     *    first matching crafting group (CRAFTING).
     *  - `true` for [EaepByNameAction] starting from commit 4 (commit 1 returns the legacy `false`
     *    so `MixinUploadCoreOverride` keeps owning the EAEP_BY_NAME special case).
     *  - `false` for [NoOpAction].
     */
    fun supportsGroupSelection(): Boolean

    /**
     * Issues the upload.
     *
     * @param encoded  the encoded-pattern stack already sitting in `encodedPatternSlot` (commit 1
     *                 callers pass the slot's current item, just like the historic flow).
     * @param pattern  the decoded details (used by some impls for caching / EAEP routing).
     * @param group    selected group, or null when [supportsGroupSelection] is false.
     */
    fun upload(
        encoded: ItemStack,
        pattern: IPatternDetails,
        group: PatternContainerGroup?,
        ctx: EncodingTerminalCtx
    ): CompletableFuture<UploadResult>
}

/**
 * Result returned by [UploadAction.upload].
 *
 * `Sent` vs `Confirmed`:
 *  - `Sent`: packet(s) dispatched; no server ack (the existing SERVER_BY_GROUP and EAEP paths
 *    are both optimistic). This is what most current actions return.
 *  - `Confirmed`: only used when an action does receive an explicit ack (commit 4 EAEP path may
 *    upgrade to this; commit 1 keeps `Sent`).
 */
sealed interface UploadResult {
    object Sent : UploadResult
    object Confirmed : UploadResult
    data class GroupNotFound(val reason: String) : UploadResult
    object NoSlot : UploadResult
    object Timeout : UploadResult
    data class Error(val reason: String) : UploadResult
}
