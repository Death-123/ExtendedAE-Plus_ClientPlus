package com.fish.extendedae_plus_client.network

import com.fish.extendedae_plus_client.ExtendedAEPlusClient
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

object EAEPCNetworking {
    @JvmStatic
    fun registerPackets(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar(ExtendedAEPlusClient.MODID).optional()

        registrar.playToServer(
            UploadPatternByGroupPacket.TYPE,
            UploadPatternByGroupPacket.STREAM_CODEC,
            UploadPatternByGroupPacket::handleServer
        )
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
