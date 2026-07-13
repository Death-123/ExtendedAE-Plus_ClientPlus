package com.fish.extendedae_plus_client.upload.registry

import appeng.api.crafting.IPatternDetails
import appeng.api.stacks.AEKey
import com.fish.extendedae_plus_client.impl.cache.CacheProvider

/**
 * Pattern index sourced from PA-terminal sync (push).
 *
 * Shared singleton across all `autoUploadMode` values so mode switching does not lose
 * accumulated knowledge — it is fed by the PA-terminal screen mixins regardless of mode.
 *
 * Commit 1: thin facade over [CacheProvider] (delegate-only); we deliberately do NOT
 * migrate storage yet so that no behavioural change occurs from this commit alone.
 * Commit 2 will rewire the original write sites to call into this registry directly.
 */
object LocalPushPatternRegistry : PatternRegistry {

    override fun hasPattern(p: IPatternDetails): Boolean = CacheProvider.hasPattern(p)

    /**
     * Per design (plan §3.2): "canCraft is judged by primary output only".
     *
     * Implemented via [primaryOutputOf] + [CacheProvider.hasPrimaryOutputKey] (added in commit 1
     * solely as a read-only overload — does not change behaviour).
     */
    override fun canCraft(output: AEKey): Boolean = CacheProvider.hasPrimaryOutputKey(output)

    /**
     * Write hook used by [com.fish.extendedae_plus_client.upload.flow.ClientLocalEncodeFlow]
     * after a successful upload, mirroring the historic `CacheProvider.markPatternAlready` call.
     *
     * Commit 1: delegates to [CacheProvider.markPatternAlready] (existing behaviour).
     */
    fun add(p: IPatternDetails) {
        CacheProvider.markPatternAlready(p)
    }
}
