package com.fish.extendedae_plus_client.upload.registry

import appeng.api.implementations.blockentities.PatternContainerGroup

/**
 * Group registry composed from a base [GroupRegistry] (e.g. [LocalPaGroupRegistry]) plus an
 * optional [EaepSlotsOverlay] that overrides the per-group empty-slot count when present.
 *
 * Used by `ClientLocalEncodeFlow` for `EAEP_BY_NAME` to combine the LocalPa group set with
 * the fresher EAEP-supplied slot counts.
 */
class CompositeGroupRegistry(
    private val base: GroupRegistry,
    private val overlay: EaepSlotsOverlay?
) : GroupRegistry {

    override fun groups(): Collection<PatternContainerGroup> = base.groups()

    override fun availableSlotsOf(group: PatternContainerGroup): Int {
        overlay?.availableSlotsOverride(group)?.let { return it }
        return base.availableSlotsOf(group)
    }

    override fun isEmpty(): Boolean = base.isEmpty()
}
