package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.mecha.Legs;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.function.Function;

/**
 * What code does to a multi-leg skeleton's bones every frame, on top of the clips (the Skitter's and the Spider
 * Walker's renderers both call it). The clips are solved for flat ground; on uneven ground the body tilts by the slope
 * under the pads (to the rig's limit) and every leg is solved again, knee up, so each pad stands on the block under it,
 * swinging pads riding the same ground. A climbing frame's whole model is turned onto its wall about the near foot
 * line, and a hanging one along its line. Everything is in the model's own (Blockbench) frame: x right +, z front -;
 * GeckoLib takes code-set rotations in that sign and positions with x negated.
 */
public final class LegBones {
    private LegBones() {
    }

    /**
     * The legs on the ground under them. {@code grounded}: standing (on a wall, in the air or hanging the pads follow the
     * clip alone). {@code dt}: the ticks this frame covers.
     */
    public static void ground(Legs.Spec spec, LivingEntity e, Legs.Pose pose, Function<String, GeoBone> bones, boolean grounded, float partial, double dt) {
        GeoBone body = bones.apply("body");
        if (body == null) return;
        int n = spec.count();
        double[][] feet = new double[n][];
        Matrix3f rb = new Matrix3f().rotationZYX(body.getRotZ(), body.getRotY(), body.getRotX());
        Vector3f bp = new Vector3f(-body.getPosX(), body.getPosY(), body.getPosZ());
        for (int i = 0; i < n; i++) feet[i] = foot(spec, i, bones, rb, bp);
        float k = (float) (1 - Math.exp(-dt * 20 / 20.0));
        double x = Mth.lerp(partial, e.xo, e.getX()), y = Mth.lerp(partial, e.yo, e.getY()), z = Mth.lerp(partial, e.zo, e.getZ());
        float yaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
        boolean any = false;
        for (int i = 0; i < n; i++) {
            float target = 0;
            if (grounded && feet[i] != null) {
                double[] w = Legs.world(x, z, yaw, feet[i][0], feet[i][2]);
                target = (float) Legs.ground(e.level(), w[0], y, w[1]);
            }
            pose.ground[i] += (target - pose.ground[i]) * k;
            if (Math.abs(pose.ground[i]) < 0.02f) pose.ground[i] = 0;
            if (pose.ground[i] != 0) any = true;
        }
        // the plane through the pads: h = a + b x + c z (least squares); the body leans with it
        float pitchTarget = 0, rollTarget = 0;
        if (any) {
            double sx = 0, sz = 0, sh = 0, sxx = 0, szz = 0, sxz = 0, sxh = 0, szh = 0;
            int m = 0;
            for (int i = 0; i < n; i++) {
                if (feet[i] == null) continue;
                double fx = feet[i][0], fz = feet[i][2], h = pose.ground[i];
                sx += fx;
                sz += fz;
                sh += h;
                sxx += fx * fx;
                szz += fz * fz;
                sxz += fx * fz;
                sxh += fx * h;
                szh += fz * h;
                m++;
            }
            if (m >= 3) {
                double mx = sx / m, mz = sz / m, mh = sh / m;
                double cxx = sxx / m - mx * mx, czz = szz / m - mz * mz, cxz = sxz / m - mx * mz, cxh = sxh / m - mx * mh, czh = szh / m - mz * mh;
                double det = cxx * czz - cxz * cxz;
                if (Math.abs(det) > 1e-6) {
                    double b = (cxh * czz - czh * cxz) / det, c = (czh * cxx - cxh * cxz) / det;
                    // nose up when the ground rises ahead (z front -), left side down when it rises to the right
                    pitchTarget = (float) Mth.clamp(Math.toDegrees(Math.atan(-c)), -spec.tiltMax(), spec.tiltMax());
                    rollTarget = (float) Mth.clamp(Math.toDegrees(Math.atan(b)), -spec.tiltMax(), spec.tiltMax());
                }
            }
        }
        pose.pitch += (pitchTarget - pose.pitch) * k;
        pose.roll += (rollTarget - pose.roll) * k;
        if (Math.abs(pose.pitch) < 0.01f) pose.pitch = 0;
        if (Math.abs(pose.roll) < 0.01f) pose.roll = 0;
        if (!any && pose.pitch == 0 && pose.roll == 0) return;
        // tilt the body, then solve each leg from its hip (as the body now stands) to its pad on the ground
        body.setRotX(body.getRotX() + pose.pitch * Mth.DEG_TO_RAD);
        body.setRotZ(body.getRotZ() + pose.roll * Mth.DEG_TO_RAD);
        Matrix3f tilted = new Matrix3f().rotationZYX(body.getRotZ(), body.getRotY(), body.getRotX());
        Matrix3f back = new Matrix3f(tilted).transpose();
        for (int i = 0; i < n; i++) {
            if (feet[i] == null) continue;
            Vector3f t = new Vector3f((float) feet[i][0], (float) (feet[i][1] + pose.ground[i]), (float) feet[i][2]);
            // into the body's rest frame: undo its position and turn about its pivot
            t.sub(bp).sub(0, (float) spec.bodyY(), 0);
            back.transform(t);
            t.add(0, (float) spec.bodyY(), 0);
            double dx = t.x - spec.hipX()[i], dy = t.y - spec.bodyY(), dz = t.z - spec.hipZ()[i];
            double r = Math.hypot(dx, dz);
            double[] a = Legs.solve(r, dy, spec.femur(), spec.tibia());
            String name = spec.names()[i];
            GeoBone hip = bones.apply("hip_" + name), leg = bones.apply("leg_" + name), shin = bones.apply("shin_" + name);
            hip.setRotY((float) Math.atan2(-dx, -dz));
            leg.setRotX((float) a[0]);
            shin.setRotX((float) (a[1] - a[0]));
        }
    }

    /** Where leg {@code i}'s pad is as the clip posed it (model px), or null if the model lacks the leg. */
    private static double[] foot(Legs.Spec spec, int i, Function<String, GeoBone> bones, Matrix3f rb, Vector3f bp) {
        String name = spec.names()[i];
        GeoBone hip = bones.apply("hip_" + name), leg = bones.apply("leg_" + name), shin = bones.apply("shin_" + name);
        if (hip == null || leg == null || shin == null) return null;
        double yaw = hip.getRotY(), a1 = leg.getRotX(), a2 = a1 + shin.getRotX();
        double r = spec.femur() * Math.cos(a1) + spec.tibia() * Math.cos(a2), dy = spec.femur() * Math.sin(a1) + spec.tibia() * Math.sin(a2);
        Vector3f local = new Vector3f((float) (spec.hipX()[i] - Math.sin(yaw) * r), (float) dy, (float) (spec.hipZ()[i] - Math.cos(yaw) * r));
        // out of the body's frame: about its pivot, then its position
        rb.transform(local);
        local.add(bp).add(0, (float) spec.bodyY(), 0);
        return new double[]{local.x, local.y, local.z};
    }

    /**
     * Turns the whole model ({@code root}) by {@code angle} (radians) about the model axis {@code axis}, round the model
     * point {@code pivot} (px), then moves it by {@code shift} (px): on top of whatever the clips did to the root.
     */
    public static void turnRoot(GeoBone root, Vector3f axis, float angle, Vector3f pivot, Vector3f shift) {
        if (root == null || Math.abs(angle) < 1e-4f) return;
        Matrix3f turn = new Matrix3f().rotation(angle, axis.x, axis.y, axis.z);
        Matrix3f clip = new Matrix3f().rotationZYX(root.getRotZ(), root.getRotY(), root.getRotX());
        Vector3f e = new Matrix3f(turn).mul(clip).getEulerAnglesZYX(new Vector3f());
        root.setRotX(e.x);
        root.setRotY(e.y);
        root.setRotZ(e.z);
        Vector3f pos = new Vector3f(-root.getPosX(), root.getPosY(), root.getPosZ());
        Vector3f p = new Vector3f(pivot).sub(turn.transform(new Vector3f(pivot))).add(turn.transform(pos)).add(shift);
        root.setPosX(-p.x);
        root.setPosY(p.y);
        root.setPosZ(p.z);
    }

    /** A world direction (x, z) in the model's frame for a machine facing {@code yaw} degrees (x right +, z front -). */
    public static Vector3f toModel(double wx, double wy, double wz, float yaw) {
        float r = yaw * Mth.DEG_TO_RAD, sin = Mth.sin(r), cos = Mth.cos(r);
        double lx = wx * cos + wz * sin, lz = -wx * sin + wz * cos;
        return new Vector3f((float) -lx, (float) wy, (float) -lz);
    }

    /**
     * Onto the wall it climbs: the model's ground turned to the wall's face (90 degrees at {@code turned} 1) about the
     * foot line nearest it, and moved so its pads touch the wall where the box does ({@code halfWidth} px from the
     * middle). {@code footReach}: how far the rest pads reach out along each axis (px).
     */
    public static void wall(GeoBone root, Direction face, float yaw, double turned, double footReach, double halfWidth) {
        if (face == null || turned <= 0) return;
        Vector3f d = toModel(face.getStepX(), 0, face.getStepZ(), yaw).normalize();
        double s = turned * turned * (3 - 2 * turned);
        double reach = footReach * (Math.abs(d.x) + Math.abs(d.z));
        Vector3f pivot = new Vector3f(d).mul((float) reach);
        Vector3f shift = new Vector3f(d).mul((float) ((halfWidth - reach) * s));
        turnRoot(root, new Vector3f(-d.z, 0, d.x).normalize(), (float) (s * Math.PI / 2), pivot, shift);
    }

    /** Hanging on a line: the model's up turned toward the anchor ({@code up}, model frame, unit), round the hang point. */
    public static void hang(GeoBone root, Vec3 up, Vector3f hangPoint, float maxDegrees) {
        double angle = Math.acos(Mth.clamp(up.y, -1, 1));
        Vector3f axis = new Vector3f((float) up.z, 0, (float) -up.x);
        if (axis.lengthSquared() < 1e-8) return;
        axis.normalize();
        turnRoot(root, axis, (float) Math.min(angle, Math.toRadians(maxDegrees)), hangPoint, new Vector3f());
    }
}
