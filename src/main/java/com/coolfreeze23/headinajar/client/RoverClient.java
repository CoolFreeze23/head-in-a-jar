package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.entity.RoverAnimation;
import com.coolfreeze23.headinajar.entity.ShellEntity;
import com.coolfreeze23.headinajar.mecha.HopperSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import software.bernie.geckolib.animation.keyframe.event.KeyFrameEvent;
import software.bernie.geckolib.animation.keyframe.event.ParticleKeyframeEvent;
import software.bernie.geckolib.animation.keyframe.event.SoundKeyframeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Rover's side of the client: its clips' sounds and particles, its little motor's hum, and the rider's own player
 * hidden while the Rover draws the jar on its tray (and while the get-off clip hops it off). The Rover has no sound set
 * of its own yet: each of its clip sounds borrows the nearest of the machines' ({@link #BORROWED}).
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID, value = Dist.CLIENT)
public final class RoverClient {
    /** The Rover's and the Spider Walker's clip sounds, by what each borrows ({@link com.coolfreeze23.headinajar.mecha.ShellSounds}). */
    static final Map<String, String> BORROWED = com.coolfreeze23.headinajar.mecha.ShellSounds.BORROWED;
    private static final Map<ShellEntity, Map<String, Long>> LAST = new WeakHashMap<>();
    private static final Map<Integer, Motor> MOTORS = new HashMap<>();

    private RoverClient() {
    }

    /** The event a Lunk clip sound plays: its own (mecha.lunk.*), else the Bulwark's, else the shared set; or null. */
    public static SoundEvent lunkSound(String id) {
        String name = id.startsWith("headinajar:") ? id.substring(11) : id;
        if (name.startsWith("mecha.")) name = name.substring(6);
        SoundEvent own = HopperSounds.get("lunk." + name);
        if (own != null) return own;
        SoundEvent heavy = HopperSounds.get("bulwark." + name);
        return heavy != null ? heavy : HopperSounds.get(name);
    }

    /** The event a Rover clip sound plays, or null. */
    public static SoundEvent sound(String id) {
        String name = id.startsWith("headinajar:") ? id.substring(11) : id;
        if (name.startsWith("mecha.")) name = name.substring(6);
        return HopperSounds.get(BORROWED.getOrDefault(name, name));
    }

    private static boolean late(KeyFrameEvent<?, ?> event) {
        return event.getAnimationTick() - event.getKeyframeData().getStartTick() > 2;
    }

    private static boolean near(ShellEntity s) {
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return camera.distanceToSqr(s.position()) < 24 * 24;
    }

    public static void sound(SoundKeyframeEvent<ShellEntity> event) {
        ShellEntity s = event.getAnimatable();
        if (late(event) || !near(s)) return;
        String[] parts = event.getKeyframeData().getSound().split("\\|");
        SoundEvent sound = s.kind() == ShellEntity.Kind.LUNK ? lunkSound(parts[0].trim()) : sound(parts[0].trim());
        if (sound == null) return;
        float volume = parts.length > 1 ? parse(parts[1]) : 1, pitch = parts.length > 2 ? parse(parts[2]) : 1;
        Map<String, Long> last = LAST.computeIfAbsent(s, k -> new HashMap<>());
        long now = s.level().getGameTime();
        Long before = last.put(parts[0] + pitch, now);
        if (before != null && now - before < 2 && now >= before) return;
        s.level().playLocalSound(s.getX(), s.getY() + 0.6, s.getZ(), sound, SoundSource.NEUTRAL, volume, pitch, false);
    }

    public static void particle(ParticleKeyframeEvent<ShellEntity> event) {
        ShellEntity s = event.getAnimatable();
        if (late(event) || !near(s)) return;
        Vec3 at = locator(s, event.getKeyframeData().getLocator());
        var r = s.level().random;
        String effect = event.getKeyframeData().getEffect();
        if (effect.equals("web_strand")) {
            // the spit: a white thread from the nozzle to where the web goes
            var to = net.minecraft.world.phys.Vec3.atCenterOf(s.webTarget());
            var dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.95f, 0.95f, 0.92f), 0.6f);
            int n = (int) Math.min(40, at.distanceTo(to) * 4);
            for (int i = 0; i <= n; i++) {
                var p = at.lerp(to, i / (double) Math.max(1, n));
                s.level().addParticle(dust, p.x, p.y, p.z, 0, 0, 0);
            }
            return;
        }
        var type = switch (effect) {
            case "dry_puff" -> ParticleTypes.SMOKE;
            case "large_smoke" -> ParticleTypes.LARGE_SMOKE;
            case "smoke" -> ParticleTypes.SMOKE;
            case "cloud" -> ParticleTypes.CLOUD;
            case "electric_spark" -> LinkHud.photosensitive() ? null : ParticleTypes.ELECTRIC_SPARK;
            default -> null;
        };
        if (type == null) return;
        for (int i = 0; i < 3; i++) s.level().addParticle(type, at.x + (r.nextDouble() - 0.5) * 0.15, at.y, at.z + (r.nextDouble() - 0.5) * 0.15, 0, 0.03, 0);
    }

    /**
     * The Lunk's timeline: its footfalls (the block's own step sound, its stubby step and a little dust at the foot) and
     * its knuckles planting (the gloved thump and dust at the fist). The rest of its moments are the server's.
     */
    public static void instruction(software.bernie.geckolib.animation.keyframe.event.CustomInstructionKeyframeEvent<ShellEntity> event) {
        ShellEntity s = event.getAnimatable();
        if (s.kind() == ShellEntity.Kind.SPIDER_WALKER) {
            walkerInstruction(event);
            return;
        }
        if (late(event) || !near(s)) return;
        for (String line : event.getKeyframeData().getInstructions().split(";")) {
            String[] p = line.trim().split(":");
            if (p.length < 2) continue;
            switch (p[0]) {
                case "step" -> {
                    Vec3 at = locator(s, p[1].startsWith("l") ? "fx_step_l" : "fx_step_r");
                    var below = net.minecraft.core.BlockPos.containing(at.x, s.getY() - 0.2, at.z);
                    var state = s.level().getBlockState(below);
                    if (!state.isAir()) {
                        var type = state.getSoundType(s.level(), below, s);
                        s.level().playLocalSound(at.x, at.y, at.z, type.getStepSound(), SoundSource.NEUTRAL, type.getVolume() * 0.2f, type.getPitch(), false);
                    }
                    playAt(s, at, "step_lunk");
                    dust(s, at, 2);
                }
                case "knuckle", "knuckles" -> {
                    for (String side : p[1].equals("both") ? new String[]{"l", "r"} : new String[]{p[1]}) {
                        Vec3 at = locator(s, side.equals("l") ? "fx_knuckle_l" : "fx_knuckle_r");
                        playAt(s, at, "knuckle");
                        dust(s, at, 2);
                    }
                }
                case "fx" -> {
                    if (p.length > 2 && p[1].equals("sparks") && !LinkHud.photosensitive()) {
                        Vec3 at = locator(s, p[2]);
                        for (int i = 0; i < 5; i++) s.level().addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 0, 0.05, 0);
                    }
                }
                default -> {
                }
            }
        }
    }

    private static void playAt(ShellEntity s, Vec3 at, String name) {
        SoundEvent sound = lunkSound(name);
        if (sound != null) s.level().playLocalSound(at.x, at.y, at.z, sound, SoundSource.NEUTRAL, 1,
                HopperSounds.pitch("lunk." + name, 1, s.level().random), false);
    }

    private static void dust(ShellEntity s, Vec3 at, int count) {
        var below = net.minecraft.core.BlockPos.containing(at.x, s.getY() - 0.2, at.z);
        var state = s.level().getBlockState(below);
        if (state.isAir()) return;
        var r = s.level().random;
        var particle = new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, state);
        for (int i = 0; i < count; i++) s.level().addParticle(particle, at.x + (r.nextDouble() - 0.5) * 0.3, s.getY() + 0.05, at.z + (r.nextDouble() - 0.5) * 0.3, 0, 0.02, 0);
    }

    /** A locator from its bone as last drawn (the Lunk's and the Spider Walker's), else on the collar (the Rover's only one). */
    static Vec3 locator(ShellEntity s, String name) {
        if (s.kind() == ShellEntity.Kind.LUNK) {
            Object[] l = com.coolfreeze23.headinajar.entity.LunkAnimation.LOCATORS.get(name);
            if (l == null) return s.position().add(0, s.getBbHeight() * 0.6, 0);
            var matrix = s.view.bones.get((String) l[0]);
            var bone = RoverRenderer.bone(s.kind(), (String) l[0]);
            if (matrix == null || bone == null) return s.position().add(0, (double) l[2] / 16, 0);
            var v = matrix.transform(new org.joml.Vector4f((float) (((double) l[1]) - bone.getPivotX()) / 16f,
                    (float) (((double) l[2]) - bone.getPivotY()) / 16f, (float) (((double) l[3]) - bone.getPivotZ()) / 16f, 1));
            return new Vec3(v.x, v.y, v.z);
        }
        var l = s.kind() == ShellEntity.Kind.SPIDER_WALKER ? com.coolfreeze23.headinajar.entity.SpiderWalkerAnimation.LOCATORS.get(name) : null;
        if (l != null) {
            var matrix = s.walker.bones.get(l.bone());
            var bone = RoverRenderer.model(s.kind()).cached(l.bone());
            if (matrix == null || bone == null) return s.position().add(0, l.px().y / 16, 0);
            var v = matrix.transform(new org.joml.Vector4f((float) (l.px().x - bone.getPivotX()) / 16f, (float) (l.px().y - bone.getPivotY()) / 16f,
                    (float) (l.px().z - bone.getPivotZ()) / 16f, 1));
            return new Vec3(v.x, v.y, v.z);
        }
        var m = s.view.bones.get("jar_anchor");
        if (m == null) return s.position().add(0, 1, 0);
        var v = m.transform(new org.joml.Vector4f(0, 2 / 16f, 0, 1));
        return new Vec3(v.x, v.y, v.z);
    }

    private static float parse(String s) {
        try {
            return Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /**
     * The Spider Walker's timeline: footfalls ({@code step:a} and {@code step:b}, a set of four pads each; {@code light}
     * a quiet patter; {@code wall} a scrabble on the wall's block), its light landing, the collapse's sparks.
     */
    private static void walkerInstruction(software.bernie.geckolib.animation.keyframe.event.CustomInstructionKeyframeEvent<ShellEntity> event) {
        ShellEntity s = event.getAnimatable();
        if (late(event) || !near(s)) return;
        for (String line : event.getKeyframeData().getInstructions().split(";")) {
            String[] p = line.trim().split(":");
            if (p.length < 2) continue;
            switch (p[0]) {
                case "step" -> {
                    String[] pads = p[1].equals("a") ? com.coolfreeze23.headinajar.entity.SpiderWalkerAnimation.SET_A
                            : p[1].equals("b") ? com.coolfreeze23.headinajar.entity.SpiderWalkerAnimation.SET_B : new String[0];
                    if (pads.length == 0) {
                        play(s, locator(s, "fx_hull"), p[1].equals("wall") ? "tin_scrape" : "step_light", 0.25f, 1.3f);
                        if (p[1].equals("wall")) wall(s);
                        continue;
                    }
                    for (String pad : pads) footfall(s, locator(s, "fx_foot_" + pad));
                }
                case "land" -> {
                    for (String pad : com.coolfreeze23.headinajar.entity.SpiderWalkerAnimation.LEGS.names()) footfall(s, locator(s, "fx_foot_" + pad));
                }
                case "fx" -> {
                    if (p.length > 2 && p[1].equals("sparks") && !LinkHud.photosensitive()) {
                        Vec3 at = locator(s, p[2]);
                        for (int i = 0; i < 5; i++) s.level().addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 0, 0.05, 0);
                    }
                }
                default -> {
                }
            }
        }
    }

    /** One pad down: the block's own step sound, high and very quiet, and a speck of its dust. */
    private static void footfall(ShellEntity s, Vec3 at) {
        var level = s.level();
        var below = net.minecraft.core.BlockPos.containing(at.x, s.getY() - 0.2, at.z);
        var state = level.getBlockState(below);
        if (state.isAir()) return;
        var type = state.getSoundType(level, below, s);
        level.playLocalSound(at.x, at.y, at.z, type.getStepSound(), SoundSource.NEUTRAL, type.getVolume() * 0.08f, type.getPitch() * 1.6f, false);
        level.addParticle(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, state), at.x, s.getY() + 0.05, at.z, 0, 0.02, 0);
    }

    /** The front points on the wall's block: its own hit sound, quiet. */
    private static void wall(ShellEntity s) {
        var face = s.walker.climb.face;
        if (face == null) return;
        var pos = s.blockPosition().relative(face);
        if (s.level().getBlockState(pos).isAir()) pos = pos.above();
        var state = s.level().getBlockState(pos);
        if (state.isAir()) return;
        var type = state.getSoundType(s.level(), pos, s);
        s.level().playLocalSound(pos.getX() + 0.5, s.getY() + 0.5, pos.getZ() + 0.5, type.getHitSound(), SoundSource.NEUTRAL, type.getVolume() * 0.12f,
                type.getPitch() * 1.5f, false);
    }

    private static void play(ShellEntity s, Vec3 at, String id, float volume, float pitch) {
        SoundEvent sound = sound(id);
        if (sound != null) s.level().playLocalSound(at.x, at.y, at.z, sound, SoundSource.NEUTRAL, volume, pitch, false);
    }

    /** The rider's player is not drawn: the Rover draws the jar on its tray. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void hideRider(RenderPlayerEvent.Pre event) {
        if (RoverRenderer.drawsRider(event.getEntity()) || RoverRenderer.hoppingOff(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            MOTORS.clear();
            return;
        }
        for (var e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ShellEntity s) || s.kind() != ShellEntity.Kind.ROVER) continue;
            Motor motor = MOTORS.get(s.getId());
            boolean wanted = RoverAnimation.piloted(s) && s.isAlive() && mc.player.distanceToSqr(s) < 20 * 20 && s.view.wheels.speed() > 0.01;
            if (wanted && (motor == null || motor.isStopped())) {
                var sound = HopperSounds.get("motor");
                if (sound == null) continue;
                motor = new Motor(s, sound);
                MOTORS.put(s.getId(), motor);
                mc.getSoundManager().play(motor);
            }
        }
        MOTORS.values().removeIf(Motor::isStopped);
        // a Lunk's toss: the held creature rides in the fists on this client too (never the local player: they move themselves)
        for (var e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ShellEntity s) || s.kind() != ShellEntity.Kind.LUNK || s.tossHeldId() < 0 || s.tossAt() < 0) continue;
            double t = mc.level.getGameTime() - s.tossAt() / 4;
            var load = mc.level.getEntity(s.tossHeldId());
            if (load != null && load != mc.player && t >= com.coolfreeze23.headinajar.entity.LunkAnimation.TOSS_GRAB
                    && t < com.coolfreeze23.headinajar.entity.LunkAnimation.TOSS_RELEASE) s.hold(load, t);
        }
    }

    /** The trolley's motor: a higher, thinner hum than a frame's, from its measured speed. */
    static final class Motor extends AbstractTickableSoundInstance {
        private final ShellEntity s;
        private float level;

        Motor(ShellEntity s, SoundEvent sound) {
            super(sound, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.s = s;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.001f;
            this.pitch = 1.3f;
            this.x = s.getX();
            this.y = s.getY() + 0.5;
            this.z = s.getZ();
        }

        @Override
        public void tick() {
            if (s.isRemoved() || !s.isAlive()) {
                stop();
                return;
            }
            float target = RoverAnimation.piloted(s) ? (float) Mth.clamp(s.view.wheels.speed() / RoverAnimation.SPEC.driveDesign(), 0, 1) : 0;
            level += (target - level) * 0.2f;
            pitch = 1.3f + 0.4f * level;
            volume = 0.6f * (float) Math.sqrt(level);
            x = s.getX();
            y = s.getY() + 0.5;
            z = s.getZ();
            if (volume < 0.01f && target == 0) stop();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
