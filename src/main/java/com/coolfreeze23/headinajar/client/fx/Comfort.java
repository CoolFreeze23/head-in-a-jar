package com.coolfreeze23.headinajar.client.fx;

import com.coolfreeze23.headinajar.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.world.phys.Vec3;

/**
 * The one photosensitivity and reduced-motion check every effect calls. With the mod's photosensitive option on,
 * flashes become fades, strobes go steady, the camera never moves and the swap wipe is a crossfade; the screen-shake
 * slider (0-100%) scales every camera movement; vanilla's particle setting scales every particle count.
 */
public final class Comfort {
    /** The mod's existing photosensitive option. */
    public static boolean photosensitive() {
        return Config.CLIENT_SPEC.isLoaded() && Config.PHOTOSENSITIVE.get();
    }

    /** 0..1: how much the camera may move (0 under the photosensitive option). */
    public static float motion() {
        if (photosensitive()) return 0;
        return Config.CLIENT_SPEC.isLoaded() ? Config.SCREEN_SHAKE.get() / 100f : 1f;
    }

    /** How many of {@code n} particles to make under vanilla's particle setting: all, half, or none. */
    public static int particles(int n) {
        ParticleStatus status = Minecraft.getInstance().options.particles().get();
        return switch (status) {
            case ALL -> n;
            case DECREASED -> (n + 1) / 2;
            case MINIMAL -> 0;
        };
    }

    /** Particles are for close up: none beyond 32 blocks of the camera (vanilla's own limit). */
    public static boolean nearEnough(Vec3 at) {
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        return cam.isInitialized() && cam.getPosition().distanceToSqr(at) < 32 * 32;
    }

    /** How often an arc's shape may change, in ticks: every 2, or every 6 under the photosensitive option. */
    public static int rerollTicks(int normal) {
        return photosensitive() ? Math.max(6, normal) : normal;
    }

    private Comfort() {
    }
}
