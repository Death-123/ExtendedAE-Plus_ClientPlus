package com.fish.extendedae_plus_client.lang

import appeng.core.definitions.AEItems
import com.fish.extendedae_plus_client.ExtendedAEPlusClient
import com.fish.extendedae_plus_client.util.UtilKeyBuilder
import net.minecraft.data.PackOutput
import net.neoforged.neoforge.common.data.LanguageProvider

class LangZH(output: PackOutput) : LanguageProvider(output, ExtendedAEPlusClient.MODID, "zh_cn") {
    override fun addTranslations() {
        UtilKeyBuilder.BuilderDataGen.bindTranslator("zh_cn", this::add)

        UtilKeyBuilder.ofDataGen(UtilKeyBuilder.keywordGroup)
            .addStr("workstations")
            .buildInto("§6关键词组 {§r配方: §l%s§6}")

        UtilKeyBuilder.ofDataGen(UtilKeyBuilder.tooltip)
            .item(AEItems.PROCESSING_PATTERN)
            .branch("selected_provider", "§7选择的供应器: §r%s")
            .branch("auto_completable", "§7自动完成")
        UtilKeyBuilder.ofDataGen(UtilKeyBuilder.tooltip)
            .item(AEItems.CERTUS_QUARTZ_KNIFE)
            .addStr("block_name_coping")
            .branch("failed", "复制 方块/部件名{%s} 失败")
            .branch("success", "已复制 方块/部件名{%s} 到剪贴板")

        UtilKeyBuilder.ofDataGen(UtilKeyBuilder.message)
            .addStr("provider_list")
            .branch("remap_success", "[EAEP] 重载映射成功")
            .branch("remap_failed", "[EAEP] 重载映射失败")
        UtilKeyBuilder.ofDataGen(UtilKeyBuilder.message)
            .addStr("provider_list")
            .addStr("add_alias")
            .branch("empty_query", "[EAEP] 查询列表为空, 请先输入待映射配方关键词")
            .branch("empty_alias", "[EAEP] 别名为空, 请先输入待映射别名")
            .branch("success", "[EAEP] 别名映射{%s → %s} 添加成功")
            .branch("failed", "[EAEP] 别名映射{%s} 添加失败")
        UtilKeyBuilder.ofDataGen(UtilKeyBuilder.message)
            .addStr("provider_list")
            .addStr("delete_alias")
            .branch("empty_alias", "[EAEP] 别名为空, 请先输入待删除别名")
            .branch("success", "[EAEP] 别名映射{%s × %s} 删除成功")
            .branch("failed", "[EAEP] 别名映射{%s} 删除失败")
        UtilKeyBuilder.ofDataGen(UtilKeyBuilder.message)
            .addStr("provider_list")
            .addStr("empty_list")
            .buildInto("[EAEP] 本地样板供应器缓存为空, 请先打开样板管理终端以创建缓存")

        UtilKeyBuilder.ofDataGen(UtilKeyBuilder.screen)
            .addStr("provider_list")
            .branch("alias", "输入待映射别名")
            .buildInto("选择样板供应器")
        UtilKeyBuilder.ofDataGen(UtilKeyBuilder.screen)
            .addStr("stacks_reproperties")
            .buildInto("属性设置")
        UtilKeyBuilder.ofDataGen(UtilKeyBuilder.screen)
            .addStr("auto_completion")
            .branch("off", "自动完成: 关")
            .branch("on", "自动完成: 开")
            .branch("description", "以此物品作为最终产物的任务会在所有合成物被推送且打开终端后自动完成")
            .branch("only_completion", "该样板只接受自动完成或手动取消, 无法被检测返回完成")

        UtilKeyBuilder.ofDataGen(UtilKeyBuilder.screenTooltip)
            .addStr("provider_list")
            .addStr("candidate_keywords")
            .buildInto("§f§l候选关键词")
        UtilKeyBuilder.ofDataGen(UtilKeyBuilder.screenTooltip)
            .addStr("recipe_alias")
            .branch("reload", "重载映射")
            .branch("add", "添加映射")
            .branch("remove", "移除映射")
            .buildInto("别名操作")

        UtilKeyBuilder.ofDataGen(UtilKeyBuilder.keyCategory)
            .buildInto("ExtendedAE Plus [ClientPlus]")
        UtilKeyBuilder.ofDataGen(UtilKeyBuilder.key)
            .addStr("fill_to_search_field")
            .buildInto("搜索鼠标指向的物品")

        UtilKeyBuilder.ofDataGen(UtilKeyBuilder.config)
            .addStr("autoPlateRepeat")
            .branch("tooltip", "石英切割刀自动为编有有自定义名称物品的样板制作每个名称压印模板的数量")
            .buildInto("自动切割压印模板数")
        UtilKeyBuilder.ofDataGen(UtilKeyBuilder.config)
            .addStr("modeEncodingTransfer")
            .branch("tooltip", """
                        控制[Shift]进行配方转移时终端对物品的自动合并
                        NONE - 不进行改动
                        MERGE_ADJACENCY - 只合并相邻的物品
                        INDEPENDENCE - 完全不合并
                        """)
            .buildInto("配方转移合并模式")
        UtilKeyBuilder.ofDataGen(UtilKeyBuilder.message)
                            .addStr("pattern")
                            .addStr("already")
                            .buildInto("[EAEP] 样板已存在")
        UtilKeyBuilder.ofDataGen(UtilKeyBuilder.message)
                            .addStr("pattern")
                            .addStr("duplicate_output")
                            .buildInto("[EAEP] 警告: 主产物 %s (%s) 存在 %s 个不同的样板")
        UtilKeyBuilder.ofDataGen(UtilKeyBuilder.message)
                            .addStr("pattern")
                            .addStr("no_duplicates")
                            .buildInto("[EAEP] 未发现主产物重复的样板")

        UtilKeyBuilder.ofDataGen(UtilKeyBuilder.screenTooltip)
            .addStr("check_duplicates")
            .branch("description", "检查所有样板供应器中主产物相同但样板不同的情况")
            .buildInto("检查重复")

        UtilKeyBuilder.BuilderDataGen.destroy("zh_cn")
    }
}
