package com.fish.extendedae_plus_client.impl

import com.electronwill.nightconfig.core.Config
import com.electronwill.nightconfig.core.file.FileConfig
import com.electronwill.nightconfig.core.file.FileConfig.builder
import com.electronwill.nightconfig.toml.TomlFormat.instance
import com.fish.extendedae_plus_client.config.EAEPCConfig
import com.fish.extendedae_plus_client.integration.ContextModLoaded
import com.fish.extendedae_plus_client.integration.recipeViewer.jei.HelperJeiRuntime
import com.fish.extendedae_plus_client.mixin.impl.bridge.BridgePlanToEncode
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import dev.emi.emi.api.EmiApi
import dev.emi.emi.api.recipe.EmiRecipe
import dev.emi.emi.api.stack.EmiIngredient
import dev.emi.emi.api.stack.EmiStack
import dev.emi.emi.jemi.JemiRecipe
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap
import mezz.jei.api.recipe.IRecipeCatalystLookup
import mezz.jei.api.recipe.RecipeType
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.PlainTextContents
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeHolder
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files
import kotlin.concurrent.Volatile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

object EAEEncodingHelper {
    private val pathConfig = FMLPaths.CONFIGDIR.get().resolve("extendedae_plus/stored_alias.toml")
    private var pathConfigOld = pathConfig.parent.resolve("stored_alias.json")
    private val config: FileConfig = builder(pathConfig, instance()).autosave().autoreload().build()

    init {
        this.config.load()

        if (this.pathConfigOld.exists())
            this.convertConfig()
    }

    fun closeConfig() = this.config.close()

    fun convertConfig() {
        val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
        val obj = gson.fromJson(Files.readString(this.pathConfigOld), JsonObject::class.java) ?: return

        obj.entrySet().stream()
            .filter { entry -> entry.value?.isJsonPrimitive ?: false && !entry.value?.asString.isNullOrBlank() }
            .forEach { entry -> this.config.set(entry.key, entry.value.asString) }
        this.config.save()
        this.pathConfigOld.deleteIfExists()
    }

    /**
     * 向配置中新增或更新别名映射，并刷新内存映射。
     * 
     * @param typeKey 最终搜索关键字（不含冒号），大小写不敏感
     * @param alias  别名
     * @return 是否写入成功
     */
    @Synchronized
    fun addOrUpdateAlias(typeKey: String?, alias: String?): Boolean {
        if (typeKey.isNullOrBlank() || alias.isNullOrBlank()) return false

        this.config.set<String>(listOf(typeKey.lowercase()), alias)
        return true
    }

    /** @return 删除别名的数量 */
    @Synchronized
    fun removeAliases(alias: String?): Int {
        if (alias.isNullOrBlank()) return 0

        val target = alias.trim()
        if (target.isBlank()) return 0

        val toRemove = this.config.entrySet().stream()
            .filter { entry -> entry.getValue<Any>().toString().equals(target, true) }
            .map(Config.Entry::getKey)
            .toList()
        toRemove.forEach { value -> this.config.remove(value) }
        return toRemove.size
    }

    fun findMapping(key: String?): String? {
        if (key.isNullOrBlank()) return null

        return this.config.get(listOf(key.lowercase()))
    }

    /** 收集到处理配方的关键词（按优先级排序） */
    @Volatile
    private var recipeKeywords: Int2ObjectSortedMap<Component> = Int2ObjectRBTreeMap()

    @Volatile
    private var keywords: MutableSet<String> = HashSet()

    fun clearRecipeKeywords() {
        recipeKeywords.clear()
    }

    fun getRecipeKeywords(): Int2ObjectSortedMap<Component> {
        return recipeKeywords
    }

    fun collectRecipeKeyword(name: String, priority: Int) {
        if (keywords.contains(name)) return
        keywords.add(name)
        val group = Component.literal(name)
        recipeKeywords[priority] = group
    }

    /** @param recipeBase (J)EmiRecipe或RecipeHolder
     */
    @JvmStatic
    fun tryCollectKeywords(recipeBase: Any?) {
        recipeKeywords.clear()
        keywords.clear()
        if (recipeBase == null) return
        val keys = HashMap<Int, String>()
        if (ContextModLoaded.emi.isLoaded) {//TODO emi Use id first
            var workstations: MutableList<EmiIngredient> = ArrayList()

            if (recipeBase is JemiRecipe<*>) {
                workstations = EmiApi.getRecipeManager().getWorkstations(recipeBase.recipeCategory)
                keys[0] = recipeBase.category.recipeType.uid.toString()
                keys[3] = recipeBase.category.title.string
                if (recipeBase.originalId != null) {
                    keys[9] = recipeBase.originalId.path.split("/")[0]
                }
            } else if (recipeBase is EmiRecipe) {
                workstations = EmiApi.getRecipeManager().getWorkstations(recipeBase.category)
                keys[0] = recipeBase.category.id.toString()
                keys[3] = recipeBase.category.name.string
                if (recipeBase.id != null) {
                    keys[9] = recipeBase.id!!.path.split("/")[0]
                }
            }

            if (!workstations.isEmpty()) {
                val workstationKeys = ArrayList<String>()
                workstations.reversed().forEach { ingredient: EmiIngredient ->
                    ingredient.emiStacks.reversed()
                        .forEach { stack: EmiStack ->
                            val name = stack.name
                            workstationKeys.add(name.string)

                            var key: String? = null
                            val contents = name.contents
                            if (contents is PlainTextContents) key = contents.text()
                            else if (contents is TranslatableContents) key = contents.key
                            if (key == null) return@forEach
                        }
                }

                workstationKeys.forEachIndexed { index, str -> collectRecipeKeyword(str, 10 + index) }
            }
        }
        val recipeManager = HelperJeiRuntime.get()?.recipeManager
        if (recipeManager != null) {
            val lookup: IRecipeCatalystLookup? = when (recipeBase) {
                is Recipe<*> -> recipeManager.createRecipeCatalystLookup(RecipeType.createFromVanilla(recipeBase.type))
                is RecipeHolder<*> -> recipeManager.createRecipeCatalystLookup(RecipeType.createFromVanilla(recipeBase.value.type))
                else -> null
            }
            if (lookup != null) {
                val workstationKeys = ArrayList<String>()
                lookup.itemStack.forEach { stack ->
                    val name = stack.hoverName
                    val key = name.string ?: return@forEach
                    workstationKeys.add(key)
                }
                workstationKeys.forEachIndexed { index, str -> collectRecipeKeyword(str, 10 + index) }
            }
        }


        if (recipeBase is RecipeHolder<*>) {
            val key = BuiltInRegistries.RECIPE_TYPE.getKey(recipeBase.value.type)
            if (key != null) keys[0] = key.toString()
            else keys[0] = recipeBase.value.type.toString()
            keys[1000] = recipeBase.id().path.split("/")[0]
        } else if (recipeBase is Recipe<*>) {
            val key = BuiltInRegistries.RECIPE_TYPE.getKey(recipeBase.type)
            if (key != null) keys[0] = key.toString()
            else keys[0] = recipeBase.type.toString()
        }

        keys.entries.stream()
            .filter { entry -> !entry.value.isBlank() }
            .sorted { a, b -> Comparator.naturalOrder<Int>().compare(a.key, b.key) }
            .forEach { entry ->
                collectRecipeKeyword(
                    entry.value,
                    entry.key
                )
            }
        keys[0]?.let { text -> findMapping(text)?.let { text -> recipeKeywords[1] = Component.literal(text) } }
    }

    @JvmStatic
    fun tiggerAutoEncoding() {
        val player: LocalPlayer = Minecraft.getInstance().player ?: return
        if (player.containerMenu !is BridgePlanToEncode) return
        if (!EAEPCConfig.autoEncodingTiggerMode.get().shouldTigger()) return
        (player.containerMenu as BridgePlanToEncode).`eaep$autoEncoding`()
    }

    @JvmStatic
    fun matches(keyWord: Component, nameKey: String, i18nKey: String, icon: String): Boolean {

        return nameMatches(keyWord.string, nameKey) ||
                i18nKeyMatches(keyWord.string, i18nKey) ||
                iconKeyMatches(keyWord.string, icon)

    }

    @JvmStatic
    private fun nameMatches(matchKey: String?, searchKey: String?): Boolean {
        if (matchKey.isNullOrBlank()) return false
        if (searchKey.isNullOrBlank()) return true

        var jechMatches = false
        if (ContextModLoaded.jech.isLoaded) {
            try {
                val methodContains = Class.forName("me.towdium.jecharacters.utils.Match")
                    .getMethod("contains", String::class.java, CharSequence::class.java)
                jechMatches = methodContains.invoke(
                    null, searchKey.lowercase(), matchKey.lowercase()
                ) as Boolean
            } catch (_: Throwable) {
            }
        }

        return jechMatches || searchKey.lowercase().contains(matchKey.lowercase())
    }

    @JvmStatic
    private fun i18nKeyMatches(matchKey: String?, searchKey: String?): Boolean {
        if (matchKey.isNullOrBlank() || searchKey.isNullOrEmpty()) return false
        return searchKey.lowercase().contains(matchKey.lowercase())
    }

    @JvmStatic
    private fun iconKeyMatches(matchKey: String?, searchKey: String?): Boolean {
        if (matchKey.isNullOrBlank() || searchKey.isNullOrEmpty()) return false
        return matchKey.equals(searchKey, ignoreCase = true)
    }
}
