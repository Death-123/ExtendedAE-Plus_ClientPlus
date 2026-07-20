package com.fish.extendedae_plus_client.upload.registry

import appeng.api.implementations.blockentities.PatternContainerGroup

/**
 * Read-only view of pattern-provider groups visible to the client, used by
 * `ClientLocalEncodeFlow` to populate `ScreenProviderList`.
 *
 * Writes are NOT exposed (see [PatternRegistry] note).
 *
 * `ServerByGroupEncodeFlow` does NOT consume this interface — it queries the server live
 * via RPC #1 each press.
 */
interface GroupRegistry {
    fun groups(): Collection<PatternContainerGroup>
    fun availableSlotsOf(group: PatternContainerGroup): Int
    fun isEmpty(): Boolean
}
