package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.entity.RoverAnimation;
import com.coolfreeze23.headinajar.entity.ShellEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.host.HostControl;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.MechaModuleItem;
import com.coolfreeze23.headinajar.mecha.HopperAnimation;
import com.coolfreeze23.headinajar.mecha.HopperNet;
import com.coolfreeze23.headinajar.mecha.HopperSockets;
import com.coolfreeze23.headinajar.mecha.HopperView;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.MechaLogic;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.mecha.MechaRig;
import com.coolfreeze23.headinajar.mecha.Wheelhouse;
import com.coolfreeze23.headinajar.mecha.Wheels;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
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
 * The wheeled skeleton: the Wheelhouse (Rush Hour on the drive key, pressed through the drive payload's handler; its
 * drive, steering and clips from what a client measures; the bench) and the Rover (boarding and getting off by the
 * jar's own paths, the crash by the shell's own tick, the sprint by the ability key's handler). Old saves of both load.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class WheeledGameTests {
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

    /** Built ground under the test: the platform is grass, which is off-road for a tug. */
    private static void road(GameTestHelper h) {
        for (int x = 2; x <= 12; x++) for (int z = 1; z <= 13; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
    }

    private static ItemStack module(MechaModule m) {
        ItemStack stack = new ItemStack(ModRegistry.mechaModule(m));
        MechaModuleItem.setSlider(stack, 50);
        return stack;
    }

    /** A Wheelhouse with these modules, owned by {@code owner}, battery full, facing south (+z). */
    private static MechaEntity wheelhouse(GameTestHelper h, BlockPos at, UUID owner, MechaModule... mods) {
        MechaEntity m = h.spawn(ModRegistry.mechaType(MechaFrame.WHEELHOUSE), at);
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

    private static long now(GameTestHelper h) {
        return h.getLevel().getGameTime();
    }

    private static String clip(HopperAnimation.Choice c) {
        return c == null ? "none" : c.clip().name();
    }

    private static String clip(RoverAnimation.Choice c) {
        return c == null ? "none" : c.clip().name();
    }

    private static long count(ServerPlayer p, String reason) {
        return Refusals.delivered(p).stream().filter(reason::equals).count();
    }

    private static int kind(MechaEntity m) {
        return m.rushAt() < 0 ? -1 : (int) (m.rushAt() & 3);
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void rushHourBoostsRamsAndCoolsDown(GameTestHelper h) {
        road(h);
        ServerPlayer p = player(h, FLOOR.west(3));
        MechaEntity m = wheelhouse(h, FLOOR, p.getUUID());
        board(h, p, m, Form.PILOT);
        Cow cow = h.spawn(EntityType.COW, FLOOR.south(3));
        cow.setNoAi(true);
        float health = cow.getHealth();
        long[] t0 = new long[1];
        double[] slow = new double[1];
        // scheduled from the top only (a test's schedule must not grow while it runs)
        h.runAfterDelay(1, () -> {
            slow[0] = MechaLogic.riddenSpeed(m);
            int battery = m.battery();
            HopperNet.drive(p, true);
            HopperNet.drive(p, false);
            t0[0] = now(h);
            h.assertTrue(kind(m) == Wheelhouse.BOOST && m.rushAt() / 4 == t0[0], "the press did not start Rush Hour: " + m.rushAt());
            h.assertTrue(battery - m.battery() == Wheelhouse.RUSH_FE, "the press cost " + (battery - m.battery()));
            HopperView v = new HopperView();
            h.assertTrue(clip(HopperAnimation.main(m, v, t0[0] + 5)).equals("rush_windup"), "no wind-up");
            h.assertTrue(Wheelhouse.boost(m, t0[0] + 11) == 1, "boosting before the wind-up's tick 12");
        });
        h.runAfterDelay(14, () -> {
            h.assertTrue(Wheelhouse.boost(m, now(h)) == Wheelhouse.BOOST_SPEED, "no boost after the wind-up");
            h.assertTrue(Math.abs(MechaLogic.riddenSpeed(m) / slow[0] - Wheelhouse.BOOST_SPEED) < 0.01, "ridden speed not raised: " + MechaLogic.riddenSpeed(m));
        });
        // driven forward at 0.45 blocks a tick (as its pilot's client moves it) into the cow
        for (int i = 15; i < 22; i++) {
            h.runAfterDelay(i, () -> m.setPos(m.getX(), m.getY(), m.getZ() + 0.45));
        }
        h.runAfterDelay(22, () -> {
            h.assertTrue(m.ramAt() >= 0, "the buffer never rammed the cow");
            h.assertTrue(cow.getHealth() < health && cow.getHealth() >= health - 8.01f, "the ram did not hurt 4-8: " + cow.getHealth());
            h.assertTrue(cow.getLastHurtByMob() == p, "the pilot is not the attacker");
            h.assertTrue(m.getHealth() == m.getMaxHealth(), "the Wheelhouse took damage");
            HopperView v = new HopperView();
            h.assertTrue(clip(HopperAnimation.hit(m, m.ramAt() + 2)).equals("rush_ram"), "no ram clip");
            // the knocked cow is taken away before it can land in a neighbouring test
            cow.discard();
        });
        h.runAfterDelay(100, () -> {
            h.assertTrue(Wheelhouse.boost(m, now(h)) == 1, "the boost outlasted its 80 ticks");
            h.assertTrue(clip(HopperAnimation.main(m, new HopperView(), t0[0] + 100)).equals("rush_end"), "no wind-down");
        });
        h.runAfterDelay(120, () -> {
            // pressed again inside the cooldown: the refusal clip, a line, once
            HopperNet.drive(p, true);
            HopperNet.drive(p, false);
            h.assertTrue(kind(m) == Wheelhouse.REFUSED && count(p, "mecha.rush.cooldown") == 1, "the cooldown said nothing");
            h.assertTrue(clip(HopperAnimation.main(m, new HopperView(), now(h) + 9)).equals("rush_refused"), "no refusal clip");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void everyRefusedRushSaysWhy(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(3));
        MechaEntity m = wheelhouse(h, FLOOR, p.getUUID());
        board(h, p, m, Form.PILOT);
        // on grass: off-road
        HopperNet.drive(p, true);
        HopperNet.drive(p, false);
        h.assertTrue(kind(m) == Wheelhouse.REFUSED && count(p, "mecha.rush.off_road") == 1, "off-road said nothing: " + Refusals.delivered(p));
        road(h);
        h.runAfterDelay(1, () -> {
            m.setBattery(100);
            HopperNet.drive(p, true);
            HopperNet.drive(p, false);
            h.assertTrue(count(p, "mecha.rush.no_power") == 1, "a flat battery said nothing: " + Refusals.delivered(p));
            m.setBattery(m.batteryCapacity());
            m.setVent(50);
            HopperNet.drive(p, true);
            h.assertTrue(count(p, "mecha.rush.venting") == 1, "venting said nothing");
            m.setVent(0);
            HopperNet.drive(p, true);
            h.assertTrue(kind(m) == Wheelhouse.BOOST, "a fair press refused: " + Refusals.delivered(p));
            HopperNet.drive(p, true);
            h.assertTrue(kind(m) == Wheelhouse.BOOST && count(p, "mecha.rush.busy") == 1, "a press during the boost broke it or said nothing");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void wheeledClipsFollowTheMotion(GameTestHelper h) {
        road(h);
        ServerPlayer p = player(h, FLOOR.west(3));
        MechaEntity m = wheelhouse(h, FLOOR, p.getUUID());
        board(h, p, m, Form.PILOT);
        long t = now(h) + 100;
        HopperView v = new HopperView();
        String idle = clip(HopperAnimation.main(m, v, t));
        h.assertTrue(idle.equals("idle") || idle.equals("idle_tic"), "not idle: " + idle);
        v.wheels.signed = 0.5;
        h.assertTrue(clip(HopperAnimation.main(m, v, t)).equals("drive"), "driving does not play drive");
        v.wheels.startAt = t - 3;
        h.assertTrue(clip(HopperAnimation.main(m, v, t)).equals("drive_start"), "no drive_start");
        v.wheels.startAt = -1;
        m.setBattery(0);
        h.assertTrue(clip(HopperAnimation.main(m, v, t)).equals("limp"), "a flat battery does not limp");
        m.setBattery(m.batteryCapacity());
        v.wheels.signed = -0.2;
        h.assertTrue(clip(HopperAnimation.main(m, v, t)).equals("reverse"), "backing does not reverse");
        v.wheels.signed = 0;
        v.wheels.brakeAt = t - 2;
        h.assertTrue(clip(HopperAnimation.main(m, v, t)).equals("brake"), "a hard stop does not brake");
        v.wheels.brakeAt = -1;
        v.wheels.turn = 8;
        h.assertTrue(clip(HopperAnimation.steer(m, v, t)).equals("turn_r"), "turning on the spot does not play turn_r");
        v.wheels.signed = 0.5;
        h.assertTrue(HopperAnimation.steer(m, v, t) == null, "a clip steers while driving (the code should)");

        // the wheels roll 130.96 degrees a block: half a block a tick is 65.5 a tick, forward is negative
        Wheels.State s = new Wheels.State();
        for (int i = 0; i <= 40; i++) {
            m.xo = m.getX();
            m.zo = m.getZ() - 0.5;
            s.update(m, MechaRig.WHEELHOUSE.wheels, i, true, false);
        }
        float before = s.spin[0];
        m.zo = m.getZ() - 0.5;
        s.update(m, MechaRig.WHEELHOUSE.wheels, 41, true, false);
        float step = net.minecraft.util.Mth.wrapDegrees(s.spin[0] - before);
        h.assertTrue(Math.abs(step + 0.5 * 130.96) < 1.5, "the wheel turned " + step + " degrees in a tick at 0.5");
        // a gentle curve steers gently: 2 degrees a tick at 0.5 blocks a tick is about 4.6 degrees of lock, to the right
        Wheels.State g = new Wheels.State();
        for (int i = 0; i <= 80; i++) {
            m.yBodyRot = 2 * i;
            Vec3 f = Vec3.directionFromRotation(0, m.yBodyRot);
            m.xo = m.getX() - f.x * 0.5;
            m.zo = m.getZ() - f.z * 0.5;
            g.update(m, MechaRig.WHEELHOUSE.wheels, i, true, false);
        }
        h.assertTrue(g.steer < -2 && g.steer > -8, "a gentle right curve steered " + g.steer + " (want about -4.6)");
        face(m);
        // the Rover's castors are capped at 150 degrees a tick: sprinting never strobes them backwards
        Wheels.State r = new Wheels.State();
        for (int i = 0; i <= 40; i++) {
            m.xo = m.getX();
            m.zo = m.getZ() - 0.74;
            r.update(m, RoverAnimation.SPEC, i, true, false);
        }
        before = r.spin[0];
        m.zo = m.getZ() - 0.74;
        r.update(m, RoverAnimation.SPEC, 41, true, false);
        float castor = net.minecraft.util.Mth.wrapDegrees(r.spin[0] - before);
        h.assertTrue(Math.abs(castor + 150) < 0.5, "the castor was not capped: " + castor);
        m.xo = m.getX();
        m.zo = m.getZ();

        // Rush Hour's clips from the synced moment
        long n = now(h);
        m.setRushAt(n, Wheelhouse.BOOST);
        HopperView still = new HopperView();
        h.assertTrue(clip(HopperAnimation.main(m, still, n + 5)).equals("rush_windup"), "no wind-up clip");
        h.assertTrue(clip(HopperAnimation.main(m, still, n + 40)).equals("rush"), "no boost clip");
        h.assertTrue(clip(HopperAnimation.main(m, still, n + 100)).equals("rush_end"), "no wind-down clip");
        m.setRushAt(n, Wheelhouse.REFUSED);
        h.assertTrue(clip(HopperAnimation.main(m, still, n + 9)).equals("rush_refused"), "no refusal clip");
        m.setRushAt(-1, 0);
        // the right button with no tool behind it: the one tool's dry fire, and the line, through the server tick
        m.armsHeld = 2;
        h.runAfterDelay(2, () -> {
            h.assertTrue(m.dryAt() >= 0 && (m.dryAt() & 1) == 0, "the right button's dry fire is not the tool's: " + m.dryAt());
            h.assertTrue(count(p, "mecha.arm.empty") >= 1, "no line for an empty button");
            HopperView a = new HopperView();
            h.assertTrue(clip(HopperAnimation.arm(m, a, 0, m.dryAt() / 2 + 3)).equals("dry_fire_l"), "no dry fire clip");
            h.assertTrue(HopperAnimation.arm(m, a, 1, m.dryAt() / 2 + 3) == null, "a right arm clip on a one-tool frame");
            m.armsHeld = 0;
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void theJarRidesAtTheSternAndStaffSitOnTheBench(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(3));
        ServerPlayer a = player(h, FLOOR.west(2)), b = player(h, FLOOR.east(3));
        MechaEntity m = wheelhouse(h, FLOOR, p.getUUID());
        board(h, p, m, Form.PILOT);
        Vec3 neck = HostControl.ridingPosition(m).subtract(m.position());
        h.assertTrue(neck.distanceTo(new Vec3(0, 1.975, -0.594)) < 0.01, "the jar's seat is not at the stern: " + neck);
        a.interactOn(m, InteractionHand.MAIN_HAND);
        b.interactOn(m, InteractionHand.MAIN_HAND);
        h.assertTrue(a.getVehicle() == m && b.getVehicle() == m, "the staff did not get on");
        Vec3 left = m.getPassengerRidingPosition(a).subtract(m.position()), right = m.getPassengerRidingPosition(b).subtract(m.position());
        // facing south, the machine's left is east (+x) and its front +z
        h.assertTrue(left.distanceTo(new Vec3(0.3125, 1.125, 0.15625)) < 0.01, "the first passenger is not on the bench's left seat: " + left);
        h.assertTrue(right.distanceTo(new Vec3(-0.3125, 1.125, 0.15625)) < 0.01, "the second passenger is not on the right seat: " + right);
        done(h, p, a, b);
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void theRoverPlaysTheGetOffForWhereTheJarWent(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(3));
        ShellEntity r = h.spawn(ModRegistry.ROVER.get(), FLOOR);
        face(r);
        board(h, p, r, Form.HOSTED);
        long on = r.boardedAt();
        h.assertTrue(on == now(h), "boarding not stamped");
        h.assertTrue(clip(RoverAnimation.cockpit(r, on + 5)).equals("board"), "no board clip");
        Vec3 neck = HostControl.ridingPosition(r).subtract(r.position());
        h.assertTrue(neck.distanceTo(new Vec3(0, 0.875, 0)) < 0.01, "the jar is not on the tray: " + neck);
        FormActions.leaveHost(p);
        h.assertTrue(p.getVehicle() == null && r.gotOffAt() / 4 == now(h) && (r.gotOffAt() & 3) == ShellEntity.BEHIND, "getting off not stamped behind: " + r.gotOffAt());
        h.assertTrue(p.getZ() < r.getZ() - 0.5, "the jar was not put behind");
        h.assertTrue(clip(RoverAnimation.cockpit(r, now(h) + 3)).equals("get_off"), "no get_off clip");
        // behind is walled: the right (west, facing south) is next
        h.runAfterDelay(2, () -> {
            h.setBlock(FLOOR.north(1), Blocks.STONE);
            h.setBlock(FLOOR.north(1).above(), Blocks.STONE);
            face(r);
            board(h, p, r, Form.HOSTED);
            FormActions.leaveHost(p);
            h.assertTrue((r.gotOffAt() & 3) == ShellEntity.RIGHT && p.getX() < r.getX() - 0.5, "not put on the right: " + r.gotOffAt() + " " + p.position());
            h.assertTrue(clip(RoverAnimation.cockpit(r, now(h) + 3)).equals("get_off_r"), "no get_off_r clip");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void theRoverCrashIsSeenByEveryone(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(3));
        ShellEntity r = h.spawn(ModRegistry.ROVER.get(), FLOOR);
        face(r);
        board(h, p, r, Form.HOSTED);
        var state = FormActions.state(p);
        int integrity = state.integrity();
        // a wall ahead; the rider's client drives it there at 0.4 a tick and it stops dead against it
        BlockPos wall = FLOOR.south(4);
        h.setBlock(wall, Blocks.STONE);
        h.setBlock(wall.above(), Blocks.STONE);
        double stop = h.absoluteVec(Vec3.atLowerCornerOf(wall)).z - 0.65;
        for (int i = 1; i <= 6; i++) {
            int k = i;
            h.runAfterDelay(i, () -> r.setPos(r.getX(), r.getY(), stop - 0.4 * (6 - k)));
        }
        long[] at = new long[1];
        h.runAfterDelay(10, () -> {
            at[0] = r.crashedAt();
            h.assertTrue(at[0] >= 0, "the crash was not stamped");
            h.assertTrue(state.integrity() == Math.max(1, integrity - 1) || integrity <= 1, "no knock to the jar");
            h.assertTrue(clip(RoverAnimation.hit(r, at[0] + 2)).equals("crash"), "no crash clip");
        });
        h.runAfterDelay(30, () -> {
            h.assertTrue(r.crashedAt() == at[0], "one wall, several crashes");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 20)
    public static void theRoversSprintPlaysItsClip(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(3));
        ShellEntity r = h.spawn(ModRegistry.ROVER.get(), FLOOR);
        board(h, p, r, Form.HOSTED);
        Hosts.data(r).primaryCooldown = 0;
        FormActions.state(p).setCharge(FormActions.state(p).chargeCapacity());
        Network.action(p, new Network.JarAction(Network.Action.ABILITY));
        h.assertTrue(r.sprintedAt() == now(h), "the sprint was not stamped: " + Refusals.delivered(p));
        h.assertTrue(clip(RoverAnimation.main(r, r.view, now(h) + 3)).equals("sprint_start"), "no sprint clip");
        // the jar nearly out of charge: the Rover limps
        FormActions.state(p).setCharge(1);
        r.view.wheels.signed = 0.2;
        h.assertTrue(clip(RoverAnimation.main(r, r.view, now(h) + 40)).equals("limp"), "no limp on an empty jar");
        done(h, p);
    }

    private static Set<String> bones(String path) {
        Set<String> out = new HashSet<>();
        var in = WheeledGameTests.class.getResourceAsStream("/assets/headinajar/geo/entity/" + path + ".geo.json");
        if (in == null) throw new AssertionError("no model " + path);
        JsonObject geo = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        for (var b : geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")) {
            out.add(b.getAsJsonObject().get("name").getAsString());
        }
        return out;
    }

    @GameTest(template = "platform")
    public static void theWheeledModelsHaveEveryBoneTheCodeUses(GameTestHelper h) {
        Set<String> wh = bones("mecha/wheelhouse");
        MechaFrame frame = MechaFrame.WHEELHOUSE;
        for (int i = 0; i < frame.totalSlots(); i++) {
            for (String socket : HopperSockets.sockets(frame, i)) h.assertTrue(wh.contains(socket), "no socket " + socket);
        }
        MechaRig rig = MechaRig.WHEELHOUSE;
        for (String b : rig.tracked) h.assertTrue(wh.contains(b), "no tracked bone " + b);
        for (String b : rig.lamps) h.assertTrue(wh.contains(b), "no lamp " + b);
        for (var l : rig.locators.values()) h.assertTrue(wh.contains(l.bone()), "no locator bone " + l.bone());
        Set<String> rover = bones("shell/rover");
        for (Set<String> model : List.of(wh, rover)) {
            for (String c : Wheels.CORNERS) {
                for (String b : List.of("wheel_", "steer_", "strut_")) h.assertTrue(model.contains(b + c), "no " + b + c);
            }
            for (String b : List.of("root", "chassis", "body", "jar_anchor", "helm", "flag", "lamp_brake")) h.assertTrue(model.contains(b), "no " + b);
        }
        h.succeed();
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

    /** A Wheelhouse and a Rover as the previous version saved them load, and save no new key. */
    @GameTest(template = "platform")
    public static void aWheelhouseAndARoverSavedBeforeLoad(GameTestHelper h) {
        UUID owner = UUID.randomUUID();
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "headinajar:wheelhouse");
        tag.put("Pos", pos(h));
        tag.put("Rotation", rotation());
        tag.putFloat("Health", 77);
        ListTag modules = new ListTag();
        int arm = MechaFrame.WHEELHOUSE.firstIndex(MechaModule.Slot.ARM);
        for (int i = 0; i < MechaFrame.WHEELHOUSE.totalSlots(); i++) {
            modules.add((i == arm ? module(MechaModule.DRILL) : ItemStack.EMPTY).saveOptional(h.getLevel().registryAccess()));
        }
        tag.put("Modules", modules);
        tag.put("Cargo", new ListTag());
        tag.putInt("Battery", 200_000);
        tag.putFloat("Heat", 50);
        tag.putUUID("Owner", owner);
        var loaded = EntityType.loadEntityRecursive(tag, h.getLevel(), e -> e);
        h.assertTrue(loaded instanceof MechaEntity, "the old Wheelhouse did not load: " + loaded);
        MechaEntity m = (MechaEntity) loaded;
        h.assertTrue(m.frame() == MechaFrame.WHEELHOUSE && m.frame().animated() && m.arm(0) == MechaModule.DRILL, "not an animated Wheelhouse with its tool");
        h.assertTrue(m.battery() == 200_000 && owner.equals(m.owner()) && Math.abs(m.getHealth() - 77) < 0.01, "battery, owner or health lost");
        h.assertTrue(m.rushAt() == -1 && m.ramAt() == -1 && m.boardedAt() == -1, "an old Wheelhouse loads mid-sequence");
        CompoundTag saved = new CompoundTag();
        m.addAdditionalSaveData(saved);
        for (String key : List.of("Rush", "Ram", "RushAt", "Boarded")) h.assertFalse(saved.contains(key), "new saved key " + key);

        CompoundTag old = new CompoundTag();
        old.putString("id", "headinajar:rover");
        old.put("Pos", pos(h));
        old.put("Rotation", rotation());
        old.putFloat("Health", 21);
        var cargo = new net.minecraft.world.SimpleContainer(9);
        cargo.setItem(0, new ItemStack(Items.BREAD, 3));
        old.put("Cargo", cargo.createTag(h.getLevel().registryAccess()));
        var rover = EntityType.loadEntityRecursive(old, h.getLevel(), e -> e);
        h.assertTrue(rover instanceof ShellEntity s && s.animated() && Math.abs(s.getHealth() - 21) < 0.01, "the old Rover did not load: " + rover);
        ShellEntity s = (ShellEntity) rover;
        h.assertTrue(s.boardedAt() == -1 && s.gotOffAt() == -1 && s.crashedAt() == -1 && s.sprintedAt() == -1, "an old Rover loads mid-sequence");
        CompoundTag again = new CompoundTag();
        s.addAdditionalSaveData(again);
        h.assertTrue(again.getList("Cargo", 10).size() == 1, "the Rover's cargo was lost");
        for (String key : List.of("Boarded", "GotOff", "Crashed", "Sprinted")) h.assertFalse(again.contains(key), "new saved key " + key);
        h.succeed();
    }
}
