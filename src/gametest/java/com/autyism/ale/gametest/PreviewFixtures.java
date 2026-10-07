package com.autyism.ale.gametest;

import fi.dy.masa.litematica.data.DataManager;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * 浏览器 / 预览测试用的投影文件，全部写进 Litematica 的 schematics 目录：
 * <ul>
 *     <li>ale_house.litematic：小房子，带双箱子、床、告示牌、旗帜、火把、灯笼、玻璃、染色玻璃、水、树叶、花、头颅、附魔台；</li>
 *     <li>ale_tower.litematic：细高的塔；</li>
 *     <li>ale_house_struct.nbt：同一座房子的原版结构文件；</li>
 *     <li>ale_terrain.schem：Sponge 格式的一大块地形（体积约 60 万）；</li>
 *     <li>ale_huge.schem：很大但稀疏的 Sponge 投影（体积约 1600 万），测试大投影不卡死；</li>
 *     <li>broken.litematic：坏文件；</li>
 *     <li>文件夹 folder_a/inner、folder_b，以及放了很多文件的 many/。</li>
 * </ul>
 */
@SuppressWarnings("UnstableApiUsage")
public final class PreviewFixtures {
    // 染色方块、铜避雷针在 26.2 改成了按颜色 / 氧化程度的集合
    //? if >=26.2 {
    /*private static final Block BLUE_GLASS = Blocks.STAINED_GLASS.blue();
    private static final Block LIGHTNING_ROD = Blocks.LIGHTNING_ROD.weathering().unaffected();
    private static final Block RED_BED = Blocks.BED.red();
    private static final Block BLUE_BED = Blocks.BED.blue();
    private static final Block RED_BANNER = Blocks.BANNER.red();
    private static final Block YELLOW_BANNER = Blocks.BANNER.yellow();
    private static final Block PURPLE_SHULKER = Blocks.DYED_SHULKER_BOX.purple();
    private static final Block WHITE_CONCRETE = Blocks.CONCRETE.white();
    private static final Block GRAY_CONCRETE = Blocks.CONCRETE.gray();
    *///?} else {
    private static final Block BLUE_GLASS = Blocks.BLUE_STAINED_GLASS;
    private static final Block LIGHTNING_ROD = Blocks.LIGHTNING_ROD;
    private static final Block RED_BED = Blocks.RED_BED;
    private static final Block BLUE_BED = Blocks.BLUE_BED;
    private static final Block RED_BANNER = Blocks.RED_BANNER;
    private static final Block YELLOW_BANNER = Blocks.YELLOW_BANNER;
    private static final Block PURPLE_SHULKER = Blocks.PURPLE_SHULKER_BOX;
    private static final Block WHITE_CONCRETE = Blocks.WHITE_CONCRETE;
    private static final Block GRAY_CONCRETE = Blocks.GRAY_CONCRETE;
    //?}

    private PreviewFixtures() {
    }

    /** 房子所在的区域（世界坐标），占 11 x 9 x 11 */
    public static final BlockPos HOUSE_MIN = new BlockPos(200, 64, 0);
    public static final BlockPos HOUSE_MAX = HOUSE_MIN.offset(10, 8, 10);
    public static final BlockPos TOWER_MIN = new BlockPos(220, 64, 0);
    public static final BlockPos TOWER_MAX = TOWER_MIN.offset(4, 29, 4);

    public static Path dir(ClientGameTestContext context) {
        return context.computeOnClient(c -> DataManager.getSchematicsBaseDirectory());
    }

    /** 建好所有测试文件；many = 放进 many/ 文件夹的房子副本数量 */
    public static void createAll(ClientGameTestContext context, TestSingleplayerContext sp, int many) {
        Path dir = dir(context);
        GT.clearArena(sp, HOUSE_MIN.getX() - 2, HOUSE_MIN.getZ() - 2, ENT_MAX.getX() + 2, HOUSE_MAX.getZ() + 2, 100);
        buildHouse(sp);
        buildTower(sp);
        context.waitTicks(5);
        capture(sp, dir, HOUSE_MIN, HOUSE_MAX, "ale_house");
        capture(sp, dir, TOWER_MIN, TOWER_MAX, "ale_tower");
        buildBlockEntities(sp);
        context.waitTicks(3);
        capture(sp, dir, BE_MIN, BE_MAX, "ale_block_entities");
        buildEntities(sp);
        context.waitTicks(10);
        capture(sp, dir, ENT_MIN, ENT_MAX, "ale_entities");
        var withEntities = fi.dy.masa.litematica.schematic.LitematicaSchematic.createFromFile(dir, "ale_entities.litematic");
        int entityCount = 0;
        if (withEntities != null) {
            for (String region : withEntities.getAreas().keySet()) {
                var list = withEntities.getEntityListForRegion(region);
                if (list != null) entityCount += list.size();
            }
        }
        GT.log("[fixtures] ale_entities.litematic holds " + entityCount + " entities");
        saveStructure(sp, dir.resolve("ale_house_struct.nbt"), HOUSE_MIN, HOUSE_MAX);
        try {
            writeSponge(dir.resolve("ale_terrain.schem"), 160, 24, 160, PreviewFixtures::terrain);
            writeSponge(dir.resolve("ale_huge.schem"), 512, 64, 512, PreviewFixtures::sparse);
            writeSponge(dir.resolve("ale_dense.schem"), 160, 64, 160, PreviewFixtures::checkerboard);
            Files.write(dir.resolve("broken.litematic"), "this is not a schematic".getBytes());
            Path a = Files.createDirectories(dir.resolve("folder_a").resolve("inner"));
            Files.createDirectories(dir.resolve("folder_b"));
            Files.copy(dir.resolve("ale_tower.litematic"), a.resolve("tower_copy.litematic"), REPLACE_EXISTING);
            Files.copy(dir.resolve("ale_house.litematic"), dir.resolve("folder_a").resolve("house_copy.litematic"), REPLACE_EXISTING);
            if (many > 0) {
                Path m = Files.createDirectories(dir.resolve("many"));
                for (int i = 0; i < many; i++) {
                    Files.copy(dir.resolve(i % 3 == 0 ? "ale_tower.litematic" : "ale_house.litematic"), m.resolve(String.format("copy_%03d.litematic", i)), REPLACE_EXISTING);
                }
            }
        } catch (Exception e) {
            throw new AssertionError("[fixtures] " + e, e);
        }
        GT.log("[fixtures] schematics written to " + dir);
    }

    // ------------------------------------------------------------------ 房子

    private static void buildHouse(TestSingleplayerContext sp) {
        Map<BlockPos, BlockState> m = new LinkedHashMap<>();
        // 地面：草方块，房子下面是石砖
        for (int x = 0; x <= 10; x++) for (int z = 0; z <= 10; z++) {
            boolean inside = x >= 1 && x <= 9 && z >= 1 && z <= 9;
            m.put(new BlockPos(x, 0, z), (inside ? Blocks.STONE_BRICKS : Blocks.GRASS_BLOCK).defaultBlockState());
        }
        // 墙：橡木木板，四角原木，窗户玻璃板 / 染色玻璃
        for (int y = 1; y <= 4; y++) for (int x = 1; x <= 9; x++) for (int z = 1; z <= 9; z++) {
            boolean edgeX = x == 1 || x == 9, edgeZ = z == 1 || z == 9;
            if (!edgeX && !edgeZ) continue;
            BlockState s = edgeX && edgeZ ? Blocks.OAK_LOG.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState();
            if ((y == 2 || y == 3) && !(edgeX && edgeZ)) {
                if (edgeZ && (x == 3 || x == 7)) s = Blocks.GLASS.defaultBlockState();
                if (edgeX && (z == 4 || z == 6)) s = BLUE_GLASS.defaultBlockState();
            }
            m.put(new BlockPos(x, y, z), s);
        }
        // 门（朝南的前墙 z = 9，x = 5）
        m.put(new BlockPos(5, 1, 9), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        m.put(new BlockPos(5, 2, 9), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        // 屋顶：一圈朝外的楼梯 + 中间台阶
        for (int x = 0; x <= 10; x++) for (int z = 0; z <= 10; z++) {
            boolean ring = x == 0 || x == 10 || z == 0 || z == 10;
            if (ring) {
                Direction facing = z == 0 ? Direction.SOUTH : z == 10 ? Direction.NORTH : x == 0 ? Direction.EAST : Direction.WEST;
                m.put(new BlockPos(x, 5, z), Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.BOTTOM));
            } else {
                m.put(new BlockPos(x, 5, z), Blocks.DARK_OAK_PLANKS.defaultBlockState());
                if (x >= 2 && x <= 8 && z >= 2 && z <= 8) m.put(new BlockPos(x, 6, z), Blocks.DARK_OAK_SLAB.defaultBlockState());
            }
        }
        m.put(new BlockPos(5, 7, 5), LIGHTNING_ROD.defaultBlockState());
        // 屋里：双箱子（朝北，左半在西）、床、附魔台、灯笼、墙上火把、告示牌、旗帜、头颅
        m.put(new BlockPos(2, 1, 2), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH).setValue(ChestBlock.TYPE, ChestType.RIGHT));
        m.put(new BlockPos(3, 1, 2), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH).setValue(ChestBlock.TYPE, ChestType.LEFT));
        m.put(new BlockPos(7, 1, 3), RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT));
        m.put(new BlockPos(7, 1, 2), RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD));
        m.put(new BlockPos(5, 1, 5), Blocks.ENCHANTING_TABLE.defaultBlockState());
        m.put(new BlockPos(5, 4, 5), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        m.put(new BlockPos(5, 3, 2), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH));
        m.put(new BlockPos(3, 1, 8), Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 8));
        m.put(new BlockPos(8, 2, 5), Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.WEST));
        m.put(new BlockPos(2, 1, 6), RED_BANNER.defaultBlockState());
        m.put(new BlockPos(8, 1, 8), Blocks.SKELETON_SKULL.defaultBlockState());
        // 外面：树叶丛、花、草、小水池、营火
        for (int y = 1; y <= 2; y++) for (int x = 0; x <= 1; x++) m.put(new BlockPos(x, y, 0), Blocks.OAK_LEAVES.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
        m.put(new BlockPos(10, 1, 3), Blocks.POPPY.defaultBlockState());
        m.put(new BlockPos(10, 1, 5), Blocks.DANDELION.defaultBlockState());
        m.put(new BlockPos(0, 1, 7), Blocks.SHORT_GRASS.defaultBlockState());
        m.put(new BlockPos(9, 0, 10), Blocks.WATER.defaultBlockState());
        m.put(new BlockPos(10, 0, 10), Blocks.WATER.defaultBlockState());
        m.put(new BlockPos(10, 0, 9), Blocks.WATER.defaultBlockState());
        m.put(new BlockPos(0, 1, 10), Blocks.CAMPFIRE.defaultBlockState());
        sp.getServer().runOnServer(s -> {
            ServerLevel level = s.overworld();
            for (var e : m.entrySet()) {
                BlockPos pos = HOUSE_MIN.offset(e.getKey());
                level.setBlock(pos, e.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
                    GT.setSignLine(sign, true, 1, Component.literal("ALE"));
                    GT.setSignLine(sign, true, 2, Component.literal("preview"));
                }
            }
        });
    }

    /** 方块实体一字排开（没有墙挡着）：双箱子、床、告示牌（立式 / 挂墙）、旗帜、头颅、潜影盒、附魔台、钟、讲台、营火、饰纹陶罐 */
    // ------------------------------------------------------------------ 实体：盔甲架、物品展示框、画、矿车、船

    public static final BlockPos ENT_MIN = new BlockPos(248, 64, 0);
    public static final BlockPos ENT_MAX = ENT_MIN.offset(6, 3, 4);

    private static void buildEntities(TestSingleplayerContext sp) {
        sp.getServer().runOnServer(s -> {
            ServerLevel level = s.overworld();
            for (int x = 0; x <= 6; x++) {
                for (int z = 0; z <= 4; z++) level.setBlock(ENT_MIN.offset(x, 0, z), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int y = 1; y <= 3; y++) level.setBlock(ENT_MIN.offset(x, y, 0), Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            net.minecraft.world.entity.decoration.ItemFrame frame = new net.minecraft.world.entity.decoration.ItemFrame(level, ENT_MIN.offset(1, 2, 1), Direction.SOUTH);
            frame.setItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
            level.addFreshEntity(frame);
            net.minecraft.world.entity.decoration.painting.Painting.create(level, ENT_MIN.offset(4, 2, 1), Direction.SOUTH).ifPresent(level::addFreshEntity);
            net.minecraft.world.entity.decoration.ArmorStand stand = new net.minecraft.world.entity.decoration.ArmorStand(EntityType.ARMOR_STAND, level);
            stand.setPos(ENT_MIN.getX() + 5.5, ENT_MIN.getY() + 1, ENT_MIN.getZ() + 3.5);
            stand.setYRot(150.0F);
            stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_HELMET));
            level.addFreshEntity(stand);
            level.setBlock(ENT_MIN.offset(1, 1, 3), Blocks.RAIL.defaultBlockState(), Block.UPDATE_CLIENTS);
            var cart = EntityType.MINECART.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            cart.setPos(ENT_MIN.getX() + 1.5, ENT_MIN.getY() + 1.0625, ENT_MIN.getZ() + 3.5);
            level.addFreshEntity(cart);
            var boat = EntityType.OAK_BOAT.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            boat.setPos(ENT_MIN.getX() + 3.5, ENT_MIN.getY() + 1, ENT_MIN.getZ() + 3.5);
            level.addFreshEntity(boat);
        });
    }

    public static final BlockPos BE_MIN = new BlockPos(232, 64, 0);
    public static final BlockPos BE_MAX = BE_MIN.offset(10, 2, 2);

    private static void buildBlockEntities(TestSingleplayerContext sp) {
        Map<BlockPos, BlockState> m = new LinkedHashMap<>();
        for (int x = 0; x <= 10; x++) for (int z = 0; z <= 2; z++) m.put(new BlockPos(x, 0, z), Blocks.SMOOTH_STONE.defaultBlockState());
        m.put(new BlockPos(0, 1, 1), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH).setValue(ChestBlock.TYPE, ChestType.RIGHT));
        m.put(new BlockPos(1, 1, 1), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH).setValue(ChestBlock.TYPE, ChestType.LEFT));
        m.put(new BlockPos(2, 1, 0), BLUE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD));
        m.put(new BlockPos(2, 1, 1), BLUE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT));
        m.put(new BlockPos(3, 1, 1), Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 0));
        m.put(new BlockPos(4, 1, 0), Blocks.STONE_BRICKS.defaultBlockState());
        m.put(new BlockPos(4, 1, 1), Blocks.BIRCH_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.SOUTH));
        m.put(new BlockPos(5, 1, 1), YELLOW_BANNER.defaultBlockState());
        m.put(new BlockPos(6, 1, 1), Blocks.CREEPER_HEAD.defaultBlockState());
        m.put(new BlockPos(7, 1, 1), PURPLE_SHULKER.defaultBlockState());
        m.put(new BlockPos(8, 1, 1), Blocks.ENCHANTING_TABLE.defaultBlockState());
        m.put(new BlockPos(9, 1, 1), Blocks.BELL.defaultBlockState());
        m.put(new BlockPos(10, 1, 1), Blocks.DECORATED_POT.defaultBlockState());
        m.put(new BlockPos(8, 1, 2), Blocks.LECTERN.defaultBlockState());
        m.put(new BlockPos(9, 1, 2), Blocks.CAMPFIRE.defaultBlockState());
        sp.getServer().runOnServer(s -> {
            ServerLevel level = s.overworld();
            for (var e : m.entrySet()) {
                BlockPos pos = BE_MIN.offset(e.getKey());
                level.setBlock(pos, e.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
                    GT.setSignLine(sign, true, 1, Component.literal("Sign"));
                    GT.setSignLine(sign, true, 2, Component.literal("text"));
                }
            }
        });
    }

    private static void buildTower(TestSingleplayerContext sp) {
        sp.getServer().runOnServer(s -> {
            ServerLevel level = s.overworld();
            for (int y = 0; y <= 29; y++) for (int x = 0; x <= 4; x++) for (int z = 0; z <= 4; z++) {
                boolean wall = x == 0 || x == 4 || z == 0 || z == 4;
                BlockState st = !wall ? (y == 0 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState())
                        : (y % 6 == 3 && (x == 2 || z == 2)) ? Blocks.GLASS.defaultBlockState()
                        : (y % 5 == 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
                level.setBlock(TOWER_MIN.offset(x, y, z), st, Block.UPDATE_CLIENTS);
            }
            level.setBlock(TOWER_MIN.offset(2, 29, 2), Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        });
    }

    // ------------------------------------------------------------------ 写文件

    /** 把世界里 min..max 截取成 .litematic（不创建放置） */
    public static void capture(TestSingleplayerContext sp, Path dir, BlockPos min, BlockPos max, String name) {
        boolean written = sp.getServer().computeOnServer(server -> {
            fi.dy.masa.litematica.selection.AreaSelection area = new fi.dy.masa.litematica.selection.AreaSelection();
            area.setName(name);
            area.addSubRegionBox(new fi.dy.masa.litematica.selection.Box(min, max, name), false);
            area.setExplicitOrigin(min);
            var schematic = fi.dy.masa.litematica.schematic.LitematicaSchematic.createFromWorld(server.overworld(), area,
                    new fi.dy.masa.litematica.schematic.LitematicaSchematic.SchematicSaveInfo(false, false), "ALE", s -> GT.log("capture: " + s));
            return schematic != null && schematic.writeToFile(dir, name, true);
        });
        if (!written) throw new AssertionError("could not capture/write schematic " + name);
    }

    /** 原版结构文件（.nbt） */
    private static void saveStructure(TestSingleplayerContext sp, Path file, BlockPos min, BlockPos max) {
        sp.getServer().runOnServer(server -> {
            StructureTemplate template = new StructureTemplate();
            template.fillFromWorld(server.overworld(), min, max.subtract(min).offset(1, 1, 1), false, List.of(Blocks.STRUCTURE_VOID));
            template.setAuthor("ALE");
            try {
                NbtIo.writeCompressed(template.save(new CompoundTag()), file);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    private interface BlockAt {
        BlockState get(int x, int y, int z, int w, int h, int l);
    }

    /** 写一个 Sponge 第 2 版投影（WorldEdit 的 .schem） */
    private static void writeSponge(Path file, int w, int h, int l, BlockAt blocks) throws Exception {
        Map<String, Integer> palette = new LinkedHashMap<>();
        ByteArrayOutputStream data = new ByteArrayOutputStream(w * h * l);
        for (int y = 0; y < h; y++) for (int z = 0; z < l; z++) for (int x = 0; x < w; x++) {
            BlockState st = blocks.get(x, y, z, w, h, l);
            String key = stateString(st);
            int id = palette.computeIfAbsent(key, k -> palette.size());
            // varint
            int v = id;
            while ((v & ~0x7F) != 0) {
                data.write((v & 0x7F) | 0x80);
                v >>>= 7;
            }
            data.write(v);
        }
        CompoundTag root = new CompoundTag();
        root.putInt("Version", 2);
        root.putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
        root.putShort("Width", (short) w);
        root.putShort("Height", (short) h);
        root.putShort("Length", (short) l);
        CompoundTag pal = new CompoundTag();
        palette.forEach(pal::putInt);
        root.put("Palette", pal);
        root.putInt("PaletteMax", palette.size());
        root.putByteArray("BlockData", data.toByteArray());
        root.putIntArray("Offset", new int[]{0, 0, 0});
        NbtIo.writeCompressed(root, file);
    }

    private static String stateString(BlockState st) {
        StringBuilder sb = new StringBuilder(BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString());
        var props = st.getProperties();
        if (!props.isEmpty()) {
            sb.append('[');
            boolean first = true;
            for (var p : props) {
                if (!first) sb.append(',');
                first = false;
                sb.append(p.getName()).append('=').append(name(p, st.getValue(p)));
            }
            sb.append(']');
        }
        return sb.toString();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String name(net.minecraft.world.level.block.state.properties.Property p, Comparable v) {
        return p.getName(v);
    }

    /** 起伏的地形：石头、泥土、草，低处有水，零星的树 */
    private static BlockState terrain(int x, int y, int z, int w, int h, int l) {
        double hgt = 8 + 4 * Math.sin(x / 9.0) + 3 * Math.cos(z / 7.0) + 2 * Math.sin((x + z) / 13.0);
        int top = (int) hgt;
        if (y < top - 3) return Blocks.STONE.defaultBlockState();
        if (y < top) return Blocks.DIRT.defaultBlockState();
        if (y == top) return top < 7 ? Blocks.SAND.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
        if (y <= 7) return Blocks.WATER.defaultBlockState();
        boolean tree = top >= 9 && Math.floorMod(x * 31 + z * 17, 97) == 0;
        if (tree && y <= top + 4) return Blocks.OAK_LOG.defaultBlockState();
        return Blocks.AIR.defaultBlockState();
    }

    /** 最费网格的情况：三维棋盘格（每个方块的六个面都露在外面），用来测四边形上限和不卡死 */
    private static BlockState checkerboard(int x, int y, int z, int w, int h, int l) {
        return ((x + y + z) & 1) == 0 ? (y % 8 == 0 ? Blocks.GLASS : Blocks.STONE).defaultBlockState() : Blocks.AIR.defaultBlockState();
    }

    /** 很大但稀疏：只有地面一层和每 32 格一根柱子 */
    private static BlockState sparse(int x, int y, int z, int w, int h, int l) {
        if (y == 0) return ((x / 16 + z / 16) % 2 == 0 ? WHITE_CONCRETE : GRAY_CONCRETE).defaultBlockState();
        if (x % 32 == 5 && z % 32 == 5) return Blocks.QUARTZ_PILLAR.defaultBlockState();
        return Blocks.AIR.defaultBlockState();
    }
}
