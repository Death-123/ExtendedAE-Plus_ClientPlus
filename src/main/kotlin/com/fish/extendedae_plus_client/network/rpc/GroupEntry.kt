package com.fish.extendedae_plus_client.network.rpc

import appeng.api.implementations.blockentities.PatternContainerGroup

/**
 * Per-group payload carried by [PrepareEncodeResultS2CPacket].
 *
 * Combines AE2's own [PatternContainerGroup] (which already knows how to write/read itself
 * to the network via `writeToPacket`/`readFromPacket`) with the server-observed empty-slot
 * count for that group at the moment of the RPC.
 *
 * Multiple [PatternContainerGroup] instances can compare equal (record equality on
 * `(icon, name, tooltip)`) — server-side aggregation should sum [emptySlots] across
 * duplicates before sending.
 */
data class GroupEntry(
    val group: PatternContainerGroup,
    val emptySlots: Int
)
