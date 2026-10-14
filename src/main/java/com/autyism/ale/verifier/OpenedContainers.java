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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 玩家打开世界里的容器时，客户端知道它装着什么：和投影里同一格的容器比一下，
 * 一样就不再画“需要填充”的 F，不一样（包括开着时被拿走了东西）就照画。只在打开着、内容有变化时比较（每个容器最多几十格）。
 * 大箱子的界面是两个箱子拼起来的：前 27 格是 RIGHT 那半边（和原版拼的顺序一样），后 27 格是 LEFT 那半边，两半分别比较。
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
            lastUsed = null;
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
            // 界面属于刚才点的方块；用掉这一下，之后别的界面（例如运输矿车的）不会再算到这个方块头上
            menuPos = lastUsed != null && tick - lastUsedTick <= OPEN_WINDOW_TICKS ? lastUsed : null;
            lastUsed = null;
        }
        if (menuPos == null || current.getStateId() == lastStateId) return;
        lastStateId = current.getStateId();
        WorldSchematic schematic = SchematicWorldHandler.getSchematicWorld();
        if (schematic == null) return;
        // 界面里属于这个方块的那些格子（玩家背包的格子不算；合成器的结果格这种另一个容器的也不算）
        Container block = null;
        List<ItemStack> slots = new ArrayList<>();
        for (Slot slot : current.slots) {
            if (slot.container instanceof Inventory) continue;
            if (block == null) block = slot.container;
            if (slot.container != block) continue;
            slots.add(slot.getItem());
        }
        if (block == null) return;
        BlockState state = mc.level.getBlockState(menuPos);
        BlockPos other = otherChestHalf(mc.level, menuPos, state);
        if (other != null && slots.size() == 54) {
            // 原版大箱子：RIGHT 那半边在前
            boolean thisFirst = state.getValue(ChestBlock.TYPE) == ChestType.RIGHT;
            compare(mc, schematic, thisFirst ? menuPos : other, slots.subList(0, 27));
            compare(mc, schematic, thisFirst ? other : menuPos, slots.subList(27, 54));
        } else {
            compare(mc, schematic, menuPos, slots);
        }
    }

    /** 投影里 pos 的容器和界面里这些格子比；方块对不上或格数对不上就不下结论 */
    private static void compare(Minecraft mc, WorldSchematic schematic, BlockPos pos, List<ItemStack> slots) {
        if (!(schematic.getBlockEntity(pos) instanceof Container wanted)) return;
        if (schematic.getBlockState(pos).getBlock() != mc.level.getBlockState(pos).getBlock()) return;
        if (slots.size() != wanted.getContainerSize()) return;
        List<ItemStack> actual = new ArrayList<>();
        for (ItemStack stack : slots) {
            if (!stack.isEmpty()) actual.add(stack.copy());
        }
        ContainerVerifier.noteWorldContents(pos, ContainerVerifier.sameContents(ContainerVerifier.nonEmpty(wanted), actual));
    }

    /** 世界里拼成大箱子的另一半（和原版判断能不能拼的条件一样），单个箱子返回 null */
    @Nullable
    private static BlockPos otherChestHalf(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return null;
        BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
        BlockState otherState = level.getBlockState(other);
        if (!otherState.is(state.getBlock())) return null;
        ChestType otherType = otherState.getValue(ChestBlock.TYPE);
        if (otherType == ChestType.SINGLE || otherType == state.getValue(ChestBlock.TYPE)) return null;
        if (otherState.getValue(ChestBlock.FACING) != state.getValue(ChestBlock.FACING)) return null;
        return other;
    }
}
