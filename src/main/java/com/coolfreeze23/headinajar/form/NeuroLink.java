package com.coolfreeze23.headinajar.form;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.item.CollarCamItem;
import com.coolfreeze23.headinajar.item.RelayGogglesItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The neuro-link between a headless body and its head: quality (Q, 0-100) from distance and surroundings,
 * and what happens when it fails. Server side.
 */
public final class NeuroLink {
    public static final int QUALITY_TO_LINK = 20;
    private static final int DEAD_LINK_GRACE = 100;

    /** Range of the goggles in blocks, after the Collar Cam's condition. */
    public static double range(JarState state) {
        double range = state.goggles().getItem() instanceof RelayGogglesItem goggles ? goggles.range() : 0;
        if (CollarCamItem.isCrooked(state.collar())) range *= 0.9;
        return range;
    }

    /** Q from distance alone: full to half range, then falling to zero at the edge. */
    public static int fromDistance(double distance, double range) {
        if (range <= 0) return 0;
        if (distance <= range * 0.5) return 100;
        if (distance >= range) return 0;
        return (int) Math.round(100 * (1 - (distance - range * 0.5) / (range * 0.5)));
    }

    /** Multiplier for what surrounds the body. */
    public static double environment(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        double f = 1;
        if (player.isUnderWater()) f *= 0.6;
        if (level.getBrightness(LightLayer.SKY, pos) == 0 && pos.getY() < level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) - 16) f *= 0.8;
        if (level.isThundering()) f *= 0.85;
        if (level.getPoiManager().getCountInRange(p -> p.is(PoiTypes.NETHER_PORTAL), pos, 8, PoiManager.Occupancy.ANY) > 0) f *= 0.7;
        if (player.hasEffect(MobEffects.DARKNESS)) f *= 0.5;
        var fatigue = player.getEffect(MobEffects.DIG_SLOWDOWN);
        if (fatigue != null && fatigue.getAmplifier() >= 2) f *= 0.5;
        return f;
    }

    /** Q between this player and a spot (its head when LINKED, its body when a jar); -1 distance means another dimension. */
    public static int quality(ServerPlayer player, JarState state, HeadRegistry.Spot other) {
        if (state.carryingHead()) return 100;
        if (other == null || !other.dimension().equals(player.level().dimension())) return 0;
        double distance = player.position().distanceTo(other.pos());
        return (int) Math.round(fromDistance(distance, range(state)) * environment(player));
    }

    /** Runs every server tick for a decapitated player. */
    public static void tick(ServerPlayer player, JarState state) {
        if (player.tickCount % 10 != 0) return;
        HeadRegistry registry = HeadRegistry.get(player);
        if (state.form() == Form.LINKED) {
            HeadRegistry.Head head = registry.head(player.getUUID());
            if (state.carryingHead()) {
                state.setLink(100, 0);
                state.setDeadLinkTicks(0);
                return;
            }
            if (head == null || head.where() == HeadRegistry.Where.PLAYER || head.where() == HeadRegistry.Where.CARRIED) {
                // nothing to link to: a broken record; leave the player whole rather than stranded
                state.setLink(0, -1);
                return;
            }
            boolean otherWorld = !head.spot().dimension().equals(player.level().dimension());
            int q = quality(player, state, head.spot());
            state.setLink(q, otherWorld ? -1 : (float) player.position().distanceTo(head.spot().pos()));
            if (otherWorld) {
                breakLink(player, Component.translatable("message.headinajar.link_lost.dimension"));
                return;
            }
            state.setDeadLinkTicks(q == 0 ? state.deadLinkTicks() + 10 : 0);
            if (state.deadLinkTicks() >= DEAD_LINK_GRACE) {
                breakLink(player, Component.translatable("message.headinajar.link_lost"));
            }
        } else if (state.form() == Form.JAR) {
            HeadRegistry.Body body = registry.body(player.getUUID());
            if (body == null || body.corpse() || state.collar().isEmpty()) {
                state.setLink(0, -1);
                return;
            }
            boolean otherWorld = !body.spot().dimension().equals(player.level().dimension());
            state.setLink(quality(player, state, body.spot()), otherWorld ? -1 : (float) player.position().distanceTo(body.spot().pos()));
        }
    }

    /** The link failed: the player wakes up in their head and the body stays standing. */
    public static void breakLink(ServerPlayer player, Component why) {
        if (FormActions.linkedToJar(player)) {
            player.displayClientMessage(why, true);
        }
    }

    public static boolean manualUnlinkAllowed() {
        return !Config.SPEC.isLoaded() || Config.ALLOW_MANUAL_UNLINK.get();
    }

    private NeuroLink() {
    }
}
