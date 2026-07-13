package com.fish.extendedae_plus_client.upload

/**
 * Hardcoded set of "assembler-class" pattern-container icons that the auto-pick path
 * (`runAutoPickAssembler` in both [com.fish.extendedae_plus_client.upload.flow.ClientLocalEncodeFlow]
 * and [com.fish.extendedae_plus_client.upload.flow.ServerByGroupEncodeFlow]) treats as valid
 * targets when the encoding mode is non-PROCESSING (CRAFTING / SMITHING / STONECUTTING).
 *
 * Mirrors the legacy `MixinEncodingTerminal.eaep$makePattern` hardcoded list.
 *
 * TODO: replace with a `CraftingPatternAutoTarget` config option (plan §6 commit 3 TODOs).
 */
object AssemblerKeys {
    val IDS: Set<String> = setOf(
        "extendedae_plus:assembler_matrix_pattern_plus",
        "extendedae:assembler_matrix_pattern",
        "ae2:molecular_assembler",
        "extendedae:ex_molecular_assembler"
    )
}
