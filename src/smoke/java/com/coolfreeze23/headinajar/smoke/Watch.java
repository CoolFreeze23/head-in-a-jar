package com.coolfreeze23.headinajar.smoke;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * A camera that is not where the player is, for filming the player's own machine from outside while the player drives
 * it: fixed ({@code watch X Y Z YAW PITCH}), riding along beside the player's vehicle at a fixed offset
 * ({@code watch follow DX DY DZ YAW PITCH}), or fixed at such an offset from where the vehicle is now
 * ({@code watch at DX DY DZ YAW PITCH}); {@code watch off} gives the view back. The player stays the camera entity in
 * third person (so the keys still drive it and everything is drawn); only the camera's place and angles are replaced,
 * after the game sets them up (CameraWatchMixin).
 */
public final class Watch {
    private static Vec3 at;
    private static Vec3 offset;
    private static float yaw, pitch;

    private Watch() {
    }

    static String run(Minecraft mc, String[] a) {
        if (a[0].equals("off")) {
            at = null;
            offset = null;
            return "watch off";
        }
        boolean moving = a[0].equals("follow"), near = a[0].equals("at");
        int i = moving || near ? 1 : 0;
        Vec3 v = new Vec3(Double.parseDouble(a[i]), Double.parseDouble(a[i + 1]), Double.parseDouble(a[i + 2]));
        yaw = Float.parseFloat(a[i + 3]);
        pitch = Float.parseFloat(a[i + 4]);
        Entity base = base(mc);
        if (moving) {
            offset = v;
            at = null;
        } else {
            offset = null;
            at = near ? base.position().add(v) : v;
        }
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        Vec3 now = at != null ? at : base.position().add(offset);
        return String.format(java.util.Locale.ROOT, "watch %s at %.1f %.1f %.1f yaw %.0f pitch %.0f", moving ? "follow" : near ? "at" : "fixed", now.x, now.y, now.z, yaw, pitch);
    }

    private static Entity base(Minecraft mc) {
        return mc.player.getVehicle() != null ? mc.player.getVehicle() : mc.player;
    }

    /** The camera's place and angles this frame (x, y, z, yaw, pitch), or null when not watching. */
    public static double[] camera(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || at == null && offset == null) return null;
        Vec3 p = at != null ? at : base(mc).getPosition(partialTick).add(offset);
        return new double[]{p.x, p.y, p.z, yaw, pitch};
    }
}
