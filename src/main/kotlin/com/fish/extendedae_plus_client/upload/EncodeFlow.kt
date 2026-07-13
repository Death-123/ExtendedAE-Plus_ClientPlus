package com.fish.extendedae_plus_client.upload

import appeng.menu.me.items.PatternEncodingTermMenu
import java.util.concurrent.CompletableFuture

/**
 * Top-level abstraction for "player pressed the encode button".
 *
 * Replaces the historic mode-specific `if-else` ladder in `MixinEncodingTerminal.eaep$makePatternAuto`.
 *
 * The mixin is the single call site:
 *
 * ```java
 * if (flow.interceptsClientEncode()) ci.cancel();
 * flow.onEncodeTriggered(menu, ctx).thenAccept(result -> showFeedback(result));
 * ```
 *
 * Implementations:
 *  - [com.fish.extendedae_plus_client.upload.flow.ClientLocalEncodeFlow] — `NONE`/`WHEN_OPEN`/`AUTO_OPEN`/`EAEP_BY_NAME`
 *  - [com.fish.extendedae_plus_client.upload.flow.ServerByGroupEncodeFlow] — `SERVER_BY_GROUP`
 *
 * Threading: implementations may complete the future on any thread; callers MUST
 * marshal UI side-effects back to the render thread (see [EncodeResult] consumers).
 */
interface EncodeFlow {

    /**
     * Drives the full encode → intercept → group-select → upload pipeline for a single press.
     *
     * Whether AE2's native `encode()` should be cancelled is communicated by the
     * implementation calling [EncodingTerminalCtx.cancelClientEncode] *synchronously* before
     * this method returns; the mixin samples the resulting flag right after the call.
     *
     * The returned future completes (success/failure/cancel) when the flow ends. It does NOT
     * necessarily wait for server confirmation of the upload — see individual implementations
     * for their notion of "done".
     */
    fun onEncodeTriggered(
        menu: PatternEncodingTermMenu,
        ctx: EncodingTerminalCtx
    ): CompletableFuture<EncodeResult>
}

/**
 * Outcome of a single [EncodeFlow.onEncodeTriggered] invocation.
 *
 * Modeled as a sealed hierarchy so callers can branch exhaustively when feeding feedback to the UI.
 */
sealed interface EncodeResult {
    /** Upload was issued (and locally observed as confirmed where applicable). */
    object Confirmed : EncodeResult

    /** Encode was rejected by the configured intercept rules. */
    data class Intercepted(val reason: InterceptReason) : EncodeResult

    /** Player cancelled the group-selection UI. */
    object Cancelled : EncodeResult

    /** Pre-conditions failed (no providers known, pattern decode failed, etc). */
    data class InvalidState(val msg: String) : EncodeResult

    /** Crafting-mode auto-pick found no candidate group. */
    object GroupNotAvailable : EncodeResult

    /** Async RPC did not respond in time. */
    object Timeout : EncodeResult

    /** Generic failure carrier (network errors, unexpected exceptions). */
    data class Error(val msg: String) : EncodeResult
}

/** Reason a press was intercepted; mirrors `EncodingInterceptMode`. */
enum class InterceptReason { SAME_PATTERN, SAME_PRIMARY_OUTPUT }
