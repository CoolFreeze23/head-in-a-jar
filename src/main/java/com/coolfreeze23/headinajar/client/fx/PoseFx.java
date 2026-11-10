package com.coolfreeze23.headinajar.client.fx;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Whole-body poses laid over a creature's own renderer, through the pose stack only (no model is changed): a held
 * thing hangs and turns slowly on its tether; the body you leave powers down (sinks and leans a little); the body you
 * enter shakes itself awake. Under the reduced-motion setting these are scaled down with everything else.
 */
public final class PoseFx {
    private static final int POWER_DOWN = 1, WAKE = 2;

    private record Pose(int kind, long start) {
    }

    private static final Map<Integer, Pose> POSES = new HashMap<>();
    /**
     * One entry per Pre this listener saw (true where it pushed the pose stack), taken off by the matching Post, so
     * nested or repeated renders of one entity in a frame always balance.
     */
    private static final java.util.ArrayDeque<Boolean> PUSHED = new java.util.ArrayDeque<>();
    /** Smoothed lean of held things, by entity id. */
    private static final Map<Integer, Vec3> LEAN = new HashMap<>();

    public static void powerDown(int entityId) {
        if (Minecraft.getInstance().level != null) POSES.put(entityId, new Pose(POWER_DOWN, Minecraft.getInstance().level.getGameTime()));
    }

    public static void wake(int entityId) {
        if (Minecraft.getInstance().level != null) POSES.put(entityId, new Pose(WAKE, Minecraft.getInstance().level.getGameTime()));
    }

    public static void tick() {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            POSES.clear();
            LEAN.clear();
            return;
        }
        long now = level.getGameTime();
        POSES.values().removeIf(p -> now - p.start > 60);
        LEAN.keySet().removeIf(id -> level.getEntity(id) == null || !TetherFx.held(level.getEntity(id)));
        for (var e : level.entitiesForRendering()) {
            if (!TetherFx.held(e)) continue;
            // the lean chases the velocity: a held thing swings behind as it is carried and settles when it stops
            Vec3 v = e.getDeltaMovement().lengthSqr() > 0 ? new Vec3(e.getX() - e.xo, e.getY() - e.yo, e.getZ() - e.zo) : Vec3.ZERO;
            Vec3 lean = LEAN.getOrDefault(e.getId(), Vec3.ZERO);
            LEAN.put(e.getId(), lean.add(v.subtract(lean).scale(0.25)));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void pre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity e = event.getEntity();
        float pt = event.getPartialTick();
        Pose p = POSES.get(e.getId());
        boolean held = TetherFx.held(e);
        if (p == null && !held) {
            PUSHED.push(false);
            return;
        }
        float k = Math.max(0.35f, Comfort.motion());
        var pose = event.getPoseStack();
        pose.pushPose();
        PUSHED.push(true);
        float h = e.getBbHeight();
        if (held) {
            Entity holder = TetherFx.holderOf(e);
            float t = e.tickCount + pt;
            Vec3 lean = LEAN.getOrDefault(e.getId(), Vec3.ZERO);
            // hangs from the line: leans its top toward the head, swings behind with the lean, turns slowly
            Vec3 pull = holder == null ? Vec3.ZERO : holder.getEyePosition(pt).subtract(e.getPosition(pt).add(0, h / 2, 0));
            Vec3 tilt = new Vec3(pull.x, 0, pull.z).normalize().scale(8).add(new Vec3(lean.x, 0, lean.z).scale(-120));
            double deg = Math.min(18, tilt.length()) * k;
            pose.translate(0, h / 2, 0);
            if (deg > 0.1) {
                Vec3 axis = new Vec3(tilt.z, 0, -tilt.x).normalize();
                pose.mulPose(Axis.of(new org.joml.Vector3f((float) axis.x, 0, (float) axis.z)).rotationDegrees((float) deg));
            }
            pose.mulPose(Axis.YP.rotationDegrees(Mth.sin(t * Mth.TWO_PI / 90f) * 14 * k));
            pose.translate(0, -h / 2 + Mth.sin(t * Mth.TWO_PI / 50f) * 0.03f * k, 0);
        }
        if (p != null) {
            var level = Minecraft.getInstance().level;
            float t = level == null ? 99 : level.getGameTime() - p.start + pt;
            if (p.kind == POWER_DOWN) {
                // sinks 1.5 px and leans forward 5 degrees over 8 ticks, holds, and straightens by tick 45
                float in = Mth.clamp(t / 8f, 0, 1), out = Mth.clamp((t - 30) / 15f, 0, 1);
                float s = in * in * (1 - out * out * (3 - 2 * out));
                float yaw = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot);
                pose.translate(0, -0.094 * s, 0);
                // the top tips toward where the body faces: about up x forward
                float rad = yaw * Mth.DEG_TO_RAD;
                pose.mulPose(Axis.of(new org.joml.Vector3f(Mth.cos(rad), 0, Mth.sin(rad))).rotationDegrees(5 * s * k));
            } else if (p.kind == WAKE) {
                // shakes itself awake: a quick damped side-to-side shiver and a small hop
                float shiver = t < 20 ? Mth.sin(t * 1.7f) * 5 * (float) Math.exp(-t / 6) : 0;
                float hop = t < 6 ? Mth.sin(t / 6 * Mth.PI) * 0.06f : 0;
                pose.translate(0, hop * k + h / 2, 0);
                pose.mulPose(Axis.ZP.rotationDegrees(shiver * k));
                pose.translate(0, -h / 2, 0);
            }
        }
    }

    @SubscribeEvent
    public static void post(RenderLivingEvent.Post<?, ?> event) {
        if (!PUSHED.isEmpty() && PUSHED.pop()) event.getPoseStack().popPose();
    }

    private PoseFx() {
    }
}
