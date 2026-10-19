package com.autyism.ale.render;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Whether Litematica's own drawing is routed past the shader pack this frame (LitematicaPass, or Litefastica through
 * EXIT). They draw the schematic into a target of their own and lay it over the finished frame, which also keeps it
 * visible behind glass. Anything ALE draws early from the level pass would land in the pack's frame instead and be
 * lost (the error markers disappeared under IterationRP), so ALE leaves the drawing to them then.
 */
public final class ShaderRouting {
    private static final boolean ROUTER = FabricLoader.getInstance().isModLoaded("litematicapass")
            || FabricLoader.getInstance().isModLoaded("litematicafastmove");
    private static final MethodHandle PACK_IN_USE = ROUTER && FabricLoader.getInstance().isModLoaded("iris") ? packInUse() : null;

    private ShaderRouting() {
    }

    public static boolean routesLitematica() {
        if (PACK_IN_USE == null) return false;
        try {
            return (boolean) PACK_IN_USE.invokeExact();
        } catch (Throwable e) {
            return false;
        }
    }

    // Iris' public API, which every Iris version has; looked up by name so ALE does not depend on Iris
    private static MethodHandle packInUse() {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return MethodHandles.publicLookup()
                    .findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class))
                    .bindTo(instance);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }
}
