package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.AssemblyGantryBlockEntity;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.HeadRegistry;
import com.coolfreeze23.headinajar.item.MechaModuleItem;
import com.coolfreeze23.headinajar.mecha.Bulwark;
import com.coolfreeze23.headinajar.mecha.HopperAnimation;
import com.coolfreeze23.headinajar.mecha.HopperNet;
import com.coolfreeze23.headinajar.mecha.HopperSockets;
import com.coolfreeze23.headinajar.mecha.HopperView;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.MechaLogic;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.mecha.MechaRig;
import com.coolfreeze23.headinajar.menu.AssemblyGantryMenu;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
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
 * The Bulwark on GeckoLib: its clips from synced state (the shared animated-frame code with the Bulwark's rig), the
 * build and boarding, and Heavy Lifting on the drive key, pressed through the drive payload's handler.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class BulwarkGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);

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

    /** A Bulwark with these modules in their slots, owned by {@code owner}, battery full, facing south (+z). */
    private static MechaEntity bulwark(GameTestHelper h, BlockPos at, UUID owner, MechaModule... mods) {
        MechaEntity m = h.spawn(ModRegistry.BULWARK.get(), at);
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < m.frame().totalSlots(); i++) slots.add(ItemStack.EMPTY);
        Set<Integer> used = new HashSet<>();
        for (MechaModule mod : mods) {
            int i = m.frame().firstIndex(mod.slot);
            while (used.contains(i)) i++;
            used.add(i);
            slots.set(i, module(mod));
        }
        m.setModules(slots);
        m.setOwner(owner);
        m.setBattery(m.batteryCapacity());
        face(m);
        return m;
    }

    /** Faces south: the pilot's client turns a ridden machine's body (the test has none), so it is set here. */
    private static void face(MechaEntity m) {
        m.setYRot(0);
        m.setYBodyRot(0);
        m.yBodyRotO = 0;
    }

    /** The player, as a jar, climbs in by a real click on the mecha. */
    private static void board(GameTestHelper h, ServerPlayer p, MechaEntity m) {
        if (FormActions.state(p).form() == Form.BODY) FormActions.detach(p, 3);
        p.interactOn(m, InteractionHand.MAIN_HAND);
        h.assertTrue(p.getVehicle() == m && FormActions.state(p).form() == Form.PILOT, "the click did not board: " + FormActions.state(p).form());
        m.setOnGround(true);
        p.setYRot(0);
        p.setXRot(0);
        face(m);
    }

    /** The drive key, pressed and let go, as the drive payload's handler takes it. */
    private static void press(ServerPlayer p) {
        HopperNet.drive(p, true);
        HopperNet.drive(p, false);
    }

    private static long now(GameTestHelper h) {
        return h.getLevel().getGameTime();
    }

    private static String clip(HopperAnimation.Choice c) {
        return c == null ? "none" : c.clip().name();
    }

    private static long count(ServerPlayer p, String reason) {
        return Refusals.delivered(p).stream().filter(reason::equals).count();
    }

    private static int kind(MechaEntity m) {
        return m.liftAt() < 0 ? -1 : (int) (m.liftAt() & 3);
    }

    /** A cow standing two blocks ahead of a machine at {@code at}. */
    private static Cow cowAhead(GameTestHelper h, BlockPos at) {
        return h.spawn(EntityType.COW, at.south(2));
    }

    @GameTest(template = "platform", timeoutTicks = 120)
    public static void theGantryBuildsItAndBoardingBootsIt(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(3));
        h.setBlock(FLOOR, ModRegistry.ASSEMBLY_GANTRY.get());
        var gantry = (AssemblyGantryBlockEntity) h.getBlockEntity(FLOOR);
        gantry.items().setStackInSlot(AssemblyGantryBlockEntity.SLOT_FRAME, new ItemStack(ModRegistry.frameItem(MechaFrame.BULWARK)));
        gantry.items().setStackInSlot(AssemblyGantryBlockEntity.FIRST_MODULE + MechaFrame.BULWARK.firstIndex(MechaModule.Slot.LEGS), module(MechaModule.HYDRAULIC_LEGS));
        gantry.energy().set(50_000);
        h.assertTrue(gantry.button(p, AssemblyGantryMenu.ASSEMBLE), "assembly refused");
        // the frame stands on the deck once the arm has stood off
        h.runAfterDelay(com.coolfreeze23.headinajar.machine.GantryPlan.LEAD + 1, () -> {
            MechaEntity m = h.getEntities(ModRegistry.BULWARK.get()).get(0);
            h.assertTrue(m.frame().animated() && m.builtAt() == gantry.jobStart() + com.coolfreeze23.headinajar.machine.GantryPlan.LEAD, "the Bulwark's build did not start with the frame on the deck: " + m.builtAt());
            h.assertTrue(clip(HopperAnimation.main(m, new HopperView(), now(h) + 50)).equals("build"), "an empty Bulwark on the pad is not building");
            h.runAfterDelay(20, () -> {
                board(h, p, m);
                long at = now(h);
                h.assertTrue(m.builtAt() == -1 && m.boardedAt() == at, "boarding did not skip the build into the boot");
                h.assertTrue(clip(HopperAnimation.cockpit(m, at + 5)).equals("board"), "no boarding clip");
                h.assertTrue(clip(HopperAnimation.cockpit(m, at + 30)).equals("boot"), "no boot after the boarding");
                h.assertTrue(clip(HopperAnimation.main(m, new HopperView(), at + 30)).equals("boot_legs"), "the legs do not boot standing still");
                HopperView moving = new HopperView();
                moving.speed = 0.2;
                h.assertTrue(clip(HopperAnimation.main(m, moving, at + 30)).startsWith("walk"), "the boot's stamp holds a walking machine");
                p.zza = 1;
                h.assertTrue(MechaLogic.riddenInput(m, p).z > 0, "the boot locks the controls");
                p.zza = 0;
                // getting out the ordinary way opens and closes the clamps (the boarding clip backwards), then it parks
                Network.action(p, new Network.JarAction(Network.Action.EJECT));
                long out = now(h);
                h.assertTrue(p.getVehicle() == null, "the jar did not get out");
                h.assertTrue(clip(HopperAnimation.unboard(m, out + 5)).equals("board"), "the clamps do not open on the way out");
                h.assertTrue(clip(HopperAnimation.main(m, new HopperView(), out + 2)).equals("shutdown"), "no shutdown when the pilot leaves");
                h.assertTrue(HopperAnimation.still(m, out + 80), "an empty Bulwark is not parked and still afterwards");
                done(h, p);
            });
        });
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void bulwarkClipsFollowTheSyncedState(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = bulwark(h, FLOOR.east(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS, MechaModule.DRILL);
        board(h, p, m);
        long t = now(h) + 100;
        HopperView v = new HopperView();
        String idle = clip(HopperAnimation.main(m, v, t));
        h.assertTrue(idle.equals("idle") || idle.equals("idle_tic"), "not idle: " + idle);
        v.speed = 0.2;
        h.assertTrue(clip(HopperAnimation.main(m, v, t)).equals("walk_c"), "the walk is not walk_c");
        // playback follows the measured speed over the Bulwark's own divisor (0.2376 blocks a tick)
        HopperView w = new HopperView();
        w.speed = MechaRig.BULWARK.walkDesign;
        m.xo = m.getX() - MechaRig.BULWARK.walkDesign;
        w.update(m, 0);
        w.update(m, 4.5);
        w.update(m, 9);
        h.assertTrue(Math.abs(w.walkPhase() - 9) < 0.5, "nine ticks at the design speed are not one step: " + w.walkPhase());
        m.xo = m.getX();
        m.setBattery(0);
        h.assertTrue(clip(HopperAnimation.main(m, v, t)).equals("limp"), "a flat battery does not limp");
        m.setBattery(m.batteryCapacity());
        v.speed = 0;
        m.setVent(MechaLogic.VENT_TICKS - 10);
        HopperAnimation.Choice vent = HopperAnimation.main(m, v, t);
        h.assertTrue(clip(vent).equals("vent") && Math.abs(vent.tick() - 10) < 1, "vent clip not at the vent's tick: " + vent);
        m.setVent(0);
        v.turn = -8;
        h.assertTrue(clip(HopperAnimation.main(m, v, t)).equals("turn_l"), "a body turning left does not play turn_l");
        v.turn = 0;
        // the idle tic comes round at the machine's own phase
        boolean tic = false;
        for (int i = 0; i < 320 && !tic; i++) tic = clip(HopperAnimation.main(m, v, t + i)).equals("idle_tic");
        h.assertTrue(tic, "no idle tic in 320 ticks");
        m.hurt(h.getLevel().damageSources().generic(), 1);
        h.assertTrue(clip(HopperAnimation.hit(m, now(h))).equals("hurt"), "no flinch when hit");
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 140)
    public static void heavyLiftingGrabsCarriesAndThrowsACow(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        BlockPos at = FLOOR.east(3);
        MechaEntity m = bulwark(h, at, p.getUUID(), MechaModule.HYDRAULIC_LEGS);
        board(h, p, m);
        Cow cow = cowAhead(h, at);
        float health = cow.getHealth();
        long[] times = new long[2];
        // scheduled from the top only (a test's schedule must not grow while it runs)
        h.runAfterDelay(2, () -> {
            int battery = m.battery();
            long start = now(h);
            times[0] = start;
            press(p);
            h.assertTrue(kind(m) == Bulwark.GRAB && m.liftAt() / 4 == start, "the drive key did not start a grab: " + m.liftAt());
            h.assertTrue(battery - m.battery() >= Bulwark.GRAB_FE, "the grab cost nothing");
            h.assertTrue(clip(HopperAnimation.main(m, new HopperView(), start + 5)).equals("grab_high"), "no grab clip");
            h.assertTrue(MechaLogic.riddenInput(m, p).equals(Vec3.ZERO), "a reaching Bulwark still walks");
            h.assertTrue(m.heldId() == -1, "it holds before the fists close");
        });
        h.runAfterDelay(2 + Bulwark.GRAB_CLOSE + 2, () -> h.assertTrue(m.heldId() == cow.getId(), "the fists did not take the cow at tick 10: " + m.heldId()));
        h.runAfterDelay(2 + Bulwark.GRAB_TICKS + 2, () -> {
            // held at the chest by position every tick, never by the saved no-gravity flag
            Vec3 chest = Bulwark.feet(m, cow, now(h), null);
            // put back after its own tick: it never sinks between settings
            h.assertTrue(cow.position().distanceTo(chest) < 0.05, "the cow is not at the chest: " + cow.position() + " vs " + chest);
            h.assertFalse(cow.isNoGravity(), "the hold set the saved no-gravity flag");
            h.assertTrue(clip(HopperAnimation.main(m, new HopperView(), now(h))).equals("hold_high"), "standing with a load is not the hold");
            HopperView moving = new HopperView();
            moving.speed = 0.2;
            h.assertTrue(clip(HopperAnimation.main(m, moving, now(h))).equals("carry_walk_high"), "walking with a load is not the carry");
            h.assertTrue(!Bulwark.rooted(m), "a Bulwark holding a load cannot walk");
            times[1] = now(h);
            press(p);
            h.assertTrue(kind(m) == Bulwark.THROW, "the second press did not throw");
            h.assertTrue(clip(HopperAnimation.main(m, new HopperView(), times[1] + 8)).equals("throw_high"), "no throw clip");
        });
        h.runAfterDelay(2 + Bulwark.GRAB_TICKS + 2 + Bulwark.THROW_RELEASE + 2, () -> {
            h.assertTrue(m.heldId() == -1, "the throw never let go");
            Vec3 v = m.lastThrow;
            h.assertTrue(v.z > 1.0 && Math.abs(v.x) < 0.2 && v.y > 0.1, "not thrown along the look: " + v);
            h.assertTrue(Math.abs(v.horizontalDistance() - Bulwark.throwSpeed(cow)) < 0.05, "the throw's speed is not the load's");
            h.assertTrue(cow.getHealth() == health, "the throw itself hurt the cow");
            h.assertFalse(cow.isNoGravity(), "a thrown cow floats");
            // a thrown cow flies on into whichever test the grid placed next door: it goes now
            cow.discard();
            done(h, p);
        });
    }

    /** The overhead carry: its grab, hold, walk and throw clips, the load on the fists above the jar. */
    @GameTest(template = "platform", timeoutTicks = 120)
    public static void theOverheadCarryHoldsTheLoadAboveTheJar(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        BlockPos at = FLOOR.east(3);
        MechaEntity m = bulwark(h, at, p.getUUID(), MechaModule.HYDRAULIC_LEGS);
        board(h, p, m);
        Cow cow = cowAhead(h, at);
        h.runAfterDelay(2, () -> {
            press(p);
            h.assertTrue(clip(HopperAnimation.main(m, new HopperView(), now(h) + 5)).equals("grab_high"), "the overhead carry does not grab with grab_high");
        });
        h.runAfterDelay(2 + Bulwark.GRAB_TICKS + 2, () -> {
            h.assertTrue(m.heldId() == cow.getId(), "no hold");
            h.assertTrue(clip(HopperAnimation.main(m, new HopperView(), now(h))).equals("hold_high"), "not the overhead hold");
            HopperView moving = new HopperView();
            moving.speed = 0.2;
            h.assertTrue(clip(HopperAnimation.main(m, moving, now(h))).equals("carry_walk_high"), "not the overhead carry walk");
            // the load's feet on the fists, above the jar's valve (52.4 px): the clip's hold point (0, 57.2, 3.1) px
            Vec3 expected = m.position().add(0, 57.2 / 16, -3.1 / 16);
            h.assertTrue(cow.position().distanceTo(expected) < 0.05, "the load is not on the fists overhead: " + cow.position() + " vs " + expected);
            h.assertTrue(cow.getY() - m.getY() > 52.4 / 16, "the load is not above the jar");
            press(p);
            h.assertTrue(clip(HopperAnimation.main(m, new HopperView(), now(h) + 3)).equals("throw_high"), "not the overhead throw");
        });
        h.runAfterDelay(2 + Bulwark.GRAB_TICKS + 2 + Bulwark.THROW_RELEASE + 2, () -> {
            h.assertTrue(m.heldId() == -1 && m.lastThrow.z > 1.0, "the overhead throw did not let go at its tick 6: " + m.lastThrow);
            cow.discard();
            done(h, p);
        });
    }

    /** No cow goes into a roof: the grab is refused under a low ceiling, and a carried load meeting one is let go. */
    @GameTest(template = "platform", timeoutTicks = 120)
    public static void aLowCeilingIsNeverCarriedInto(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        BlockPos at = FLOOR.east(3);
        MechaEntity m = bulwark(h, at, p.getUUID(), MechaModule.HYDRAULIC_LEGS);
        board(h, p, m);
        Cow cow = cowAhead(h, at);
        // a slab of stone 4 blocks up over the machine: room for the hull and jar, not for a cow on the fists
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) h.setBlock(at.offset(dx, 4, dz), Blocks.STONE);
        h.runAfterDelay(2, () -> {
            press(p);
            h.assertTrue(kind(m) == Bulwark.REFUSED && count(p, "mecha.lift.no_headroom") == 1, "a grab under a low ceiling was not refused: " + Refusals.delivered(p));
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) h.setBlock(at.offset(dx, 4, dz), Blocks.AIR);
        });
        h.runAfterDelay(2 + Bulwark.REFUSED_TICKS + 2, () -> {
            // the cow may have wandered off while the refusal played: back where the fists reach
            cow.moveTo(Vec3.atBottomCenterOf(h.absolutePos(at.south(2))));
            press(p);
        });
        h.runAfterDelay(2 + Bulwark.REFUSED_TICKS + 2 + Bulwark.GRAB_TICKS + 2, () -> {
            h.assertTrue(m.heldId() == cow.getId(), "no hold in the open");
            // the ceiling comes down over the carried cow
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) h.setBlock(at.offset(dx, 4, dz), Blocks.STONE);
        });
        h.runAfterDelay(2 + Bulwark.REFUSED_TICKS + 2 + Bulwark.GRAB_TICKS + 4, () -> {
            h.assertTrue(m.heldId() == -1, "a carried cow was kept in a ceiling");
            cow.discard();
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 300)
    public static void everyRefusedGrabSaysWhy(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        BlockPos at = FLOOR.east(3);
        ServerPlayer stranger = player(h, at.south(2));
        MechaEntity m = bulwark(h, at, p.getUUID(), MechaModule.HYDRAULIC_LEGS);
        board(h, p, m);
        String[] steps = {"mecha.lift.untrusted", "mecha.lift.too_heavy", "mecha.lift.nothing", "mecha.lift.venting", "mecha.lift.no_power"};
        Entity[] golem = new Entity[1];
        for (int i = 0; i < steps.length; i++) {
            int step = i;
            h.runAfterDelay(2 + 30L * i, () -> {
                switch (step) {
                    case 1 -> {
                        stranger.moveTo(stranger.getX() + 20, stranger.getY(), stranger.getZ());
                        golem[0] = h.spawn(EntityType.IRON_GOLEM, at.south(2));
                    }
                    case 2 -> golem[0].discard();
                    case 3 -> m.setVent(60);
                    case 4 -> {
                        m.setVent(0);
                        m.setBattery(100);
                    }
                    default -> {
                    }
                }
                long t = now(h);
                press(p);
                h.assertTrue(kind(m) == Bulwark.REFUSED && m.liftAt() / 4 == t, steps[step] + ": the press did not play the refused grab");
                h.assertTrue(count(p, steps[step]) == 1, steps[step] + " was not said once: " + Refusals.delivered(p));
                h.assertTrue(clip(HopperAnimation.main(m, new HopperView(), t + 15)).equals("grab_refused") || step == 3,
                        steps[step] + ": no shrug");
                // a press while the shrug plays is answered too
                if (step == 0) {
                    press(p);
                    h.assertTrue(count(p, "mecha.lift.busy") == 1, "a press during the shrug said nothing");
                }
            });
        }
        h.runAfterDelay(2 + 30L * steps.length, () -> done(h, p, stranger));
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void aPlayerWhoTrustsThePilotCanBeLifted(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        BlockPos at = FLOOR.east(3);
        ServerPlayer friend = player(h, at.south(2));
        HeadRegistry.get(h.getLevel().getServer()).setTrust(friend.getUUID(), p.getUUID(), true);
        // the test area's barrier ceiling is lower than a standing player held overhead: the friend lies flat (0.6 tall)
        friend.setPose(net.minecraft.world.entity.Pose.SWIMMING);
        friend.refreshDimensions();
        MechaEntity m = bulwark(h, at, p.getUUID(), MechaModule.HYDRAULIC_LEGS);
        board(h, p, m);
        h.runAfterDelay(2, () -> {
            press(p);
            h.assertTrue(kind(m) == Bulwark.GRAB, "a trusting player was refused: " + Refusals.delivered(p));
        });
        h.runAfterDelay(2 + Bulwark.GRAB_HELD + 4, () -> {
            h.assertTrue(m.heldId() == friend.getId(), "the friend was not taken");
            h.assertTrue(friend.position().distanceTo(Bulwark.feet(m, friend, now(h), null)) < 0.4, "the friend is not at the chest");
            h.assertFalse(friend.isNoGravity(), "a lifted player floats by the saved flag");
            // and the way out: the pilot gets out, the friend simply falls
            Network.action(p, new Network.JarAction(Network.Action.EJECT));
            h.assertTrue(m.heldId() == -1, "getting out did not let go");
            done(h, p, friend);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void theHoldEndsOnEveryWayOut(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        BlockPos at = FLOOR.east(3);
        MechaEntity m = bulwark(h, at, p.getUUID(), MechaModule.HYDRAULIC_LEGS, MechaModule.DRILL);
        board(h, p, m);
        Cow first = cowAhead(h, at);
        h.runAfterDelay(2, () -> press(p));
        h.runAfterDelay(2 + Bulwark.GRAB_TICKS + 2, () -> {
            h.assertTrue(m.heldId() == first.getId(), "no first hold");
            // an arm button while the fists hold says so
            m.armsHeld = 1;
            m.tick();
            m.armsHeld = 0;
            m.tick();
            h.assertTrue(count(p, "mecha.arm.hands_full") >= 1, "an arm press with full hands said nothing");
            first.kill();
        });
        h.runAfterDelay(2 + Bulwark.GRAB_TICKS + 4, () -> {
            h.assertTrue(m.heldId() == -1, "the hold outlived its load");
            Cow second = cowAhead(h, at);
            second.moveTo(m.getX(), m.getY(), m.getZ() + 2.2);
            press(p);
        });
        h.runAfterDelay(2 + 2 * Bulwark.GRAB_TICKS + 8, () -> {
            h.assertTrue(m.heldId() >= 0, "no second hold: " + Refusals.delivered(p));
            m.setVent(MechaLogic.VENT_TICKS);
        });
        h.runAfterDelay(2 + 2 * Bulwark.GRAB_TICKS + 10, () -> {
            h.assertTrue(m.heldId() == -1, "venting kept the load");
            m.setVent(0);
            for (Cow c : h.getEntities(EntityType.COW)) c.discard();
            Cow third = cowAhead(h, at);
            third.moveTo(m.getX(), m.getY(), m.getZ() + 2.2);
            press(p);
        });
        h.runAfterDelay(2 + 3 * Bulwark.GRAB_TICKS + 14, () -> {
            h.assertTrue(m.heldId() >= 0, "no third hold: " + Refusals.delivered(p));
            int held = m.heldId();
            // the machine is destroyed under its pilot: the pilot is thrown clear and the load falls
            m.hurt(h.getLevel().damageSources().generic(), 10_000);
            h.assertTrue(m.heldId() == -1, "the dying machine kept its load");
            Entity load = h.getLevel().getEntity(held);
            h.assertTrue(load != null && !load.isNoGravity(), "the released load floats");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void aBlockIsLiftedAndSetDownLookingDown(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        BlockPos at = FLOOR.east(3);
        MechaEntity m = bulwark(h, at, p.getUUID(), MechaModule.HYDRAULIC_LEGS);
        board(h, p, m);
        BlockPos crate = at.south(2);
        h.setBlock(crate, Blocks.HAY_BLOCK);
        h.runAfterDelay(2, () -> press(p));
        h.runAfterDelay(2 + Bulwark.GRAB_CLOSE + 2, () -> {
            Entity held = h.getLevel().getEntity(m.heldId());
            h.assertTrue(held instanceof net.minecraft.world.entity.item.FallingBlockEntity, "the hay was not lifted: " + held + " " + Refusals.delivered(p));
            h.assertTrue(h.getBlockState(crate).isAir(), "the hay is still in the ground");
        });
        h.runAfterDelay(2 + Bulwark.GRAB_TICKS + 2, () -> {
            p.setXRot(60);
            press(p);
            h.assertTrue(kind(m) == Bulwark.SET, "a press looking down did not set the load down");
            p.setXRot(0);
        });
        h.runAfterDelay(2 + Bulwark.GRAB_TICKS + Bulwark.SET_TICKS + 30, () -> {
            h.assertTrue(m.heldId() == -1, "the set-down never let go");
            h.assertTrue(h.getEntities(EntityType.FALLING_BLOCK).isEmpty(), "the lifted block is still about");
            boolean placed = false;
            for (BlockPos pos : BlockPos.betweenClosed(at.offset(-3, -1, -1), at.offset(3, 2, 5))) placed |= h.getBlockState(pos).is(Blocks.HAY_BLOCK);
            h.assertTrue(placed, "the hay was not set down as a block");
            done(h, p);
        });
    }

    /** Every slot of the Bulwark resolves to socket bones the shipped model has. */
    @GameTest(template = "platform")
    public static void modulesResolveToBulwarkSockets(GameTestHelper h) {
        Set<String> bones = bones("bulwark");
        MechaFrame frame = MechaFrame.BULWARK;
        for (int i = 0; i < frame.totalSlots(); i++) {
            var sockets = HopperSockets.sockets(frame, i);
            h.assertFalse(sockets.isEmpty(), "slot " + i + " has no socket");
            for (String s : sockets) {
                h.assertTrue(bones.contains(s), "slot " + i + " socket " + s + " is not in the Bulwark's model");
                h.assertTrue(HopperSockets.slotOf(frame, s) == i, "socket " + s + " maps back to the wrong slot");
            }
        }
        for (String b : MechaRig.BULWARK.tracked) h.assertTrue(bones.contains(b), "tracked bone " + b + " missing");
        for (String b : MechaRig.BULWARK.lamps) h.assertTrue(bones.contains(b), "lamp bone " + b + " missing");
        for (var l : MechaRig.BULWARK.locators.values()) h.assertTrue(bones.contains(l.bone()), "locator bone " + l.bone() + " missing");
        h.succeed();
    }

    private static Set<String> bones(String model) {
        Set<String> out = new HashSet<>();
        var in = BulwarkGameTests.class.getResourceAsStream("/assets/headinajar/geo/entity/mecha/" + model + ".geo.json");
        if (in == null) throw new AssertionError("no model " + model);
        JsonObject geo = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        for (var b : geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")) {
            out.add(b.getAsJsonObject().get("name").getAsString());
        }
        return out;
    }

    /** A Bulwark as the previous version saved it loads with its modules, battery, heat and owner, and saves no new key. */
    @GameTest(template = "platform")
    public static void aBulwarkSavedBeforeLoads(GameTestHelper h) {
        UUID owner = UUID.randomUUID();
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "headinajar:bulwark");
        Vec3 at = h.absoluteVec(Vec3.atBottomCenterOf(FLOOR));
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(at.x));
        pos.add(DoubleTag.valueOf(at.y));
        pos.add(DoubleTag.valueOf(at.z));
        tag.put("Pos", pos);
        ListTag rot = new ListTag();
        rot.add(FloatTag.valueOf(90));
        rot.add(FloatTag.valueOf(0));
        tag.put("Rotation", rot);
        tag.putFloat("Health", 97);
        ListTag modules = new ListTag();
        int arm = MechaFrame.BULWARK.firstIndex(MechaModule.Slot.ARM);
        for (int i = 0; i < MechaFrame.BULWARK.totalSlots(); i++) {
            ItemStack stack = i == 0 ? module(MechaModule.HYDRAULIC_LEGS) : i == arm ? module(MechaModule.DRILL) : ItemStack.EMPTY;
            modules.add(stack.saveOptional(h.getLevel().registryAccess()));
        }
        tag.put("Modules", modules);
        tag.put("Cargo", new ListTag());
        tag.putInt("Battery", 250_000);
        tag.putFloat("Heat", 120);
        tag.putUUID("Owner", owner);
        var loaded = EntityType.loadEntityRecursive(tag, h.getLevel(), e -> e);
        h.assertTrue(loaded instanceof MechaEntity, "the old Bulwark did not load: " + loaded);
        MechaEntity m = (MechaEntity) loaded;
        h.assertTrue(m.frame() == MechaFrame.BULWARK && m.frame().animated(), "not an animated Bulwark");
        h.assertTrue(m.has(MechaModule.HYDRAULIC_LEGS) && m.arm(0) == MechaModule.DRILL, "modules lost");
        h.assertTrue(m.battery() == 250_000 && Math.abs(m.heat() - 120) < 0.01 && owner.equals(m.owner()) && Math.abs(m.getHealth() - 97) < 0.01,
                "battery, heat, owner or health lost");
        h.assertTrue(m.builtAt() == -1 && m.boardedAt() == -1 && m.liftAt() == -1 && m.heldId() == -1, "an old Bulwark loads mid-sequence");
        CompoundTag saved = new CompoundTag();
        m.addAdditionalSaveData(saved);
        for (String key : List.of("Built", "Boarded", "Lift", "Held", "Grab")) h.assertFalse(saved.contains(key), "new saved key " + key);
        h.succeed();
    }
}
