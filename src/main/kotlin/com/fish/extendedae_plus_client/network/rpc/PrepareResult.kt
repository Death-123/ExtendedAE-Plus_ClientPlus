package com.fish.extendedae_plus_client.network.rpc

/**
 * Outcome of the server-side validation phase of [PrepareEncodeC2SPacket] handling.
 *
 * - [ALLOWED]: menu is a valid `PatternEncodingTermMenu`, the client's pre-encoded stack
 *   decodes cleanly, and the grid is reachable. The S2C reply carries `groups` and
 *   `networkPrimaryOutputs`.
 * - [INVALID_MENU]: player's container menu is not a `PatternEncodingTermMenu`, or the
 *   grid is not reachable (no node / no online controller). Empty `groups`.
 * - [INVALID_PATTERN]: client's pre-encoded `ItemStack` failed `PatternDetailsHelper.decodePattern`.
 *   Empty `groups`.
 */
enum class PrepareResult {
    ALLOWED,
    INVALID_MENU,
    INVALID_PATTERN
}
