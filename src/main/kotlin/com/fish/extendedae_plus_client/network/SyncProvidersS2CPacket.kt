package com.fish.extendedae_plus_client.network

import appeng.api.implementations.blockentities.PatternContainerGroup
import appeng.api.stacks.AEItemKey
import com.fish.extendedae_plus_client.ExtendedAEPlusClient
import com.fish.extendedae_plus_client.impl.cache.CacheProvider
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * S2C chunked packet for syncing pattern provider groups.
 *
 * Large provider lists are split into multiple packets to respect
 * the maximum packet size limit (similar to AE2's incremental sync).
 *
 * Protocol:
 * - First chunk has [isFirst]=true → client clears stale cache
 * - Each chunk appends [providers] to the accumulator
 * - Last chunk has [isLast]=true → client finalises the sync
 *
 * A single-chunk response has both flags set to true.
 */
class SyncProvidersS2CPacket(
    val isFirst: Boolean,
    val isLast: Boolean,
    val providers: List<ProviderEntry>
) : CustomPacketPayload {

    data class ProviderEntry(
        val iconStack: ItemStack,
        val name: Component,
        val tooltip: List<Component>,
        val availableSlots: Int
    )

    override fun type(): CustomPacketPayload.Type<SyncProvidersS2CPacket> = TYPE

    companion object {
        const val CHUNK_SIZE = 32

        val TYPE: CustomPacketPayload.Type<SyncProvidersS2CPacket> =
            CustomPacketPayload.Type(ExtendedAEPlusClient.getLocation("sync_providers"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, SyncProvidersS2CPacket> =
            object : StreamCodec<RegistryFriendlyByteBuf, SyncProvidersS2CPacket> {
                override fun decode(buf: RegistryFriendlyByteBuf): SyncProvidersS2CPacket {
                    val flags = buf.readByte().toInt()
                    val isFirst = (flags and 1) != 0
                    val isLast = (flags and 2) != 0

                    val size = buf.readVarInt()
                    val entries = ArrayList<ProviderEntry>(size)
                    repeat(size) {
                        val iconStack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)
                        val name = ComponentSerialization.STREAM_CODEC.decode(buf)
                        val tooltipSize = buf.readVarInt()
                        val tooltip = ArrayList<Component>(tooltipSize)
                        repeat(tooltipSize) {
                            tooltip.add(ComponentSerialization.STREAM_CODEC.decode(buf))
                        }
                        val slots = buf.readVarInt()
                        entries.add(ProviderEntry(iconStack, name, tooltip, slots))
                    }
                    return SyncProvidersS2CPacket(isFirst, isLast, entries)
                }

                override fun encode(buf: RegistryFriendlyByteBuf, packet: SyncProvidersS2CPacket) {
                    var flags = 0
                    if (packet.isFirst) flags = flags or 1
                    if (packet.isLast) flags = flags or 2
                    buf.writeByte(flags)

                    buf.writeVarInt(packet.providers.size)
                    for (entry in packet.providers) {
                        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, entry.iconStack)
                        ComponentSerialization.STREAM_CODEC.encode(buf, entry.name)
                        buf.writeVarInt(entry.tooltip.size)
                        for (line in entry.tooltip) {
                            ComponentSerialization.STREAM_CODEC.encode(buf, line)
                        }
                        buf.writeVarInt(entry.availableSlots)
                    }
                }
            }

        @JvmStatic
        fun handleClient(packet: SyncProvidersS2CPacket, context: IPayloadContext) {
            context.enqueueWork {
                if (packet.isFirst) {
                    CacheProvider.beginSync()
                }

                for (entry in packet.providers) {
                    val icon = if (!entry.iconStack.isEmpty) AEItemKey.of(entry.iconStack) else null
                    val group = PatternContainerGroup(icon, entry.name, entry.tooltip)
                    CacheProvider.addNetworkProvider(group, entry.availableSlots)
                }

                if (packet.isLast) {
                    CacheProvider.endSync()
                }
            }
        }
    }
}
