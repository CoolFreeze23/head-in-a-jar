package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.AssemblyGantryBlockEntity;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.Wear;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A damaged frame's dents ({@link Wear}): its own sheet with the dent tiles of each side at that side's stage laid over
 * it (the tiles are {@code mecha/dents/<frame>.png}: the sheet's width, twelve sheets tall, side x 3 + stage - 1), made
 * once per frame and look the first time a machine shows it and kept as a texture of its own. Drawing a dented machine
 * then costs exactly what drawing a whole one does: the same model with a different sheet bound.
 * <p>
 * On the Gantry the dents go as the plates go on: the server mends at the press, and here the stages step down over the
 * repair pass the arm is seen making, from what was shown before it.
 */
public final class MechaDents {
    /** Sheets made, by frame (or shell) and the four sides' stages packed ({@link Wear#packed}). */
    private static final Map<String, Map<Integer, ResourceLocation>> SHEETS = new HashMap<>();
    /** What each machine shows now, and the repair pass it is stepping down through. */
    private static final Map<MechaEntity, Shown> SHOWN = new WeakHashMap<>();
    /** Dented sheets made since the game started, and the time spent making them (for the harness). */
    static int made;
    static long madeNanos;

    private static final class Shown {
        int[] stages = new int[4];
        int[] from;
        long job = Long.MIN_VALUE;
        /** When the health was first seen mended with no repair pass in sight yet (its block update may come a tick later). */
        double mendedAt = Double.NaN;
    }

    private MechaDents() {
    }

    /** The sheet to draw this machine with: its frame's own, or that sheet dented as it is now. */
    public static ResourceLocation sheet(MechaEntity m, ResourceLocation own) {
        return sheet(m.frame().id(), own, Wear.packed(shown(m)));
    }

    /**
     * A Utility Shell's sheet: its own, or dented as its health and remembered blows call for (the blows come on the
     * optional shell_dents channel; on a server without it a shell has none and shows none). No Gantry steps a shell's
     * dents down: they go when it is whole.
     */
    public static ResourceLocation sheet(com.coolfreeze23.headinajar.entity.ShellEntity s, ResourceLocation own) {
        return sheet(shellId(s.kind()), own, Wear.packed(Wear.stages(s.hitBearings(), s.getHealth(), s.getMaxHealth())));
    }

    static String shellId(com.coolfreeze23.headinajar.entity.ShellEntity.Kind kind) {
        return kind.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static ResourceLocation sheet(String id, ResourceLocation own, int packed) {
        if (packed == 0) return own;
        return SHEETS.computeIfAbsent(id, f -> new HashMap<>()).computeIfAbsent(packed, p -> make(id, own, p));
    }

    /** The four sides' stages as drawn now (front, right, back, left). */
    public static int[] shown(MechaEntity m) {
        int[] target = Wear.stages(m.hitBearings(), m.getHealth(), m.getMaxHealth());
        Shown s = SHOWN.get(m);
        if (s == null) {
            if (Wear.packed(target) == 0) return target;
            s = new Shown();
            s.stages = target;
            SHOWN.put(m, s);
            return target;
        }
        double now = m.level().getGameTime() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        boolean mended = false;
        for (int i = 0; i < 4; i++) mended |= target[i] < s.stages[i];
        AssemblyGantryBlockEntity g = repairing(m);
        if (g != null) {
            if (s.job != g.jobStart()) {
                s.job = g.jobStart();
                s.from = s.stages.clone();
            }
            // the welds run from a sixth of the pass to its last tenth: the stages step down evenly over them
            double len = Math.max(1, g.jobLength());
            double progress = Math.clamp((now - g.jobStart() - len / 6) / (len * (1 - 1 / 6.0 - 0.1)), 0, 1);
            int[] out = new int[4];
            for (int i = 0; i < 4; i++) {
                int from = Math.max(s.from[i], target[i]);
                out[i] = target[i] >= from ? target[i] : from - (int) Math.floor((from - target[i]) * progress + 1e-6);
            }
            s.stages = out;
            s.mendedAt = Double.NaN;
            return out;
        }
        if (mended && s.job == Long.MIN_VALUE) {
            // mended, and the Gantry's pass may not have arrived yet: hold what was shown for half a second
            if (Double.isNaN(s.mendedAt)) s.mendedAt = now;
            if (now - s.mendedAt < 10) return s.stages;
        }
        s.job = Long.MIN_VALUE;
        s.mendedAt = Double.NaN;
        s.stages = target;
        if (Wear.packed(target) == 0) SHOWN.remove(m);
        return target;
    }

    /** The Gantry whose deck the machine stands on, while it runs a repair pass. */
    static AssemblyGantryBlockEntity repairing(MechaEntity m) {
        if (m.isVehicle() || m.level() == null) return null;
        if (!(m.level().getBlockEntity(m.blockPosition().below()) instanceof AssemblyGantryBlockEntity g)) return null;
        if (g.job() != AssemblyGantryBlockEntity.JOB_REPAIR) return null;
        var deck = g.deck();
        if (Math.abs(m.getX() - deck.x) > 0.6 || Math.abs(m.getZ() - deck.z) > 0.6) return null;
        return g;
    }

    /** A frame's sheet as read (its pixels) and its dent tiles as the few pixels each changes: read once per frame. */
    private record Source(int w, int h, int[] sheet, int[][] tiles) {
    }

    private static final Map<String, Source> SOURCES = new HashMap<>();

    private static Source source(String frame, ResourceLocation own) {
        var mc = Minecraft.getInstance();
        try (var in = mc.getResourceManager().open(own); NativeImage sheet = NativeImage.read(in);
             var tin = mc.getResourceManager().open(HeadInAJar.id("mecha/dents/" + frame + ".png")); NativeImage dents = NativeImage.read(tin)) {
            int w = sheet.getWidth(), h = sheet.getHeight();
            if (dents.getWidth() != w || dents.getHeight() != h * 12) {
                HeadInAJar.LOGGER.warn("The dents of {} do not fit its sheet ({}x{} for {}x{})", frame, dents.getWidth(), dents.getHeight(), w, h);
                return null;
            }
            int[] pixels = new int[w * h];
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) pixels[y * w + x] = sheet.getPixelRGBA(x, y);
            int[][] tiles = new int[12][];
            for (int t = 0; t < 12; t++) {
                // each changed pixel as its index and colour, side by side
                int[] buf = new int[64];
                int n = 0;
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int c = dents.getPixelRGBA(x, y + t * h);
                        if (c >>> 24 == 0) continue;
                        if (n + 2 > buf.length) buf = java.util.Arrays.copyOf(buf, buf.length * 2);
                        buf[n++] = y * w + x;
                        buf[n++] = c;
                    }
                }
                tiles[t] = java.util.Arrays.copyOf(buf, n);
            }
            return new Source(w, h, pixels, tiles);
        } catch (Exception e) {
            HeadInAJar.LOGGER.warn("Could not read the dents of {}", frame, e);
            return null;
        }
    }

    private static ResourceLocation make(String frame, ResourceLocation own, int packed) {
        long start = System.nanoTime();
        Source src = SOURCES.computeIfAbsent(frame, f -> source(f, own));
        if (src == null) return own;
        int w = src.w();
        NativeImage out = new NativeImage(w, src.h(), false);
        int[] px = src.sheet().clone();
        for (int side = 0; side < 4; side++) {
            int stage = packed >> (2 * side) & 3;
            if (stage == 0) continue;
            int[] tile = src.tiles()[side * 3 + stage - 1];
            for (int i = 0; i < tile.length; i += 2) px[tile[i]] = tile[i + 1];
        }
        for (int i = 0; i < px.length; i++) out.setPixelRGBA(i % w, i / w, px[i]);
        ResourceLocation at = HeadInAJar.id("dynamic/dents/" + frame + "_" + packed);
        Minecraft.getInstance().getTextureManager().register(at, new DynamicTexture(out));
        made++;
        madeNanos += System.nanoTime() - start;
        return at;
    }

    /** For the harness: what a machine shows, and what the dented sheets cost to make. */
    public static String describe(com.coolfreeze23.headinajar.entity.ShellEntity s) {
        int[] st = Wear.stages(s.hitBearings(), s.getHealth(), s.getMaxHealth());
        return String.format(java.util.Locale.ROOT, "dents front %d right %d back %d left %d (health %.1f / %.1f, bearings %016x), %d sheets made in %.1f ms",
                st[0], st[1], st[2], st[3], s.getHealth(), s.getMaxHealth(), s.hitBearings(), made, madeNanos / 1e6);
    }

    public static String describe(MechaEntity m) {
        int[] s = shown(m);
        return String.format(java.util.Locale.ROOT, "dents front %d right %d back %d left %d (health %.1f / %.1f, bearings %016x), %d sheets made in %.1f ms",
                s[0], s[1], s[2], s[3], m.getHealth(), m.getMaxHealth(), m.hitBearings(), made, madeNanos / 1e6);
    }

    static void clear() {
        var textures = Minecraft.getInstance().getTextureManager();
        for (Map<Integer, ResourceLocation> byLook : SHEETS.values()) {
            for (ResourceLocation at : byLook.values()) if (at.getPath().startsWith("dynamic/")) textures.release(at);
        }
        SHEETS.clear();
        SOURCES.clear();
    }

    @EventBusSubscriber(modid = HeadInAJar.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {
        }

        /**
         * New sheets or dents: every dented sheet is made again from them, and each frame's are read now, while the game
         * loads, so the first dent seen in play costs only its own sheet.
         */
        @SubscribeEvent
        public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> RenderSystem.recordRenderCall(() -> {
                clear();
                for (MechaFrame frame : MechaFrame.values()) {
                    if (!frame.animated()) continue;
                    Source s = source(frame.id(), HeadInAJar.id("textures/entity/mecha/" + frame.id() + ".png"));
                    if (s != null) SOURCES.put(frame.id(), s);
                }
                for (var kind : com.coolfreeze23.headinajar.entity.ShellEntity.Kind.values()) {
                    Source s = source(shellId(kind), HeadInAJar.id("textures/entity/shell/" + shellId(kind) + ".png"));
                    if (s != null) SOURCES.put(shellId(kind), s);
                }
            }));
        }
    }
}
