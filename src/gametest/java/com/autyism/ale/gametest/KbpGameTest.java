package com.autyism.ale.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 真实实例检查 KeyBind Profiles+（用户自己的另一个模组）：Meteor、malilib 系、IPN 的热键都能在 KBP+ 里看到并修改，
 * 改动由那个模组自己存进它的配置文件。每个模组挑一个热键改成 F19，确认生效和存盘，再改回原值。
 * 没装 KBP+ 时跳过（开发环境）。
 */
@SuppressWarnings("UnstableApiUsage")
public final class KbpGameTest implements FabricClientGameTest {
    private static final String KEYS = "io.github.autyi6969.keybindprofilesplus.external.ExternalKeys";

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GTFilter.enabled("kbp")) return;
        if (!FabricLoader.getInstance().isModLoaded("keybindprofilesplus")) {
            GT.log("[kbp] KeyBind Profiles+ not loaded, skipped");
            return;
        }
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            context.waitTicks(40);
            List<Object> all = context.computeOnClient(c -> list());
            Map<String, int[]> perSource = new TreeMap<>(); // source -> {total, editable}
            Map<String, Object> pick = new LinkedHashMap<>();
            for (Object b : all) {
                String src = (String) call(b, "sourceId");
                int[] n = perSource.computeIfAbsent(src, k -> new int[2]);
                n[0]++;
                if ((boolean) call(b, "editable")) {
                    n[1]++;
                    // 每个模组挑一个“已绑定、改了也无害”的热键（Meteor 挑一个模块键）
                    if (!pick.containsKey(src) && call(b, "value") != null && !(boolean) call(b, "unbound")) pick.put(src, b);
                }
            }
            perSource.forEach((k, v) -> GT.log("[kbp] source " + k + ": " + v[0] + " hotkeys, " + v[1] + " editable"));
            List<String> problems = new ArrayList<>();
            for (String must : List.of("meteor", "litematica", "tweakeroo", "minihud", "itemscroller", "litematica-printer-autyism", "autyism-le")) {
                if (!perSource.containsKey(must) || perSource.get(must)[1] == 0) problems.add("no editable hotkeys from " + must);
            }
            for (var e : pick.entrySet()) {
                Object b = e.getValue();
                String id = (String) call(b, "hotkeyId");
                String original = (String) call(b, "value");
                Path file = FabricLoader.getInstance().getGameDir().resolve((String) call(b, "file"));
                long before = Files.exists(file) ? file.toFile().lastModified() : 0;
                context.waitTicks(25); // 文件时间精度
                boolean ok = context.computeOnClient(c -> (boolean) callStatic("bind", b, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F19), 0));
                context.waitTicks(10);
                Object now = context.computeOnClient(c -> callStatic("find", id));
                String newValue = now == null ? null : (String) call(now, "value");
                long after = Files.exists(file) ? file.toFile().lastModified() : 0;
                String text = now == null ? "?" : call(now, "keyText").toString();
                GT.log("[kbp] " + e.getKey() + " " + call(b, "name") + ": " + original + " -> " + newValue + " (" + text + ") bind=" + ok
                        + " file " + file.getFileName() + (after > before ? " saved" : " NOT saved"));
                if (!ok || newValue == null || newValue.equals(original)) problems.add(e.getKey() + ": rebind did not take");
                if (after <= before) problems.add(e.getKey() + ": " + file.getFileName() + " not saved");
                boolean restored = context.computeOnClient(c -> (boolean) callStatic("setValue", callStatic("find", id), original));
                Object back = context.computeOnClient(c -> callStatic("find", id));
                if (!restored || back == null || !original.equals(call(back, "value"))) problems.add(e.getKey() + ": restore failed");
            }
            for (String p : problems) GT.log("[kbp] PROBLEM " + p);
            if (!problems.isEmpty()) throw new AssertionError("[kbp] " + problems);
            GT.log("[kbp] OK: " + pick.size() + " mods rebound, saved by their own code and restored");
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list() {
        callStatic("refresh");
        return new ArrayList<>((List<Object>) callStatic("all"));
    }

    private static Object call(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            return m.invoke(target);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static Object callStatic(String method, Object... args) {
        try {
            for (Method m : Class.forName(KEYS).getMethods()) {
                if (m.getName().equals(method) && m.getParameterCount() == args.length) return m.invoke(null, args);
            }
            throw new NoSuchMethodException(method);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}
