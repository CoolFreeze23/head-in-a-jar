package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.block.ChairSounds;
import com.coolfreeze23.headinajar.block.SeparationChairBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
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
 * The chair's keyframe effects (small sounds and flourishes, on screen only). Every one is dropped if it comes more
 * than two ticks late, so a player who arrives half way through doesn't hear the whole past at once; nothing a
 * keyframe names can throw on the render thread.
 */
public final class ChairKeyframes {
    /** Keyframe sounds are not heard further off than this. */
    private static final double SOUND_RANGE = 16;
    /** The same sound from one chair is not repeated within this many ticks. */
    private static final int REPEAT_TICKS = 4;
    private static final Map<SeparationChairBlockEntity, Map<String, Long>> LAST = new WeakHashMap<>();

    /** Locators: the bone each one rides on and its position in the model (pixels, chair facing north). */
    private record Locator(String bone, Vec3 px) {
    }

    private static final Map<String, Locator> LOCATORS = new HashMap<>();

    static {
        LOCATORS.put("gas", new Locator("boom", new Vec3(0, 27, -5)));
        LOCATORS.put("pour", new Locator("boom", new Vec3(2, 27, 0)));
        LOCATORS.put("spark", new Locator("boom", new Vec3(0, 32, 0)));
        LOCATORS.put("blade_contact", new Locator("neck_anchor", new Vec3(0, 18.5, 0)));
        LOCATORS.put("jar_anchor", new Locator("jar_anchor", new Vec3(0, 7, 0)));
        LOCATORS.put("lamp", new Locator(null, new Vec3(9, 9, -11.5)));
        LOCATORS.put("vent_l", new Locator(null, new Vec3(-5, 2, -1)));
        LOCATORS.put("vent_r", new Locator(null, new Vec3(5, 2, -1)));
    }

    /** The bones whose world position the effects need (their matrices are tracked while drawn). */
    public static final String[] TRACKED = {"neck_anchor", "boom", "jar_anchor"};

    private static boolean late(KeyFrameEvent<?, ?> event) {
        return event.getAnimationTick() - event.getKeyframeData().getStartTick() > 2;
    }

    public static void sound(SoundKeyframeEvent<SeparationChairBlockEntity> event) {
        if (late(event)) return;
        SeparationChairBlockEntity chair = event.getAnimatable();
        Level level = chair.getLevel();
        if (level == null) return;
        String[] parts = event.getKeyframeData().getSound().split("\\|");
        String id = parts[0].trim();
        SoundEvent sound = ChairSounds.byId(id);
        // a name no event has yet plays nothing (listed in the chair notes); never an exception here
        if (sound == null) return;
        float volume = parts.length > 1 ? parse(parts[1], 1) : 1;
        float pitch = parts.length > 2 ? parse(parts[2], 1) : 1;
        Vec3 at = Vec3.atCenterOf(chair.getBlockPos());
        var player = Minecraft.getInstance().player;
        if (player == null || player.distanceToSqr(at) > SOUND_RANGE * SOUND_RANGE) return;
        // the patient is in the silent second after the cut: the plonk and the ding are for the people watching
        if ((id.endsWith("chair.ding") || id.endsWith("chair.plonk")) && player.getUUID().equals(chair.patient())) return;
        Map<String, Long> last = LAST.computeIfAbsent(chair, c -> new HashMap<>());
        long now = level.getGameTime();
        Long before = last.get(id);
        if (before != null && now - before < REPEAT_TICKS && now >= before) return;
        last.put(id, now);
        level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.BLOCKS, volume, ChairSounds.pitch(id, pitch, level.random), false);
    }

    private static float parse(String s, float fallback) {
        try {
            return Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** A locator's position in the world this frame: on its bone as last drawn, else from the rest pose. */
    public static Vec3 locator(SeparationChairBlockEntity chair, String name) {
        Locator locator = LOCATORS.get(name);
        if (locator == null) return Vec3.atCenterOf(chair.getBlockPos());
        GeoBone bone = locator.bone() == null ? null : SeparationChairRenderer.bone(locator.bone());
        if (bone != null && bone.isTrackingMatrices()) {
            Vector4f v = bone.getWorldSpaceMatrix().transform(new Vector4f(
                    (float) (locator.px().x - bone.getPivotX()) / 16f, (float) (locator.px().y - bone.getPivotY()) / 16f,
                    (float) (locator.px().z - bone.getPivotZ()) / 16f, 1));
            return new Vec3(v.x, v.y, v.z);
        }
        return chair.world(locator.px());
    }

    public static void particle(ParticleKeyframeEvent<SeparationChairBlockEntity> event) {
        if (late(event)) return;
        SeparationChairBlockEntity chair = event.getAnimatable();
        Level level = chair.getLevel();
        if (level == null) return;
        String effect = event.getKeyframeData().getEffect();
        Vec3 at = locator(chair, event.getKeyframeData().getLocator());
        switch (effect) {
            case "falling_water" -> spawn(level, ParticleTypes.FALLING_WATER, at, 3, 0.03);
            case "electric_spark" -> {
                // the antenna's sparks: a flash, so none under the photosensitive option (the sound stays)
                if (!LinkHud.photosensitive()) spawn(level, ParticleTypes.ELECTRIC_SPARK, at, 3, 0.06);
                if (ChairSounds.SPARK.isBound()) {
                    level.playLocalSound(at.x, at.y, at.z, ChairSounds.SPARK.get(), SoundSource.BLOCKS, 1,
                            ChairSounds.pitch("chair.spark", 1, level.random), false);
                }
            }
            case "cloud" -> spawn(level, ParticleTypes.CLOUD, at, 2, 0.04);
            default -> {
            }
        }
    }

    /** Timeline instructions: {@code fx:<what>:<locator>:<count>}, read into a fixed table and nothing else. */
    public static void instruction(CustomInstructionKeyframeEvent<SeparationChairBlockEntity> event) {
        if (late(event)) return;
        SeparationChairBlockEntity chair = event.getAnimatable();
        Level level = chair.getLevel();
        if (level == null) return;
        for (String line : event.getKeyframeData().getInstructions().split(";")) {
            String[] p = line.trim().split(":");
            if (p.length < 3 || !p[0].equals("fx")) continue;
            int count = p.length > 3 ? (int) parse(p[3], 1) : 1;
            Vec3 at = locator(chair, p[2]);
            if (p[1].equals("bubbles")) spawn(level, ParticleTypes.BUBBLE_POP, at, Math.min(count, 8), 0.12);
        }
    }

    private static void spawn(Level level, ParticleOptions type, Vec3 at, int count, double spread) {
        var random = level.random;
        for (int i = 0; i < count; i++) {
            level.addParticle(type, at.x + (random.nextDouble() - 0.5) * spread * 2, at.y + (random.nextDouble() - 0.5) * spread,
                    at.z + (random.nextDouble() - 0.5) * spread * 2, 0, 0.01, 0);
        }
    }

    private ChairKeyframes() {
    }
}
