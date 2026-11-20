package com.coolfreeze23.headinajar.host;

import com.coolfreeze23.headinajar.HeadInAJar;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The tricks' two payloads, both optional (a game on either side without them still connects under the same version,
 * and is simply not told): what a host's trick is doing now, for its rider's client and onlookers, and a cue for the
 * pilot's own client (a pig's Rooting outlines).
 */
public final class TrickNet {
    /** {@link HostState#bits}: a turtle withdrawn into its shell (its rider's input moves it nowhere). */
    public static final int WITHDRAWN = 1;
    /** {@link HostState#bits}: a strider's Warm Up is on (the HUD says so). */
    public static final int WARMING = 2;
    /** {@link HostState#bits}: a frog is in its Long Jump. */
    public static final int LEAPING = 4;

    private TrickNet() {
    }

    /** Server to clients: a host's trick state, sent when it changes and when a player starts seeing the host. */
    public record HostState(int entityId, int bits) implements CustomPacketPayload {
        public static final Type<HostState> TYPE = new Type<>(HeadInAJar.id("host_tricks"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HostState> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, HostState::entityId, ByteBufCodecs.VAR_INT, HostState::bits, HostState::new);

        @Override
        public Type<HostState> type() {
            return TYPE;
        }
    }

    /** Server to the pilot: a cue for its own client ({@code rooting}: outline what is worth digging up within {@code radius}). */
    public record HostCue(String kind, int ticks, int radius) implements CustomPacketPayload {
        public static final Type<HostCue> TYPE = new Type<>(HeadInAJar.id("host_cue"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HostCue> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(32), HostCue::kind, ByteBufCodecs.VAR_INT, HostCue::ticks, ByteBufCodecs.VAR_INT, HostCue::radius, HostCue::new);

        @Override
        public Type<HostCue> type() {
            return TYPE;
        }
    }

    /** Registers both on an optional registrar ({@code registrar.optional()}). */
    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(HostState.TYPE, HostState.CODEC, (msg, ctx) -> {
            if (ctx.player().level().getEntity(msg.entityId()) instanceof Mob mob) apply(Hosts.data(mob), msg.bits());
        });
        registrar.playToClient(HostCue.TYPE, HostCue.CODEC, (msg, ctx) -> {
            if (msg.kind().equals("rooting")) com.coolfreeze23.headinajar.client.RootingOutlines.start(msg.ticks(), msg.radius());
        });
    }

    /** A client's copy of a host's trick state (the times are the server's, so a client keeps only "on" or "off"). */
    static void apply(HostData data, int bits) {
        data.withdrawnUntil = (bits & WITHDRAWN) != 0 ? Long.MAX_VALUE : 0;
        data.warming = (bits & WARMING) != 0;
        data.leaping = (bits & LEAPING) != 0;
    }

    /** A host's trick state now (server side). */
    static int bits(Mob host, HostData data) {
        return (data.withdrawn(host.level().getGameTime()) ? WITHDRAWN : 0) | (data.warming ? WARMING : 0) | (data.leaping ? LEAPING : 0);
    }

    private static boolean open(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return player.connection != null && player.connection.hasChannel(type);
    }

    /** Tells every player near enough to see the host (whose game has the channel) its trick state. */
    static void send(Mob host) {
        if (!(host.level() instanceof ServerLevel level)) return;
        HostState msg = new HostState(host.getId(), bits(host, Hosts.data(host)));
        double range = host.getType().clientTrackingRange() * 16.0 + 16;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(host) <= range * range && open(p, HostState.TYPE)) PacketDistributor.sendToPlayer(p, msg);
        }
    }

    /** Tells one player who just started seeing the host, if there is anything to tell. */
    static void sendTo(ServerPlayer viewer, Mob host) {
        int bits = bits(host, Hosts.data(host));
        if (bits != 0 && open(viewer, HostState.TYPE)) PacketDistributor.sendToPlayer(viewer, new HostState(host.getId(), bits));
    }

    /** Sends the Rooting cue to the pilot's own client; false when its game has no channel for it (the server then does it). */
    static boolean cueRooting(ServerPlayer pilot, int ticks, int radius) {
        if (!open(pilot, HostCue.TYPE)) return false;
        PacketDistributor.sendToPlayer(pilot, new HostCue("rooting", ticks, radius));
        return true;
    }
}
