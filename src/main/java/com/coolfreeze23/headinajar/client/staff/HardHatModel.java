package com.coolfreeze23.headinajar.client.staff;

import com.coolfreeze23.headinajar.HeadInAJar;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Hard Hat as the armour layer draws it on players and every humanoid: the art's size-l hat (a stepped dome over a
 * brim with a peak, a crown and a ridge) on the {@code head} part, its brim's underside two rows above the eyes and the
 * dome clear of the head by 1 px; every other part empty. Texture 64 x 64 ({@code textures/models/armor/hard_hat_layer_1.png},
 * the armour material's), box UV. The boxes are the art's {@code hard_hat_geo.armor_parts()}.
 */
public final class HardHatModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(HeadInAJar.id("hard_hat"), "main");
    /** The same hat for a zombie villager's head, 2 px taller than a humanoid's (as vanilla's armour for it is raised). */
    public static final ModelLayerLocation LAYER_TALL = new ModelLayerLocation(HeadInAJar.id("hard_hat"), "tall");
    static final float TALL_HEAD = 2;
    private static HumanoidModel<LivingEntity> baked, bakedTall;

    private HardHatModel() {
    }

    public static LayerDefinition create() {
        return create(0, false);
    }

    /**
     * The zombie villager's: 2 px up, and the dome's stepped corners filled (its head's outer layer, where its headbands
     * and caps are painted, stands half a pixel out and would show through the notches).
     */
    public static LayerDefinition createTall() {
        return create(-TALL_HEAD, true);
    }

    /** The boxes moved {@code y} px (the head part's own pose is the wearer's, copied over it every frame). */
    private static LayerDefinition create(float y, boolean squareCorners) {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0);
        PartDefinition root = mesh.getRoot();
        CubeListBuilder hat = CubeListBuilder.create();
        if (squareCorners) {
            // 1 x 3 x 1 posts in the dome's corner notches, painted from the plain dome front
            for (float x : new float[]{-5F, 4F}) {
                for (float z : new float[]{-5F, 4F}) hat.texOffs(10, 19).addBox(x, -9F + y, z, 1F, 3F, 1F);
            }
        }
        root.addOrReplaceChild("head", hat
                .texOffs(0, 0).addBox(-6F, -6F + y, -6F, 12F, 1F, 11F)     // brim
                .texOffs(0, 36).addBox(-5F, -6F + y, -7F, 10F, 1F, 1F)     // brim_front (the peak)
                .texOffs(36, 32).addBox(-5F, -6F + y, 5F, 10F, 1F, 1F)     // brim_back
                .texOffs(0, 12).addBox(-5F, -9F + y, -4F, 10F, 3F, 8F)     // dome
                .texOffs(18, 32).addBox(-4F, -9F + y, -5F, 8F, 3F, 1F)     // dome_front
                .texOffs(0, 32).addBox(-4F, -9F + y, 4F, 8F, 3F, 1F)       // dome_back
                .texOffs(0, 23).addBox(-4F, -10F + y, -4F, 8F, 1F, 8F)     // crown
                .texOffs(32, 23).addBox(-1F, -11F + y, -4F, 2F, 1F, 8F),   // ridge
                PartPose.ZERO);
        for (String part : new String[]{"hat", "body", "right_arm", "left_arm", "right_leg", "left_leg"}) {
            root.addOrReplaceChild(part, CubeListBuilder.create(), PartPose.ZERO);
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** The model baked for this resource load (null before the first). */
    public static HumanoidModel<LivingEntity> model() {
        return baked;
    }

    /** The hat a wearer's head takes: a zombie villager's is 2 px taller, so its hat sits 2 px higher. */
    public static HumanoidModel<LivingEntity> model(LivingEntity wearer) {
        return wearer instanceof net.minecraft.world.entity.monster.ZombieVillager ? bakedTall : baked;
    }

    static void bake(net.minecraft.client.model.geom.EntityModelSet models) {
        baked = new HumanoidModel<>(models.bakeLayer(LAYER));
        bakedTall = new HumanoidModel<>(models.bakeLayer(LAYER_TALL));
    }
}
