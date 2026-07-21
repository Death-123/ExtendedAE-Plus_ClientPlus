package com.fish.extendedae_plus_client.render.widgets.button

import appeng.client.gui.AEBaseScreen
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.neoforged.neoforge.network.PacketDistributor
import java.util.function.BiConsumer
import java.util.function.Consumer

open class EAEPCycleButton(
    protected val states: List<EAEPActionItems>,
    statedTask: BiConsumer<Int, EAEPActionItems>,
    protected val iteratorState: IteratorState
) : EAEPButton({ button ->
    val screen = Minecraft.getInstance().screen
    if (button is EAEPCycleButton && screen is AEBaseScreen<*> && button.states.isNotEmpty())
        statedTask.accept(button.iterateState(screen.isHandlingRightClick), button.action)
}) {
    protected var stateIndex: Int = 0

    init {
        this.updateTooltip()
    }

    override val action: EAEPActionItems
        get() = if (this.states != null) this.states[this.stateIndex] else EAEPActionItems.BACKING_OUT

    fun setStateIndex(stateIndex: Int, triggerEvent: Boolean) {
        this.stateIndex = stateIndex
        if (triggerEvent) this.onPress()
        else this.updateTooltip()
    }

    /**
     * @return 被迭代过的 stateIndex
     */
    fun iterateState(reversed: Boolean): Int {
        this.iteratorState.let { this.stateIndex = it.iterate(this.stateIndex, reversed) }
        return this.stateIndex
    }

    class Builder {
        private val states: MutableList<EAEPActionItems> = ArrayList()
        private val tasks: MutableList<Consumer<EAEPActionItems>> = ArrayList()
        private var task: Consumer<EAEPActionItems>? = null
        private var iteratorState: IteratorState? = null

        fun addPart(action: EAEPActionItems, packet: CustomPacketPayload): Builder {
            return this.addPart(action, Runnable { PacketDistributor.sendToServer(packet) })
        }

        fun addPart(action: EAEPActionItems, onPress: Runnable): Builder {
            return this.addPart(action) { _ -> onPress.run() }
        }

        fun addPart(action: EAEPActionItems, onPress: Consumer<EAEPActionItems>? = Consumer {}): Builder {
            this.states.add(action)
            this.tasks.add(onPress?: Consumer {})
            return this
        }

        fun addGlobalTask(task: Consumer<EAEPActionItems>?): Builder {
            this.task = task
            return this
        }

        fun setIterator(iteratorState: IteratorState): Builder {
            this.iteratorState = iteratorState
            return this
        }

        fun build(): EAEPCycleButton {
            return EAEPCycleButton(
                this.states,
                { index: Int, action: EAEPActionItems ->
                    this.task?.accept(action)
                    this.tasks[index].accept(action)
                },
                this.iteratorState ?: IteratorState { prev, reversed ->
                    (prev + if (reversed) -1 else 1 + this.states.size) % this.states.size
                }
            )
        }
    }

    fun interface IteratorState {
        fun iterate(prev: Int, reversed: Boolean): Int
    }
}
