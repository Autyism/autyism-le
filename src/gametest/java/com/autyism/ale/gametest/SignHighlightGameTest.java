package com.autyism.ale.gametest;

import com.autyism.ale.verifier.SignHighlight;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 验证器界面的“高亮告示牌”：投影范围里有字的告示牌透墙标出来，已放置亮绿、投影里有但没放的深绿，没字的不标。
 * 放下、敲掉告示牌后一秒内跟上；关掉开关就不画。截图里告示牌在一堵墙后面。
 */
@SuppressWarnings("UnstableApiUsage")
public final class SignHighlightGameTest implements FabricClientGameTest {
    private static final int X0 = 460, Y = 64, Z = 40;

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GTFilter.enabled("signs")) return;
        BlockPos withText = new BlockPos(X0, Y, Z);
        BlockPos notPlaced = new BlockPos(X0 + 3, Y, Z);
        BlockPos blank = new BlockPos(X0 + 6, Y, Z);
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            GT.clearArena(sp, X0 - 6, Z - 8, X0 + 12, Z + 10, 80);
            ViewShots.begin(context, sp);
            // 先站过去：Litematica 只载入玩家附近的投影区块
            sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f %d %.2f 180 15", X0 + 3.5, Y + 2, Z + 8.5));
            context.waitTicks(20);
            // 投影的来源：三块告示牌，两块有字
            sp.getServer().runOnServer(s -> {
                ServerLevel level = s.overworld();
                for (BlockPos p : List.of(withText, notPlaced, blank)) level.setBlock(p, Blocks.OAK_SIGN.defaultBlockState(), ViewShots.QUIET);
                setText(level, withText, "fill here");
                setText(level, notPlaced, "hopper", "64 iron");
            });
            context.waitTicks(5);
            SchematicPlacement placement = GT.captureAndPlace(context, sp, withText, blank, withText, "ale_signs");
            GT.waitSchematicBlock(context, notPlaced, Blocks.OAK_SIGN);
            // 世界里只留第一块；镜头和告示牌之间一堵墙
            sp.getServer().runOnServer(s -> {
                ServerLevel level = s.overworld();
                level.setBlock(notPlaced, Blocks.AIR.defaultBlockState(), ViewShots.QUIET);
                level.setBlock(blank, Blocks.AIR.defaultBlockState(), ViewShots.QUIET);
                for (int x = X0 - 4; x <= X0 + 10; x++) {
                    for (int y = Y; y <= Y + 3; y++) level.setBlock(new BlockPos(x, y, Z + 2), Blocks.STONE.defaultBlockState(), ViewShots.QUIET);
                }
            });
            sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f %d %.2f 180 15", X0 + 3.5, Y + 2, Z + 8.5));
            context.waitTicks(20);
            context.runOnClient(c -> DataManager.getSchematicPlacementManager().setSelectedSchematicPlacement(placement));

            List<String> failures = new ArrayList<>();
            GT.shot(context, "ale-signs-off");
            context.runOnClient(c -> {
                if (!SignHighlight.enabled) SignHighlight.toggle();
            });
            context.waitTicks(25);
            GT.log("[signs] diag: " + context.computeOnClient(c -> {
                var sel = DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement();
                var box = sel == null ? null : sel.getEclosingBox();
                var ws = fi.dy.masa.litematica.world.SchematicWorldHandler.getSchematicWorld();
                return "selected=" + (sel != null) + " box=" + (box == null ? null : box.getPos1() + ".." + box.getPos2())
                        + " schematicHasChunk=" + (ws != null && ws.hasChunk(withText.getX() >> 4, withText.getZ() >> 4))
                        + " schematicBE=" + (ws == null ? null : ws.getBlockEntity(notPlaced))
                        + " worldBE=" + c.level.getBlockEntity(withText) + " enabled=" + SignHighlight.enabled;
            }));
            check(context, "on", Set.of(withText), Set.of(notPlaced), failures);
            GT.shot(context, "ale-signs-on");

            // 没放的那块放上（世界里这块还没写字）：投影里它有字，算已放置
            sp.getServer().runOnServer(s -> s.overworld().setBlock(notPlaced, Blocks.OAK_SIGN.defaultBlockState(), ViewShots.QUIET));
            context.waitTicks(25);
            check(context, "after placing", Set.of(withText, notPlaced), Set.of(), failures);

            // 敲掉第一块：变成没放
            sp.getServer().runOnServer(s -> s.overworld().setBlock(withText, Blocks.AIR.defaultBlockState(), ViewShots.QUIET));
            context.waitTicks(25);
            check(context, "after breaking", Set.of(notPlaced), Set.of(withText), failures);
            GT.shot(context, "ale-signs-after");

            context.runOnClient(c -> SignHighlight.toggle());
            context.waitTicks(5);
            boolean cleared = context.computeOnClient(c -> !SignHighlight.enabled && SignHighlight.placed().isEmpty() && SignHighlight.missing().isEmpty());
            GT.log("[signs] off: " + (cleared ? "nothing drawn" : "STILL DRAWN"));
            if (!cleared) failures.add("off");

            if (!failures.isEmpty()) throw new AssertionError("[signs] wrong: " + failures);
            GT.log("[signs] OK: signs with text marked (placed / not placed), blank sign ignored, follows placing and breaking, off clears");
            ViewShots.end(context);
        } finally {
            context.runOnClient(c -> {
                if (SignHighlight.enabled) SignHighlight.toggle();
            });
            ViewShots.end(context);
            GT.removeAllPlacements(context);
        }
    }

    private static void check(ClientGameTestContext context, String step, Set<BlockPos> wantPlaced, Set<BlockPos> wantMissing, List<String> failures) {
        Set<BlockPos> placed = context.computeOnClient(c -> Set.copyOf(SignHighlight.placed()));
        Set<BlockPos> missing = context.computeOnClient(c -> Set.copyOf(SignHighlight.missing()));
        boolean ok = placed.equals(wantPlaced) && missing.equals(wantMissing);
        GT.log("[signs] " + step + ": placed=" + placed + " missing=" + missing + (ok ? " ok" : " WRONG (want placed=" + wantPlaced + " missing=" + wantMissing + ")"));
        if (!ok) failures.add(step);
    }

    private static void setText(ServerLevel level, BlockPos pos, String... lines) {
        if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) return;
        SignText text = new SignText();
        for (int i = 0; i < lines.length; i++) text = text.setMessage(i, Component.literal(lines[i]));
        sign.setText(text, true);
        sign.setChanged();
        level.sendBlockUpdated(pos, sign.getBlockState(), sign.getBlockState(), 3);
    }
}
