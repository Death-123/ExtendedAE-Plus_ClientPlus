package com.fish.extendedae_plus_client.upload.routing

import appeng.api.implementations.blockentities.PatternContainerGroup
import com.extendedae_plus.network.ProvidersListS2CPacket
import com.fish.extendedae_plus_client.mixin.impl.helper.HelperProvidersListS2CPacket
import com.fish.extendedae_plus_client.upload.registry.EaepSlotsOverlay
import com.fish.extendedae_plus_client.upload.registry.EaepUploadAddressBook
import com.fish.extendedae_plus_client.util.ComponentLocaleConverter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * Routes EAEP's `ProvidersListS2CPacket` to:
 *
 * 1. [EaepSlotsOverlay]      — refreshed unconditionally on every packet so the group-selection
 *                              UI's empty-slot counts stay fresh even when no upload is pending.
 * 2. [EaepUploadAddressBook] — refreshed unconditionally so subsequent uploads can skip the
 *                              `RequestProvidersListC2SPacket` round-trip.
 * 3. Pending [register] callbacks — resolved (with id or null) when the matching group's
 *                                   name appears (or doesn't) in the packet.
 *
 * Commit 4 contract:
 *
 *  - Multiple uploads to different groups can be in flight at once; the router maintains a
 *    queue of [Pending] entries and resolves all of them on each packet (a single packet
 *    carries every visible provider, so a "this name is not in the packet" outcome is final
 *    for that round — no need to wait for another packet).
 *  - Each [register] call gets a 5 s timeout. On timeout the callback is invoked with `null`
 *    so the caller can surface a `Timeout` result.
 *  - Replaces the commit 2 design which only tracked a single optimistic `Sent` future and
 *    relied on `HelperPatternMoving` static fields for the matching state.
 */
object EaepResponseRouter {

    const val DEFAULT_TIMEOUT_SECONDS: Long = 5

    private data class Pending(
        val group: PatternContainerGroup,
        val resolve: (Long?) -> Unit,
        val future: CompletableFuture<Unit>
    )

    private val queue = CopyOnWriteArrayList<Pending>()

    /**
     * Registers a one-shot [callback] waiting for the next `ProvidersListS2CPacket`.
     *
     * If the packet carries a matching name for [group], [callback] receives the
     * corresponding id. Otherwise it receives `null` (group not visible on the server side
     * → caller should treat as `GroupNotFound`). On 5 s timeout, [callback] also receives
     * `null`.
     *
     * The callback is invoked at most once.
     */
    fun register(group: PatternContainerGroup, callback: (id: Long?) -> Unit) {
        val future = CompletableFuture<Unit>()
        val entry = Pending(group, callback, future)
        queue.add(entry)

        future.orTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .whenComplete { _, throwable ->
                // remove returns false if a packet already resolved this entry
                if (queue.remove(entry) && throwable != null) {
                    entry.resolve(null)
                }
            }
    }

    /** Whether any pending upload is waiting for a packet. */
    @JvmStatic
    fun hasPending(): Boolean = queue.isNotEmpty()

    /**
     * Called by `ProvidersListS2CPacketMixin` for every incoming packet.
     *
     * Returns `true` when at least one pending entry was resolved by this packet (so the
     * mixin should `ci.cancel()` to suppress EAEP's own screen handler from also reacting).
     * Returns `false` when there were no pending entries — the packet's contents are still
     * forwarded to [EaepSlotsOverlay] / [EaepUploadAddressBook], but EAEP's vanilla handler
     * is allowed to run as well (it powers EAEP's own UIs).
     */
    @JvmStatic
    fun dispatch(packet: ProvidersListS2CPacket): Boolean {
        val helper = packet as HelperProvidersListS2CPacket
        val ids = helper.ids
        val names = helper.names
        val emptySlots = helper.emptySlots

        // 1. unconditional cache refresh (drives the group-selection UI's slot counts and
        //    future-presses' AddressBook hits)
        val nameStrings = names.map { it.string }
        EaepSlotsOverlay.update(nameStrings.zip(emptySlots))
        EaepUploadAddressBook.update(nameStrings.zip(ids))

        // 2. resolve pending callbacks
        if (queue.isEmpty()) return false
        val snapshot = ArrayList(queue)
        queue.clear()
        for (entry in snapshot) {
            entry.future.complete(Unit)
            val id = findIdInPacket(entry.group, helper)
            entry.resolve(id)
        }
        return true
    }

    private fun findIdInPacket(group: PatternContainerGroup, packet: HelperProvidersListS2CPacket): Long? {
        val localName = ComponentLocaleConverter.normalizeForCompare(group.name().string)
        val enUsName = ComponentLocaleConverter.normalizeForCompare(
            ComponentLocaleConverter.toLocaleString(group.name(), "en_us")
        )
        for (i in packet.ids.indices) {
            val serverNameComp = packet.names[i]
            val serverName = ComponentLocaleConverter.normalizeForCompare(serverNameComp.string)
            val serverNameEnUs = ComponentLocaleConverter.normalizeForCompare(
                ComponentLocaleConverter.toLocaleString(serverNameComp, "en_us")
            )
            val matches =
                (enUsName.isNotEmpty() && (serverNameEnUs == enUsName || serverName == enUsName)) ||
                (localName.isNotEmpty() && (serverName == localName || serverNameEnUs == localName))
            if (matches) return packet.ids[i]
        }
        return null
    }

    /** Drops every pending entry. Called on disconnect / world unload. */
    fun clear() {
        for (entry in queue) entry.future.cancel(false)
        queue.clear()
    }
}
