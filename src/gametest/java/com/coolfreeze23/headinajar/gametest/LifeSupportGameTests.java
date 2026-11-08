package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.BodyPrinterBlockEntity;
import com.coolfreeze23.headinajar.block.LifeSupportCradleBlockEntity;
import com.coolfreeze23.headinajar.block.PowerBlockEntities;
import com.coolfreeze23.headinajar.block.SalineMixerBlockEntity;
import com.coolfreeze23.headinajar.entity.HeadJarEntity;
import com.coolfreeze23.headinajar.entity.HeadlessBody;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.FormEvents;
import com.coolfreeze23.headinajar.form.HeadRegistry;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.form.LifeSupport;
import com.coolfreeze23.headinajar.form.NeuroLink;
import com.coolfreeze23.headinajar.form.RelayRegistry;
import com.coolfreeze23.headinajar.module.ModuleEffects;
import com.coolfreeze23.headinajar.module.ModuleType;
import com.coolfreeze23.headinajar.registry.ModFluids;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Phase 2, Life Support: saline, integrity, power, relays, jammers, the mixer, the printer and the new modules. */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class LifeSupportGameTests {
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

    private static JarState state(ServerPlayer p) {
        return FormActions.state(p);
    }

    private static void separate(GameTestHelper h, ServerPlayer p, BlockPos headAt, LifeSupportCradleBlockEntity cradle) {
        h.assertTrue(FormActions.separate(p, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()), new ItemStack(ModRegistry.RELAY_GOGGLES.get()),
                new ItemStack(ModRegistry.COLLAR_CAM.get()), h.absoluteVec(Vec3.atBottomCenterOf(headAt)), cradle), "separate failed");
    }

    @GameTest(template = "platform")
    public static void salineDrainsOnlyAwayFromCradle(GameTestHelper h) {
        ServerPlayer docked = player(h, FLOOR);
        h.setBlock(FLOOR.east(2), ModRegistry.CRADLE.get());
        separate(h, docked, FLOOR, (LifeSupportCradleBlockEntity) h.getBlockEntity(FLOOR.east(2)));
        ServerPlayer placed = player(h, FLOOR.north(2));
        separate(h, placed, FLOOR.north(3), null);
        h.assertTrue(state(docked).saline() == 1000 && state(placed).saline() == 1000, "fresh jars not full");
        for (ServerPlayer p : new ServerPlayer[]{docked, placed}) {
            p.tickCount = LifeSupport.drainTicks(state(p));
            LifeSupport.tick(p, state(p));
        }
        h.assertTrue(state(docked).saline() == 1000, "a docked head dried out");
        h.assertTrue(state(placed).saline() == 999, "a placed head did not use saline: " + state(placed).saline());
        done(h, docked, placed);
    }

    @GameTest(template = "platform")
    public static void dehydrationHurtsIntegrity(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        state(p).setSaline(0);
        p.tickCount = 600 * LifeSupport.drainTicks(state(p));
        LifeSupport.tick(p, state(p));
        h.assertTrue(state(p).integrity() == 9, "integrity " + state(p).integrity());
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void integrityAbsorbsDamage(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        float health = p.getHealth();
        p.hurt(p.damageSources().generic(), 4);
        h.assertTrue(p.getHealth() == health && state(p).integrity() == 6, "health " + p.getHealth() + " integrity " + state(p).integrity());
        p.invulnerableTime = 0;
        p.hurt(p.damageSources().generic(), 8);
        h.assertTrue(state(p).integrity() == 0 && p.getHealth() < health, "overflow did not reach health: " + p.getHealth());
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void teslaLinkMovesEnergyWithLoss(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.TESLA_NODE.get());
        h.setBlock(FLOOR.east(5), ModRegistry.TESLA_NODE.get());
        var a = (PowerBlockEntities.TeslaNode) h.getBlockEntity(FLOOR);
        var b = (PowerBlockEntities.TeslaNode) h.getBlockEntity(FLOOR.east(5));
        b.link(null);
        a.energy().set(10_000);
        a.link(h.absolutePos(FLOOR.east(5)));
        h.runAfterDelay(3, () -> {
            int total = a.energy().getEnergyStored() + b.energy().getEnergyStored();
            h.assertTrue(b.energy().getEnergyStored() > 0, "nothing arrived");
            h.assertTrue(total < 10_000, "no loss: " + total);
            h.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void relayChainExtendsRange(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        separate(h, p, FLOOR.east(1), null);
        var dim = h.getLevel().dimension();
        Vec3 body = p.position();
        Vec3 head = body.add(0, 0, 150);
        long now = h.getLevel().getGameTime();
        int direct = NeuroLink.quality(p, state(p), new HeadRegistry.Spot(dim, head));
        h.assertTrue(direct == 0, "150 blocks should be out of Mk I range: " + direct);
        RelayRegistry relays = RelayRegistry.get(h.getLevel().getServer());
        BlockPos m1 = BlockPos.containing(body.add(0, 0, 60)), m2 = BlockPos.containing(body.add(0, 0, 120));
        relays.put(new RelayRegistry.Station(RelayRegistry.Kind.MAST, dim, m1, 96, null, 100_000, now, 1));
        relays.put(new RelayRegistry.Station(RelayRegistry.Kind.MAST, dim, m2, 96, null, 100_000, now, 1));
        int relayed = NeuroLink.quality(p, state(p), new HeadRegistry.Spot(dim, head));
        relays.remove(dim, m1);
        relays.remove(dim, m2);
        h.assertTrue(relayed > 50, "two masts should carry the link: " + relayed);
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void jammerCutsOthersNotOwner(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        separate(h, p, FLOOR.east(2), null);
        var dim = h.getLevel().dimension();
        HeadRegistry.Spot head = new HeadRegistry.Spot(dim, h.absoluteVec(Vec3.atBottomCenterOf(FLOOR.east(2))));
        int clear = NeuroLink.quality(p, state(p), head);
        RelayRegistry relays = RelayRegistry.get(h.getLevel().getServer());
        BlockPos at = h.absolutePos(FLOOR.south(2));
        long now = h.getLevel().getGameTime();
        relays.put(new RelayRegistry.Station(RelayRegistry.Kind.JAMMER, dim, at, 16, UUID.randomUUID(), 100_000, now, 40));
        int jammed = NeuroLink.quality(p, state(p), head);
        relays.put(new RelayRegistry.Station(RelayRegistry.Kind.JAMMER, dim, at, 16, p.getUUID(), 100_000, now, 40));
        int own = NeuroLink.quality(p, state(p), head);
        relays.remove(dim, at);
        h.assertTrue(jammed == Math.round(clear * 0.3), "stranger's jammer: " + clear + " -> " + jammed);
        h.assertTrue(own == clear, "own jammer cut the link: " + own);
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 260)
    public static void mixerMakesSaline(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.SALINE_MIXER.get());
        var mixer = (SalineMixerBlockEntity) h.getBlockEntity(FLOOR);
        mixer.pipes().fill(new FluidStack(Fluids.WATER, 2000), IFluidHandler.FluidAction.EXECUTE);
        mixer.items().setStackInSlot(SalineMixerBlockEntity.SLOT_SUGAR, new ItemStack(Items.SUGAR));
        mixer.items().setStackInSlot(SalineMixerBlockEntity.SLOT_KELP, new ItemStack(Items.KELP));
        mixer.items().setStackInSlot(SalineMixerBlockEntity.SLOT_REDSTONE, new ItemStack(Items.REDSTONE));
        mixer.energy().set(20_000);
        h.runAfterDelay(SalineMixerBlockEntity.BATCH_TICKS + 5, () -> {
            h.assertTrue(mixer.saline().getFluidAmount() == 1000, "saline " + mixer.saline().getFluidAmount());
            h.assertTrue(mixer.water().getFluidAmount() == 1000, "water " + mixer.water().getFluidAmount());
            mixer.items().setStackInSlot(SalineMixerBlockEntity.SLOT_BOTTLE_IN, new ItemStack(Items.GLASS_BOTTLE, 2));
        });
        h.runAfterDelay(SalineMixerBlockEntity.BATCH_TICKS + 10, () -> {
            h.assertTrue(mixer.items().getStackInSlot(SalineMixerBlockEntity.SLOT_BOTTLE_OUT).getCount() == 2, "bottles not filled");
            h.assertTrue(mixer.saline().getFluidAmount() == 500, "bottling used " + (1000 - mixer.saline().getFluidAmount()));
            h.succeed();
        });
    }

    @GameTest(template = "platform", timeoutTicks = 1300)
    public static void printerPrintsBody(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.north(3));
        FormActions.detach(p, 3);
        HeadlessBody old = (HeadlessBody) h.getLevel().getEntity(state(p).body());
        old.hurt(h.getLevel().damageSources().generic(), 100);
        h.assertTrue(old.isCorpse(), "the old body did not become a corpse");
        h.setBlock(FLOOR, ModRegistry.BODY_PRINTER.get());
        var printer = (BodyPrinterBlockEntity) h.getBlockEntity(FLOOR);
        net.minecraft.world.item.Item[] items = {Items.ROTTEN_FLESH, Items.BONE_BLOCK, Items.REDSTONE_BLOCK, Items.SLIME_BALL};
        for (int i = 0; i < 4; i++) printer.items().setStackInSlot(i, new ItemStack(items[i], BodyPrinterBlockEntity.NEED[i]));
        printer.saline().fill(new FluidStack(ModFluids.SALINE.source.get(), 2000), IFluidHandler.FluidAction.EXECUTE);
        printer.energy().set(250_000);
        h.assertTrue(printer.start(p), "printer refused a jar");
        h.runAfterDelay(BodyPrinterBlockEntity.TICKS + 10, () -> {
            HeadlessBody fresh = (HeadlessBody) h.getLevel().getEntity(state(p).body());
            h.assertTrue(fresh != null && fresh != old && !fresh.isCorpse() && fresh.hasCollar(), "no fresh body with a collar");
            h.assertTrue(old.isAlive() && old.isCorpse(), "the old corpse (with its belongings) vanished");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 260)
    public static void cradleRefillsDockedHead(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        h.setBlock(FLOOR.east(2), ModRegistry.CRADLE.get());
        var cradle = (LifeSupportCradleBlockEntity) h.getBlockEntity(FLOOR.east(2));
        separate(h, p, FLOOR, cradle);
        state(p).setSaline(100);
        state(p).setIntegrity(5);
        state(p).setCharge(0);
        cradle.saline().fill(new FluidStack(ModFluids.SALINE.source.get(), 2000), IFluidHandler.FluidAction.EXECUTE);
        cradle.energy().set(50_000);
        h.runAfterDelay(220, () -> {
            h.assertTrue(state(p).saline() == state(p).maxSaline(), "saline " + state(p).saline());
            h.assertTrue(state(p).integrity() > 5, "integrity " + state(p).integrity());
            h.assertTrue(state(p).charge() > 0, "no charge");
            done(h, p);
        });
    }

    @GameTest(template = "platform")
    public static void newModulesTick(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 5);
        state(p).setGoggles(new ItemStack(ModRegistry.RELAY_GOGGLES.get()));
        double base = NeuroLink.range(state(p));
        state(p).setModule(0, new ItemStack(ModRegistry.module(ModuleType.SPIDER_LEGS)));
        state(p).setModule(1, new ItemStack(ModRegistry.module(ModuleType.SELF_RIGHTING_GYRO)));
        state(p).setModule(2, new ItemStack(ModRegistry.module(ModuleType.HUB_ANTENNA)));
        ModuleEffects.apply(p, state(p));
        h.assertTrue(Math.abs(p.getAttributeValue(Attributes.STEP_HEIGHT) - 1.05) < 1e-6, "step " + p.getAttributeValue(Attributes.STEP_HEIGHT));
        h.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1, "gyro gives no knockback resistance");
        h.assertTrue(Math.abs(NeuroLink.range(state(p)) - base * 1.5) < 1e-6, "hub antenna range " + NeuroLink.range(state(p)));
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void gogglesUpgradeExtendsRange(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        separate(h, p, FLOOR.east(1), null);
        HeadJarEntity head = h.getEntities(ModRegistry.HEAD_JAR.get()).get(0);
        h.assertTrue(NeuroLink.range(state(p)) == 48, "mk I range " + NeuroLink.range(state(p)));
        h.assertTrue(FormActions.fitGoggles(p, new ItemStack(ModRegistry.RELAY_GOGGLES_MK2.get()), head, null), "fit failed");
        h.assertTrue(NeuroLink.range(state(p)) == 160, "mk II range " + NeuroLink.range(state(p)));
        h.assertTrue(p.getInventory().contains(new ItemStack(ModRegistry.RELAY_GOGGLES.get())), "old goggles not returned");
        h.assertTrue(head.goggles().is(ModRegistry.RELAY_GOGGLES_MK2.get()), "head still wears the old pair");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void bareJarWorksHeadMachines(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        h.setBlock(FLOOR.east(2), ModRegistry.CHARGING_DOCK.get());
        h.setBlock(FLOOR.west(2), Blocks.LEVER);
        FormActions.detach(p, 3);
        BlockPos dock = h.absolutePos(FLOOR.east(2)), lever = h.absolutePos(FLOOR.west(2));
        var onDock = new PlayerInteractEvent.RightClickBlock(p, InteractionHand.MAIN_HAND, dock, new BlockHitResult(Vec3.atCenterOf(dock), Direction.UP, dock, false));
        var onLever = new PlayerInteractEvent.RightClickBlock(p, InteractionHand.MAIN_HAND, lever, new BlockHitResult(Vec3.atCenterOf(lever), Direction.UP, lever, false));
        h.assertFalse(NeoForge.EVENT_BUS.post(onDock).isCanceled(), "a jar could not work a head machine");
        h.assertTrue(NeoForge.EVENT_BUS.post(onLever).isCanceled(), "a bare jar pulled a lever");
        h.assertTrue(FormEvents.lockedOut(p), "lockout lost");
        done(h, p);
    }
}
