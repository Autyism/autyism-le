package com.autyism.ale.mixin.litematica;

import com.autyism.ale.render.TranslucentEntityRender;
import fi.dy.masa.litematica.render.schematic.WorldRendererSchematic;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 需求 5b：Litematica 提交投影实体 / 方块实体时，把提交队列换成半透明代理。
 */
@Mixin(value = WorldRendererSchematic.class, remap = false)
public abstract class WorldRendererSchematicMixin {
    @ModifyVariable(method = "renderEntities", at = @At("HEAD"), argsOnly = true)
    private SubmitNodeCollector ale$wrapEntityQueue(SubmitNodeCollector queue) {
        if (!TranslucentEntityRender.enabledForEntities()) return queue;
        TranslucentEntityRender.ACTIVE.set(true);
        return TranslucentEntityRender.wrap(queue);
    }

    @Inject(method = "renderEntities", at = @At("RETURN"))
    private void ale$endEntities(CallbackInfo ci) {
        TranslucentEntityRender.ACTIVE.set(false);
    }

    @ModifyVariable(method = "renderBlockEntities", at = @At("HEAD"), argsOnly = true)
    private SubmitNodeCollector ale$wrapBlockEntityQueue(SubmitNodeCollector queue) {
        if (!TranslucentEntityRender.enabledForBlockEntities()) return queue;
        TranslucentEntityRender.ACTIVE.set(true);
        return TranslucentEntityRender.wrap(queue);
    }

    @Inject(method = "renderBlockEntities", at = @At("RETURN"))
    private void ale$endBlockEntities(CallbackInfo ci) {
        TranslucentEntityRender.ACTIVE.set(false);
    }
}
