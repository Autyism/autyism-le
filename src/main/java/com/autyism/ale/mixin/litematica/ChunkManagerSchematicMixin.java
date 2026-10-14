package com.autyism.ale.mixin.litematica;

import fi.dy.masa.litematica.world.ChunkManagerSchematic;
import fi.dy.masa.litematica.world.ChunkSchematic;
import fi.dy.masa.litematica.world.ChunkSchematicState;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Litematica 查投影区块状态时先看“有没有”再去取：中间投影区块被后台线程卸载了，取到的是 null，
 * 渲染线程就抛空指针、游戏直接崩掉（投影频繁重建时偶尔碰上）。这里只取一次：没取到就当作没载入。
 */
@Mixin(value = ChunkManagerSchematic.class, remap = false)
public abstract class ChunkManagerSchematicMixin {
    @Shadow
    @Final
    private ConcurrentHashMap<Long, ChunkSchematic> loadedChunks;

    @Inject(method = "getChunkState", at = @At("HEAD"), cancellable = true)
    private void ale$getChunkStateSafely(int chunkX, int chunkZ, CallbackInfoReturnable<ChunkSchematicState> cir) {
        ChunkSchematic chunk = this.loadedChunks.get(ChunkPos.asLong(chunkX, chunkZ));
        cir.setReturnValue(chunk == null ? ChunkSchematicState.UNLOADED : chunk.getState());
    }
}
