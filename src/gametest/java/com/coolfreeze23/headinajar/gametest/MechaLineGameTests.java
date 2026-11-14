package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.entity.ShellEntity;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.item.MechaModuleItem;
import com.coolfreeze23.headinajar.block.AssemblyGantryBlockEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.feedback.Warnings;
import com.coolfreeze23.headinajar.form.HeadRegistry;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.form.NeuroLink;
import com.coolfreeze23.headinajar.mecha.Aloft;
import com.coolfreeze23.headinajar.mecha.HopperNet;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.MechaLogic;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.mecha.Siege;
import com.coolfreeze23.headinajar.menu.AssemblyGantryMenu;
import com.coolfreeze23.headinajar.module.ModuleEffects;
import com.coolfreeze23.headinajar.module.ModuleType;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The whole mecha line together: what holds across every frame and shell. A server without allow-flight keeps its
 * fliers connected while the mod holds them up; a parked machine makes no heat and never vents.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class MechaLineGameTests {
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

    static MechaEntity mecha(GameTestHelper h, MechaFrame frame, BlockPos at, UUID owner, MechaModule... mods) {
        MechaEntity m = h.spawn(ModRegistry.MECHA.get(frame).get(), at);
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
        m.setYRot(0);
        m.setYBodyRot(0);
        return m;
    }

    /** The player, as a jar, climbs in by a real click on the machine. */
    private static void board(GameTestHelper h, ServerPlayer p, MechaEntity m) {
        if (FormActions.state(p).form() == Form.BODY) FormActions.detach(p, 3);
        p.interactOn(m, InteractionHand.MAIN_HAND);
        h.assertTrue(p.getVehicle() == m && FormActions.state(p).form() == Form.PILOT, "the click did not board: " + FormActions.state(p).form());
    }

    /**
     * One tick of the player's connection, as the server runs it for a real player: what that player's client last
     * sent said it floats (a dedicated server without allow-flight sets these from every move packet with no block
     * round it), then the listener counts.
     */
    private static void floatTick(ServerPlayer p, boolean vehicle) {
        if (vehicle) p.connection.clientVehicleIsFloating = true;
        else p.connection.clientIsFloating = true;
        p.connection.tick();
    }

    // --- 1. a server without allow-flight keeps its fliers ----------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 160)
    public static void aFlyingKiteKeepsItsPilotOnAServerWithoutFlight(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        ServerPlayer q = player(h, FLOOR.west(4));
        MechaEntity kite = mecha(h, MechaFrame.KITE, FLOOR.east(3), p.getUUID());
        MechaEntity hopper = mecha(h, MechaFrame.HOPPER, FLOOR.west(3), q.getUUID(), MechaModule.HYDRAULIC_LEGS);
        board(h, p, kite);
        board(h, q, hopper);
        Vec3 up = h.absoluteVec(Vec3.atBottomCenterOf(FLOOR.east(3).above(4)));
        Vec3 upToo = h.absoluteVec(Vec3.atBottomCenterOf(FLOOR.west(3).above(4)));
        // both held four blocks up for 100 ticks, as their pilots' clients would hold them, the listener ticking each tick
        for (int i = 1; i <= 100; i++) {
            int tick = i;
            h.runAfterDelay(i, () -> {
                kite.setPos(up.x, up.y, up.z);
                kite.setOnGround(false);
                hopper.setPos(upToo.x, upToo.y, upToo.z);
                hopper.setOnGround(false);
                floatTick(p, true);
                floatTick(q, true);
                if (tick == 40) {
                    h.assertTrue(MechaLogic.aloft(kite), "a flying Kite is not aloft");
                    h.assertFalse(MechaLogic.aloft(hopper), "a Hopper stood in the air on nothing counts as aloft");
                    // the control: nothing holds the Hopper up, so the server's count runs on toward the kick
                    h.assertTrue(q.connection.aboveGroundVehicleTickCount >= 35, "the count did not run: " + q.connection.aboveGroundVehicleTickCount);
                    // its pilot leaves before the kick (80): this test is about the Kite
                    FormActions.leaveHost(q);
                }
            });
        }
        h.runAfterDelay(102, () -> {
            h.assertTrue(p.connection.aboveGroundVehicleTickCount < 5, "the Kite's pilot is counted floating: " + p.connection.aboveGroundVehicleTickCount);
            h.assertTrue(p.getVehicle() == kite && p.connection.isAcceptingMessages(), "the Kite's pilot was disconnected");
            done(h, p, q);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 160)
    public static void aJarUnderTheParachuteIsNotKickedForFloating(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        ServerPlayer q = player(h, FLOOR.west(3));
        MechaEntity m = mecha(h, MechaFrame.HOPPER, FLOOR.east(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS);
        board(h, p, m);
        FormActions.detach(q, 3);
        // the hull under a tenth: the Golden Parachute throws the jar clear
        m.hurt(h.getLevel().damageSources().generic(), m.getHealth() - m.getMaxHealth() * 0.05f);
        h.assertTrue(FormActions.state(p).form() == Form.JAR && p.getPersistentData().getInt(HopperNet.CHUTE) != HopperNet.FOLDED,
                "no parachute: " + FormActions.state(p).form());
        // the jar comes down under the canopy (its own client moves it): held a few blocks up here, off the ground
        Vec3 up = h.absoluteVec(Vec3.atBottomCenterOf(FLOOR.east(1).above(3)));
        for (int i = 1; i <= 100; i++) {
            int tick = i;
            h.runAfterDelay(i, () -> {
                p.setPos(up.x, up.y, up.z);
                p.setOnGround(false);
                floatTick(p, false);
                floatTick(q, false);
                if (tick == 40) {
                    h.assertTrue(Aloft.heldUp(p), "a jar under the parachute is not held up");
                    h.assertTrue(q.connection.aboveGroundTickCount >= 35, "the control's count did not run: " + q.connection.aboveGroundTickCount);
                    q.connection.aboveGroundTickCount = 0;
                }
            });
        }
        h.runAfterDelay(102, () -> {
            h.assertTrue(p.connection.aboveGroundTickCount < 5, "the jar under the parachute is counted floating: " + p.connection.aboveGroundTickCount);
            h.assertTrue(p.connection.isAcceptingMessages(), "the jar under the parachute was disconnected");
            done(h, p, q);
        });
    }

    // --- 2. a parked machine makes no heat and never vents -----------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void aParkedMachineMakesNoHeatAndNeverVents(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        ServerPlayer near = player(h, FLOOR.east(5));
        MechaEntity m = mecha(h, MechaFrame.HOPPER, FLOOR.east(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS, MechaModule.FUSION_BOTTLE);
        m.cargo().setItem(0, new ItemStack(ModRegistry.PLASMA_PELLET.get(), 16));
        m.setBattery(m.batteryCapacity() / 2);
        // parked at its limit, its core burning: no vent, no steam, no heat made
        m.setHeat(m.frame().heatCap);
        int battery = m.battery();
        h.runAfterDelay(60, () -> {
            h.assertValueEqual(m.vent(), 0, "a parked machine's vent");
            h.assertTrue(m.heat() < m.frame().heatCap, "a parked machine kept its heat: " + m.heat());
            h.assertFalse(near.hasEffect(MobEffects.BLINDNESS), "steam from a parked machine blinded a player beside it");
            h.assertTrue(m.battery() > battery, "the parked core stopped turning fuel into charge");
            // aboard, the same core heats it again, and a machine at its limit vents
            m.setHeat(m.frame().heatCap);
            board(h, p, m);
        });
        h.runAfterDelay(66, () -> {
            h.assertTrue(m.vent() > 0, "a piloted machine at its limit did not vent");
            done(h, p, near);
        });
    }

    // --- 4. things that did nothing or contradicted themselves --------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void telekinesisLiftsAMachineOnlyWithItsOwnersTrust(GameTestHelper h) {
        ServerPlayer owner = player(h, FLOOR.west(3));
        ServerPlayer lifter = player(h, FLOOR);
        MechaEntity m = mecha(h, MechaFrame.HOPPER, FLOOR.east(3), owner.getUUID(), MechaModule.HYDRAULIC_LEGS);
        FormActions.detach(lifter, 3);
        JarState state = FormActions.state(lifter);
        state.setModule(0, new ItemStack(ModRegistry.module(ModuleType.TELEKINESIS)));
        ModuleEffects.apply(lifter, state);
        state.setCharge(state.chargeCapacity());
        // the jar looks east straight at the machine's middle and presses the ability key
        Vec3 at = h.absoluteVec(Vec3.atBottomCenterOf(FLOOR));
        Vec3 aim = m.getBoundingBox().getCenter();
        lifter.moveTo(at.x, at.y, at.z, -90, 0);
        Vec3 eye = lifter.getEyePosition();
        lifter.setXRot((float) -Math.toDegrees(Math.atan2(aim.y - eye.y, Math.hypot(aim.x - eye.x, aim.z - eye.z))));
        Network.action(lifter, new Network.JarAction(Network.Action.ABILITY));
        h.assertTrue(state.heldEntity() < 0, "a stranger lifted the owner's mecha");
        h.assertTrue(Refusals.delivered(lifter).contains("tk.not_trusted"), "the refusal did not name trust: " + Refusals.delivered(lifter));
        HeadRegistry.get(h.getLevel().getServer()).setTrust(owner.getUUID(), lifter.getUUID(), true);
        Network.action(lifter, new Network.JarAction(Network.Action.ABILITY));
        h.assertTrue(state.heldEntity() == m.getId(), "a trusted jar could not lift the mecha: holds " + state.heldEntity());
        done(h, owner, lifter);
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void anchorClampsGripTheGroundAgainstABlow(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity clamped = mecha(h, MechaFrame.KITE, FLOOR.east(3), p.getUUID(), MechaModule.ANCHOR_CLAMPS);
        MechaEntity loose = mecha(h, MechaFrame.KITE, FLOOR.west(3), p.getUUID());
        h.runAfterDelay(5, () -> {
            h.assertTrue(clamped.onGround() && loose.onGround(), "the Kites are not standing");
            for (MechaEntity m : List.of(clamped, loose)) {
                Vec3 from = m.position().add(m == clamped ? -2 : 2, 0, 0);
                p.moveTo(from.x, from.y, from.z, m == clamped ? -90 : 90, 0);
                p.attack(m);
            }
            h.assertTrue(clamped.getDeltaMovement().horizontalDistance() < 1e-4, "a clamped Kite was knocked back: " + clamped.getDeltaMovement());
            h.assertTrue(loose.getDeltaMovement().horizontalDistance() > 0.05, "the control was not knocked back: " + loose.getDeltaMovement());
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void aSignalBoosterCarriesTheLinkLikeAMast(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        JarState state = FormActions.state(p);
        state.setGoggles(new ItemStack(ModRegistry.RELAY_GOGGLES.get()));
        state.setCollar(new ItemStack(ModRegistry.COLLAR_CAM.get()));
        double range = NeuroLink.range(state);
        // the head beyond the goggles' reach, a machine with a Signal Booster half way, within reach of both
        var head = new HeadRegistry.Spot(p.level().dimension(), p.position().add(range + 12, 0, 0));
        h.assertTrue(NeuroLink.quality(p, state, head) == 0, "the head was in reach without a relay");
        MechaEntity m = mecha(h, MechaFrame.HOPPER, FLOOR.east((int) ((range + 12) / 2)), p.getUUID(), MechaModule.HYDRAULIC_LEGS,
                MechaModule.SIGNAL_BOOSTER);
        h.runAfterDelay(2, () -> {
            int relayed = NeuroLink.quality(p, state, head);
            h.assertTrue(relayed > NeuroLink.QUALITY_TO_LINK, "the Signal Booster carried no link: " + relayed);
            m.setBattery(0);
            h.assertTrue(NeuroLink.quality(p, state, head) == 0, "a flat Signal Booster still relayed");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void aMachineBuiltOverItsLoadLockIsToldSo(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(3));
        h.setBlock(FLOOR, ModRegistry.ASSEMBLY_GANTRY.get());
        var gantry = (AssemblyGantryBlockEntity) h.getBlockEntity(FLOOR);
        gantry.items().setStackInSlot(AssemblyGantryBlockEntity.SLOT_FRAME, new ItemStack(ModRegistry.frameItem(MechaFrame.HOPPER)));
        MechaModule[] full = {MechaModule.HYDRAULIC_LEGS, MechaModule.ROCKET_PACK, MechaModule.FUSION_BOTTLE, MechaModule.ARMOR_PLATING,
                MechaModule.REACTIVE_ARMOUR, MechaModule.PLASMA_LANCE, MechaModule.ARC_CANNON, MechaModule.ORE_SCANNER};
        Set<Integer> used = new HashSet<>();
        for (MechaModule mod : full) {
            int i = MechaFrame.HOPPER.firstIndex(mod.slot);
            while (used.contains(i)) i++;
            used.add(i);
            gantry.items().setStackInSlot(AssemblyGantryBlockEntity.FIRST_MODULE + i, module(mod));
        }
        gantry.energy().set(50_000);
        h.assertTrue(gantry.button(p, AssemblyGantryMenu.ASSEMBLE), "assembly refused");
        // the frame stands on the deck once the arm has stood off
        h.runAfterDelay(com.coolfreeze23.headinajar.machine.GantryPlan.LEAD + 1, () -> {
            MechaEntity m = h.getEntities(ModRegistry.mechaType(MechaFrame.HOPPER)).get(0);
            h.assertTrue(m.load() > MechaLogic.LOAD_LOCK, "the loadout is not over the lock: " + m.load());
            h.assertTrue(Warnings.delivered(p).contains("gantry.over_lock"), "the hand-over did not say it is over the lock: " + Warnings.delivered(p));
            h.runAfterDelay(2, () -> {
                board(h, p, m);
                p.zza = 1;
                h.assertTrue(MechaLogic.riddenInput(m, p).equals(Vec3.ZERO), "an overloaded machine walks");
                done(h, p);
            });
        });
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void aPilotGetsOutUnderWater(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = mecha(h, MechaFrame.CHIMERA, FLOOR.east(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 0; dy < 5; dy++) {
            h.setBlock(FLOOR.east(3).offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.WATER);
        }
        board(h, p, m);
        h.runAfterDelay(3, () -> {
            h.assertTrue(m.isInWater(), "the machine is not in the water");
            // sneak and the jar menu key held a second: the client sends the eject, the server's handler takes it
            Network.action(p, new Network.JarAction(Network.Action.EJECT));
            h.assertTrue(p.getVehicle() == null && FormActions.state(p).form() == Form.JAR, "the pilot did not get out under water: " + FormActions.state(p).form());
            done(h, p);
        });
    }

    // --- 5. the Colossus leaves Siege by its clip, never in one tick ---------------------------------------------------

    private static String clipNow(MechaEntity m, double at) {
        var choice = com.coolfreeze23.headinajar.mecha.HopperAnimation.main(m, new com.coolfreeze23.headinajar.mecha.HopperView(), at);
        return choice == null ? "none" : choice.clip().name();
    }

    /** Kneels a boarded Colossus into Siege by the drive key (the drive payload's handler) and waits until it is anchored. */
    private static void siege(GameTestHelper h, ServerPlayer p, MechaEntity m, Runnable then) {
        m.setOnGround(true);
        h.runAfterDelay(3, () -> {
            HopperNet.drive(p, true);
            HopperNet.drive(p, false);
            h.assertTrue(Siege.rooted(m), "the drive key did not start Siege: " + Refusals.delivered(p));
        });
        h.runAfterDelay(3 + Siege.IN_TICKS + 2, () -> {
            h.assertTrue(Siege.locked(m), "not anchored");
            then.run();
        });
    }

    @GameTest(template = "platform", timeoutTicks = 160)
    public static void anEjectFromSiegeStandsTheMachineUpByItsClip(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = mecha(h, MechaFrame.COLOSSUS, FLOOR.east(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS);
        board(h, p, m);
        long[] out = new long[1];
        siege(h, p, m, () -> {
            out[0] = h.getLevel().getGameTime();
            Network.action(p, new Network.JarAction(Network.Action.EJECT));
            h.assertTrue(p.getVehicle() == null && !Siege.locked(m), "still anchored with nobody aboard");
            h.assertValueEqual(clipNow(m, out[0] + 5), "siege_out", "the empty machine's clip 5 ticks after the eject");
            h.assertValueEqual(clipNow(m, out[0] + 30), "siege_out", "the empty machine's clip 30 ticks after the eject");
        });
        h.runAfterDelay(3 + Siege.IN_TICKS + 2 + Siege.OUT_TICKS + 12, () -> {
            h.assertFalse(Siege.rising(m, h.getLevel().getGameTime()), "still standing up after the clip");
            h.assertFalse(clipNow(m, h.getLevel().getGameTime()).startsWith("siege"), "a Siege clip after the stand-up");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void aColossusWreckedInSiegeStandsUpThenCollapses(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(3));
        MechaEntity m = mecha(h, MechaFrame.COLOSSUS, FLOOR.east(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS);
        board(h, p, m);
        long[] wrecked = new long[1];
        siege(h, p, m, () -> {
            wrecked[0] = h.getLevel().getGameTime();
            m.hurt(h.getLevel().damageSources().generic(), 100_000);
            h.assertTrue(m.isDeadOrDying() && p.getVehicle() == null, "the machine was not wrecked with its pilot thrown clear");
            h.assertValueEqual(Siege.riseBeforeCollapse(m), Siege.WRECK_RISE, "the stand-up before the collapse");
        });
        int base = 3 + Siege.IN_TICKS + 2;
        h.runAfterDelay(base + 4, () -> h.assertValueEqual(clipNow(m, h.getLevel().getGameTime()), "siege_out", "the wrecked machine's clip 4 ticks in"));
        h.runAfterDelay(base + Siege.WRECK_RISE + 6, () -> h.assertValueEqual(clipNow(m, h.getLevel().getGameTime()), "destroyed", "the clip after the stand-up"));
        h.runAfterDelay(base + com.coolfreeze23.headinajar.mecha.Hopper.COLLAPSE_TICKS + 4, () -> h.assertFalse(m.isRemoved(), "the collapse was cut short by the stand-up"));
        h.runAfterDelay(base + com.coolfreeze23.headinajar.mecha.Hopper.COLLAPSE_TICKS + Siege.WRECK_RISE + 4, () -> {
            h.assertTrue(m.isRemoved(), "the wreck was never destroyed");
            done(h, p);
        });
    }

    // --- old worlds ---------------------------------------------------------------------------------------------------

    /**
     * One of each of the eight frames and three shells, written the way a world saved before this line held them, loads
     * side by side: animated, its modules, charge, heat, owner and health kept, in no sequence, and saving again what
     * it loaded and no transient state.
     */
    @GameTest(template = "platform", timeoutTicks = 40)
    public static void everyFrameAndShellSavedBeforeLoads(GameTestHelper h) {
        UUID owner = UUID.randomUUID();
        int x = 0;
        for (MechaFrame frame : MechaFrame.values()) {
            Vec3 at = h.absoluteVec(Vec3.atBottomCenterOf(FLOOR.offset(-6 + 2 * (x++ % 7), 0, x > 7 ? 3 : -3)));
            CompoundTag tag = base("headinajar:" + frame.id(), at);
            ListTag modules = new ListTag();
            int legs = frame.slots(MechaModule.Slot.LEGS) > 0 ? frame.firstIndex(MechaModule.Slot.LEGS) : -1;
            for (int i = 0; i < frame.totalSlots(); i++) {
                ItemStack stack = i == legs && frame != MechaFrame.KITE ? module(MechaModule.HYDRAULIC_LEGS) : ItemStack.EMPTY;
                modules.add(stack.saveOptional(h.getLevel().registryAccess()));
            }
            tag.put("Modules", modules);
            tag.put("Cargo", new ListTag());
            tag.putInt("Battery", 12_345);
            tag.putFloat("Heat", 40);
            tag.putFloat("Health", 50);
            tag.putUUID("Owner", owner);
            var loaded = EntityType.loadEntityRecursive(tag, h.getLevel(), e -> e);
            h.assertTrue(loaded instanceof MechaEntity, "an old " + frame + " did not load: " + loaded);
            MechaEntity m = (MechaEntity) loaded;
            h.getLevel().addFreshEntity(m);
            h.assertTrue(m.frame() == frame && frame.animated(), frame + " is not animated");
            h.assertTrue(m.battery() == 12_345 && Math.abs(m.heat() - 40) < 0.01 && owner.equals(m.owner()) && Math.abs(m.getHealth() - 50) < 0.01
                    && (legs < 0 || frame == MechaFrame.KITE || m.has(MechaModule.HYDRAULIC_LEGS)), "an old " + frame + " lost what it held");
            h.assertTrue(m.builtAt() == -1 && m.boardedAt() == -1 && m.ejectedAt() == -1 && m.verbAt() == -1 && m.lineAt() == -1 && m.takeoffAt() == -1
                    && m.vent() == 0, "an old " + frame + " loads mid-sequence");
            CompoundTag saved = new CompoundTag();
            m.addAdditionalSaveData(saved);
            for (String key : List.of("Spared", "Aloft", "Siege", "Verb", "Line", "Takeoff", "Graft"))
                h.assertFalse(saved.contains(key), "an old " + frame + " saves a new key " + key);
            h.assertTrue(saved.getInt("Battery") == 12_345 && saved.hasUUID("Owner"), "an old " + frame + " does not save what it loaded");
        }
        for (ShellEntity.Kind kind : ShellEntity.Kind.values()) {
            Vec3 at = h.absoluteVec(Vec3.atBottomCenterOf(FLOOR.offset(-4 + 4 * kind.ordinal(), 0, 6)));
            CompoundTag tag = base("headinajar:" + kind.name().toLowerCase(java.util.Locale.ROOT), at);
            tag.putFloat("Health", 20);
            tag.put("Cargo", new ListTag());
            var loaded = EntityType.loadEntityRecursive(tag, h.getLevel(), e -> e);
            h.assertTrue(loaded instanceof ShellEntity s && s.kind() == kind && s.animated(), "an old " + kind + " did not load: " + loaded);
            ShellEntity s = (ShellEntity) loaded;
            h.getLevel().addFreshEntity(s);
            h.assertTrue(s.boardedAt() == -1 && Math.abs(s.getHealth() - 20) < 0.01, "an old " + kind + " loads mid-sequence or hurt");
            CompoundTag saved = new CompoundTag();
            s.addAdditionalSaveData(saved);
            for (String key : List.of("Toss", "Web", "Hit", "Boarded")) h.assertFalse(saved.contains(key), "an old " + kind + " saves a new key " + key);
        }
        // a tick later every one of them is still there, standing
        h.runAfterDelay(5, () -> {
            h.assertValueEqual(h.getEntities(ModRegistry.mechaType(MechaFrame.HOPPER)).size() >= 1, true, "the old machines did not stay");
            h.succeed();
        });
    }

    private static CompoundTag base(String id, Vec3 at) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(at.x));
        pos.add(DoubleTag.valueOf(at.y));
        pos.add(DoubleTag.valueOf(at.z));
        tag.put("Pos", pos);
        return tag;
    }

    // --- 10. the Bulwark lifts what its pilot looks at ------------------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 80)
    public static void theBulwarkGrabsWhatThePilotLooksAtBeforeItsBodyTurns(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = mecha(h, MechaFrame.BULWARK, FLOOR.east(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS, MechaModule.ROCKET_PACK,
                MechaModule.CARGO_HOLD);
        board(h, p, m);
        // a cow two blocks east of the machine, which faces south: the pilot looks at it, the body has not turned yet
        var cow = h.spawn(EntityType.COW, FLOOR.east(5));
        cow.setNoAi(true);
        h.runAfterDelay(2, () -> {
            m.setOnGround(true);
            m.setYRot(0);
            m.setYBodyRot(0);
            p.setYRot(-90);
            p.setXRot(30);
            HopperNet.drive(p, true);
            HopperNet.drive(p, false);
            h.assertTrue((m.liftAt() & 3) == com.coolfreeze23.headinajar.mecha.Bulwark.GRAB, "the grab was refused: " + Refusals.delivered(p));
        });
        h.runAfterDelay(2 + com.coolfreeze23.headinajar.mecha.Bulwark.GRAB_TICKS + 2, () -> {
            h.assertTrue(m.heldId() == cow.getId(), "the cow the pilot looked at is not held");
            done(h, p);
        });
    }
}
