package com.coolfreeze23.headinajar.client.staff;

import com.coolfreeze23.headinajar.staff.work.WorkerNet;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.EndermanModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;

/**
 * Workers' poses, set after the model's own animation (every model class, so a zombie's arms are its own first):
 * bipeds (and a pillager, whose arms hang free) hold a carried crate in both hands, bring both hands to the chest to unpin the badge on Let go, and hold a
 * Job Card up to the face to read it; every other shape dips its head to a card (animals sniff it twice).
 */
public final class StaffPoses {
    /** An enderman carrying holds its arms as vanilla's EndermanModel does for a carried block. */
    private static final float ENDERMAN_CARRY_X = -0.5f, ENDERMAN_CARRY_Z = 0.05f;

    private StaffPoses() {
    }

    /** From {@code LivingRendererPoseMixin}, right after {@code setupAnim}. */
    public static void after(EntityModel<?> model, LivingEntity entity, float pt) {
        // vanilla never poses a villager's or an illager's folded arms: put them back at rest before anything is added
        // to them (a chant's lift added frame on frame to the one model every villager shares turned them right over)
        restFoldedArms(model);
        WorkerLines.Look look = WorkerLines.look(entity);
        if (look == null) return;
        // 0.7.2 strikes: a held picket sign's arm pose and the chant's lifts
        com.coolfreeze23.headinajar.client.StrikeClient.pose(model, entity, look, pt);
        float sinceRead = WorkerLines.since(look.readAt, pt);
        if (model instanceof HumanoidModel<?> h) {
            humanoid(h, entity, look, pt, sinceRead);
            return;
        }
        if (model instanceof IllagerModel<?> illager) illager(illager, entity, look, pt);
        if (sinceRead >= LanyardMotion.READ_OUT) return;
        ModelPart head = StaffLayer.head(model, StaffFit.of(entity.getType()));
        if (head == null) return;
        head.xRot += entity instanceof Animal ? LanyardMotion.sniff(sinceRead) : LanyardMotion.SNIFF_DIP * LanyardMotion.readWeight(sinceRead);
    }

    /** Carrying: how far the arms are forward, chased in and out (1 - e^(-12 t), t in seconds) so picking up and setting down never snap. */
    private static float carry(LivingEntity entity, WorkerLines.Look look, float pt) {
        float now = entity.tickCount + pt;
        float dt = Float.isNaN(look.carryAt) ? 1 : Mth.clamp(now - look.carryAt, 0, 5);
        look.carryAt = now;
        float target = look.has(WorkerNet.CARRYING) ? 1 : 0;
        look.carry += (target - look.carry) * (1 - (float) Math.exp(-dt / 20f * LanyardMotion.CARRY_EASE_K));
        return look.carry;
    }

    /**
     * An illager whose arms hang free (a pillager never crosses them) holds its crate in both hands as a biped does; one
     * with crossed arms rests the crate on them, and one that carries nothing (evoker, illusioner) is left alone.
     */
    /** A villager's (witch's, trader's) or an illager's folded arms back at their part's rest turn. */
    private static void restFoldedArms(EntityModel<?> model) {
        ModelPart arms = model instanceof net.minecraft.client.model.VillagerModel<?> v ? v.root().getChild("arms")
                : model instanceof IllagerModel<?> i ? i.root().getChild("arms") : null;
        if (arms != null) arms.xRot = arms.getInitialPose().xRot;
    }

    private static void illager(IllagerModel<?> model, LivingEntity entity, WorkerLines.Look look, float pt) {
        StaffFit.Fit fit = StaffFit.of(entity.getType());
        if (fit == null || fit.crate() == null || fit.crate().frame() == null) return;
        ModelPart right = model.root().getChild("right_arm"), left = model.root().getChild("left_arm");
        if (!right.visible) return;
        float w = carry(entity, look, pt);
        if (w > 0.001f) {
            arm(right, LanyardMotion.CARRY_X, 0, -LanyardMotion.CARRY_Z, w);
            arm(left, LanyardMotion.CARRY_X, 0, LanyardMotion.CARRY_Z, w);
        }
    }

    private static void humanoid(HumanoidModel<?> h, LivingEntity entity, WorkerLines.Look look, float pt, float sinceRead) {
        float carry = carry(entity, look, pt);
        if (carry > 0.001f) {
            if (h instanceof EndermanModel<?>) {
                // vanilla's carried-block arms, so the barrel (or the crate in its place) sits in its hands
                arm(h.rightArm, ENDERMAN_CARRY_X, 0, ENDERMAN_CARRY_Z, carry);
                arm(h.leftArm, ENDERMAN_CARRY_X, 0, -ENDERMAN_CARRY_Z, carry);
            } else {
                arm(h.rightArm, LanyardMotion.CARRY_X, 0, -LanyardMotion.CARRY_Z, carry);
                arm(h.leftArm, LanyardMotion.CARRY_X, 0, LanyardMotion.CARRY_Z, carry);
            }
        }
        // Let go: both hands to the chest to unpin the badge
        if (look.has(WorkerNet.NOTICE)) {
            float w = LanyardMotion.unpinWeight(WorkerLines.since(look.poppedAt, pt));
            if (w > 0) {
                arm(h.rightArm, LanyardMotion.UNPIN_X, -LanyardMotion.UNPIN_Y, 0, w);
                arm(h.leftArm, LanyardMotion.UNPIN_X, LanyardMotion.UNPIN_Y, 0, w);
            }
        }
        // reading a card: the card hand up to the face, the head looking down at it
        float w = LanyardMotion.readWeight(sinceRead);
        if (w > 0) {
            boolean right = entity.getMainArm() == HumanoidArm.RIGHT;
            ModelPart hand = right ? h.rightArm : h.leftArm;
            arm(hand, LanyardMotion.READ_ARM_X, right ? -LanyardMotion.READ_ARM_Y : LanyardMotion.READ_ARM_Y, 0, w);
            h.head.xRot += LanyardMotion.READ_HEAD_X * w;
            h.hat.copyFrom(h.head);
        }
    }

    private static void arm(ModelPart arm, float x, float y, float z, float w) {
        arm.xRot = Mth.lerp(w, arm.xRot, x);
        arm.yRot = Mth.lerp(w, arm.yRot, y);
        arm.zRot = Mth.lerp(w, arm.zRot, z);
    }
}
