package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.AssemblyGantryBlockEntity;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.genetics.Gene;
import com.coolfreeze23.headinajar.genetics.Genetics;
import com.coolfreeze23.headinajar.genetics.Serum;
import com.coolfreeze23.headinajar.item.MechaModuleItem;
import com.coolfreeze23.headinajar.mecha.Executive;
import com.coolfreeze23.headinajar.mecha.HopperAnimation;
import com.coolfreeze23.headinajar.mecha.HopperNet;
import com.coolfreeze23.headinajar.mecha.HopperSockets;
import com.coolfreeze23.headinajar.mecha.HopperView;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.MechaLogic;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.mecha.MechaRig;
import com.coolfreeze23.headinajar.mecha.Siege;
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
 * The Colossus and the Executive on GeckoLib: their models and sockets, the build and boarding, Siege and the premium
 * jingle on the drive key (pressed through the drive payload's handler), the Gene Port vials, and old saves.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class HeavyFrameGameTests {
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

    /** A machine of this frame with these modules in their slots, owned by {@code owner}, battery full, facing south. */
    private static MechaEntity machine(GameTestHelper h, MechaFrame frame, BlockPos at, UUID owner, MechaModule... mods) {
        MechaEntity m = h.spawn(ModRegistry.mechaType(frame), at);
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

    private static void face(MechaEntity m) {
        m.setYRot(0);
        m.setYBodyRot(0);
        m.yBodyRotO = 0;
    }

    /** The player, as a jar, climbs in by a real click on the machine. */
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
        return m.verbAt() < 0 ? -1 : (int) (m.verbAt() & 7);
    }

    private static String main(MechaEntity m, double at) {
        return clip(HopperAnimation.main(m, new HopperView(), at));
    }

    // --- the Colossus --------------------------------------------------------------------------------------------

    /** The Gantry's Assemble builds a Colossus on the pad (its 320-tick commissioning); a jar's click boards and boots it. */
    @GameTest(template = "tall_platform", timeoutTicks = 120)
    public static void theGantryBuildsAColossusAndBoardingBootsIt(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(3));
        h.setBlock(FLOOR, ModRegistry.ASSEMBLY_GANTRY.get());
        var gantry = (AssemblyGantryBlockEntity) h.getBlockEntity(FLOOR);
        gantry.items().setStackInSlot(AssemblyGantryBlockEntity.SLOT_FRAME, new ItemStack(ModRegistry.frameItem(MechaFrame.COLOSSUS)));
        gantry.items().setStackInSlot(AssemblyGantryBlockEntity.FIRST_MODULE + MechaFrame.COLOSSUS.firstIndex(MechaModule.Slot.LEGS), module(MechaModule.HYDRAULIC_LEGS));
        gantry.energy().set(50_000);
        h.assertTrue(gantry.button(p, AssemblyGantryMenu.ASSEMBLE), "assembly refused");
        // the frame stands on the deck once the arm has stood off
        h.runAfterDelay(com.coolfreeze23.headinajar.machine.GantryPlan.LEAD + 1, () -> {
            MechaEntity m = h.getEntities(ModRegistry.mechaType(MechaFrame.COLOSSUS)).get(0);
            h.assertTrue(m.frame().animated() && m.builtAt() == gantry.jobStart() + com.coolfreeze23.headinajar.machine.GantryPlan.LEAD, "the Colossus's build did not start with the frame on the deck: " + m.builtAt());
            h.assertTrue(main(m, now(h) + 300).equals("build"), "an empty Colossus is not still building at 300 ticks of 320");
            h.runAfterDelay(20, () -> {
                board(h, p, m);
                long at = now(h);
                h.assertTrue(m.builtAt() == -1 && m.boardedAt() == at, "boarding did not skip the build into the boot");
                h.assertTrue(clip(HopperAnimation.cockpit(m, at + 30)).equals("boot"), "no boot after the boarding");
                p.zza = 1;
                h.assertTrue(MechaLogic.riddenInput(m, p).z > 0, "the boot locks the controls");
                p.zza = 0;
                HopperView moving = new HopperView();
                moving.speed = 0.2;
                h.assertTrue(clip(HopperAnimation.main(m, moving, at + 100)).equals("walk_c"), "the Colossus does not walk walk_c");
                // the machine the Gantry built (legs in its one Legs slot) can siege, once it has walked off the turntable onto the ground
                net.minecraft.world.phys.Vec3 off = h.absoluteVec(net.minecraft.world.phys.Vec3.atBottomCenterOf(FLOOR.west(3)));
                m.teleportTo(off.x, off.y, off.z);
                m.setOnGround(true);
                press(p);
                h.assertTrue(kind(m) == Siege.ENTER, "a Gantry-built Colossus can't siege: " + Refusals.delivered(p) + " at " + m.position() + " blocked by "
                        + Siege.obstacle(m) + " " + (Siege.obstacle(m) == null ? "" : h.getLevel().getBlockState(Siege.obstacle(m))));
                done(h, p);
            });
        });
    }

    /**
     * Siege: a press kneels it (10 kFE), it neither walks nor turns from the first tick, the seat goes down a block, it
     * is anchored from tick 38 (no knock-back, front blows cut, heat out faster), and a second press stands it up.
     */
    @GameTest(template = "platform", timeoutTicks = 200)
    public static void siegeKneelsAnchorsAndStandsUp(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        BlockPos at = FLOOR.east(3);
        MechaEntity m = machine(h, MechaFrame.COLOSSUS, at, p.getUUID(), MechaModule.HYDRAULIC_LEGS);
        board(h, p, m);
        ServerPlayer other = player(h, FLOOR.east(3).south(4));
        long[] start = new long[1];
        float[] front = new float[1];
        h.runAfterDelay(3, () -> {
            int battery = m.battery();
            start[0] = now(h);
            press(p);
            h.assertTrue(kind(m) == Siege.ENTER && m.verbAt() / 8 == start[0], "the drive key did not start Siege: " + m.verbAt() + " " + Refusals.delivered(p));
            h.assertTrue(battery - m.battery() >= Siege.SIEGE_FE, "Siege cost nothing");
            h.assertTrue(main(m, start[0] + 5).equals("siege_in"), "no kneel");
            p.zza = 1;
            h.assertTrue(MechaLogic.riddenInput(m, p).equals(Vec3.ZERO), "a kneeling Colossus still walks");
            p.zza = 0;
            h.assertFalse(Siege.locked(m), "anchored before the spades are in");
            // the seat follows the deck tick by tick: up a little for the survey, the steps' dips, the drop, the strike's overshoot
            h.assertTrue(Siege.seatPx(m, start[0] + 3) < 0 && Math.abs(Siege.seatPx(m, start[0] + 13) - 6) < 1e-6
                    && Math.abs(Siege.seatPx(m, start[0] + 31) - 27.5) < 1e-6 && Math.abs(Siege.seatPx(m, start[0] + 39.5) - 26) < 1e-6,
                    "the seat does not follow the deck's travel");
        });
        h.runAfterDelay(3 + Siege.IN_TICKS + 2, () -> {
            h.assertTrue(Siege.locked(m), "not anchored after the kneel");
            h.assertTrue(main(m, now(h)).equals("siege"), "the held Siege loop is not playing: " + main(m, now(h)));
            h.assertTrue(Math.abs(Siege.seatOffset(m).y + 1.625) < 1e-6 && Siege.seatOffset(m).z == 0, "the seat is not 26 px straight down");
            // knock-back does nothing while anchored
            m.setDeltaMovement(Vec3.ZERO);
            m.knockback(2, 1, 0);
            h.assertTrue(m.getDeltaMovement().lengthSqr() < 1e-6, "an anchored Colossus was knocked back");
            // a blow from the front (the other player stands south of it, where it faces)
            other.setYRot(180);
            float before = m.getHealth();
            other.attack(m);
            front[0] = before - m.getHealth();
            // heat goes out half as fast again
            m.setHeat(500);
        });
        h.runAfterDelay(3 + Siege.IN_TICKS + 3, () -> {
            float lost = 500 - m.heat();
            h.assertTrue(lost >= MechaFrame.COLOSSUS.dissipation * Siege.DISSIPATION - 0.5f, "an anchored Colossus sheds heat no faster: " + lost);
        });
        h.runAfterDelay(3 + Siege.IN_TICKS + 20, () -> {
            // the same blow from behind
            Vec3 behind = m.position().add(0, 0, -2.5);
            other.moveTo(behind.x, behind.y, behind.z, 0, 0);
            float before = m.getHealth();
            other.attack(m);
            float back = before - m.getHealth();
            h.assertTrue(front[0] > 0 && back > front[0], "a blow from the front is not cut: front " + front[0] + ", back " + back);
            long out = now(h);
            press(p);
            h.assertTrue(kind(m) == Siege.LEAVE && !Siege.locked(m), "the second press did not stand it up");
            h.assertTrue(main(m, out + 5).equals("siege_out"), "no getting up");
            h.assertTrue(Math.abs(Siege.seatPx(m, out + 12) - 21) < 1e-6 && Math.abs(Siege.seatPx(m, out + 20) - 7.3) < 1e-6,
                    "the seat does not rise with the deck");
            p.zza = 1;
            h.assertTrue(MechaLogic.riddenInput(m, p).equals(Vec3.ZERO), "a rising Colossus walks");
            p.zza = 0;
        });
        h.runAfterDelay(3 + Siege.IN_TICKS + 20 + Siege.OUT_TICKS + 2, () -> {
            p.zza = 1;
            h.assertTrue(MechaLogic.riddenInput(m, p).z > 0, "the controls stay locked after Siege");
            p.zza = 0;
            h.assertTrue(Siege.seatOffset(m).equals(Vec3.ZERO), "the seat did not come back up");
            done(h, p, other);
        });
    }

    /** Every refused Siege plays the refused clip and says why once; a press while kneeling says Planning is busy. */
    @GameTest(template = "platform", timeoutTicks = 300)
    public static void everyRefusedSiegeSaysWhy(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        BlockPos at = FLOOR.east(3);
        // the Colossus walks on the one Legs module it has room for: its knee spades stand in for Anchor Clamps
        MechaEntity bare = machine(h, MechaFrame.COLOSSUS, at, p.getUUID(), MechaModule.HYDRAULIC_LEGS);
        board(h, p, bare);
        h.runAfterDelay(2, () -> {
            h.assertTrue(MechaFrame.COLOSSUS.slots(MechaModule.Slot.LEGS) == 1 && Siege.whyNot(bare) == null, "a walking Colossus can't siege: " + Siege.whyNot(bare));
            // the machine is airborne (a reason the later machine is not tested on)
            bare.setOnGround(false);
            press(p);
            h.assertTrue(count(p, "mecha.siege.airborne") == 1 && kind(bare) == Siege.REFUSED, "a machine in the air was not refused once");
            h.assertTrue(main(bare, now(h) + 9).equals("siege_refused"), "no refused clip");
            Network.action(p, new Network.JarAction(Network.Action.EJECT));
            bare.discard();
        });
        MechaEntity[] m = new MechaEntity[1];
        h.runAfterDelay(6, () -> {
            m[0] = machine(h, MechaFrame.COLOSSUS, at, p.getUUID(), MechaModule.HYDRAULIC_LEGS);
            board(h, p, m[0]);
            m[0].setVent(40);
        });
        h.runAfterDelay(8, () -> {
            press(p);
            h.assertTrue(count(p, "mecha.siege.venting") == 1, "venting was not refused");
            m[0].setVent(0);
            m[0].setBattery(5000);
        });
        h.runAfterDelay(40, () -> {
            press(p);
            h.assertTrue(count(p, "mecha.siege.no_power") == 1, "a low battery was not refused: " + Refusals.delivered(p));
            m[0].setBattery(m[0].batteryCapacity());
            // soft ground under the spades: they bite behind the machine (it faces south), and the ground there gives way
            for (int dx = -2; dx <= 2; dx++) h.setBlock(at.offset(dx, -1, -2), Blocks.AIR);
            m[0].setOnGround(true);
        });
        h.runAfterDelay(70, () -> {
            press(p);
            h.assertTrue(count(p, "mecha.siege.ground") == 1, "soft ground was not refused: " + Refusals.delivered(p));
            for (int dx = -2; dx <= 2; dx++) h.setBlock(at.offset(dx, -1, -2), Blocks.STONE);
        });
        // moving: the machine is walked half a block a tick for a few ticks
        for (int i = 0; i < 6; i++) {
            int k = i;
            h.runAfterDelay(100 + i, () -> {
                Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(at)).add(0, 0, (k % 2) * 0.4);
                m[0].setPos(v.x, v.y, v.z);
                m[0].setOnGround(true);
            });
        }
        h.runAfterDelay(106, () -> {
            press(p);
            h.assertTrue(count(p, "mecha.siege.moving") == 1, "a moving machine was not refused: " + Refusals.delivered(p) + " " + m[0].siegeSpeed);
        });
        h.runAfterDelay(140, () -> {
            // in water
            h.setBlock(at, Blocks.WATER);
        });
        h.runAfterDelay(143, () -> {
            m[0].setOnGround(true);
            press(p);
            h.assertTrue(count(p, "mecha.siege.fluid") == 1, "a machine in water was not refused: " + Refusals.delivered(p));
            h.setBlock(at, Blocks.AIR);
        });
        h.runAfterDelay(170, () -> {
            press(p);
            h.assertTrue(kind(m[0]) == Siege.ENTER, "a fit Colossus did not start Siege: " + Refusals.delivered(p));
            press(p);
            h.assertTrue(count(p, "mecha.siege.busy") == 1 && kind(m[0]) == Siege.ENTER, "a press while kneeling did not say busy");
        });
        h.runAfterDelay(200, () -> {
            // the pilot leaves: the Siege ends with them, and the empty machine stands up out of it by its clip
            Network.action(p, new Network.JarAction(Network.Action.EJECT));
            h.assertTrue(p.getVehicle() == null && !Siege.locked(m[0]) && kind(m[0]) == Siege.LEAVE, "the Siege outlived its pilot");
            done(h, p);
        });
    }

    /** The held pose reaches 3.5 blocks behind: a wall there refuses Siege (the clip and one line), open ground lets it kneel. */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void siegeNeedsRoomBehind(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(4));
        BlockPos at = FLOOR.east(3);
        MechaEntity m = machine(h, MechaFrame.COLOSSUS, at, p.getUUID(), MechaModule.HYDRAULIC_LEGS);
        board(h, p, m);
        // a wall three blocks behind (north: it faces south), at knee height, where the outriggers would lie
        for (int dx = -1; dx <= 1; dx++) h.setBlock(at.offset(dx, 0, -3), Blocks.STONE);
        h.runAfterDelay(2, () -> {
            press(p);
            h.assertTrue(count(p, "mecha.siege.room") == 1 && kind(m) == Siege.REFUSED, "a wall behind was not refused: " + Refusals.delivered(p));
            h.assertTrue(main(m, now(h) + 9).equals("siege_refused"), "no refused clip");
            for (int dx = -1; dx <= 1; dx++) h.setBlock(at.offset(dx, 0, -3), Blocks.AIR);
        });
        h.runAfterDelay(40, () -> {
            press(p);
            h.assertTrue(kind(m) == Siege.ENTER, "open ground behind did not let it kneel: " + Refusals.delivered(p));
            done(h, p);
        });
    }

    /** In Siege the forearms are planted: an arm button is answered in text and plays no arm verb; the shoulder weapon fires. */
    @GameTest(template = "platform", timeoutTicks = 80)
    public static void siegedArmsWaitAndShouldersFire(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        BlockPos at = FLOOR.east(3);
        MechaEntity m = machine(h, MechaFrame.COLOSSUS, at, p.getUUID(), MechaModule.HYDRAULIC_LEGS, MechaModule.DRILL, MechaModule.DRILL,
                MechaModule.ARC_CANNON);
        board(h, p, m);
        h.runAfterDelay(2, () -> press(p));
        h.runAfterDelay(2 + Siege.IN_TICKS + 2, () -> {
            h.assertTrue(Siege.locked(m), "not sieged");
            // the left button: the left arm (a drill) and the left shoulder (an arc cannon) answer it
            int battery = m.battery();
            // the left button, through the arm input payload's handler
            Network.arms(p, 1);
            m.tick();
            h.assertTrue(count(p, "mecha.siege.arms_planted") == 1, "an arm press in Siege said nothing: " + Refusals.delivered(p));
            h.assertTrue(HopperAnimation.arm(m, new HopperView(), 0, now(h)) == null, "an arm verb plays over the planted forearm");
            // the arc cannon's shot costs 6 kFE (the drills, planted, cost nothing)
            h.assertTrue(battery - m.battery() >= 6000, "the shoulder weapon did not fire in Siege: " + (battery - m.battery()));
            Network.arms(p, 0);
            m.tick();
            done(h, p);
        });
    }

    /**
     * A shoulder weapon on each cradle fires through the real arm input path: each with the button of its side, its own
     * bit in the synced arm state; a press that can't fire (cooling down) is answered.
     */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void shoulderWeaponsFireFromTheirButtons(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        BlockPos at = FLOOR.east(3);
        // arms empty, an arc cannon on each cradle (arm slots 3 and 4)
        MechaEntity m = machine(h, MechaFrame.COLOSSUS, at, p.getUUID(), MechaModule.HYDRAULIC_LEGS);
        int first = MechaFrame.COLOSSUS.firstIndex(MechaModule.Slot.ARM);
        List<ItemStack> mods = new ArrayList<>(m.modules());
        mods.set(first + 2, module(MechaModule.ARC_CANNON));
        mods.set(first + 3, module(MechaModule.ARC_CANNON));
        m.setModules(mods);
        board(h, p, m);
        h.runAfterDelay(2, () -> {
            h.assertTrue(m.arm(2) == MechaModule.ARC_CANNON && m.arm(3) == MechaModule.ARC_CANNON, "no shoulder weapons");
            int battery = m.battery();
            Network.arms(p, 1);
            m.tick();
            h.assertTrue(battery - m.battery() >= 6000 && battery - m.battery() < 12000, "the left button did not fire just the left shoulder: " + (battery - m.battery()));
            h.assertTrue((m.armsShown() & 4) != 0 && (m.armsShown() & 8) == 0, "the left shoulder's own bit is not in the synced state: " + m.armsShown());
            h.assertTrue(count(p, "mecha.arm.empty") == 0, "an empty arm under a fitted shoulder said the button has nothing behind it");
            Network.arms(p, 0);
            m.tick();
            int before = m.battery();
            Network.arms(p, 2);
            m.tick();
            h.assertTrue(before - m.battery() >= 6000 && (m.armsShown() & 8) != 0, "the right button did not fire the right shoulder");
            Network.arms(p, 0);
            m.tick();
            // pressed again at once: the cannon is cooling, and says so
            Network.arms(p, 2);
            m.tick();
            h.assertTrue(count(p, "mecha.arm.cooling") == 1, "a press during the cooldown was not answered: " + Refusals.delivered(p));
            Network.arms(p, 0);
            m.tick();
            done(h, p);
        });
    }

    // --- the Executive -------------------------------------------------------------------------------------------

    /** The drive key plays the jingle (20 kFE), it stands still for it, a press during it says so; it never lifts. */
    @GameTest(template = "platform", timeoutTicks = 100)
    public static void theExecutivePlaysItsJingleAndNeverLifts(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        BlockPos at = FLOOR.east(3);
        MechaEntity m = machine(h, MechaFrame.EXECUTIVE, at, p.getUUID(), MechaModule.HYDRAULIC_LEGS);
        board(h, p, m);
        Cow cow = h.spawn(EntityType.COW, at.south(2));
        h.runAfterDelay(2, () -> {
            int battery = m.battery();
            long start = now(h);
            press(p);
            h.assertTrue(kind(m) == Executive.JINGLE && m.verbAt() / 8 == start, "the drive key did not play the jingle");
            h.assertTrue(battery - m.battery() >= Executive.JINGLE_FE, "the jingle cost nothing");
            h.assertTrue(m.heldId() == -1 && m.liftAt() == -1, "an Executive lifted");
            h.assertTrue(main(m, start + 10).equals("drive_jingle"), "no jingle clip");
            p.zza = 1;
            h.assertTrue(MechaLogic.riddenInput(m, p).equals(Vec3.ZERO), "it walks through its jingle");
            p.zza = 0;
            press(p);
            h.assertTrue(count(p, "mecha.jingle.busy") == 1, "a press during the jingle said nothing");
        });
        h.runAfterDelay(2 + Executive.JINGLE_TICKS + 2, () -> {
            p.zza = 1;
            h.assertTrue(MechaLogic.riddenInput(m, p).z > 0, "the controls stay locked after the jingle");
            p.zza = 0;
            String idle = main(m, now(h) + 200);
            h.assertTrue(idle.equals("idle") || idle.equals("idle_tic"), "not idle after the jingle: " + idle);
            cow.discard();
            done(h, p);
        });
    }

    private static ItemStack cassette(Gene... genes) {
        ItemStack stack = new ItemStack(ModRegistry.SERUM_CASSETTE.get());
        new Serum(java.util.Arrays.stream(genes).map(g -> new Serum.Entry(g, 1f)).toList(), 100).writeTo(stack);
        return stack;
    }

    /**
     * The vials tell the truth: a serum clicked into a port fills its vial by its load and colours it by its genes; an
     * empty port shows nothing; a fired gene's port rests (dims) while its gene cools down.
     */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void theVialsShowTheirSerums(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        BlockPos at = FLOOR.east(3);
        MechaEntity m = machine(h, MechaFrame.EXECUTIVE, at, p.getUUID(), MechaModule.HYDRAULIC_LEGS);
        h.assertTrue(Executive.fill(m, 0) == 0, "an empty port shows serum");
        // a real click with the cassette in hand
        p.setItemInHand(InteractionHand.MAIN_HAND, cassette(Gene.ENDER_BLINK));
        p.interactOn(m, InteractionHand.MAIN_HAND);
        h.assertTrue(Genetics.genes(m).serums.size() == 1, "the click did not fill a port");
        h.assertTrue(Math.abs(Executive.fill(m, 0) - Gene.ENDER_BLINK.load / 8f) < 1e-4, "port 0 is not filled by its load: " + Executive.fill(m, 0));
        h.assertTrue(Executive.fill(m, 1) == 0, "port 1 shows serum it lacks");
        p.setItemInHand(InteractionHand.MAIN_HAND, cassette(Gene.GUARDIAN_EYE, Gene.VENOM_SAC));
        p.interactOn(m, InteractionHand.MAIN_HAND);
        h.assertTrue(Executive.colour(m, 0) != Executive.colour(m, 1), "a movement serum and an offence serum look the same");
        board(h, p, m);
        h.runAfterDelay(2, () -> {
            // fire Ender Blink (R) at the floor ahead: its port rests
            p.setXRot(60);
            Network.action(p, new Network.JarAction(Network.Action.ABILITY));
        });
        h.runAfterDelay(4, () -> {
            h.assertTrue(Executive.resting(m, 0) && !Executive.resting(m, 1), "the fired gene's port does not rest: " + m.portsResting());
            done(h, p);
        });
    }

    // --- both ---------------------------------------------------------------------------------------------------

    /** Every slot, tracked bone, lamp, locator and clip of both frames exists in the shipped model and clip file. */
    @GameTest(template = "platform")
    public static void bothFramesResolveToTheirModels(GameTestHelper h) {
        for (MechaFrame frame : List.of(MechaFrame.COLOSSUS, MechaFrame.EXECUTIVE)) {
            MechaRig rig = MechaRig.of(frame);
            h.assertTrue(frame.animated() && frame.renderScale == 1f, frame + " is not animated at true size");
            Set<String> bones = names("geo", frame.id(), "minecraft:geometry");
            for (int i = 0; i < frame.totalSlots(); i++) {
                for (String s : HopperSockets.sockets(frame, i)) {
                    h.assertTrue(bones.contains(s), frame + " slot " + i + " socket " + s + " is not in its model");
                    h.assertTrue(HopperSockets.slotOf(frame, s) == i, frame + " socket " + s + " maps back to the wrong slot");
                }
            }
            h.assertTrue(HopperSockets.sockets(frame, frame.firstIndex(MechaModule.Slot.ARM) + 2).contains("socket_arm_2")
                    && HopperSockets.sockets(frame, frame.firstIndex(MechaModule.Slot.ARM) + 3).contains("socket_arm_3"), "shoulder slots not on socket_arm_2/3");
            for (String b : rig.tracked) h.assertTrue(bones.contains(b), frame + " tracked bone " + b + " missing");
            for (String b : rig.lamps) h.assertTrue(bones.contains(b), frame + " lamp bone " + b + " missing");
            for (var l : rig.locators.values()) h.assertTrue(bones.contains(l.bone()), frame + " locator bone " + l.bone() + " missing");
            Set<String> clips = names("animations", frame.id(), "animations");
            for (String c : List.of("parked", "board", "boot", "boot_legs", "shutdown", "idle", "idle_tic", "walk_c", "limp", "turn_l", "turn_r",
                    "jump", "fall", "land", "land_heavy", "vent", "hurt", "eject", "destroyed", "build", "dry_fire_l", "dry_fire_r")) {
                h.assertTrue(clips.contains(c) && rig.has(c), frame + " lacks clip " + c);
                h.assertTrue(Math.abs(rig.clip(c).length() - length(frame.id(), c)) <= 1, frame + " clip " + c + " has the wrong length in the rig");
            }
            h.assertFalse(clips.contains("walk") || clips.contains("walk_b"), frame + " still ships the legacy walks");
        }
        Set<String> colossus = names("animations", "colossus", "animations");
        for (String c : List.of("siege_in", "siege", "siege_out", "siege_refused")) h.assertTrue(colossus.contains(c), "the Colossus lacks " + c);
        h.assertFalse(colossus.contains("grab") || colossus.contains("carry_walk_c"), "the Colossus ships lifting clips");
        h.assertTrue(names("animations", "executive", "animations").contains("drive_jingle"), "the Executive lacks its jingle");
        // the pilot looks out from the jar's eyes on these two; the other animated frames keep the player's eye height
        h.assertTrue(MechaRig.COLOSSUS.firstPersonEye == MechaRig.JAR_EYES && MechaRig.EXECUTIVE.firstPersonEye == MechaRig.JAR_EYES,
                "the heavy frames' first-person eye is not the jar's");
        h.assertTrue(Double.isNaN(MechaRig.HOPPER.firstPersonEye) && Double.isNaN(MechaRig.BULWARK.firstPersonEye)
                && Double.isNaN(MechaRig.WHEELHOUSE.firstPersonEye), "another frame's first-person eye moved");
        for (int n = 0; n < 4; n++) h.assertTrue(names("geo", "executive", "minecraft:geometry").contains("gene_port_" + n), "no gene_port_" + n);
        h.succeed();
    }

    private static JsonObject json(String kind, String model) {
        String path = kind.equals("geo") ? "/assets/headinajar/geo/entity/mecha/" + model + ".geo.json" : "/assets/headinajar/animations/entity/mecha/" + model + ".animation.json";
        var in = HeavyFrameGameTests.class.getResourceAsStream(path);
        if (in == null) throw new AssertionError("no " + path);
        return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static Set<String> names(String kind, String model, String key) {
        Set<String> out = new HashSet<>();
        JsonObject j = json(kind, model);
        if (kind.equals("geo")) {
            for (var b : j.getAsJsonArray(key).get(0).getAsJsonObject().getAsJsonArray("bones")) out.add(b.getAsJsonObject().get("name").getAsString());
        } else {
            out.addAll(j.getAsJsonObject(key).keySet());
        }
        return out;
    }

    private static double length(String model, String clip) {
        return json("animations", model).getAsJsonObject("animations").getAsJsonObject(clip).get("animation_length").getAsDouble() * 20;
    }

    /** A Colossus and an Executive as the previous version saved them load with their state and save no new key. */
    @GameTest(template = "platform")
    public static void heavyFramesSavedBeforeLoad(GameTestHelper h) {
        for (MechaFrame frame : List.of(MechaFrame.COLOSSUS, MechaFrame.EXECUTIVE)) {
            UUID owner = UUID.randomUUID();
            CompoundTag tag = new CompoundTag();
            tag.putString("id", "headinajar:" + frame.id());
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
            int shoulder = frame.firstIndex(MechaModule.Slot.ARM) + 3;
            for (int i = 0; i < frame.totalSlots(); i++) {
                ItemStack stack = i == 0 ? module(MechaModule.HYDRAULIC_LEGS) : i == shoulder ? module(MechaModule.ARC_CANNON) : ItemStack.EMPTY;
                modules.add(stack.saveOptional(h.getLevel().registryAccess()));
            }
            tag.put("Modules", modules);
            tag.put("Cargo", new ListTag());
            tag.putInt("Battery", 600_000);
            tag.putFloat("Heat", 120);
            tag.putUUID("Owner", owner);
            var loaded = EntityType.loadEntityRecursive(tag, h.getLevel(), e -> e);
            h.assertTrue(loaded instanceof MechaEntity, "the old " + frame + " did not load: " + loaded);
            MechaEntity m = (MechaEntity) loaded;
            h.assertTrue(m.frame() == frame && frame.animated(), "not an animated " + frame);
            h.assertTrue(m.has(MechaModule.HYDRAULIC_LEGS) && m.arm(3) == MechaModule.ARC_CANNON, frame + " modules lost");
            h.assertTrue(m.battery() == 600_000 && Math.abs(m.heat() - 120) < 0.01 && owner.equals(m.owner()) && Math.abs(m.getHealth() - 97) < 0.01,
                    frame + " battery, heat, owner or health lost");
            h.assertTrue(m.builtAt() == -1 && m.boardedAt() == -1 && m.verbAt() == -1 && m.portsResting() == 0, "an old " + frame + " loads mid-sequence");
            CompoundTag saved = new CompoundTag();
            m.addAdditionalSaveData(saved);
            for (String key : List.of("Built", "Boarded", "Siege", "Verb", "Ports", "Jingle")) h.assertFalse(saved.contains(key), "new saved key " + key);
        }
        h.succeed();
    }
}
