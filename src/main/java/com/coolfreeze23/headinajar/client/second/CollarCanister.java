package com.coolfreeze23.headinajar.client.second;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * The Secondment Collar's broth canister: a 2 x 3 x 2 px cube standing on the back wall of the collar ring, centred,
 * half over the wall's outside, with its sight glass facing back (away from the jar) showing the broth in four steps
 * (full, two thirds, one third, empty). Drawn in the ring's own model space and turned with the ring as a jar seats.
 * <p>
 * The art's sheet {@link #SHEET} (16 x 16) holds the four steps as vanilla box UV blocks of 8 x 5 at (0, 0), (8, 0),
 * (0, 5) and (8, 5); the sight glass is each box's whole +z face (u 6-8, v 2-5), the ring's back. Should the sheet be
 * missing from the resources (a pack that drops it), a plain copper stand-in is drawn (chosen once each resource load).
 */
public final class CollarCanister {
    public static final ResourceLocation SHEET = HeadInAJar.id("textures/entity/secondment_canister.png");
    private static final ResourceLocation STAND_IN = ResourceLocation.withDefaultNamespace("textures/block/copper_block.png");
    /** Each step's corner on the sheet: full, two thirds, one third, empty. */
    private static final int[][] STEPS = {{0, 0}, {8, 0}, {0, 5}, {8, 5}};
    /** Baked per ring (wide, compact) and step; and whether the art's sheet is there. */
    private static ModelPart[][] parts;
    private static boolean artSheet;

    private CollarCanister() {
    }

    /** The canister behind a ring of {@code outer} px (16 wide, 13 compact), at one step's texels. */
    private static ModelPart part(int outer, int u, int v) {
        MeshDefinition mesh = new MeshDefinition();
        float h = outer / 2f;
        // ring model space (px, y down, the jar's base underside at 24): the ring stands 22.5-24.5, its back wall h-1..h.
        // The canister stands on that wall, half over its outside, glass facing out: behind the ring it would sit inside
        // the back of every long body (a cow's, a horse's), its glass facing into it
        mesh.getRoot().addOrReplaceChild("canister", CubeListBuilder.create().texOffs(u, v).addBox(-1, 19.5f, h - 1, 2, 3, 2), PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16).bakeRoot().getChild("canister");
    }

    private static void bake() {
        artSheet = Minecraft.getInstance().getResourceManager().getResource(SHEET).isPresent();
        parts = new ModelPart[2][4];
        for (int s = 0; s < 4; s++) {
            int u = artSheet ? STEPS[s][0] : 0, v = artSheet ? STEPS[s][1] : 0;
            parts[0][s] = part(16, u, v);
            parts[1][s] = part(13, u, v);
        }
    }

    /**
     * Draws the canister for a ring round a jar whose base is at the pose's origin, facing -Z (the pose a hosted jar is
     * drawn in). {@code step}: 0 full to 3 empty; {@code twist}: the ring's turn ({@code CollarRing.twist}).
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, int light, boolean compact, int step, float twist) {
        if (parts == null) bake();
        ModelPart part = parts[compact ? 1 : 0][Math.max(0, Math.min(3, step))];
        part.yRot = twist;
        pose.pushPose();
        pose.scale(-1, -1, 1);
        pose.translate(0, -1.501f, 0);
        part.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(artSheet ? SHEET : STAND_IN)), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /** Whether the art's sheet is drawn (false: the stand-in). For the harness. */
    public static boolean artSheet() {
        if (parts == null) bake();
        return artSheet;
    }

    /** Forgets the baked parts after a resource reload (called with the ring's). */
    public static void reset() {
        parts = null;
    }
}
