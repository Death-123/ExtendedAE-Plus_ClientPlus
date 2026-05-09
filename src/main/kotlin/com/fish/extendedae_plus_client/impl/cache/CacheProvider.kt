package com.fish.extendedae_plus_client.impl.cache

import appeng.api.crafting.IPatternDetails
import appeng.api.implementations.blockentities.PatternContainerGroup
import appeng.api.stacks.AEKey
import appeng.client.gui.me.patternaccess.PatternContainerRecord
import net.minecraft.world.item.ItemStack
import java.util.*

object CacheProvider {
    @JvmStatic
    private var providerList: MutableMap<PatternContainerGroup, MutableList<PatternContainerRecord>> = HashMap()

    @JvmStatic
    private val selectedProvider: MutableMap<IPatternDetails, PatternContainerGroup> = HashMap()

    @JvmStatic
    private val selectedPattern: MutableSet<IPatternDetails> = HashSet()

    @JvmStatic
    private val providerSlots: MutableMap<PatternContainerGroup, MutableMap<PatternContainerRecord, BitSet>> = HashMap()

    @JvmStatic
    private val markedCount: MutableMap<PatternContainerGroup, Int> = HashMap()

    @JvmStatic
    private val networkSlots: MutableMap<PatternContainerGroup, Int> = LinkedHashMap()

    @JvmStatic
    private val primaryOutputOfProvider: MutableMap<AEKey, Int> = HashMap()
    @JvmStatic
    private val primaryOutputOfPattern: MutableMap<AEKey, Int> = HashMap()

    private fun primaryOutputOf(pattern: IPatternDetails): AEKey? {
        val outputs = pattern.outputs
        return if (outputs.isNotEmpty()) outputs[0].what else null
    }

    private fun incPrimaryOutput(map: MutableMap<AEKey, Int>, key: AEKey?) {
        if (key != null) map[key] = (map[key] ?: 0) + 1
    }

    private fun decPrimaryOutput(map: MutableMap<AEKey, Int>, key: AEKey?) {
        if (key == null) return
        val v = (map[key] ?: 0) - 1
        if (v <= 0) map.remove(key) else map[key] = v
    }

    @JvmStatic
    fun beginSync() {
        networkSlots.clear()
    }

    @JvmStatic
    fun addNetworkProvider(group: PatternContainerGroup, availableSlots: Int) {
        networkSlots[group] = availableSlots
    }

    @JvmStatic
    fun endSync() {
    }

    @JvmStatic
    fun incMark(group: PatternContainerGroup) {
        markedCount[group] = (markedCount[group] ?: 0) + 1
    }

    @JvmStatic
    fun decMark(group: PatternContainerGroup) {
        val v = (markedCount[group] ?: 0) - 1
        if (v <= 0) markedCount.remove(group) else markedCount[group] = v
    }

    @JvmStatic
    fun markPattern(pattern: IPatternDetails, container: PatternContainerGroup) {
        val old = selectedProvider.put(pattern, container)
        if (old == null) {
            incMark(container)
            incPrimaryOutput(primaryOutputOfProvider, primaryOutputOf(pattern))
        } else if (old != container) {
            decMark(old)
            incMark(container)
        }
    }

    @JvmStatic
    fun unmarkPattern(pattern: IPatternDetails) {
        val old = selectedProvider.remove(pattern)
        if (old != null) {
            decMark(old)
            decPrimaryOutput(primaryOutputOfProvider, primaryOutputOf(pattern))
        }
    }

    @JvmStatic
    fun markPatternAlready(pattern: IPatternDetails) {
        if (selectedPattern.add(pattern)) {
            incPrimaryOutput(primaryOutputOfPattern, primaryOutputOf(pattern))
        }
    }

    @JvmStatic
    fun unmarkPatternAlready(pattern: IPatternDetails) {
        if (selectedPattern.remove(pattern)) {
            decPrimaryOutput(primaryOutputOfPattern, primaryOutputOf(pattern))
        }
    }

    @JvmStatic
    fun clearPattern() {
        selectedProvider.clear()
        markedCount.clear()
        primaryOutputOfProvider.clear()
    }

    @JvmStatic
    fun clearPatternAlready() {
        selectedPattern.clear()
        providerSlots.clear()
        primaryOutputOfPattern.clear()
    }

    @JvmStatic
    fun hasPatternAlready(pattern: IPatternDetails): Boolean {
        return selectedPattern.contains(pattern)
    }

    @JvmStatic
    fun findProvider(pattern: IPatternDetails?): PatternContainerGroup? {
        return selectedProvider[pattern]
    }

    @JvmStatic
    fun hasPattern(pattern: IPatternDetails): Boolean {
        return selectedPattern.contains(pattern) || selectedProvider.containsKey(pattern)
    }

    @JvmStatic
    fun hasPrimaryOutput(pattern: IPatternDetails): Boolean {
        val key = primaryOutputOf(pattern) ?: return false
        return primaryOutputOfProvider.containsKey(key) || primaryOutputOfPattern.containsKey(key)
    }

    @JvmStatic
    fun putProvider(container: PatternContainerRecord, mabeHasSlot: Boolean) {
        if (mabeHasSlot) {
            providerList.getOrPut(container.group) { mutableListOf() }.add(container)
            val bs = providerSlots.getOrPut(container.group) { HashMap() }
                .getOrPut(container) { BitSet(container.inventory.size()) }
            for (i in 0..<container.inventory.size()){
                bs[i] = container.inventory.getStackInSlot(i) != ItemStack.EMPTY
            }
        } else {
            providerList.getOrPut(container.group) { mutableListOf() }
        }
    }

    @JvmStatic
    fun setSlots(record: PatternContainerRecord, idx: Int, used: Boolean) {
        providerSlots.getOrPut(record.group) { HashMap() }[record]?.let { it -> it[idx] = used; }
    }

    @JvmStatic
    fun getAvailableSlots(group: PatternContainerGroup): Int {
        val reserved = markedCount[group] ?: 0

        val map = providerSlots[group]
        if (map != null && map.isNotEmpty()) {
            var all = 0
            val it = map.entries.iterator()
            while (it.hasNext()) {
                val (record, set) = it.next()
                val used = set.cardinality()
                val canUsed = record.inventory.size() - used
                if (canUsed > 0) {
                    all += canUsed
                } else {
                    it.remove()
                }
            }
            return all - reserved
        }

        val netSlots = networkSlots[group]
        if (netSlots != null) {
            return netSlots - reserved
        }

        return 0
    }

    @JvmStatic
    fun getAvailableProvider(group: PatternContainerGroup): PatternContainerRecord? {
        val list = providerList[group] ?: return null
        for (rec in list) {
            val inv = rec.inventory
            val size = inv.size()
            for (i in 0 until size) {
                if (inv.getStackInSlot(i).isEmpty) return rec
            }
        }
        return null
    }

    @JvmStatic
    fun getGroups(): MutableSet<PatternContainerGroup> {
        val groups = LinkedHashSet(providerList.keys)
        groups.addAll(networkSlots.keys)
        return groups
    }

    @JvmStatic
    fun isEmpty(): Boolean {
        return providerList.isEmpty() && networkSlots.isEmpty()
    }

    @JvmStatic
    fun clearProvider() {
        providerList.clear()
        providerSlots.clear()
        networkSlots.clear()
    }
}
