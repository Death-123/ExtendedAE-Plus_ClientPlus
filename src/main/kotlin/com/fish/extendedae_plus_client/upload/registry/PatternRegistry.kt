package com.fish.extendedae_plus_client.upload.registry

import appeng.api.crafting.IPatternDetails
import appeng.api.stacks.AEKey

/**
 * Read-only index of patterns the client knows about, used by `ClientLocalEncodeFlow`
 * to evaluate the configured `EncodingInterceptMode`.
 *
 * Writes are NOT exposed: each implementation owns its private write API and is fed
 * by its own data source (PA-terminal sync, EAEP packet, etc).
 *
 * `ServerByGroupEncodeFlow` does NOT consume this interface — it queries the server live.
 */
interface PatternRegistry {
    /** True if the registry already holds an entry whose decoded pattern equals [p]. */
    fun hasPattern(p: IPatternDetails): Boolean

    /**
     * True if any known pattern in the registry produces [output] as its primary output.
     *
     * "primaryOutput" = `IPatternDetails.outputs[0].what()` (matches existing
     * `CacheProvider.hasPrimaryOutput` semantics).
     */
    fun canCraft(output: AEKey): Boolean
}
