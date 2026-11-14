package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.entity.HeadlessBody;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.fx.Fx;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * The head and the body, through the paths a click takes ({@code interactOn}, {@code useItemOn}, the body taking
 * damage, a menu opening and closing, a dismount): each moment sends one memo that every watching client draws it
 * from, at the right creature, and the action under it is still instant.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class BodyWaveGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);

    private static ServerPlayer player(GameTestHelper h, BlockPos at) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(at));
        p.moveTo(v.x, v.y, v.z, 0, 0);
        return p;
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    /** The memos of {@code kind} since {@code since} about {@code entity} (as either of its two entities): tests run side by side. */
    private static List<Fx.Sent> memos(int kind, long since, net.minecraft.world.entity.Entity entity) {
        return Fx.sent().stream().filter(s -> s.fx().kind() == kind && s.gameTime() >= since
                && (s.fx().entityA() == entity.getId() || s.fx().entityB() == entity.getId())).toList();
    }

    private static <T extends net.minecraft.world.entity.Mob> T weak(GameTestHelper h, EntityType<T> type, BlockPos at) {
        T mob = h.spawn(type, at);
        mob.setHealth(1);
        mob.setNoAi(true);
        return mob;
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void theSawPopsAHeadWithOneMemo(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.SURGICAL_SAW.get()));
        Cow cow = weak(h, EntityType.COW, FLOOR.east(1));
        long t0 = h.getLevel().getGameTime();
        h.assertTrue(p.interactOn(cow, InteractionHand.MAIN_HAND).consumesAction(), "the saw's click was swallowed");
        h.assertTrue(Hosts.isHost(cow), "the cut is still instant: the cow is a host at once");
        List<Fx.Sent> cuts = memos(Fx.HOST_CUT, t0, cow);
        h.assertTrue(cuts.size() == 1 && cuts.get(0).fx().entityB() == cow.getId() && cuts.get(0).fx().entityA() == p.getId()
                && cuts.get(0).fx().arg() == 0, "one cut memo naming the cutter and the cow, head not kept: " + cuts);
        // the pop, the snip and the ding are played later by the server's own tick: the cow stays a calm host meanwhile
        h.runAfterDelay(14, () -> {
            h.assertTrue(cow.isAlive() && Hosts.isHost(cow), "the cow did not survive its own cork");
            h.assertTrue(memos(Fx.HOST_CUT, t0, cow).size() == 1, "the cut was told more than once");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void aJarInTheOffHandCatchesTheHead(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.SURGICAL_SAW.get()));
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()));
        Cow cow = weak(h, EntityType.COW, FLOOR.east(1));
        long t0 = h.getLevel().getGameTime();
        p.interactOn(cow, InteractionHand.MAIN_HAND);
        List<Fx.Sent> cuts = memos(Fx.HOST_CUT, t0, cow);
        h.assertTrue(cuts.size() == 1 && cuts.get(0).fx().arg() == 1, "the cut memo says the head goes into the jar: " + cuts);
        h.assertTrue(p.getInventory().items.stream().anyMatch(s -> s.is(ModRegistry.JARRED_HEAD.get())), "no jarred head was handed over");
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 80)
    public static void aJarSeatsAndLeavesWithOneMemoEach(GameTestHelper h) {
        ServerPlayer cutter = player(h, FLOOR);
        cutter.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.SURGICAL_SAW.get()));
        Pig pig = weak(h, EntityType.PIG, FLOOR.east(2));
        cutter.interactOn(pig, InteractionHand.MAIN_HAND);
        cutter.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        ServerPlayer jar = player(h, FLOOR.west(2));
        FormActions.detach(jar, 3);
        long t0 = h.getLevel().getGameTime();
        jar.interactOn(pig, InteractionHand.MAIN_HAND);
        h.assertTrue(FormActions.state(jar).form() == Form.HOSTED && jar.getVehicle() == pig, "the jar did not climb on");
        List<Fx.Sent> seats = memos(Fx.HOST_SEAT, t0, pig);
        h.assertTrue(seats.size() == 1 && seats.get(0).fx().entityA() == jar.getId() && seats.get(0).fx().entityB() == pig.getId(),
                "one seat memo naming the jar and the pig: " + seats);
        // getting off the way sneaking does it (the vehicle lets go): heard once, at the pig
        jar.stopRiding();
        List<Fx.Sent> off = memos(Fx.HOST_UNSEAT, t0, pig);
        h.assertTrue(off.size() == 1 && off.get(0).fx().entityB() == pig.getId(), "one unseat memo at the pig: " + off);
        h.runAfterDelay(2, () -> {
            h.assertTrue(FormActions.state(jar).form() == Form.JAR, "the jar is a plain jar again");
            h.assertTrue(memos(Fx.HOST_UNSEAT, t0, pig).size() == 1, "leaving was told twice");
            done(h, cutter, jar);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void embalmingIsSeenOnce(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.SURGICAL_SAW.get()));
        Pig pig = weak(h, EntityType.PIG, FLOOR.east(1));
        p.interactOn(pig, InteractionHand.MAIN_HAND);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.EMBALMING_FLUID.get()));
        long t0 = h.getLevel().getGameTime();
        p.interactOn(pig, InteractionHand.MAIN_HAND);
        h.assertTrue(Hosts.data(pig).embalmedUntil > t0, "not embalmed");
        List<Fx.Sent> m = memos(Fx.HOST_EMBALMED, t0, pig);
        h.assertTrue(m.size() == 1 && m.get(0).fx().entityB() == pig.getId() && m.get(0).fx().arg() == 1, "one embalming memo with its sparkle: " + m);
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void aSyringeDrawIsSeenInTheHand(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.SYRINGE.get()));
        Pig pig = h.spawn(EntityType.PIG, FLOOR.east(1));
        pig.setNoAi(true);
        long t0 = h.getLevel().getGameTime();
        p.interactOn(pig, InteractionHand.MAIN_HAND);
        List<Fx.Sent> m = memos(Fx.TOOL, t0, p);
        h.assertTrue(m.size() == 1 && m.get(0).fx().arg() == Fx.TOOL_SYRINGE && m.get(0).fx().entityA() == p.getId()
                && m.get(0).fx().entityB() == pig.getId(), "one syringe memo naming the user and the pig: " + m);
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void theWrenchRatchetsOnANode(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.SURGICAL_WRENCH.get()));
        BlockPos node = FLOOR.east(2);
        h.setBlock(node, ModRegistry.TESLA_NODE.get());
        BlockPos abs = h.absolutePos(node);
        long t0 = h.getLevel().getGameTime();
        p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        List<Fx.Sent> m = memos(Fx.TOOL, t0, p);
        h.assertTrue(m.size() == 1 && m.get(0).fx().arg() == Fx.TOOL_WRENCH, "one wrench memo for the first node: " + m);
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 80)
    public static void aPlacedHeadShowsItsOwnersSalineChargeAndGlass(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        var state = FormActions.state(p);
        state.setCollar(new ItemStack(ModRegistry.COLLAR_CAM.get()));
        state.setGoggles(new ItemStack(ModRegistry.RELAY_GOGGLES.get()));
        // the swap key puts the head down where the jar stands and wakes the player in their body
        com.coolfreeze23.headinajar.network.Network.action(p, new com.coolfreeze23.headinajar.network.Network.JarAction(com.coolfreeze23.headinajar.network.Network.Action.SWAP));
        h.assertTrue(state.form() == Form.LINKED, "the swap did not happen: " + com.coolfreeze23.headinajar.feedback.Refusals.lastDelivered(p));
        var heads = h.getLevel().getEntitiesOfClass(com.coolfreeze23.headinajar.entity.HeadJarEntity.class, p.getBoundingBox().inflate(32));
        h.assertTrue(heads.size() == 1, "no placed head: " + heads.size());
        var head = heads.get(0);
        state.setSaline(state.maxSaline() / 2);
        state.setCharge(0);
        state.setIntegrity(state.maxIntegrity() / 4);
        h.runAfterDelay(21, () -> {
            int v = head.vitals();
            int saline = Math.round(100f * state.saline() / state.maxSaline()), glass = Math.round(100f * state.integrity() / state.maxIntegrity());
            h.assertTrue((v & 127) == saline && (v >> 7 & 127) == 0 && (v >> 14 & 127) == glass,
                    "the placed head shows saline " + (v & 127) + ", charge " + (v >> 7 & 127) + ", glass " + (v >> 14 & 127));
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 120)
    public static void aCrackedPlacedHeadShowsItsLevelAndLampOnly(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        var state = FormActions.state(p);
        state.setCollar(new ItemStack(ModRegistry.COLLAR_CAM.get()));
        state.setGoggles(new ItemStack(ModRegistry.RELAY_GOGGLES.get()));
        com.coolfreeze23.headinajar.network.Network.action(p, new com.coolfreeze23.headinajar.network.Network.JarAction(com.coolfreeze23.headinajar.network.Network.Action.SWAP));
        h.assertTrue(state.form() == Form.LINKED, "the swap did not happen: " + com.coolfreeze23.headinajar.feedback.Refusals.lastDelivered(p));
        var heads = h.getLevel().getEntitiesOfClass(com.coolfreeze23.headinajar.entity.HeadJarEntity.class, p.getBoundingBox().inflate(32));
        h.assertTrue(heads.size() == 1, "no placed head: " + heads.size());
        var head = heads.get(0);
        // badly cracked, half full, low charge: what a watcher's renderer is given for this head
        state.setSaline(state.maxSaline() / 2);
        state.setCharge(state.chargeCapacity() / 10);
        state.setIntegrity(state.maxIntegrity() / 5);
        h.runAfterDelay(21, () -> {
            var v = com.coolfreeze23.headinajar.client.JarRenderer.Vitals.packed(head.vitals(), head.getId(), 0);
            h.assertTrue(v.cracks() == 3, "the glass stage is " + v.cracks());
            h.assertFalse(com.coolfreeze23.headinajar.client.body.JarLife.drawsCracks(v), "a cracked jar draws marks on its glass");
            h.assertFalse(com.coolfreeze23.headinajar.client.body.JarLife.leaks(v.cracks(), true), "a cracked jar drops saline");
            h.assertTrue(com.coolfreeze23.headinajar.client.body.JarLife.bubbles(v) == 0, "bubbles are drawn");
            h.assertFalse(com.coolfreeze23.headinajar.client.body.JarLife.sloshes(), "the saline sloshes");
            int half = com.coolfreeze23.headinajar.client.body.JarLife.levelPixels(v.saline());
            h.assertTrue(half >= 1 && half <= 6, "half a jar of saline stands at " + half);
            h.assertTrue(com.coolfreeze23.headinajar.client.body.JarLife.lampState(v.charge()) == 1, "a low jar's lamp is not amber");
            // topped up and charged, then drained and flat: the level and the lamp follow
            state.setSaline(state.maxSaline());
            state.setCharge(state.chargeCapacity());
            h.runAfterDelay(21, () -> {
                var full = com.coolfreeze23.headinajar.client.JarRenderer.Vitals.packed(head.vitals(), head.getId(), 0);
                h.assertTrue(com.coolfreeze23.headinajar.client.body.JarLife.levelPixels(full.saline()) == 7, "a full jar is not full");
                h.assertTrue(com.coolfreeze23.headinajar.client.body.JarLife.lampState(full.charge()) == 2, "a charged jar's lamp is not green");
                state.setSaline(0);
                state.setCharge(0);
                h.runAfterDelay(21, () -> {
                    var dry = com.coolfreeze23.headinajar.client.JarRenderer.Vitals.packed(head.vitals(), head.getId(), 0);
                    h.assertTrue(com.coolfreeze23.headinajar.client.body.JarLife.levelPixels(dry.saline()) == 0, "a dry jar shows saline");
                    h.assertTrue(com.coolfreeze23.headinajar.client.body.JarLife.lampState(dry.charge()) == 0, "a flat jar's lamp is lit");
                    done(h, p);
                });
            });
        });
    }

    @GameTest(template = "platform", timeoutTicks = 80)
    public static void aBodyThatDiesLiesDownAndShowsWhenItIsOpened(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        HeadlessBody body = h.getLevel().getEntitiesOfClass(HeadlessBody.class, p.getBoundingBox().inflate(3)).stream()
                .filter(b -> p.getUUID().equals(b.ownerId())).findFirst().orElse(null);
        h.assertTrue(body != null, "no body was left standing");
        long t0 = h.getLevel().getGameTime();
        body.hurt(h.getLevel().damageSources().generic(), 1000);
        h.assertTrue(body.isCorpse() && body.isAlive(), "the body did not become a corpse");
        h.runAfterDelay(3, () -> {
            List<Fx.Sent> m = memos(Fx.CORPSE, t0, body);
            h.assertTrue(m.size() == 1 && m.get(0).fx().entityB() == body.getId(), "one lying-down memo for the body: " + m);
            // its owner, a jar with no Suture Kit, opens its things: the hands unfold; closing folds them again
            p.moveTo(body.getX() + 1, body.getY(), body.getZ());
            body.interact(p, InteractionHand.MAIN_HAND);
            h.assertTrue(body.isOpened(), "opening the corpse's things did not show on it");
            p.closeContainer();
            h.assertTrue(!body.isOpened(), "closing the corpse's things did not fold its hands again");
            done(h, p);
        });
    }
}
