package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.entity.ShellEntity;
import com.coolfreeze23.headinajar.entity.SpiderWalkerAnimation;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.host.HostControl;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.MechaModuleItem;
import com.coolfreeze23.headinajar.mecha.HopperAnimation;
import com.coolfreeze23.headinajar.mecha.HopperNet;
import com.coolfreeze23.headinajar.mecha.HopperSockets;
import com.coolfreeze23.headinajar.mecha.HopperSounds;
import com.coolfreeze23.headinajar.mecha.HopperView;
import com.coolfreeze23.headinajar.mecha.Legs;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.MechaLogic;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.mecha.MechaRig;
import com.coolfreeze23.headinajar.mecha.Skitter;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The multi-leg skeleton: the Skitter (the Dragline on the drive key, pressed and held through the drive payload's
 * handler; its clips from what a client measures, a wall climbed by real blocks) and the Spider Walker (its web through
 * the ability key's handler, landing with the spit; its dud; getting on and off by the jar's own paths). Old saves of both
 * load.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class MultiLegGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 4);

    private static ServerPlayer player(GameTestHelper h, BlockPos at) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.spawnInvulnerableTime = 0;
        Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(at));
        player.moveTo(v.x, v.y, v.z, 0, 0);
        return player;
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    private static ItemStack module(MechaModule m) {
        ItemStack stack = new ItemStack(ModRegistry.mechaModule(m));
        MechaModuleItem.setSlider(stack, 50);
        return stack;
    }

    /** A Skitter with these modules, owned by {@code owner}, battery full, facing south (+z). */
    private static MechaEntity skitter(GameTestHelper h, BlockPos at, UUID owner, MechaModule... mods) {
        MechaEntity m = h.spawn(ModRegistry.mechaType(MechaFrame.SKITTER), at);
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < m.frame().totalSlots(); i++) slots.add(ItemStack.EMPTY);
        for (MechaModule mod : mods) slots.set(m.frame().firstIndex(mod.slot), module(mod));
        m.setModules(slots);
        m.setOwner(owner);
        m.setBattery(m.batteryCapacity());
        face(m);
        return m;
    }

    private static void face(net.minecraft.world.entity.LivingEntity m) {
        m.setYRot(0);
        m.setYBodyRot(0);
        m.yBodyRotO = 0;
    }

    /** The player, as a jar, climbs in by a real click. */
    private static void board(GameTestHelper h, ServerPlayer p, net.minecraft.world.entity.Mob m, Form form) {
        if (FormActions.state(p).form() == Form.BODY) FormActions.detach(p, 3);
        p.interactOn(m, InteractionHand.MAIN_HAND);
        h.assertTrue(p.getVehicle() == m && FormActions.state(p).form() == form, "the click did not board: " + FormActions.state(p).form());
        m.setOnGround(true);
        p.setYRot(0);
        p.setXRot(0);
        face(m);
    }

    /** The pilot looks at the middle of a block. */
    private static void aim(GameTestHelper h, ServerPlayer p, BlockPos at) {
        p.lookAt(EntityAnchorArgument.Anchor.EYES, h.absoluteVec(Vec3.atCenterOf(at)));
    }

    private static void post(GameTestHelper h, BlockPos at, int high) {
        for (int i = 0; i < high; i++) h.setBlock(at.above(i), Blocks.STONE);
    }

    private static long now(GameTestHelper h) {
        return h.getLevel().getGameTime();
    }

    private static String clip(HopperAnimation.Choice c) {
        return c == null ? "none" : c.clip().name();
    }

    private static String clip(SpiderWalkerAnimation.Choice c) {
        return c == null ? "none" : c.clip().name();
    }

    private static long count(ServerPlayer p, String reason) {
        return Refusals.delivered(p).stream().filter(reason::equals).count();
    }

    private static int kind(MechaEntity m) {
        return m.lineAt() < 0 ? -1 : (int) (m.lineAt() & 3);
    }

    @GameTest(template = "platform", timeoutTicks = 160)
    public static void theDraglineFiresReelsHangsAndCuts(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(3));
        MechaEntity m = skitter(h, FLOOR, p.getUUID());
        board(h, p, m, Form.PILOT);
        BlockPos target = FLOOR.south(6);
        post(h, target, 4);
        aim(h, p, target.above());
        int battery = m.battery();
        HopperNet.drive(p, true);
        long fired = now(h);
        h.assertTrue(kind(m) == Skitter.FIRED && m.lineAt() / 4 == fired, "the press did not fire the line: " + m.lineAt() + " " + Refusals.delivered(p));
        h.assertTrue(battery - m.battery() == Skitter.FIRE_FE, "the line cost " + (battery - m.battery()) + ", not 1% of the battery");
        h.assertTrue(m.anchor().distanceTo(h.absoluteVec(Vec3.atCenterOf(target.above()))) < 0.9, "the anchor is not on the post: " + m.anchor());
        h.assertTrue(clip(HopperAnimation.main(m, m.view, fired + 3)).equals("dragline_fire"), "no fire clip");
        h.assertFalse(Skitter.out(m), "the line is out before the gun fires it (tick 9)");
        HopperNet.drive(p, false);
        // a press while the gun is in its clip says so in text and changes nothing
        HopperNet.drive(p, true);
        HopperNet.drive(p, false);
        h.assertTrue(count(p, "mecha.dragline.busy") == 1 && m.lineAt() / 4 == fired, "a press mid-fire was not refused once");
        // once the gun has fired: on the ground the line trails; pressed and held, the reel winds in
        int base = Skitter.FIRE_TICKS + 1;
        h.runAfterDelay(base, () -> {
            h.assertTrue(Skitter.out(m) && m.lineLength() == -1 && m.lineMode() == Skitter.SLACK, "on the ground the line does not trail: "
                    + m.lineLength() + " " + m.lineMode());
            HopperNet.drive(p, true);
        });
        float[] reach = new float[1];
        h.runAfterDelay(base + Skitter.TAP + 3, () -> {
            reach[0] = (float) Skitter.hangPoint(m).distanceTo(m.anchor());
            h.assertTrue(m.lineMode() == Skitter.REEL && m.lineLength() > 0 && m.lineLength() < reach[0] - 0.3,
                    "the held key does not reel in: " + m.lineMode() + " " + m.lineLength() + " of " + reach[0]);
            h.assertFalse(clip(HopperAnimation.main(m, m.view, now(h))).startsWith("dragline"), "reeling on the ground plays an airborne clip");
            HopperNet.drive(p, false);
            h.assertTrue(Skitter.out(m), "letting go of a held reel cut the line");
            // off the ground (over an edge, clear of anything a pad could stand on): the brake holds the line and it swings
            m.setPos(m.getX(), m.getY() + 2, m.getZ());
            m.setOnGround(false);
        });
        h.runAfterDelay(base + Skitter.TAP + 4, () -> {
            // dragged off the ground but with a pad in reach of a surface, it keeps its ground stance (no airborne clip)
            m.setPos(m.getX(), m.getY() - 1.6, m.getZ());
            h.assertTrue(Legs.canStand(m) && !clip(HopperAnimation.main(m, m.view, now(h))).startsWith("dragline"), "a machine over the ground hangs");
            m.setPos(m.getX(), m.getY() + 1.6, m.getZ());
        });
        h.runAfterDelay(base + Skitter.TAP + 5, () -> {
            h.assertTrue(m.lineMode() == Skitter.HANG && m.lineLength() > 0, "hanging, the brake does not hold: " + m.lineMode());
            h.assertTrue(clip(HopperAnimation.main(m, m.view, now(h))).equals("dragline_swing"), "no swing clip while hanging");
            h.assertTrue(m.fallDistance == 0, "a fall builds up while the line holds it");
            reach[0] = m.lineLength();
            p.setJumping(true);
        });
        h.runAfterDelay(base + Skitter.TAP + 9, () -> {
            h.assertTrue(m.lineMode() == Skitter.RAPPEL && m.lineLength() > reach[0] + 0.3, "the jump key does not pay out: " + m.lineLength());
            h.assertTrue(clip(HopperAnimation.main(m, m.view, now(h))).equals("dragline_rappel"), "no rappel clip");
            p.setJumping(false);
            // a tap cuts it, on the cut clip's tick 4
            HopperNet.drive(p, true);
            HopperNet.drive(p, false);
            h.assertTrue(kind(m) == Skitter.CUT && m.lineAt() / 4 == now(h), "the tap did not cut");
            h.assertTrue(clip(HopperAnimation.main(m, m.view, now(h) + 2)).equals("dragline_cut"), "no cut clip");
        });
        h.runAfterDelay(base + Skitter.TAP + 9 + Skitter.CUT_AT + 2, () -> {
            h.assertFalse(Skitter.out(m), "the line is still out after the cut");
            m.setOnGround(true);
        });
        int again = base + Skitter.TAP + 9 + Skitter.CUT_TICKS + 1;
        h.runAfterDelay(again, () -> {
            // held from the firing press on, the reel winds in as soon as the line is out
            aim(h, p, target.above());
            HopperNet.drive(p, true);
            h.assertTrue(kind(m) == Skitter.FIRED, "the second press did not fire: " + Refusals.delivered(p));
        });
        h.runAfterDelay(again + Skitter.LINE_OUT + 2, () -> {
            h.assertTrue(Skitter.out(m) && m.lineMode() == Skitter.REEL, "the firing press held on does not reel: " + m.lineMode());
            HopperNet.drive(p, false);
            h.assertTrue(Skitter.out(m), "letting go after a held fire cut the line");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void everyRefusedDraglineSaysWhy(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(3));
        MechaEntity m = skitter(h, FLOOR, p.getUUID());
        board(h, p, m, Form.PILOT);
        // nothing to fix to: up at the sky (the test's invisible ceiling takes no line)
        p.setXRot(-90);
        HopperNet.drive(p, true);
        HopperNet.drive(p, false);
        h.assertTrue(kind(m) == Skitter.REFUSED && count(p, "mecha.dragline.nothing") == 1, "no refusal for nothing in reach: " + Refusals.delivered(p));
        h.assertTrue(clip(HopperAnimation.main(m, m.view, now(h) + 8)).equals("dragline_refused"), "no refusal clip");
        h.assertFalse(Skitter.out(m), "a refused line is out");
        BlockPos target = FLOOR.south(5);
        post(h, target, 3);
        h.runAfterDelay(2, () -> {
            aim(h, p, target.above());
            m.setBattery(100);
            HopperNet.drive(p, true);
            HopperNet.drive(p, false);
            h.assertTrue(count(p, "mecha.dragline.no_power") == 1 && kind(m) == Skitter.REFUSED, "no refusal on a flat battery");
            m.setBattery(m.batteryCapacity());
            m.setVent(40);
        });
        h.runAfterDelay(4, () -> {
            HopperNet.drive(p, true);
            HopperNet.drive(p, false);
            h.assertTrue(count(p, "mecha.dragline.venting") == 1 && m.lineAt() / 4 == now(h), "no refusal while venting");
            m.setVent(0);
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void theLineHoldsTheMachineAndLetsGoOnEveryWayOut(GameTestHelper h) {
        // the line's hold: a hang point past the line's length is drawn back onto it, the speed outward taken away
        Vec3[] held = Skitter.constrain(new Vec3(0, -3, 0), new Vec3(0.3, -0.5, 0), Vec3.ZERO, 2);
        h.assertTrue(held != null && held[0].distanceTo(new Vec3(0, -2, 0)) < 1e-6 && held[1].distanceTo(new Vec3(0.3, 0, 0)) < 1e-6,
                "the line does not hold a point on its length");
        h.assertTrue(Skitter.constrain(new Vec3(0, -1, 0), Vec3.ZERO, Vec3.ZERO, 2) == null, "a slack line pulls");
        ServerPlayer p = player(h, FLOOR.west(3));
        MechaEntity m = skitter(h, FLOOR, p.getUUID());
        board(h, p, m, Form.PILOT);
        // the machine's own move (MechaEntity.travel, which its pilot's client runs): hanging 2 blocks under an anchor
        Vec3 hang = Skitter.hangPoint(m);
        m.setAnchor(hang.add(0, 2, 0));
        m.setLineLength(1.5f);
        m.setLineMode(Skitter.HANG);
        m.setDeltaMovement(0.2, -0.3, 0);
        m.setOnGround(false);
        Skitter.hold(m);
        double d = Skitter.hangPoint(m).distanceTo(m.anchor());
        h.assertTrue(Math.abs(d - 1.5) < 0.05, "the hang point is " + d + " from the anchor on a 1.5 line");
        h.assertTrue(m.getDeltaMovement().y > -1e-6 && Math.abs(m.getDeltaMovement().x - 0.2) < 1e-6, "the swing lost its speed across the line: " + m.getDeltaMovement());
        m.setLineLength(0);
        m.setLineMode(Skitter.SLACK);
        m.setOnGround(true);
        // fired at a post, then the post goes: the line is lost
        BlockPos target = FLOOR.south(5);
        post(h, target, 3);
        aim(h, p, target.above());
        HopperNet.drive(p, true);
        HopperNet.drive(p, false);
        h.runAfterDelay(Skitter.LINE_OUT + 2, () -> {
            h.assertTrue(Skitter.out(m), "the line did not go out");
            for (int i = 0; i < 3; i++) h.setBlock(target.above(i), Blocks.AIR);
        });
        h.runAfterDelay(Skitter.LINE_OUT + 4, () -> {
            h.assertFalse(Skitter.out(m), "the line held on to a block that is gone");
        });
        h.runAfterDelay(Skitter.FIRE_TICKS + 1, () -> {
            post(h, target, 3);
            aim(h, p, target.above());
            HopperNet.drive(p, true);
            HopperNet.drive(p, false);
        });
        h.runAfterDelay(2 * Skitter.FIRE_TICKS + 8, () -> {
            h.assertTrue(Skitter.out(m), "the second line did not go out");
            // the pilot gets out: the line is let go
            Network.action(p, new Network.JarAction(Network.Action.EJECT));
            h.assertTrue(p.getVehicle() == null && !Skitter.out(m), "the line outlived its pilot");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void skitterClipsFollowTheStateAndTheWall(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(3));
        MechaEntity m = skitter(h, FLOOR, p.getUUID(), MechaModule.MANIPULATOR_HAND);
        board(h, p, m, Form.PILOT);
        long t = now(h) + 100;
        HopperView v = new HopperView();
        String idle = clip(HopperAnimation.main(m, v, t));
        h.assertTrue(idle.equals("idle") || idle.equals("idle_tic"), "not idle: " + idle);
        v.speed = 0.25;
        h.assertTrue(clip(HopperAnimation.main(m, v, t)).equals("walk"), "walking does not trot");
        v.forward = -0.1;
        h.assertTrue(clip(HopperAnimation.main(m, v, t)).equals("walk_back"), "backing does not play walk_back");
        v.forward = 0;
        m.setBattery(0);
        h.assertTrue(clip(HopperAnimation.main(m, v, t)).equals("limp"), "a flat battery does not limp");
        m.setBattery(m.batteryCapacity());
        v.speed = 0;
        v.turn = 8;
        h.assertTrue(clip(HopperAnimation.main(m, v, t)).equals("turn_r"), "turning on the spot does not play turn_r");
        // the trot's playback: measured speed over 0.2592 blocks a tick (eight ticks a cycle)
        HopperView w = new HopperView();
        w.speed = MechaRig.SKITTER.walkDesign;
        m.xo = m.getX();
        m.zo = m.getZ() - MechaRig.SKITTER.walkDesign;
        w.update(m, 0);
        w.update(m, 4);
        w.update(m, 8);
        h.assertTrue(Math.abs(w.walkPhase() - 8) < 0.5, "eight ticks at the design speed are not one trot: " + w.walkPhase());
        m.xo = m.getX();
        m.zo = m.getZ();
        // one mandible: the right button is the mandible's dry fire, never a right arm's clip
        h.assertTrue(HopperAnimation.arm(m, v, 1, t) == null, "a right arm clip on a one-tool frame");

        // a wall ahead, flush with the box: rising against it, it takes to it, clings on, climbs; stopped, it clings
        BlockPos wall = FLOOR.south(2);
        post(h, wall, 4);
        post(h, wall.east(), 4);
        post(h, wall.west(), 4);
        Vec3 base = m.position();
        double z = h.absoluteVec(Vec3.atLowerCornerOf(wall)).z - m.getBbWidth() / 2 - 0.01;
        long t0 = now(h);
        for (int i = 0; i <= 10; i++) {
            m.setPos(base.x, base.y + 0.2 * i, z);
            m.xo = base.x;
            m.zo = z;
            m.setOnGround(i == 0);
            m.view.update(m, t0 + i);
        }
        h.assertTrue(m.view.climb.face == Direction.SOUTH, "rising against the wall, it did not take to it: " + m.view.climb.face);
        h.assertTrue(clip(HopperAnimation.main(m, m.view, m.view.climb.since + 3)).equals("cling_on"), "no cling_on as it takes to the wall");
        h.assertTrue(clip(HopperAnimation.main(m, m.view, t0 + 10)).equals("climb"), "no climb while rising on the wall");
        for (int i = 11; i <= 22; i++) m.view.update(m, t0 + i);
        h.assertTrue(clip(HopperAnimation.main(m, m.view, t0 + 22)).equals("cling"), "no cling while still on the wall");
        h.assertTrue(HopperAnimation.cageShut(m), "the cage may open on a wall");
        // back on the ground (over the top): the model turns back upright as it lands
        m.setOnGround(true);
        m.view.update(m, t0 + 23);
        h.assertTrue(m.view.climb.face == null && clip(HopperAnimation.main(m, m.view, t0 + 24)).equals("land"), "no landing off the wall");
        h.assertTrue(m.view.climb.turned(t0 + 26, 6) > 0 && m.view.climb.turned(t0 + 30, 6) == 0, "the model does not turn back over 6 ticks");
        m.setPos(base);
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void theSkitterHoldsOnToItsWall(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(3));
        MechaEntity m = skitter(h, FLOOR, p.getUUID());
        board(h, p, m, Form.PILOT);
        BlockPos wall = FLOOR.south(2);
        for (int dx = -1; dx <= 1; dx++) post(h, wall.east(dx), 4);
        double z = h.absoluteVec(Vec3.atLowerCornerOf(wall)).z - m.getBbWidth() / 2 - 0.01;
        m.setPos(m.getX(), m.getY() + 1, z);
        m.setOnGround(false);
        p.zza = 0;
        p.xxa = 0;
        // no input on a wall: the move its pilot's client makes (tickRidden) holds it where it is
        h.assertTrue(Skitter.clings(m, p), "a Skitter on a wall with no input does not hold on");
        m.setDeltaMovement(0, -0.3, 0);
        MechaLogic.tickRidden(m, p);
        h.assertTrue(m.getDeltaMovement().y == 0 && m.fallDistance == 0, "it slides down its wall: " + m.getDeltaMovement());
        // sneaking, or steering away from the wall (it faces the wall: backing is away), lets go
        p.setShiftKeyDown(true);
        h.assertFalse(Skitter.clings(m, p), "sneaking does not let go");
        p.setShiftKeyDown(false);
        h.assertFalse(Skitter.clings(m, p), "a sneak's tap lets go only while the key is down");
        p.zza = 1;
        h.assertTrue(Skitter.clings(m, p), "steering back into the wall does not take hold again");
        p.zza = 0;
        p.setShiftKeyDown(true);
        Skitter.clings(m, p);
        p.setShiftKeyDown(false);
        m.setOnGround(true);
        Skitter.clings(m, p);
        m.setOnGround(false);
        h.assertTrue(Skitter.clings(m, p), "once off the wall it does not hold on again");
        p.zza = -1;
        h.assertFalse(Skitter.clings(m, p), "steering away does not let go");
        p.xxa = 1;
        p.zza = 0;
        h.assertTrue(Skitter.clings(m, p), "steering along the wall lets go");
        p.xxa = 0;
        // a flat battery lets go, and says so once
        m.setBattery(0);
        h.assertFalse(Skitter.clings(m, p), "a flat battery holds on");
        h.runAfterDelay(3, () -> {
            m.setPos(m.getX(), m.getY(), z);
            m.setOnGround(false);
        });
        h.runAfterDelay(8, () -> {
            h.assertTrue(count(p, "mecha.cling.no_power") == 1, "the flat battery's letting go is not said once: " + Refusals.delivered(p));
            m.setBattery(m.batteryCapacity());
            m.setVent(60);
            h.assertFalse(Skitter.clings(m, p), "venting holds on");
            m.setVent(0);
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void theJumpKeyIsTheRappelWhileTheLineHolds(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(3));
        MechaEntity m = skitter(h, FLOOR, p.getUUID(), MechaModule.GLIDER_WINGS);
        board(h, p, m, Form.PILOT);
        m.setOnGround(false);
        m.setAnchor(Skitter.hangPoint(m).add(0, 3, 0));
        m.setLineLength(3);
        m.setLineMode(Skitter.HANG);
        // the line's block (the server lets go of a line whose block is gone)
        post(h, FLOOR.north(3), 3);
        m.lineBlock = h.absolutePos(FLOOR.north(3).above(2));
        p.setJumping(true);
        m.setDeltaMovement(0, -0.5, 0);
        MechaLogic.tickRidden(m, p);
        h.assertTrue(Skitter.lineHolds(m) && Math.abs(m.getDeltaMovement().y + 0.5) < 1e-6, "the glider answered the jump key on the line: " + m.getDeltaMovement());
        // the line cut: the wings answer again
        m.setLineLength(0);
        m.setDeltaMovement(0, -0.5, 0);
        MechaLogic.tickRidden(m, p);
        h.assertTrue(Math.abs(m.getDeltaMovement().y + 0.08) < 1e-6, "with no line the glider does not answer: " + m.getDeltaMovement());
        p.setJumping(false);
        // the server's side: hanging with the jump key held, the line pays out
        m.setLineLength(3);
        p.setJumping(true);
        h.runAfterDelay(4, () -> {
            h.assertTrue(m.lineMode() == Skitter.RAPPEL && m.lineLength() > 3.3, "the jump key did not pay out: " + m.lineLength());
            p.setJumping(false);
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void theSkittersJarRidesInItsCageAndBoots(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(3));
        MechaEntity m = skitter(h, FLOOR, p.getUUID());
        board(h, p, m, Form.PILOT);
        Vec3 neck = HostControl.ridingPosition(m).subtract(m.position());
        h.assertTrue(neck.distanceTo(new Vec3(0, 1.725, 0)) < 0.01, "the jar does not sit in the cage's collar: " + neck);
        long at = m.boardedAt();
        h.assertTrue(at == now(h), "boarding not stamped");
        h.assertTrue(clip(HopperAnimation.cockpit(m, at + 5)).equals("board") && clip(HopperAnimation.cockpit(m, at + 30)).equals("boot"), "no board then boot");
        p.zza = 1;
        h.assertTrue(MechaLogic.riddenInput(m, p).z > 0, "the boot locks the controls");
        p.zza = 0;
        // its clip sounds borrow other machines' takes: not one of them is silent
        for (String name : clipSounds("mecha/skitter")) h.assertTrue(HopperSounds.get(MechaFrame.SKITTER, name) != null, "the Skitter's " + name + " plays nothing");
        // getting out on the ground: the cage opens and closes again over the empty collar
        Network.action(p, new Network.JarAction(Network.Action.EJECT));
        long out = now(h);
        h.assertTrue(p.getVehicle() == null && clip(HopperAnimation.unboard(m, out + 5)).equals("board"), "the cage does not open on the way out");
        done(h, p);
    }

    private static Set<String> bones(String path) {
        Set<String> out = new HashSet<>();
        var in = MultiLegGameTests.class.getResourceAsStream("/assets/headinajar/geo/entity/" + path + ".geo.json");
        if (in == null) throw new AssertionError("no model " + path);
        JsonObject geo = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        for (var b : geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")) {
            out.add(b.getAsJsonObject().get("name").getAsString());
        }
        return out;
    }

    /** Every mecha sound a model's clips name. */
    private static Set<String> clipSounds(String path) {
        Set<String> out = new HashSet<>();
        var in = MultiLegGameTests.class.getResourceAsStream("/assets/headinajar/animations/entity/" + path + ".animation.json");
        if (in == null) throw new AssertionError("no clips " + path);
        JsonObject anims = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("animations");
        for (var clip : anims.entrySet()) {
            JsonObject sounds = clip.getValue().getAsJsonObject().getAsJsonObject("sound_effects");
            if (sounds == null) continue;
            for (var key : sounds.entrySet()) {
                String effect = key.getValue().getAsJsonObject().get("effect").getAsString().split("[|]")[0];
                out.add(effect.substring(effect.indexOf("mecha.") + 6));
            }
        }
        return out;
    }

    @GameTest(template = "platform")
    public static void theMultiLegModelsHaveEveryBoneTheCodeUses(GameTestHelper h) {
        Set<String> sk = bones("mecha/skitter");
        MechaFrame frame = MechaFrame.SKITTER;
        h.assertTrue(frame.slots(MechaModule.Slot.LEGS) == 0, "the Skitter takes a legs module");
        for (int i = 0; i < frame.totalSlots(); i++) {
            for (String socket : HopperSockets.sockets(frame, i)) h.assertTrue(sk.contains(socket), "no socket " + socket);
        }
        MechaRig rig = MechaRig.SKITTER;
        for (String b : rig.tracked) h.assertTrue(sk.contains(b), "no tracked bone " + b);
        for (String b : rig.lamps) h.assertTrue(sk.contains(b), "no lamp " + b);
        for (var l : rig.locators.values()) h.assertTrue(sk.contains(l.bone()), "no locator bone " + l.bone());
        Set<String> sw = bones("shell/spider_walker");
        for (var l : SpiderWalkerAnimation.LOCATORS.values()) h.assertTrue(sw.contains(l.bone()), "no Spider Walker locator bone " + l.bone());
        for (int k = 0; k < 2; k++) {
            Set<String> model = k == 0 ? sk : sw;
            Legs.Spec legs = k == 0 ? rig.legs : SpiderWalkerAnimation.LEGS;
            for (String leg : legs.names()) {
                for (String b : List.of("hip_", "leg_", "shin_")) h.assertTrue(model.contains(b + leg), "no " + b + leg);
            }
            for (String b : List.of("root", "body", "jar_anchor", "restraint", "clamp_l", "clamp_r", "sensor", "launcher", "reel")) h.assertTrue(model.contains(b), "no " + b);
        }
        // the code's leg solve stands the Skitter's rest pad where its art script does: (18, 0, 18) from hips at 9.5, 13 up
        double[] a = Legs.solve((18 - 9.5) * Math.sqrt(2), -13, 12, 20);
        h.assertTrue(Math.abs(Math.toDegrees(a[0]) - 34.98) < 0.05 && Math.abs(Math.toDegrees(a[1] - a[0]) + 118.70) < 0.05,
                "the solve's rest angles differ from the art's: " + Math.toDegrees(a[0]) + " " + Math.toDegrees(a[1] - a[0]));
        h.succeed();
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void theSpiderWalkersWebLandsWithTheSpit(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(3));
        ShellEntity s = h.spawn(ModRegistry.SPIDER_WALKER.get(), FLOOR);
        face(s);
        board(h, p, s, Form.HOSTED);
        h.assertTrue(s.animated() && clip(SpiderWalkerAnimation.cockpit(s, s.boardedAt() + 5)).equals("board"), "no board clip");
        Hosts.data(s).primaryCooldown = 0;
        FormActions.state(p).setCharge(FormActions.state(p).chargeCapacity());
        BlockPos wall = FLOOR.south(5);
        post(h, wall, 3);
        p.lookAt(EntityAnchorArgument.Anchor.EYES, h.absoluteVec(Vec3.atCenterOf(wall.above()).add(0, 0, -0.5)));
        Network.action(p, new Network.JarAction(Network.Action.ABILITY));
        long at = now(h);
        BlockPos web = s.webTarget();
        h.assertTrue(s.webAt() == at * 2, "the web was not stamped: " + Refusals.delivered(p));
        h.assertTrue(web.equals(h.absolutePos(wall.above().north())), "the web is not aimed at the face looked at: " + web);
        h.assertTrue(h.getLevel().getBlockState(web).isAir(), "the web is there before the spit");
        h.assertTrue(clip(SpiderWalkerAnimation.web(s, at + 2)).equals("web"), "no web clip");
        h.runAfterDelay(SpiderWalkerAnimation.SPIT + 1, () -> {
            h.assertTrue(h.getLevel().getBlockState(web).is(Blocks.COBWEB), "the spit did not leave a web");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void aRefusedWebIsADud(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(3));
        ShellEntity s = h.spawn(ModRegistry.SPIDER_WALKER.get(), FLOOR);
        face(s);
        board(h, p, s, Form.HOSTED);
        Hosts.data(s).primaryCooldown = 0;
        FormActions.state(p).setCharge(FormActions.state(p).chargeCapacity());
        // a cow standing in grass: no room for a web where it stands
        BlockPos cowAt = FLOOR.south(4);
        Cow cow = h.spawn(EntityType.COW, cowAt);
        h.setBlock(cowAt, Blocks.SHORT_GRASS);
        p.lookAt(EntityAnchorArgument.Anchor.EYES, cow.getBoundingBox().getCenter());
        Network.action(p, new Network.JarAction(Network.Action.ABILITY));
        h.assertTrue(s.webAt() == now(h) * 2 + 1 && count(p, "host.web_blocked") == 1, "no dud for a blocked web: " + Refusals.delivered(p));
        h.assertTrue(clip(SpiderWalkerAnimation.web(s, now(h) + 5)).equals("web_dud"), "no dud clip");
        cow.discard();
        // cooling down: the dud again, with its line
        Hosts.data(s).primaryCooldown = 40;
        h.runAfterDelay(2, () -> {
            Network.action(p, new Network.JarAction(Network.Action.ABILITY));
            h.assertTrue(s.webAt() == now(h) * 2 + 1 && count(p, "host.cooling") >= 1, "no dud while cooling");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void spiderWalkerClipsFollowTheMotion(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(3));
        ShellEntity s = h.spawn(ModRegistry.SPIDER_WALKER.get(), FLOOR);
        face(s);
        board(h, p, s, Form.HOSTED);
        FormActions.state(p).setCharge(FormActions.state(p).chargeCapacity());
        Vec3 neck = HostControl.ridingPosition(s).subtract(s.position());
        h.assertTrue(neck.distanceTo(new Vec3(0, 0.81, 0)) < 0.01, "the jar is not on the lid: " + neck);
        long t = now(h) + 100;
        var v = new SpiderWalkerAnimation.View();
        String idle = clip(SpiderWalkerAnimation.main(s, v, t));
        h.assertTrue(idle.equals("idle") || idle.equals("idle_tic"), "not idle: " + idle);
        v.speed = 0.3;
        h.assertTrue(clip(SpiderWalkerAnimation.main(s, v, t)).equals("walk"), "no walk");
        v.forward = -0.08;
        h.assertTrue(clip(SpiderWalkerAnimation.main(s, v, t)).equals("walk_back"), "no walk_back");
        v.forward = 0;
        FormActions.state(p).setCharge(1);
        h.assertTrue(clip(SpiderWalkerAnimation.main(s, v, t)).equals("limp"), "no limp on an empty jar");
        FormActions.state(p).setCharge(FormActions.state(p).chargeCapacity());
        v.speed = 0;
        v.landAt = t - 3;
        h.assertTrue(clip(SpiderWalkerAnimation.main(s, v, t)).equals("land"), "no light landing");
        v.landAt = -1;
        v.airTicks = 8;
        h.assertTrue(clip(SpiderWalkerAnimation.main(s, v, t)).equals("fall"), "no fall");
        v.airTicks = 0;
        // up a wall: it stays upright and climbs, then hauls itself over the top
        BlockPos wall = FLOOR.south(2);
        post(h, wall, 4);
        post(h, wall.east(), 4);
        post(h, wall.west(), 4);
        Vec3 base = s.position();
        double z = h.absoluteVec(Vec3.atLowerCornerOf(wall)).z - s.getBbWidth() / 2 - 0.01;
        long t0 = now(h);
        for (int i = 0; i <= 10; i++) {
            s.setPos(base.x, base.y + 0.2 * i, z);
            s.xo = base.x;
            s.zo = z;
            s.setOnGround(i == 0);
            s.walker.update(s, t0 + i);
        }
        h.assertTrue(s.walker.climb.face == Direction.SOUTH && clip(SpiderWalkerAnimation.main(s, s.walker, t0 + 10)).equals("climb"), "no climb up the wall");
        s.setOnGround(true);
        s.walker.update(s, t0 + 11);
        h.assertTrue(clip(SpiderWalkerAnimation.main(s, s.walker, t0 + 12)).equals("climb_top"), "no climb_top over the top");
        s.setPos(base);
        s.setOnGround(true);
        // getting off: the get-off clip, the jar put where the clip lands it (behind)
        FormActions.leaveHost(p);
        h.assertTrue(p.getVehicle() == null && p.getZ() < s.getZ() - 0.5, "the jar was not put behind");
        h.assertTrue(clip(SpiderWalkerAnimation.cockpit(s, now(h) + 3)).equals("get_off"), "no get_off clip");
        h.assertTrue(SpiderWalkerAnimation.still(s, now(h) + 60), "an empty Spider Walker is not parked and still afterwards");
        done(h, p);
    }

    private static ListTag pos(GameTestHelper h) {
        Vec3 at = h.absoluteVec(Vec3.atBottomCenterOf(FLOOR));
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(at.x));
        pos.add(DoubleTag.valueOf(at.y));
        pos.add(DoubleTag.valueOf(at.z));
        return pos;
    }

    private static ListTag rotation() {
        ListTag rot = new ListTag();
        rot.add(FloatTag.valueOf(90));
        rot.add(FloatTag.valueOf(0));
        return rot;
    }

    /** A Skitter and a Spider Walker as the previous version saved them load, and save no new key. */
    @GameTest(template = "platform")
    public static void aSkitterAndASpiderWalkerSavedBeforeLoad(GameTestHelper h) {
        UUID owner = UUID.randomUUID();
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "headinajar:skitter");
        tag.put("Pos", pos(h));
        tag.put("Rotation", rotation());
        tag.putFloat("Health", 71);
        ListTag modules = new ListTag();
        int arm = MechaFrame.SKITTER.firstIndex(MechaModule.Slot.ARM);
        for (int i = 0; i < MechaFrame.SKITTER.totalSlots(); i++) {
            modules.add((i == arm ? module(MechaModule.DRILL) : ItemStack.EMPTY).saveOptional(h.getLevel().registryAccess()));
        }
        tag.put("Modules", modules);
        tag.put("Cargo", new ListTag());
        tag.putInt("Battery", 150_000);
        tag.putFloat("Heat", 40);
        tag.putUUID("Owner", owner);
        var loaded = EntityType.loadEntityRecursive(tag, h.getLevel(), e -> e);
        h.assertTrue(loaded instanceof MechaEntity, "the old Skitter did not load: " + loaded);
        MechaEntity m = (MechaEntity) loaded;
        h.assertTrue(m.frame() == MechaFrame.SKITTER && m.frame().animated() && m.arm(0) == MechaModule.DRILL, "not an animated Skitter with its tool");
        h.assertTrue(m.battery() == 150_000 && owner.equals(m.owner()) && Math.abs(m.getHealth() - 71) < 0.01, "battery, owner or health lost");
        h.assertTrue(m.lineAt() == -1 && !Skitter.out(m) && m.boardedAt() == -1, "an old Skitter loads with a line out");
        CompoundTag saved = new CompoundTag();
        m.addAdditionalSaveData(saved);
        h.assertTrue(saved.getAllKeys().stream().noneMatch(k -> k.contains("Line") || k.contains("Anchor") || k.contains("Wall") || k.contains("Reel")),
                "new saved key in " + saved.getAllKeys());

        CompoundTag old = new CompoundTag();
        old.putString("id", "headinajar:spider_walker");
        old.put("Pos", pos(h));
        old.put("Rotation", rotation());
        old.putFloat("Health", 19);
        var cargo = new net.minecraft.world.SimpleContainer(9);
        cargo.setItem(0, new ItemStack(Items.STRING, 5));
        old.put("Cargo", cargo.createTag(h.getLevel().registryAccess()));
        var walker = EntityType.loadEntityRecursive(old, h.getLevel(), e -> e);
        h.assertTrue(walker instanceof ShellEntity s && s.animated() && s.kind() == ShellEntity.Kind.SPIDER_WALKER && Math.abs(s.getHealth() - 19) < 0.01,
                "the old Spider Walker did not load: " + walker);
        ShellEntity s = (ShellEntity) walker;
        h.assertTrue(s.webAt() == -1 && s.hitAt() == -1 && s.boardedAt() == -1, "an old Spider Walker loads mid-sequence");
        CompoundTag again = new CompoundTag();
        s.addAdditionalSaveData(again);
        h.assertTrue(again.getList("Cargo", 10).size() == 1, "the Spider Walker's cargo was lost");
        for (String key : List.of("Web", "WebAt", "Hit", "Boarded")) h.assertFalse(again.contains(key), "new saved key " + key);
        h.succeed();
    }
}
