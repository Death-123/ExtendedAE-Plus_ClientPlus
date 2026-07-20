package com.fish.extendedae_plus_client.upload.flow

import appeng.api.crafting.IPatternDetails
import appeng.api.crafting.PatternDetailsHelper
import appeng.api.implementations.blockentities.PatternContainerGroup
import appeng.client.gui.me.items.PatternEncodingTermScreen
import appeng.menu.me.items.PatternEncodingTermMenu
import appeng.parts.encoding.EncodingMode
import com.fish.extendedae_plus_client.config.EAEPCConfig
import com.fish.extendedae_plus_client.config.enums.EncodingInterceptMode
import com.fish.extendedae_plus_client.network.UploadPatternByGroupPacket
import com.fish.extendedae_plus_client.network.rpc.GroupEntry
import com.fish.extendedae_plus_client.network.rpc.PrepareEncodeC2SPacket
import com.fish.extendedae_plus_client.network.rpc.PrepareEncodeResultS2CPacket
import com.fish.extendedae_plus_client.network.rpc.PrepareResult
import com.fish.extendedae_plus_client.render.screen.ScreenProviderList
import com.fish.extendedae_plus_client.upload.AssemblerKeys
import com.fish.extendedae_plus_client.upload.EncodeFlow
import com.fish.extendedae_plus_client.upload.EncodeResult
import com.fish.extendedae_plus_client.upload.EncodingTerminalCtx
import com.fish.extendedae_plus_client.upload.InterceptReason
import com.fish.extendedae_plus_client.upload.registry.LocalPushPatternRegistry
import com.fish.extendedae_plus_client.upload.routing.PrepareEncodeResponseRouter
import com.fish.extendedae_plus_client.util.UtilKeyBuilder
import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.PacketDistributor
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

/**
 * EncodeFlow used by `autoUploadMode == SERVER_BY_GROUP` (commit 3+).
 *
 * Two-phase RPC per plan §5.3 — solves the legacy "add machine after opening terminal
 * requires reopen" bug by querying the server live on every press instead of relying on a
 * one-shot client cache.
 *
 * Lifecycle (single press):
 *
 *  1. [EncodingTerminalCtx.cancelClientEncode] is invoked synchronously — the AE2 native
 *     `encode()` is ALWAYS suppressed in this flow (it would consume a blank pattern before
 *     the server even knows whether the upload should happen).
 *  2. Client pre-encodes the pattern stack via [EncodingTerminalCtx.preEncode] — this does
 *     NOT consume blanks.
 *  3. RPC #1: [PrepareEncodeC2SPacket] → server walks the grid, returns
 *     [PrepareEncodeResultS2CPacket] with `(group, emptySlots)` list and the union of every
 *     existing pattern's primary output. 5 s timeout via [PrepareEncodeResponseRouter].
 *  4. Client-side intercept evaluation:
 *     - SAME_PATTERN          → [LocalPushPatternRegistry.hasPattern] (commit 5 will add
 *                               server-supplied `networkPatternFingerprints`)
 *     - SAME_PRIMARY_OUTPUT   → response's `networkPrimaryOutputs` ∪ [LocalPushPatternRegistry.canCraft]
 *  5. Group selection:
 *     - PROCESSING → pop [ScreenProviderList] populated from the RPC response (NOT from
 *       [com.fish.extendedae_plus_client.upload.registry.LocalPaGroupRegistry] — the response is the source of truth here)
 *     - non-PROCESSING → auto-pick first group whose icon is in [AssemblerKeys] with empty slots
 *  6. RPC #2 (optimistic, no ack): [EncodingTerminalCtx.triggerAE2Encode] (= AE2's
 *     `sendClientAction("encode")`) immediately followed by [UploadPatternByGroupPacket].
 *     TCP order guarantees the server processes encode before upload.
 *  7. [LocalPushPatternRegistry.add] writes back the pattern so subsequent presses can
 *     intercept it locally without another RPC #1.
 *
 * The future is completed:
 *  - synchronously (intercept hit / pre-encode failed) on the render thread, OR
 *  - asynchronously after the RPC #1 response (or its 5 s timeout)
 *
 * Threading: response handling marshals back to the render thread via
 * `Minecraft.getInstance().execute` because UI work and packet sends both expect it.
 */
class ServerByGroupEncodeFlow : EncodeFlow {

    override fun onEncodeTriggered(
        menu: PatternEncodingTermMenu,
        ctx: EncodingTerminalCtx
    ): CompletableFuture<EncodeResult> {
        // 1. always cancel AE2's native encode — we drive the round-trip ourselves
        ctx.cancelClientEncode.run()

        // 2. client pre-encode (does NOT consume a blank pattern)
        val preEncodedStack = ctx.preEncode.get()
            ?: return CompletableFuture.completedFuture(EncodeResult.InvalidState("encodePattern returned null"))
        val preDetails = PatternDetailsHelper.decodePattern(preEncodedStack, ctx.player.level())
            ?: return CompletableFuture.completedFuture(EncodeResult.InvalidState("decodePattern returned null"))

        // 3. fire RPC #1
        val reqId = PrepareEncodeResponseRouter.nextReqId()
        val responseFuture = PrepareEncodeResponseRouter.register(reqId)
        PacketDistributor.sendToServer(PrepareEncodeC2SPacket(reqId, preEncodedStack))

        val resultFuture = CompletableFuture<EncodeResult>()
        responseFuture.whenComplete { resp, throwable ->
            // marshal back to the render thread for UI work + packet sends
            Minecraft.getInstance().execute {
                handleResponse(menu, preEncodedStack, preDetails, resp, throwable, ctx, resultFuture)
            }
        }
        return resultFuture
    }

    private fun handleResponse(
        menu: PatternEncodingTermMenu,
        preEncodedStack: ItemStack,
        preDetails: IPatternDetails,
        resp: PrepareEncodeResultS2CPacket?,
        throwable: Throwable?,
        ctx: EncodingTerminalCtx,
        future: CompletableFuture<EncodeResult>
    ) {
        if (throwable != null || resp == null) {
            future.complete(EncodeResult.Timeout)
            return
        }
        when (resp.result) {
            PrepareResult.INVALID_MENU -> {
                future.complete(EncodeResult.InvalidState("server reported invalid menu"))
                return
            }
            PrepareResult.INVALID_PATTERN -> {
                future.complete(EncodeResult.InvalidState("server reported invalid pre-encoded pattern"))
                return
            }
            PrepareResult.ALLOWED -> Unit
        }

        // 4. client-side intercept evaluation using the server-supplied data
        val interceptReason = evaluateIntercept(preDetails, resp)
        if (interceptReason != null) {
            notifyAlready(ctx)
            future.complete(EncodeResult.Intercepted(interceptReason))
            return
        }

        if (resp.groups.isEmpty()) {
            notifyEmptyProviders(ctx)
            future.complete(EncodeResult.InvalidState("no providers"))
            return
        }

        // 5+6. group selection → RPC #2
        // TODO: menu state comparison — if currentMenuState differs from when RPC #1 was sent,
        // re-issue RPC #1 with a fresh preEncodedStack (plan §6 commit 3 TODO).
        runMakePattern(menu, preEncodedStack, preDetails, resp, ctx, future)
    }

    private fun evaluateIntercept(
        preDetails: IPatternDetails,
        resp: PrepareEncodeResultS2CPacket
    ): InterceptReason? = when (EAEPCConfig.encodingInterceptMode.get()) {
        EncodingInterceptMode.SAME_PATTERN -> {
            // commit 5 will add: || resp.networkPatternFingerprints.contains(fingerprintOf(preEncodedStack))
            if (LocalPushPatternRegistry.hasPattern(preDetails)) InterceptReason.SAME_PATTERN else null
        }
        EncodingInterceptMode.SAME_PRIMARY_OUTPUT -> {
            val primary = preDetails.outputs.firstOrNull()?.what()
            when {
                primary == null -> null
                resp.networkPrimaryOutputs.contains(primary) -> InterceptReason.SAME_PRIMARY_OUTPUT
                LocalPushPatternRegistry.canCraft(primary) -> InterceptReason.SAME_PRIMARY_OUTPUT
                else -> null
            }
        }
        else -> null
    }

    private fun runMakePattern(
        menu: PatternEncodingTermMenu,
        preEncodedStack: ItemStack,
        preDetails: IPatternDetails,
        resp: PrepareEncodeResultS2CPacket,
        ctx: EncodingTerminalCtx,
        future: CompletableFuture<EncodeResult>
    ) {
        if (menu.mode != EncodingMode.PROCESSING) {
            runAutoPickAssembler(preEncodedStack, preDetails, resp, ctx, future)
            return
        }
        runProcessingPickerUI(preEncodedStack, preDetails, resp, ctx, future)
    }

    private fun runAutoPickAssembler(
        preEncodedStack: ItemStack,
        preDetails: IPatternDetails,
        resp: PrepareEncodeResultS2CPacket,
        ctx: EncodingTerminalCtx,
        future: CompletableFuture<EncodeResult>
    ) {
        for (entry in resp.groups) {
            val icon = entry.group.icon() ?: continue
            if (icon.id.toString() !in AssemblerKeys.IDS) continue
            if (entry.emptySlots <= 0) continue
            performUpload(preEncodedStack, preDetails, entry, ctx, future)
            return
        }
        future.complete(EncodeResult.GroupNotAvailable)
    }

    private fun runProcessingPickerUI(
        preEncodedStack: ItemStack,
        preDetails: IPatternDetails,
        resp: PrepareEncodeResultS2CPacket,
        ctx: EncodingTerminalCtx,
        future: CompletableFuture<EncodeResult>
    ) {
        val mc = Minecraft.getInstance()
        val screen = mc.screen
        if (screen !is PatternEncodingTermScreen<*>) {
            future.complete(EncodeResult.InvalidState("current screen is not encoding-term"))
            return
        }

        val groupSet: Set<PatternContainerGroup> = resp.groups.mapTo(LinkedHashSet()) { it.group }
        val byGroup: Map<PatternContainerGroup, GroupEntry> = resp.groups.associateBy { it.group }

        @Suppress("UNCHECKED_CAST")
        val typedScreen = screen as PatternEncodingTermScreen<PatternEncodingTermMenu>

        val list = ScreenProviderList(
            typedScreen,
            groupSet,
            Consumer<PatternContainerGroup?> { selected ->
                if (selected == null) {
                    future.complete(EncodeResult.Cancelled)
                } else {
                    val entry = byGroup[selected]
                    if (entry == null) {
                        future.complete(EncodeResult.GroupNotAvailable)
                    } else {
                        performUpload(preEncodedStack, preDetails, entry, ctx, future)
                    }
                }
            }
        )
        if (!ctx.autoSelectIfPossible || !list.tryAutoEncoding()) {
            typedScreen.switchToScreen(list)
        }
    }

    /**
     * RPC #2: AE2 ACTION_ENCODE + UploadPatternByGroupPacket, fire-and-forget.
     *
     * TCP order guarantees the server processes the encode action first (which fills
     * `encodedPatternSlot`), so when the upload packet arrives the server-side handler can
     * read the freshly encoded stack out of the slot.
     *
     * TODO: RPC #2 ack feedback — current optimistic flow has no failure signal beyond the
     * player observing that the slot didn't clear. A future protocol extension could add an
     * ack S2C packet (plan §6 commit 3 TODO).
     */
    private fun performUpload(
        preEncodedStack: ItemStack,
        preDetails: IPatternDetails,
        entry: GroupEntry,
        ctx: EncodingTerminalCtx,
        future: CompletableFuture<EncodeResult>
    ) {
        ctx.triggerAE2Encode.run()
        PacketDistributor.sendToServer(
            UploadPatternByGroupPacket(entry.group.icon()?.id, entry.group.name())
        )
        // Optimistic write-back so future presses can intercept locally without another RPC #1.
        LocalPushPatternRegistry.add(preDetails)
        future.complete(EncodeResult.Confirmed)
    }

    private fun notifyAlready(ctx: EncodingTerminalCtx) {
        ctx.player.displayClientMessage(
            UtilKeyBuilder.of(UtilKeyBuilder.message)
                .addStr("pattern")
                .addStr("already")
                .build(),
            false
        )
    }

    private fun notifyEmptyProviders(ctx: EncodingTerminalCtx) {
        ctx.player.displayClientMessage(
            UtilKeyBuilder.of(UtilKeyBuilder.message)
                .addStr("provider_list")
                .addStr("empty_list")
                .build(),
            false
        )
    }
}
