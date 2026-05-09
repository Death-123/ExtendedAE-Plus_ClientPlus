package com.fish.extendedae_plus_client.config

import com.fish.extendedae_plus_client.ExtendedAEPlusClient
import com.fish.extendedae_plus_client.util.UtilKeyBuilder
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.jarjar.nio.util.Lazy
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.client.settings.IKeyConflictContext
import net.neoforged.neoforge.client.settings.KeyConflictContext
import org.lwjgl.glfw.GLFW

@EventBusSubscriber(modid = ExtendedAEPlusClient.MODID, value = [Dist.CLIENT])
object EAEPCKeyMapping {
    private val mappings: MutableSet<Lazy<KeyMapping>> = HashSet<Lazy<KeyMapping>>()

    private val CATEGORY: String = UtilKeyBuilder.of(UtilKeyBuilder.keyCategory).buildRaw()

    @JvmField
    val fillToSearchField: Lazy<KeyMapping> = this.register(
        "fill_to_search_field",
        KeyConflictContext.GUI,
        GLFW.GLFW_KEY_F
    )

    private fun register(
        name: String,
        keyConflictContext: IKeyConflictContext,
        inputType: InputConstants.Type,
        keyCode: Int,
        category: String
    ): Lazy<KeyMapping> {
        val mapping = Lazy.of<KeyMapping> {
            KeyMapping(
                UtilKeyBuilder.of(UtilKeyBuilder.key)
                    .addStr(name)
                    .buildRaw(),
                keyConflictContext,
                inputType,
                keyCode,
                category
            )
        }
        mappings.add(mapping)
        return mapping
    }

    private fun register(
        name: String, keyConflictContext: IKeyConflictContext, keyCode: Int
    ): Lazy<KeyMapping> {
        return register(name, keyConflictContext, InputConstants.Type.KEYSYM, keyCode, CATEGORY)
    }

    @SubscribeEvent
    private fun onKeyMappingReg(event: RegisterKeyMappingsEvent) {
        this.mappings.stream()
            .map(Lazy<KeyMapping>::get)
            .forEach(event::register)
    }
}
