package com.coolfreeze23.headinajar.client.fx;

import com.coolfreeze23.headinajar.block.PowerBlockEntities;
import com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity;
import com.coolfreeze23.headinajar.fx.FxSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Beams that belong to blocks, drawn from state the block's update tag already carries: the arc between linked Tesla
 * Nodes (with a bead running from sender to receiver while power flows) and a guardian turret's lock beam. They are
 * drawn in the kit's own pass with their own culling box, so a beam is never cut because one block is off screen or
 * hidden by a culling mod; the turret's render box covers its beam as well.
 */
public final class BlockBeams {
    private static final Set<BlockEntity> LOADED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final ResourceLocation GUARDIAN_BEAM = ResourceLocation.withDefaultNamespace("textures/entity/guardian_beam.png");

    /** Called by the block entities on the client when they load and unload. */
    public static void loaded(BlockEntity be) {
        LOADED.add(be);
    }

    public static void unloaded(BlockEntity be) {
        LOADED.remove(be);
    }

    static Vec3 teslaTip(net.minecraft.core.BlockPos pos) {
        return Vec3.atCenterOf(pos).add(0, 0.42, 0);
    }

    public static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.level == null) {
            LOADED.clear();
            return;
        }
        long now = mc.level.getGameTime();
        for (BlockEntity be : LOADED.toArray(new BlockEntity[0])) {
            if (be.isRemoved() || be.getLevel() != mc.level) {
                LOADED.remove(be);
                continue;
            }
            if (be instanceof PowerBlockEntities.TeslaNode node && node.target() != null && node.linkedOnClient()) {
                Vec3 a = teslaTip(node.getBlockPos()), b = teslaTip(node.target());
                Vec3 mid = a.add(b).scale(0.5);
                SoundLoops.want(node, FxSounds.TESLA_ARC.get(), SoundSource.BLOCKS, mid, node.flowing() ? 1f : 0.35f, node.flowing() ? 1f : 0.85f);
                if (node.flowing() && now % 8 == (node.getBlockPos().asLong() & 7)) {
                    for (Vec3 tip : new Vec3[]{a, b}) {
                        FxParticle s = FxParticle.make(FxParticle.Look.SPARK, tip, new Vec3((Math.random() - 0.5) * 0.15, 0.12, (Math.random() - 0.5) * 0.15));
                        if (s != null) s.color(FxColors.POWER).size(0.05f).gravity(0.5f).life(6).add();
                    }
                }
            }
        }
    }

    public static void render(Beams.Frame f) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        for (BlockEntity be : LOADED) {
            if (be.isRemoved()) continue;
            if (be instanceof PowerBlockEntities.TeslaNode node && node.target() != null && node.linkedOnClient()) {
                tesla(f, node, now);
            } else if (be instanceof TurretPedestalBlockEntity turret && turret.lockId() >= 0) {
                Entity target = mc.level.getEntity(turret.lockId());
                if (target != null) lock(f, turret, target, now);
            }
        }
    }

    private static void tesla(Beams.Frame f, PowerBlockEntities.TeslaNode node, long now) {
        Vec3 a = teslaTip(node.getBlockPos()), b = teslaTip(node.target());
        if (!f.visible(a, b)) return;
        boolean flowing = node.flowing();
        // the shape crackles, its brightness stays steady: idle re-rolls every 10 ticks, transferring every 2
        int every = Comfort.rerollTicks(flowing ? 2 : 10);
        long seed = node.getBlockPos().asLong() * 31 + now / every;
        int n = Mth.clamp((int) (a.distanceTo(b) * 2), 6, 16);
        Vec3[] pts = Beams.arcPath(a, b, n, seed, flowing ? 0.28f : 0.18f, null);
        Beams.arc(f, pts, FxColors.POWER, FxColors.POWER_PALE, flowing ? 0.07f : 0.035f, flowing ? 0.95f : 0.45f);
        if (flowing) {
            // a bright bead runs from the sender to the receiver every 10 ticks: which way the power goes
            float t = ((now % 10) + f.partialTick()) / 10f;
            Beams.bead(f, Beams.along(pts, t), FxColors.POWER_PALE, 0.14f, 1);
        }
    }

    private static void lock(Beams.Frame f, TurretPedestalBlockEntity turret, Entity target, long now) {
        Vec3 from = Vec3.atCenterOf(turret.getBlockPos()).add(0, 0.45, 0);
        Vec3 to = target.getPosition(f.partialTick()).add(0, target.getBbHeight() * 0.6, 0);
        if (!f.visible(from, to)) return;
        float progress = Mth.clamp((now - turret.lockStart() + f.partialTick()) / 40f, 0, 1);
        // the vanilla guardian's beam, cold to warm as it charges, so every player reads "about to hurt"
        float p2 = progress * progress;
        int r = 64 + (int) (p2 * 191), g = 32 + (int) (p2 * 191), b = 128 - (int) (p2 * 64);
        Vec3[] pts = {from, from.lerp(to, 0.5), to};
        float scroll = (now + f.partialTick()) * 0.05f * (1 + progress);
        guardianStrip(f, pts, (r << 16) | (g << 8) | b, 0.2f, scroll);
        // and a faint glowing core so it reads at night
        Beams.ribbon(f, pts, new Beams.Ribbon(FxColors.SCAN, (r << 16) | (g << 8) | b, 0.05f, 0.05f, 0.35f + 0.3f * progress, scroll), null);
        // brackets closing on the target over the lock: amber, then steady red for the last 10 ticks (never blinking)
        if (now % 4 == 0) {
            FxParticle ring = FxParticle.make(FxParticle.Look.RING, to, Vec3.ZERO);
            if (ring != null) ring.color(progress > 0.75f ? FxColors.WARN : FxColors.POWER).size(Math.max(0.3f, target.getBbWidth() * (1.3f - progress * 0.6f)))
                    .frame(3).life(4).steady().alpha(0.8f).add();
        }
    }

    private static void guardianStrip(Beams.Frame f, Vec3[] pts, int rgb, float width, float scroll) {
        var vc = f.buffers().getBuffer(RenderType.entityCutoutNoCull(GUARDIAN_BEAM));
        var mat = f.pose().last().pose();
        var pose = f.pose().last();
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
        float along = 0;
        for (int i = 0; i + 1 < pts.length; i++) {
            Vec3 p0 = pts[i].subtract(f.camera()), p1 = pts[i + 1].subtract(f.camera());
            double seg = p0.distanceTo(p1);
            double dist = p0.add(p1).scale(0.5).length();
            float w = Math.max(width, (float) dist * Beams.MIN_WIDTH_PER_BLOCK * 2) / 2;
            Vec3 s = p1.subtract(p0).cross(p0.add(p1).scale(0.5));
            if (s.lengthSqr() < 1e-9) continue;
            s = s.normalize().scale(w);
            float v0 = along * 0.5f - scroll, v1 = (float) (along + seg) * 0.5f - scroll;
            along += (float) seg;
            put(vc, mat, pose, p0.subtract(s), 0, v0, r, g, b);
            put(vc, mat, pose, p0.add(s), 0.5f, v0, r, g, b);
            put(vc, mat, pose, p1.add(s), 0.5f, v1, r, g, b);
            put(vc, mat, pose, p1.subtract(s), 0, v1, r, g, b);
        }
    }

    private static void put(com.mojang.blaze3d.vertex.VertexConsumer vc, org.joml.Matrix4f mat, com.mojang.blaze3d.vertex.PoseStack.Pose pose,
                            Vec3 p, float u, float v, float r, float g, float b) {
        vc.addVertex(mat, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, 1).setUv(u, v)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0, 1, 0);
    }

    private BlockBeams() {
    }
}
