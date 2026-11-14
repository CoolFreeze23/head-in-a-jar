package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.mecha.HopperSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;
import software.bernie.geckolib.animation.keyframe.event.CustomInstructionKeyframeEvent;
import software.bernie.geckolib.animation.keyframe.event.KeyFrameEvent;
import software.bernie.geckolib.animation.keyframe.event.ParticleKeyframeEvent;
import software.bernie.geckolib.animation.keyframe.event.SoundKeyframeEvent;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The walking mecha's keyframe effects, on screen only: servo and latch sounds, steam and sparks at the clip's locators,
 * and the footfalls (the block's own step sound, a metal layer and dust from the block underfoot at the planted foot).
 * The moments that matter to everyone (the pogo's launch and landing, the vent blast, the eject, the collapse) are played
 * by the server instead. Every effect more than two ticks late is dropped, so a late arrival hears no burst of the past.
 */
public final class HopperKeyframes {
    private static final double RANGE = 24;
    private static final int REPEAT_TICKS = 2;
    private static final Map<MechaEntity, Map<String, Long>> LAST = new WeakHashMap<>();


    private HopperKeyframes() {
    }

    private static boolean late(KeyFrameEvent<?, ?> event) {
        return event.getAnimationTick() - event.getKeyframeData().getStartTick() > 2;
    }

    private static boolean near(MechaEntity m) {
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return camera.distanceToSqr(m.position()) < RANGE * RANGE;
    }

    /** A locator's place in the world this frame: on its bone as last drawn, else beside the machine. */
    static Vec3 locator(MechaEntity m, String name) {
        var rig = com.coolfreeze23.headinajar.mecha.MechaRig.of(m.frame());
        var l = rig == null ? null : rig.locators.get(name);
        if (l == null) return m.position().add(0, m.getBbHeight() * 0.6, 0);
        GeoBone bone = HopperRenderer.bone(m.frame(), l.bone());
        org.joml.Matrix4f matrix = m.view.bones.get(l.bone());
        if (bone != null && matrix != null) {
            Vector4f v = matrix.transform(new Vector4f((float) (l.px().x - bone.getPivotX()) / 16f,
                    (float) (l.px().y - bone.getPivotY()) / 16f, (float) (l.px().z - bone.getPivotZ()) / 16f, 1));
            return new Vec3(v.x, v.y, v.z);
        }
        return m.position().add(0, l.px().y / 16, 0);
    }

    public static void sound(SoundKeyframeEvent<MechaEntity> event) {
        if (late(event)) return;
        MechaEntity m = event.getAnimatable();
        if (!near(m)) return;
        String[] parts = event.getKeyframeData().getSound().split("\\|");
        String id = parts[0].trim();
        float volume = parts.length > 1 ? parse(parts[1], 1) : 1, pitch = parts.length > 2 ? parse(parts[2], 1) : 1;
        play(m, m.position().add(0, 1.4, 0), id, volume, pitch);
    }

    /** Plays a mecha sound here unless the same one just played from this machine; an unknown name plays nothing. */
    static void play(MechaEntity m, Vec3 at, String id, float volume, float pitch) {
        SoundEvent sound = HopperSounds.get(m.frame(), id);
        if (sound == null) return;
        Level level = m.level();
        Map<String, Long> last = LAST.computeIfAbsent(m, k -> new HashMap<>());
        long now = level.getGameTime();
        String key = id + pitch;
        Long before = last.get(key);
        if (before != null && now - before < REPEAT_TICKS && now >= before) return;
        last.put(key, now);
        level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.NEUTRAL, volume, HopperSounds.pitch(HopperSounds.resolve(m.frame(), id), pitch, level.random), false);
    }

    public static void particle(ParticleKeyframeEvent<MechaEntity> event) {
        if (late(event)) return;
        MechaEntity m = event.getAnimatable();
        if (!near(m)) return;
        Vec3 at = locator(m, event.getKeyframeData().getLocator());
        switch (event.getKeyframeData().getEffect()) {
            // the anchored Colossus's back vents blast and breathe heat: a fuller plume than a walker's puff
            case "cloud" -> spawn(m.level(), ParticleTypes.CLOUD, at, com.coolfreeze23.headinajar.mecha.Siege.rooted(m) ? 6 : 2,
                    com.coolfreeze23.headinajar.mecha.Siege.rooted(m) ? 0.2 : 0.08);
            case "smoke" -> spawn(m.level(), ParticleTypes.SMOKE, at, 3, 0.06);
            case "large_smoke" -> spawn(m.level(), ParticleTypes.LARGE_SMOKE, at, 2, 0.1);
            case "flame" -> spawn(m.level(), ParticleTypes.FLAME, at, 2, 0.04);
            case "electric_spark" -> {
                if (!LinkHud.photosensitive()) spawn(m.level(), ParticleTypes.ELECTRIC_SPARK, at, 4, 0.08);
            }
            default -> {
            }
        }
    }

    /** Timeline instructions: footfalls, taps, light and heavy landings, sparks. Server moments are ignored here. */
    public static void instruction(CustomInstructionKeyframeEvent<MechaEntity> event) {
        if (late(event)) return;
        MechaEntity m = event.getAnimatable();
        if (!near(m)) return;
        for (String line : event.getKeyframeData().getInstructions().split(";")) {
            String[] p = line.trim().split(":");
            if (p[0].equals("horn")) {
                // the boot's beep-beep
                play(m, locator(m, "fx_hull"), "horn", 1, 1);
                continue;
            }
            if (p.length < 2) continue;
            switch (p[0]) {
                case "step" -> step(m, p[1]);
                case "stomp" -> {
                    // the boot's satisfied stamp at the end of the boot
                    Vec3 at = locator(m, p[1].startsWith("l") ? "fx_step_l" : "fx_step_r");
                    play(m, at, "stomp", 1, 1);
                    dust(m, at, 5);
                }
                case "tap" -> {
                    if (!p[1].equals("r")) play(m, locator(m, "fx_step_l"), "tap", 1, 1);
                    if (!p[1].equals("l")) play(m, locator(m, "fx_step_r"), "tap", 1, 1.05f);
                }
                case "land" -> {
                    boolean heavy = p[1].equals("heavy");
                    // a frame with its own heavy landing plays it; the Hopper's is its heavy step
                    play(m, m.position(), heavy ? (HopperSounds.own(m.frame(), "land_heavy") ? "land_heavy" : "step_heavy") : "land_light", 1, 1);
                    // the feet, on wheels the four tyres, on many legs every pad
                    var rig = com.coolfreeze23.headinajar.mecha.MechaRig.of(m.frame());
                    String[] at = rig != null && rig.wheels != null ? new String[]{"fx_contact_fl", "fx_contact_fr", "fx_contact_rl", "fx_contact_rr"}
                            : rig != null && rig.legs != null ? pads(rig.legs.names())
                            : new String[]{"fx_step_l", "fx_step_r"};
                    for (String foot : at) dust(m, locator(m, foot), heavy ? 6 : 2);
                }
                case "siege" -> {
                    // the spades bite and come out (the cracks and the thud are the server's): the ground shakes
                    if (p[1].equals("anchor") || p[1].equals("unanchor")) HopperClient.shake(m, 0.55f, 14);
                }
                case "graft" -> {
                    // the gulp at the take: a puff off the growth in the colour the veins are about to run
                    if (p[1].equals("take")) {
                        Vec3 at = locator(m, "fx_graft");
                        int c = m.graftTint() != 0 ? m.graftTint() : 0x40C0B0;
                        var dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f((c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f), 0.8f);
                        spawn(m.level(), dust, at, 6, 0.15);
                    }
                }
                case "gene" -> {
                    if (p.length > 2 && p[1].equals("pulse")) GenePorts.pulse(m, (int) parse(p[2], -1));
                }
                case "build" -> {
                    // the ribbon cut at the end of the commissioning: a burst of gold
                    if (p[1].equals("ribbon")) spawn(m.level(), ParticleTypes.WAX_ON, locator(m, "fx_wrist_l"), 12, 0.3);
                }
                case "fx" -> {
                    if (p.length > 2 && p[1].equals("sparks") && !LinkHud.photosensitive()) spawn(m.level(), ParticleTypes.ELECTRIC_SPARK, locator(m, p[2]), 5, 0.1);
                }
                case "arm" -> {
                    // the arm clip's contact: the fist's piston vents, the gun breathes smoke, the shield sheds sparks
                    if (p.length > 2) armContact(m, p[1], p[2].startsWith("r") ? 1 : 0);
                }
                default -> {
                }
            }
        }
    }

    /** What an arm's tool shows on its clip's contact ({@code arm:hit}, {@code arm:fire}, {@code arm:block}). */
    private static void armContact(MechaEntity m, String what, int side) {
        var module = m.arm(side);
        if (module == null) return;
        Vec3 at = com.coolfreeze23.headinajar.mecha.ArmAim.origin(m, side, 1);
        var r = m.level().random;
        int count = 0, rgb = 0;
        com.coolfreeze23.headinajar.client.fx.FxParticle.Look look = com.coolfreeze23.headinajar.client.fx.FxParticle.Look.PUFF;
        if (what.equals("hit") && module == com.coolfreeze23.headinajar.mecha.MechaModule.HYDRAULIC_FIST) {
            count = 3;
            rgb = 0xe8ecef;
        } else if (what.equals("fire") && module == com.coolfreeze23.headinajar.mecha.MechaModule.RIVET_GUN) {
            count = 1;
            rgb = 0x8a8f96;
        } else if (what.equals("block") && module == com.coolfreeze23.headinajar.mecha.MechaModule.RIOT_SHIELD) {
            count = 4;
            rgb = com.coolfreeze23.headinajar.client.fx.FxColors.POWER_PALE;
            look = com.coolfreeze23.headinajar.client.fx.FxParticle.Look.SPARK;
        }
        for (int i = 0; i < com.coolfreeze23.headinajar.client.fx.Comfort.particles(count); i++) {
            var f = com.coolfreeze23.headinajar.client.fx.FxParticle.make(look, at, new Vec3(r.nextGaussian() * 0.06, 0.05 + r.nextDouble() * 0.05, r.nextGaussian() * 0.06));
            if (f == null) continue;
            if (look == com.coolfreeze23.headinajar.client.fx.FxParticle.Look.SPARK) f.color(rgb).size(0.06f).gravity(0.7f).life(8).add();
            else f.color(rgb).alpha(0.7f).size(0.2f).grow(2.5f).life(16).drag(0.9f).gravity(-0.05f).add();
        }
    }

    static String[] pads(String[] legs) {
        String[] out = new String[legs.length];
        for (int i = 0; i < legs.length; i++) out[i] = "fx_foot_" + legs[i];
        return out;
    }

    /**
     * Footfalls on many legs: the pads named ({@code fl_rr}, a diagonal pair; {@code fl}, one), each the block's own
     * step sound high and quiet with a little dust; {@code fl_drag} that pad scraping; {@code light} a quiet patter with
     * no dust; {@code grip} every pad taking hold of a wall.
     */
    static void pads(MechaEntity m, String[] legs, String which, java.util.function.BiFunction<MechaEntity, String, Vec3> locate) {
        boolean drag = which.endsWith("_drag");
        if (which.equals("light") || which.equals("grip")) {
            play(m, locate.apply(m, "fx_hull"), which.equals("grip") ? "servo_catch" : "step_light", 1, 1.2f);
            return;
        }
        for (String part : which.split("_")) {
            if (!java.util.Arrays.asList(legs).contains(part)) continue;
            Vec3 at = locate.apply(m, "fx_foot_" + part);
            BlockPos below = BlockPos.containing(at.x, at.y - 0.2, at.z);
            BlockState state = m.level().getBlockState(below);
            if (state.isAir()) below = BlockPos.containing(at.x, m.getY() - 0.2, at.z);
            state = m.level().getBlockState(below);
            if (!state.isAir()) {
                SoundType type = state.getSoundType(m.level(), below, m);
                m.level().playLocalSound(at.x, at.y, at.z, type.getStepSound(), SoundSource.NEUTRAL, type.getVolume() * 0.15f, type.getPitch() * 1.4f, false);
                spawn(m.level(), new BlockParticleOption(ParticleTypes.BLOCK, state), new Vec3(at.x, at.y + 0.05, at.z), 1, 0.15);
            }
            play(m, at, drag ? "step_drag" : "step_light", 1, 1.1f);
        }
    }

    /** One footfall: {@code l}, {@code r}, with {@code _light} (half weight, no dust), {@code _heavy} (the heavy walk) or {@code _drag} (a scrape). */
    private static void step(MechaEntity m, String which) {
        var rig = com.coolfreeze23.headinajar.mecha.MechaRig.of(m.frame());
        if (rig != null && rig.legs != null) {
            pads(m, rig.legs.names(), which, HopperKeyframes::locator);
            return;
        }
        String foot = which.startsWith("l") ? "fx_step_l" : "fx_step_r";
        Vec3 at = locator(m, foot);
        boolean light = which.endsWith("_light"), drag = which.endsWith("_drag");
        // the Colossus's quake (a dust ring and a small shake for whoever stands near), the Executive's quiet step
        boolean quake = which.endsWith("_quake"), quiet = which.endsWith("_quiet"), heavy = which.endsWith("_heavy") || quake;
        BlockPos below = BlockPos.containing(at.x, m.getY() - 0.2, at.z);
        BlockState state = m.level().getBlockState(below);
        if (!state.isAir()) {
            SoundType type = state.getSoundType(m.level(), below, m);
            m.level().playLocalSound(at.x, at.y, at.z, type.getStepSound(), SoundSource.NEUTRAL,
                    type.getVolume() * (light ? 0.1f : quake ? 0.4f : heavy ? 0.3f : quiet ? 0.12f : 0.2f), type.getPitch() * (quake ? 0.8f : heavy ? 0.9f : 1.2f), false);
        }
        play(m, at, light ? "step_light" : quake ? "step_quake" : quiet ? "step_quiet" : heavy ? "step_heavy" : "step", 1, 1);
        if (drag) play(m, at, "step_drag", 1, 1);
        if (quake) {
            ring(m, at, 8);
            HopperClient.shake(m, 0.25f, 10);
        } else if (!light && !quiet) dust(m, at, heavy ? 4 : 2);
        else if (quiet) dust(m, at, 1);
    }

    private static void dust(MechaEntity m, Vec3 at, int count) {
        BlockPos below = BlockPos.containing(at.x, m.getY() - 0.2, at.z);
        BlockState state = m.level().getBlockState(below);
        if (state.isAir()) return;
        spawn(m.level(), new BlockParticleOption(ParticleTypes.BLOCK, state), new Vec3(at.x, m.getY() + 0.05, at.z), count, 0.25);
    }

    /** A ring of the block's dust round a footfall. */
    private static void ring(MechaEntity m, Vec3 at, int count) {
        BlockPos below = BlockPos.containing(at.x, m.getY() - 0.2, at.z);
        BlockState state = m.level().getBlockState(below);
        if (state.isAir()) return;
        var particle = new BlockParticleOption(ParticleTypes.BLOCK, state);
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2 / count;
            m.level().addParticle(particle, at.x + Math.cos(a) * 0.5, m.getY() + 0.05, at.z + Math.sin(a) * 0.5, Math.cos(a) * 0.08, 0.02, Math.sin(a) * 0.08);
        }
    }

    private static void spawn(Level level, ParticleOptions type, Vec3 at, int count, double spread) {
        var random = level.random;
        for (int i = 0; i < count; i++) {
            level.addParticle(type, at.x + (random.nextDouble() - 0.5) * spread * 2, at.y + random.nextDouble() * spread,
                    at.z + (random.nextDouble() - 0.5) * spread * 2, (random.nextDouble() - 0.5) * 0.05, 0.03, (random.nextDouble() - 0.5) * 0.05);
        }
    }

    private static float parse(String s, float fallback) {
        try {
            return Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
