package com.coolfreeze23.headinajar.mecha;

import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.feedback.Refuser;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.module.ModuleType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** Everything a mecha does: power, heat and weight each tick, movement, its arms, and taking damage. */
public final class MechaLogic {
    public static final float THROTTLE = 0.85f;
    public static final int VENT_TICKS = 100;
    /** Ticks of Bio-Gel left in a Chimera's veins (persistent data key). One Bio-Gel is 60 s. */
    public static final String VEINS = "headinajar_veins";
    public static final int VEINS_PER_GEL = 1200;

    private MechaLogic() {
    }

    /** Work in progress for one arm: what is being drilled and how far, what is held. */
    public static class ArmState {
        BlockPos target;
        float progress;
        int held = -1;
        int cooldown;
        /** This press has already been refused (one refusal per press, however long it is held). */
        boolean refused;
        /** A tap tool's blow or shot waiting for its arm clip's contact (game time, -1 for none), and its strength. */
        long contactAt = -1;
        float contactPower;
        /** The Arc Cannon's chain: the creatures it has jumped to so far, and when its next jump goes (-1: none). */
        java.util.List<LivingEntity> chain;
        long hopAt = -1;
        float hopDamage;
    }

    // --- weight ---------------------------------------------------------------------------------------

    /** Speed multiplier from load: full to 60%, then down to 60% at the rating, 30% overloaded, 0 locked. */
    public static double loadSpeed(float load) {
        if (load <= 0.6f) return 1;
        if (load <= 1.0f) return 1 - (load - 0.6f) / 0.4f * 0.4f;
        if (load <= 1.25f) return 0.3;
        return 0;
    }

    public static double loadJump(float load) {
        if (load <= 0.6f) return 1;
        if (load <= 1.0f) return 1 - (load - 0.6f) / 0.4f * 0.5f;
        return 0;
    }

    /** Throttled (85-99% heat) modules work at 70%; venting ones not at all. */
    public static float effectiveness(MechaEntity m) {
        if (m.vent() > 0 || m.battery() <= 0) return 0;
        return m.heat() >= m.frame().heatCap * THROTTLE ? 0.7f : 1f;
    }

    public static boolean overloaded(MechaEntity m) {
        return m.load() > 1.0f;
    }

    // --- movement (both sides) -------------------------------------------------------------------------

    public static Vec3 riddenInput(MechaEntity m, Player pilot) {
        boolean swimming = m.isInWater() && m.has(MechaModule.AQUA_FINS);
        if (!canWalk(m) && !swimming && !(canFly(m) && !m.onGround())) return Vec3.ZERO;
        if (m.load() > LOAD_LOCK || m.vent() > 0 && m.onGround()) return Vec3.ZERO;
        // crouched on its springs for a pogo, or planted while the Bulwark's fists reach, throw or set down
        // (and while the Colossus kneels into, holds or leaves Siege, or the Executive plays its jingle)
        if (m.pogoAt() >= 0 || Bulwark.rooted(m) || Siege.rooted(m) || Executive.rooted(m) || Chimera.rooted(m)) return Vec3.ZERO;
        float strafe = pilot.xxa * 0.5f, forward = pilot.zza;
        if (forward <= 0) forward *= 0.4f;
        return new Vec3(strafe, 0, forward);
    }

    /** Over this share of its load rating a machine's servos lock: it cannot walk, turn on its legs or jump. */
    public static final float LOAD_LOCK = 1.25f;

    /** The Legs modules that carry a frame built on legs. */
    public static final java.util.List<MechaModule> WALKING_LEGS = java.util.List.of(MechaModule.HYDRAULIC_LEGS, MechaModule.ARACHNID_LEGS,
            MechaModule.STILTS, MechaModule.HOVER_SKIRT, MechaModule.LAVA_TREADS);

    /** Whether the frame can walk: a Legs module that walks, or locomotion built into the frame. */
    public static boolean canWalk(MechaEntity m) {
        return walks(m.frame(), m::has);
    }

    /** Whether a frame with these modules fitted can walk (also asked by the Gantry before it builds). */
    public static boolean walks(MechaFrame frame, java.util.function.Predicate<MechaModule> fitted) {
        return frame.locomotion != MechaFrame.Locomotion.LEGS || WALKING_LEGS.stream().anyMatch(fitted);
    }

    /** Off-road for treads: soft natural ground. */
    public static boolean offRoad(MechaEntity m) {
        var below = m.level().getBlockState(m.getBlockPosBelowThatAffectsMyMovement());
        return below.is(BlockTags.DIRT) || below.is(BlockTags.SAND) || below.is(BlockTags.SNOW) || below.is(net.minecraft.world.level.block.Blocks.GRAVEL)
                || below.is(BlockTags.LEAVES) || below.is(net.minecraft.world.level.block.Blocks.MUD);
    }

    public static float riddenSpeed(MechaEntity m) {
        int legs = m.indexOf(MechaModule.HYDRAULIC_LEGS);
        double stride = legs < 0 ? 1 : 0.6 + 0.8 * m.sliderAt(legs) / 100.0;
        double base = m.frame().speed;
        if (m.frame().locomotion == MechaFrame.Locomotion.TREADS && offRoad(m)) base = 0.08;
        if (m.frame().locomotion == MechaFrame.Locomotion.WINGS && !m.onGround()) base = MechaFrame.FLIGHT_SPEED;
        double speed = base * stride * loadSpeed(m.load());
        if (m.battery() <= 0) speed *= 0.3;
        if (m.isInWater() && m.has(MechaModule.AQUA_FINS)) speed *= 2;
        int servos = m.indexOf(MechaModule.SPRINT_SERVOS);
        if (servos >= 0 && m.battery() > 0) speed *= 1 + 0.8 * m.sliderAt(servos) / 100.0;
        // the Wheelhouse's Rush Hour, from its synced moment (the pilot's client moves it, so both sides must agree)
        speed *= Wheelhouse.boost(m, m.level().getGameTime());
        return (float) speed;
    }

    /** Lift (kg) a Rocket Pack gives at its slider: 200 to 800. */
    public static int thrust(MechaEntity m) {
        int i = m.indexOf(MechaModule.ROCKET_PACK);
        return i < 0 ? 0 : 200 + 6 * m.sliderAt(i);
    }

    /** Rocket thrust beats mass, wings are built in, or the frame carries Draconic Wings. */
    public static boolean canFly(MechaEntity m) {
        if (m.battery() <= 0) return false;
        return thrust(m) > m.mass() || m.frame().locomotion == MechaFrame.Locomotion.WINGS && m.load() <= 1.0f
                || com.coolfreeze23.headinajar.genetics.GeneEffects.has(m, com.coolfreeze23.headinajar.genetics.Gene.DRACONIC_WINGS);
    }

    /**
     * Whether a piloted machine is in the air by its own means, as it is meant to be: a winged frame flying, hovering or
     * gliding on charge; anything that can lift itself (thrust over its mass, Draconic Wings); a Rocket Pack, Jump Jets,
     * a Hover Skirt or Glider Wings fitted; a Hopper on its pogo arc; a Skitter hanging, swinging or reeling on its
     * Dragline. A server without allow-flight would otherwise disconnect its pilot after about four seconds
     * ({@link Aloft}).
     */
    public static boolean aloft(MechaEntity m) {
        if (m.onGround() || Hosts.pilot(m) == null) return false;
        if (m.pogoFlight || Skitter.out(m)) return true;
        boolean live = m.battery() > 0;
        if (live && (m.frame().locomotion == MechaFrame.Locomotion.WINGS || canFly(m))) return true;
        if (live && (m.has(MechaModule.ROCKET_PACK) || m.has(MechaModule.JUMP_JETS) || m.has(MechaModule.HOVER_SKIRT))) return true;
        return m.has(MechaModule.GLIDER_WINGS);
    }

    /** How fast an animated frame's body turns after its pilot's look, degrees a tick. */
    public static final float BODY_TURN = 15;

    public static void tickRidden(MechaEntity m, Player pilot) {
        if (m.frame().animated()) {
            // the body turns after the pilot (its sensor takes the look at once); a crouched or venting machine holds still
            m.yRotO = m.getYRot();
            if (m.pogoAt() < 0 && m.vent() == 0 && (!Bulwark.rooted(m) || Bulwark.turnsToGrab(m)) && !Siege.rooted(m) && !Executive.rooted(m) && !Chimera.rooted(m)) m.setYRot(net.minecraft.util.Mth.approachDegrees(m.getYRot(), pilot.getYRot(), BODY_TURN));
            m.yBodyRot = m.getYRot();
            m.yHeadRot = pilot.getYRot();
        } else {
            m.setYRot(pilot.getYRot());
            m.yRotO = m.getYRot();
            m.yBodyRot = m.getYRot();
            m.yHeadRot = m.getYRot();
        }
        // while the Dragline holds a machine off the ground, the jump key pays the line out and does nothing else
        boolean jump = pilot.jumping && m.frame().locomotion != MechaFrame.Locomotion.TREADS && !Siege.rooted(m) && !Skitter.lineHolds(m);
        Vec3 v = m.getDeltaMovement();
        boolean fly = jump && canFly(m);
        // the Kite leaves the ground on its takeoff clip's push, after the crouch (Kite.mayLift)
        if (fly && Kite.mayLift(m)) {
            double lift = m.frame().locomotion == MechaFrame.Locomotion.WINGS && thrust(m) <= m.mass() ? 0.08 : 0.12;
            m.setDeltaMovement(v.x, Math.min(0.45, v.y + lift), v.z);
            m.fallDistance = 0;
            return;
        }
        if (m.onGround()) {
            m.jetsLeft = 3;
            if (jump && !fly && m.load() <= 1.0f && m.vent() == 0) {
                int heels = m.indexOf(MechaModule.SPRING_HEELS);
                double power = heels < 0 ? 0.5 : 0.6 + 0.6 * m.sliderAt(heels) / 100.0;
                m.setDeltaMovement(v.x, power * loadJump(m.load()), v.z);
                m.hasImpulse = true;
            }
        } else {
            if (m.has(MechaModule.ROCKET_PACK) && jump && v.y < -0.1) m.setDeltaMovement(v.x, -0.1, v.z);
            if (m.has(MechaModule.GLIDER_WINGS) && jump && v.y < -0.08) {
                Vec3 look = pilot.getLookAngle().multiply(1, 0, 1).normalize().scale(0.35);
                m.setDeltaMovement(look.x, -0.08, look.z);
                m.fallDistance = 0;
            }
        }
        if (m.horizontalCollision && m.isInWater() && m.has(MechaModule.AQUA_FINS)) m.setDeltaMovement(v.x, 0.2, v.z);
        // spiders and Arachnid Legs walk up walls
        if (m.horizontalCollision && (m.frame().locomotion == MechaFrame.Locomotion.SPIDER || m.has(MechaModule.ARACHNID_LEGS)) && m.battery() > 0) {
            int grip = m.indexOf(MechaModule.ARACHNID_LEGS);
            m.setDeltaMovement(m.getDeltaMovement().x, grip < 0 ? 0.2 : 0.12 + 0.16 * m.sliderAt(grip) / 100.0, m.getDeltaMovement().z);
            m.fallDistance = 0;
        } else if (Skitter.clings(m, pilot)) {
            // a frame that climbs holds on to its wall where it is while the pilot gives no input against it
            m.setDeltaMovement(m.getDeltaMovement().x, 0, m.getDeltaMovement().z);
            m.fallDistance = 0;
        }
        MechaSystems.riddenMovement(m);
    }

    /** Why the Jump Jets won't fire now, or null when they will. */
    public static String jetRefusal(MechaEntity m) {
        if (!m.has(MechaModule.JUMP_JETS) || m.onGround()) return null;
        if (m.vent() > 0) return "mecha.venting";
        if (m.jetsLeft <= 0) return "mecha.jets_spent";
        if (m.battery() <= 0) return "mecha.no_power";
        return null;
    }

    /**
     * A push on a ridden mecha. Its pilot's client moves it, and vanilla zeroes a ridden mob's velocity on the server
     * every tick and sends that zero back, so a velocity set here alone is lost: the pilot's client is told to apply it.
     * Without a pilot on a connected client it is set here, as for any mob.
     */
    public static void impulse(MechaEntity m, Vec3 velocity) {
        m.lastImpulse = velocity;
        if (Hosts.pilot(m) instanceof ServerPlayer sp && HopperNet.push(sp, m, velocity)) return;
        m.setDeltaMovement(velocity);
        m.hurtMarked = true;
    }

    /** Jump Jets: a mid-air burst (the client asks; the server spends). */
    public static boolean jet(MechaEntity m) {
        if (!m.has(MechaModule.JUMP_JETS) || m.onGround() || m.jetsLeft <= 0 || m.vent() > 0) return false;
        if (!m.spend(600)) return false;
        m.jetsLeft--;
        m.setHeat(m.heat() + 15);
        Vec3 v = m.getDeltaMovement();
        // the Thrust slider sets the lift: 0.45 to 0.85 (0.65, as before, at the default 50%)
        impulse(m, new Vec3(v.x, 0.45 + 0.4 * m.sliderAt(m.indexOf(MechaModule.JUMP_JETS)) / 100.0, v.z));
        m.fallDistance = 0;
        m.playSound(com.coolfreeze23.headinajar.fx.HouseSounds.JETS.get(), 1, 0.8f);
        // the jets' own burst on an animated frame (each client plays it at the socket)
        if (m.frame().animated()) m.level().broadcastEntityEvent(m, MechaEntity.JET_BURST);
        return true;
    }

    // --- every server tick ---------------------------------------------------------------------------------

    public static void serverTick(MechaEntity m) {
        ServerLevel level = m.serverLevel();
        Player pilot = Hosts.pilot(m);
        boolean moving = m.getDeltaMovement().horizontalDistanceSqr() > 0.001;
        float drain = m.frame().idleDrain();
        float heat = 0;
        for (int i = 0; i < m.frame().totalSlots(); i++) {
            MechaModule mod = m.moduleAt(i);
            if (mod == null) continue;
            drain += mod.idlePower;
            heat += mod.idleHeat;
        }
        if (moving && pilot != null) {
            drain += 4;
            heat += 0.2f;
        }
        if (overloaded(m)) drain *= 1.5f;
        if (pilot == null) drain = 0.5f;
        // a parked machine's cores and tools make no heat: nobody is running them (it never vents parked)
        if (pilot == null) heat = 0;
        // nobody aboard holds a button (an eject, the Golden Parachute or a logout mid-press leaves none held)
        if (pilot == null) m.armsHeld = 0;
        // power in
        int gain = 0;
        if (m.has(MechaModule.GALVANIC_STACK)) gain += 10;
        if (m.has(MechaModule.SOLAR_SKIN) && level.isDay() && level.canSeeSky(m.blockPosition().above((int) m.getBbHeight()))) gain += 15;
        if (m.has(MechaModule.COMBUSTION_KERNEL)) gain += burnFuel(m, pilot != null);
        if (m.has(MechaModule.ROCKET_PACK) && !m.onGround() && m.getDeltaMovement().y > 0 && pilot != null) {
            drain += 120 + 1.8f * m.sliderAt(m.indexOf(MechaModule.ROCKET_PACK));
            heat += 6;
        }
        if (m.frame().locomotion == MechaFrame.Locomotion.WINGS && !m.onGround() && pilot != null) {
            drain += 40;
            heat += 2;
            int aloft = m.getPersistentData().getInt("headinajar_aloft") + 1;
            m.getPersistentData().putInt("headinajar_aloft", aloft);
            if (aloft == 600) com.coolfreeze23.headinajar.Advance.grant(pilot, "frequent_flyer");
        }
        int net = gain - Math.round(drain);
        if (pilot == null && !m.has(MechaModule.COMBUSTION_KERNEL)) {
            // parked: the same FE a tick as ever (the 1 FE drain, less what a Galvanic Stack or Solar Skin brings in),
            // settled once a second, so a parked mecha isn't re-sent to every watcher every tick. A Combustion Kernel
            // burns its fuel a tick at a time and keeps the old path.
            if (m.tickCount % 20 == 0) m.setBattery(m.battery() + 20 * net);
        } else {
            m.setBattery(m.battery() + net);
        }
        MechaSystems.serverTick(m, pilot);
        if (pilot instanceof ServerPlayer flier) Aloft.vehicle(m, flier);
        // Bio-Gel in a Chimera's veins: 1 HP every 5 s while it lasts
        var data = m.getPersistentData();
        int veins = data.getInt(VEINS);
        if (veins > 0) {
            data.putInt(VEINS, veins - 1);
            if (veins % 100 == 0 && m.getHealth() < m.getMaxHealth()) m.heal(1);
        }

        // arms
        if (pilot instanceof ServerPlayer sp) {
            boolean live = m.vent() == 0 && m.battery() > 0;
            // the Bulwark's fists are on a load: the arm buttons wait for them, and say so
            boolean full = Bulwark.handsBusy(m, level.getGameTime());
            // the Colossus's forearms are planted on the ground in Siege: its arms wait (the shoulder weapons still fire)
            boolean planted = Siege.rooted(m);
            int armed = 0;
            for (int side = 0; side < m.frame().slots(MechaModule.Slot.ARM); side++) {
                // each arm slot has its own bit (the shoulders' are set from their side's button: Network.arms)
                int bit = 1 << side;
                boolean held = (m.armsHeld & bit) != 0, before = (m.armsBefore & bit) != 0;
                if (m.arm(side) != null) armed |= 1 << (side % 2);
                if (full) {
                    MechaArms.release(m, side);
                    if (held && !before) Refusals.deny(sp, Refuser.at(m), "mecha.arm.hands_full");
                } else if (planted && side < 2) {
                    MechaArms.release(m, side);
                    if (held && !before && m.arm(side) != null) Refusals.deny(sp, Refuser.at(m), "mecha.siege.arms_planted");
                } else if (live) {
                    m.armActing = side;
                    MechaArms.tick(m, sp, side, held, held && !before, !held && before);
                    m.armActing = -1;
                } else {
                    MechaArms.release(m, side);
                    if (held && !before && m.arm(side) != null) {
                        m.dryFire(side);
                        Refusals.deny(sp, Refuser.at(m), m.vent() > 0 ? "mecha.arm.venting" : "mecha.arm.no_power",
                                Component.translatable("block.headinajar.assembly_gantry"));
                    }
                }
            }
            // a button with no arm behind it on this frame
            int pressed = full ? 0 : m.armsHeld & ~m.armsBefore & ~armed & 3;
            if (pressed != 0) m.dryFire((pressed & 1) != 0 ? 0 : 1);
            if (pressed != 0) Refusals.deny(sp, Refuser.at(m), "mecha.arm.empty", Component.translatable("block.headinajar.assembly_gantry"));
        } else {
            for (int side = 0; side < m.frame().slots(MechaModule.Slot.ARM); side++) MechaArms.release(m, side);
        }
        m.armsBefore = m.armsHeld;

        // heat out
        double env = 1;
        if (m.isUnderWater()) env = 3;
        else if (level.isRainingAt(m.blockPosition().above())) env = 1.5;
        if (level.dimensionType().ultraWarm()) env *= 0.5;
        float dissipation = m.frame().dissipation + (m.has(MechaModule.RADIATOR_FINS) ? 4 : 0) + (m.has(MechaModule.COOLANT_LOOP) ? 6 : 0);
        // anchored in Siege, the back vents stand open: half as much heat again goes out
        if (Siege.locked(m)) dissipation *= Siege.DISSIPATION;
        if (m.isInLava()) heat += 10;
        if (m.isOnFire() && !m.has(MechaModule.FIRE_SUPPRESSION)) heat += 3;
        m.setHeat((float) (m.heat() + heat - dissipation * env));

        if (m.vent() > 0) {
            m.setVent(m.vent() - 1);
            if (m.vent() % 4 == 0) {
                level.sendParticles(ParticleTypes.CLOUD, m.getX(), m.getY() + m.getBbHeight() * 0.6, m.getZ(), 6, 0.8, 0.6, 0.8, 0.02);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, m.getBoundingBox().inflate(3), e -> e != m && e != pilot)) {
                    // once a second of the vent's own count (the machine's age would line up with it in one vent of four)
                    if (m.vent() % 20 == 0) e.hurt(level.damageSources().hotFloor(), 2);
                    e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40));
                }
            }
        } else if (m.heat() >= m.frame().heatCap && pilot != null) {
            vent(m, pilot);
        }

        // torso modules
        if (pilot != null) {
            JarState state = FormActions.state(pilot);
            if (m.has(MechaModule.LIFE_SUPPORT_RECYCLER) && m.battery() > 0) {
                state.setSaline(state.maxSaline());
                state.setCharge(state.chargeCapacity());
                if (m.tickCount % 600 == 0) state.setIntegrity(state.integrity() + 1);
            }
            if (m.has(MechaModule.NIGHT_OPTICS) && m.battery() > 0 && pilot.tickCount % 100 == 0) {
                pilot.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, true, false, false));
            }
            if (m.has(MechaModule.FIRE_SUPPRESSION)) {
                if (pilot.isOnFire()) m.act(MechaModule.FIRE_SUPPRESSION);
                m.clearFire();
                pilot.clearFire();
            }
        }
    }

    private static int burnFuel(MechaEntity m, boolean piloted) {
        var data = m.getPersistentData();
        int fuel = data.getInt("headinajar_fuel");
        if (fuel <= 0) {
            for (int i = 0; i < m.cargo().getContainerSize(); i++) {
                ItemStack stack = m.cargo().getItem(i);
                int burn = stack.getBurnTime(RecipeType.SMELTING);
                if (burn > 0) {
                    stack.shrink(1);
                    fuel = burn * 10;
                    break;
                }
            }
        }
        if (fuel <= 0) return 0;
        int out = Math.min(80, fuel);
        data.putInt("headinajar_fuel", fuel - out);
        if (piloted) m.setHeat(m.heat() + 0.05f * out / 20f);
        return out;
    }

    /** Over its heat limit: everything shuts down for 5 s in a cloud of scalding steam. */
    public static void vent(MechaEntity m, Player pilot) {
        m.setVent(VENT_TICKS);
        m.setHeat(m.frame().heatCap * 0.5f);
        // an animated frame's blast comes with its clip's tick 3 (Hopper.serverTick)
        if (!m.frame().animated()) m.playSound(com.coolfreeze23.headinajar.fx.HouseSounds.VENT_STEAM.get(), 2, 0.6f);
        if (pilot != null && !FormActions.state(pilot).has(ModuleType.HEAT_SHIELD)) {
            pilot.hurt(m.serverLevel().damageSources().hotFloor(), 2);
        }
    }

    // --- damage -------------------------------------------------------------------------------------------

    /** Riot Shield, thorns: returns the damage the frame actually takes. */
    public static float incomingDamage(MechaEntity m, DamageSource source, float amount) {
        if (m.level().isClientSide) return amount;
        Entity attacker = source.getEntity();
        Player pilot = Hosts.pilot(m);
        if (attacker != null && attacker == pilot) return 0;
        if (attacker != null) {
            for (int side = 0; side < 2; side++) {
                if (m.arm(side) == MechaModule.RIOT_SHIELD && (m.armsHeld & (1 << side)) != 0) {
                    Vec3 toAttacker = attacker.position().subtract(m.position()).normalize();
                    if (toAttacker.dot(m.getLookAngle()) > 0.3) amount *= 0.2f;
                }
            }
            if (m.has(MechaModule.THORN_PLATING) && attacker instanceof LivingEntity living && source.getDirectEntity() == attacker) {
                living.hurt(m.serverLevel().damageSources().thorns(m), amount * 0.3f);
            }
        }
        int plating = m.indexOf(MechaModule.ARMOR_PLATING);
        if (plating >= 0) amount *= 1 - (0.1f + 0.002f * m.sliderAt(plating));
        return MechaSystems.absorb(m, source, amount);
    }

    /** Golden Parachute: under 10% health the pilot is thrown clear with slow falling. */
    public static void afterDamage(MechaEntity m) {
        Player pilot = Hosts.pilot(m);
        if (pilot instanceof ServerPlayer sp && m.getHealth() < m.getMaxHealth() * 0.1f && m.isAlive()) GoldenParachute.eject(m, sp);
    }

    public static void eject(MechaEntity m, ServerPlayer pilot) {
        m.ejecting = true;
        FormActions.leaveHost(pilot);
        m.ejecting = false;
        // the buttons held at the moment of the eject are let go: the empty frame doesn't keep firing or shielding
        m.armsHeld = m.armsBefore = 0;
        for (int side = 0; side < m.frame().slots(MechaModule.Slot.ARM); side++) MechaArms.release(m, side);
    }

    /**
     * A wrecked mecha's cockpit protects its jar to the end: the pilot is thrown clear under the Golden Parachute before
     * anything else happens (an animated frame's collapse plays over an empty collar), and the glass takes one knock
     * (never the last point), as in a shell's crash. The pilot is told what happened and what is left. Everyone aboard
     * now is spared the blast that follows, whenever it comes.
     */
    public static void throwClear(MechaEntity m) {
        ServerLevel level = m.serverLevel();
        m.spared.addAll(m.getPassengers());
        if (Hosts.pilot(m) instanceof ServerPlayer pilot) {
            GoldenParachute.eject(m, pilot);
            var state = FormActions.state(pilot);
            if (state.integrity() > 1) state.setIntegrity(state.integrity() - 1);
            level.playSound(null, pilot.getX(), pilot.getY(), pilot.getZ(), com.coolfreeze23.headinajar.fx.HouseSounds.JAR_KNOCK.get(), net.minecraft.sounds.SoundSource.PLAYERS, 1, 0.9f);
            com.coolfreeze23.headinajar.feedback.Warnings.danger(pilot, 1, "mecha.wrecked", m.getDisplayName(), state.integrity(), state.maxIntegrity(),
                    net.minecraft.network.chat.Component.translatable("item.headinajar.glass_patch"));
        }
    }

    /**
     * Destroyed: a puff with no block damage, half the modules drop, the rest is scrap. Whoever is still aboard is thrown
     * clear first ({@link #throwClear}); the blast spares everyone who was.
     */
    public static void destroyed(MechaEntity m) {
        ServerLevel level = m.serverLevel();
        throwClear(m);
        java.util.Set<Entity> aboard = m.spared;
        var spare = new net.minecraft.world.level.ExplosionDamageCalculator() {
            @Override
            public boolean shouldDamageEntity(net.minecraft.world.level.Explosion explosion, Entity entity) {
                return !aboard.contains(entity) && super.shouldDamageEntity(explosion, entity);
            }
        };
        level.explode(m, null, spare, m.getX(), m.getY() + 1, m.getZ(), 2, false, Level.ExplosionInteraction.NONE);
        for (ItemStack stack : m.modules()) {
            if (stack.isEmpty()) continue;
            if (level.random.nextBoolean()) m.spawnAtLocation(stack.copy());
            else m.spawnAtLocation(new ItemStack(com.coolfreeze23.headinajar.registry.ModRegistry.MECHA_PLATING.get()));
        }
        for (int i = 0; i < m.cargo().getContainerSize(); i++) m.spawnAtLocation(m.cargo().removeItemNoUpdate(i));
    }

    // --- shared helpers for arms --------------------------------------------------------------------------

    static BlockHitResult lookBlock(Player pilot, double range) {
        Vec3 eye = pilot.getEyePosition();
        return pilot.level().clip(new ClipContext(eye, eye.add(pilot.getLookAngle().scale(range)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, pilot));
    }

    static Entity lookEntity(Player pilot, MechaEntity m, double range) {
        Vec3 eye = pilot.getEyePosition(), end = eye.add(pilot.getLookAngle().scale(range));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(pilot, eye, end, new AABB(eye, end).inflate(1),
                e -> e != pilot && e != m && !e.isSpectator() && e.isPickable(), range * range);
        return hit == null ? null : hit.getEntity();
    }

    /** Breaks a block as the pilot (protection mods can refuse), dropping into the Cargo Hold if there is one. */
    static boolean breakAs(MechaEntity m, ServerPlayer pilot, BlockPos pos) {
        ServerLevel level = m.serverLevel();
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0 || !level.mayInteract(pilot, pos)) return false;
        if (NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, pilot)).isCanceled()) return false;
        var be = level.getBlockEntity(pos);
        var drops = Block.getDrops(state, level, pos, be, pilot, new ItemStack(Items.IRON_PICKAXE));
        level.destroyBlock(pos, false, pilot);
        for (ItemStack drop : drops) {
            ItemStack left = m.has(MechaModule.CARGO_HOLD) ? m.cargo().addItem(drop) : drop;
            if (!left.isEmpty()) Block.popResource(level, pos, left);
        }
        return true;
    }

    /** A whole tree from one log: connected logs up to 128. */
    static Set<BlockPos> tree(Level level, BlockPos start) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty() && seen.size() < 128) {
            BlockPos p = queue.poll();
            if (!seen.add(p) || !level.getBlockState(p).is(BlockTags.LOGS)) {
                seen.remove(p);
                continue;
            }
            for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos n = p.offset(dx, dy, dz);
                if (!seen.contains(n)) queue.add(n);
            }
        }
        return seen;
    }

    static Direction face(BlockHitResult hit) {
        return hit.getDirection();
    }

    static boolean miss(HitResult hit) {
        return hit == null || hit.getType() == HitResult.Type.MISS;
    }
}
