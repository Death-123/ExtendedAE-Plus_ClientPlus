package com.fish.extendedae_plus_client.upload.internal

import net.minecraft.world.item.ItemStack

/**
 * Hands the next "encodedPatternSlot just got filled" event off from the mixin
 * (`MixinEncodingTerminal.onSlotChange`) to whoever is awaiting it (currently
 * `ClientLocalEncodeFlow.onEncodeTriggered` slow path).
 *
 * Replaces the legacy `eaep$flagPatternSelection` boolean — instead of a single bit
 * shared across the whole menu, we register a per-`(containerId, slotIndex)` callback so
 * that future flows could await multiple slots concurrently if needed.
 *
 * Per-key at-most-one pending callback: a subsequent registration replaces the previous.
 *
 * Threading: registered/notified on the render thread.
 *
 * `@JvmStatic` is used on every method so the Java mixin can call them as plain statics.
 */
object SlotFillObserver {

    private val pending = mutableMapOf<Long, (ItemStack) -> Unit>()

    private fun key(containerId: Int, slotIdx: Int): Long =
        (containerId.toLong() shl 32) or (slotIdx.toLong() and 0xFFFFFFFFL)

    /**
     * Registers [callback] to fire the next time the slot identified by
     * [containerId]/[slotIdx] is observed to be non-empty by `MixinEncodingTerminal.onSlotChange`.
     *
     * If a callback was already pending for this key, it is silently dropped.
     */
    @JvmStatic
    fun await(containerId: Int, slotIdx: Int, callback: (ItemStack) -> Unit) {
        pending[key(containerId, slotIdx)] = callback
    }

    /** Called by the mixin on every slot-change for `encodedPatternSlot`. No-op when no pending. */
    @JvmStatic
    fun notifyFilled(containerId: Int, slotIdx: Int, stack: ItemStack) {
        if (stack.isEmpty) return
        pending.remove(key(containerId, slotIdx))?.invoke(stack)
    }

    /** Drops any pending callback for the given slot (e.g. when the screen closes). */
    @JvmStatic
    fun cancel(containerId: Int, slotIdx: Int) {
        pending.remove(key(containerId, slotIdx))
    }

    @JvmStatic
    fun clear() = pending.clear()
}
