package com.fish.extendedae_plus_client.upload.flow

import appeng.api.crafting.IPatternDetails
import appeng.api.crafting.PatternDetailsHelper
import appeng.api.implementations.blockentities.PatternContainerGroup
import appeng.client.gui.me.items.PatternEncodingTermScreen
import appeng.menu.me.items.PatternEncodingTermMenu
import appeng.parts.encoding.EncodingMode
import com.fish.extendedae_plus_client.config.EAEPCConfig
import com.fish.extendedae_plus_client.config.enums.EncodingInterceptMode
import com.fish.extendedae_plus_client.impl.cache.CacheProvider
import com.fish.extendedae_plus_client.render.screen.ScreenProviderList
import com.fish.extendedae_plus_client.upload.AssemblerKeys
import com.fish.extendedae_plus_client.upload.EncodeFlow
import com.fish.extendedae_plus_client.upload.EncodeResult
import com.fish.extendedae_plus_client.upload.EncodingTerminalCtx
import com.fish.extendedae_plus_client.upload.InterceptReason
import com.fish.extendedae_plus_client.upload.action.UploadAction
import com.fish.extendedae_plus_client.upload.action.UploadResult
import com.fish.extendedae_plus_client.upload.internal.SlotFillObserver
import com.fish.extendedae_plus_client.upload.registry.GroupRegistry
import com.fish.extendedae_plus_client.upload.registry.PatternRegistry
import com.fish.extendedae_plus_client.util.UtilKeyBuilder
import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

/**
 * EncodeFlow used by `NONE` / `WHEN_OPEN` / `AUTO_OPEN` / `EAEP_BY_NAME`.
 *
 * Composed from three orthogonal collaborators (see plan §3.2):
 *  - [pReg]  drives the `EncodingInterceptMode` evaluation (SAME_PATTERN / SAME_PRIMARY_OUTPUT)
 *  - [gReg]  supplies the group set for `ScreenProviderList` and the auto-pick search
 *  - [action] is the actual upload strategy
 *
 * One-shot lifecycle per press (`onEncodeTriggered`):
 *
 *  1. Empty-providers guard. If [GroupRegistry.isEmpty], display the legacy
 *     "provider_list.empty_list" message, cancel `ci`, and return [EncodeResult.InvalidState].
 *  2. Pre-encode + decode for intercept evaluation. A null/invalid pattern aborts.
 *  3. Unmark any pattern currently in `encodedPatternSlot` (legacy bookkeeping — keeps
 *     `markedCount`/`primaryOutputOfProvider` consistent when the user re-encodes over a
 *     previous pending pattern).
 *  4. Intercept check (per the configured `EncodingInterceptMode`). On hit: notify, cancel
 *     `ci`, complete with [EncodeResult.Intercepted].
 *  5. Fast path. If the slot already contains an `ItemStack` equal to the freshly pre-encoded
 *     stack, skip the AE2 native round-trip: cancel `ci` and run `runMakePattern` directly.
 *  6. Slow path. Register a [SlotFillObserver] callback for `encodedPatternSlot` and let the
 *     mixin run AE2's native flow (no `ci.cancel()`). When the server's encode response fills
 *     the slot, `MixinEncodingTerminal.onSlotChange` notifies the observer which then runs
 *     `runMakePattern`.
 */
class ClientLocalEncodeFlow(
    private val pReg: PatternRegistry,
    private val gReg: GroupRegistry,
    private val action: UploadAction
) : EncodeFlow {

    override fun onEncodeTriggered(
        menu: PatternEncodingTermMenu,
        ctx: EncodingTerminalCtx
    ): CompletableFuture<EncodeResult> {
        // 1. empty providers guard (matches legacy MixinEncodingTerminal.onEncode)
        if (gReg.isEmpty()) {
            notifyEmptyProviders(ctx)
            ctx.cancelClientEncode.run()
            return CompletableFuture.completedFuture(EncodeResult.InvalidState("no providers"))
        }

        // 2. pre-encode candidate (does NOT consume a blank pattern)
        val encoded = ctx.preEncode.get()
            ?: return CompletableFuture.completedFuture(EncodeResult.InvalidState("encodePattern returned null"))
        val details = PatternDetailsHelper.decodePattern(encoded, ctx.player.level())
        if (details == null) {
            // legacy behaviour: cancel ci on decode failure to prevent broken AE2 flow
            ctx.cancelClientEncode.run()
            return CompletableFuture.completedFuture(EncodeResult.InvalidState("decodePattern returned null"))
        }

        // 3. unmark any existing slot pattern so the bookkeeping doesn't double-count
        val existing = ctx.encodedPatternSlot.item
        if (!existing.isEmpty) {
            val existingDetails = PatternDetailsHelper.decodePattern(existing, ctx.player.level())
            if (existingDetails != null) CacheProvider.unmarkPattern(existingDetails)
        }

        // 4. intercept check
        when (EAEPCConfig.encodingInterceptMode.get()) {
            EncodingInterceptMode.SAME_PATTERN -> {
                if (pReg.hasPattern(details)) {
                    notifyAlready(ctx)
                    ctx.cancelClientEncode.run()
                    return CompletableFuture.completedFuture(EncodeResult.Intercepted(InterceptReason.SAME_PATTERN))
                }
            }
            EncodingInterceptMode.SAME_PRIMARY_OUTPUT -> {
                val primary = details.outputs.firstOrNull()?.what()
                if (primary != null && pReg.canCraft(primary)) {
                    notifyAlready(ctx)
                    ctx.cancelClientEncode.run()
                    return CompletableFuture.completedFuture(EncodeResult.Intercepted(InterceptReason.SAME_PRIMARY_OUTPUT))
                }
            }
            else -> Unit
        }

        // 5. fast path — slot already contains the same encoded pattern
        val slotItem = ctx.encodedPatternSlot.item
        if (!slotItem.isEmpty && ItemStack.isSameItemSameComponents(slotItem, encoded)) {
            ctx.cancelClientEncode.run()
            return runMakePattern(slotItem, details, ctx)
        }

        // 6. slow path — let AE2 do the round-trip; resume in onSlotChange
        val future = CompletableFuture<EncodeResult>()
        SlotFillObserver.await(ctx.containerId, ctx.encodedPatternSlot.index) { filled ->
            val filledDetails = PatternDetailsHelper.decodePattern(filled, ctx.player.level())
            if (filledDetails == null) {
                future.complete(EncodeResult.InvalidState("decode of filled slot failed"))
                return@await
            }
            runMakePattern(filled, filledDetails, ctx).whenComplete { r, t ->
                if (t != null) future.complete(EncodeResult.Error(t.message ?: t.javaClass.simpleName))
                else future.complete(r)
            }
        }
        return future
    }

    /**
     * Group selection (auto-pick assembler for non-PROCESSING modes; ScreenProviderList for
     * PROCESSING) followed by the upload action. Mirrors legacy `eaep$makePattern`.
     */
    private fun runMakePattern(
        encoded: ItemStack,
        details: IPatternDetails,
        ctx: EncodingTerminalCtx
    ): CompletableFuture<EncodeResult> {
        if (gReg.isEmpty()) {
            return CompletableFuture.completedFuture(EncodeResult.InvalidState("no providers"))
        }
        if (!action.supportsGroupSelection()) {
            // commit 2: no Action returns false here, but kept for completeness
            return CompletableFuture.completedFuture(EncodeResult.Confirmed)
        }

        if (ctx.menu.mode != EncodingMode.PROCESSING) {
            return runAutoPickAssembler(encoded, details, ctx)
        }
        return runProcessingPickerUI(encoded, details, ctx)
    }

    /**
     * Non-PROCESSING modes (CRAFTING/SMITHING/STONECUTTING): pick the first known group whose
     * icon is in [AssemblerKeys] and which still has empty slots. Matches the historic
     * hardcoded list in `eaep$makePattern`.
     */
    private fun runAutoPickAssembler(
        encoded: ItemStack,
        details: IPatternDetails,
        ctx: EncodingTerminalCtx
    ): CompletableFuture<EncodeResult> {
        for (group in gReg.groups()) {
            val icon = group.icon() ?: continue
            if (icon.id.toString() !in AssemblerKeys.IDS) continue
            if (gReg.availableSlotsOf(group) > 0) {
                return uploadTo(encoded, details, group, ctx)
            }
        }
        return CompletableFuture.completedFuture(EncodeResult.GroupNotAvailable)
    }

    /**
     * PROCESSING mode: open `ScreenProviderList`. The future stays pending until the player
     * either picks a group (→ upload) or escapes the screen (→ Cancelled).
     *
     * If [EncodingTerminalCtx.autoSelectIfPossible] is set (i.e. press came from the
     * `eaep$autoEncoding` affordance) and `ScreenProviderList.tryAutoEncoding` succeeds, the
     * group is picked silently without ever showing the screen.
     */
    private fun runProcessingPickerUI(
        encoded: ItemStack,
        details: IPatternDetails,
        ctx: EncodingTerminalCtx
    ): CompletableFuture<EncodeResult> {
        val mc = Minecraft.getInstance()
        val screen = mc.screen
        if (screen !is PatternEncodingTermScreen<*>) {
            return CompletableFuture.completedFuture(
                EncodeResult.InvalidState("current screen is not encoding-term")
            )
        }
        val future = CompletableFuture<EncodeResult>()

        @Suppress("UNCHECKED_CAST")
        val typedScreen = screen as PatternEncodingTermScreen<PatternEncodingTermMenu>

        val list = ScreenProviderList(
            typedScreen,
            gReg.groups().toSet(),
            Consumer<PatternContainerGroup?> { selected ->
                if (selected == null) {
                    future.complete(EncodeResult.Cancelled)
                } else {
                    uploadTo(encoded, details, selected, ctx).whenComplete { r, t ->
                        if (t != null) future.complete(EncodeResult.Error(t.message ?: t.javaClass.simpleName))
                        else future.complete(r)
                    }
                }
            }
        )
        if (!ctx.autoSelectIfPossible || !list.tryAutoEncoding()) {
            typedScreen.switchToScreen(list)
        }
        return future
    }

    /**
     * Pre-marks the pattern (legacy `CacheProvider.markPattern`) and dispatches to the action,
     * mapping its [UploadResult] back into an [EncodeResult].
     *
     * Uses `thenApplyAsync(Minecraft.getInstance())` so the result mapping (and any UI
     * feedback inside it) always runs on the render thread, regardless of whether the
     * action's future was completed on it (commit 4 EaepByNameAction may complete on
     * `ForkJoinPool` via `orTimeout`).
     */
    private fun uploadTo(
        encoded: ItemStack,
        details: IPatternDetails,
        group: PatternContainerGroup,
        ctx: EncodingTerminalCtx
    ): CompletableFuture<EncodeResult> {
        CacheProvider.markPattern(details, group)
        return action.upload(encoded, details, group, ctx)
            .thenApplyAsync({ r -> mapResultWithFeedback(r, ctx) }, Minecraft.getInstance())
    }

    private fun mapResultWithFeedback(r: UploadResult, ctx: EncodingTerminalCtx): EncodeResult {
        // Surface terminal-state feedback to the player before mapping.
        // (Sent/Confirmed are silent; ClientLocalEncodeFlow's per-mode messaging is handled
        //  earlier in the press lifecycle, e.g. notifyAlready / notifyEmptyProviders.)
        when (r) {
            UploadResult.Timeout -> notifyTimeout(ctx)
            is UploadResult.GroupNotFound -> notifyGroupNotFound(ctx)
            else -> Unit
        }
        return when (r) {
            UploadResult.Sent, UploadResult.Confirmed -> EncodeResult.Confirmed
            is UploadResult.GroupNotFound -> EncodeResult.GroupNotAvailable
            UploadResult.NoSlot -> EncodeResult.GroupNotAvailable
            UploadResult.Timeout -> EncodeResult.Timeout
            is UploadResult.Error -> EncodeResult.Error(r.reason)
        }
    }

    private fun notifyTimeout(ctx: EncodingTerminalCtx) {
        ctx.player.displayClientMessage(
            UtilKeyBuilder.of(UtilKeyBuilder.message)
                .addStr("pattern")
                .addStr("upload_timeout")
                .build(),
            false
        )
    }

    private fun notifyGroupNotFound(ctx: EncodingTerminalCtx) {
        ctx.player.displayClientMessage(
            UtilKeyBuilder.of(UtilKeyBuilder.message)
                .addStr("provider_list")
                .addStr("group_not_found")
                .build(),
            false
        )
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
