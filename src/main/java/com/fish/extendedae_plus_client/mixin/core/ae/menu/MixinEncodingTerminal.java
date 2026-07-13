package com.fish.extendedae_plus_client.mixin.core.ae.menu;

import appeng.api.storage.ITerminalHost;
import appeng.core.definitions.AEItems;
import appeng.core.network.serverbound.MEInteractionPacket;
import appeng.helpers.InventoryAction;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.parts.encoding.EncodingMode;
import com.fish.extendedae_plus_client.config.EAEPCConfig;
import com.fish.extendedae_plus_client.mixin.core.ae.accessor.AccessorAEBaseMenu;
import com.fish.extendedae_plus_client.mixin.core.ae.accessor.AccessorPatternEncodingTermMenu;
import com.fish.extendedae_plus_client.mixin.impl.bridge.BridgePlanToEncode;
import com.fish.extendedae_plus_client.mixin.impl.helper.AutoEncodingStage;
import com.fish.extendedae_plus_client.mixin.impl.helper.HelperEncodingTerminal;
import com.fish.extendedae_plus_client.upload.EncodingTerminalCtx;
import com.fish.extendedae_plus_client.upload.ModeBindings;
import com.fish.extendedae_plus_client.upload.internal.SlotFillObserver;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(PatternEncodingTermMenu.class)
public abstract class MixinEncodingTerminal extends MEStorageMenu implements BridgePlanToEncode, HelperEncodingTerminal {
    @Shadow
    public EncodingMode mode;
    @Shadow
    @Final
    private RestrictedInputSlot encodedPatternSlot;
    @Shadow
    @Final
    private RestrictedInputSlot blankPatternSlot;
    @Unique
    private AutoEncodingStage eaep$autoEncoding = AutoEncodingStage.None;

    public MixinEncodingTerminal(MenuType<?> menuType, int id, Inventory ip, ITerminalHost host) {
        super(menuType, id, ip, host);
    }

    @Shadow
    public abstract void encode();

    /**
     * Single entry point for "player pressed encode". Replaces the historic mode-specific
     * {@code if-else} ladder; the actual policy lives behind
     * {@link ModeBindings#currentEncodeFlow()}.
     *
     * <p>Whether to cancel {@code ci} is communicated by the flow calling
     * {@link EncodingTerminalCtx#getCancelClientEncode()} synchronously — we sample the
     * resulting flag right after the call.
     */
    @Inject(method = "encode", at = @At("HEAD"), cancellable = true)
    private void onEncode(CallbackInfo ci) {
        if (this.isServerSide()) return;

        eaep$fillPattern();

        if (!EAEPCConfig.encodingTiggerMode.get().shouldTigger()
                && eaep$autoEncoding == AutoEncodingStage.None) return;

        var self = (PatternEncodingTermMenu) (Object) this;
        var accessorMenu = (AccessorPatternEncodingTermMenu) (Object) this;
        var accessorBase = (AccessorAEBaseMenu) (Object) this;

        var cancelFlag = new AtomicBoolean(false);
        var ctx = new EncodingTerminalCtx(
                this.getPlayer(),
                self,
                this.containerId,
                this.encodedPatternSlot,
                accessorMenu::eaep$encodePattern,
                () -> accessorBase.eaep$sendClientAction("encode"),
                () -> cancelFlag.set(true),
                this.eaep$autoEncoding != AutoEncodingStage.None
        );

        // Flow may complete the future synchronously (intercept / fast path) or asynchronously
        // (slow path waiting for SlotFillObserver); commit 2 doesn't add UI feedback in the mixin.
        ModeBindings.currentEncodeFlow().onEncodeTriggered(self, ctx);

        if (cancelFlag.get()) ci.cancel();
    }

    /**
     * Forwards every {@code encodedPatternSlot} fill event to the {@link SlotFillObserver}.
     * The flow's slow-path branch registers itself there to resume after AE2's native encode
     * round-trip completes server-side and the slot syncs back.
     */
    @Inject(method = "onSlotChange", at = @At("TAIL"))
    private void onSlotChange(Slot slot, CallbackInfo ci) {
        if (this.isServerSide()) return;
        if (!this.encodedPatternSlot.equals(slot)) return;
        if (!this.encodedPatternSlot.hasItem()) return;

        SlotFillObserver.notifyFilled(
                this.containerId,
                this.encodedPatternSlot.index,
                this.encodedPatternSlot.getItem()
        );
    }

    @Override
    public void eaep$autoEncoding() {
        this.eaep$autoEncoding = AutoEncodingStage.Init;
    }

    /**
     * Per-tick maintenance: drives the {@code eaep$autoEncoding} state machine that powers the
     * "open terminal → auto-encode one pattern" affordance. The legacy SERVER_BY_GROUP
     * one-shot {@code RequestProvidersC2SPacket} priming has been removed in commit 3 — the
     * two-phase RPC inside {@code ServerByGroupEncodeFlow} now queries the server live on
     * every press, fixing the "add machine after opening terminal requires reopen" bug.
     */
    @Unique
    @Override
    public void eaep$tick() {
        if (this.eaep$autoEncoding != AutoEncodingStage.None) {
            this.eaep$autoEncoding = AutoEncodingStage.values()[(eaep$autoEncoding.ordinal() + 1) % AutoEncodingStage.values().length];
        }
        if (eaep$autoEncoding == AutoEncodingStage.Encode) {
            encode();
        }
    }

    /**
     * Auto-pulls a blank pattern into the menu's blank-pattern slot when both the carried
     * stack and the slot are empty. Independent of {@code autoUploadMode}.
     */
    @Unique
    public void eaep$fillPattern() {
        if (!getCarried().isEmpty()) return;
        if (blankPatternSlot.getItem().getCount() > 0) return;
        var patternIngredient = Ingredient.of(AEItems.BLANK_PATTERN);
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        var clientRepo = getClientRepo();
        if (clientRepo == null) return;
        var patternSlotList = clientRepo.getByIngredient(patternIngredient);
        if (patternSlotList.isEmpty()) return;
        var patternSlot = patternSlotList.toArray(new GridInventoryEntry[0])[0];
        PacketDistributor.sendToServer(new MEInteractionPacket(
                containerId,
                patternSlot.getSerial(),
                InventoryAction.PICKUP_OR_SET_DOWN
        ));
        player.connection.send(new ServerboundContainerClickPacket(
                containerId, 1, this.blankPatternSlot.index,
                0, ClickType.PICKUP, this.getCarried(), new Int2ObjectOpenHashMap<>()
        ));
    }
}
