package com.coolfreeze23.headinajar.form;

import com.coolfreeze23.headinajar.entity.HeadlessBody;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.network.Network;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.Set;

/**
 * The Noggin & Sons Courier: the safety net for a head stranded away from its body. From the jar menu (or
 * /headoffice courier) a jar books a delivery; 30 seconds later the body (or its corpse) is set down beside the head,
 * from anywhere, even another dimension or an unloaded chunk. The fee is a fifth of the jar's charge, refunded when the
 * body can't be found or the booking lapses.
 */
public final class Courier {
    public static final int DELIVERY_TICKS = 600;
    public static final String KEY = "headinajar_courier";
    /** The charge the booking took, for a refund. */
    public static final String FEE = "headinajar_courier_fee";
    /**
     * How many more ticks the courier keeps looking for a body whose chunk is still loading its entities. They load
     * after the chunk, from disk, and on a busy server that has been seen to take several seconds.
     */
    private static final String SEARCH = "headinajar_courier_search";
    private static final int SEARCH_TICKS = 400;
    /** Closer than this, the body is right there: no courier needed. */
    public static final double NEARBY = 8;
    /** Keeps the body's chunk loaded from the booking until well after the delivery, so its entities are there. */
    private static final TicketType<ChunkPos> TICKET = TicketType.create("headinajar_courier", Comparator.comparingLong(ChunkPos::toLong),
            DELIVERY_TICKS + SEARCH_TICKS + 200);

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
        int before = state.charge();
        state.setCharge(state.charge() - JarState.maxCharge() / 5);
        player.getPersistentData().putInt(FEE, before - state.charge());
        player.getPersistentData().putInt(SEARCH, SEARCH_TICKS);
        holdChunk(player, body);
        Network.sync(player);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1, 0.8f);
        return "courier.headinajar.booked";
    }

    /** The jar menu's Courier button: a booking is confirmed over the menu, a refusal says why and what to do. */
    public static void bookFromMenu(ServerPlayer player) {
        String result = book(player);
        String reason = result.substring("courier.headinajar.".length());
        switch (reason) {
            case "booked" -> Network.refused(player, Component.translatable(result));
            case "no_body" -> Refusals.deny(player, "courier.no_body", Component.translatable("block.headinajar.body_printer"));
            default -> Refusals.deny(player, "courier." + reason);
        }
    }

    /** Loads the body's chunk for the delivery: its entities load a moment after the chunk does. */
    private static void holdChunk(ServerPlayer player, HeadRegistry.Body body) {
        ServerLevel from = player.server.getLevel(body.spot().dimension());
        if (from == null) return;
        ChunkPos chunk = new ChunkPos(BlockPos.containing(body.spot().pos()));
        from.getChunkSource().addRegionTicket(TICKET, chunk, 2, chunk);
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

    /** The player is no longer a jar (linked, sutured): a booking still open lapses and the fee comes back. */
    public static void cancel(ServerPlayer player) {
        if (player.getPersistentData().getInt(KEY) <= 0) return;
        refund(player);
        player.displayClientMessage(Component.translatable("courier.headinajar.cancelled"), false);
    }

    private static void refund(ServerPlayer player) {
        JarState state = FormActions.state(player);
        state.setCharge(state.charge() + player.getPersistentData().getInt(FEE));
        player.getPersistentData().remove(KEY);
        player.getPersistentData().remove(FEE);
        player.getPersistentData().remove(SEARCH);
        Network.sync(player);
    }

    /**
     * Fetches the body from wherever it is and sets it down beside the head. Returns whether it arrived. A body whose
     * chunk is still loading is looked for again each tick for a few seconds; one that is gone for good refunds the fee.
     */
    public static boolean deliver(ServerPlayer player) {
        HeadRegistry registry = HeadRegistry.get(player);
        HeadRegistry.Body record = registry.body(player.getUUID());
        ServerLevel from = record == null ? null : player.server.getLevel(record.spot().dimension());
        var found = from == null ? null : from.getEntity(record.entity());
        if (!(found instanceof HeadlessBody body)) {
            int search = player.getPersistentData().getInt(SEARCH);
            if (record != null && from != null && search > 0) {
                // not loaded yet: keep the chunk and look again next tick
                player.getPersistentData().putInt(SEARCH, search - 1);
                player.getPersistentData().putInt(KEY, 1);
                holdChunk(player, record);
                return false;
            }
            refund(player);
            player.displayClientMessage(Component.translatable("courier.headinajar.lost", Component.translatable("block.headinajar.body_printer")), false);
            return false;
        }
        if (!body.isAlive()) {
            refund(player);
            player.displayClientMessage(Component.translatable("courier.headinajar.lost", Component.translatable("block.headinajar.body_printer")), false);
            return false;
        }
        player.getPersistentData().remove(KEY);
        player.getPersistentData().remove(FEE);
        player.getPersistentData().remove(SEARCH);
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
        // a corpse is not walked into with the Swap key: it takes the Suture Kit
        player.displayClientMessage(record.corpse()
                ? Component.translatable("courier.headinajar.delivered_corpse", Component.translatable("item.headinajar.suture_kit"))
                : Component.translatable("courier.headinajar.delivered"), false);
        return true;
    }
}
