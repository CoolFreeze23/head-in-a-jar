package com.coolfreeze23.headinajar.host;

import com.coolfreeze23.headinajar.HeadInAJar;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.HashMap;
import java.util.Map;

/**
 * Server to client: every host data file. A ridden host is moved by its rider's own client, so that client has to know
 * whether the body flies, climbs, falls slowly or can't jump; and which abilities it has, for the HUD.
 */
public record HostFiles(Map<ResourceLocation, HostDefinition> files) implements CustomPacketPayload {
    public static final Type<HostFiles> TYPE = new Type<>(HeadInAJar.id("host_files"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HostFiles> CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC, ByteBufCodecs.fromCodec(HostDefinition.CODEC)),
            HostFiles::files, HostFiles::new);

    @Override
    public Type<HostFiles> type() {
        return TYPE;
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(TYPE, CODEC, (msg, ctx) -> Hosts.setClientDefinitions(msg.files()));
    }

    public static void send(ServerPlayer player) {
        if (player.connection != null && player.connection.hasChannel(TYPE)) PacketDistributor.sendToPlayer(player, new HostFiles(new HashMap<>(Hosts.definitions())));
    }
}
