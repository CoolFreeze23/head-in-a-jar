package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.machine.MachineSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.keyframe.event.KeyFrameEvent;
import software.bernie.geckolib.animation.keyframe.event.ParticleKeyframeEvent;
import software.bernie.geckolib.animation.keyframe.event.SoundKeyframeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The kit machines' keyframe effects (small sounds and flourishes, on screen only), the chair's rules: an effect more
 * than two ticks late is dropped (an arrival half way through hears nothing of the past), an id with no event plays
 * nothing and never throws, 16 blocks' range, the same sound from one machine not twice within 3 ticks. A key's sound
 * is {@code id|volume|pitch}; the event's own volume is used (the files carry their loudness), the key's pitch kept.
 */
public final class MachineKeyframes {
    private static final double SOUND_RANGE = 16;
    private static final Map<BlockEntity, Map<String, Long>> LAST = new WeakHashMap<>();
    /** Locators (pixels, the machine facing north) of every kit machine, by name. */
    private static final Map<String, Vec3> LOCATORS = new HashMap<>();

    static {
        // Galvanic Pile
        LOCATORS.put("term_a", new Vec3(-1, 27, -5));
        LOCATORS.put("term_b", new Vec3(1, 27, -5));
        LOCATORS.put("screw_top", new Vec3(0, 28, -2));
        LOCATORS.put("pan", new Vec3(0, 5, 0));
        // Saline Mixer
        LOCATORS.put("dose_s", new Vec3(0, 21.5, -3));
        LOCATORS.put("dose_k", new Vec3(-3, 21.5, 3));
        LOCATORS.put("dose_r", new Vec3(3, 21.5, 3));
        LOCATORS.put("spout", new Vec3(-2, 9, -3.5));
        LOCATORS.put("tank", new Vec3(0, 18, 0));
        // Centrifuge
        LOCATORS.put("whine", new Vec3(0, 21, 0));
        LOCATORS.put("rack", new Vec3(0, 17, -6));
        LOCATORS.put("drawer_front", new Vec3(-2, 9, -5));
        // Life Support Cradle
        LOCATORS.put("valve", new Vec3(0, 22, -1));
        LOCATORS.put("crest", new Vec3(0, 28, 5));
        LOCATORS.put("lap", new Vec3(0, 7, -10));
        LOCATORS.put("bag", new Vec3(11, 17, -1.5));
        // Body Printer
        LOCATORS.put("doorway", new Vec3(0, 12, -10));
        LOCATORS.put("column_top", new Vec3(8, 27, -9));
        // Gene Splicer
        LOCATORS.put("needle", new Vec3(5, 15, 0));
        LOCATORS.put("cassette", new Vec3(-3, 20, -6));
        LOCATORS.put("strand", new Vec3(1, 15, 0));
        // Cerebral Array
        LOCATORS.put("gavel", new Vec3(0, 28, 3.5));
        LOCATORS.put("urn", new Vec3(-5, 32, 5.5));
        // Employee Mount
        LOCATORS.put("jar_top", new Vec3(0, 17.5, 0));
        // DNA Sequencer
        LOCATORS.put("receipt", new Vec3(-2, 34, 4.5));
        // Tesla Node
        LOCATORS.put("coil", new Vec3(0, 13, -3));
        LOCATORS.put("crown", new Vec3(0, 22, 0));
        // Charging Dock
        LOCATORS.put("jaws", new Vec3(6, 26, 3.5));
        // Neural Socket (a closed clamp's top)
        LOCATORS.put("claw_fl", new Vec3(7, 16.5, -7));
    }

    /** A point of the model (pixels, facing north) in the world, turned with the machine. */
    public static Vec3 world(BlockEntity machine, Vec3 px) {
        var state = machine.getBlockState();
        Direction facing = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
        float turn = switch (facing) {
            case SOUTH -> 180;
            case WEST -> 90;
            case EAST -> 270;
            default -> 0;
        };
        Vec3 v = new Vec3(px.x / 16, px.y / 16, px.z / 16).yRot((float) Math.toRadians(turn));
        var p = machine.getBlockPos();
        return new Vec3(p.getX() + 0.5 + v.x, p.getY() + v.y, p.getZ() + 0.5 + v.z);
    }

    public static Vec3 locator(BlockEntity machine, String name) {
        Vec3 px = name == null ? null : LOCATORS.get(name);
        return px == null ? Vec3.atCenterOf(machine.getBlockPos()) : world(machine, px);
    }

    private static boolean late(KeyFrameEvent<?, ?> event) {
        return event.getAnimationTick() - event.getKeyframeData().getStartTick() > 2;
    }

    public static <T extends BlockEntity & software.bernie.geckolib.animatable.GeoAnimatable> void sound(SoundKeyframeEvent<T> event) {
        if (late(event)) return;
        String[] parts = event.getKeyframeData().getSound().split("\\|");
        play(event.getAnimatable(), parts[0].trim(), parts.length > 2 ? parse(parts[2], 1) : 1, null);
    }

    /** Plays a kit sound at the machine (or at {@code at}), at the event's volume and {@code pitch}. */
    /**
     * A clip's instruction keyframe naming a sound, {@code family:name;} (the Locker's check-in and portrait): played as
     * the event {@code family.name}. Anything else in an instruction is left alone.
     */
    public static <T extends BlockEntity & software.bernie.geckolib.animatable.GeoAnimatable> void instruction(
            software.bernie.geckolib.animation.keyframe.event.CustomInstructionKeyframeEvent<T> event) {
        for (String line : event.getKeyframeData().getInstructions().split(";")) {
            String id = line.trim();
            if (id.matches("[a-z]+:[a-z_]+")) play(event.getAnimatable(), id.replace(':', '.'), 1, null);
        }
    }

    public static void play(BlockEntity machine, String id, float pitch, Vec3 at) {
        Level level = machine.getLevel();
        if (level == null) return;
        SoundEvent sound = MachineSounds.byId(id);
        if (sound == null) return;
        Vec3 where = at != null ? at : Vec3.atCenterOf(machine.getBlockPos());
        var player = Minecraft.getInstance().player;
        if (player == null || player.distanceToSqr(where) > SOUND_RANGE * SOUND_RANGE) return;
        Map<String, Long> last = LAST.computeIfAbsent(machine, m -> new HashMap<>());
        long now = level.getGameTime();
        String key = id + pitch;
        Long before = last.get(key);
        if (before != null && now - before < 3 && now >= before) return;
        last.put(key, now);
        level.playLocalSound(where.x, where.y, where.z, sound, SoundSource.BLOCKS, MachineSounds.volume(id), MachineSounds.pitch(id, pitch, level.random), false);
    }

    private static float parse(String s, float fallback) {
        try {
            return Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static <T extends BlockEntity & software.bernie.geckolib.animatable.GeoAnimatable> void particle(ParticleKeyframeEvent<T> event) {
        if (late(event)) return;
        T machine = event.getAnimatable();
        Level level = machine.getLevel();
        if (level == null) return;
        String effect = event.getKeyframeData().getEffect();
        Vec3 at = locator(machine, event.getKeyframeData().getLocator());
        switch (effect) {
            case "headinajar:pile_spark" -> {
                // the spark across the terminals' gap: a flash, so none under the photosensitive option
                if (LinkHud.photosensitive()) return;
                Vec3 b = locator(machine, "term_b");
                for (int i = 0; i <= 3; i++) {
                    Vec3 p = at.lerp(b, i / 3.0);
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, p.y + (level.random.nextDouble() - 0.5) * 0.03, p.z, 0, 0, 0);
                }
            }
            case "minecraft:poof" -> spawn(level, ParticleTypes.POOF, at, 2, 0.04, 0.01);
            case "headinajar:mixer_dose_sugar" -> spawn(level, new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.SUGAR)), at, 4, 0.05, -0.05);
            case "headinajar:mixer_dose_kelp" -> spawn(level, new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.DRIED_KELP)), at, 4, 0.05, -0.05);
            case "headinajar:mixer_dose_redstone" -> spawn(level, ParticleTypes.DUST_PLUME, at, 0, 0, 0);
            case "headinajar:mixer_stream" -> spawn(level, ParticleTypes.FALLING_WATER, at, 1, 0.01, 0);
            case "headinajar:cradle_drip" -> spawn(level, ParticleTypes.DRIPPING_WATER, at, 1, 0.01, 0);
            case "headinajar:cradle_fluff" -> spawn(level, ParticleTypes.WHITE_ASH, at, 4, 0.2, 0.01);
            case "headinajar:cradle_puff" -> spawn(level, ParticleTypes.POOF, at, 2, 0.03, 0.01);
            case "minecraft:wax_on" -> spawn(level, ParticleTypes.WAX_ON, at, 3, 0.06, 0.02);
            case "minecraft:electric_spark" -> {
                if (!LinkHud.photosensitive()) spawn(level, ParticleTypes.ELECTRIC_SPARK, at, 3, 0.05, 0.02);
                // the Charging Dock's jaws snap as they spark (heard under the photosensitive option too: it is not a flash)
                if (machine.getBlockState().getBlock() instanceof com.coolfreeze23.headinajar.block.ChargingDockBlock) play(machine, "dock.spark", 1, at);
            }
            case "minecraft:smoke" -> spawn(level, ParticleTypes.SMOKE, at, 3, 0.04, 0.02);
            case "minecraft:happy_villager" -> spawn(level, ParticleTypes.HAPPY_VILLAGER, at, 2, 0.08, 0.02);
            case "headinajar:sequencer_paper" -> spawn(level, new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.PAPER)), at, 4, 0.05, -0.04);
            default -> {
            }
        }
        if (effect.equals("headinajar:mixer_dose_redstone")) {
            spawn(level, new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.REDSTONE)), at, 4, 0.05, -0.05);
        }
    }

    private static void spawn(Level level, ParticleOptions type, Vec3 at, int count, double spread, double dy) {
        var random = level.random;
        for (int i = 0; i < count; i++) {
            level.addParticle(type, at.x + (random.nextDouble() - 0.5) * spread * 2, at.y + (random.nextDouble() - 0.5) * spread,
                    at.z + (random.nextDouble() - 0.5) * spread * 2, 0, dy, 0);
        }
    }

    private MachineKeyframes() {
    }
}
