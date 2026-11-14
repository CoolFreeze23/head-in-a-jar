package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.entity.ShellEntity;
import com.coolfreeze23.headinajar.mecha.Wear;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;

/**
 * A Utility Shell's wear: its blows are remembered by side and saved (a shell never hit saves exactly as before), forgotten
 * when it is whole; they reach a client on an optional channel, so a game without that channel still connects and is
 * never sent it.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class ShellWearGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);

    /** A Lunk facing south (its right is west). */
    private static ShellEntity lunk(GameTestHelper h) {
        ShellEntity s = ModRegistry.LUNK.get().create(h.getLevel());
        Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(FLOOR));
        s.moveTo(v.x, v.y, v.z, 0, 0);
        s.setYBodyRot(0);
        s.yBodyRotO = 0;
        h.getLevel().addFreshEntity(s);
        return s;
    }

    private static void strike(GameTestHelper h, ShellEntity s, BlockPos at) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(at));
        p.moveTo(v.x, v.y, v.z, 0, 0);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, s.position().add(0, 1, 0));
        s.invulnerableTime = 0;
        p.attack(s);
        h.getLevel().getServer().getPlayerList().remove(p);
    }

    /** Blows from its right are remembered as from its right, saved, reloaded, and forgotten when it is whole. */
    @GameTest(template = "platform", timeoutTicks = 40)
    public static void aShellRemembersItsBlowsOverAReload(GameTestHelper h) {
        ShellEntity s = lunk(h);
        h.runAfterDelay(2, () -> {
            CompoundTag fresh = new CompoundTag();
            h.assertTrue(s.save(fresh), "could not save");
            h.assertFalse(fresh.contains("HitBearings"), "a shell never hit saved blows");
            for (int i = 0; i < 3; i++) strike(h, s, FLOOR.west(2));
            int[] n = Wear.counts(s.hitBearings());
            h.assertTrue(n[Wear.RIGHT] == 3 && n[Wear.LEFT] == 0, "not three from the right: " + Arrays.toString(n));
            s.setHealth(s.getMaxHealth() * 0.4f);
            h.assertValueEqual(Arrays.toString(Wear.stages(s.hitBearings(), s.getHealth(), s.getMaxHealth())), "[0, 2, 0, 0]", "dents at two fifths");
            CompoundTag tag = new CompoundTag();
            h.assertTrue(s.save(tag), "could not save");
            Entity back = EntityType.loadEntityRecursive(tag, h.getLevel(), e -> e);
            h.assertTrue(back instanceof ShellEntity again && again.hitBearings() == s.hitBearings(), "the blows did not survive the reload");
            s.setHealth(s.getMaxHealth());
            h.assertValueEqual(s.hitBearings(), 0L, "a whole shell still remembers blows");
            h.succeed();
        });
    }

    /**
     * A game without the shell channel (0.5.1) connects to this one in either direction (the channel is optional and the
     * version unchanged), and a player whose game has not the channel is never sent it (NeoForge would refuse the send
     * with an exception).
     */
    @GameTest(template = "platform", timeoutTicks = 20)
    @SuppressWarnings("unchecked")
    public static void aGameWithoutTheShellChannelStillConnects(GameTestHelper h) throws ReflectiveOperationException {
        var field = net.neoforged.neoforge.network.registration.NetworkRegistry.class.getDeclaredField("PAYLOAD_REGISTRATIONS");
        field.setAccessible(true);
        var byProtocol = (java.util.Map<net.minecraft.network.ConnectionProtocol, java.util.Map<net.minecraft.resources.ResourceLocation,
                net.neoforged.neoforge.network.registration.PayloadRegistration<?>>>) field.get(null);
        java.util.List<net.neoforged.neoforge.network.negotiation.NegotiableNetworkComponent> ours = new java.util.ArrayList<>(), old = new java.util.ArrayList<>();
        boolean optional = false;
        for (var reg : byProtocol.get(net.minecraft.network.ConnectionProtocol.PLAY).values()) {
            if (!reg.id().getNamespace().equals(HeadInAJar.MOD_ID)) continue;
            var c = new net.neoforged.neoforge.network.negotiation.NegotiableNetworkComponent(reg);
            ours.add(c);
            if (reg.id().equals(Network.ShellDents.TYPE.id())) optional = c.optional();
            else old.add(c);
        }
        h.assertTrue(optional, "the shell channel is not optional");
        h.assertValueEqual(Network.VERSION, "5", "the network version");
        for (boolean oldServer : new boolean[]{false, true}) {
            var r = oldServer ? net.neoforged.neoforge.network.negotiation.NetworkComponentNegotiator.negotiate(old, ours)
                    : net.neoforged.neoforge.network.negotiation.NetworkComponentNegotiator.negotiate(ours, old);
            h.assertTrue(r.success(), "a game without the shell channel was refused as " + (oldServer ? "server" : "client") + ": " + r.failureReasons());
        }
        // a mock player's connection has no channel open: seeing a dented shell must not send it anything
        ShellEntity s = lunk(h);
        s.setHitBearings(Wear.push(0, 90));
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        h.assertFalse(p.connection.hasChannel(Network.ShellDents.TYPE), "the mock connection has the channel");
        s.startSeenByPlayer(p);
        s.tick();
        h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }
}
