package com.fish.extendedae_plus_client.integration

import net.neoforged.fml.ModList

@Suppress("EnumEntryName")
enum class ContextModLoaded(private val modID: String) {
    emi("emi"),
    jei("jei"),
    jech("jecharacters"),
    curios("curios"),
    ae2wtlib("ae2wtlib"),
    advancedAE("advanced_ae"),
    appliedFlux("appflux"),
    mekanism("mekanism"),
    appliedMekanistics("appmek"),
    gtceuModern("gtceu"),
    ftbLibrary("ftblibrary"),
    ae2helpers("ae2helpers"),
    ;

    var isLoaded: Boolean
        private set

    init {
        this.isLoaded = false
    }

    companion object {
        private var initialized = false

        fun init() {
            check(!initialized) { "Contexts has already been initialized" }
            initialized = true

            for (context in entries) {
                context.isLoaded = ModList.get().isLoaded(context.modID)
            }
        }
    }
}
