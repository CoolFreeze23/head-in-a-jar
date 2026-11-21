package com.coolfreeze23.headinajar.client.body;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * The bayonet collar between a host's neck and the jar on it: a brass ring with two lugs, just round the jar's base
 * plate. As the jar seats it twists a quarter turn into place, like a bottle cap. The jar and its seat are untouched;
 * the ring is drawn around them. Sized per jar (whole pixels, nothing scaled).
 */
public final class CollarRing {
    public static final ResourceLocation TEXTURE = HeadInAJar.id("textures/entity/host_collar.png");
    private static ModelPart wide, compact;

    /** A square ring of {@code outer} px with a 1 px wall and 2 px tall, round the base (jar model space, base underside at y 24). */
    private static ModelPart ring(int outer) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        float h = outer / 2f;
        CubeListBuilder b = CubeListBuilder.create()
                .texOffs(0, 0).addBox(-h, 22.5f, -h, outer, 2, 1)
                .texOffs(0, 4).addBox(-h, 22.5f, h - 1, outer, 2, 1)
                .texOffs(0, 8).addBox(-h, 22.5f, -h + 1, 1, 2, outer - 2)
                .texOffs(32, 8).addBox(h - 1, 22.5f, -h + 1, 1, 2, outer - 2)
                // the bayonet lugs, either side
                .texOffs(56, 0).addBox(-h - 1, 23, -1, 1, 1, 2)
                .texOffs(56, 4).addBox(h, 23, -1, 1, 1, 2);
        root.addOrReplaceChild("ring", b, PartPose.ZERO);
        // the sheet is 64 x 32 (the side walls' faces lie on rows 8-24)
        return LayerDefinition.create(mesh, 64, 32).bakeRoot().getChild("ring");
    }

    /**
     * Draws the ring under a hosted jar. The pose is the jar's (base at the origin, facing the host's way); {@code seatedAt}
     * is when this client saw it seat, or null.
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, int light, Entity host, boolean small, float pt) {
        if (wide == null) {
            wide = ring(16);
            compact = ring(13);
        }
        ModelPart ring = small ? compact : wide;
        ring.yRot = twist(host, pt);
        pose.pushPose();
        pose.scale(-1, -1, 1);
        pose.translate(0, -1.501f, 0);
        ring.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /**
     * The ring's turn now (radians about the jar's axis, in the ring's own flipped model space): a quarter turn back
     * when this client saw a jar seat on {@code host}, then home with a small overshoot (2 ticks still, 6 to turn); 0
     * otherwise. The collar's canister turns with it.
     */
    public static float twist(Entity host, float pt) {
        Long seated = HostFx.seatedAt(host.getId());
        if (seated == null || Minecraft.getInstance().level == null) return 0;
        float t = Minecraft.getInstance().level.getGameTime() - seated + pt;
        if (t < 2) return -90 * Mth.DEG_TO_RAD;
        float u = Mth.clamp((t - 2) / 6f, 0, 1);
        float back = 1 - u;
        float over = u < 1 ? Mth.sin(u * Mth.PI) * 0.08f : 0;
        return (-90 * back * back - 90 * over * 0.5f) * Mth.DEG_TO_RAD;
    }

    /** Clears the baked rings after a resource reload (they are rebuilt on the next frame). */
    public static void reset() {
        wide = compact = null;
        // the collar's canister, drawn with the ring on a secondee (0.7.1)
        com.coolfreeze23.headinajar.client.second.CollarCanister.reset();
    }

    private CollarRing() {
    }
}
