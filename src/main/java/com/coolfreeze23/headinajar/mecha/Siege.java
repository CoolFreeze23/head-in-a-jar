package com.coolfreeze23.headinajar.mecha;

import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.feedback.Refuser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The Colossus's Siege, on the drive key, decided on the server. A press standing still on firm ground (its knee spades
 * stand in for Anchor Clamps, which its one Legs slot can't carry beside its legs) kneels the machine a full block and digs its knee spades in ({@code siege_in}, 40 ticks); from the clip's tick
 * 38 it is anchored (no knock-back, its shoulder weapons cycle twice as fast, its vents shed half as much heat again, and
 * blows from the front are cut by 30%). A second press stands it up ({@code siege_out}, 40 ticks, no bonuses while it
 * rises). From the first tick in to the last tick out it neither walks nor turns; the pilot still aims. Every refused press
 * plays {@code siege_refused} (a step out, the permit buzzer, the step back and a shrug) and says why.
 * <p>
 * The state is one synced moment on the machine ({@code verbAt}: the time and ENTER, LEAVE or REFUSED), never saved: a
 * Siege ends with the session, its pilot, or the machine.
 */
public final class Siege {
    /** The kinds of Siege moment (the low three bits of the synced verb time). */
    public static final int ENTER = 0, LEAVE = 1, REFUSED = 2;
    /** The clips: in 40 ticks (anchored at 31, locked at 38), out 40 (the knees leave the ground at 12), refused 26. */
    public static final int IN_TICKS = 40, ANCHOR = 31, LOCKED = 38, OUT_TICKS = 40, UNANCHOR = 12, REFUSED_TICKS = 26;
    /**
     * FE: kneeling in (about 1% of the Colossus's 1,000 kFE battery: a full-strength use) and every tick held (its own
     * idle drain again, 8 FE a tick: a sustained effect).
     */
    public static final int SIEGE_FE = 10_000, HOLD_FE = 8;
    /** Faster than this (blocks a tick, measured) is not a standstill. */
    public static final double CRAWL = 0.03;
    /** The anchored bonuses: heat out x1.5, blows from within 60 degrees of the front cut by 30%. */
    public static final float DISSIPATION = 1.5f, FRONT_CUT = 0.3f;
    static final double FRONT_COS = 0.5;
    /** Held, the deck and the jar are 26 px (1.625 blocks) down, straight down. */
    public static final double SEAT_DROP = 26 / 16.0;
    /**
     * How far the jar is below its standing place, px, at each tick of siege_in and siege_out (the clips' own deck travel:
     * the survey's lift, the two steps' dips, the drop, the strike's overshoot; the push, the pop on 12, the draw-in and
     * the steps back). The pilot's seat follows it, linear between ticks, so the seat and the first-person eye ride the deck.
     */
    static final double[] IN = {0, -0.8, -1.3, -1.5, -1.3, -1.0, -0.5, -0.1, 0, 1.0, 3.0, 4.0, 4.5, 6.0, 4.9, 4.5, 4.5, 4.5, 4.5, 4.5,
            4.7, 5.2, 6.0, 5.3, 5.0, 6.3, 9.5, 12.7, 14.0, 15.8, 21.0, 27.5, 25.9, 25.4, 25.7, 26.2, 26.4, 26.3, 26.1, 26.0, 26.0};
    static final double[] OUT = {26.0, 26.0, 26.0, 26.0, 26.0, 26.8, 27.0, 27.0, 26.8, 26.3, 25.2, 23.5, 21.0, 20.5, 20.1, 20.0, 19.8,
            18.7, 15.6, 10.4, 7.3, 6.2, 6.0, 5.3, 5.0, 4.9, 4.6, 4.5, 4.7, 5.2, 6.0, 4.9, 4.5, 4.4, 4.0, 3.6, 3.5, 3.3, 2.8, 2.0, 1.6};
    /** Where the spades bite in the held pose, in the model (pixels, front = -z): behind the machine, either side. */
    static final Vec3 SPADE_L = new Vec3(-22.7, 0, 26.7), SPADE_R = new Vec3(22.7, 0, 26.7);
    /** What the held pose covers on the ground (pixels): 2.4 blocks to each side, from 18 px ahead to 3.5 blocks behind. */
    static final double REACH_SIDE = 38, REACH_FRONT = -18, REACH_BACK = 56;

    private Siege() {
    }

    public static boolean sieges(MechaEntity m) {
        MechaRig rig = MechaRig.of(m.frame());
        return rig != null && rig.verb == MechaRig.Verb.SIEGE;
    }

    static int kind(MechaEntity m) {
        return (int) (m.verbAt() & 7);
    }

    /** Ticks since the last Siege moment, or -1 for none. */
    static double since(MechaEntity m, double now) {
        long at = m.verbAt();
        return at < 0 ? -1 : now - at / 8;
    }

    /** Kneeling in, held or getting up: it neither walks nor turns. */
    public static boolean rooted(MechaEntity m) {
        if (!sieges(m)) return false;
        double t = since(m, m.level().getGameTime());
        if (t < 0) return false;
        int kind = kind(m);
        return kind == ENTER || kind == LEAVE && t < OUT_TICKS;
    }

    /** Anchored: from {@code siege:locked} (siege_in's tick 38) until {@code siege:release} (siege_out's tick 0). */
    public static boolean locked(MechaEntity m) {
        if (!sieges(m)) return false;
        double t = since(m, m.level().getGameTime());
        return t >= LOCKED && kind(m) == ENTER;
    }

    /** How far the jar is below its standing place now, px (both sides, from the synced moment; negative is higher). */
    public static double seatPx(MechaEntity m, double now) {
        if (!sieges(m)) return 0;
        double t = since(m, now);
        if (t < 0) return 0;
        return switch (kind(m)) {
            case ENTER -> t >= IN_TICKS ? IN[IN_TICKS] : table(IN, t);
            case LEAVE -> t >= OUT_TICKS ? 0 : table(OUT, t);
            default -> 0;
        };
    }

    /** How far down the pilot's seat is now, 0 standing to 1 knelt. */
    public static double seat(MechaEntity m, double now) {
        return seatPx(m, now) / 16 / SEAT_DROP;
    }

    static double table(double[] px, double t) {
        int i = Mth.clamp((int) Math.floor(t), 0, px.length - 2);
        return Mth.lerp(Mth.clamp(t - i, 0, 1), px[i], px[i + 1]);
    }

    /** The seat's offset from the standing seat, blocks: straight down with the deck. */
    public static Vec3 seatOffset(MechaEntity m) {
        double px = seatPx(m, m.level().getGameTime());
        return px == 0 ? Vec3.ZERO : new Vec3(0, -px / 16, 0);
    }

    // --- the drive key ------------------------------------------------------------------------------------------

    /** The pilot pressed (true) or let go of the drive key. Only the press acts, and every press answers. */
    public static void input(ServerPlayer pilot, boolean held) {
        if (!held || !(pilot.getVehicle() instanceof MechaEntity m) || m.getFirstPassenger() != pilot || !sieges(m)) return;
        long now = m.level().getGameTime();
        double t = since(m, now);
        int kind = kind(m);
        if (kind == ENTER && t >= 0 && t < IN_TICKS || kind == LEAVE && t >= 0 && t < OUT_TICKS) {
            Refusals.deny(pilot, Refuser.at(m), "mecha.siege.busy");
            return;
        }
        if (kind == ENTER && t >= IN_TICKS) {
            m.setVerbAt(now, LEAVE);
            return;
        }
        Component gantry = Component.translatable("block.headinajar.assembly_gantry");
        String why = whyNot(m);
        if (why != null) {
            refuse(m, pilot, why, why.endsWith("no_clamps") || why.endsWith("no_power") ? new Object[]{gantry} : new Object[0]);
            return;
        }
        m.spend(SIEGE_FE);
        m.setVerbAt(now, ENTER);
    }

    /** Why Siege can't start now, or null: venting, no Anchor Clamps, moving, in the air, in a fluid, soft ground, flat. */
    public static String whyNot(MechaEntity m) {
        if (m.vent() > 0) return "mecha.siege.venting";
        // Anchor Clamps are a Legs module: a frame with one Legs slot (the Colossus) can't carry them beside the legs it
        // walks on (the Gantry won't build it without walking legs), so its own knee spades stand in for them; a frame
        // with room for both needs the clamps fitted
        if (m.frame().slots(MechaModule.Slot.LEGS) > 1 && !m.has(MechaModule.ANCHOR_CLAMPS)) return "mecha.siege.no_clamps";
        if (m.siegeSpeed > CRAWL) return "mecha.siege.moving";
        if (!m.onGround()) return "mecha.siege.airborne";
        if (m.isInWater() || m.isInLava() || m.isInFluidType()) return "mecha.siege.fluid";
        if (!firm(m, SPADE_L) || !firm(m, SPADE_R)) return "mecha.siege.ground";
        if (!room(m)) return "mecha.siege.room";
        if (m.battery() < SIEGE_FE) return "mecha.siege.no_power";
        return null;
    }

    /** The block under a knee spade (the one the machine stands on, at that spot) takes it: a full top face. */
    static boolean firm(MechaEntity m, Vec3 px) {
        BlockPos pos = spadeBlock(m, px);
        BlockState state = m.level().getBlockState(pos);
        return state.isFaceSturdy(m.level(), pos, Direction.UP);
    }

    /**
     * Open ground for the held pose: nothing solid at foot or knee height anywhere it reaches (2.4 blocks each side, 3.5
     * behind), so the outriggers, boots and fists never sink into a wall.
     */
    static boolean room(MechaEntity m) {
        return obstacle(m) == null;
    }

    /** The first solid block in the held pose's way, or null. */
    public static BlockPos obstacle(MechaEntity m) {
        for (double z = REACH_FRONT; z <= REACH_BACK; z += 4) {
            for (double x = -REACH_SIDE; x <= REACH_SIDE; x += 4) {
                for (int up = 0; up < 2; up++) {
                    BlockPos pos = place(m, x, z, up + 0.5);
                    // what it stands on (a pad, a slab) is ground, not a wall: only what rises above its feet is in the way
                    var shape = m.level().getBlockState(pos).getCollisionShape(m.level(), pos);
                    if (!shape.isEmpty() && pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y) > m.getY() + 0.2) return pos;
                }
            }
        }
        return null;
    }

    /** The block at a model point (pixels across and along the body, front = -z) and a height over the feet (blocks). */
    static BlockPos place(MechaEntity m, double x, double z, double up) {
        Vec3 fwd = Bulwark.forward(m.yBodyRot), side = Bulwark.forward(m.yBodyRot + 90);
        return BlockPos.containing(m.getX() - fwd.x * z / 16 + side.x * x / 16, m.getY() + up, m.getZ() - fwd.z * z / 16 + side.z * x / 16);
    }

    static BlockPos spadeBlock(MechaEntity m, Vec3 px) {
        Vec3 at = Bulwark.world(m, new Vec3(px.x, 0, px.z));
        // Bulwark.world turns z only; x is sideways: put it across the body's yaw
        Vec3 side = Bulwark.forward(m.yBodyRot + 90).scale(px.x / 16);
        return BlockPos.containing(at.x + side.x, m.getY() - 0.5, at.z + side.z);
    }

    static void refuse(MechaEntity m, ServerPlayer pilot, String why, Object... args) {
        m.setVerbAt(m.level().getGameTime(), REFUSED);
        Refusals.deny(pilot, Refuser.at(m), why, args);
    }

    /** Ends any Siege at once: no bonuses, standing. */
    public static void clear(MechaEntity m) {
        if (m.level().isClientSide || m.verbAt() < 0) return;
        m.setVerbAt(-1, 0);
    }

    /** Ticks a machine wrecked in Siege takes to stand up out of it before its collapse ({@code siege_out} four times as fast). */
    public static final int WRECK_RISE = 10;

    /**
     * The pilot gone (ejected, thrown clear, logged out) or the machine wrecked mid-Siege: no bonuses from now, and it
     * stands up out of Siege by its own clip ({@code siege_out}), with nobody aboard, instead of popping up in one tick.
     */
    public static void release(MechaEntity m) {
        if (m.level().isClientSide || m.verbAt() < 0) return;
        if (kind(m) == ENTER) m.setVerbAt(m.level().getGameTime(), LEAVE);
        else if (kind(m) == REFUSED) clear(m);
    }

    /** Standing up out of Siege now (piloted or not). */
    public static boolean rising(MechaEntity m, double now) {
        if (!sieges(m) || kind(m) != LEAVE) return false;
        double t = since(m, now);
        return t >= 0 && t < OUT_TICKS;
    }

    /** {@link #WRECK_RISE} for a machine wrecked in Siege (it began standing up the tick it was wrecked), else 0. */
    public static int riseBeforeCollapse(MechaEntity m) {
        if (!sieges(m) || kind(m) != LEAVE || !m.isDeadOrDying()) return 0;
        double wrecked = m.level().getGameTime() - m.deathTime;
        return Math.abs(m.verbAt() / 8 - wrecked) <= 2 ? WRECK_RISE : 0;
    }

    // --- every server tick ----------------------------------------------------------------------------------------

    public static void serverTick(MechaEntity m) {
        if (!sieges(m)) return;
        Vec3 here = m.position();
        if (m.siegeLast != null) {
            double moved = Math.hypot(here.x - m.siegeLast.x, here.z - m.siegeLast.z);
            m.siegeSpeed += (moved - m.siegeSpeed) * 0.5;
        }
        m.siegeLast = here;
        long now = m.level().getGameTime();
        double t = since(m, now);
        if (t < 0) return;
        int kind = kind(m);
        if (kind == ENTER && t == ANCHOR || kind == LEAVE && t == UNANCHOR) {
            Hopper.play(m, "siege_anchor");
            for (Vec3 spade : new Vec3[]{SPADE_L, SPADE_R}) crack(m, spadeBlock(m, spade));
            // the footings go in: a ring of the ground's dust right round the machine, as a building's would
            Hopper.dust(m, 24, 2.0);
        }
        if (kind == ENTER) {
            if (!m.spend(HOLD_FE) || m.battery() <= 0) {
                // the battery ran flat: it stands up again
                m.setVerbAt(now, LEAVE);
                if (com.coolfreeze23.headinajar.host.Hosts.pilot(m) instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.translatable("hud.headinajar.siege_flat"), true);
                }
            }
        }
    }

    /** The spades bite (or come out): cracks of the block under each, and a puff of its dust. */
    static void crack(MechaEntity m, BlockPos pos) {
        BlockState state = m.level().getBlockState(pos);
        if (state.isAir()) return;
        var particle = new BlockParticleOption(ParticleTypes.BLOCK, state);
        m.serverLevel().sendParticles(particle, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 18, 0.35, 0.05, 0.35, 0.15);
    }

    /** A blow on an anchored Colossus from within 60 degrees of its front is cut by 30%. */
    public static float incoming(MechaEntity m, DamageSource source, float amount) {
        if (amount <= 0 || !locked(m)) return amount;
        Vec3 from = source.getSourcePosition();
        if (from == null) return amount;
        Vec3 to = new Vec3(from.x - m.getX(), 0, from.z - m.getZ());
        if (to.lengthSqr() < 1e-6) return amount;
        return to.normalize().dot(Bulwark.forward(m.yBodyRot)) >= FRONT_COS ? amount * (1 - FRONT_CUT) : amount;
    }
}
