package com.coolfreeze23.headinajar.mecha;

import com.coolfreeze23.headinajar.entity.MechaEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;

/**
 * What a parked frame looks like, apart from where it stands. A parked machine (nobody aboard, nothing happening to it)
 * shows one settled pose, so a client may draw it from geometry kept since it last changed; this says when it is parked
 * and what its look depends on, so the kept geometry is used exactly while the look is the same and rebuilt the moment
 * any of it changes: the fitted modules, its light, its facing, the battery's lamps, its Gene Ports' vials, the Chimera's
 * grafted veins. Nothing here is saved or sent.
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
     * still holds (the drill's resting angle, the jets' spent lamps).
     */
    public record Key(Object modules, MechaFrame frame, int light, int overlay, float yaw, float roll, float pitch, float scale, int lamps,
                      long ports, int graft, int extra) {
        /** The same look: equal in everything, the angles within a thousandth of a degree. */
        public boolean matches(Key o) {
            return o != null && modules == o.modules && frame == o.frame && light == o.light && overlay == o.overlay && close(yaw, o.yaw)
                    && close(roll, o.roll) && close(pitch, o.pitch) && scale == o.scale && lamps == o.lamps && ports == o.ports && graft == o.graft
                    && extra == o.extra;
        }

        /** The first thing that differs from another look, by name (for the harness's figures), or null if the same. */
        public String differs(Key o) {
            if (o == null) return "none kept";
            if (modules != o.modules) return "modules";
            if (frame != o.frame) return "frame";
            if (light != o.light) return "light";
            if (overlay != o.overlay) return "overlay";
            if (!close(yaw, o.yaw) || !close(roll, o.roll) || !close(pitch, o.pitch)) return "facing";
            if (scale != o.scale) return "scale";
            if (lamps != o.lamps) return "lamps";
            if (ports != o.ports) return "gene ports";
            if (graft != o.graft) return "graft";
            return extra != o.extra ? "client motion or name" : null;
        }

        private static boolean close(float a, float b) {
            return Math.abs(net.minecraft.util.Mth.wrapDegrees(a - b)) < 1e-3f;
        }
    }

    public static Key key(MechaEntity m, int light, int overlay, float yaw, float roll, float pitch, int extra) {
        long ports = 0;
        for (int n = 0; n < Math.min(4, m.genePorts()); n++) {
            // each vial: its fill in hundredths, its serum's colour and whether its gene rests
            ports = ports * 1_000_003L + Math.round(Executive.fill(m, n) * 100);
            ports = ports * 1_000_003L + Executive.colour(m, n);
            ports = ports * 31 + (Executive.resting(m, n) ? 1 : 0);
        }
        int graft = Chimera.grafted(m) == null ? -1 : Chimera.tint(m) & 0xFFFFFF;
        return new Key(m.modulesKey(), m.frame(), light, overlay, yaw, roll, pitch, m.getScale(), lamps(m), ports, graft, extra);
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
