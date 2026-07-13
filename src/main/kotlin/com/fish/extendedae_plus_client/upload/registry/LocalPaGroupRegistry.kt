package com.fish.extendedae_plus_client.upload.registry

import appeng.api.implementations.blockentities.PatternContainerGroup
import com.fish.extendedae_plus_client.impl.cache.CacheProvider

/**
 * Group index sourced from PA-terminal sync (LocalPa).
 *
 * Backing data lives in [CacheProvider]; this object is a thin facade.
 * Commit 2 will rewire the original write sites (PA-terminal mixins) to call into
 * a write API on this object directly, then [CacheProvider] becomes a vestigial facade.
 */
object LocalPaGroupRegistry : GroupRegistry {

    override fun groups(): Collection<PatternContainerGroup> = CacheProvider.getGroups()

    override fun availableSlotsOf(group: PatternContainerGroup): Int =
        CacheProvider.getAvailableSlots(group)

    override fun isEmpty(): Boolean = CacheProvider.isEmpty()
}
