package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.mecha.ParkedLook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/**
 * Iris, looked up by name so that it stays optional: whether a shader pack is on, whether its shadow pass is being
 * drawn, and whether parked machines may be drawn from kept geometry under the pack in use. They may only under an Iris
 * and a pack they were checked with ({@link ParkedLook.ShaderPacks}); anything not found, not known or failing is the
 * ordinary drawing. What is relied on, read in Iris 1.8.12: a vertex buffer of the game's entity layout is built in the
 * pack's longer layout while a pack is on (so what is kept is what the ordinary path writes), the game's entity
 * programs are the pack's while it is on (its shadow programs in the shadow pass), and the pack reads every entity's
 * own values (its id, its hurt flash) from the vertices.
 */
final class IrisParked {
    private static final String PROGRAM = "net.irisshaders.iris.pipeline.programs.ExtendedShader";

    private static boolean looked, present, known;
    private static Object api;
    private static Method inUse, shadowPass, packName, original;
    private static Class<?> wrapper;
    private static String version;

    private IrisParked() {
    }

    private static void look() {
        if (looked) return;
        looked = true;
        try {
            Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = type.getMethod("getInstance").invoke(null);
            inUse = type.getMethod("isShaderPackInUse");
            shadowPass = type.getMethod("isRenderingShadowPass");
            present = api != null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            present = false;
            return;
        }
        try {
            version = ModList.get().getModContainerById("iris").map(c -> c.getModInfo().getVersion().toString()).orElse(null);
            if (!ParkedLook.ShaderPacks.knownIris(version)) {
                HeadInAJar.LOGGER.info("Iris {} has not been checked with parked machines: under a shader pack they are drawn the ordinary way", version);
                return;
            }
            packName = Class.forName("net.irisshaders.iris.Iris").getMethod("getCurrentPackName");
            wrapper = Class.forName("net.irisshaders.iris.layer.BufferSourceWrapper");
            original = wrapper.getMethod("getOriginal");
            Class.forName(PROGRAM);
            known = true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            known = false;
            HeadInAJar.LOGGER.info("Iris {} is not as expected: under a shader pack parked machines are drawn the ordinary way", version);
        }
    }

    /** An Iris shader pack is on (an Iris that cannot be asked counts as one: the ordinary drawing). */
    static boolean packInUse() {
        look();
        if (!present) return false;
        try {
            return (boolean) inUse.invoke(api);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return true;
        }
    }

    static boolean shadowPass() {
        look();
        if (!present) return false;
        try {
            return (boolean) shadowPass.invoke(api);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** The pack in use when it is one parked machines may be kept under with this Iris, else null. */
    static String knownPack() {
        look();
        if (!known) return null;
        try {
            Object name = packName.invoke(null);
            return name instanceof String s && ParkedLook.ShaderPacks.knownPack(s) ? s : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** The level's own buffers in the main view, as Iris hands them to an entity (wrapped to mark them as an entity's). */
    static boolean levelBuffers(MultiBufferSource buffers, Minecraft mc) {
        MultiBufferSource level = mc.renderBuffers().bufferSource();
        if (buffers == level) return true;
        if (!known || !wrapper.isInstance(buffers)) return false;
        try {
            return original.invoke(buffers) == level;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** The shadow pass's own buffers (Iris's, wrapped or not). */
    static boolean shadowBuffers(MultiBufferSource buffers) {
        return known && buffers != null && buffers.getClass().getName().startsWith("net.irisshaders.");
    }

    /** A program of the pack (not the game's own, which would draw kept vertices of the pack's layout wrongly). */
    static boolean packProgram(ShaderInstance shader) {
        return shader != null && shader.getClass().getName().equals(PROGRAM);
    }

    /** The pack's programs stand in for the game's three entity programs the kept geometry is drawn with. */
    static boolean entityPrograms() {
        return packProgram(GameRenderer.getRendertypeEntityCutoutShader()) && packProgram(GameRenderer.getRendertypeEntityCutoutNoCullShader())
                && packProgram(GameRenderer.getRendertypeEntityTranslucentCullShader());
    }

    static String describe() {
        look();
        if (!present) return "";
        if (!packInUse()) return ", Iris " + version + " without a shader pack";
        if (!known) return ", shader pack on under Iris " + version + " (not checked with this Iris: ordinary drawing)";
        String name;
        try {
            name = String.valueOf(packName.invoke(null));
        } catch (ReflectiveOperationException | RuntimeException e) {
            name = "?";
        }
        return ParkedLook.ShaderPacks.knownPack(name) ? ", shader pack " + name + " (kept, main view and shadows)"
                : ", shader pack " + name + " (not checked: ordinary drawing)";
    }
}
