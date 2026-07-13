package com.fish.extendedae_plus_client.upload

import appeng.menu.me.items.PatternEncodingTermMenu
import appeng.menu.slot.RestrictedInputSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import java.util.function.Supplier

/**
 * Per-encode invocation context handed to an [EncodeFlow].
 *
 * The lambdas bridge to AE2 / mixin internals so that EncodeFlow implementations stay free
 * of mixin-specific call sites. They use Java functional interfaces ([Runnable] / [Supplier])
 * so the Java mixin can pass plain lambdas without Kotlin `Unit` boilerplate.
 *
 * Synchronous-vs-async contract:
 *  - [cancelClientEncode] MUST be invoked synchronously (before [EncodeFlow.onEncodeTriggered]
 *    returns) if the flow needs the mixin's `@Inject(at=HEAD)` to cancel `ci`. The mixin
 *    samples the resulting flag right after `onEncodeTriggered` returns.
 *  - The other lambdas may be invoked at any time on any thread; they internally re-marshal
 *    onto the render thread when needed (Minecraft network sends are render-thread-safe).
 *
 * @property preEncode               invokes private `PatternEncodingTermMenu.encodePattern()`
 *                                   without consuming a blank pattern. May return null.
 * @property triggerAE2Encode        invokes protected `AEBaseMenu.sendClientAction("encode")`
 *                                   directly — bypasses our own `MixinEncodingTerminal.onEncode`
 *                                   inject (otherwise we would infinite-loop).
 * @property cancelClientEncode      tells the mixin to cancel `ci` for the current press.
 *                                   MUST be called synchronously by the flow.
 * @property autoSelectIfPossible    true when the press came from `eaep$autoEncoding` (the
 *                                   "auto-pick the only candidate group" affordance, mirrors
 *                                   the legacy `eaep$autoEncoding != None` check).
 */
data class EncodingTerminalCtx(
    val player: Player,
    val menu: PatternEncodingTermMenu,
    val containerId: Int,
    val encodedPatternSlot: RestrictedInputSlot,
    val preEncode: Supplier<ItemStack?>,
    val triggerAE2Encode: Runnable,
    val cancelClientEncode: Runnable,
    val autoSelectIfPossible: Boolean
)
