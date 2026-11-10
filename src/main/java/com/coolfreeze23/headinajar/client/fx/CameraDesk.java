package com.coolfreeze23.headinajar.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * The camera service: shake as decaying trauma, short deterministic kicks, and field-of-view pulls, all applied in
 * {@link ViewportEvent} (the view-bobbing path is cancelled by other mods). Limits from the style guide: trauma 0-1
 * decaying 0.05 a tick, rotation trauma squared x 3 degrees, pitch and roll only, smooth noise; never roll while riding;
 * everything x {@link Comfort#motion()} (0 under the photosensitive option, then the 0-100% slider), and halved when
 * Camera Overhaul or Perception is loaded, since they shake the camera too.
 */
public final class CameraDesk {
    private static float trauma, traumaO;
    private static float kickPitch, kickTime = 99, kickLength = 8, kickRise = 2;
    private static float fovPull, fovTime = 99, fovRise = 4, fovFall = 8;
    private static int ticks;
    private static Boolean other;

    /** Adds trauma (0.04-0.1 for a knock, 0.25-0.5 for a signature hit). */
    public static void shake(float amount) {
        trauma = Mth.clamp(trauma + amount, 0, 1);
    }

    /** Shake felt by the local player near a big event at {@code at}: scaled 1 - d/8, nothing beyond 8 blocks. */
    public static void nearby(Vec3 at, float amount) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        double d = player.getEyePosition().distanceTo(at);
        if (d < 8) shake(amount * (float) (1 - d / 8) * (others() ? 0.5f : 1));
    }

    /** A pitch kick of {@code degrees} (positive looks up), rising over {@code rise} ticks and settling over {@code fall}. */
    public static void kick(float degrees, int rise, int fall) {
        kickPitch = degrees;
        kickRise = rise;
        kickLength = rise + fall;
        kickTime = 0;
    }

    /** A field-of-view pull of {@code degrees} (negative narrows), in over {@code rise} ticks, back over {@code fall}. */
    public static void fov(float degrees, int rise, int fall) {
        fovPull = degrees;
        fovRise = rise;
        fovFall = fall;
        fovTime = 0;
    }

    public static void tick() {
        ticks++;
        traumaO = trauma;
        trauma = Math.max(0, trauma - 0.05f);
        if (kickTime < 99) kickTime++;
        if (fovTime < 99) fovTime++;
    }

    private static boolean others() {
        if (other == null) other = ModList.get().isLoaded("cameraoverhaul") || ModList.get().isLoaded("perception");
        return other;
    }

    private static float scale() {
        return Comfort.motion() * (others() ? 0.5f : 1);
    }

    /** Smooth value noise in -1..1 (the same at the same time, so a frame never jumps). */
    private static float noise(float t, int axis) {
        int i = Mth.floor(t);
        float f = t - i;
        float a = hash(i, axis), b = hash(i + 1, axis);
        float s = f * f * (3 - 2 * f);
        return Mth.lerp(s, a, b);
    }

    private static float hash(int i, int axis) {
        int h = i * 374761393 + axis * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 32767.5f - 1;
    }

    static float envelope(float t, float rise, float length) {
        if (t >= length) return 0;
        if (t < rise) return Mth.sin(t / rise * Mth.HALF_PI);
        float u = (t - rise) / Math.max(1, length - rise);
        return 1 - u * u * (3 - 2 * u);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void angles(ViewportEvent.ComputeCameraAngles event) {
        float k = scale();
        if (k <= 0) return;
        float pt = (float) event.getPartialTick();
        float tr = Mth.lerp(pt, traumaO, trauma);
        float rot = tr * tr * 3 * k;
        float time = (ticks + pt) * 0.6f;
        float pitch = rot * noise(time, 1) + kickPitch * envelope(kickTime + pt, kickRise, kickLength) * k;
        event.setPitch(event.getPitch() - pitch);
        var player = Minecraft.getInstance().player;
        // never touch roll while riding or piloting (other mods roll mounts; a cockpit must stay level)
        if (player != null && !player.isPassenger()) event.setRoll(event.getRoll() + rot * noise(time, 2));
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov event) {
        if (fovTime >= fovRise + fovFall) return;
        float k = scale() * Minecraft.getInstance().options.fovEffectScale().get().floatValue();
        if (k <= 0) return;
        float e = envelope(fovTime + (float) event.getPartialTick(), fovRise, fovRise + fovFall);
        event.setFOV(event.getFOV() + fovPull * e * k);
    }

    /** For tests and the harness: the current trauma. */
    public static float trauma() {
        return trauma;
    }

    private CameraDesk() {
    }
}
