package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.AssemblyGantryBlockEntity;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.HeadRegistry;
import com.coolfreeze23.headinajar.item.MechaModuleItem;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.MechaLogic;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.menu.AssemblyGantryMenu;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Phase 4, Mecha Division: the Gantry, weight, heat, power, arms, damage, eject, ownership and flight. */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class MechaGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);
    private static final List<UUID> BREAKERS = new ArrayList<>();
    private static boolean listening;

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

    private static ItemStack module(MechaModule m, int slider) {
        ItemStack stack = new ItemStack(ModRegistry.mechaModule(m));
        MechaModuleItem.setSlider(stack, slider);
        return stack;
    }

    /** A Hopper with these modules in its first matching slots, owned by and piloted by the player. */
    private static MechaEntity hopper(GameTestHelper h, ServerPlayer pilot, BlockPos at, ItemStack... mods) {
        MechaEntity m = h.spawn(ModRegistry.HOPPER.get(), at);
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < m.frame().totalSlots(); i++) slots.add(ItemStack.EMPTY);
        for (ItemStack stack : mods) {
            MechaModule type = ((MechaModuleItem) stack.getItem()).type();
            int first = m.frame().firstIndex(type.slot);
            for (int i = first; i < first + m.frame().slots(type.slot); i++) {
                if (slots.get(i).isEmpty()) {
                    slots.set(i, stack);
                    break;
                }
            }
        }
        m.setModules(slots);
        m.setOwner(pilot.getUUID());
        m.setBattery(m.batteryCapacity());
        if (FormActions.state(pilot).form() == Form.BODY) FormActions.detach(pilot, 3);
        h.assertTrue(FormActions.mountHost(pilot, m), "could not board");
        return m;
    }

    @GameTest(template = "platform", timeoutTicks = 320)
    public static void gantryAssembles(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(3));
        h.setBlock(FLOOR, ModRegistry.ASSEMBLY_GANTRY.get());
        var gantry = (AssemblyGantryBlockEntity) h.getBlockEntity(FLOOR);
        gantry.items().setStackInSlot(AssemblyGantryBlockEntity.SLOT_FRAME, new ItemStack(ModRegistry.frameItem(MechaFrame.HOPPER)));
        int legs = MechaFrame.HOPPER.firstIndex(MechaModule.Slot.LEGS), arm = MechaFrame.HOPPER.firstIndex(MechaModule.Slot.ARM);
        gantry.items().setStackInSlot(AssemblyGantryBlockEntity.FIRST_MODULE + legs, module(MechaModule.HYDRAULIC_LEGS, 50));
        gantry.items().setStackInSlot(AssemblyGantryBlockEntity.FIRST_MODULE + arm, module(MechaModule.HYDRAULIC_FIST, 50));
        h.assertFalse(gantry.items().isItemValid(AssemblyGantryBlockEntity.FIRST_MODULE + legs, module(MechaModule.DRILL, 50)), "a drill fits a leg slot");
        gantry.energy().set(20_000);
        h.assertTrue(gantry.button(p, AssemblyGantryMenu.ASSEMBLE), "assembly refused");
        h.assertTrue(gantry.items().getStackInSlot(AssemblyGantryBlockEntity.SLOT_FRAME).isEmpty(), "frame not used");
        // the frame stands on the pad once the arm has stood off
        h.runAfterDelay(com.coolfreeze23.headinajar.machine.GantryPlan.LEAD + 1, () -> {
            MechaEntity m = h.getEntities(ModRegistry.HOPPER.get()).get(0);
            h.assertTrue(m.has(MechaModule.HYDRAULIC_LEGS) && m.arm(0) == MechaModule.HYDRAULIC_FIST, "modules lost in assembly");
            h.assertTrue(p.getUUID().equals(m.owner()), "no owner");
        });
        // the arm is busy until the build's clip ends; then a take-apart is its 60 ticks
        h.runAfterDelay(com.coolfreeze23.headinajar.machine.GantryPlan.of(MechaFrame.HOPPER).length() + 2, () -> {
            MechaEntity m = h.getEntities(ModRegistry.HOPPER.get()).get(0);
            h.assertTrue(gantry.button(p, AssemblyGantryMenu.DISASSEMBLE), "could not take it apart");
            h.runAfterDelay(61, () -> {
                h.assertTrue(m.isRemoved() && !gantry.items().getStackInSlot(AssemblyGantryBlockEntity.FIRST_MODULE + arm).isEmpty(), "modules not back in the gantry");
                done(h, p);
            });
        });
    }

    @GameTest(template = "platform")
    public static void overweightLocksServos(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = hopper(h, p, FLOOR, module(MechaModule.HYDRAULIC_LEGS, 50), module(MechaModule.PASSENGER_POD, 50),
                module(MechaModule.HYDRAULIC_FIST, 50), module(MechaModule.RIOT_SHIELD, 50), module(MechaModule.ARMOR_PLATING, 50),
                module(MechaModule.COOLANT_LOOP, 50));
        h.assertTrue(m.load() > 1.25f, "load only " + m.load());
        p.zza = 1;
        h.assertTrue(MechaLogic.riddenInput(m, p).equals(Vec3.ZERO), "an overloaded mecha walked");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void legsNeededToWalk(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = hopper(h, p, FLOOR, module(MechaModule.HYDRAULIC_FIST, 50));
        p.zza = 1;
        h.assertTrue(MechaLogic.riddenInput(m, p).equals(Vec3.ZERO), "a legless mecha walked");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void heatThrottlesAt85(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = hopper(h, p, FLOOR, module(MechaModule.HYDRAULIC_LEGS, 50));
        m.setHeat(m.frame().heatCap * 0.5f);
        h.assertTrue(MechaLogic.effectiveness(m) == 1f, "throttled at 50%");
        m.setHeat(m.frame().heatCap * 0.9f);
        h.assertTrue(MechaLogic.effectiveness(m) == 0.7f, "not throttled at 90%: " + MechaLogic.effectiveness(m));
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void ventAt100(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = hopper(h, p, FLOOR, module(MechaModule.HYDRAULIC_LEGS, 50));
        m.setHeat(m.frame().heatCap + 5);
        MechaLogic.serverTick(m);
        h.assertTrue(m.vent() > 0, "no vent");
        h.assertTrue(m.heat() <= m.frame().heatCap * 0.5f + 1, "heat after vent " + m.heat());
        h.assertTrue(MechaLogic.effectiveness(m) == 0, "modules ran while venting");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void noPowerLimpMode(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = hopper(h, p, FLOOR, module(MechaModule.HYDRAULIC_LEGS, 50));
        float full = MechaLogic.riddenSpeed(m);
        m.setBattery(0);
        float limp = MechaLogic.riddenSpeed(m);
        h.assertTrue(Math.abs(limp - full * 0.3f) < 1e-4, "limp " + limp + " vs " + full);
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void drillFiresBreakEvent(GameTestHelper h) {
        if (!listening) {
            listening = true;
            NeoForge.EVENT_BUS.addListener((BlockEvent.BreakEvent e) -> BREAKERS.add(e.getPlayer().getUUID()));
        }
        ServerPlayer p = player(h, FLOOR);
        BlockPos stone = FLOOR.south(2).above();
        h.setBlock(stone, Blocks.STONE);
        MechaEntity m = hopper(h, p, FLOOR, module(MechaModule.HYDRAULIC_LEGS, 50), module(MechaModule.DRILL, 30));
        Vec3 target = Vec3.atCenterOf(h.absolutePos(stone));
        p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target);
        m.armsHeld = 1;
        h.succeedWhen(() -> {
            h.assertBlockPresent(Blocks.AIR, stone);
            h.assertTrue(BREAKERS.contains(p.getUUID()), "the break was not posted as the pilot");
            m.armsHeld = 0;
            h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    @GameTest(template = "platform")
    public static void damageRoutesToMecha(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = hopper(h, p, FLOOR, module(MechaModule.HYDRAULIC_LEGS, 50));
        float pilotHealth = p.getHealth(), mechaHealth = m.getHealth();
        p.hurt(p.damageSources().generic(), 5);
        h.assertTrue(p.getHealth() == pilotHealth, "the pilot was hit");
        h.assertTrue(m.getHealth() < mechaHealth, "the mecha took nothing");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void ejectDropsJar(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = hopper(h, p, FLOOR, module(MechaModule.HYDRAULIC_LEGS, 50));
        // sneaking is for the Anchor Clamps: it doesn't get the pilot out (a teleport or spectator mode does)
        p.setShiftKeyDown(true);
        p.stopRiding();
        p.setShiftKeyDown(false);
        h.assertTrue(p.getVehicle() == m, "a sneak dismount left the cockpit");
        MechaLogic.eject(m, p);
        h.assertTrue(p.getVehicle() == null && FormActions.state(p).form() == Form.JAR, "eject failed: " + FormActions.state(p).form());
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void onlyOwnerCanBoard(GameTestHelper h) {
        ServerPlayer owner = player(h, FLOOR);
        ServerPlayer other = player(h, FLOOR.east(2));
        MechaEntity m = h.spawn(ModRegistry.HOPPER.get(), FLOOR.west(2));
        m.setOwner(owner.getUUID());
        FormActions.detach(other, 3);
        h.assertFalse(FormActions.mountHost(other, m), "a stranger boarded");
        HeadRegistry.get(owner).setTrust(owner.getUUID(), other.getUUID(), true);
        h.assertTrue(FormActions.mountHost(other, m), "a trusted friend could not board");
        HeadRegistry.get(owner).setTrust(owner.getUUID(), other.getUUID(), false);
        done(h, owner, other);
    }

    @GameTest(template = "platform")
    public static void flightNeedsThrustAboveMass(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = hopper(h, p, FLOOR, module(MechaModule.HYDRAULIC_LEGS, 50), module(MechaModule.ROCKET_PACK, 0));
        h.assertTrue(m.mass() > 200 && !MechaLogic.canFly(m), "flew on 200 kg of thrust at " + m.mass() + " kg");
        m.setModules(List.of(module(MechaModule.HYDRAULIC_LEGS, 50), module(MechaModule.ROCKET_PACK, 100)));
        h.assertTrue(MechaLogic.thrust(m) == 800 && MechaLogic.canFly(m), "full thrust can't lift " + m.mass() + " kg");
        done(h, p);
    }
}
