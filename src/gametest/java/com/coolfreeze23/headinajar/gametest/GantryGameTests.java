package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.AssemblyGantryBlock;
import com.coolfreeze23.headinajar.block.AssemblyGantryBlockEntity;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.item.MechaModuleItem;
import com.coolfreeze23.headinajar.machine.GantryPlan;
import com.coolfreeze23.headinajar.machine.MachineLamp;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.menu.AssemblyGantryMenu;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Assembly Gantry on the kit: every frame is built on its turntable through the Assemble button's packet; the frame
 * stands on the deck once the arm has stood off, turns with the turntable by the same function the client draws, and
 * ends facing whoever pressed Assemble; boarding cuts the build short; a take-apart is the arm's 60 ticks; a repair is
 * instant and then a weld pass; every refusal says why; an old Gantry loads with what it held.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class GantryGameTests {
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

    private static long now(GameTestHelper h) {
        return h.getLevel().getGameTime();
    }

    private static ItemStack module(MechaModule m) {
        ItemStack stack = new ItemStack(ModRegistry.mechaModule(m));
        MechaModuleItem.setSlider(stack, 50);
        return stack;
    }

    private static AssemblyGantryBlockEntity gantry(GameTestHelper h, int energy) {
        h.setBlock(FLOOR, ModRegistry.ASSEMBLY_GANTRY.get());
        var gantry = (AssemblyGantryBlockEntity) h.getBlockEntity(FLOOR);
        gantry.energy().set(energy);
        return gantry;
    }

    /** A frame in the slot with walking legs where it needs them. */
    private static void stock(AssemblyGantryBlockEntity gantry, MechaFrame frame) {
        gantry.items().setStackInSlot(AssemblyGantryBlockEntity.SLOT_FRAME, new ItemStack(ModRegistry.frameItem(frame)));
        if (frame.slots(MechaModule.Slot.LEGS) > 0) {
            gantry.items().setStackInSlot(AssemblyGantryBlockEntity.FIRST_MODULE + frame.firstIndex(MechaModule.Slot.LEGS), module(MechaModule.HYDRAULIC_LEGS));
        }
    }

    /** The player opens the Gantry's screen (the menu its click makes) and presses a button: the button packet's own handler. */
    private static boolean press(ServerPlayer p, AssemblyGantryBlockEntity gantry, int button) {
        if (!(p.containerMenu instanceof AssemblyGantryMenu)) {
            p.containerMenu = gantry.createMenu(7, p.getInventory(), p);
        }
        String before = Refusals.lastDelivered(p);
        int count = Refusals.delivered(p).size();
        p.connection.handleContainerButtonClick(new ServerboundContainerButtonClickPacket(p.containerMenu.containerId, button));
        return Refusals.delivered(p).size() == count && java.util.Objects.equals(before, Refusals.lastDelivered(p));
    }

    private static double yawGap(float a, float b) {
        return Math.abs(Mth.wrapDegrees(a - b));
    }

    // --- every frame ---------------------------------------------------------------------------------------------

    /**
     * Assemble: the arm stands off; nothing on the deck until the plan's lead, then the frame stands at y + 1.25 facing
     * the front, its build clock started then; through the build its yaw is the turntable's (the plan's function) every
     * tick; at the clip's end it faces the presser and the arm is free again.
     */
    private static void builds(GameTestHelper h, MechaFrame frame) {
        ServerPlayer p = player(h, FLOOR.east(3));
        var gantry = gantry(h, 100_000);
        stock(gantry, frame);
        GantryPlan.Frame plan = GantryPlan.of(frame);
        h.assertTrue(plan != null && plan.contacts().size() == frame.totalSlots(), frame + ": the plan does not weld every slot");
        h.assertTrue(press(p, gantry, AssemblyGantryMenu.ASSEMBLE), frame + ": Assemble was refused: " + Refusals.lastDelivered(p));
        long pressed = now(h);
        h.assertTrue(gantry.job() == AssemblyGantryBlockEntity.JOB_BUILD && gantry.jobFrame() == frame, frame + ": no build job");
        h.assertTrue(gantry.items().getStackInSlot(AssemblyGantryBlockEntity.SLOT_FRAME).isEmpty(), frame + ": the frame was not taken");
        h.assertFalse(press(p, gantry, AssemblyGantryMenu.ASSEMBLE), frame + ": a second Assemble was taken during the build");
        h.assertValueEqual(Refusals.lastDelivered(p), "gantry.busy", frame + ": busy line");
        h.runAfterDelay(GantryPlan.LEAD - 2, () -> h.assertTrue(h.getEntities(ModRegistry.mechaType(frame)).isEmpty(), frame + ": the frame stood up before the arm stood off"));
        h.runAfterDelay(GantryPlan.LEAD + 1, () -> {
            MechaEntity m = h.getEntities(ModRegistry.mechaType(frame)).get(0);
            Vec3 deck = gantry.deck();
            h.assertTrue(Math.abs(m.getY() - deck.y) < 1e-3 && Math.abs(m.getX() - deck.x) < 1e-3 && Math.abs(m.getZ() - deck.z) < 1e-3,
                    frame + ": not on the deck at " + m.position() + " (deck " + deck + ")");
            h.assertTrue(Math.abs(deck.y - (h.absolutePos(FLOOR).getY() + AssemblyGantryBlock.DECK)) < 1e-6, frame + ": the deck is not at 20 px");
            h.assertTrue(m.builtAt() == pressed + GantryPlan.LEAD, frame + ": the build clock " + m.builtAt() + " against the press " + pressed);
            h.assertTrue(p.getUUID().equals(m.owner()), frame + ": no owner");
        });
        // every 10 ticks of the build the server's yaw is the plan's turntable
        for (int tau = GantryPlan.LEAD + 5; tau < plan.length(); tau += 10) {
            final int at = tau;
            h.runAfterDelay(at, () -> {
                MechaEntity m = h.getEntities(ModRegistry.mechaType(frame)).get(0);
                // this tick's or the last (whichever ran first, the test or the Gantry)
                float want = gantry.yawFor(gantry.turntable(now(h))), was = gantry.yawFor(gantry.turntable(now(h) - 1));
                h.assertTrue(Math.min(yawGap(m.getYRot(), want), yawGap(m.getYRot(), was)) < 0.5, frame + " at tau " + at + ": yaw " + m.getYRot() + ", the turntable " + want);
            });
        }
        h.runAfterDelay(plan.length() + 2, () -> {
            MechaEntity m = h.getEntities(ModRegistry.mechaType(frame)).get(0);
            h.assertTrue(gantry.job() == AssemblyGantryBlockEntity.JOB_NONE, frame + ": the build job never ended");
            double dx = p.getX() - m.getX(), dz = p.getZ() - m.getZ();
            float toPlayer = (float) Math.toDegrees(Math.atan2(-dx, dz));
            h.assertTrue(yawGap(m.getYRot(), toPlayer) < 1, frame + ": handed over facing " + m.getYRot() + ", the presser is at " + toPlayer);
            h.assertTrue(m.builtAt() == -1, frame + ": the frame's own build never ended");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 260)
    public static void buildsAHopper(GameTestHelper h) {
        builds(h, MechaFrame.HOPPER);
    }

    @GameTest(template = "platform", timeoutTicks = 260)
    public static void buildsABulwark(GameTestHelper h) {
        builds(h, MechaFrame.BULWARK);
    }

    @GameTest(template = "tall_platform", timeoutTicks = 380)
    public static void buildsAColossus(GameTestHelper h) {
        builds(h, MechaFrame.COLOSSUS);
    }

    @GameTest(template = "tall_platform", timeoutTicks = 300)
    public static void buildsAnExecutive(GameTestHelper h) {
        builds(h, MechaFrame.EXECUTIVE);
    }

    @GameTest(template = "platform", timeoutTicks = 260)
    public static void buildsAWheelhouse(GameTestHelper h) {
        builds(h, MechaFrame.WHEELHOUSE);
    }

    @GameTest(template = "platform", timeoutTicks = 260)
    public static void buildsASkitter(GameTestHelper h) {
        builds(h, MechaFrame.SKITTER);
    }

    @GameTest(template = "platform", timeoutTicks = 260)
    public static void buildsAKite(GameTestHelper h) {
        builds(h, MechaFrame.KITE);
    }

    @GameTest(template = "platform", timeoutTicks = 280)
    public static void buildsAChimera(GameTestHelper h) {
        builds(h, MechaFrame.CHIMERA);
    }

    /** Every contact of every frame's plan is at its seat tick or after it, inside the clip, at a point the plan names. */
    @GameTest(template = "platform")
    public static void everyContactIsPlanned(GameTestHelper h) {
        for (MechaFrame frame : MechaFrame.values()) {
            GantryPlan.Frame plan = GantryPlan.of(frame);
            h.assertTrue(plan != null, frame + " has no plan");
            h.assertTrue(plan.presentTo() <= plan.buildLength() - 1 && plan.length() >= plan.buildLength() + GantryPlan.LEAD,
                    frame + ": the clip does not cover the frame's build");
            for (GantryPlan.Contact c : plan.contacts()) {
                h.assertTrue(c.contact() >= c.seat() && c.contact() < plan.length() - GantryPlan.LEAD, frame + " " + c.slot() + ": contact " + c.contact());
                String arc = "arc_" + frame.id() + "_" + c.slot();
                h.assertTrue(c.reach() ? GantryPlan.locator(c.locator()) != null : GantryPlan.arc(arc) != null, frame + " " + c.slot() + ": no point " + (c.reach() ? c.locator() : arc));
            }
        }
        h.succeed();
    }

    // --- boarding, taking apart, repair ---------------------------------------------------------------------------

    /** A jar's real click boards the frame mid-build: the build is skipped, the job ends, the arm folds home from there. */
    @GameTest(template = "platform", timeoutTicks = 120)
    public static void boardingCutsTheBuildShort(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(3));
        var gantry = gantry(h, 100_000);
        stock(gantry, MechaFrame.HOPPER);
        h.assertTrue(press(p, gantry, AssemblyGantryMenu.ASSEMBLE), "Assemble was refused");
        h.runAfterDelay(GantryPlan.LEAD + 30, () -> {
            MechaEntity m = gantry.onPad();
            if (FormActions.state(p).form() == Form.BODY) FormActions.detach(p, 3);
            p.interactOn(m, InteractionHand.MAIN_HAND);
            h.assertTrue(p.getVehicle() == m, "the click did not board");
            long at = now(h);
            h.runAfterDelay(1, () -> {
                h.assertTrue(gantry.job() == AssemblyGantryBlockEntity.JOB_NONE && m.builtAt() == -1, "the build was not cut short");
                h.assertTrue(gantry.foldAt() >= at && gantry.foldAt() <= at + 1, "the arm was not sent home: " + gantry.foldAt());
                h.assertTrue(gantry.loaded(), "the pad does not know a frame stands on it");
                done(h, p);
            });
        });
    }

    /** Disassemble: refused while busy; the arm's 60 ticks; then the frame and modules in the slots and the charge back. */
    @GameTest(template = "platform", timeoutTicks = 140)
    public static void aTakeApartIsTheArmsSixtyTicks(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(3));
        var gantry = gantry(h, 0);
        MechaEntity m = MechaLineGameTests.mecha(h, MechaFrame.BULWARK, FLOOR.above(), p.getUUID(), MechaModule.HYDRAULIC_LEGS, MechaModule.DRILL);
        int battery = m.battery();
        h.runAfterDelay(2, () -> {
            h.assertTrue(Math.abs(m.getY() - gantry.deck().y) < 1e-3, "a frame stood on the pad is not lifted onto the deck");
            h.assertTrue(press(p, gantry, AssemblyGantryMenu.DISASSEMBLE), "Disassemble was refused: " + Refusals.lastDelivered(p));
            h.assertFalse(press(p, gantry, AssemblyGantryMenu.REPAIR), "Repair was taken during a take-apart");
            h.assertValueEqual(Refusals.lastDelivered(p), "gantry.busy", "busy line");
            int length = GantryPlan.of(MechaFrame.BULWARK).disassemble();
            h.runAfterDelay(length - 5, () -> h.assertTrue(m.isAlive() && gantry.frame() == null, "taken apart before the arm was done"));
            h.runAfterDelay(length + 2, () -> {
                h.assertTrue(m.isRemoved(), "the frame is still on the pad");
                h.assertTrue(gantry.frame() == MechaFrame.BULWARK, "the frame is not in its slot");
                boolean drill = false;
                for (int i = 0; i < AssemblyGantryBlockEntity.MODULE_SLOTS; i++) {
                    drill |= gantry.items().getStackInSlot(AssemblyGantryBlockEntity.FIRST_MODULE + i).is(ModRegistry.mechaModule(MechaModule.DRILL));
                }
                h.assertTrue(drill, "the modules are not back in the slots");
                h.assertTrue(gantry.energy().getEnergyStored() == Math.min(100_000, battery), "the charge did not come back: " + gantry.energy().getEnergyStored());
                h.assertTrue(gantry.job() == AssemblyGantryBlockEntity.JOB_NONE, "the job never ended");
                done(h, p);
            });
        });
    }

    /**
     * Repair: the plating mends the hull and the battery is topped up at the press (the logic is instant); then the arm's
     * weld pass turns the frame and brings it back to where it stood. A full frame: nothing to repair. A hurt one with no
     * plating and a full battery: no plating.
     */
    @GameTest(template = "platform", timeoutTicks = 140)
    public static void aRepairIsInstantThenAWeldPass(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(3));
        var gantry = gantry(h, 100_000);
        MechaEntity m = MechaLineGameTests.mecha(h, MechaFrame.HOPPER, FLOOR.above(), p.getUUID(), MechaModule.HYDRAULIC_LEGS);
        h.runAfterDelay(2, () -> {
            // full: nothing to do
            h.assertFalse(press(p, gantry, AssemblyGantryMenu.REPAIR), "a full frame was repaired");
            h.assertValueEqual(Refusals.lastDelivered(p), "gantry.nothing_to_repair", "nothing-to-repair line");
            // hurt, a full battery, no plating
            m.setHealth(m.getMaxHealth() - 30);
            h.assertFalse(press(p, gantry, AssemblyGantryMenu.REPAIR), "a hurt frame was repaired with no plating");
            h.assertValueEqual(Refusals.lastDelivered(p), "gantry.no_plating", "no-plating line");
            h.assertTrue(gantry.job() == AssemblyGantryBlockEntity.JOB_NONE, "a refused repair started the arm");
            // hurt, half charged, two plates
            m.setBattery(m.batteryCapacity() / 2);
            gantry.items().setStackInSlot(AssemblyGantryBlockEntity.SLOT_PLATING, new ItemStack(ModRegistry.MECHA_PLATING.get(), 2));
            float yaw = m.getYRot();
            h.assertTrue(press(p, gantry, AssemblyGantryMenu.REPAIR), "Repair was refused: " + Refusals.lastDelivered(p));
            h.assertTrue(m.getHealth() >= m.getMaxHealth() - 1e-3 && m.battery() == m.batteryCapacity(), "the repair was not done at the press");
            h.assertTrue(gantry.items().getStackInSlot(AssemblyGantryBlockEntity.SLOT_PLATING).getCount() < 2, "no plating spent");
            h.assertTrue(gantry.job() == AssemblyGantryBlockEntity.JOB_REPAIR, "no weld pass");
            int length = GantryPlan.of(MechaFrame.HOPPER).repair();
            h.runAfterDelay(length / 2, () -> h.assertTrue(yawGap(m.getYRot(), yaw) > 20, "the turntable did not turn the frame for the pass"));
            h.runAfterDelay(length + 2, () -> {
                h.assertTrue(gantry.job() == AssemblyGantryBlockEntity.JOB_NONE, "the pass never ended");
                h.assertTrue(yawGap(m.getYRot(), yaw) < 1, "the frame was not turned back: " + m.getYRot() + " against " + yaw);
                done(h, p);
            });
        });
    }

    // --- refusals --------------------------------------------------------------------------------------------------

    /** Every no says why, through the button's packet: no frame, no legs, no power, the pad taken, the space over it blocked. */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void everyRefusalSaysWhy(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(3));
        var gantry = gantry(h, 100_000);
        h.assertFalse(press(p, gantry, AssemblyGantryMenu.ASSEMBLE), "built with no frame");
        h.assertValueEqual(Refusals.lastDelivered(p), "gantry.no_frame", "no-frame line");
        gantry.items().setStackInSlot(AssemblyGantryBlockEntity.SLOT_FRAME, new ItemStack(ModRegistry.frameItem(MechaFrame.HOPPER)));
        h.assertFalse(press(p, gantry, AssemblyGantryMenu.ASSEMBLE), "built with no legs");
        h.assertValueEqual(Refusals.lastDelivered(p), "gantry.no_legs", "no-legs line");
        stock(gantry, MechaFrame.HOPPER);
        gantry.energy().set(5_000);
        h.runAfterDelay(12, () -> {
            h.assertFalse(press(p, gantry, AssemblyGantryMenu.ASSEMBLE), "built with no power");
            h.assertValueEqual(Refusals.lastDelivered(p), "gantry.no_power", "no-power line");
            gantry.energy().set(100_000);
            h.setBlock(FLOOR.above(2), Blocks.STONE);
            h.runAfterDelay(12, () -> {
                h.assertFalse(press(p, gantry, AssemblyGantryMenu.ASSEMBLE), "built into stone");
                h.assertValueEqual(Refusals.lastDelivered(p), "gantry.pad_blocked", "blocked line");
                h.assertTrue(gantry.frame() == MechaFrame.HOPPER, "a refused build took the frame");
                h.assertTrue(h.getBlockState(FLOOR).getValue(MachineLamp.LAMP) == MachineLamp.FAULT, "the lamp did not go red");
                h.setBlock(FLOOR.above(2), Blocks.AIR);
                MechaLineGameTests.mecha(h, MechaFrame.HOPPER, FLOOR.above(), p.getUUID(), MechaModule.HYDRAULIC_LEGS);
                h.runAfterDelay(12, () -> {
                    h.assertFalse(press(p, gantry, AssemblyGantryMenu.ASSEMBLE), "built over a parked frame");
                    h.assertValueEqual(Refusals.lastDelivered(p), "gantry.pad_busy", "pad-busy line");
                    done(h, p);
                });
            });
        });
    }

    // --- the lamp, the pad, the update tag ----------------------------------------------------------------------

    /** A frame on the pad with room in its battery charges (the lamp works, the clients are told); full, it waits. */
    @GameTest(template = "platform", timeoutTicks = 80)
    public static void thePadChargesAndTheLampSaysSo(GameTestHelper h) {
        var gantry = gantry(h, 100_000);
        MechaEntity m = MechaLineGameTests.mecha(h, MechaFrame.HOPPER, FLOOR.above(), null, MechaModule.HYDRAULIC_LEGS);
        m.setBattery(m.batteryCapacity() - 40_000);
        h.runAfterDelay(3, () -> {
            h.assertTrue(gantry.charging() && gantry.loaded() && m.battery() > 0, "the pad is not charging");
            h.assertTrue(gantry.getUpdateTag(h.getLevel().registryAccess()).getBoolean("charging"), "the clients are not told it charges");
            h.assertTrue(h.getBlockState(FLOOR).getValue(MachineLamp.LAMP) == MachineLamp.WORK, "the lamp does not say work");
            h.runAfterDelay(40, () -> {
                h.assertTrue(m.battery() >= m.batteryCapacity() - 200 && !gantry.charging(), "still charging a full battery: " + m.battery() + " of " + m.batteryCapacity());
                h.assertTrue(h.getBlockState(FLOOR).getValue(MachineLamp.LAMP) == MachineLamp.WAIT, "the lamp does not wait with a frame parked");
                h.succeed();
            });
        });
    }

    /** The update tag carries the job and the shown items, never the save. */
    @GameTest(template = "platform", timeoutTicks = 40)
    public static void theUpdateTagCarriesTheJobAndNoSave(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(3));
        var gantry = gantry(h, 100_000);
        stock(gantry, MechaFrame.KITE);
        CompoundTag idle = gantry.getUpdateTag(h.getLevel().registryAccess());
        h.assertTrue(!idle.contains("Items") && !idle.contains("Energy") && !idle.getString("f").isEmpty() && !idle.contains("job"), "the idle tag: " + idle);
        h.assertTrue(press(p, gantry, AssemblyGantryMenu.ASSEMBLE), "Assemble was refused: " + Refusals.lastDelivered(p));
        CompoundTag work = gantry.getUpdateTag(h.getLevel().registryAccess());
        h.assertTrue(work.getByte("job") == AssemblyGantryBlockEntity.JOB_BUILD && work.getLong("start") == now(h)
                && work.getByte("frame") == MechaFrame.KITE.ordinal() && work.contains("angle"), "the job's tag: " + work);
        done(h, p);
    }

    // --- old worlds ----------------------------------------------------------------------------------------------

    /**
     * A Gantry saved before the turntable (the 0.4.0 save: energy and items, thirteen module slots, no lamp) loads with
     * everything it held, faces as it did, and lights its lamp; a frame parked on it with its feet at y + 1 is lifted
     * onto the deck. A build saved between the press and the frame standing up stands at once on load.
     */
    @GameTest(template = "platform", timeoutTicks = 80)
    public static void anOldGantryLoadsWithEverythingItHeld(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.ASSEMBLY_GANTRY.get().defaultBlockState().setValue(com.coolfreeze23.headinajar.block.MachineBlock.FACING, net.minecraft.core.Direction.EAST));
        var gantry = (AssemblyGantryBlockEntity) h.getBlockEntity(FLOOR);
        var reg = h.getLevel().registryAccess();
        CompoundTag old = new CompoundTag();
        old.put("Energy", net.minecraft.nbt.IntTag.valueOf(40_000));
        net.neoforged.neoforge.items.ItemStackHandler items = new net.neoforged.neoforge.items.ItemStackHandler(13);
        items.setStackInSlot(AssemblyGantryBlockEntity.SLOT_FRAME, new ItemStack(ModRegistry.frameItem(MechaFrame.BULWARK)));
        items.setStackInSlot(AssemblyGantryBlockEntity.SLOT_PLATING, new ItemStack(ModRegistry.MECHA_PLATING.get(), 9));
        items.setStackInSlot(AssemblyGantryBlockEntity.FIRST_MODULE, module(MechaModule.HYDRAULIC_LEGS));
        old.put("Items", items.serializeNBT(reg));
        gantry.loadWithComponents(old, reg);
        h.assertTrue(gantry.energy().getEnergyStored() == 40_000, "the old charge was not kept: " + gantry.energy().getEnergyStored());
        h.assertTrue(gantry.frame() == MechaFrame.BULWARK && gantry.items().getStackInSlot(AssemblyGantryBlockEntity.SLOT_PLATING).getCount() == 9
                && gantry.items().getStackInSlot(AssemblyGantryBlockEntity.FIRST_MODULE).is(ModRegistry.mechaModule(MechaModule.HYDRAULIC_LEGS)), "the old contents were not kept");
        MechaEntity parked = MechaLineGameTests.mecha(h, MechaFrame.HOPPER, FLOOR.above(), null, MechaModule.HYDRAULIC_LEGS);
        // where an older Gantry's frames really rest: on its 6 px slab (and one already sunk to the block's base)
        parked.setPos(parked.getX(), h.absolutePos(FLOOR).getY() + 0.375, parked.getZ());
        h.runAfterDelay(20, () -> {
            h.assertTrue(h.getBlockState(FLOOR).getValue(com.coolfreeze23.headinajar.block.MachineBlock.FACING) == net.minecraft.core.Direction.EAST, "it turned");
            h.assertTrue(h.getBlockState(FLOOR).getValue(MachineLamp.LAMP) != MachineLamp.OFF, "the lamp is still off with 40 kFE and a frame parked");
            h.assertTrue(Math.abs(parked.getY() - gantry.deck().y) < 1e-3, "the parked frame was not lifted onto the deck: " + parked.getY());
            parked.discard();
            // a build saved in its first ticks
            ServerPlayer p = player(h, FLOOR.east(3));
            stock(gantry, MechaFrame.HOPPER);
            gantry.items().setStackInSlot(AssemblyGantryBlockEntity.SLOT_FRAME, new ItemStack(ModRegistry.frameItem(MechaFrame.HOPPER)));
            for (int i = 0; i < AssemblyGantryBlockEntity.MODULE_SLOTS; i++) gantry.items().setStackInSlot(AssemblyGantryBlockEntity.FIRST_MODULE + i, ItemStack.EMPTY);
            gantry.items().setStackInSlot(AssemblyGantryBlockEntity.FIRST_MODULE + MechaFrame.HOPPER.firstIndex(MechaModule.Slot.LEGS), module(MechaModule.HYDRAULIC_LEGS));
            gantry.energy().set(100_000);
            h.runAfterDelay(2, () -> {
                h.assertTrue(press(p, gantry, AssemblyGantryMenu.ASSEMBLE), "Assemble was refused: " + Refusals.lastDelivered(p));
                CompoundTag saved = gantry.saveWithFullMetadata(reg);
                h.assertTrue(saved.contains("PendingBuild"), "a build in its first ticks is not saved");
                h.setBlock(FLOOR, Blocks.AIR);
                h.setBlock(FLOOR, ModRegistry.ASSEMBLY_GANTRY.get());
                var fresh = (AssemblyGantryBlockEntity) h.getBlockEntity(FLOOR);
                fresh.loadWithComponents(saved, reg);
                h.runAfterDelay(3, () -> {
                    MechaEntity m = fresh.onPad();
                    h.assertTrue(m != null && m.frame() == MechaFrame.HOPPER && m.has(MechaModule.HYDRAULIC_LEGS), "the saved build did not stand on load");
                    done(h, p);
                });
            });
        });
    }
}
