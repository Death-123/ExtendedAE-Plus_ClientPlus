package com.fish.extendedae_plus_client.upload

import com.fish.extendedae_plus_client.config.EAEPCConfig
import com.fish.extendedae_plus_client.config.enums.AutoUploadMode
import com.fish.extendedae_plus_client.upload.action.EaepByNameAction
import com.fish.extendedae_plus_client.upload.action.NoOpAction
import com.fish.extendedae_plus_client.upload.action.QuickMoveAction
import com.fish.extendedae_plus_client.upload.action.QuickMoveThenAutoOpenAction
import com.fish.extendedae_plus_client.upload.flow.ClientLocalEncodeFlow
import com.fish.extendedae_plus_client.upload.flow.ServerByGroupEncodeFlow
import com.fish.extendedae_plus_client.upload.registry.CompositeGroupRegistry
import com.fish.extendedae_plus_client.upload.registry.EaepSlotsOverlay
import com.fish.extendedae_plus_client.upload.registry.LocalPaGroupRegistry
import com.fish.extendedae_plus_client.upload.registry.LocalPushPatternRegistry

/**
 * Mode → [EncodeFlow] mapping table.
 *
 * Single entry point for the mixin (commit 2 onward): `ModeBindings.currentEncodeFlow()` is
 * the only call site that branches on [AutoUploadMode]. Everywhere else stays mode-agnostic.
 *
 * All flows are stateless singletons; no per-tick / per-screen allocation cost.
 */
object ModeBindings {

    private val NONE_FLOW: EncodeFlow = ClientLocalEncodeFlow(
        pReg = LocalPushPatternRegistry,
        gReg = LocalPaGroupRegistry,
        action = NoOpAction
    )

    private val WHEN_OPEN_FLOW: EncodeFlow = ClientLocalEncodeFlow(
        pReg = LocalPushPatternRegistry,
        gReg = LocalPaGroupRegistry,
        action = QuickMoveAction
    )

    private val AUTO_OPEN_FLOW: EncodeFlow = ClientLocalEncodeFlow(
        pReg = LocalPushPatternRegistry,
        gReg = LocalPaGroupRegistry,
        action = QuickMoveThenAutoOpenAction
    )

    private val SERVER_BY_GROUP_FLOW: EncodeFlow = ServerByGroupEncodeFlow()

    private val EAEP_BY_NAME_FLOW: EncodeFlow = ClientLocalEncodeFlow(
        pReg = LocalPushPatternRegistry,
        gReg = CompositeGroupRegistry(LocalPaGroupRegistry, EaepSlotsOverlay),
        action = EaepByNameAction
    )

    @JvmStatic
    fun currentEncodeFlow(): EncodeFlow = when (EAEPCConfig.autoUploadMode.get()) {
        AutoUploadMode.NONE -> NONE_FLOW
        AutoUploadMode.WHEN_OPEN -> WHEN_OPEN_FLOW
        AutoUploadMode.AUTO_OPEN -> AUTO_OPEN_FLOW
        AutoUploadMode.SERVER_BY_GROUP -> SERVER_BY_GROUP_FLOW
        AutoUploadMode.EAEP_BY_NAME -> EAEP_BY_NAME_FLOW
        null -> NONE_FLOW
    }
}
