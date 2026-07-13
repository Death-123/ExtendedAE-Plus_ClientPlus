package com.fish.extendedae_plus_client.upload.routing

import com.fish.extendedae_plus_client.network.rpc.PrepareEncodeResultS2CPacket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Asynchronous router for SERVER_BY_GROUP RPC #1.
 *
 * Each press allocates a fresh `reqId` via [nextReqId], registers a
 * `CompletableFuture<PrepareEncodeResultS2CPacket>` against it via [register], and then
 * sends [com.fish.extendedae_plus_client.network.rpc.PrepareEncodeC2SPacket] with that id.
 * The matching server reply (handled in `PrepareEncodeResultS2CPacket.handleClient`) calls
 * [dispatch] which completes the future.
 *
 * If no reply arrives within the configured timeout, the future fails with `TimeoutException`
 * — the caller in [com.fish.extendedae_plus_client.upload.flow.ServerByGroupEncodeFlow]
 * surfaces this to the user as `EncodeResult.Timeout`.
 *
 * RPC #2 (= AE2 ACTION_ENCODE + UploadPatternByGroupPacket) intentionally does NOT use this
 * router — it is fire-and-forget per plan §6 commit 3.
 */
object PrepareEncodeResponseRouter {

    /** Default RPC #1 timeout. Plan §7.2 mentions a future config option `serverByGroupRpcTimeoutSeconds`. */
    const val DEFAULT_TIMEOUT_SECONDS: Long = 5

    private val pending = ConcurrentHashMap<Int, CompletableFuture<PrepareEncodeResultS2CPacket>>()
    private val nextId = AtomicInteger(0)

    fun nextReqId(): Int = nextId.incrementAndGet()

    /**
     * Allocates a future for the given [reqId] with the requested timeout.
     *
     * The future is auto-removed from the pending map on completion (success or timeout).
     */
    fun register(reqId: Int, timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS): CompletableFuture<PrepareEncodeResultS2CPacket> {
        val future = CompletableFuture<PrepareEncodeResultS2CPacket>()
        pending[reqId] = future
        future.orTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .whenComplete { _, _ -> pending.remove(reqId) }
        return future
    }

    /**
     * Resolves the matching `reqId` future. No-op when no future is registered (e.g.
     * after a timeout or duplicate reply).
     */
    @JvmStatic
    fun dispatch(packet: PrepareEncodeResultS2CPacket) {
        pending.remove(packet.reqId)?.complete(packet)
    }

    /** Drops every pending future. Called on disconnect / world unload. */
    fun clear() {
        for ((_, future) in pending) future.cancel(false)
        pending.clear()
    }
}
