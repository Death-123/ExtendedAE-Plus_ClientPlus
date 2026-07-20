package com.fish.extendedae_plus_client.network

import com.fish.extendedae_plus_client.ExtendedAEPlusClient
import com.fish.extendedae_plus_client.network.rpc.PrepareEncodeC2SPacket
import com.fish.extendedae_plus_client.network.rpc.PrepareEncodeResultS2CPacket
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

object EAEPCNetworking {
    @JvmStatic
    fun registerPackets(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar(ExtendedAEPlusClient.MODID).optional()

        // RPC #2 upload (also used by the legacy SERVER_BY_GROUP path before commit 3 wiring).
        registrar.playToServer(
            UploadPatternByGroupPacket.TYPE,
            UploadPatternByGroupPacket.STREAM_CODEC,
            UploadPatternByGroupPacket::handleServer
        )

        // SERVER_BY_GROUP RPC #1 (commit 3): client → server prepare-encode probe + server reply.
        registrar.playToServer(
            PrepareEncodeC2SPacket.TYPE,
            PrepareEncodeC2SPacket.STREAM_CODEC,
            PrepareEncodeC2SPacket::handleServer
        )
        registrar.playToClient(
            PrepareEncodeResultS2CPacket.TYPE,
            PrepareEncodeResultS2CPacket.STREAM_CODEC,
            PrepareEncodeResultS2CPacket::handleClient
        )

        // Legacy "one-shot priming" packets — kept registered so older clients/servers stay
        // protocol-compatible. The client no longer sends RequestProvidersC2SPacket as of
        // commit 3; both can be removed in a future commit once compat is no longer a concern.
        registrar.playToServer(
            RequestProvidersC2SPacket.TYPE,
            RequestProvidersC2SPacket.STREAM_CODEC,
            RequestProvidersC2SPacket::handleServer
        )
        registrar.playToClient(
            SyncProvidersS2CPacket.TYPE,
            SyncProvidersS2CPacket.STREAM_CODEC,
            SyncProvidersS2CPacket::handleClient
        )
    }
}
