package com.fish.extendedae_plus_client.upload.registry

import appeng.api.implementations.blockentities.PatternContainerGroup
import com.fish.extendedae_plus_client.util.ComponentLocaleConverter

/**
 * Overlay providing fresher empty-slot counts for EAEP_BY_NAME mode, sourced from
 * `ProvidersListS2CPacket` (the EAEP server packet).
 *
 * Composed with [LocalPaGroupRegistry] via [CompositeGroupRegistry] in `ClientLocalEncodeFlow`
 * when `autoUploadMode == EAEP_BY_NAME`: when a group's name matches an entry here, the
 * overlay's count wins over the (potentially stale) PA-terminal-sourced count.
 *
 * Populated by [com.fish.extendedae_plus_client.upload.routing.EaepResponseRouter.dispatch]
 * on every incoming `ProvidersListS2CPacket` (commit 4).
 */
object EaepSlotsOverlay {

    private val byNormalizedName: MutableMap<String, Int> = HashMap()

    /**
     * Returns the latest empty-slot count for [group], or `null` when no overlay entry is
     * known (caller should fall back to the underlying [LocalPaGroupRegistry]).
     *
     * Matching is name-based via [ComponentLocaleConverter.normalizeForCompare] — the same
     * normalization used by EAEP server-side name matching.
     */
    fun availableSlotsOverride(group: PatternContainerGroup): Int? {
        if (byNormalizedName.isEmpty()) return null
        val key = ComponentLocaleConverter.normalizeForCompare(group.name().string)
        return byNormalizedName[key]
    }

    /**
     * Wholesale replacement write API. Called by
     * [com.fish.extendedae_plus_client.upload.routing.EaepResponseRouter] every time the
     * server sends a fresh provider list.
     */
    fun update(namesToSlots: List<Pair<String, Int>>) {
        byNormalizedName.clear()
        for ((name, slots) in namesToSlots) {
            byNormalizedName[ComponentLocaleConverter.normalizeForCompare(name)] = slots
        }
    }

    fun clear() {
        byNormalizedName.clear()
    }
}
