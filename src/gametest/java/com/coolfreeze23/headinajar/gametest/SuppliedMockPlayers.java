package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.supplier.Supplier;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.util.Map;
import java.util.Set;

/**
 * With the supplier loaded, a test's mock player must be able to receive the other mods' play payloads: a mock
 * connection negotiates none, and the supplier sends its own to every player each tick, which would fail every test
 * that makes a mock player. Only in a game where the supplier has delivered, and never for this mod's own payloads, so
 * the old suite runs exactly as before.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID)
public final class SuppliedMockPlayers {
    private static Set<ResourceLocation> others;

    private SuppliedMockPlayers() {
    }

    @SubscribeEvent
    static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) open(player);
    }

    /**
     * Before the player is in the level: the supplier sends to a player as soon as the level starts tracking it, which
     * is before the login event (a mock player placed by a test, or a respawned one).
     */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    static void joined(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer player) open(player);
    }

    private static void open(ServerPlayer player) {
        if (player instanceof net.neoforged.neoforge.common.util.FakePlayer || player.connection == null || Supplier.delivered().isEmpty()) return;
        var connection = player.connection.getConnection();
        if (connection == null || connection.channel() == null) return;
        var setup = ChannelAttributes.getPayloadSetup(connection);
        // a real, negotiated client has its channels; a test's mock connection has no payload setup at all
        if (setup != null && !setup.getChannels(ConnectionProtocol.PLAY).isEmpty()) return;
        ChannelAttributes.getOrCreateAdHocChannels(connection).addAll(others());
    }

    @SuppressWarnings("unchecked")
    private static Set<ResourceLocation> others() {
        if (others != null) return others;
        try {
            var field = NetworkRegistry.class.getDeclaredField("PAYLOAD_REGISTRATIONS");
            field.setAccessible(true);
            var all = (Map<ConnectionProtocol, Map<ResourceLocation, ?>>) field.get(null);
            others = all.get(ConnectionProtocol.PLAY).keySet().stream()
                    .filter(id -> !id.getNamespace().equals(HeadInAJar.MOD_ID)).collect(java.util.stream.Collectors.toUnmodifiableSet());
        } catch (ReflectiveOperationException e) {
            HeadInAJar.LOGGER.warn("Mock players: cannot read the payload registrations: {}", e.toString());
            others = Set.of();
        }
        return others;
    }
}
