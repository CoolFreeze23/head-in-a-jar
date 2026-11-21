package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.HeadInfo;
import com.coolfreeze23.headinajar.entity.RedundancyPackageEntity;
import com.coolfreeze23.headinajar.registry.SecondRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * The Redundancy Package as a client sees it: the seconded head in its own jar, looking down at where it is going, a
 * parcel tied to the lid and its small gold canopy over it ({@link Parachute#PACKAGE}, or {@link Parachute#PACKAGE_COMPACT}
 * over a Compact Jar, whose lid is lower). The canopy's state rides the package's own synced data; once it is down,
 * only the folded canopy is drawn (the head is on the ground by then, placed or left as an item).
 */
public class RedundancyPackageRenderer extends EntityRenderer<RedundancyPackageEntity> {
    /** How far the head looks down on the way down (degrees). */
    static final float LOOK_DOWN = 18;

    public RedundancyPackageRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.3f;
    }

    @Override
    public void render(RedundancyPackageEntity pkg, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pkg.toldState = Parachute.follow(pkg, pkg.toldState, pkg.state(), pkg.firedAt(), pkg.rate());
        HeadInfo info = HeadInfo.of(pkg.shownHead());
        if (!pkg.down()) {
            if (info.type() != null) {
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partialTick, pkg.yRotO, pkg.getYRot())));
                float age = pkg.tickCount + partialTick;
                // a little sway inside the glass as it drifts
                float yaw = Mth.sin(age * 0.11f) * 12;
                JarredHeadRenderer.drawJar(info.type(), info.villagerData(), info.size(), info.jar(), pose, buffers, light, age, yaw, LOOK_DOWN, 1);
                pose.popPose();
            }
        }
        Parachute.render(pkg, info.compact() ? Parachute.PACKAGE_COMPACT : Parachute.PACKAGE, partialTick, pose, buffers, light);
        super.render(pkg, entityYaw, partialTick, pose, buffers, light);
    }

    @Override
    public boolean shouldRender(RedundancyPackageEntity pkg, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        // the canopy reaches well above the jar: cull by both
        return frustum.isVisible(pkg.getBoundingBox().expandTowards(0, 2, 0).inflate(1)) || super.shouldRender(pkg, frustum, x, y, z);
    }

    @Override
    public ResourceLocation getTextureLocation(RedundancyPackageEntity pkg) {
        return JarRenderer.TEXTURE;
    }

    @EventBusSubscriber(modid = HeadInAJar.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(SecondRegistry.REDUNDANCY_PACKAGE.get(), RedundancyPackageRenderer::new);
        }

        private Registration() {
        }
    }
}
