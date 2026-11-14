package com.coolfreeze23.headinajar.mecha;

import com.coolfreeze23.headinajar.HeadInAJar;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The walking mecha's own messages: the drive key held and let go in the cockpit (the Pogo Protocol, Heavy Lifting), and the Golden
 * Parachute's canopy over an ejected jar (opened at the top of the jar's flight, folded when it lands), which every
 * player who can see the jar is told, including one who arrives while it falls.
 */
public final class HopperNet {
    /** Client to server: the drive key went down (true) or came up. */
    public record Pogo(boolean held) implements CustomPacketPayload {
        public static final Type<Pogo> TYPE = new Type<>(HeadInAJar.id("pogo"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Pogo> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, Pogo::held, Pogo::new);

        @Override
        public Type<Pogo> type() {
            return TYPE;
        }
    }

    /**
     * Server to clients: this player's parachute changed state ({@link #FOLDED} on land, {@link #FIRED}, {@link #OPEN},
     * {@link #TANGLED}, {@link #SUNK} folded in water), and how many ticks ago the jar was fired (a late arrival starts
     * the deploy at its age), and the rate the deploy plays at (faster for a short drop).
     */
    public record Chute(int entityId, int state, int age, float rate) implements CustomPacketPayload {
        public static final Type<Chute> TYPE = new Type<>(HeadInAJar.id("chute"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Chute> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Chute::entityId,
                ByteBufCodecs.VAR_INT, Chute::state, ByteBufCodecs.VAR_INT, Chute::age, ByteBufCodecs.FLOAT, Chute::rate, Chute::new);

        @Override
        public Type<Chute> type() {
            return TYPE;
        }
    }

    /** Server to the pilot's client: push the mecha it drives (its client moves it). */
    public record Push(int entityId, double x, double y, double z) implements CustomPacketPayload {
        public static final Type<Push> TYPE = new Type<>(HeadInAJar.id("mecha_push"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Push> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Push::entityId,
                ByteBufCodecs.DOUBLE, Push::x, ByteBufCodecs.DOUBLE, Push::y, ByteBufCodecs.DOUBLE, Push::z, Push::new);

        @Override
        public Type<Push> type() {
            return TYPE;
        }
    }

    /**
     * The drive key went down or up in an animated frame's cockpit (the {@code pogo} payload's handler): the frame's own
     * verb answers it: the Hopper's Pogo Protocol, the Bulwark's Heavy Lifting, the Colossus's Siege, the Executive's jingle.
     */
    public static void drive(ServerPlayer player, boolean held) {
        if (!(player.getVehicle() instanceof com.coolfreeze23.headinajar.entity.MechaEntity m)) return;
        MechaRig rig = MechaRig.of(m.frame());
        if (rig == null) return;
        switch (rig.verb) {
            case LIFT -> Bulwark.input(player, held);
            case SIEGE -> Siege.input(player, held);
            case JINGLE -> Executive.input(player, held);
            case RUSH -> Wheelhouse.input(player, held);
            case GRAFT -> Chimera.input(player, held);
            case DRAGLINE -> Skitter.input(player, held);
            case TALON -> Kite.input(player, held);
            default -> Hopper.input(player, held);
        }
    }

    /** Tells the pilot's client to push the mecha it drives; false if that client can't be told (a test player). */
    public static boolean push(ServerPlayer pilot, net.minecraft.world.entity.Entity mecha, net.minecraft.world.phys.Vec3 v) {
        if (pilot.connection == null || !pilot.connection.hasChannel(Push.TYPE)) return false;
        PacketDistributor.sendToPlayer(pilot, new Push(mecha.getId(), v.x, v.y, v.z));
        return true;
    }

    /** The player's persistent flag: 1 fired clear and rising, 2 under the open canopy, 3 out but tangled. */
    public static final String CHUTE = "headinajar_chute";
    /** Parachute states as sent to clients (the flag's values, and the two ways of folding). */
    public static final int FOLDED = 0, FIRED = 1, OPEN = 2, TANGLED = 3, SUNK = 4;
    /** How long a canopy may stay open before it folds anyway (ticks). */
    static final int MAX_OPEN = 600;
    /** The deploy clip's snap, when the canopy opens (ticks after the jar is fired). */
    public static final int SNAP = 20;
    /** The tick after the hand-over the room overhead is judged at, and the air it needs to open (blocks). */
    public static final int CHECK = 5;
    public static final double ROOM = 3;
    /** The canopy is open at least this long before the jar touches down (ticks), the deploy playing at most this fast. */
    public static final int OPEN_BEFORE = 10;
    public static final float FASTEST = 2;

    private HopperNet() {
    }

    @EventBusSubscriber(modid = HeadInAJar.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void registerPayloads(RegisterPayloadHandlersEvent event) {
            var registrar = event.registrar("1");
            registrar.playToServer(Pogo.TYPE, Pogo.CODEC, (msg, ctx) -> {
                if (ctx.player() instanceof ServerPlayer player) drive(player, msg.held());
            });
            registrar.playToClient(Push.TYPE, Push.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
                // only the mecha this client itself drives
                var vehicle = ctx.player().getVehicle();
                if (vehicle != null && vehicle.getId() == msg.entityId() && vehicle.isControlledByLocalInstance()) {
                    vehicle.setDeltaMovement(msg.x(), msg.y(), msg.z());
                    vehicle.hasImpulse = true;
                }
            }));
            registrar.playToClient(Chute.TYPE, Chute.CODEC,
                    (msg, ctx) -> ctx.enqueueWork(() -> com.coolfreeze23.headinajar.client.Parachute.set(msg.entityId(), msg.state(), msg.age(), msg.rate())));
        }

        private Registration() {
        }
    }

    /**
     * The jar was fired clear of a dying mecha: every client that sees it starts the deploy now, and the canopy snaps
     * open on the deploy's tick 20 (or tangles, without room overhead).
     */
    public static void fired(ServerPlayer player) {
        fired(player, 1);
    }

    /** Fired with the deploy at this rate (1 as drawn, up to {@link #FASTEST}); 0 or less: no time to open, tangled. */
    static void fired(ServerPlayer player, float rate) {
        var data = player.getPersistentData();
        data.putInt(CHUTE, rate > 0 ? FIRED : TANGLED);
        data.putLong(CHUTE + "_at", player.level().getGameTime());
        data.putFloat(CHUTE + "_rate", rate > 0 ? rate : FASTEST);
        send(player, rate > 0 ? FIRED : TANGLED, 0);
        if (rate <= 0) player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.headinajar.parachute_short"), true);
    }

    /**
     * How fast the deploy must play for a jar thrown from where it is now with this velocity: as drawn (1) when it has a
     * second and a half of air or more, faster (up to twice) so the canopy is open {@link #OPEN_BEFORE} ticks before it
     * touches down, or 0 when even that is too late. The flight is worked out as the game moves a player: the throw
     * against gravity, then Slow Falling's gentle sink, stopped by a block overhead and ended by the ground or water.
     */
    public static float deployRate(Player player, net.minecraft.world.phys.Vec3 v) {
        int touch = ticksToLand(player, v);
        int room = touch - OPEN_BEFORE;
        if (room >= SNAP) return 1;
        if (room * FASTEST < SNAP) return 0;
        return SNAP / (float) room;
    }

    /** Ticks until a jar thrown with this velocity from here touches ground or water (at most 600). */
    public static int ticksToLand(Player player, net.minecraft.world.phys.Vec3 v) {
        var level = player.level();
        var box = player.getBoundingBox();
        double vx = v.x, vy = v.y, vz = v.z;
        for (int t = 1; t < 600; t++) {
            var moved = box.move(vx, vy, vz);
            // blocks only (the machine it left is no floor), and none it starts inside
            if (!level.noBlockCollision(player, moved) && level.noBlockCollision(player, box)) {
                if (vy > 0) {
                    // a roof: the climb stops there
                    vy = 0;
                    moved = box.move(vx, 0, vz);
                    if (!level.noBlockCollision(player, moved)) moved = box;
                } else {
                    return t;
                }
            }
            box = moved;
            if (level.containsAnyLiquid(box)) return t;
            vy = (vy - (vy <= 0 ? 0.01 : 0.08)) * 0.98;
            vx *= 0.91;
            vz *= 0.91;
        }
        return 600;
    }

    public static boolean open(Player player) {
        return player.getPersistentData().getInt(CHUTE) == 2;
    }

    @EventBusSubscriber(modid = HeadInAJar.MOD_ID)
    public static final class Events {
        @SubscribeEvent
        public static void tick(PlayerTickEvent.Post event) {
            if (event.getEntity() instanceof ServerPlayer player) tickChute(player);
        }

        /**
         * Each tick of a player fired clear: judged for room overhead just after the hand-over (under three blocks of
         * air the canopy tangles, and says so), snapped open on the deploy's tick 20, held up by renewed Slow Falling for
         * as long as it is out (the effect alone runs out before a long fall does), and folded on landing, in water, on
         * boarding anything, on death, or after {@link #MAX_OPEN} ticks.
         */
        public static void tickChute(ServerPlayer player) {
            var data = player.getPersistentData();
            int state = data.getInt(CHUTE);
            if (state == 0) return;
            long now = player.level().getGameTime();
            long age = now - data.getLong(CHUTE + "_at");
            // once a tick, however many callers
            if (data.contains(CHUTE + "_tick") && data.getLong(CHUTE + "_tick") == now) return;
            data.putLong(CHUTE + "_tick", now);
            if (age >= 1 && data.contains(CHUTE + "_vy")) {
                player.setDeltaMovement(data.getDouble(CHUTE + "_vx"), data.getDouble(CHUTE + "_vy"), data.getDouble(CHUTE + "_vz"));
                player.hurtMarked = true;
                data.remove(CHUTE + "_vx");
                data.remove(CHUTE + "_vy");
                data.remove(CHUTE + "_vz");
                return;
            }
            boolean down = player.onGround() || player.isInWater() || player.isPassenger() || !player.isAlive();
            if (down && age > 3 || age > MAX_OPEN + SNAP) {
                boolean water = player.isInWater();
                data.remove(CHUTE);
                data.remove(CHUTE + "_at");
                data.remove(CHUTE + "_tick");
                data.remove(CHUTE + "_rate");
                data.remove(CHUTE + "_vx");
                data.remove(CHUTE + "_vy");
                data.remove(CHUTE + "_vz");
                send(player, water ? SUNK : FOLDED, (int) age);
                sound(player, "parachute_fold");
                return;
            }
            // the cheap insurance, kept topped up while the canopy is out
            var slow = player.getEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING);
            if (slow == null || slow.getDuration() < 20) {
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 60, 0, false, false, true));
            }
            float rate = data.contains(CHUTE + "_rate") ? data.getFloat(CHUTE + "_rate") : 1;
            if (state == FIRED && age == CHECK && !room(player)) {
                data.putInt(CHUTE, TANGLED);
                send(player, TANGLED, (int) age);
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.headinajar.parachute_tangled"), true);
            } else if (state == FIRED && age * rate >= SNAP) {
                data.putInt(CHUTE, OPEN);
                send(player, OPEN, (int) age);
                sound(player, "parachute_pop");
            }
        }

        /** Someone who starts seeing a falling jar sees its parachute as it is, at its age. */
        @SubscribeEvent
        public static void tracking(PlayerEvent.StartTracking event) {
            if (event.getTarget() instanceof Player target && state(target) != 0 && event.getEntity() instanceof ServerPlayer viewer
                    && viewer.connection != null && viewer.connection.hasChannel(Chute.TYPE)) {
                long age = target.level().getGameTime() - target.getPersistentData().getLong(CHUTE + "_at");
                float rate = target.getPersistentData().contains(CHUTE + "_rate") ? target.getPersistentData().getFloat(CHUTE + "_rate") : 1;
                PacketDistributor.sendToPlayer(viewer, new Chute(target.getId(), state(target), (int) age, rate));
            }
        }

        private Events() {
        }
    }

    /** The throw to give the jar once more on the tick after the eject (its client has left the seat by then). */
    public static void throwAgain(ServerPlayer player, net.minecraft.world.phys.Vec3 v) {
        var data = player.getPersistentData();
        data.putDouble(CHUTE + "_vx", v.x);
        data.putDouble(CHUTE + "_vy", v.y);
        data.putDouble(CHUTE + "_vz", v.z);
    }

    /** Three blocks of air over the jar: room for the canopy to open. */
    public static boolean room(Player player) {
        var box = player.getBoundingBox();
        return player.level().noBlockCollision(player, new net.minecraft.world.phys.AABB(box.minX, box.maxY, box.minZ, box.maxX, box.maxY + ROOM, box.maxZ));
    }

    /** The parachute's state ({@link #FIRED}, {@link #OPEN}, {@link #TANGLED}), or 0. */
    public static int state(Player player) {
        return player.getPersistentData().getInt(CHUTE);
    }

    private static void send(ServerPlayer player, int state, int age) {
        if (player.connection == null) return;
        var data = player.getPersistentData();
        Chute msg = new Chute(player.getId(), state, age, data.contains(CHUTE + "_rate") ? data.getFloat(CHUTE + "_rate") : 1);
        if (player.connection.hasChannel(Chute.TYPE)) PacketDistributor.sendToPlayer(player, msg);
        for (ServerPlayer viewer : player.serverLevel().getChunkSource().chunkMap.getPlayersWatching(player)) {
            if (viewer != player && viewer.connection != null && viewer.connection.hasChannel(Chute.TYPE)) PacketDistributor.sendToPlayer(viewer, msg);
        }
    }

    private static void sound(ServerPlayer player, String name) {
        var sound = HopperSounds.get(name);
        if (sound != null) player.level().playSound(null, player.getX(), player.getY() + 1, player.getZ(), sound,
                net.minecraft.sounds.SoundSource.PLAYERS, 1, HopperSounds.pitch(name, 1, player.getRandom()));
    }
}
