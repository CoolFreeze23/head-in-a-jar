package com.coolfreeze23.headinajar.network;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.module.Telekinesis;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class Network {
    /** Server to clients: a player's form and jar. */
    public record FormSync(int entityId, JarState state) implements CustomPacketPayload {
        public static final Type<FormSync> TYPE = new Type<>(HeadInAJar.id("form_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FormSync> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, FormSync::entityId, JarState.SYNC_CODEC, FormSync::state, FormSync::new);

        @Override
        public Type<FormSync> type() {
            return TYPE;
        }
    }

    public enum Action {
        OPEN_MENU, ABILITY, THROW, LET_GO, SWAP, SECONDARY
    }

    /** Client to server: a key the jarred player pressed. */
    public record JarAction(Action action) implements CustomPacketPayload {
        public static final Type<JarAction> TYPE = new Type<>(HeadInAJar.id("jar_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, JarAction> CODEC = StreamCodec.composite(
                ByteBufCodecs.idMapper(i -> Action.values()[i], Action::ordinal), JarAction::action, JarAction::new);

        @Override
        public Type<JarAction> type() {
            return TYPE;
        }
    }

    /** Server to clients: a mob's host state. */
    public record HostSync(int entityId, boolean host, float freshness, float sync, boolean shell) implements CustomPacketPayload {
        public static final Type<HostSync> TYPE = new Type<>(HeadInAJar.id("host_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HostSync> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, HostSync::entityId, ByteBufCodecs.BOOL, HostSync::host, ByteBufCodecs.FLOAT, HostSync::freshness,
                ByteBufCodecs.FLOAT, HostSync::sync, ByteBufCodecs.BOOL, HostSync::shell, HostSync::new);

        @Override
        public Type<HostSync> type() {
            return TYPE;
        }
    }

    /** Server to client: where the neck is on each host creature (from the data files). */
    public record HostNecks(java.util.Map<net.minecraft.resources.ResourceLocation, java.util.List<Float>> necks) implements CustomPacketPayload {
        public static final Type<HostNecks> TYPE = new Type<>(HeadInAJar.id("host_necks"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HostNecks> CODEC = StreamCodec.composite(
                ByteBufCodecs.map(java.util.HashMap::new, net.minecraft.resources.ResourceLocation.STREAM_CODEC, ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list())),
                HostNecks::necks, HostNecks::new);

        @Override
        public Type<HostNecks> type() {
            return TYPE;
        }
    }

    /** Server to client: start the Neural Handshake for this host. Client to server: how many rings hit. */
    public record Handshake(int entityId, int hits) implements CustomPacketPayload {
        public static final Type<Handshake> TYPE = new Type<>(HeadInAJar.id("handshake"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Handshake> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Handshake::entityId, ByteBufCodecs.VAR_INT, Handshake::hits, Handshake::new);

        @Override
        public Type<Handshake> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(FormSync.TYPE, FormSync.CODEC, Network::onSync);
        registrar.playToServer(JarAction.TYPE, JarAction.CODEC, Network::onAction);
        registrar.playToClient(HostSync.TYPE, HostSync.CODEC, (msg, ctx) -> {
            if (ctx.player().level().getEntity(msg.entityId()) instanceof net.minecraft.world.entity.Mob mob) {
                var data = com.coolfreeze23.headinajar.host.Hosts.data(mob);
                data.host = msg.host();
                data.freshness = msg.freshness();
                data.sync = msg.sync();
                data.shell = msg.shell();
            }
        });
        registrar.playToClient(HostNecks.TYPE, HostNecks.CODEC, (msg, ctx) -> {
            java.util.Map<net.minecraft.resources.ResourceLocation, net.minecraft.world.phys.Vec3> necks = new java.util.HashMap<>();
            msg.necks().forEach((id, v) -> necks.put(id, new net.minecraft.world.phys.Vec3(v.get(0), v.get(1), v.get(2))));
            com.coolfreeze23.headinajar.host.Hosts.setClientNecks(necks);
        });
        registrar.playBidirectional(Handshake.TYPE, Handshake.CODEC, Network::onHandshake);
    }

    private static void onSync(FormSync msg, IPayloadContext ctx) {
        Player me = ctx.player();
        if (me.level().getEntity(msg.entityId()) instanceof Player target) {
            JarState state = target.getData(ModRegistry.JAR_STATE);
            boolean changedForm = state.form() != msg.state().form();
            state.copyFrom(msg.state());
            state.takeDirty(true);
            if (changedForm) target.refreshDimensions();
        }
    }

    private static void onAction(JarAction msg, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        JarState state = player.getData(ModRegistry.JAR_STATE);
        if (msg.action() == Action.SWAP) {
            // body to head at any signal (if allowed), head to body only on a good signal
            if (state.form() == com.coolfreeze23.headinajar.form.Form.LINKED) {
                if (com.coolfreeze23.headinajar.form.NeuroLink.manualUnlinkAllowed()) FormActions.linkedToJar(player);
            } else if (state.form() == com.coolfreeze23.headinajar.form.Form.JAR) {
                FormActions.jarToLinked(player);
            }
            return;
        }
        if (state.form() == com.coolfreeze23.headinajar.form.Form.HOSTED && player.getVehicle() instanceof net.minecraft.world.entity.Mob host) {
            if (msg.action() == Action.ABILITY || msg.action() == Action.SECONDARY) {
                com.coolfreeze23.headinajar.host.HostAbilities.use(player, host, msg.action() == Action.SECONDARY);
                return;
            }
        }
        if (!state.form().isJarred()) return;
        switch (msg.action()) {
            case OPEN_MENU -> FormActions.openMenu(player);
            case ABILITY -> Telekinesis.toggle(player, state);
            case THROW -> Telekinesis.throwHeld(player, state);
            case LET_GO -> FormActions.letGo(player);
            default -> {
            }
        }
    }

    private static void onHandshake(Handshake msg, IPayloadContext ctx) {
        if (ctx.flow().isClientbound()) {
            com.coolfreeze23.headinajar.client.ClientHooks.startHandshake(msg.entityId());
            return;
        }
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.getVehicle() instanceof net.minecraft.world.entity.Mob host)
                || host.getId() != msg.entityId() || !com.coolfreeze23.headinajar.host.Hosts.isHost(host)) return;
        var data = com.coolfreeze23.headinajar.host.Hosts.data(host);
        if (data.handshaken) return;
        data.handshaken = true;
        int hits = Math.max(0, Math.min(3, msg.hits()));
        data.sync = Math.min(100, data.sync + (msg.hits() < 0 ? 5 : hits * 3));
        if (msg.hits() >= 0 && hits < 3) {
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.CONFUSION, 40 * (3 - hits)));
        }
        syncHost(host);
    }

    public static void startHandshake(ServerPlayer player, net.minecraft.world.entity.Entity host) {
        send(player, new Handshake(host.getId(), 0));
    }

    public static void syncHost(net.minecraft.world.entity.Entity host) {
        var d = com.coolfreeze23.headinajar.host.Hosts.data(host);
        HostSync msg = new HostSync(host.getId(), d.host, d.freshness, d.sync, d.shell);
        if (host.level() instanceof net.minecraft.server.level.ServerLevel level) {
            for (ServerPlayer viewer : level.getChunkSource().chunkMap.getPlayersWatching(host)) send(viewer, msg);
        }
    }

    public static void syncHostTo(ServerPlayer viewer, net.minecraft.world.entity.Entity host) {
        var d = com.coolfreeze23.headinajar.host.Hosts.data(host);
        send(viewer, new HostSync(host.getId(), d.host, d.freshness, d.sync, d.shell));
    }

    public static void sendNecks(ServerPlayer player) {
        java.util.Map<net.minecraft.resources.ResourceLocation, java.util.List<Float>> necks = new java.util.HashMap<>();
        com.coolfreeze23.headinajar.host.Hosts.necks().forEach((id, v) -> necks.put(id, java.util.List.of((float) v.x, (float) v.y, (float) v.z)));
        send(player, new HostNecks(necks));
    }

    /** Sends a player's state to them and everyone who can see them. */
    public static void sync(ServerPlayer player) {
        FormSync msg = new FormSync(player.getId(), player.getData(ModRegistry.JAR_STATE));
        send(player, msg);
        for (ServerPlayer viewer : player.serverLevel().getChunkSource().chunkMap.getPlayersWatching(player)) {
            if (viewer != player) send(viewer, msg);
        }
    }

    /** Fake and mock players never agree channels with a client; sending to them would throw. */
    private static void send(ServerPlayer to, CustomPacketPayload msg) {
        if (to.connection != null && to.connection.hasChannel(msg.type())) PacketDistributor.sendToPlayer(to, msg);
    }

    /** Sends another player's state to one viewer, when they start tracking them. */
    public static void syncTo(ServerPlayer viewer, Player target) {
        send(viewer, new FormSync(target.getId(), target.getData(ModRegistry.JAR_STATE)));
    }

    private Network() {
    }
}
