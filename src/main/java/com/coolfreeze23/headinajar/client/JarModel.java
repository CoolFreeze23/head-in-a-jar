package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The life-support jar in model space (y down, the ground at y = 24, 16 units to a block).
 * The jar texture is 64x64; the head layer uses the player's skin.
 */
public final class JarModel {
    public static final ModelLayerLocation JAR = new ModelLayerLocation(HeadInAJar.id("jar"), "main");
    public static final ModelLayerLocation HEAD = new ModelLayerLocation(HeadInAJar.id("jar"), "head");
    public static final ModelLayerLocation STUMP = new ModelLayerLocation(HeadInAJar.id("neck_stump"), "main");

    /** Neck pivot height of the head inside the jar, in model units. */
    public static final float NECK_Y = 22.0f;

    public static LayerDefinition jar() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("frame", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-5.5f, 23.0f, -5.5f, 11, 1, 11)        // base plate
                .texOffs(0, 12).addBox(-5.5f, 11.0f, -5.5f, 11, 1, 11)       // lid
                .texOffs(44, 0).addBox(-1.5f, 9.5f, -1.5f, 3, 2, 3)          // valve
                .texOffs(44, 5).addBox(5.0f, 15.0f, -0.5f, 1, 6, 1)          // side pipe, right
                .texOffs(52, 5).addBox(-6.0f, 15.0f, -0.5f, 1, 6, 1)         // side pipe, left
                .texOffs(44, 14).addBox(-1.0f, 22.0f, 5.0f, 2, 1, 1),        // status light
                PartPose.ZERO);
        root.addOrReplaceChild("fluid", CubeListBuilder.create()
                .texOffs(0, 46).addBox(-4.5f, 16.5f, -4.5f, 9, 6.5f, 9),
                PartPose.ZERO);
        root.addOrReplaceChild("glass", CubeListBuilder.create()
                .texOffs(0, 24).addBox(-5.0f, 12.0f, -5.0f, 10, 11, 10),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    public static LayerDefinition head() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8),
                PartPose.offset(0, NECK_Y, 0));
        root.addOrReplaceChild("hat", CubeListBuilder.create().texOffs(32, 0).addBox(-4, -8, -4, 8, 8, 8, new CubeDeformation(0.4f)),
                PartPose.offset(0, NECK_Y, 0));
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** The cut neck on top of a Headless Body, in the body's model space (the neck at y = 0). */
    public static LayerDefinition stump() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("stump", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-2.5f, -1.0f, -2.0f, 5, 1, 4)
                .texOffs(0, 5).addBox(-1.5f, -1.6f, -1.0f, 3, 1, 2), PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16);
    }

    /** Relay Goggles strapped round the head, pivoting with it (texture 64x32). */
    public static LayerDefinition goggles() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("goggles", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.3f, -5.4f, -4.3f, 8.6f, 2, 8.6f)
                .texOffs(0, 12).addBox(-3.6f, -5.9f, -5.3f, 3, 3, 1)
                .texOffs(8, 12).addBox(0.6f, -5.9f, -5.3f, 3, 3, 1)
                .texOffs(16, 12).addBox(2.5f, -10.5f, 0.5f, 1, 3, 1), PartPose.offset(0, NECK_Y, 0));
        return LayerDefinition.create(mesh, 64, 32);
    }

    /** The Collar Cam bolted on the neck of a body, in the body part's space (neck at y = 0, front is -Z; texture 32x32). */
    public static LayerDefinition collar() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("collar", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.0f, -1.6f, -2.5f, 6, 2, 5)
                .texOffs(0, 8).addBox(-1.0f, -2.8f, -3.4f, 2, 2, 1)
                .texOffs(8, 8).addBox(1.6f, -3.8f, 0.5f, 1, 2, 1), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    public static final ModelLayerLocation GOGGLES = new ModelLayerLocation(HeadInAJar.id("relay_goggles"), "main");
    public static final ModelLayerLocation COLLAR = new ModelLayerLocation(HeadInAJar.id("collar_cam"), "main");

    public record Parts(ModelPart frame, ModelPart fluid, ModelPart glass) {
        public static Parts of(ModelPart root) {
            return new Parts(root.getChild("frame"), root.getChild("fluid"), root.getChild("glass"));
        }
    }

    private JarModel() {
    }
}
