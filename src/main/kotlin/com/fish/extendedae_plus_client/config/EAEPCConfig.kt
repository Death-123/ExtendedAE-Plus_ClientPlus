package com.fish.extendedae_plus_client.config

import com.fish.extendedae_plus_client.config.enums.AutoUploadMode
import com.fish.extendedae_plus_client.config.enums.EncodingInterceptMode
import com.fish.extendedae_plus_client.config.enums.ModeEncodingTransfer
import com.fish.extendedae_plus_client.config.enums.TiggerMode
import net.neoforged.fml.ModContainer
import net.neoforged.fml.config.ModConfig
import net.neoforged.neoforge.client.gui.ConfigurationScreen
import net.neoforged.neoforge.client.gui.IConfigScreenFactory
import net.neoforged.neoforge.common.ModConfigSpec
import java.util.function.Consumer

object EAEPCConfig {
    val SPEC: ModConfigSpec

    @JvmField
    val autoPlateRepeat: ModConfigSpec.IntValue
    @JvmField
    val autoTransferDelay: ModConfigSpec.IntValue
    @JvmField
    val modeEncodingTransfer: ModConfigSpec.EnumValue<ModeEncodingTransfer>

    @JvmField
    val autoUploadMode: ModConfigSpec.EnumValue<AutoUploadMode>
    @JvmField
    val encodingTiggerMode: ModConfigSpec.EnumValue<TiggerMode>
    @JvmField
    val itemEditingTiggerMode: ModConfigSpec.EnumValue<TiggerMode>
    @JvmField
    val autoEncodingTiggerMode: ModConfigSpec.EnumValue<TiggerMode>
    @JvmField
    val encodingInterceptMode: ModConfigSpec.EnumValue<EncodingInterceptMode>
    @JvmField
    val autoCraftingFill: ModConfigSpec.BooleanValue

    init {
        val builder = ModConfigSpec.Builder()

        autoPlateRepeat = builder.defineInRange("autoPlateRepeat", 1, 1, 64)
        autoTransferDelay = builder.defineInRange("autoTransferDelay", 10, 1, 40)
        modeEncodingTransfer = builder.defineEnum("modeEncodingTransfer", ModeEncodingTransfer.MERGE_ADJACENCY)
        autoUploadMode = builder.defineEnum("autoUploadMode", AutoUploadMode.AUTO_OPEN)
        encodingTiggerMode = builder.defineEnum("encodingTiggerMode", TiggerMode.ON_NOT_SHIFT)
        itemEditingTiggerMode = builder.defineEnum("itemEditingTiggerMode", TiggerMode.ON_CTRL)
        autoEncodingTiggerMode = builder.defineEnum("autoEncodingTiggerMode", TiggerMode.ON_CTRL)
        encodingInterceptMode = builder.defineEnum("encodingInterceptMode", EncodingInterceptMode.SAME_PATTERN)
        autoCraftingFill = builder.define("autoCraftingFill", true)
        SPEC = builder.build()
    }

    fun init(modContainer: ModContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, SPEC, "extendedae_plus/client.toml")
        modContainer.registerExtensionPoint(
            IConfigScreenFactory::class.java,
            IConfigScreenFactory(::ConfigurationScreen)
        )
    }

    private fun ModConfigSpec.Builder.section(section: String, modifier: Consumer<ModConfigSpec.Builder>): ModConfigSpec.Builder {
        this.push(section)
        modifier.accept(this)
        this.pop()
        return this
    }
}
