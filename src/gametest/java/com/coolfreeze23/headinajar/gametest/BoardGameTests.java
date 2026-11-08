package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.AssemblyGantryBlockEntity;
import com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity;
import com.coolfreeze23.headinajar.block.EmployeeMountBlockEntity;
import com.coolfreeze23.headinajar.block.HeadJobs;
import com.coolfreeze23.headinajar.block.LabMachines;
import com.coolfreeze23.headinajar.block.MachineBlock;
import com.coolfreeze23.headinajar.block.NeuralSocketBlockEntity;
import com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.entity.MissileEntity;
import com.coolfreeze23.headinajar.form.BoardEvents;
import com.coolfreeze23.headinajar.form.Emp;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.HeadRegistry;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.form.NeuroLink;
import com.coolfreeze23.headinajar.genetics.Gene;
import com.coolfreeze23.headinajar.genetics.GeneEffects;
import com.coolfreeze23.headinajar.genetics.Serum;
import com.coolfreeze23.headinajar.item.GeneticsItems;
import com.coolfreeze23.headinajar.item.JarredHeadItem;
import com.coolfreeze23.headinajar.item.MechaModuleItem;
import com.coolfreeze23.headinajar.item.QuantumItems;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.MechaLogic;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.mecha.MechaSystems;
import com.coolfreeze23.headinajar.menu.AssemblyGantryMenu;
import com.coolfreeze23.headinajar.module.ModuleType;
import com.coolfreeze23.headinajar.registry.ModFluids;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Phase 6, Board of Directors: the new frames and modules, heads at work, villager staff, the socket, quantum links, EMP. */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class BoardGameTests {
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

    private static MechaEntity mecha(GameTestHelper h, MechaFrame frame, BlockPos at, ServerPlayer owner, MechaModule... mods) {
        MechaEntity m = h.spawn(ModRegistry.mechaType(frame), at);
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < frame.totalSlots(); i++) slots.add(ItemStack.EMPTY);
        for (MechaModule type : mods) {
            int first = frame.firstIndex(type.slot);
            for (int i = first; i < first + frame.slots(type.slot); i++) {
                if (slots.get(i).isEmpty()) {
                    ItemStack stack = new ItemStack(ModRegistry.mechaModule(type));
                    MechaModuleItem.setSlider(stack, 100);
                    slots.set(i, stack);
                    break;
                }
            }
        }
        m.setModules(slots);
        if (owner != null) m.setOwner(owner.getUUID());
        m.setBattery(m.batteryCapacity());
        return m;
    }

    private static ItemStack jarredHead(GameTestHelper h, EntityType<? extends Mob> type, VillagerProfession job) {
        Mob mob = h.spawn(type, FLOOR.above(3));
        if (mob instanceof Villager v && job != null) v.setVillagerData(v.getVillagerData().setProfession(job));
        ItemStack stack = JarredHeadItem.of(mob);
        mob.discard();
        return stack;
    }

    // --- frames ----------------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void framesMatchTheDesign(GameTestHelper h) {
        h.assertTrue(MechaFrame.COLOSSUS.totalSlots() == 16 && MechaFrame.COLOSSUS.slots(MechaModule.Slot.ARM) == 4, "colossus slots");
        h.assertTrue(MechaFrame.maxSlots() == AssemblyGantryBlockEntity.MODULE_SLOTS, "gantry is too small");
        h.assertTrue(MechaFrame.EXECUTIVE.genePorts() == 4 && MechaFrame.CHIMERA.genePorts() == 3 && MechaFrame.HOPPER.genePorts() == 0, "gene ports");
        MechaEntity wheelhouse = mecha(h, MechaFrame.WHEELHOUSE, FLOOR, null);
        h.assertTrue(MechaLogic.canWalk(wheelhouse), "a Wheelhouse needs no legs module");
        MechaEntity hopper = mecha(h, MechaFrame.HOPPER, FLOOR.east(3), null);
        h.assertFalse(MechaLogic.canWalk(hopper), "a Hopper walked with no legs");
        MechaEntity kite = mecha(h, MechaFrame.KITE, FLOOR.west(3), null);
        h.assertTrue(MechaLogic.canFly(kite), "a Kite can't fly");
        h.succeed();
    }

    @GameTest(template = "platform")
    public static void wheelhouseSlowOffRoad(GameTestHelper h) {
        h.setBlock(FLOOR.below(), Blocks.STONE);
        h.setBlock(FLOOR.east(4).below(), Blocks.GRASS_BLOCK);
        MechaEntity road = mecha(h, MechaFrame.WHEELHOUSE, FLOOR, null);
        MechaEntity field = mecha(h, MechaFrame.WHEELHOUSE, FLOOR.east(4), null);
        h.runAfterDelay(5, () -> {
            float a = MechaLogic.riddenSpeed(road), b = MechaLogic.riddenSpeed(field);
            h.assertTrue(a > 2.5f * b, "road " + a + " vs field " + b);
            h.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void colossusSkipsPortals(GameTestHelper h) {
        h.assertFalse(mecha(h, MechaFrame.COLOSSUS, FLOOR, null).canUsePortal(false), "a Colossus fits a nether portal");
        h.assertTrue(mecha(h, MechaFrame.HOPPER, FLOOR.east(4), null).canUsePortal(false), "a Hopper can't use a portal");
        h.succeed();
    }

    @GameTest(template = "platform")
    public static void gantryBuildsAColossus(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(4));
        h.setBlock(FLOOR, ModRegistry.ASSEMBLY_GANTRY.get());
        var gantry = (AssemblyGantryBlockEntity) h.getBlockEntity(FLOOR);
        gantry.energy().set(100_000);
        gantry.items().setStackInSlot(AssemblyGantryBlockEntity.SLOT_FRAME, new ItemStack(ModRegistry.frameItem(MechaFrame.COLOSSUS)));
        int last = MechaFrame.COLOSSUS.firstIndex(MechaModule.Slot.SENSOR) + 1;
        gantry.items().setStackInSlot(AssemblyGantryBlockEntity.FIRST_MODULE + last, new ItemStack(ModRegistry.mechaModule(MechaModule.TARGET_LOCK)));
        int shoulder = MechaFrame.COLOSSUS.firstIndex(MechaModule.Slot.ARM) + 3;
        gantry.items().setStackInSlot(AssemblyGantryBlockEntity.FIRST_MODULE + shoulder, new ItemStack(ModRegistry.mechaModule(MechaModule.ARC_CANNON)));
        h.assertTrue(gantry.button(p, AssemblyGantryMenu.ASSEMBLE), "assemble failed");
        MechaEntity m = gantry.onPad();
        h.assertTrue(m != null && m.frame() == MechaFrame.COLOSSUS && m.moduleAt(last) == MechaModule.TARGET_LOCK && m.arm(3) == MechaModule.ARC_CANNON,
                "16th slot or shoulder lost");
        done(h, p);
    }

    // --- modules ---------------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void reactiveArmourSpendsCharges(GameTestHelper h) {
        MechaEntity m = mecha(h, MechaFrame.BULWARK, FLOOR, null, MechaModule.REACTIVE_ARMOUR);
        var blast = h.getLevel().damageSources().explosion(null, null);
        float left = MechaSystems.absorb(m, blast, 10);
        h.assertTrue(Math.abs(left - 6) < 1e-3 && MechaSystems.reactiveCharges(m) == 5, "first blast " + left);
        for (int i = 0; i < 5; i++) MechaSystems.absorb(m, blast, 10);
        h.assertTrue(MechaSystems.reactiveCharges(m) == 0 && MechaSystems.absorb(m, blast, 10) == 10, "charges did not run out");
        h.succeed();
    }

    @GameTest(template = "platform")
    public static void energyShieldPaysInFe(GameTestHelper h) {
        MechaEntity m = mecha(h, MechaFrame.BULWARK, FLOOR, null, MechaModule.ENERGY_SHIELD);
        int before = m.battery();
        float left = MechaSystems.absorb(m, h.getLevel().damageSources().generic(), 8);
        h.assertTrue(left < 1e-3 && before - m.battery() == 8000, "left " + left + ", spent " + (before - m.battery()));
        h.succeed();
    }

    @GameTest(template = "platform")
    public static void missilePodFiresASalvo(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = mecha(h, MechaFrame.BULWARK, FLOOR, p, MechaModule.MISSILE_POD, MechaModule.ORE_SCANNER);
        FormActions.detach(p, 3);
        h.assertTrue(FormActions.mountHost(p, m), "could not board");
        h.spawn(EntityType.ZOMBIE, FLOOR.east(6));
        h.assertTrue(MechaSystems.utilities(m).equals(List.of(MechaModule.MISSILE_POD, MechaModule.ORE_SCANNER)), "utilities " + MechaSystems.utilities(m));
        h.assertTrue(MechaSystems.useUtility(m, p), "missile pod did not fire");
        h.assertTrue(h.getLevel().getEntitiesOfClass(MissileEntity.class, m.getBoundingBox().inflate(6)).size() == 4, "not a salvo of four");
        MechaSystems.nextUtility(m);
        h.assertTrue(MechaSystems.selectedUtility(m) == MechaModule.ORE_SCANNER, "G did not step to the scanner");
        done(h, p);
    }

    // --- heads at work ---------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void turretNeverTargetsOwner(GameTestHelper h) {
        ServerPlayer owner = player(h, FLOOR.east(2)), stranger = player(h, FLOOR.west(2));
        h.setBlock(FLOOR, ModRegistry.TURRET_PEDESTAL.get());
        var turret = (TurretPedestalBlockEntity) h.getBlockEntity(FLOOR);
        turret.setOwner(owner.getUUID());
        var wolf = h.spawn(EntityType.WOLF, FLOOR.north(2));
        wolf.tame(owner);
        var villager = h.spawn(EntityType.VILLAGER, FLOOR.south(2));
        var zombie = h.spawn(EntityType.ZOMBIE, FLOOR.north(4));
        h.assertFalse(turret.mayTarget(owner), "targets its owner");
        h.assertFalse(turret.mayTarget(stranger), "targets players with PvP turrets off");
        h.assertFalse(turret.mayTarget(wolf), "targets a tamed wolf");
        h.assertFalse(turret.mayTarget(villager), "targets a villager");
        h.assertTrue(turret.mayTarget(zombie), "ignores a zombie");
        done(h, owner, stranger);
    }

    @GameTest(template = "platform")
    public static void skeletonTurretShoots(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.TURRET_PEDESTAL.get());
        h.setBlock(FLOOR.below(), Blocks.CHEST);
        ((ChestBlockEntity) h.getBlockEntity(FLOOR.below())).setItem(0, new ItemStack(Items.ARROW, 4));
        var turret = (TurretPedestalBlockEntity) h.getBlockEntity(FLOOR);
        turret.items().setStackInSlot(0, jarredHead(h, EntityType.SKELETON, null));
        turret.energy().set(40_000);
        h.spawn(EntityType.ZOMBIE, FLOOR.east(5));
        TurretPedestalBlockEntity.serverTick(h.getLevel(), turret.getBlockPos(), turret.getBlockState(), turret);
        int arrows = ((ChestBlockEntity) h.getBlockEntity(FLOOR.below())).getItem(0).getCount();
        h.assertTrue(arrows == 3, "arrows left " + arrows);
        h.succeed();
    }

    @GameTest(template = "platform")
    public static void arrayOutputPerHead(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.CEREBRAL_ARRAY.get());
        var array = (CerebralArrayBlockEntity) h.getBlockEntity(FLOOR);
        ItemStack zombie = jarredHead(h, EntityType.ZOMBIE, null);
        for (int i = 0; i < 3; i++) array.items().setStackInSlot(i, zombie.copy());
        h.assertTrue(array.potential() == 60, "three zombie heads make " + array.potential());
        array.items().setStackInSlot(3, jarredHead(h, EntityType.ENDERMAN, null));
        array.items().setStackInSlot(4, zombie.copy());
        // five heads: a Board Meeting, +10%: (20 x 4 + 80) x 1.1 = 176
        h.assertTrue(array.potential() == 176, "board meeting makes " + array.potential());
        array.items().setStackInSlot(5, new ItemStack(Items.DRAGON_HEAD));
        h.assertTrue(array.potential() == 176 + 0, "a dragon head worked without the Board Room Core");
        array.items().setStackInSlot(CerebralArrayBlockEntity.SLOT_CORE, new ItemStack(ModRegistry.BOARD_ROOM_CORE.get()));
        h.assertTrue(array.potential() == Math.round((20 * 4 + 80 + 1000) * 1.1f), "dragon head with the core makes " + array.potential());
        // no broth, no thought
        h.getLevel().setDayTime(0);
        CerebralArrayBlockEntity.serverTick(h.getLevel(), array.getBlockPos(), array.getBlockState(), array);
        array.broth().fill(new FluidStack(ModFluids.BROTH.source.get(), 4000), IFluidHandler.FluidAction.EXECUTE);
        h.runAfterDelay(25, () -> {
            h.assertTrue(array.output() == array.potential() && array.broth().getFluidInTank(0).getAmount() < 4000, "fed array output " + array.output());
            h.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void unpaidStaffHalfBonus(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(3));
        h.setBlock(FLOOR, ModRegistry.DNA_SEQUENCER.get());
        BlockPos mountAt = FLOOR.east();
        h.setBlock(mountAt, ModRegistry.EMPLOYEE_MOUNT.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.EAST));
        var mount = (EmployeeMountBlockEntity) h.getBlockEntity(mountAt);
        mount.use(p, jarredHead(h, EntityType.VILLAGER, VillagerProfession.LIBRARIAN));
        BlockPos machine = h.absolutePos(FLOOR);
        h.assertTrue(HeadJobs.staff(h.getLevel(), machine, "librarian") == 0.5f, "unpaid staff gave " + HeadJobs.staff(h.getLevel(), machine, "librarian"));
        h.assertTrue(HeadJobs.staff(h.getLevel(), machine, "cleric") == 0, "a librarian counted as a cleric");
        h.setBlock(mountAt.east(), Blocks.CHEST);
        ((ChestBlockEntity) h.getBlockEntity(mountAt.east())).setItem(0, new ItemStack(Items.EMERALD, 3));
        h.assertTrue(mount.payroll() && HeadJobs.staff(h.getLevel(), machine, "librarian") == 1f, "paid staff not at full bonus");
        h.assertTrue(((ChestBlockEntity) h.getBlockEntity(mountAt.east())).getItem(0).getCount() == 2, "payroll took the wrong amount");
        h.assertTrue(mount.payroll() && ((ChestBlockEntity) h.getBlockEntity(mountAt.east())).getItem(0).getCount() == 2, "paid twice in a day");
        done(h, p);
    }

    // --- the socket ------------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void socketSixChannels(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(3));
        h.setBlock(FLOOR, ModRegistry.NEURAL_SOCKET.get());
        var socket = (NeuralSocketBlockEntity) h.getBlockEntity(FLOOR);
        FormActions.detach(p, 3);
        h.assertTrue(socket.plugIn(p) && FormActions.state(p).form() == Form.SOCKET, "could not plug in");
        BlockPos at = h.absolutePos(FLOOR);
        for (Direction d : Direction.values()) socket.setOutput(d, 2 + d.get3DDataValue() * 2);
        for (Direction d : Direction.values()) {
            // a neighbour on side d asks with the direction pointing from it into the socket
            int seen = h.getLevel().getBlockState(at).getSignal(h.getLevel(), at, d.getOpposite());
            h.assertTrue(seen == 2 + d.get3DDataValue() * 2, d + " reads " + seen);
        }
        socket.pulse(Direction.UP);
        h.assertTrue(socket.output(Direction.UP) == 15, "no pulse");
        h.runAfterDelay(NeuralSocketBlockEntity.PULSE_TICKS + 2, () -> {
            h.assertTrue(socket.output(Direction.UP) == 2 + Direction.UP.get3DDataValue() * 2, "pulse did not end");
            h.assertTrue(NeuralSocketBlockEntity.unplug(p) && FormActions.state(p).form() == Form.JAR, "could not unplug");
            done(h, p);
        });
    }

    @GameTest(template = "platform")
    public static void zombieHeadIsAClock(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.NEURAL_SOCKET.get());
        var socket = (NeuralSocketBlockEntity) h.getBlockEntity(FLOOR);
        socket.items().setStackInSlot(0, jarredHead(h, EntityType.ZOMBIE, null));
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int i = 0; i < 20; i++) {
            h.runAfterDelay(i + 1, () -> seen.add(socket.output(Direction.NORTH)));
        }
        h.runAfterDelay(23, () -> {
            h.assertTrue(seen.contains(0) && seen.contains(15), "clock levels " + seen);
            h.succeed();
        });
    }

    // --- quantum links and EMP ---------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void entangledLinkCrossesDimensions(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        JarState state = FormActions.state(p);
        ItemStack goggles = new ItemStack(ModRegistry.RELAY_GOGGLES.get()), collar = new ItemStack(ModRegistry.COLLAR_CAM.get());
        state.setGoggles(goggles);
        state.setCollar(collar);
        var nether = new HeadRegistry.Spot(Level.NETHER, new Vec3(0, 64, 0));
        h.assertTrue(NeuroLink.quality(p, state, nether) == 0, "a plain link crossed dimensions");
        UUID pair = UUID.randomUUID();
        ItemStack g2 = goggles.copy(), c2 = collar.copy();
        QuantumItems.entangle(g2, pair);
        QuantumItems.entangle(c2, pair);
        state.setGoggles(g2);
        state.setCollar(c2);
        h.assertTrue(NeuroLink.quality(p, state, nether) > NeuroLink.QUALITY_TO_LINK, "entangled link failed across dimensions");
        QuantumItems.entangle(c2, UUID.randomUUID());
        state.setCollar(c2);
        h.assertTrue(NeuroLink.quality(p, state, nether) == 0, "halves of different pairs linked");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void quantumGogglesReachAnywhere(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        JarState state = FormActions.state(p);
        state.setGoggles(new ItemStack(ModRegistry.QUANTUM_GOGGLES.get()));
        state.setCollar(new ItemStack(ModRegistry.COLLAR_CAM.get()));
        var far = new HeadRegistry.Spot(p.level().dimension(), p.position().add(50_000, 0, 0));
        h.assertTrue(NeuroLink.quality(p, state, far) > 90, "quantum goggles lost the link at 50,000 blocks");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void empBreaksLinkUnlessShielded(GameTestHelper h) {
        ServerPlayer a = player(h, FLOOR), b = player(h, FLOOR.east(2));
        for (ServerPlayer p : new ServerPlayer[]{a, b}) {
            h.assertTrue(FormActions.separate(p, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()), new ItemStack(ModRegistry.RELAY_GOGGLES.get()),
                    new ItemStack(ModRegistry.COLLAR_CAM.get()), p.position().add(0, 0, 1.5), null), "separate failed");
            h.assertTrue(FormActions.state(p).form() == Form.LINKED, "not linked");
        }
        ItemStack shielded = FormActions.state(b).collar();
        h.assertTrue(QuantumItems.install(shielded, QuantumItems.Upgrade.SHIELDED_HOUSING), "could not fit the housing");
        FormActions.state(b).setCollar(shielded);
        Emp.burst(h.getLevel(), h.absoluteVec(Vec3.atCenterOf(FLOOR.east())), Emp.RADIUS);
        h.assertTrue(FormActions.state(a).form() == Form.JAR, "the EMP did not break the link");
        h.assertTrue(FormActions.state(b).form() == Form.LINKED, "Shielded Housing did not hold");
        done(h, a, b);
    }

    @GameTest(template = "platform")
    public static void collarHoldsFourUpgrades(GameTestHelper h) {
        ItemStack collar = new ItemStack(ModRegistry.COLLAR_CAM.get());
        QuantumItems.Upgrade[] ups = QuantumItems.Upgrade.values();
        for (int i = 0; i < 4; i++) h.assertTrue(QuantumItems.install(collar, ups[i]), "upgrade " + i + " refused");
        h.assertFalse(QuantumItems.install(collar, ups[4]), "a fifth upgrade fit");
        h.assertFalse(QuantumItems.install(new ItemStack(ModRegistry.COLLAR_CAM.get()), ups[0]) && QuantumItems.install(collar, ups[0]), "a duplicate fit");
        h.succeed();
    }

    // --- legendary genes and jar modules ----------------------------------------------------------------

    @GameTest(template = "platform")
    public static void dragonsBreathSamplesTheDragon(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.CENTRIFUGE.get());
        var c = (LabMachines.Centrifuge) h.getBlockEntity(FLOOR);
        c.energy().set(10_000);
        c.items().setStackInSlot(0, new ItemStack(Items.DRAGON_BREATH));
        for (int i = 0; i < LabMachines.Centrifuge.TICKS; i++) LabMachines.Centrifuge.serverTick(h.getLevel(), c.getBlockPos(), c.getBlockState(), c);
        ItemStack dna = c.items().getStackInSlot(1);
        h.assertTrue(GeneticsItems.species(dna) == EntityType.ENDER_DRAGON && GeneticsItems.quality(dna) == 60, "no dragon DNA");
        h.assertTrue(c.items().getStackInSlot(2).is(Items.GLASS_BOTTLE), "no bottle back");
        h.succeed();
    }

    @GameTest(template = "platform")
    public static void decayAuraWithersHostiles(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        var genes = p.getData(ModRegistry.GENES);
        genes.serums.add(new Serum(List.of(new Serum.Entry(Gene.DECAY_AURA, 1f)), 100));
        genes.changed();
        var zombie = h.spawn(EntityType.ZOMBIE, FLOOR.east(3));
        var cow = h.spawn(EntityType.COW, FLOOR.west(3));
        p.tickCount = 40;
        GeneEffects.tick(p);
        h.assertTrue(zombie.hasEffect(MobEffects.WITHER) && !cow.hasEffect(MobEffects.WITHER), "aura hit the wrong creatures");
        h.assertTrue(GeneEffects.actives(p).contains(Gene.DECAY_AURA), "Decay Aura is not an active gene");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void lacrimalLaserCostsAndCools(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        JarState state = FormActions.state(p);
        state.setModule(0, new ItemStack(ModRegistry.module(ModuleType.LACRIMAL_LASER)));
        int before = state.charge();
        h.assertTrue(BoardEvents.laser(p), "laser did not fire");
        h.assertTrue(before - state.charge() == BoardEvents.LASER_COST, "laser cost " + (before - state.charge()));
        h.assertFalse(BoardEvents.laser(p), "laser ignored its cooldown");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void executiveJarHasSevenSlots(GameTestHelper h) {
        JarState state = new JarState();
        state.setSlots(7);
        h.assertTrue(state.slots() == 7 && state.maxIntegrity() == 30 && state.maxSaline() == 2000, "executive jar tier");
        h.succeed();
    }
}
