package com.fish.extendedae_plus_client.mixin.core.ae.screen;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.core.network.serverbound.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import appeng.menu.me.items.PatternEncodingTermMenu;
import com.fish.extendedae_plus_client.config.EAEPCConfig;
import com.fish.extendedae_plus_client.config.enums.AutoUploadMode;
import com.fish.extendedae_plus_client.mixin.impl.helper.HelperEncodingTerminal;
import com.fish.extendedae_plus_client.render.screen.ScreenStacksReproperties;
import com.fish.extendedae_plus_client.render.widgets.button.EAEPActionItems;
import com.fish.extendedae_plus_client.render.widgets.button.EAEPCycleButton;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PatternEncodingTermScreen.class)
public class MixinEncodingTerminalReproperties<TMenu extends PatternEncodingTermMenu>
        extends MEStorageScreen<TMenu> {
    public MixinEncodingTerminalReproperties(TMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        var changeUploadModeButton = new EAEPCycleButton.Builder()
                .addPart(EAEPActionItems.CHANGE_UPLOAD_MODE_NONE, action -> eaep$changeUploadMode(0))
                .addPart(EAEPActionItems.CHANGE_UPLOAD_MODE_WHEN_OPEN, action -> eaep$changeUploadMode(1))
                .addPart(EAEPActionItems.CHANGE_UPLOAD_MODE_AUTO_OPEN, action -> eaep$changeUploadMode(2))
                .addPart(EAEPActionItems.CHANGE_UPLOAD_MODE_EAEP_BY_NAME, action -> eaep$changeUploadMode(3))
                .addPart(EAEPActionItems.CHANGE_UPLOAD_MODE_SERVER_BY_GROUP, action -> eaep$changeUploadMode(4))
                .build();
        changeUploadModeButton.setStateIndex(EAEPCConfig.autoUploadMode.get().ordinal(), false);
        this.addToLeftToolbar(changeUploadModeButton);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClick(double xCoord, double yCoord, int btn, CallbackInfoReturnable<Boolean> cir) {
        if (this.minecraft == null) return;

        if (!this.menu.canModifyAmountForSlot(this.hoveredSlot)) return;

        if (!this.minecraft.options.keyPickItem.matchesMouse(btn)) return;
        if (!EAEPCConfig.itemEditingTiggerMode.get().shouldTigger()) return;

        var stack = this.hoveredSlot.getItem();
        var screen = new ScreenStacksReproperties<>(
                this,
                stack,
                newStack -> {
                    var packetUpdateStack = new InventoryActionPacket(
                            InventoryAction.SET_FILTER, this.hoveredSlot.index, newStack);
                    PacketDistributor.sendToServer(packetUpdateStack);
                },
                this.hoveredSlot == this.menu.getProcessingOutputSlots()[0]
        );
        this.switchToScreen(screen);
        cir.setReturnValue(true);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        ((HelperEncodingTerminal) this.menu).eaep$tick();
    }

    @Unique
    private void eaep$changeUploadMode(int i) {
        EAEPCConfig.autoUploadMode.set(AutoUploadMode.getEntries().get(i));
    }
}
