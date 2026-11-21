package com.coolfreeze23.headinajar.staff.work;

import com.coolfreeze23.headinajar.HeadInAJar;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The workforce's payloads, all optional (a game on either side without them still connects; a server sends one only
 * when the client has its channel): a worker's floating line and its look (to clients), and a typed name and a
 * whistle's point (to the server). The Time Clock's own are {@link ClockNet}'s.
 */
public final class WorkerNet {
    private WorkerNet() {
    }

    /** A floating line over a worker for 5 seconds (a stop line, an answer). */
    public record Say(int entity, Component line) implements CustomPacketPayload {
        public static final Type<Say> TYPE = new Type<>(HeadInAJar.id("worker_say"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Say> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Say::entity, ComponentSerialization.STREAM_CODEC, Say::line, Say::new);

        @Override
        public Type<Say> type() {
            return TYPE;
        }
    }

    /**
     * How a worker looks to others: its employer's colour (the badge) and bits ({@link #CARRYING}, {@link #NOTICE},
     * {@link #HIRED}, {@link #HAT}...). Sent when a player starts seeing it and when any of it changes. Colour -1: not
     * staff (the loop comes off). {@code name}: its given name for the nameplate, empty unless this viewer may see it.
     */
    public record Look(int entity, int colour, int bits, Component name) implements CustomPacketPayload {
        public static final Type<Look> TYPE = new Type<>(HeadInAJar.id("worker_look"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Look> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Look::entity, ByteBufCodecs.INT, Look::colour, ByteBufCodecs.VAR_INT, Look::bits,
                ComponentSerialization.STREAM_CODEC, Look::name, Look::new);

        @Override
        public Type<Look> type() {
            return TYPE;
        }
    }

    public static final int CARRYING = 1, NOTICE = 1 << 1, HIRED = 1 << 2, HAT = 1 << 3, READING = 1 << 4;
    /**
     * 0.7.2: the grade (0 Intern .. 3 Team Lead) in two bits at {@link #GRADE_SHIFT}, a picket sign ({@link #PICKET}),
     * the Union Rep's rosette ({@link #REP}) and a grievance ({@link #GRIEVANCE}). An older game ignores them.
     */
    public static final int GRADE_SHIFT = 5, GRADE_MASK = 3 << GRADE_SHIFT, PICKET = 1 << 7, REP = 1 << 8, GRIEVANCE = 1 << 9;

    /** The grade in a look's bits. */
    public static int grade(int bits) {
        return (bits & GRADE_MASK) >> GRADE_SHIFT;
    }

    /** A name typed on a screen: {@link #RENAME} a worker (its entity id), or {@link #ORDER} name the held Clipboard's order. */
    public record Text(int kind, int target, String text) implements CustomPacketPayload {
        public static final Type<Text> TYPE = new Type<>(HeadInAJar.id("worker_text"));
        public static final StreamCodec<FriendlyByteBuf, Text> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Text::kind, ByteBufCodecs.VAR_INT, Text::target, ByteBufCodecs.stringUtf8(96), Text::text, Text::new);

        @Override
        public Type<Text> type() {
            return TYPE;
        }
    }

    public static final int RENAME = 0, ORDER = 1;

    /** An attack in the air with the Whistle: the entity under the crosshair (up to 32 blocks), as the client saw it. */
    public record Point(int entity) implements CustomPacketPayload {
        public static final Type<Point> TYPE = new Type<>(HeadInAJar.id("whistle_point"));
        public static final StreamCodec<FriendlyByteBuf, Point> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Point::entity, Point::new);

        @Override
        public Type<Point> type() {
            return TYPE;
        }
    }

    /** Registers them on an optional registrar ({@code registrar.optional()}). */
    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(Say.TYPE, Say.CODEC, (msg, ctx) -> com.coolfreeze23.headinajar.client.staff.WorkerLines.say(msg));
        registrar.playToClient(Look.TYPE, Look.CODEC, (msg, ctx) -> com.coolfreeze23.headinajar.client.staff.WorkerLines.look(msg));
        registrar.playToServer(Text.TYPE, Text.CODEC, (msg, ctx) -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (msg.kind() == RENAME) Shifts.rename(player, msg.target(), msg.text());
            else if (msg.kind() == ORDER) com.coolfreeze23.headinajar.menu.ClipboardMenu.name(player, msg.text());
        });
        registrar.playToServer(Point.TYPE, Point.CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer player) Detail.point(player, msg.entity());
        });
        ClockNet.register(registrar);
    }

    /** Whether this player's game hears the workforce's lines. */
    public static boolean hears(ServerPlayer player) {
        return player.connection != null && player.connection.hasChannel(Say.TYPE);
    }
}
