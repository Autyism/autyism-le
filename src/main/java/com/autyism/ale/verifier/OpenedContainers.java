package com.autyism.ale.verifier;

import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 玩家打开世界里的容器时，客户端知道它装着什么：和投影里同一格的容器比一下，
 * 一样就不再画“需要填充”的 F，还差东西就照画。只在打开着、内容有变化时比较（每个容器最多几十格，不扫别的东西）。
 * 大箱子（两格拼成一个界面）不知道哪半边是哪个，跳过。
 */
public final class OpenedContainers {
    /** 最近一次点的方块（打开容器的那一下），在这之后这么多 tick 内打开的界面算是它的 */
    private static final int OPEN_WINDOW_TICKS = 40;
    @Nullable
    private static BlockPos lastUsed;
    private static int lastUsedTick;
    private static int tick;
    @Nullable
    private static AbstractContainerMenu menu;
    @Nullable
    private static BlockPos menuPos;
    private static int lastStateId = -1;

    private OpenedContainers() {
    }

    /** 客户端对一个方块按了使用键 */
    public static void onUseItemOn(BlockPos pos) {
        lastUsed = pos.immutable();
        lastUsedTick = tick;
    }

    public static void tick(Minecraft mc) {
        tick++;
        if (mc.player == null || mc.level == null) {
            menu = null;
            return;
        }
        AbstractContainerMenu current = mc.player.containerMenu;
        if (current == null || current == mc.player.inventoryMenu) {
            menu = null;
            return;
        }
        if (current != menu) {
            menu = current;
            lastStateId = -1;
            menuPos = lastUsed != null && tick - lastUsedTick <= OPEN_WINDOW_TICKS ? lastUsed : null;
        }
        if (menuPos == null || current.getStateId() == lastStateId) return;
        lastStateId = current.getStateId();
        WorldSchematic schematic = SchematicWorldHandler.getSchematicWorld();
        if (schematic == null || !(schematic.getBlockEntity(menuPos) instanceof Container wanted)) return;
        if (schematic.getBlockState(menuPos).getBlock() != mc.level.getBlockState(menuPos).getBlock()) return;
        // 界面里属于这个方块的那些格子（玩家背包的格子不算；合成器的结果格这种另一个容器的也不算）
        Container block = null;
        int size = 0;
        List<ItemStack> actual = new ArrayList<>();
        for (Slot slot : current.slots) {
            if (slot.container instanceof Inventory) continue;
            if (block == null) block = slot.container;
            if (slot.container != block) continue;
            size++;
            if (!slot.getItem().isEmpty()) actual.add(slot.getItem().copy());
        }
        if (block == null || size != wanted.getContainerSize()) return;
        List<ItemStack> want = new ArrayList<>();
        for (int i = 0; i < wanted.getContainerSize(); i++) {
            if (!wanted.getItem(i).isEmpty()) want.add(wanted.getItem(i));
        }
        ContainerVerifier.noteWorldContents(menuPos, ContainerVerifier.sameContents(want, actual));
    }
}
