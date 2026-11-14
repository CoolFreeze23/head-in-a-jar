package com.coolfreeze23.headinajar.mecha;

import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.feedback.Refuser;
import com.coolfreeze23.headinajar.host.Hosts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What an animated walker (the Hopper, and the moments every animated frame shares) does on the server beyond the shared mecha logic: the Pogo Protocol on the drive
 * key, the contact moments its clips are authored to (launch, landing, vent blast, collapse, the build's modules
 * clunking home, the boot's all-clear), and the Golden Parachute's eject. The moments are synced on the entity so every
 * client's clip lands on the same tick.
 */
public final class Hopper {
    /** Ticks of crouch to a full charge (the crouch clip's length). */
    public static final int CHARGE_TICKS = 30;
    /** FE a jump costs: the least charge and the full charge (a full battery is 100 kFE: a hundred full jumps). */
    public static final int JUMP_FE = 250, FULL_JUMP_FE = 1000;
    /** The landing's knock-back ring, blocks. */
    public static final double RING = 3;
    /** The collapse's length; it lands face down on tick 27. */
    public static final int COLLAPSE_TICKS = 40, COLLAPSE_CONTACT = 27;

    private Hopper() {
    }

    // --- the Pogo Protocol ---------------------------------------------------------------------------------------

    /** Why a pogo can't start now, or null: venting, no legs, in the air, a flat battery, no headroom. */
    public static String refusal(MechaEntity m) {
        if (m.vent() > 0) return "mecha.pogo.venting";
        if (!MechaLogic.canWalk(m)) return "mecha.pogo.no_legs";
        if (!m.onGround()) return "mecha.pogo.airborne";
        if (m.battery() < JUMP_FE) return "mecha.pogo.no_power";
        if (!headroom(m)) return "mecha.pogo.no_room";
        return null;
    }

    /** Two blocks clear over the hull. */
    public static boolean headroom(MechaEntity m) {
        AABB box = m.getBoundingBox();
        return m.level().noBlockCollision(m, new AABB(box.minX, box.maxY, box.minZ, box.maxX, box.maxY + 2, box.maxZ));
    }

    /** The pilot pressed or let go of the drive key (the Pogo payload's handler). One answer per press. */
    public static void input(ServerPlayer pilot, boolean held) {
        if (!(pilot.getVehicle() instanceof MechaEntity m) || m.getFirstPassenger() != pilot || MechaRig.of(m.frame()) != MechaRig.HOPPER) return;
        long now = m.level().getGameTime();
        if (held) {
            if (m.pogoAt() >= 0 || m.pogoCharge >= 0) return;
            String why = refusal(m);
            if (why != null) {
                Refusals.deny(pilot, Refuser.at(m), why, Component.translatable("block.headinajar.assembly_gantry"));
                return;
            }
            m.setPogoAt(now);
            return;
        }
        if (m.pogoAt() < 0) return;
        float charge = charge(m, now);
        m.setPogoAt(-1);
        if (!m.onGround()) return;
        m.spend(Math.round(JUMP_FE + (FULL_JUMP_FE - JUMP_FE) * charge));
        m.setHeat(m.heat() + 5 + 10 * charge);
        m.pogoCharge = charge;
        // the leap clip's tick 1 is the launch: the impulse comes on the next tick
        m.setLeapAt(now);
    }

    /** The charge held so far, 0 to 1. */
    public static float charge(MechaEntity m, long now) {
        return m.pogoAt() < 0 ? 0 : Mth.clamp((now - m.pogoAt()) / (float) CHARGE_TICKS, 0, 1);
    }

    /** How far a leap at this charge carries on level ground, blocks: 3 to 9. */
    public static double distance(float charge) {
        return 3 + 6 * charge;
    }

    /** The launch velocity for a charge along a look (yaw, pitch in degrees), set so the leap carries {@link #distance}. */
    public static Vec3 launch(float charge, float yaw, float pitch) {
        double up = Math.max(0, -Math.sin(pitch * Mth.DEG_TO_RAD));
        double vy = 0.6 + 0.3 * charge + 0.2 * charge * up;
        // ticks in the air on level ground (living gravity: v = (v - 0.08) * 0.98 a tick)
        double y = 0, v = vy;
        int ticks = 0;
        do {
            y += v;
            v = (v - 0.08) * 0.98;
            ticks++;
        } while (y > 0 && ticks < 200);
        // horizontal speed falls by 0.91 a tick in the air
        double carried = (1 - Math.pow(0.91, ticks)) / (1 - 0.91);
        double h = distance(charge) / carried;
        float r = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(r) * h, vy, Mth.cos(r) * h);
    }

    // --- every server tick ---------------------------------------------------------------------------------------

    public static void serverTick(MechaEntity m) {
        ServerLevel level = m.serverLevel();
        long now = level.getGameTime();
        Player pilot = Hosts.pilot(m);
        // a charge ends if the machine leaves the ground or its pilot
        if (m.pogoAt() >= 0 && (pilot == null || !m.onGround() || m.vent() > 0)) m.setPogoAt(-1);
        if (m.pogoCharge >= 0 && now - m.leapAt() >= 1) {
            float yaw = pilot != null ? pilot.getYRot() : m.getYRot(), pitch = pilot != null ? pilot.getXRot() : 0;
            // the pilot's client moves a ridden mecha and applies this launch itself (HopperClient), the moment the leap
            // time reaches it: a velocity set here would be overwritten by the server's own zeroed motion a tick later
            m.launch = launch(m.pogoCharge, yaw, pitch);
            if (pilot == null) {
                m.setDeltaMovement(m.launch);
                m.hurtMarked = true;
            }
            m.fallDistance = 0;
            m.pogoAirborne = false;
            m.pogoFlight = true;
            play(m, "pogo_launch");
            dust(m, 10, 1.0);
            m.pogoCharge = -1;
        }
        if (m.pogoFlight) {
            m.fallDistance = 0;
            if (!m.onGround()) m.pogoAirborne = true;
            // down again once it has been seen in the air (or if it never left the ground: a ceiling, a test)
            boolean down = m.onGround() || m.isInWater();
            if (down && (m.pogoAirborne || now - m.leapAt() >= 20)) land(m, pilot);
            else if (now - m.leapAt() > 200) m.pogoFlight = false;
        }
        if (m.vent() == MechaLogic.VENT_TICKS - 3) play(m, "vent_blast");
        // an ordinary landing (not the pogo's): its weight is decided here once and synced, so every client plays the same
        if (!m.onGround() && !m.isInWater()) {
            if (m.airTicks++ == 0) m.peakY = m.getY();
            m.peakY = Math.max(m.peakY, m.getY());
        } else {
            if (m.airTicks > 3 && !m.pogoFlight && m.landedAt() != now) {
                m.lastDrop = m.peakY - m.getY();
                // a flier comes down from any height under its wings: its landing is heavy by how fast it came down
                m.setLandAt(now, HopperAnimation.rig(m).flies() ? m.sinkRate >= Kite.HEAVY_SINK : m.lastDrop >= 4);
            }
            m.airTicks = 0;
        }
        long boarded = m.boardedAt();
        if (boarded >= 0 && pilot instanceof ServerPlayer sp && now - boarded == HopperAnimation.BOOT_START + 38) {
            // the boot's all-clear: the company jingle and the checklist's last line
            level.playSound(null, m.getX(), m.getY() + 1.5, m.getZ(), com.coolfreeze23.headinajar.registry.ModSounds.MECHA_BOOT.get(), SoundSource.NEUTRAL, 1, 1);
            int fitted = 0;
            for (int i = 0; i < m.frame().totalSlots(); i++) if (m.moduleAt(i) != null) fitted++;
            sp.displayClientMessage(Component.translatable("hud.headinajar.self_test", fitted), true);
        }
        long built = m.builtAt();
        if (built >= 0) {
            long t = now - built;
            int[] seats = HopperAnimation.rig(m).seatTicks;
            for (int i = 0; i < seats.length && i < m.frame().totalSlots(); i++) {
                if (t == seats[i] && m.moduleAt(i) != null) {
                    play(m, "build_seat");
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, m.getX(), m.getY() + 1.2, m.getZ(), 4, 0.4, 0.4, 0.4, 0.05);
                }
            }
            if (t >= HopperAnimation.rig(m).clip("build").length()) m.endBuild();
        }
        // no legs: a move input is answered by the left arm's dry fire (the HUD carries the remedy)
        if (pilot != null && !MechaLogic.canWalk(m) && (pilot.zza != 0 || pilot.xxa != 0) && (m.dryAt() < 0 || now - m.dryAt() / 2 > 30)) {
            m.dryFire(0);
        }
    }

    /** Down on its springs: the thud, dust from the block underfoot, a knock-back ring that hurts nothing. */
    static void land(MechaEntity m, Player pilot) {
        m.pogoFlight = false;
        m.fallDistance = 0;
        m.setLandedAt(m.level().getGameTime());
        play(m, "step_heavy");
        dust(m, 16, 1.6);
        for (LivingEntity e : m.level().getEntitiesOfClass(LivingEntity.class, m.getBoundingBox().inflate(RING, 1, RING),
                e -> e != m && e != pilot && !m.hasPassenger(e) && e.isAlive())) {
            if (e instanceof Player other && (pilot == null || !trusts(other, pilot))) continue;
            double dx = m.getX() - e.getX(), dz = m.getZ() - e.getZ();
            if (dx * dx + dz * dz < 1e-4) dx = 0.01;
            e.knockback(0.9, dx, dz);
            e.hurtMarked = true;
        }
    }

    /** Has {@code other} trusted the pilot (only then may the pilot's landing move them)? */
    static boolean trusts(Player other, Player pilot) {
        return other.getServer() != null && com.coolfreeze23.headinajar.form.HeadRegistry.get(other.getServer()).trusts(other.getUUID(), pilot.getUUID());
    }

    /** The collapse: on its face at tick 27, with dust. The destruction itself follows at 40. */
    public static void dying(MechaEntity m, int deathTime) {
        if (deathTime == COLLAPSE_CONTACT) {
            play(m, "collapse");
            dust(m, 14, 1.4);
        }
    }

    /** The Golden Parachute's moment: the klaxon, the hoop blown open, the jar fired clear (the eject clip's tick 4). */
    public static void goldenParachute(MechaEntity m, ServerPlayer pilot) {
        long now = m.level().getGameTime();
        m.setEjectedAt(now - 4);
        play(m, "klaxon");
        // the Hopper's hoop is blown back, the Bulwark's clamps blown open, the Wheelhouse's ring blown, the Kite's lantern floor blown down
        play(m, HopperSounds.own(m.frame(), "floor_blow") ? "floor_blow" : HopperSounds.own(m.frame(), "clamp_blow") ? "clamp_blow"
                : HopperSounds.own(m.frame(), "ring_blow") ? "ring_blow" : "bar_blow");
        play(m, "eject_pop");
        m.serverLevel().sendParticles(ParticleTypes.POOF, m.getX(), m.getY() + m.getBbHeight() + 0.25, m.getZ(), 12, 0.3, 0.1, 0.3, 0.05);
        BlockPos at = m.blockPosition();
        if (m.frame() == MechaFrame.HOPPER) {
            pilot.sendSystemMessage(Component.translatable("message.headinajar.severance", at.getX(), at.getY(), at.getZ()));
        } else {
            pilot.sendSystemMessage(Component.translatable("message.headinajar.severance_frame",
                    com.coolfreeze23.headinajar.registry.ModRegistry.frameItem(m.frame()).getDescription(), at.getX(), at.getY(), at.getZ()));
        }
    }

    static void play(MechaEntity m, String name) {
        var sound = HopperSounds.get(m.frame(), name);
        if (sound != null) m.level().playSound(null, m.getX(), m.getY(), m.getZ(), sound, SoundSource.NEUTRAL, 1,
                HopperSounds.pitch(HopperSounds.resolve(m.frame(), name), 1, m.getRandom()));
    }

    /** A ring of dust from the block underfoot. */
    static void dust(MechaEntity m, int count, double radius) {
        BlockState below = m.level().getBlockState(m.getBlockPosBelowThatAffectsMyMovement());
        if (below.isAir()) return;
        var particle = new BlockParticleOption(ParticleTypes.BLOCK, below);
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2 / count;
            m.serverLevel().sendParticles(particle, m.getX() + Math.cos(a) * radius, m.getY() + 0.1, m.getZ() + Math.sin(a) * radius, 1,
                    Math.cos(a) * 0.1, 0.05, Math.sin(a) * 0.1, 0.15);
        }
    }
}
