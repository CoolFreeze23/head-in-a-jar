package com.coolfreeze23.headinajar.mecha;

import com.coolfreeze23.headinajar.entity.MechaEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

/**
 * Wear a bystander can read: where a machine has been hit and how badly it is damaged, from two things every client has,
 * its health and the bearings of its last few blows ({@link MechaEntity#hitBearings}, synced and saved; a Utility
 * Shell's are {@link com.coolfreeze23.headinajar.entity.ShellEntity#hitBearings}, saved and sent on an optional channel). At three
 * quarters, half and a quarter of its health a dent stage appears on the side it was hit from most; a side it was hit
 * from often enough besides shows one stage less. Below a quarter a walking frame limps on the leg of the side it was
 * hit from more (with power: the limp of a flat battery is its own). A machine never hit shows nothing, whatever its
 * health (an old world's machine starts clean); healing to full forgets the blows.
 */
public final class Wear {
    /** The machine's sides, as the dent sheets are laid out. */
    public static final int FRONT = 0, RIGHT = 1, BACK = 2, LEFT = 3;
    /** How many blows are remembered. */
    public static final int KEPT = 8;
    /** The health shares at which dent stages 1, 2 and 3 appear (strictly below), and below which a walker limps. */
    public static final float STAGE_1 = 0.75f, STAGE_2 = 0.5f, STAGE_3 = 0.25f;

    private Wear() {
    }

    /**
     * Where a blow came from, in degrees about the machine from its front (0) through its right (90), back (180) and left
     * (270); NaN for a blow from nowhere in particular (a fall, fire, starving), which leaves no dent.
     */
    public static float bearing(net.minecraft.world.entity.LivingEntity m, DamageSource source) {
        Vec3 from = source.getSourcePosition();
        if (from == null) return Float.NaN;
        double dx = from.x - m.getX(), dz = from.z - m.getZ();
        if (dx * dx + dz * dz < 1e-6) return Float.NaN;
        // the game's yaw of the direction to the source (0 south, 90 west), against the way the body faces
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        return Mth.positiveModulo(yaw - m.yBodyRot, 360f);
    }

    /** The remembered bearings with one more blow in front: a byte each, 1-255 (0 is an empty place), newest lowest. */
    public static long push(long bearings, float degrees) {
        if (Float.isNaN(degrees)) return bearings;
        long b = 1 + Math.min(254, (long) Math.floor(Mth.positiveModulo(degrees, 360f) / 360f * 255f));
        return bearings << 8 | b;
    }

    /** One remembered bearing in degrees, or NaN for an empty place. */
    public static float at(long bearings, int i) {
        long b = bearings >>> (8 * i) & 0xFF;
        return b == 0 ? Float.NaN : (b - 1 + 0.5f) * 360f / 255f;
    }

    /** The side a bearing hits. */
    public static int side(float degrees) {
        return Math.floorMod(Math.round(degrees / 90f), 4);
    }

    /** Blows remembered from each side. */
    public static int[] counts(long bearings) {
        int[] n = new int[4];
        for (int i = 0; i < KEPT; i++) {
            float d = at(bearings, i);
            if (!Float.isNaN(d)) n[side(d)]++;
        }
        return n;
    }

    /** The dent stage the machine's health calls for, 0 to 3. */
    public static int stage(float health, float max) {
        float share = health / Math.max(1e-3f, max);
        return share < STAGE_3 ? 3 : share < STAGE_2 ? 2 : share < STAGE_1 ? 1 : 0;
    }

    /** The side hit most, the newest blow breaking a tie; -1 if none is remembered. */
    public static int most(long bearings) {
        int[] n = counts(bearings);
        int best = -1;
        for (int i = KEPT - 1; i >= 0; i--) {
            float d = at(bearings, i);
            if (Float.isNaN(d)) continue;
            int s = side(d);
            if (best < 0 || n[s] >= n[best]) best = s;
        }
        return best;
    }

    /**
     * The dent stage on each side (front, right, back, left): the stage health calls for on the side hit most, one less on
     * any other side that took at least two of the remembered blows; none at all without a remembered blow.
     */
    public static int[] stages(long bearings, float health, float max) {
        int[] out = new int[4];
        int most = most(bearings);
        int stage = stage(health, max);
        if (most < 0 || stage == 0) return out;
        int[] n = counts(bearings);
        for (int s = 0; s < 4; s++) {
            if (s == most) out[s] = stage;
            else if (n[s] >= 2) out[s] = stage - 1;
        }
        return out;
    }

    /** The four sides' stages packed two bits each (front lowest): what a dented look is known by. */
    public static int packed(int[] stages) {
        return stages[0] | stages[1] << 2 | stages[2] << 4 | stages[3] << 6;
    }

    public static int packed(MechaEntity m) {
        return packed(stages(m.hitBearings(), m.getHealth(), m.getMaxHealth()));
    }

    /**
     * The leg a damaged walker drags: 0 the left, 1 the right, from the blows that came from either side (a blow from the
     * front or the back counts for the side it leaned to); the left when nothing tells them apart.
     */
    public static int limpSide(long bearings) {
        double lean = 0;
        int[] n = counts(bearings);
        for (int i = 0; i < KEPT; i++) {
            float d = at(bearings, i);
            if (!Float.isNaN(d)) lean += Math.sin(Math.toRadians(d));
        }
        if (n[RIGHT] != n[LEFT]) return n[RIGHT] > n[LEFT] ? 1 : 0;
        return lean > 1e-3 ? 1 : 0;
    }

    /** Badly damaged with power: a walker limps on its worse leg (the flat battery's limp is another clip). */
    public static boolean hurtLimp(MechaEntity m) {
        return m.battery() > 0 && m.getHealth() < m.getMaxHealth() * STAGE_3;
    }
}
