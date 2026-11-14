package com.coolfreeze23.headinajar.clarity;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.entity.HeadJarEntity;
import com.coolfreeze23.headinajar.entity.HeadlessBody;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

/**
 * Finding your other half: the link bar's chevron points at your head (from the body) or your body (from the jar) when
 * it is near enough for this client to see it, and your own corpse sends up a small orange flare every two seconds, seen
 * only by you. Nothing is sent: both are read from what this client already has loaded.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID, value = Dist.CLIENT)
public final class Wayfinding {
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private static final double SEARCH = 96;

    private Wayfinding() {
    }

    /** Your loaded head (looking for the head) or body (looking for the body), or null. */
    public static Entity otherHalf(Player player, boolean head) {
        AABB box = player.getBoundingBox().inflate(SEARCH);
        if (head) {
            for (HeadJarEntity e : player.level().getEntitiesOfClass(HeadJarEntity.class, box)) {
                if (player.getUUID().equals(e.ownerId())) return e;
            }
        } else {
            for (HeadlessBody e : player.level().getEntitiesOfClass(HeadlessBody.class, box)) {
                if (player.getUUID().equals(e.ownerId())) return e;
            }
        }
        return null;
    }

    /** An arrow toward a spot, turned to where the player faces (up is straight ahead), or null when on top of it. */
    public static String chevron(Player player, Vec3 to) {
        double dx = to.x - player.getX(), dz = to.z - player.getZ();
        if (dx * dx + dz * dz < 4) return null;
        float bearing = (float) (Mth.atan2(dz, dx) * (180 / Math.PI)) - 90;
        float rel = Mth.wrapDegrees(bearing - player.getYRot());
        int i = Math.floorMod(Math.round(rel / 45f), 8);
        return ARROWS[i];
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.isPaused() || mc.player.tickCount % 40 != 0) return;
        if (!(otherHalf(mc.player, false) instanceof HeadlessBody body) || !body.isCorpse()) return;
        // a corpse's flare: a short orange column, for its owner only
        DustParticleOptions orange = new DustParticleOptions(new Vector3f(1.0f, 0.55f, 0.1f), 1.2f);
        int n = com.coolfreeze23.headinajar.client.fx.Comfort.particles(6);
        for (int k = 0; k < n; k++) {
            mc.level.addParticle(orange, body.getX(), body.getY() + body.getBbHeight() + 0.2 + k * 0.35, body.getZ(), 0, 0.05, 0);
        }
    }
}
