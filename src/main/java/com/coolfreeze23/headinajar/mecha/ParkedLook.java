package com.coolfreeze23.headinajar.mecha;

import com.coolfreeze23.headinajar.entity.MechaEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;

/**
 * What a parked frame looks like, apart from where it stands. A parked machine (nobody aboard, nothing happening to it)
 * shows one settled pose, so a client may draw it from geometry kept since it last changed; this says when it is parked
 * and what its look depends on, so the kept geometry is used exactly while the look is the same and rebuilt the moment
 * any of it changes: the fitted modules, its light, its facing, the battery's lamps, its Gene Ports' vials, the Chimera's
 * grafted veins, its dents, and the shader pack it is drawn under. Nothing here is saved or sent.
 */
public final class ParkedLook {
    private ParkedLook() {
    }

    /** The machine shows its settled parked pose now: nobody aboard, not building, getting out, hurt, venting or dying. */
    public static boolean parked(MechaEntity m, double now) {
        return m.frame().animated() && m.isAlive() && !m.isInvisible() && HopperAnimation.still(m, now)
                && m.portsResting() == m.view.restingSeen && !pulsing(m, now);
    }

    /** A Gene Port vial lit by a pulse (it fades over eight ticks): drawn as it fades, not kept. */
    static boolean pulsing(MechaEntity m, double now) {
        for (double at : m.view.pulseAt) if (now - at >= 0 && now - at < 8) return true;
        return false;
    }

    /** The light the game draws a mob in (block and sky light at its feet, packed as the renderer packs them). */
    public static int light(MechaEntity m) {
        BlockPos pos = BlockPos.containing(m.getLightProbePosition(1));
        int block = m.isOnFire() ? 15 : m.level().getBrightness(LightLayer.BLOCK, pos);
        int sky = m.level().getBrightness(LightLayer.SKY, pos);
        return block << 4 | sky << 20;
    }

    /** How many of the battery rack's three level lamps are lit at this charge. */
    public static int lamps(MechaEntity m) {
        return (int) Math.ceil(3.0 * m.battery() / Math.max(1, m.batteryCapacity()));
    }

    /**
     * Everything the parked look depends on. {@code light} and {@code overlay} are what the renderer is given; {@code yaw},
     * {@code roll} and {@code pitch} the machine's facing as drawn; {@code extra} whatever the client's own module motion
     * still holds (the drill's resting angle, the jets' spent lamps); {@code pack} the shader pack it is drawn under (null:
     * none), since a pack writes more into each vertex than the game does (its entity ids among it) and in another layout.
     */
    public record Key(Object modules, MechaFrame frame, int light, int overlay, float yaw, float roll, float pitch, float scale, int lamps,
                      long ports, int graft, int extra, int dents, String pack) {
        /** The same look: equal in everything, the angles within a thousandth of a degree. */
        public boolean matches(Key o) {
            return o != null && modules == o.modules && frame == o.frame && light == o.light && overlay == o.overlay && close(yaw, o.yaw)
                    && close(roll, o.roll) && close(pitch, o.pitch) && scale == o.scale && lamps == o.lamps && ports == o.ports && graft == o.graft
                    && extra == o.extra && dents == o.dents && java.util.Objects.equals(pack, o.pack);
        }

        /** The first thing that differs from another look, by name (for the harness's figures), or null if the same. */
        public String differs(Key o) {
            if (o == null) return "none kept";
            if (!java.util.Objects.equals(pack, o.pack)) return "shader pack";
            if (modules != o.modules) return "modules";
            if (frame != o.frame) return "frame";
            if (light != o.light) return "light";
            if (overlay != o.overlay) return "overlay";
            if (!close(yaw, o.yaw) || !close(roll, o.roll) || !close(pitch, o.pitch)) return "facing";
            if (scale != o.scale) return "scale";
            if (lamps != o.lamps) return "lamps";
            if (ports != o.ports) return "gene ports";
            if (graft != o.graft) return "graft";
            if (dents != o.dents) return "dents";
            return extra != o.extra ? "client motion or name" : null;
        }

        private static boolean close(float a, float b) {
            return Math.abs(net.minecraft.util.Mth.wrapDegrees(a - b)) < 1e-3f;
        }
    }

    public static Key key(MechaEntity m, int light, int overlay, float yaw, float roll, float pitch, int extra) {
        return key(m, light, overlay, yaw, roll, pitch, extra, Wear.packed(m), null);
    }

    public static Key key(MechaEntity m, int light, int overlay, float yaw, float roll, float pitch, int extra, String pack) {
        return key(m, light, overlay, yaw, roll, pitch, extra, Wear.packed(m), pack);
    }

    /**
     * The look with the dents as drawn ({@link Wear#packed}: on the Gantry they step down as the client shows the repair)
     * and the shader pack it is drawn under (null: none).
     */
    public static Key key(MechaEntity m, int light, int overlay, float yaw, float roll, float pitch, int extra, int dents, String pack) {
        long ports = 0;
        for (int n = 0; n < Math.min(4, m.genePorts()); n++) {
            // each vial: its fill in hundredths, its serum's colour and whether its gene rests
            ports = ports * 1_000_003L + Math.round(Executive.fill(m, n) * 100);
            ports = ports * 1_000_003L + Executive.colour(m, n);
            ports = ports * 31 + (Executive.resting(m, n) ? 1 : 0);
        }
        int graft = Chimera.grafted(m) == null ? -1 : Chimera.tint(m) & 0xFFFFFF;
        return new Key(m.modulesKey(), m.frame(), light, overlay, yaw, roll, pitch, m.getScale(), lamps(m), ports, graft, extra, dents, pack);
    }

    /**
     * What a kept look's vertices say, to compare a check with what is kept. {@code size} is the bytes a vertex (36 for
     * the game's entity layout; a shader pack's is longer). Each place to a 1024th of a block (where the camera stood
     * when they were written moves them by a few millionths), everything else exactly: colour, the two texture
     * coordinates, the light, the normal and whatever a shader pack adds after the game's 36 bytes (its entity ids, the
     * middle of the texture, the tangent); the game's padding byte (35, never written) left out.
     */
    public static long hash(java.nio.ByteBuffer b, int size) {
        long h = 1;
        for (int v = b.position(); v + size <= b.limit(); v += size) {
            for (int c = 0; c < 3; c++) h = h * 31 + Math.round(b.getFloat(v + 4 * c) * 1024);
            h = h * 31 + b.getLong(v + 12);
            h = h * 31 + b.getLong(v + 20);
            h = h * 31 + b.getInt(v + 28);
            for (int c = 32; c < 35; c++) h = h * 31 + b.get(v + c);
            for (int c = 36; c < size; c++) h = h * 31 + b.get(v + c);
        }
        return h;
    }

    /**
     * The Iris versions and shader packs under which parked machines are drawn from kept geometry through the pack's own
     * programs, main view and shadows: only those it was checked with, by eye and pixel by pixel. Any other version or
     * pack (or one renamed) is drawn the ordinary way.
     */
    public static final class ShaderPacks {
        public static final java.util.Set<String> IRIS = java.util.Set.of("1.8.12");
        public static final java.util.Set<String> PACKS = java.util.Set.of("BSLClassic_v10.0.zip", "ComplementaryReimagined_r5.5.1.zip");

        private ShaderPacks() {
        }

        /** A known Iris: its version up to the first '-' or '+' ("1.8.12-snapshot+mc1.21.1-local" is 1.8.12). */
        public static boolean knownIris(String version) {
            if (version == null) return false;
            int end = version.length();
            for (int i = 0; i < version.length(); i++) {
                char c = version.charAt(i);
                if (c == '-' || c == '+') {
                    end = i;
                    break;
                }
            }
            return IRIS.contains(version.substring(0, end));
        }

        public static boolean knownPack(String name) {
            return name != null && PACKS.contains(name);
        }
    }

    /**
     * When a kept look is checked again and when a machine whose look keeps changing is left to the ordinary drawing for a
     * while (frames are the client's rendered frames). A kept look is drawn again the ordinary way once every
     * {@link #VERIFY} frames, staggered by the machine's id, and compared with what is kept: anything the key does not
     * cover is then caught within a few seconds. A machine rebuilt more than {@link #BURST} times within {@link #WINDOW}
     * frames (its look changes nearly every frame: a flickering light, a shaking frame) is drawn the ordinary way for
     * {@link #REST} frames.
     */
    public static final class Schedule {
        public static final int VERIFY = 240, BURST = 3, WINDOW = 120, REST = 600;
        private final long[] builds = new long[BURST + 1];
        private int count;
        private long restUntil = Long.MIN_VALUE;

        public static boolean verifyDue(int id, long frame) {
            return Math.floorMod(frame + id * 37L, VERIFY) == 0;
        }

        /** A rebuild at this frame; true when that makes too many and the machine should rest. */
        public boolean built(long frame) {
            builds[count++ % builds.length] = frame;
            if (count <= BURST) return false;
            long oldest = Long.MAX_VALUE;
            for (long b : builds) oldest = Math.min(oldest, b);
            if (frame - oldest < WINDOW) {
                restUntil = frame + REST;
                count = 0;
                return true;
            }
            return false;
        }

        public boolean resting(long frame) {
            return frame < restUntil;
        }
    }
}
