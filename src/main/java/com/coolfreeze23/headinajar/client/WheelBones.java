package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.mecha.Wheels;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.function.Function;

/**
 * What code does to a wheeled skeleton's bones every frame, on top of the clips (the Wheelhouse's and the Rover's
 * renderers both call it): the wheels' spin from distance, the steering angle on the steer bones and the helm, the lean
 * (and the sway it gives a bone that swings with it, three times as far), each station's ride, the machine's tilt on uneven ground,
 * the drift's swing of the rear, and the brake lamps.
 */
public final class WheelBones {
    private WheelBones() {
    }

    /**
     * @param steering whether the code holds the steering (no turn or drift clip is playing)
     * @param brakes   the brake lamps' state (lit), or null to leave them to the clips
     * @param spinning whether the wheels spin (not while the machine collapses: its clip spins the wheel that comes off)
     */
    public static void apply(Wheels.Spec spec, Wheels.State w, Function<String, GeoBone> bones, boolean steering, Boolean brakes, boolean spinning) {
        float deg = Mth.DEG_TO_RAD;
        for (int i = 0; i < 4; i++) {
            String c = Wheels.CORNERS[i];
            if (spinning) {
                GeoBone wheel = bones.apply("wheel_" + c);
                if (wheel != null) wheel.setRotX(wheel.getRotX() + w.spin[i] * deg);
            }
            GeoBone steer = bones.apply("steer_" + c);
            if (steer != null && steering && w.steer != 0) {
                float angle = i < 2 ? w.steer : -w.steer * spec.rearShare();
                steer.setRotY(steer.getRotY() + angle * deg);
            }
            GeoBone strut = bones.apply("strut_" + c);
            if (strut != null && w.ride[i] != 0) strut.setPosY(strut.getPosY() + w.ride[i]);
        }
        GeoBone helm = bones.apply("helm");
        if (helm != null && steering && w.steer != 0 && spec.helmRatio() != 0) helm.setRotZ(helm.getRotZ() + w.steer * spec.helmRatio() * deg);
        GeoBone body = bones.apply("body");
        if (body != null && w.lean != 0) body.setRotZ(body.getRotZ() + w.lean * deg);
        if (spec.swayBone() != null && w.lean != 0) {
            GeoBone sway = bones.apply(spec.swayBone());
            if (sway != null) sway.setRotZ(sway.getRotZ() + w.lean * 3 * deg);
        }
        GeoBone chassis = bones.apply("chassis");
        if (chassis != null && (w.pitch != 0 || w.roll != 0)) {
            chassis.setRotX(chassis.getRotX() + w.pitch * deg);
            chassis.setRotZ(chassis.getRotZ() + w.roll * deg);
        }
        GeoBone root = bones.apply("root");
        if (root != null && w.drift != 0) root.setRotY(root.getRotY() + w.drift * deg);
        if (brakes != null) {
            GeoBone lamp = bones.apply("lamp_brake");
            if (lamp != null) {
                float s = brakes ? 1 : 0;
                lamp.setScaleX(s);
                lamp.setScaleY(s);
                lamp.setScaleZ(s);
            }
        }
    }
}
