package com.fish.extendedae_plus_client.network.rpc

import appeng.api.crafting.PatternDetailsHelper
import appeng.api.implementations.blockentities.PatternContainerGroup
import appeng.api.stacks.AEKey
import appeng.menu.me.items.PatternEncodingTermMenu
import com.fish.extendedae_plus_client.ExtendedAEPlusClient
import com.fish.extendedae_plus_client.network.ServerGridHelper
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * RPC #1 (request) of the SERVER_BY_GROUP two-phase upload flow.
 *
 * Sent by the client when the player presses the encode button while
 * `autoUploadMode == SERVER_BY_GROUP`. Carries the client-side pre-encoded `ItemStack`
 * (computed via the private `PatternEncodingTermMenu.encodePattern()` accessor — it does
 * NOT consume a blank pattern, see [com.fish.extendedae_plus_client.upload.EncodingTerminalCtx.preEncode]).
 *
 * Server response: [PrepareEncodeResultS2CPacket].
 *
 * Server-side responsibilities (per plan §5.3):
 *  1. Verify menu type → [PrepareResult.INVALID_MENU] otherwise.
 *  2. Verify the pre-encoded stack decodes → [PrepareResult.INVALID_PATTERN] otherwise.
 *  3. Walk the grid, collect `(group, emptySlots)` pairs and the union of every existing
 *     pattern's primary output ([appeng.api.crafting.IPatternDetails.outputs] index 0).
 *  4. Reply with [PrepareResult.ALLOWED] + the collected data.
 *
 * The reply is *always* sent — even on validation failure — so the client's
 * [PrepareEncodeResponseRouter] can resolve the matching `reqId` future without waiting
 * for its 5 s timeout.
 */
class PrepareEncodeC2SPacket(
    val reqId: Int,
    val preEncodedStack: ItemStack
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<PrepareEncodeC2SPacket> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<PrepareEncodeC2SPacket> =
            CustomPacketPayload.Type(ExtendedAEPlusClient.getLocation("prepare_encode"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, PrepareEncodeC2SPacket> =
            object : StreamCodec<RegistryFriendlyByteBuf, PrepareEncodeC2SPacket> {
                override fun decode(buf: RegistryFriendlyByteBuf): PrepareEncodeC2SPacket {
                    val reqId = ByteBufCodecs.VAR_INT.decode(buf)
                    val stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)
                    return PrepareEncodeC2SPacket(reqId, stack)
                }

                override fun encode(buf: RegistryFriendlyByteBuf, packet: PrepareEncodeC2SPacket) {
                    ByteBufCodecs.VAR_INT.encode(buf, packet.reqId)
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, packet.preEncodedStack)
                }
            }

        @JvmStatic
        fun handleServer(packet: PrepareEncodeC2SPacket, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                val menu = player.containerMenu
                if (menu !is PatternEncodingTermMenu) {
                    sendFailure(player, packet.reqId, PrepareResult.INVALID_MENU)
                    return@enqueueWork
                }

                val details = PatternDetailsHelper.decodePattern(packet.preEncodedStack, player.level())
                if (details == null) {
                    sendFailure(player, packet.reqId, PrepareResult.INVALID_PATTERN)
                    return@enqueueWork
                }

                val grid = ServerGridHelper.getGridFromMenu(player)
                if (grid == null) {
                    sendFailure(player, packet.reqId, PrepareResult.INVALID_MENU)
                    return@enqueueWork
                }

                val groupSlots = LinkedHashMap<PatternContainerGroup, Int>()
                val networkPrimaryOutputs = LinkedHashSet<AEKey>()
                ServerGridHelper.forEachPatternContainer(grid) { group, inv ->
                    var empty = 0
                    for (i in 0 until inv.slots) {
                        val stack = inv.getStackInSlot(i)
                        if (stack.isEmpty) {
                            empty++
                        } else {
                            val patternDetails = PatternDetailsHelper.decodePattern(stack, player.level())
                            patternDetails?.outputs?.firstOrNull()?.what()?.let { networkPrimaryOutputs.add(it) }
                        }
                    }
                    groupSlots[group] = (groupSlots[group] ?: 0) + empty
                    false
                }

                val groups = groupSlots.map { (group, slots) -> GroupEntry(group, slots) }
                PacketDistributor.sendToPlayer(
                    player,
                    PrepareEncodeResultS2CPacket(
                        reqId = packet.reqId,
                        result = PrepareResult.ALLOWED,
                        groups = groups,
                        networkPrimaryOutputs = networkPrimaryOutputs.toList()
                    )
                )
            }
        }

        private fun sendFailure(player: ServerPlayer, reqId: Int, result: PrepareResult) {
            PacketDistributor.sendToPlayer(
                player,
                PrepareEncodeResultS2CPacket(
                    reqId = reqId,
                    result = result,
                    groups = emptyList(),
                    networkPrimaryOutputs = emptyList()
                )
            )
        }
    }
}
