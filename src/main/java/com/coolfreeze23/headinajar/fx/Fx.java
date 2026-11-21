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
    /** The Surgical Saw takes a head: a = the neck, entityA = the cutter, entityB = the creature, arg 1 when the head goes into a jar. */
    public static final int HOST_CUT = 9;
    /** A jar seats on a host's neck: a = the neck, entityA = the jar's player, entityB = the host. */
    public static final int HOST_SEAT = 10;
    /** A jar leaves its host: a = the neck, entityB = the host. */
    public static final int HOST_UNSEAT = 11;
    /** A host is embalmed (or is seen embalmed when it comes into view): entityB = the host, arg 1 when it just happened. */
    public static final int HOST_EMBALMED = 12;
    /** A tool worked in the hand: entityA = who held it, entityB = what it worked on or -1, arg = {@link #TOOL_SYRINGE} etc. */
    public static final int TOOL = 13;
    /** A body became a corpse: a = where it lies, entityB = the body. */
    public static final int CORPSE = 14;
    /**
     * One jump of an arm's arc (the Arc Cannon's chain, the Tesla Coil's zaps), 2 ticks after the last: a = where it leaves
     * from (for the first jump the tool's muzzle as the server reckons it; the client uses the muzzle it drew), b = where
     * it lands, entityA = the machine (first jump) or the creature it jumps from, entityB = the creature hit or -1 (a miss,
     * at a block), arg = the tool's flat slot + 64 x the jump's number.
     */
    public static final int ARM_ARC = 15;
    /**
     * A creature the saw refused shakes its head no: a = its centre, entityB = the creature. Each client turns the head (or,
     * for a creature whose model has no turning head, the body) 25 degrees either way, period 6 ticks, over 12 ticks.
     */
    public static final int STAFF_SHAKE = 16;
    // 0.6.2's genes and the printer (an old client drops a kind it does not know)
    /** Phase: a = where the dash left, b = where it arrived, entityA = the body (a REVERSE_PORTAL trail between). */
    public static final int GENE_PHASE = 17;
    /** Tongue: a = the mouth, b = the tip's farthest point, entityA = the body, entityB = what it caught or -1, arg = 1 eaten. */
    public static final int GENE_TONGUE = 18;
    /** Snow Pounce lands: a = where, entityA = the body, arg = creatures caught. */
    public static final int GENE_POUNCE = 19;
    /** Roll Up: entityA = the body, arg = ticks it stays curled (0: uncurled), so the client mirrors the pose. */
    public static final int GENE_ROLL_UP = 20;
    /** Puffer: entityA = the body, arg = ticks puffed (0: deflated). */
    public static final int GENE_PUFFER = 21;
    /** A host printed from a template steps out: a = the doorstep, entityB = the host. Each client plays only the shake. */
    public static final int PRINTED = 22;
    /** Spit Gland: a = the mouth, entityA = the body (a jar's lid hops in jar form). */
    public static final int GENE_SPIT = 23;
    /** Fang Line cast: a = the caster's feet, entityA = the body, arg = 1 a ring, 0 a line. */
    public static final int GENE_FANGS = 24;
    /** Weeping Glands: entityA = the body, arg = 1 a tear dropped, 0 a sob (a mecha's cockpit fogs). */
    public static final int GENE_WEEP = 25;
    /** Spare kinds kept for the gene work: 26-27 Livestock and Field Office, 28 Night Shift and the rest, 29 actives. */
    public static final int GENE_SPARE_A = 26, GENE_SPARE_B = 27, GENE_SPARE_C = 28, GENE_SPARE_D = 29;
    /**
     * A seconded pair reached a quarter of team sync (0.7.1): a = the host's feet, entityB = the host, arg = the quarter
     * (1-4; 4 is 100%). Each client runs the sync ring down the body, the pale one on the ground at 4.
     */
    public static final int TEAM_SYNC_PULSE = 30;
    /** A striker's chant (0.7.2): entityA the striker, arg its beat count; clients pump its arms, head or jar twice. */
    public static final int CHANT = 31;
    /** The Promotion's confetti (0.7.2): at a, over entityA; teal and gold dust, a single grey puff under photosensitive. */
    public static final int CONFETTI = 32;
    /** A Promotion begins (0.7.2): entityA the candidate, entityB the presser, arg its length in ticks. */
    public static final int CEREMONY_LOOK = 33;

    public static final int TOOL_SYRINGE = 1, TOOL_SUTURE = 2, TOOL_WRENCH = 3;

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
        COUNTS.clear();
    }

    /**
     * How many memos of each kind have been sent about each entity (as either of its two entities), for tests. Kept apart
     * from the last 64, which tests running side by side can push a memo out of. At most {@value #COUNTED} pairs, oldest
     * dropped.
     */
    private static final int COUNTED = 4096;
    private static final java.util.LinkedHashMap<Long, Integer> COUNTS = new java.util.LinkedHashMap<>(256, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<Long, Integer> eldest) {
            return size() > COUNTED;
        }
    };

    private static long countKey(int kind, int entity) {
        return (long) kind << 32 | (entity & 0xFFFFFFFFL);
    }

    /** How many memos of {@code kind} have named {@code entity} (by id) since the server started. */
    public static synchronized int count(int kind, int entity) {
        return COUNTS.getOrDefault(countKey(kind, entity), 0);
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
            if (fx.entityA >= 0) COUNTS.merge(countKey(fx.kind, fx.entityA), 1, Integer::sum);
            if (fx.entityB >= 0 && fx.entityB != fx.entityA) COUNTS.merge(countKey(fx.kind, fx.entityB), 1, Integer::sum);
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
