package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.AssemblyGantryBlockEntity;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.mecha.HopperAnimation;
import com.coolfreeze23.headinajar.mecha.HopperView;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.mecha.Wear;
import com.coolfreeze23.headinajar.menu.AssemblyGantryMenu;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;
import java.util.UUID;

/**
 * Wear: the blows a machine takes are remembered by the side they came from (synced, saved, forgotten when it is mended
 * to full); its dents show at three quarters, half and a quarter of its health on the side hit most; an old world's
 * machine, with no blows remembered, shows none; below a quarter a walker limps on the leg of the side hit more, at its
 * walking pace. Blows are struck by players' attacks, the repair is the Gantry's button packet.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class WearGameTests {
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

    /** A machine facing south (its right is west, its left east), unpiloted, with a full battery. */
    private static MechaEntity machine(GameTestHelper h, MechaFrame frame, BlockPos at, UUID owner) {
        MechaEntity m = MechaLineGameTests.mecha(h, frame, at, owner, MechaModule.HYDRAULIC_LEGS);
        m.yBodyRotO = 0;
        return m;
    }

    /** A player standing at {@code at} strikes the machine with an iron sword, as a click on it does (no wait between blows). */
    private static void strike(GameTestHelper h, MechaEntity m, BlockPos at) {
        ServerPlayer p = player(h, at);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, m.position().add(0, 1, 0));
        m.invulnerableTime = 0;
        p.attack(m);
        h.getLevel().getServer().getPlayerList().remove(p);
    }

    private static String stages(MechaEntity m) {
        return Arrays.toString(Wear.stages(m.hitBearings(), m.getHealth(), m.getMaxHealth()));
    }

    /** Blows from the machine's right are remembered as from its right, are sent to watchers, and survive a reload. */
    @GameTest(template = "platform", timeoutTicks = 40)
    public static void blowsAreRememberedBySideAndSurviveAReload(GameTestHelper h) {
        MechaEntity m = machine(h, MechaFrame.HOPPER, FLOOR, UUID.randomUUID());
        h.runAfterDelay(2, () -> {
            h.assertTrue(m.hitBearings() == 0, "a new machine remembers blows");
            float before = m.getHealth();
            // west of a machine facing south: its right
            for (int i = 0; i < 3; i++) strike(h, m, FLOOR.west(2));
            h.assertTrue(m.getHealth() < before, "the blows did no damage");
            int[] n = Wear.counts(m.hitBearings());
            h.assertTrue(n[Wear.RIGHT] == 3 && n[Wear.LEFT] == 0 && n[Wear.FRONT] == 0 && n[Wear.BACK] == 0, "not three from the right: " + Arrays.toString(n));
            // one from the front (south of it)
            strike(h, m, FLOOR.south(2));
            h.assertValueEqual(Wear.counts(m.hitBearings())[Wear.FRONT], 1, "blows from the front");
            h.assertValueEqual(Wear.most(m.hitBearings()), Wear.RIGHT, "the side hit most");
            m.setHealth(m.getMaxHealth() * 0.4f);
            h.assertValueEqual(stages(m), "[0, 2, 0, 0]", "dents at two fifths, hit mostly from the right");
            CompoundTag tag = new CompoundTag();
            h.assertTrue(m.save(tag), "could not save");
            h.assertTrue(tag.contains("HitBearings"), "the blows were not saved");
            Entity back = EntityType.loadEntityRecursive(tag, h.getLevel(), e -> e);
            h.assertTrue(back instanceof MechaEntity again && again.hitBearings() == m.hitBearings(), "the blows did not survive the reload");
            h.assertValueEqual(stages((MechaEntity) back), "[0, 2, 0, 0]", "dents after the reload");
            done(h);
        });
    }

    /** The stage by health, on the side hit most, one less on a side hit twice; nothing without blows; eight blows kept. */
    @GameTest(template = "platform", timeoutTicks = 40)
    public static void dentStagesFollowHealthOnTheSideHitMost(GameTestHelper h) {
        MechaEntity m = machine(h, MechaFrame.BULWARK, FLOOR, UUID.randomUUID());
        h.runAfterDelay(2, () -> {
            float max = m.getMaxHealth();
            m.setHealth(max * 0.2f);
            h.assertValueEqual(stages(m), "[0, 0, 0, 0]", "a machine never hit, at a fifth of its health");
            // from behind (north of a machine facing south), three times; once from the left (east)
            strike(h, m, FLOOR.north(2));
            strike(h, m, FLOOR.north(2));
            strike(h, m, FLOOR.north(2));
            strike(h, m, FLOOR.east(2));
            float[] shares = {1f, 0.8f, 0.7f, 0.45f, 0.2f};
            String[] want = {"[0, 0, 0, 0]", "[0, 0, 0, 0]", "[0, 0, 1, 0]", "[0, 0, 2, 0]", "[0, 0, 3, 0]"};
            for (int i = 1; i < shares.length; i++) {
                m.setHealth(max * shares[i]);
                h.assertValueEqual(stages(m), want[i], "dents at " + shares[i]);
            }
            // a second blow from the left: that side shows one stage less
            strike(h, m, FLOOR.east(2));
            m.setHealth(max * 0.2f);
            h.assertValueEqual(stages(m), "[0, 0, 3, 2]", "a side hit twice besides");
            // only the last eight blows count
            long b = 0;
            for (int i = 0; i < 12; i++) b = Wear.push(b, i < 4 ? 0 : 90);
            h.assertTrue(Arrays.equals(Wear.counts(b), new int[]{0, 8, 0, 0}), "more than eight kept: " + Arrays.toString(Wear.counts(b)));
            h.assertValueEqual(Wear.side(Wear.at(Wear.push(0, 359), 0)), Wear.FRONT, "a bearing just left of ahead");
            // a parked machine's kept look is rebuilt when a stage changes, and says why
            m.setHealth(max * 0.7f);
            var light = com.coolfreeze23.headinajar.mecha.ParkedLook.light(m);
            var before = com.coolfreeze23.headinajar.mecha.ParkedLook.key(m, light, 0, m.yBodyRot, 0, 0, 0);
            m.setHealth(max * 0.45f);
            var after = com.coolfreeze23.headinajar.mecha.ParkedLook.key(m, light, 0, m.yBodyRot, 0, 0, 0);
            h.assertFalse(after.matches(before), "a new dent stage kept the old parked look");
            h.assertValueEqual(after.differs(before), "dents", "why the parked look was rebuilt");
            done(h);
        });
    }

    /** An old world's machine (no blows saved) loads clean at any health; a machine never hit saves no new key. */
    @GameTest(template = "platform", timeoutTicks = 40)
    public static void anOldWorldsMachineStartsClean(GameTestHelper h) {
        MechaEntity m = machine(h, MechaFrame.HOPPER, FLOOR, UUID.randomUUID());
        h.runAfterDelay(2, () -> {
            CompoundTag tag = new CompoundTag();
            h.assertTrue(m.save(tag), "could not save");
            h.assertFalse(tag.contains("HitBearings"), "a machine never hit saved a key");
            // as saved by the last release: battered to a fifth, no blows
            tag.putFloat("Health", m.getMaxHealth() * 0.2f);
            tag.remove("UUID");
            Entity back = EntityType.loadEntityRecursive(tag, h.getLevel(), e -> e);
            h.assertTrue(back instanceof MechaEntity, "did not load");
            MechaEntity old = (MechaEntity) back;
            h.assertTrue(old.getHealth() < old.getMaxHealth() * 0.25f, "the health did not load: " + old.getHealth());
            h.assertTrue(old.hitBearings() == 0, "blows from nowhere");
            h.assertValueEqual(stages(old), "[0, 0, 0, 0]", "an old machine's dents");
            // a fresh machine is at full health: clean
            h.assertTrue(m.getHealth() >= m.getMaxHealth(), "a new machine is not whole: " + m.getHealth() + " / " + m.getMaxHealth());
            done(h);
        });
    }

    /** The Gantry's Repair mends to full at the press and the blows go with the dents. */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void aRepairAtTheGantryForgetsTheBlows(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(3));
        h.setBlock(FLOOR, ModRegistry.ASSEMBLY_GANTRY.get());
        var gantry = (AssemblyGantryBlockEntity) h.getBlockEntity(FLOOR);
        gantry.energy().set(100_000);
        MechaEntity m = machine(h, MechaFrame.HOPPER, FLOOR.above(), p.getUUID());
        h.runAfterDelay(2, () -> {
            strike(h, m, FLOOR.above().south(2));
            strike(h, m, FLOOR.above().south(2));
            m.setHealth(m.getMaxHealth() * 0.3f);
            h.assertValueEqual(stages(m), "[2, 0, 0, 0]", "dents before the repair");
            gantry.items().setStackInSlot(AssemblyGantryBlockEntity.SLOT_PLATING, new ItemStack(ModRegistry.MECHA_PLATING.get(), 16));
            p.containerMenu = gantry.createMenu(7, p.getInventory(), p);
            int refusals = Refusals.delivered(p).size();
            p.connection.handleContainerButtonClick(new ServerboundContainerButtonClickPacket(p.containerMenu.containerId, AssemblyGantryMenu.REPAIR));
            h.assertTrue(Refusals.delivered(p).size() == refusals, "Repair was refused: " + Refusals.lastDelivered(p));
            h.assertTrue(m.getHealth() >= m.getMaxHealth() - 1e-3, "not mended to full: " + m.getHealth());
            h.assertTrue(m.hitBearings() == 0, "the blows were not forgotten");
            h.assertValueEqual(stages(m), "[0, 0, 0, 0]", "dents after the repair");
            h.assertTrue(gantry.job() == AssemblyGantryBlockEntity.JOB_REPAIR, "no weld pass (the dents step down over it)");
            done(h, p);
        });
    }

    /**
     * Below a quarter of its health with power a biped limps on the leg of the side hit more, at its walking pace; a flat
     * battery keeps the slow limp; the Skitter, whose feet are placed on the ground by code, keeps its trot.
     */
    @GameTest(template = "platform", timeoutTicks = 40)
    public static void aDamagedWalkerLimpsOnItsWorseSide(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = machine(h, MechaFrame.BULWARK, FLOOR.east(3), p.getUUID());
        MechaEntity skitter = MechaLineGameTests.mecha(h, MechaFrame.SKITTER, FLOOR.west(3), p.getUUID());
        h.runAfterDelay(2, () -> {
            FormActions.detach(p, 3);
            p.interactOn(m, InteractionHand.MAIN_HAND);
            h.assertTrue(p.getVehicle() == m && FormActions.state(p).form() == Form.PILOT, "the click did not board");
            m.setOnGround(true);
            HopperView v = new HopperView();
            v.speed = 0.24;
            double t = h.getLevel().getGameTime() + 200;
            h.assertValueEqual(clip(HopperAnimation.main(m, v, t)), "walk_c", "a sound machine's walk");
            // struck from its left (east of a machine facing south), then brought to a fifth
            BlockPos at = FLOOR.east(3);
            strike(h, m, at.east(2));
            strike(h, m, at.east(2));
            m.setHealth(m.getMaxHealth() * 0.2f);
            h.assertValueEqual(clip(HopperAnimation.main(m, v, t)), "limp_l", "struck from the left");
            strike(h, m, at.west(2));
            strike(h, m, at.west(2));
            strike(h, m, at.west(2));
            m.setHealth(m.getMaxHealth() * 0.2f);
            h.assertValueEqual(clip(HopperAnimation.main(m, v, t)), "limp_r", "struck more from the right");
            m.setBattery(0);
            h.assertValueEqual(clip(HopperAnimation.main(m, v, t)), "limp", "a flat battery");
            m.setBattery(m.batteryCapacity());
            skitter.setHealth(skitter.getMaxHealth() * 0.2f);
            h.assertFalse(HopperAnimation.limps(skitter), "the Skitter limps when damaged");
            done(h, p);
        });
    }

    /**
     * A 0.5.0 client (payload version 4) and a server of this version are refused at the handshake by NeoForge's own
     * negotiation, with a reason naming both versions, in either direction; the same versions agree. Run on the payloads
     * this game really registered.
     */
    @GameTest(template = "platform", timeoutTicks = 20)
    @SuppressWarnings("unchecked")
    public static void anOlderVersionIsRefusedAtTheHandshake(GameTestHelper h) throws ReflectiveOperationException {
        var field = net.neoforged.neoforge.network.registration.NetworkRegistry.class.getDeclaredField("PAYLOAD_REGISTRATIONS");
        field.setAccessible(true);
        var byProtocol = (java.util.Map<net.minecraft.network.ConnectionProtocol, java.util.Map<net.minecraft.resources.ResourceLocation,
                net.neoforged.neoforge.network.registration.PayloadRegistration<?>>>) field.get(null);
        java.util.List<net.neoforged.neoforge.network.negotiation.NegotiableNetworkComponent> ours = new java.util.ArrayList<>(), old = new java.util.ArrayList<>();
        for (var reg : byProtocol.get(net.minecraft.network.ConnectionProtocol.PLAY).values()) {
            if (!reg.id().getNamespace().equals(HeadInAJar.MOD_ID)) continue;
            var c = new net.neoforged.neoforge.network.negotiation.NegotiableNetworkComponent(reg);
            ours.add(c);
            // the last release sent the same payloads at version 4
            old.add(new net.neoforged.neoforge.network.negotiation.NegotiableNetworkComponent(c.id(), c.version().equals(com.coolfreeze23.headinajar.network.Network.VERSION) ? "4" : c.version(), c.flow(), c.optional()));
        }
        h.assertTrue(ours.stream().anyMatch(c -> c.version().equals("5")), "no payload at version 5");
        var same = net.neoforged.neoforge.network.negotiation.NetworkComponentNegotiator.negotiate(ours, ours);
        h.assertTrue(same.success(), "the same version was refused: " + same.failureReasons());
        for (boolean oldServer : new boolean[]{false, true}) {
            var r = oldServer ? net.neoforged.neoforge.network.negotiation.NetworkComponentNegotiator.negotiate(old, ours)
                    : net.neoforged.neoforge.network.negotiation.NetworkComponentNegotiator.negotiate(ours, old);
            h.assertFalse(r.success(), "a 0.5.0 " + (oldServer ? "server" : "client") + " was let in");
            net.minecraft.network.chat.Component why = r.failureReasons().values().iterator().next();
            h.assertTrue(namesVersions(why), "the reason does not name the versions: " + why);
        }
        h.succeed();
    }

    /** NeoForge's version-mismatch reason with both versions, anywhere in the (possibly untranslated) message. */
    private static boolean namesVersions(Object part) {
        if (part instanceof net.minecraft.network.chat.Component c && c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t) {
            if (t.getKey().endsWith("version.mismatch") && java.util.Arrays.asList(t.getArgs()).containsAll(java.util.List.of("4", "5"))) return true;
            for (Object arg : t.getArgs()) if (namesVersions(arg)) return true;
        }
        return false;
    }

    private static String clip(HopperAnimation.Choice c) {
        return c == null ? null : c.clip().name();
    }
}
