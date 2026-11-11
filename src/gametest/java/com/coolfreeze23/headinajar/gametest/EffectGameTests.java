package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.PowerBlockEntities;
import com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity;
import com.coolfreeze23.headinajar.entity.HeadlessBody;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.HeadRegistry;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.fx.Fx;
import com.coolfreeze23.headinajar.item.JarredHeadItem;
import com.coolfreeze23.headinajar.module.ModuleType;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * The effect kit's server side, through the real key handler ({@code Network.action}, the JarAction payload's
 * handler) and the real ticks: the trust rule for lifting players and what they own, one memo per moment (never one
 * per tick or per particle), and holds that end cleanly for each reason.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class EffectGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);

    private static ServerPlayer player(GameTestHelper h, BlockPos at, float yaw, float pitch) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.spawnInvulnerableTime = 0;
        place(h, player, at, yaw, pitch);
        return player;
    }

    private static void place(GameTestHelper h, ServerPlayer p, BlockPos at, float yaw, float pitch) {
        Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(at));
        p.moveTo(v.x, v.y, v.z, yaw, pitch);
        p.setYHeadRot(yaw);
    }

    /** A survival player that is not creative (the helper's mock player always says it is, and creative players can't be lifted). */
    private static ServerPlayer survivor(GameTestHelper h, BlockPos at, float yaw) {
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "test-survivor"), false);
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, p, cookie);
        p.setGameMode(GameType.SURVIVAL);
        place(h, p, at, yaw, 0);
        return p;
    }

    /** A jar with Telekinesis at {@code at}; its body is left standing well out of the way. */
    private static ServerPlayer jar(GameTestHelper h, BlockPos at, float yaw, float pitch) {
        ServerPlayer p = player(h, at.north(6).west(6), yaw, pitch);
        FormActions.detach(p, 3);
        FormActions.state(p).setModule(0, new ItemStack(ModRegistry.module(ModuleType.TELEKINESIS)));
        place(h, p, at, yaw, pitch);
        return p;
    }

    private static void key(ServerPlayer p, Network.Action action) {
        Network.action(p, new Network.JarAction(action));
    }

    private static void tick(ServerPlayer p) {
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(p));
    }

    private static JarState state(ServerPlayer p) {
        return FormActions.state(p);
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    /** The memos of {@code kind} sent since {@code from} (a list index from {@link Fx#sent()} taken earlier). */
    private static List<Fx.Sent> memos(int kind, long since, ServerPlayer holder) {
        return Fx.sent().stream().filter(s -> s.fx().kind() == kind && s.gameTime() >= since && s.fx().entityA() == holder.getId()).toList();
    }

    // --- the trust rule -----------------------------------------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void anotherPlayerIsLiftedOnlyWithTheirTrust(GameTestHelper h) {
        ServerPlayer holder = jar(h, FLOOR.east(3), 0, -15);
        ServerPlayer other = survivor(h, FLOOR.east(3).south(3), 180);
        long t0 = h.getLevel().getGameTime();
        key(holder, Network.Action.ABILITY);
        h.assertTrue(state(holder).heldEntity() < 0, "lifted a player who never trusted the jar's owner");
        h.assertValueEqual(Refusals.lastDelivered(holder), "tk.not_trusted", "the refusal for an untrusting player");
        List<Fx.Sent> fizzles = memos(Fx.TK_FIZZLE, t0, holder);
        h.assertTrue(fizzles.size() == 1 && fizzles.get(0).fx().entityB() == other.getId() && fizzles.get(0).fx().arg() == Fx.WHY_TRUST,
                "the failed grab was not shown once at the player: " + fizzles);
        // the jar's owner on the trust list of the other player (not the other way round) allows it
        HeadRegistry.get(holder.server).setTrust(holder.getUUID(), other.getUUID(), true);
        key(holder, Network.Action.ABILITY);
        h.assertTrue(state(holder).heldEntity() < 0, "the holder trusting the other player was enough; it must be the other way round");
        HeadRegistry.get(holder.server).setTrust(other.getUUID(), holder.getUUID(), true);
        key(holder, Network.Action.ABILITY);
        h.assertTrue(state(holder).heldEntity() == other.getId(), "a trusting player was not lifted: " + Refusals.delivered(holder));
        key(holder, Network.Action.ABILITY);
        h.assertTrue(state(holder).heldEntity() < 0, "still holding after the second press");
        h.assertFalse(other.isNoGravity(), "the lifted player was left with no gravity");
        done(h, holder, other);
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void anIdleBodyNeedsItsOwnersTrust(GameTestHelper h) {
        ServerPlayer holder = jar(h, FLOOR.east(3), 0, -15);
        ServerPlayer owner = player(h, FLOOR.west(4), 0, 0);
        HeadlessBody body = new HeadlessBody(ModRegistry.HEADLESS_BODY.get(), h.getLevel());
        Vec3 at = h.absoluteVec(Vec3.atBottomCenterOf(FLOOR.east(3).south(3)));
        body.moveTo(at.x, at.y, at.z, 180, 0);
        body.setOwner(owner.getUUID(), "Owner");
        h.getLevel().addFreshEntity(body);
        key(holder, Network.Action.ABILITY);
        h.assertTrue(state(holder).heldEntity() < 0, "lifted a stranger's idle body");
        h.assertValueEqual(Refusals.lastDelivered(holder), "tk.not_trusted", "the refusal for a stranger's body");
        HeadRegistry.get(holder.server).setTrust(owner.getUUID(), holder.getUUID(), true);
        key(holder, Network.Action.ABILITY);
        h.assertTrue(state(holder).heldEntity() == body.getId(), "the body of someone who trusts the holder was not lifted: " + Refusals.delivered(holder));
        done(h, holder, owner);
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void creaturesAreLiftedAsBefore(GameTestHelper h) {
        ServerPlayer holder = jar(h, FLOOR.east(3), 0, -15);
        var cow = h.spawn(EntityType.COW, FLOOR.east(3).south(3));
        long t0 = h.getLevel().getGameTime();
        key(holder, Network.Action.ABILITY);
        h.assertTrue(state(holder).heldEntity() == cow.getId(), "a cow was not lifted: " + Refusals.delivered(holder));
        List<Fx.Sent> grabs = memos(Fx.TK_GRAB, t0, holder);
        h.assertTrue(grabs.size() == 1 && grabs.get(0).fx().entityA() == holder.getId() && grabs.get(0).fx().entityB() == cow.getId(),
                "the grab was not one memo naming holder and cow: " + grabs);
        done(h, holder);
    }

    @GameTest(template = "platform", timeoutTicks = 80)
    public static void aHeldCowIsCarriedInTheAir(GameTestHelper h) {
        ServerPlayer holder = jar(h, FLOOR.east(3), 0, 0);
        state(holder).setCharge(100_000);
        var cow = h.spawn(EntityType.COW, FLOOR.east(3).south(3));
        double floor = cow.getY();
        place(h, holder, FLOOR.east(3), 0, -15);
        key(holder, Network.Action.ABILITY);
        h.assertTrue(state(holder).heldEntity() == cow.getId(), "a cow was not lifted");
        place(h, holder, FLOOR.east(3), 0, 0);
        h.onEachTick(() -> tick(holder));
        h.runAfterDelay(40, () -> {
            double up = cow.getY() - floor;
            h.assertTrue(up > 1.2 && up < 2.0, "looking level, the cow's feet are " + up + " above the floor, not about a block and a half");
            h.assertTrue(cow.distanceTo(holder) > 2.5, "the cow is in the jar's face: " + cow.distanceTo(holder));
            h.assertFalse(cow.isNoGravity(), "the hold set the saved gravity flag");
            done(h, holder);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void aJarStandingInItsBodyLiftsWhatItLooksAt(GameTestHelper h) {
        // the head comes off where the body stands, so the jar is inside its own body's box
        ServerPlayer holder = player(h, FLOOR.east(3), 0, -15);
        FormActions.detach(holder, 3);
        FormActions.state(holder).setModule(0, new ItemStack(ModRegistry.module(ModuleType.TELEKINESIS)));
        place(h, holder, FLOOR.east(3), 0, -15);
        var cow = h.spawn(EntityType.COW, FLOOR.east(3).south(3));
        key(holder, Network.Action.ABILITY);
        h.assertTrue(state(holder).heldEntity() == cow.getId(), "the jar lifted something other than the cow it looks at: " + state(holder).heldEntity());
        key(holder, Network.Action.ABILITY);
        // looking down at its own body with nothing else in line, it may still lift it
        cow.discard();
        place(h, holder, FLOOR.east(3), 0, 80);
        key(holder, Network.Action.ABILITY);
        var held = h.getLevel().getEntity(state(holder).heldEntity());
        h.assertTrue(held instanceof HeadlessBody, "looking down at its own body, the jar did not lift it: " + held);
        done(h, holder);
    }

    // --- one memo per moment ------------------------------------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 120)
    public static void aHoldSendsNoMemosWhileItLasts(GameTestHelper h) {
        ServerPlayer holder = jar(h, FLOOR.east(3), 0, -15);
        state(holder).setCharge(100_000);
        var cow = h.spawn(EntityType.COW, FLOOR.east(3).south(3));
        key(holder, Network.Action.ABILITY);
        h.assertTrue(state(holder).heldEntity() == cow.getId(), "a cow was not lifted");
        long t0 = h.getLevel().getGameTime();
        h.onEachTick(() -> tick(holder));
        h.runAfterDelay(40, () -> {
            h.assertTrue(state(holder).heldEntity() == cow.getId(), "the hold ended by itself");
            long during = Fx.sent().stream().filter(s -> s.gameTime() > t0 && s.fx().entityA() == holder.getId()).count();
            h.assertTrue(during == 0, during + " memos were sent during 40 ticks of holding (the hold is drawn from the synced id)");
            key(holder, Network.Action.THROW);
            List<Fx.Sent> throwsSent = memos(Fx.TK_THROW, t0, holder);
            h.assertTrue(throwsSent.size() == 1 && throwsSent.get(0).fx().entityB() == cow.getId() && throwsSent.get(0).fx().b().length() > 1,
                    "the throw was not one memo with the cow and its velocity: " + throwsSent);
            h.assertTrue(cow.getDeltaMovement().length() > 1, "the cow was not thrown");
            h.assertFalse(cow.isNoGravity(), "the thrown cow has no gravity");
            done(h, holder);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void theLaserIsOneMemo(GameTestHelper h) {
        ServerPlayer p = jar(h, FLOOR.east(3), 0, -15);
        state(p).setModule(1, new ItemStack(ModRegistry.module(ModuleType.LACRIMAL_LASER)));
        state(p).setCharge(100_000);
        var zombie = h.spawn(EntityType.ZOMBIE, FLOOR.east(3).south(4));
        long t0 = h.getLevel().getGameTime();
        key(p, Network.Action.SECONDARY);
        List<Fx.Sent> lasers = memos(Fx.LASER, t0, p);
        h.assertTrue(lasers.size() == 1, "the laser sent " + lasers.size() + " memos, not one");
        h.assertTrue(lasers.get(0).fx().arg() == 1 && zombie.getHealth() < zombie.getMaxHealth(), "the laser's memo or its hit is missing: " + lasers);
        done(h, p);
    }

    // --- holds end cleanly ------------------------------------------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void eachEndingOfAHoldIsTold(GameTestHelper h) {
        ServerPlayer holder = jar(h, FLOOR.east(3), 0, -15);
        state(holder).setCharge(100_000);
        var cow = h.spawn(EntityType.COW, FLOOR.east(3).south(3));
        long t0 = h.getLevel().getGameTime();
        // set down on purpose
        key(holder, Network.Action.ABILITY);
        key(holder, Network.Action.ABILITY);
        h.assertTrue(memos(Fx.TK_SET_DOWN, t0, holder).size() == 1, "setting down was not one memo");
        // out of charge
        key(holder, Network.Action.ABILITY);
        h.assertTrue(state(holder).heldEntity() == cow.getId(), "regrab failed");
        state(holder).setCharge(0);
        tick(holder);
        h.assertTrue(state(holder).heldEntity() < 0, "the hold outlived its charge");
        h.assertTrue(memos(Fx.TK_DROP, t0, holder).stream().anyMatch(s -> s.fx().arg() == Fx.DROP_CHARGE), "no out-of-charge drop memo");
        // out of range
        state(holder).setCharge(100_000);
        key(holder, Network.Action.ABILITY);
        h.assertTrue(state(holder).heldEntity() == cow.getId(), "third grab failed");
        place(h, holder, FLOOR.east(3).north(2), 0, -15);
        Vec3 far = holder.position().add(0, 0, -40);
        holder.teleportTo(far.x, far.y, far.z);
        tick(holder);
        h.assertTrue(state(holder).heldEntity() < 0, "the hold outlived its range");
        h.assertTrue(memos(Fx.TK_DROP, t0, holder).stream().anyMatch(s -> s.fx().arg() == Fx.DROP_RANGE), "no out-of-range drop memo");
        h.assertFalse(cow.isNoGravity(), "the cow was left floating");
        // the held thing is gone
        place(h, holder, FLOOR.east(3), 0, -15);
        key(holder, Network.Action.ABILITY);
        h.assertTrue(state(holder).heldEntity() == cow.getId(), "fourth grab failed");
        cow.discard();
        tick(holder);
        h.assertTrue(state(holder).heldEntity() < 0, "still holding a removed cow");
        h.assertTrue(memos(Fx.TK_DROP, t0, holder).stream().anyMatch(s -> s.fx().arg() == Fx.DROP_GONE), "no gone drop memo");
        done(h, holder);
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void graspingAtBedrockFizzlesOutLoud(GameTestHelper h) {
        h.setBlock(FLOOR.south(2), net.minecraft.world.level.block.Blocks.BEDROCK);
        ServerPlayer holder = jar(h, FLOOR, 0, 0);
        long t0 = h.getLevel().getGameTime();
        key(holder, Network.Action.ABILITY);
        h.assertValueEqual(Refusals.lastDelivered(holder), "tk.too_hard", "the refusal for bedrock");
        List<Fx.Sent> fizzles = memos(Fx.TK_FIZZLE, t0, holder);
        h.assertTrue(fizzles.size() == 1 && fizzles.get(0).fx().arg() == Fx.WHY_HARD, "no single hard-block fizzle memo: " + fizzles);
        h.assertTrue(h.getLevel().getBlockState(h.absolutePos(FLOOR.south(2))).is(net.minecraft.world.level.block.Blocks.BEDROCK), "the bedrock moved");
        done(h, holder);
    }

    // --- blocks: state, not particle packets -----------------------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void teslaLinkSyncsItsFlowNotParticles(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.TESLA_NODE.get());
        h.setBlock(FLOOR.east(5), ModRegistry.TESLA_NODE.get());
        var sender = (PowerBlockEntities.TeslaNode) h.getBlockEntity(FLOOR);
        var receiver = (PowerBlockEntities.TeslaNode) h.getBlockEntity(FLOOR.east(5));
        sender.link(receiver.getBlockPos());
        sender.energy().set(10_000);
        long t0 = h.getLevel().getGameTime();
        for (int i = 0; i < 25; i++) PowerBlockEntities.TeslaNode.tick(h.getLevel(), sender.getBlockPos(), sender.getBlockState(), sender);
        h.assertTrue(sender.flowing(), "the sender doesn't say power is flowing");
        var tag = sender.getUpdateTag(h.getLevel().registryAccess());
        h.assertTrue(tag.getLong("Target") == receiver.getBlockPos().asLong() && tag.getBoolean("Flowing"), "the update tag lacks the link or the flow: " + tag);
        Vec3 here = Vec3.atCenterOf(sender.getBlockPos());
        h.assertTrue(Fx.sent().stream().noneMatch(s -> s.gameTime() >= t0 && s.fx().a().distanceTo(here) < 8), "a Tesla link sent memos");
        done(h);
    }

    @GameTest(template = "platform", timeoutTicks = 120)
    public static void guardianLockSyncsOnceAndFiresOnce(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.TURRET_PEDESTAL.get());
        var turret = (TurretPedestalBlockEntity) h.getBlockEntity(FLOOR);
        Mob guardian = h.spawn(EntityType.GUARDIAN, FLOOR.above(3));
        ItemStack head = JarredHeadItem.of(guardian);
        guardian.discard();
        turret.items().setStackInSlot(0, head);
        turret.energy().set(40_000);
        var zombie = h.spawn(EntityType.ZOMBIE, FLOOR.east(5));
        zombie.setNoAi(true);
        long t0 = h.getLevel().getGameTime();
        TurretPedestalBlockEntity.serverTick(h.getLevel(), turret.getBlockPos(), turret.getBlockState(), turret);
        h.assertTrue(turret.lockId() == zombie.getId(), "the lock was not synced: " + turret.lockId());
        var tag = turret.getUpdateTag(h.getLevel().registryAccess());
        h.assertTrue(tag.getInt("Lock") == zombie.getId(), "the update tag lacks the lock");
        for (int i = 0; i < 39; i++) TurretPedestalBlockEntity.serverTick(h.getLevel(), turret.getBlockPos(), turret.getBlockState(), turret);
        long shots = Fx.sent().stream().filter(s -> s.fx().kind() == Fx.TURRET_SHOT && s.gameTime() >= t0 && s.fx().entityB() == zombie.getId()).count();
        h.assertTrue(shots == 1, "40 ticks of lock sent " + shots + " shot memos, not one");
        h.assertTrue(turret.lockId() < 0, "the lock stayed on after the shot");
        h.assertTrue(zombie.getHealth() < zombie.getMaxHealth(), "the shot did no harm");
        done(h);
    }
}
