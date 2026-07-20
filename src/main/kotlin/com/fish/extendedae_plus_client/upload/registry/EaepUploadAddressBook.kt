package com.fish.extendedae_plus_client.upload.registry

import appeng.api.implementations.blockentities.PatternContainerGroup
import com.fish.extendedae_plus_client.util.ComponentLocaleConverter

/**
 * Cache of `groupName → EAEP serverId` mappings, populated by
 * [com.fish.extendedae_plus_client.upload.routing.EaepResponseRouter.dispatch] on every
 * incoming `ProvidersListS2CPacket`.
 *
 * Used by [com.fish.extendedae_plus_client.upload.action.EaepByNameAction] to skip an extra
 * `RequestProvidersListC2SPacket` round-trip when the server-id for the chosen group is
 * already known — the upload packet can be sent immediately.
 */
object EaepUploadAddressBook {

    private val byNormalizedName: MutableMap<String, Long> = HashMap()

    /**
     * Returns the EAEP server-id for [group] if known, else null (caller should fall back to
     * sending a fresh `RequestProvidersListC2SPacket`).
     *
     * Tries the localized name first, then the en_us name — matches how EAEP itself matches
     * provider names on the server side.
     */
    fun findIdByGroup(group: PatternContainerGroup): Long? {
        if (byNormalizedName.isEmpty()) return null
        val local = ComponentLocaleConverter.normalizeForCompare(group.name().string)
        byNormalizedName[local]?.let { return it }
        val enUs = ComponentLocaleConverter.normalizeForCompare(
            ComponentLocaleConverter.toLocaleString(group.name(), "en_us")
        )
        return byNormalizedName[enUs]
    }

    /** Single-entry write API (used after a successful resolved upload to keep the cache hot). */
    fun put(group: PatternContainerGroup, id: Long) {
        val key = ComponentLocaleConverter.normalizeForCompare(group.name().string)
        byNormalizedName[key] = id
    }

    /**
     * Bulk write API called by
     * [com.fish.extendedae_plus_client.upload.routing.EaepResponseRouter] for every incoming
     * `ProvidersListS2CPacket`.
     */
    fun update(namesToIds: List<Pair<String, Long>>) {
        for ((name, id) in namesToIds) {
            byNormalizedName[ComponentLocaleConverter.normalizeForCompare(name)] = id
        }
    }

    fun clear() {
        byNormalizedName.clear()
    }
}
