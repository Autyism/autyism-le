package com.autyism.ale.diff;

import com.autyism.ale.AleMod;
import com.autyism.ale.preview.FullscreenPreviewScreen;
import com.autyism.ale.preview.PreviewSession;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.Box;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The difference between two versions in ALE's 3D preview: a temporary schematic holding only the blocks that differ,
 * added and changed ones as the newer version has them, removed ones as red glass. Built on a worker thread; the
 * preview opens when it is ready.
 */
public final class DiffPreview {
    private static final String NAME = "ale-diff-preview";

    private DiffPreview() {
    }

    public static void open(@Nullable Screen parent) {
        DiffSession session = DiffSession.current();
        SchematicDiff.Result r = session == null ? null : session.result();
        if (r == null || r.size() == 0) return;
        Minecraft mc = Minecraft.getInstance();
        Path dir = mc.gameDirectory.toPath().resolve("config").resolve("autyism-le");
        Thread worker = new Thread(() -> {
            Path file = build(session, r, dir);
            if (file == null) return;
            mc.execute(() -> {
                PreviewSession.closeCurrent();
                mc.setScreen(new FullscreenPreviewScreen(parent, PreviewSession.forFile(file)));
            });
        }, "ALE diff preview");
        worker.setDaemon(true);
        worker.start();
    }

    @Nullable
    private static Path build(DiffSession session, SchematicDiff.Result r, Path dir) {
        try {
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (int i = 0; i < r.size(); i++) {
                BlockPos p = BlockPos.of(r.positions()[i]);
                minX = Math.min(minX, p.getX());
                minY = Math.min(minY, p.getY());
                minZ = Math.min(minZ, p.getZ());
                maxX = Math.max(maxX, p.getX());
                maxY = Math.max(maxY, p.getY());
                maxZ = Math.max(maxZ, p.getZ());
            }
            AreaSelection area = new AreaSelection();
            area.setName(NAME);
            area.addSubRegionBox(new Box(new BlockPos(0, 0, 0), new BlockPos(maxX - minX, maxY - minY, maxZ - minZ), NAME), false);
            LitematicaSchematic schematic = LitematicaSchematic.createEmptySchematic(area, "ALE");
            LitematicaBlockStateContainer container = schematic.getSubRegionContainer(NAME);
            if (container == null) return null;
            BlockState removed = Blocks.RED_STAINED_GLASS.defaultBlockState();
            for (int i = 0; i < r.size(); i++) {
                BlockPos p = BlockPos.of(r.positions()[i]);
                int kind = session.shownKind(r.kinds()[i]);
                BlockState state = kind == DiffSession.REMOVED ? removed : session.newState(i);
                if (state.isAir()) state = removed;
                container.set(p.getX() - minX, p.getY() - minY, p.getZ() - minZ, state);
            }
            Files.createDirectories(dir);
            if (!schematic.writeToFile(dir, NAME, true)) return null;
            return dir.resolve(NAME + LitematicaSchematic.FILE_EXTENSION);
        } catch (Throwable t) {
            AleMod.LOGGER.warn("Could not build the 3D preview of the difference", t);
            return null;
        }
    }
}
