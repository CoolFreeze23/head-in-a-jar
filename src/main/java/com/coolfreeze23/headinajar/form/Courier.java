package com.coolfreeze23.headinajar.form;

import com.coolfreeze23.headinajar.entity.HeadlessBody;
import com.coolfreeze23.headinajar.network.Network;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * The Noggin & Sons Courier: the safety net for a head stranded away from its body. From the jar menu (or
 * /headoffice courier) a jar books a delivery; 30 seconds later the body (or its corpse) is set down beside the head,
 * from anywhere, even another dimension or an unloaded chunk. The fee is a fifth of the jar's charge.
 */
public final class Courier {
    public static final int DELIVERY_TICKS = 600;
    public static final String KEY = "headinajar_courier";
    /** Closer than this, the body is right there: no courier needed. */
    public static final double NEARBY = 8;

    private Courier() {
    }

    /** Books a delivery. Returns the message key explaining the outcome. */
    public static String book(ServerPlayer player) {
        JarState state = FormActions.state(player);
        if (state.form() != Form.JAR && state.form() != Form.SOCKET) return "courier.headinajar.only_jars";
        HeadRegistry.Body body = HeadRegistry.get(player).body(player.getUUID());
        if (body == null) return "courier.headinajar.no_body";
        if (body.spot().dimension().equals(player.level().dimension()) && body.spot().pos().distanceTo(player.position()) < NEARBY) {
            return "courier.headinajar.already_here";
        }
        if (player.getPersistentData().getInt(KEY) > 0) return "courier.headinajar.on_its_way";
        player.getPersistentData().putInt(KEY, DELIVERY_TICKS);
        state.setCharge(state.charge() - JarState.maxCharge() / 5);
        Network.sync(player);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1, 0.8f);
        return "courier.headinajar.booked";
    }

    /** Counts a booked delivery down (called every server tick for jarred players). */
    public static void tick(ServerPlayer player) {
        int left = player.getPersistentData().getInt(KEY);
        if (left <= 0) return;
        player.getPersistentData().putInt(KEY, --left);
        if (left > 0 && left % 100 == 0) {
            player.displayClientMessage(Component.translatable("courier.headinajar.eta", left / 20), true);
        }
        if (left == 0) deliver(player);
    }

    /** Fetches the body from wherever it is and sets it down beside the head. Returns whether it arrived. */
    public static boolean deliver(ServerPlayer player) {
        player.getPersistentData().remove(KEY);
        HeadRegistry registry = HeadRegistry.get(player);
        HeadRegistry.Body record = registry.body(player.getUUID());
        if (record == null) return false;
        ServerLevel from = player.server.getLevel(record.spot().dimension());
        if (from == null) return false;
        // load the chunk it was last seen in, so an unloaded body can be found
        from.getChunk(BlockPos.containing(record.spot().pos()));
        if (!(from.getEntity(record.entity()) instanceof HeadlessBody body) || !body.isAlive()) {
            player.displayClientMessage(Component.translatable("courier.headinajar.lost"), false);
            return false;
        }
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        Vec3 to = player.position().add(look.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : look.normalize().scale(1.5));
        ServerLevel here = player.serverLevel();
        body.teleportTo(here, to.x, player.getY(), to.z, Set.of(), player.getYRot() + 180, 0);
        var moved = here.getEntity(record.entity());
        if (moved instanceof HeadlessBody arrived) {
            registry.setBody(player.getUUID(), new HeadRegistry.Body(arrived.getUUID(), HeadRegistry.Spot.of(arrived), record.corpse()));
            FormActions.state(player).setBody(arrived.getUUID());
        }
        here.sendParticles(ParticleTypes.CLOUD, to.x, player.getY() + 1, to.z, 20, 0.4, 0.8, 0.4, 0.02);
        here.playSound(null, BlockPos.containing(to), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1, 1.2f);
        player.displayClientMessage(Component.translatable("courier.headinajar.delivered"), false);
        return true;
    }
}
