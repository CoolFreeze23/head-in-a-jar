package com.coolfreeze23.headinajar.client.body;

import com.coolfreeze23.headinajar.entity.HeadlessBody;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;

/** The vanilla player model, posed by {@link BodyPoses} after its own pose is set (sleeves and trousers follow). */
public class BodyModel extends PlayerModel<HeadlessBody> {
    public BodyModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    @Override
    public void setupAnim(HeadlessBody body, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
        super.setupAnim(body, limbSwing, limbSwingAmount, age, headYaw, headPitch);
        BodyPoses.pose(this, body, age);
        leftSleeve.copyFrom(leftArm);
        rightSleeve.copyFrom(rightArm);
        leftPants.copyFrom(leftLeg);
        rightPants.copyFrom(rightLeg);
        jacket.copyFrom(this.body);
    }
}
