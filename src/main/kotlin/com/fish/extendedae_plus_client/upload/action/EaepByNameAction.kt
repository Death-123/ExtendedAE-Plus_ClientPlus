package com.fish.extendedae_plus_client.upload.action

import appeng.api.crafting.IPatternDetails
import appeng.api.implementations.blockentities.PatternContainerGroup
import com.extendedae_plus.network.RequestProvidersListC2SPacket
import com.extendedae_plus.network.UploadEncodedPatternToProviderC2SPacket
import com.fish.extendedae_plus_client.impl.cache.CacheProvider
import com.fish.extendedae_plus_client.upload.EncodingTerminalCtx
import com.fish.extendedae_plus_client.upload.registry.EaepUploadAddressBook
import com.fish.extendedae_plus_client.upload.routing.EaepResponseRouter
import net.minecraft.world.item.ItemStack
import net.neoforged.fml.ModList
import net.neoforged.neoforge.network.PacketDistributor
import java.util.concurrent.CompletableFuture

/**
 * Action used by `autoUploadMode == EAEP_BY_NAME`.
 *
 * Commit 4: full async implementation.
 *
 *  - **AddressBook cache hit**: send `UploadEncodedPatternToProviderC2SPacket` immediately,
 *    update bookkeeping, return `Confirmed`. Avoids the extra `RequestProvidersListC2SPacket`
 *    round-trip — typical case after the player has used EAEP_BY_NAME at least once.
 *  - **AddressBook miss**: register a [EaepResponseRouter] pending entry keyed by [group],
 *    send `RequestProvidersListC2SPacket`, await the reply. On match: send upload + return
 *    `Confirmed`. On miss: return `GroupNotFound`. On 5 s timeout: return `Timeout`.
 *
 * All bookkeeping (`unmarkPattern` / `markPatternAlready` / `incMark`) happens *after* the
 * upload packet is dispatched — matching the legacy `HelperPatternMoving.eaepPacketHandler`
 * order, so that subsequent intercept checks see this pattern as "already uploaded".
 *
 * Group selection always runs (the `ClientLocalEncodeFlow` PROCESSING UI / non-PROCESSING
 * auto-pick) — `supportsGroupSelection()` returns `true`.
 */
object EaepByNameAction : UploadAction {
    override fun supportsGroupSelection(): Boolean = true

    override fun upload(
        encoded: ItemStack,
        pattern: IPatternDetails,
        group: PatternContainerGroup?,
        ctx: EncodingTerminalCtx
    ): CompletableFuture<UploadResult> {
        if (!ModList.get().isLoaded("extendedae_plus")) {
            return CompletableFuture.completedFuture(
                UploadResult.Error("extendedae_plus not loaded on server")
            )
        }
        if (group == null) {
            return CompletableFuture.completedFuture(
                UploadResult.GroupNotFound("group is null")
            )
        }

        // Fast path: cached provider id.
        val cachedId = EaepUploadAddressBook.findIdByGroup(group)
        if (cachedId != null) {
            performUpload(group, pattern, cachedId)
            return CompletableFuture.completedFuture(UploadResult.Confirmed)
        }

        // Slow path: round-trip via RequestProvidersListC2SPacket + EaepResponseRouter.
        val future = CompletableFuture<UploadResult>()
        EaepResponseRouter.register(group) { id ->
            if (id == null) {
                future.complete(UploadResult.GroupNotFound("name=${group.name().string}"))
            } else {
                performUpload(group, pattern, id)
                EaepUploadAddressBook.put(group, id)
                future.complete(UploadResult.Confirmed)
            }
        }
        PacketDistributor.sendToServer(RequestProvidersListC2SPacket.INSTANCE)
        // EaepResponseRouter has its own 5 s timeout that fires `callback(null)` —
        // so the future will always complete via the register callback path. No outer
        // .orTimeout needed here, but we add one as a safety net for any future code change.
        return future.orTimeout(EaepResponseRouter.DEFAULT_TIMEOUT_SECONDS + 1, java.util.concurrent.TimeUnit.SECONDS)
            .exceptionally { UploadResult.Timeout }
    }

    private fun performUpload(group: PatternContainerGroup, pattern: IPatternDetails, id: Long) {
        PacketDistributor.sendToServer(UploadEncodedPatternToProviderC2SPacket(id))
        CacheProvider.unmarkPattern(pattern)
        CacheProvider.markPatternAlready(pattern)
        CacheProvider.incMark(group)
    }
}
