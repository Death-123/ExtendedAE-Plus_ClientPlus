package com.fish.extendedae_plus_client.network.rpc

import appeng.api.implementations.blockentities.PatternContainerGroup
import appeng.api.stacks.AEKey
import com.fish.extendedae_plus_client.ExtendedAEPlusClient
import com.fish.extendedae_plus_client.upload.routing.PrepareEncodeResponseRouter
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * RPC #1 (response) of the SERVER_BY_GROUP two-phase upload flow.
 *
 * Sent by the server in reply to every [PrepareEncodeC2SPacket], regardless of whether the
 * request succeeded — the [reqId] is mandatory so the client's
 * [PrepareEncodeResponseRouter] can complete the matching `CompletableFuture`.
 *
 * Field semantics:
 *  - [result]                : pass/fail outcome of server-side validation
 *  - [groups]                : `(group, emptySlots)` pairs visible on the grid right now;
 *                              empty when [result] is not [PrepareResult.ALLOWED]
 *  - [networkPrimaryOutputs] : union of every existing pattern's primary output across the
 *                              grid; used by client `SAME_PRIMARY_OUTPUT` interception so it
 *                              becomes globally accurate (not just "things this client uploaded
 *                              this session"). Empty when [result] is not [PrepareResult.ALLOWED].
 *
 * Plan §6 commit 5 will add a `networkPatternFingerprints: List<Long>` field to make
 * `SAME_PATTERN` interception globally accurate as well.
 */
class PrepareEncodeResultS2CPacket(
    val reqId: Int,
    val result: PrepareResult,
    val groups: List<GroupEntry>,
    val networkPrimaryOutputs: List<AEKey>
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<PrepareEncodeResultS2CPacket> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<PrepareEncodeResultS2CPacket> =
            CustomPacketPayload.Type(ExtendedAEPlusClient.getLocation("prepare_encode_result"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, PrepareEncodeResultS2CPacket> =
            object : StreamCodec<RegistryFriendlyByteBuf, PrepareEncodeResultS2CPacket> {
                override fun decode(buf: RegistryFriendlyByteBuf): PrepareEncodeResultS2CPacket {
                    val reqId = ByteBufCodecs.VAR_INT.decode(buf)
                    val result = PrepareResult.entries[buf.readByte().toInt()]
                    val groupCount = buf.readVarInt()
                    val groups = ArrayList<GroupEntry>(groupCount)
                    repeat(groupCount) {
                        val group = PatternContainerGroup.readFromPacket(buf)
                        val empty = buf.readVarInt()
                        groups.add(GroupEntry(group, empty))
                    }
                    val outputCount = buf.readVarInt()
                    val outputs = ArrayList<AEKey>(outputCount)
                    repeat(outputCount) { outputs.add(AEKey.STREAM_CODEC.decode(buf)) }
                    return PrepareEncodeResultS2CPacket(reqId, result, groups, outputs)
                }

                override fun encode(buf: RegistryFriendlyByteBuf, packet: PrepareEncodeResultS2CPacket) {
                    ByteBufCodecs.VAR_INT.encode(buf, packet.reqId)
                    buf.writeByte(packet.result.ordinal)
                    buf.writeVarInt(packet.groups.size)
                    for (entry in packet.groups) {
                        entry.group.writeToPacket(buf)
                        buf.writeVarInt(entry.emptySlots)
                    }
                    buf.writeVarInt(packet.networkPrimaryOutputs.size)
                    for (key in packet.networkPrimaryOutputs) {
                        AEKey.STREAM_CODEC.encode(buf, key)
                    }
                }
            }

        @JvmStatic
        fun handleClient(packet: PrepareEncodeResultS2CPacket, context: IPayloadContext) {
            context.enqueueWork {
                PrepareEncodeResponseRouter.dispatch(packet)
            }
        }
    }
}
