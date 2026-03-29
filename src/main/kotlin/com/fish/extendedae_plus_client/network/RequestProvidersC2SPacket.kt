package com.fish.extendedae_plus_client.network

import appeng.api.implementations.blockentities.PatternContainerGroup
import appeng.menu.me.items.PatternEncodingTermMenu
import com.fish.extendedae_plus_client.ExtendedAEPlusClient
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext

class RequestProvidersC2SPacket : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<RequestProvidersC2SPacket> = TYPE

    companion object {
        @JvmField
        val INSTANCE = RequestProvidersC2SPacket()

        val TYPE: CustomPacketPayload.Type<RequestProvidersC2SPacket> =
            CustomPacketPayload.Type(ExtendedAEPlusClient.getLocation("request_providers"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, RequestProvidersC2SPacket> =
            StreamCodec.unit(INSTANCE)

        @JvmStatic
        fun handleServer(packet: RequestProvidersC2SPacket, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                val menu = player.containerMenu
                if (menu !is PatternEncodingTermMenu) return@enqueueWork

                val grid = ServerGridHelper.getGridFromMenu(player) ?: return@enqueueWork

                val groupSlots = LinkedHashMap<PatternContainerGroup, Int>()
                ServerGridHelper.forEachPatternContainer(grid) { group, inv ->
                    var empty = 0
                    for (i in 0 until inv.slots) {
                        if (inv.getStackInSlot(i).isEmpty) empty++
                    }
                    groupSlots[group] = (groupSlots[group] ?: 0) + empty
                    false
                }

                val allEntries = groupSlots.map { (group, slots) ->
                    SyncProvidersS2CPacket.ProviderEntry(
                        iconStack = group.icon()?.toStack() ?: ItemStack.EMPTY,
                        name = group.name(),
                        tooltip = group.tooltip(),
                        availableSlots = slots
                    )
                }

                if (allEntries.isEmpty()) {
                    PacketDistributor.sendToPlayer(
                        player,
                        SyncProvidersS2CPacket(isFirst = true, isLast = true, providers = emptyList())
                    )
                    return@enqueueWork
                }

                val chunks = allEntries.chunked(SyncProvidersS2CPacket.CHUNK_SIZE)
                for ((index, chunk) in chunks.withIndex()) {
                    PacketDistributor.sendToPlayer(
                        player,
                        SyncProvidersS2CPacket(
                            isFirst = index == 0,
                            isLast = index == chunks.lastIndex,
                            providers = chunk
                        )
                    )
                }
            }
        }
    }
}
