package com.fish.extendedae_plus_client.mixin.impl.helper;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.client.gui.AEBaseScreen;
import appeng.core.network.serverbound.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import com.extendedae_plus.network.ProvidersListS2CPacket;
import com.extendedae_plus.network.RequestProvidersListC2SPacket;
import com.extendedae_plus.network.UploadEncodedPatternToProviderC2SPacket;
import com.fish.extendedae_plus_client.config.EAEPCConfig;
import com.fish.extendedae_plus_client.config.enums.AutoUploadMode;
import com.fish.extendedae_plus_client.impl.cache.CacheProvider;
import com.fish.extendedae_plus_client.network.UploadPatternByGroupPacket;
import com.fish.extendedae_plus_client.util.ComponentLocaleConverter;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

public final class HelperPatternMoving {
    private final AEBaseScreen<?> host;

    private final Map<Long, Integer> cacheUsedSlots;
    private final List<Pair<Integer, Pair<IPatternDetails, PatternContainerGroup>>> patterns;
    private final Set<IPatternDetails> cache = new HashSet<>();
    private final Map<IPatternDetails, PatternContainerGroup> perSuccess = new HashMap<>();
    private int delay = EAEPCConfig.autoTransferDelay.getAsInt();
    private boolean perCompleted = false;

    // EAEP_BY_NAME flow state
    public static IPatternDetails eaepPendingPattern = null;
    public static PatternContainerGroup eaepPendingGroup = null;

    public static HelperPatternMoving INSTANCE = null;

    public HelperPatternMoving(AEBaseScreen<?> host) {
        this.host = host;
        this.patterns = new ArrayList<>();
        this.cacheUsedSlots = new HashMap<>();
        INSTANCE = this;
    }

    public void onClose() {
        CacheProvider.clearPattern();
        this.cacheUsedSlots.clear();
        for (var i : perSuccess.entrySet()) {
            CacheProvider.markPattern(i.getKey(), i.getValue());
        }
        INSTANCE = null;
    }

    public boolean isEmpty() {
        return this.patterns.isEmpty();
    }

    public void markSuccess(IPatternDetails patternDetails) {
        perSuccess.remove(patternDetails);
    }

    public void movePattern() {
        if (perCompleted && perSuccess.isEmpty()) {
            WTLibHelper.goBackCyc();
        }
        if (perCompleted) return;
        if (EAEPCConfig.autoUploadMode.get() != AutoUploadMode.WHEN_OPEN && EAEPCConfig.autoUploadMode.get() != AutoUploadMode.AUTO_OPEN) {
            perCompleted = true;
            return;
        }
        if (this.delay > 0) {
            this.delay--;
            return;
        }

        if (this.patterns.isEmpty() && this.host.getMenu().getCarried().isEmpty())
            this.filterPattern();
        if (this.patterns.isEmpty()) return;

        var info = this.patterns.getFirst();
        var perSuccess = this.movePatternNew(info.getFirst(), info.getSecond().getSecond());

        if (perSuccess) {
            this.perSuccess.put(info.getSecond().getFirst(), info.getSecond().getSecond());
            this.patterns.removeFirst();
        } else {
            this.patterns.removeFirst();
            this.patterns.addLast(info);
        }

        if (this.patterns.isEmpty()) {
            this.perCompleted = true;
        }
    }

    private void filterPattern() {
        if (Minecraft.getInstance().player == null) return;

        var inv = Minecraft.getInstance().player.getInventory().items;
        for (int index = 0; index < inv.size(); index++) {
            ItemStack stack = inv.get(index);
            if (stack.isEmpty()) continue;
            var details = PatternDetailsHelper.decodePattern(stack, Minecraft.getInstance().level);
            if (details == null || CacheProvider.hasPatternAlready(details) || cache.contains(details)) continue;
            cache.add(details);

            var hashGroup = CacheProvider.findProvider(details);
            if (hashGroup == null) continue;

            this.patterns.add(new Pair<>(index, new Pair<>(details, hashGroup)));
        }
    }

    private boolean movePatternNew(Integer slot, PatternContainerGroup group) {
        if (CacheProvider.isEmpty()) {
            return false;
        }
        var menu = this.host.getMenu();
        var player = Minecraft.getInstance().player;
        var providerInfo = CacheProvider.getAvailableProvider(group);

        if (providerInfo == null) return false;
        if (player == null || slot < 0 || slot > menu.slots.size()) {
            return false;
        }
        if (!menu.getSlot(slot).hasItem()) {
            return false;
        }
        player.connection.send(new ServerboundContainerClickPacket(
                menu.containerId, 1, slot, 0,
                ClickType.PICKUP, menu.getCarried(), new Int2ObjectOpenHashMap<>()
        ));

        int targetSlot = -1;
        var inventory = providerInfo.getInventory();
        long providerId = providerInfo.getServerId();

        var used = cacheUsedSlots.getOrDefault(providerId, 0);
        for (int i = used; i < inventory.size(); i++) {
            if (!inventory.getStackInSlot(i).isEmpty()) continue;
            targetSlot = i;
            break;
        }
        if (targetSlot == -1) {
            return false;
        }
        PacketDistributor.sendToServer(new InventoryActionPacket(
                InventoryAction.PICKUP_OR_SET_DOWN,
                targetSlot,
                providerId
        ));

        cacheUsedSlots.put(providerId, targetSlot + 1);
        CacheProvider.setSlots(providerInfo, slot, true);
        return true;
    }

    // ======================== SERVER_BY_GROUP (本mod服务端) ========================

    /**
     * 通过本mod自己的服务端handler上传，使用 {@link PatternContainerGroup} 精确匹配。
     * 需要服务端安装本mod。
     */
    public static void uploadPatternToGroup(PatternContainerGroup group, IPatternDetails pattern) {
        var iconId = group.icon() != null ? group.icon().getId() : null;
        PacketDistributor.sendToServer(new UploadPatternByGroupPacket(iconId, group.name()));
        CacheProvider.unmarkPattern(pattern);
        CacheProvider.markPatternAlready(pattern);
        CacheProvider.incMark(group);
    }

    // ======================== EAEP_BY_NAME (兼容EAEP服务端) ========================

    /**
     * 通过EAEP的服务端handler上传，使用名称匹配。
     * 兼容只安装了EAEP的服务器。
     */
    public static void eaepUploadPatternByName(PatternContainerGroup group, IPatternDetails pattern) {
        if (!ModList.get().isLoaded("extendedae_plus")) return;
        eaepUploadPatternByNameSafe(group);
        eaepPendingPattern = pattern;
    }

    private static void eaepUploadPatternByNameSafe(PatternContainerGroup group) {
        PacketDistributor.sendToServer(RequestProvidersListC2SPacket.INSTANCE);
        eaepPendingGroup = group;
    }

    public static void eaepPacketHandler(ProvidersListS2CPacket tmp) {
        HelperProvidersListS2CPacket packet = (HelperProvidersListS2CPacket) tmp;
        if (eaepPendingGroup == null || eaepPendingPattern == null) return;

        final String localName = ComponentLocaleConverter.normalizeForCompare(eaepPendingGroup.name().getString());
        final String enUsName = ComponentLocaleConverter.normalizeForCompare(
                ComponentLocaleConverter.toLocaleString(eaepPendingGroup.name(), "en_us")
        );

        for (var i = 0; i < packet.getIds().size(); ++i) {
            var serverNameComp = packet.getNames().get(i);
            String serverName = ComponentLocaleConverter.normalizeForCompare(serverNameComp.getString());
            String serverNameEnUs = ComponentLocaleConverter.normalizeForCompare(
                    ComponentLocaleConverter.toLocaleString(serverNameComp, "en_us")
            );
            if ((!enUsName.isEmpty() && (serverNameEnUs.equals(enUsName) || serverName.equals(enUsName)))
                    || (!localName.isEmpty() && (serverName.equals(localName) || serverNameEnUs.equals(localName)))) {
                PacketDistributor.sendToServer(new UploadEncodedPatternToProviderC2SPacket(packet.getIds().get(i)));
                CacheProvider.unmarkPattern(eaepPendingPattern);
                CacheProvider.markPatternAlready(eaepPendingPattern);
                CacheProvider.incMark(eaepPendingGroup);

                eaepPendingPattern = null;
                eaepPendingGroup = null;
                break;
            }
        }
    }
}
