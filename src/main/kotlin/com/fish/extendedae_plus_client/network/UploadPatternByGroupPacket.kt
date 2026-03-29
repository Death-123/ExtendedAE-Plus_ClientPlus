package com.fish.extendedae_plus_client.network

import appeng.api.crafting.PatternDetailsHelper
import appeng.api.implementations.blockentities.PatternContainerGroup
import appeng.menu.me.items.PatternEncodingTermMenu
import com.fish.extendedae_plus_client.ExtendedAEPlusClient
import com.fish.extendedae_plus_client.mixin.impl.helper.HelperPatternEncodingTermMenu
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.handling.IPayloadContext

class UploadPatternByGroupPacket(
    val groupIconId: ResourceLocation?,
    val groupName: Component
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<UploadPatternByGroupPacket> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<UploadPatternByGroupPacket> =
            CustomPacketPayload.Type(ExtendedAEPlusClient.getLocation("upload_pattern_by_group"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, UploadPatternByGroupPacket> =
            object : StreamCodec<RegistryFriendlyByteBuf, UploadPatternByGroupPacket> {
                override fun decode(buf: RegistryFriendlyByteBuf): UploadPatternByGroupPacket {
                    val hasIcon = buf.readBoolean()
                    val iconId = if (hasIcon) buf.readResourceLocation() else null
                    val name = ComponentSerialization.STREAM_CODEC.decode(buf)
                    return UploadPatternByGroupPacket(iconId, name)
                }

                override fun encode(buf: RegistryFriendlyByteBuf, packet: UploadPatternByGroupPacket) {
                    buf.writeBoolean(packet.groupIconId != null)
                    if (packet.groupIconId != null) buf.writeResourceLocation(packet.groupIconId)
                    ComponentSerialization.STREAM_CODEC.encode(buf, packet.groupName)
                }
            }

        @JvmStatic
        fun handleServer(packet: UploadPatternByGroupPacket, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                val menu = player.containerMenu
                if (menu !is PatternEncodingTermMenu) return@enqueueWork

                val encodedSlot = (menu as HelperPatternEncodingTermMenu).`eaep$getEncodedPatternSlot`()
                val encodedPattern = encodedSlot.item
                if (encodedPattern.isEmpty || !PatternDetailsHelper.isEncodedPattern(encodedPattern)) return@enqueueWork

                val grid = ServerGridHelper.getGridFromMenu(player) ?: return@enqueueWork

                ServerGridHelper.forEachPatternContainer(grid) { group, inv ->
                    if (!matchesGroup(group, packet)) return@forEachPatternContainer false

                    for (i in 0 until inv.slots) {
                        if (!inv.getStackInSlot(i).isEmpty) continue
                        val remaining = inv.insertItem(i, encodedPattern.copy(), false)
                        if (remaining.isEmpty) {
                            encodedSlot.set(ItemStack.EMPTY)
                            menu.broadcastChanges()
                            return@forEachPatternContainer true
                        }
                    }
                    false
                }
            }
        }

        private fun matchesGroup(
            serverGroup: PatternContainerGroup,
            packet: UploadPatternByGroupPacket
        ): Boolean {
            val serverIconId = serverGroup.icon()?.id
            if (serverIconId != packet.groupIconId) return false
            return serverGroup.name() == packet.groupName
        }
    }
}
