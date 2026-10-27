package com.autyism.ale.verifier;

import com.autyism.ale.config.AleConfigs;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 打开投影里的容器时，界面里每一格和投影里那一格比：
 * <ul>
 * <li>缺（投影里有、这格空着）：蓝色，画一个半透明的虚影，鼠标移上去显示该放什么；</li>
 * <li>放错（不是这种物品）：红色；</li>
 * <li>多出（投影里这格是空的）：紫色；</li>
 * <li>数量不对、合成器禁用的格子不对：橙色。</li>
 * </ul>
 * 颜色和透明度：格子底色 72/255，边框调亮 45% 后 216/255，虚影 80% 不透明再盖一层调亮 78% 的 120/255。
 * 界面属于哪个方块用 OpenedContainers 记下的位置（刚点的那一下）。
 */
public final class SlotHints {
    public enum Status {
        MISSING(0x2979FF), WRONG(0xFF1744), EXTRA(0xD500F9), COUNT(0xFF9100);

        private final int rgb;

        Status(int rgb) {
            this.rgb = rgb;
        }

        public int fill() {
            return withAlpha(this.rgb, 72);
        }

        public int border() {
            return withAlpha(towardWhite(this.rgb, 0.45f), 216);
        }

        public int ghostMask() {
            return withAlpha(towardWhite(this.rgb, 0.78f), 120);
        }
    }

    /** 虚影：物品画完后盖一层这个颜色，盖住的程度 = 1 - 物品的不透明度（0.8） */
    public static final int GHOST_SHADE = 0xD0E3FF;
    public static final float GHOST_ALPHA = 0.8f;

    public record Hint(Status status, ItemStack expected) {
    }

    @Nullable
    private static AbstractContainerMenu cachedMenu;
    private static int cachedState = -1;
    @Nullable
    private static Container cachedContainer;
    private static List<Hint> cachedHints = List.of();

    private SlotHints() {
    }

    /** 这一格的提示；不在投影里、不是投影里的容器的格子是 null */
    @Nullable
    public static Hint hint(AbstractContainerMenu menu, Slot slot) {
        if (!AleConfigs.Generic.CONTAINER_SLOT_HINTS.getBooleanValue() || slot.container instanceof Inventory) return null;
        refresh(menu);
        if (slot.container != cachedContainer) return null;
        int i = slot.getContainerSlot();
        return i >= 0 && i < cachedHints.size() ? cachedHints.get(i) : null;
    }

    private static void refresh(AbstractContainerMenu menu) {
        if (menu == cachedMenu && menu.getStateId() == cachedState) return;
        cachedMenu = menu;
        cachedState = menu.getStateId();
        cachedContainer = null;
        cachedHints = List.of();
        Minecraft mc = Minecraft.getInstance();
        BlockPos pos = OpenedContainers.currentPos();
        WorldSchematic ws = SchematicWorldHandler.getSchematicWorld();
        if (pos == null || ws == null || mc.level == null) {
            cachedMenu = null; // 界面属于哪个方块还没记下（界面先画、tick 后到）：下一帧再看
            return;
        }
        // 界面里属于这个方块的格子（玩家背包不算），按它们在容器里的位置
        Container block = null;
        List<ItemStack> found = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory) continue;
            if (block == null) block = slot.container;
            if (slot.container != block) continue;
            int index = slot.getContainerSlot();
            while (found.size() <= index) found.add(ItemStack.EMPTY);
            boolean disabled = menu instanceof net.minecraft.world.inventory.CrafterMenu crafter && crafter.isSlotDisabled(slot.index);
            found.set(index, disabled ? ContainerVerifier.disabledSlot() : slot.getItem());
        }
        if (block == null) return;
        // 投影里该放的：大箱子是两半拼起来的，RIGHT 那半边在前（和原版界面一样）
        List<ItemStack> wanted = new ArrayList<>();
        BlockState state = mc.level.getBlockState(pos);
        BlockPos other = otherHalf(mc, pos, state);
        if (other != null && found.size() == 54) {
            boolean thisFirst = state.getValue(ChestBlock.TYPE) == ChestType.RIGHT;
            if (!addWanted(ws, mc, thisFirst ? pos : other, wanted) || !addWanted(ws, mc, thisFirst ? other : pos, wanted)) return;
        } else if (!addWanted(ws, mc, pos, wanted)) {
            return;
        }
        if (wanted.size() != found.size()) return;
        List<Hint> hints = new ArrayList<>(found.size());
        for (int i = 0; i < found.size(); i++) hints.add(compare(wanted.get(i), found.get(i)));
        cachedContainer = block;
        cachedHints = hints;
    }

    private static boolean addWanted(WorldSchematic ws, Minecraft mc, BlockPos pos, List<ItemStack> out) {
        if (!(ws.getBlockEntity(pos) instanceof Container c)) return false;
        if (ws.getBlockState(pos).getBlock() != mc.level.getBlockState(pos).getBlock()) return false;
        out.addAll(ContainerVerifier.nonEmpty(c));
        return true;
    }

    @Nullable
    private static Hint compare(ItemStack want, ItemStack found) {
        boolean wantOff = want == ContainerVerifier.disabledSlot(), foundOff = found == ContainerVerifier.disabledSlot();
        if (wantOff || foundOff) return wantOff == foundOff ? null : new Hint(Status.COUNT, wantOff ? ItemStack.EMPTY : want);
        if (want.isEmpty() && found.isEmpty()) return null;
        if (found.isEmpty()) return new Hint(Status.MISSING, want);
        if (want.isEmpty()) return new Hint(Status.EXTRA, ItemStack.EMPTY);
        if (!ItemStack.isSameItemSameComponents(want, found)) return new Hint(Status.WRONG, want);
        if (want.getCount() != found.getCount()) return new Hint(Status.COUNT, want);
        return null;
    }

    @Nullable
    private static BlockPos otherHalf(Minecraft mc, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return null;
        BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
        BlockState o = mc.level.getBlockState(other);
        if (!o.is(state.getBlock()) || o.getValue(ChestBlock.TYPE) == ChestType.SINGLE || o.getValue(ChestBlock.TYPE) == state.getValue(ChestBlock.TYPE)) return null;
        return other;
    }

    private static int withAlpha(int rgb, int alpha) {
        return (alpha & 0xFF) << 24 | rgb & 0xFFFFFF;
    }

    private static int towardWhite(int rgb, float amount) {
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        r += Math.round((255 - r) * amount);
        g += Math.round((255 - g) * amount);
        b += Math.round((255 - b) * amount);
        return r << 16 | g << 8 | b;
    }
}
