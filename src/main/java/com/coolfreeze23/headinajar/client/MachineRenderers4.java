package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.block.PowerBlockEntities;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.cache.object.GeoBone;

/** The fourth batch's renderers on the kit ({@link MachineRenderers.Base}). */
public final class MachineRenderers4 {
    private MachineRenderers4() {
    }

    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModRegistry.MAST_BE.get(), c -> new Station("relay_mast", 16, 33));
        event.registerBlockEntityRenderer(ModRegistry.MAST2_BE.get(), c -> new Station("relay_mast_mk2", 17, 33));
        event.registerBlockEntityRenderer(ModRegistry.JAMMER_BE.get(), c -> new Station("signal_jammer", 8, 21));
        event.registerBlockEntityRenderer(ModRegistry.TURRET_BE.get(), c -> new Turret());
        event.registerBlockEntityRenderer(ModRegistry.LOCKER_BE.get(), c -> new Locker());
    }

    static float turn(net.minecraft.world.level.block.entity.BlockEntity be) {
        return switch (MachineRenderers.Base.facing(be)) {
            case SOUTH -> 180;
            case WEST -> 90;
            case EAST -> 270;
            default -> 0;
        };
    }

    // --- Relay Masts and the Signal Jammer ----------------------------------------------------------------------

    static class Station extends MachineRenderers.Base<PowerBlockEntities.Station> {
        Station(String name, double reach, double top) {
            super(name, -reach, 0, -reach, reach, top, reach);
            if (name.equals("relay_mast_mk2")) {
                // the Mk II's aviation lamp rides its stage: it glows (its glowmask) while the mast has power
                addRenderLayer(new software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer<>(this) {
                    @Override
                    public void render(com.mojang.blaze3d.vertex.PoseStack pose, PowerBlockEntities.Station s, software.bernie.geckolib.cache.object.BakedGeoModel model,
                                       net.minecraft.client.renderer.RenderType type, net.minecraft.client.renderer.MultiBufferSource buffers,
                                       com.mojang.blaze3d.vertex.VertexConsumer buffer, float partial, int light, int overlay) {
                        if (s.powered()) super.render(pose, s, model, type, buffers, buffer, partial, light, overlay);
                    }
                });
            }
        }

        @Override
        protected boolean awake(PowerBlockEntities.Station s, double now) {
            return MachineAnimations4.awake(s, now);
        }

        /** A mast's dish is seen from far off: drawn within 96 blocks. */
        @Override
        public int getViewDistance() {
            return 96;
        }

        /** The dish turned to the next hop and drooped when unpowered; the Jammer's turbine spun. */
        @Override
        protected void pose(PowerBlockEntities.Station s, MachineRenderers.Model<PowerBlockEntities.Station> model, float partial) {
            double now = MachineAnimations.now(s);
            GeoBone dish = model.bone("dish");
            if (dish != null) {
                float yaw = MachineAnimations4.dishYaw(s, now) - turn(s);
                dish.setRotY(dish.getInitialSnapshot().getRotY() + (float) Math.toRadians(yaw));
                dish.setRotX(dish.getInitialSnapshot().getRotX() - (float) Math.toRadians(MachineAnimations4.dishDroop(s, now)));
            }
            GeoBone rotor = model.bone("rotor");
            if (rotor != null) rotor.setRotY(rotor.getInitialSnapshot().getRotY() + (float) Math.toRadians(Mth.lerp(partial, s.anglePrev, s.angle)));
        }
    }

    // --- Turret Pedestal ------------------------------------------------------------------------------------------

    static class Turret extends MachineRenderers.Base<com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity> {
        Turret() {
            super("turret_pedestal", -8, 0, -14, 8, 33, 8);
        }

        @Override
        protected boolean awake(com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity t, double now) {
            return MachineAnimations4.awake(t, now);
        }

        /** The mount turned and tilted by code; the cradle's and the barrel's frames kept for the jar and the muzzle. */
        @Override
        protected void pose(com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity t, MachineRenderers.Model<com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity> model, float partial) {
            double now = MachineAnimations.now(t);
            GeoBone yaw = model.bone("yaw"), pitch = model.bone("pitch"), recoil = model.bone("recoil");
            if (yaw == null || pitch == null) return;
            float y = Mth.rotLerp(partial, t.yawO, t.yaw) - turn(t) + MachineAnimations4.turretYawExtra(t, now);
            float p = Mth.lerp(partial, t.pitchO, t.pitch) + MachineAnimations4.turretPitchExtra(t, now);
            yaw.setRotY(yaw.getInitialSnapshot().getRotY() + (float) Math.toRadians(y));
            pitch.setRotX(pitch.getInitialSnapshot().getRotX() - (float) Math.toRadians(p));
            pitch.setTrackingMatrices(true);
            if (recoil != null) recoil.setTrackingMatrices(true);
        }

        /** After the bones are drawn: this turret's bore mouth, kept on it for the lock beam (the model's bones are shared by every turret). */
        @Override
        public void postRender(com.mojang.blaze3d.vertex.PoseStack pose, com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity t, software.bernie.geckolib.cache.object.BakedGeoModel model,
                               net.minecraft.client.renderer.MultiBufferSource buffers, @org.jetbrains.annotations.Nullable com.mojang.blaze3d.vertex.VertexConsumer buffer,
                               boolean isReRender, float partialTick, int light, int overlay, int colour) {
            super.postRender(pose, t, model, buffers, buffer, isReRender, partialTick, light, overlay, colour);
            if (isReRender || model == null) return;
            GeoBone recoil = model.getBone("recoil").orElse(null);
            if (recoil == null) return;
            org.joml.Vector4f at = recoil.getWorldSpaceMatrix().transform(new org.joml.Vector4f(0, 0, -6 / 16f, 1));
            t.muzzleSeen = new net.minecraft.world.phys.Vec3(at.x(), at.y(), at.z());
        }

        /** The real jar at full size, standing on the cradle's tray (its anchor 2 px above the pin line), turning and tilting with it. */
        @Override
        protected void contents(com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity t, com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light, float partial) {
            if (t.head().isEmpty() || t.getLevel() == null) return;
            var info = t.headInfo();
            GeoBone pitch = getGeoModel().getBakedModel(getGeoModel().getModelResource(t)).getBone("pitch").orElse(null);
            pose.pushPose();
            if (pitch != null) pose.mulPose(pitch.getModelSpaceMatrix());
            pose.translate(0, 2 / 16.0, 0);
            if (info.type() != null) {
                JarredHeadRenderer.drawJar(info.type(), info.villagerData(), pose, buffers, light, t.getLevel().getGameTime() + partial);
            } else {
                pose.translate(0, 0.25, 0);
                net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(t.head(), net.minecraft.world.item.ItemDisplayContext.FIXED, light,
                        net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, pose, buffers, null, 0);
            }
            pose.popPose();
        }
    }

    /**
     * Where the lock beam starts and every shot leaves: the bore's mouth, 6 px in front of the barrel's pivot, in the world,
     * as this turret was last drawn (so it follows the cradle as it slews); the top of the mount until it has been drawn.
     */
    public static net.minecraft.world.phys.Vec3 muzzle(com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity t) {
        return t.muzzleSeen != null ? t.muzzleSeen : t.getBlockPos().getCenter().add(0, 0.9, 0);
    }

    // --- Body Locker (the Cloakroom) -------------------------------------------------------------------------------

    static class Locker extends MachineRenderers.Base<com.coolfreeze23.headinajar.block.BodyLockerBlockEntity> {
        Locker() {
            super("body_locker", -8, 0, -8, 8, 32, 8);
        }

        @Override
        protected boolean awake(com.coolfreeze23.headinajar.block.BodyLockerBlockEntity l, double now) {
            return MachineAnimations4.awake(l, now);
        }

        /** The portrait in the header's brass frame: the stored body's face at three texels, while the clip shows it. */
        @Override
        protected void contents(com.coolfreeze23.headinajar.block.BodyLockerBlockEntity l, com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light, float partial) {
            if (l.look().isEmpty() || !MachineAnimations4.portrait(l, MachineAnimations.now(l))) return;
            var type = net.minecraft.world.entity.EntityType.byString(l.look()).orElse(null);
            if (type == null) return;
            var head = JarredHeadRenderer.head(type, null);
            if (head == null) return;
            var texture = HeadFaces.face(head.part(), head.texture());
            if (texture == null) return;
            var v = buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(texture));
            pose.pushPose();
            pose.translate(0, (26 - 1.5) / 16.0, -3.15 / 16.0);
            var m = pose.last();
            float h = HeadFaces.SIZE / 32f;
            portraitVertex(v, m, h, 2 * h, 0, 0, light);
            portraitVertex(v, m, h, 0, 0, 1, light);
            portraitVertex(v, m, -h, 0, 1, 1, light);
            portraitVertex(v, m, -h, 2 * h, 1, 0, light);
            pose.popPose();
        }

        private static void portraitVertex(com.mojang.blaze3d.vertex.VertexConsumer v, com.mojang.blaze3d.vertex.PoseStack.Pose m, float x, float y, float u, float vv, int light) {
            v.addVertex(m, x, y, 0).setColor(255, 255, 255, 255).setUv(u, vv).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                    .setLight(light).setNormal(m, 0, 0, -1);
        }
    }
}
