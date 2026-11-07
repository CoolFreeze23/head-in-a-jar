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
        OPEN_MENU, ABILITY, THROW, LET_GO
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

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(FormSync.TYPE, FormSync.CODEC, Network::onSync);
        registrar.playToServer(JarAction.TYPE, JarAction.CODEC, Network::onAction);
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
        if (!state.form().isJarred()) return;
        switch (msg.action()) {
            case OPEN_MENU -> FormActions.openMenu(player);
            case ABILITY -> Telekinesis.toggle(player, state);
            case THROW -> Telekinesis.throwHeld(player, state);
            case LET_GO -> FormActions.letGo(player);
        }
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
