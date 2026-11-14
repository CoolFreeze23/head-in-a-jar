package com.coolfreeze23.headinajar.client.fx;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.fx.HouseSounds;
import com.coolfreeze23.headinajar.fx.LinkStatic;
import com.coolfreeze23.headinajar.module.ModuleType;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * What a jar sounds like getting about, heard by everyone near it: the Wheels' motor while it drives, the tipped jar
 * rolling, the Propeller while it flies (loops whose pitch and volume follow the jar's speed), and the Hop Springs
 * releasing and the jar landing. And the link heard by the one driving a body over it: static in their own head, in
 * bands by link quality, swelling on exactly the ticks the weak link drops their inputs ({@link LinkStatic}). Every
 * client works it out for every player it can see, from the synced jar state and the player's motion.
 */
public final class JarSounds {
    /** Top speeds the loops are scaled to, blocks a tick: the jar on its wheels, tipped and rolling, flying. */
    static final double WHEELS_TOP = 0.13, ROLL_TOP = 0.03, FLY_TOP = 0.3;
    private static final Map<Integer, Integer> AIRBORNE = new HashMap<>();
    private static final Map<Integer, Long> LANDED = new HashMap<>();
    private static int linkedTicks;

    public static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            AIRBORNE.clear();
            LANDED.clear();
            return;
        }
        for (Player p : mc.level.players()) {
            if (p.distanceToSqr(mc.player) > 32 * 32) continue;
            JarState s = p.getData(ModRegistry.JAR_STATE);
            if (s.form() != Form.JAR || p.isPassenger()) {
                AIRBORNE.remove(p.getId());
                continue;
            }
            double dx = p.getX() - p.xo, dz = p.getZ() - p.zo, dy = p.getY() - p.yo;
            double speed = Math.sqrt(dx * dx + dz * dz);
            if (s.wheels() && s.has(ModuleType.WHEELS) && p.onGround() && speed > 0.005) {
                float f = (float) Math.min(1, speed / WHEELS_TOP);
                SoundLoops.want("drive:" + p.getId(), HouseSounds.DRIVE.get(), SoundSource.PLAYERS, p.position(), 0.35f + 0.65f * f, 0.75f + 0.75f * f);
            }
            if (s.tipped() && p.onGround() && speed > 0.002) {
                float f = (float) Math.min(1, speed / ROLL_TOP);
                SoundLoops.want("roll:" + p.getId(), HouseSounds.ROLL.get(), SoundSource.PLAYERS, p.position(), f, 0.8f + 0.4f * f);
            }
            boolean flying = p == mc.player ? p.getAbilities().flying : !p.onGround() && !p.isInWater();
            if (s.has(ModuleType.PROPELLER) && flying) {
                float f = (float) Math.min(1, (speed + Math.abs(dy)) / FLY_TOP);
                SoundLoops.want("prop:" + p.getId(), HouseSounds.PROPELLER.get(), SoundSource.PLAYERS, p.position().add(0, 0.6, 0), 0.6f + 0.4f * f, 0.75f + 0.55f * f);
            }
            if (s.has(ModuleType.HOP_SPRINGS)) hops(mc, p, dy);
        }
        linkStatic(mc.player);
    }

    /** Hop Springs: the release as the jar leaves the ground going up, the glass and slosh as it comes down. */
    private static void hops(Minecraft mc, Player p, double dy) {
        int id = p.getId();
        int air = AIRBORNE.getOrDefault(id, 0);
        if (!p.onGround()) {
            if (air == 0 && dy > 0.05) play(mc, p, HouseSounds.HOP.get());
            AIRBORNE.put(id, air + 1);
            return;
        }
        AIRBORNE.put(id, 0);
        long now = mc.level.getGameTime();
        if (air >= 3 && now - LANDED.getOrDefault(id, -100L) >= 4) {
            LANDED.put(id, now);
            play(mc, p, HouseSounds.HOP_LAND.get());
        }
    }

    private static void play(Minecraft mc, Player p, SoundEvent sound) {
        mc.level.playLocalSound(p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, 1, HouseSounds.pitch(mc.level.random), false);
    }

    /** The static of a weak link, heard by the body's driver only, in their own head. */
    private static void linkStatic(Player me) {
        JarState s = me.getData(ModRegistry.JAR_STATE);
        if (s.form() != Form.LINKED) {
            linkedTicks = 0;
            return;
        }
        // the half second of the commute's own wipe, while the link's first quality is still on its way, is not static
        if (++linkedTicks <= 10) return;
        int q = s.quality();
        float v = LinkStatic.volume(q, me.level().getGameTime()) * intensity(me);
        if (v <= 0) return;
        SoundEvent sound = switch (LinkStatic.band(q)) {
            case FAINT -> HouseSounds.STATIC_FAINT.get();
            case STEADY -> HouseSounds.STATIC.get();
            default -> HouseSounds.STATIC_HEAVY.get();
        };
        SoundLoops.inHead("link.static", sound, SoundSource.PLAYERS, v, 1);
    }

    /** The player's static-intensity setting (0 to 1), halved by a Quantum Goggles noise filter, as the picture's. */
    private static float intensity(Player me) {
        float base = Config.CLIENT_SPEC.isLoaded() ? Config.STATIC_INTENSITY.get() / 100f : 1f;
        boolean filter = com.coolfreeze23.headinajar.item.QuantumItems.has(me, com.coolfreeze23.headinajar.item.QuantumItems.Upgrade.NOISE_FILTER);
        return filter ? base * 0.5f : base;
    }

    private JarSounds() {
    }
}
