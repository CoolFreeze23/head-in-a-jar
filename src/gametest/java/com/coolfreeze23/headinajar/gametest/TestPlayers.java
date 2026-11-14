package com.coolfreeze23.headinajar.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mock players with names of their own. The game's mock players all share one name, and a scoreboard team holds names,
 * so a team made of two of them would take in every mock player of every test running alongside. Otherwise these are
 * the game's mock players (survival, on an embedded connection).
 */
final class TestPlayers {
    private static final AtomicInteger NEXT = new AtomicInteger();

    private TestPlayers() {
    }

    /** A survival mock player named {@code prefix} and a number, standing at {@code at} (relative to the test). */
    static ServerPlayer at(GameTestHelper h, String prefix, BlockPos at) {
        String name = prefix + NEXT.incrementAndGet();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name.substring(0, Math.min(16, name.length()))), false);
        ServerPlayer player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return true;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        player.spawnInvulnerableTime = 0;
        Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(at));
        player.moveTo(v.x, v.y, v.z, 0, 0);
        return player;
    }
}
