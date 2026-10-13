package com.autyism.ale.mixin.litematica;

import fi.dy.masa.litematica.scheduler.tasks.TaskBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** 验证器信息栏（HUD）的文字行 */
@Mixin(value = TaskBase.class, remap = false)
public interface TaskBaseAccessor {
    @Accessor("infoHudLines")
    List<String> ale$getInfoHudLines();
}
