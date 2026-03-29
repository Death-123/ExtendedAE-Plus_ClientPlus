package com.fish.extendedae_plus_client.mixin.core.ae.screen;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.patternaccess.PatternAccessTermScreen;
import appeng.client.gui.me.patternaccess.PatternContainerRecord;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AETextField;
import appeng.menu.implementations.PatternAccessTermMenu;
import com.fish.extendedae_plus_client.impl.cache.CacheProvider;
import com.fish.extendedae_plus_client.mixin.impl.helper.HelperPatternMoving;
import com.fish.extendedae_plus_client.render.widgets.button.EAEPActionButton;
import com.fish.extendedae_plus_client.render.widgets.button.EAEPActionItems;
import com.fish.extendedae_plus_client.util.UtilKeyBuilder;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@Mixin(PatternAccessTermScreen.class)
public class MixinAccessTerminal<TMenu extends PatternAccessTermMenu> extends AEBaseScreen<TMenu> {
    @Shadow
    @Final
    private HashMap<Long, PatternContainerRecord> byId;
    @Shadow
    @Final
    private AETextField searchField;

    @Unique
    private HelperPatternMoving eaep$helperMoving;

    public MixinAccessTerminal(TMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        CacheProvider.clearProvider();
        CacheProvider.clearPatternAlready();
        this.eaep$helperMoving = new HelperPatternMoving(this);
        this.addToLeftToolbar(
                new EAEPActionButton(EAEPActionItems.CHECK_DUPLICATES, action -> eaep$checkDuplicateOutputs())
        );
    }

    @SuppressWarnings("MixinAnnotationTarget")
    @Inject(method = "onClose", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        this.eaep$helperMoving.onClose();
    }

    @Inject(method = "postFullUpdate", at = @At("TAIL"))
    private void onProviderListSync(long inventoryId,
                                    long sortBy,
                                    PatternContainerGroup group,
                                    int inventorySize,
                                    Int2ObjectMap<ItemStack> slots,
                                    CallbackInfo ci) {
        CacheProvider.putProvider(this.byId.get(inventoryId), inventorySize - slots.size() > 0);
        for (var i : slots.values()) {
            var patternDetail = PatternDetailsHelper.decodePattern(i, this.getPlayer().level());
            if (patternDetail != null) {
                CacheProvider.markPatternAlready(patternDetail);
            }
        }
    }

    @Inject(method = "postIncrementalUpdate", at = @At("HEAD"))
    private void onProviderListSyncInc(long inventoryId, Int2ObjectMap<ItemStack> slots, CallbackInfo ci) {
        for (var i : slots.int2ObjectEntrySet()) {
            if (i.getValue() == ItemStack.EMPTY) {
                CacheProvider.putProvider(this.byId.get(inventoryId), true);
                CacheProvider.setSlots(this.byId.get(inventoryId), i.getIntKey(), false);
                var pattern = this.byId.get(inventoryId).getInventory().getStackInSlot(i.getIntKey());
                var patternDetail = PatternDetailsHelper.decodePattern(pattern, this.getPlayer().level());
                if (patternDetail != null) {
                    CacheProvider.unmarkPatternAlready(patternDetail);
                }
            } else {
                CacheProvider.setSlots(this.byId.get(inventoryId), i.getIntKey(), true);
                var patternDetail = PatternDetailsHelper.decodePattern(i.getValue(), this.getPlayer().level());
                if (patternDetail != null) {
                    CacheProvider.markPatternAlready(patternDetail);
                    this.eaep$helperMoving.markSuccess(patternDetail);
                }
            }

        }
    }

    @Inject(method = "updateBeforeRender", at = @At("HEAD"))
    private void onRenderUpdating(CallbackInfo ci) {
        if (this.eaep$helperMoving.isEmpty()) return;
        this.searchField.setFocused(false);
    }

    @SuppressWarnings("MixinAnnotationTarget")
    @Inject(method = "containerTick", at = @At("TAIL"))
    private void onUpdating(CallbackInfo ci) {
        this.eaep$helperMoving.movePattern();
    }

    @Unique
    private void eaep$checkDuplicateOutputs() {
        var player = this.getPlayer();
        if (player == null) return;

        Map<AEKey, Set<IPatternDetails>> outputPatterns = new LinkedHashMap<>();

        for (var record : byId.values()) {
            var inv = record.getInventory();
            for (int i = 0; i < inv.size(); i++) {
                var stack = inv.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                var details = PatternDetailsHelper.decodePattern(stack, player.level());
                if (details == null) continue;
                var output = details.getPrimaryOutput().what();
                outputPatterns.computeIfAbsent(output, k -> new HashSet<>()).add(details);
            }
        }

        int found = 0;
        for (var entry : outputPatterns.entrySet()) {
            if (entry.getValue().size() > 1) {
                found++;
                var aeKey = entry.getKey();
                Component outputName = aeKey instanceof AEItemKey itemKey
                        ? itemKey.toStack().getHoverName()
                        : Component.literal(aeKey.getId().toString());
                player.displayClientMessage(
                        UtilKeyBuilder.of(UtilKeyBuilder.message)
                                .addStr("pattern")
                                .addStr("duplicate_output")
                                .args(outputName, aeKey.getId().toString(), entry.getValue().size())
                                .build(),
                        false
                );
            }
        }

        if (found == 0) {
            player.displayClientMessage(
                    UtilKeyBuilder.of(UtilKeyBuilder.message)
                            .addStr("pattern")
                            .addStr("no_duplicates")
                            .build(),
                    false
            );
        }
    }
}
