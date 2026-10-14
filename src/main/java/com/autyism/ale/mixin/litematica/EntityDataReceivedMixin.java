package com.autyism.ale.mixin.litematica;

import com.autyism.ale.verifier.ServerContainerData;
import fi.dy.masa.litematica.data.EntityDataManager;
import fi.dy.masa.malilib.util.data.tag.CompoundData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 服务端发来的方块实体数据到了（Servux 或 OP 的备用查询）：容器的内容记给 ALE 的容器检查用（见 ServerContainerData） */
@Mixin(value = EntityDataManager.class, remap = false)
public abstract class EntityDataReceivedMixin {
    @Inject(method = "handleBlockEntityData", at = @At("RETURN"))
    private void ale$noteServerContainer(BlockPos pos, CompoundData data, CallbackInfoReturnable<BlockEntity> cir) {
        ServerContainerData.received(pos, cir.getReturnValue());
    }
}
