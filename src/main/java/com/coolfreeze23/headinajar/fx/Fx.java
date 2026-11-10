package com.coolfreeze23.headinajar.fx;

import com.coolfreeze23.headinajar.HeadInAJar;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * One memo per phenomenon: the server says what happened (a kind, two places, two entities, a number and a seed) and
 * each client draws it from the kit. Nothing in the mod sends a packet per particle; effects that follow state a
 * client already has (what a jar holds, a Tesla link, a turret's lock) are drawn from that state with no memo at all.
 */
public record Fx(int kind, Vec3 a, Vec3 b, int entityA, int entityB, float arg, int seed) implements CustomPacketPayload {
    /** Telekinesis took hold: a = the target's centre, entityA = holder, entityB = held (may not have reached the client yet). */
    public static final int TK_GRAB = 1;
    /** Thrown: a = release point, b = velocity, entityA = holder, entityB = what flies. */
    public static final int TK_THROW = 2;
    /** Set down on purpose: a = where it is, entityA = holder, entityB = held. */
    public static final int TK_SET_DOWN = 3;
    /** Dropped because the hold ended: arg = {@link #DROP_CHARGE}, {@link #DROP_RANGE}, {@link #DROP_GONE}. */
    public static final int TK_DROP = 4;
    /** A grab that failed: a = where the line reached, entityA = holder, entityB = the refused thing or -1, arg = reason code. */
    public static final int TK_FIZZLE = 5;
    /** The swap: a = the goggles, b = the collar, entityA = the body left behind (to head) or -1, arg = 1 head to body, -1 body to head, 0 far. */
    public static final int SWAP = 6;
    /** Lacrimal Laser: a = the eye point between the goggles, b = where it hit, entityA = the shooter, arg = 1 when it hit a creature. */
    public static final int LASER = 7;
    /** A guardian turret's shot lands: a = the target point. */
    public static final int TURRET_SHOT = 8;

    public static final int DROP_CHARGE = 1, DROP_RANGE = 2, DROP_GONE = 3;
    /** Fizzle reasons (the client picks a look; the refusal line says why in words). */
    public static final int WHY_NOTHING = 0, WHY_HARD = 1, WHY_MACHINE = 2, WHY_BOSS = 3, WHY_TRUST = 4, WHY_OTHER = 5;

    /** How far a memo is sent (players nearer than this to its first point, in the same level). */
    public static final double RANGE = 64;

    public static final Type<Fx> TYPE = new Type<>(HeadInAJar.id("fx"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Fx> CODEC = StreamCodec.of((buf, m) -> {
        buf.writeVarInt(m.kind);
        buf.writeDouble(m.a.x);
        buf.writeDouble(m.a.y);
        buf.writeDouble(m.a.z);
        buf.writeFloat((float) m.b.x);
        buf.writeFloat((float) m.b.y);
        buf.writeFloat((float) m.b.z);
        buf.writeVarInt(m.entityA + 1);
        buf.writeVarInt(m.entityB + 1);
        buf.writeFloat(m.arg);
        buf.writeInt(m.seed);
    }, buf -> new Fx(buf.readVarInt(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
            new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat()), buf.readVarInt() - 1, buf.readVarInt() - 1, buf.readFloat(), buf.readInt()));

    @Override
    public Type<Fx> type() {
        return TYPE;
    }

    public static Fx of(int kind, Vec3 a, Vec3 b, int entityA, int entityB, float arg) {
        return new Fx(kind, a, b == null ? Vec3.ZERO : b, entityA, entityB, arg, (int) (Double.doubleToLongBits(a.x * 31 + a.z) ^ kind * 977L));
    }

    /** What was sent, for tests: the memo and how many players it went to. Last 64, oldest first. */
    public record Sent(Fx fx, int recipients, long gameTime) {
    }

    private static final ArrayDeque<Sent> SENT = new ArrayDeque<>();

    public static synchronized List<Sent> sent() {
        return new ArrayList<>(SENT);
    }

    public static synchronized void clearSent() {
        SENT.clear();
    }

    /** Sends one memo to every player within {@link #RANGE} of its first point. Returns how many got it. */
    public static int send(ServerLevel level, Fx fx) {
        int n = 0;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(fx.a) > RANGE * RANGE) continue;
            if (p.connection != null && p.connection.hasChannel(TYPE)) {
                PacketDistributor.sendToPlayer(p, fx);
                n++;
            }
        }
        synchronized (Fx.class) {
            SENT.addLast(new Sent(fx, n, level.getGameTime()));
            while (SENT.size() > 64) SENT.removeFirst();
        }
        return n;
    }

    /** The point a tether leaves from on a jar: the head's eyes, between the goggles. */
    public static Vec3 eyes(Entity holder) {
        return holder.getEyePosition();
    }

    /** Registers the payload (called from the mod's payload registration). */
    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(TYPE, CODEC, (msg, ctx) -> com.coolfreeze23.headinajar.client.fx.FxManager.handle(msg));
    }
}
