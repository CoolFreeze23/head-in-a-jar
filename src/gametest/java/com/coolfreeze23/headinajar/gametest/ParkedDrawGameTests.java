package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.item.MechaModuleItem;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.mecha.ParkedLook;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A parked machine is drawn from geometry kept since it parked: when it counts as parked, and which changes make a new
 * look that must be built again (and which do not).
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class ParkedDrawGameTests {
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

    private static List<ItemStack> slots(MechaFrame frame, MechaModule... mods) {
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < frame.totalSlots(); i++) slots.add(ItemStack.EMPTY);
        Set<Integer> used = new HashSet<>();
        for (MechaModule mod : mods) {
            int i = frame.firstIndex(mod.slot);
            while (used.contains(i)) i++;
            used.add(i);
            ItemStack stack = new ItemStack(ModRegistry.mechaModule(mod));
            MechaModuleItem.setSlider(stack, 50);
            slots.set(i, stack);
        }
        return slots;
    }

    private static MechaEntity mecha(GameTestHelper h, MechaFrame frame, BlockPos at, UUID owner, MechaModule... mods) {
        MechaEntity m = h.spawn(ModRegistry.MECHA.get(frame).get(), at);
        m.setModules(slots(frame, mods));
        m.setOwner(owner);
        m.setBattery(m.batteryCapacity());
        m.setYRot(0);
        m.setYBodyRot(0);
        // a new mob's head starts a few degrees off its yaw and the body follows it: set as the Assembly Gantry sets a machine
        m.setYHeadRot(0);
        return m;
    }

    private static double now(MechaEntity m) {
        return m.level().getGameTime();
    }

    /** The look as a client would key it now: its light from the level, no overlay, its facing. */
    private static ParkedLook.Key key(MechaEntity m) {
        return ParkedLook.key(m, ParkedLook.light(m), 0, m.yBodyRot, 0, 0, 0);
    }

    /** The player, as a jar, climbs in by a real click on the machine. */
    private static void board(GameTestHelper h, ServerPlayer p, MechaEntity m) {
        if (FormActions.state(p).form() == Form.BODY) FormActions.detach(p, 3);
        p.interactOn(m, InteractionHand.MAIN_HAND);
        h.assertTrue(p.getVehicle() == m && FormActions.state(p).form() == Form.PILOT, "the click did not board: " + FormActions.state(p).form());
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void aParkedMachineKeepsOneLook(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = mecha(h, MechaFrame.HOPPER, FLOOR.east(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS, MechaModule.DRILL, MechaModule.NIGHT_OPTICS);
        ParkedLook.Key[] first = new ParkedLook.Key[1];
        h.runAfterDelay(5, () -> {
            h.assertTrue(ParkedLook.parked(m, now(m)), "a machine nobody has boarded is not parked");
            first[0] = key(m);
        });
        h.runAfterDelay(45, () -> {
            h.assertTrue(ParkedLook.parked(m, now(m)), "a parked machine stopped being parked by itself");
            h.assertTrue(key(m).matches(first[0]), "a parked machine's look changed with nothing done to it: " + first[0] + " / " + key(m));
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void boardingEndsTheParkedLookAndGettingOutBringsItBack(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = mecha(h, MechaFrame.HOPPER, FLOOR.east(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS);
        ParkedLook.Key[] before = new ParkedLook.Key[1];
        long[] out = new long[1];
        h.runAfterDelay(5, () -> {
            before[0] = key(m);
            board(h, p, m);
            h.assertFalse(ParkedLook.parked(m, now(m)), "a boarded machine still counts as parked");
        });
        h.runAfterDelay(25, () -> {
            h.assertFalse(ParkedLook.parked(m, now(m)), "a piloted machine counts as parked");
            // sneak and the jar menu key held a second: the client sends the eject, the server's handler takes it
            Network.action(p, new Network.JarAction(Network.Action.EJECT));
            h.assertTrue(p.getVehicle() == null, "the pilot did not get out");
            out[0] = m.level().getGameTime();
            h.assertFalse(ParkedLook.parked(m, now(m)), "a machine still shutting down counts as parked");
        });
        h.runAfterDelay(26, () -> h.succeedWhen(() -> {
            h.assertTrue(ParkedLook.parked(m, now(m)), "not parked again yet");
            long took = m.level().getGameTime() - out[0];
            h.assertTrue(took > 20, "parked again only " + took + " ticks after the pilot got out, before the hoop closed");
            h.assertTrue(key(m).matches(before[0]), "the same machine with the same modules parked in the same place has a new look: " + before[0] + " / " + key(m));
            h.getLevel().getServer().getPlayerList().remove(p);
        }));
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void aModuleFittedOrTakenOutIsANewLook(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = mecha(h, MechaFrame.BULWARK, FLOOR.east(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS);
        h.runAfterDelay(5, () -> {
            ParkedLook.Key bare = key(m);
            // what the Assembly Gantry does to a machine's slots
            m.setModules(slots(MechaFrame.BULWARK, MechaModule.HYDRAULIC_LEGS, MechaModule.MISSILE_POD));
            ParkedLook.Key fitted = key(m);
            h.assertFalse(fitted.matches(bare), "a missile pod fitted to a parked machine did not make a new look");
            h.assertTrue(ParkedLook.parked(m, now(m)), "fitting a module stopped it being parked");
            m.setModules(slots(MechaFrame.BULWARK, MechaModule.HYDRAULIC_LEGS));
            h.assertFalse(key(m).matches(fitted), "the pod taken out again did not make a new look");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void theBatteryRacksLampsStepWithTheCharge(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = mecha(h, MechaFrame.HOPPER, FLOOR.east(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS, MechaModule.BATTERY_RACK);
        h.runAfterDelay(5, () -> {
            m.setBattery(m.batteryCapacity());
            ParkedLook.Key full = key(m);
            h.assertTrue(ParkedLook.lamps(m) == 3, "a full battery lights " + ParkedLook.lamps(m) + " lamps");
            m.setBattery(m.batteryCapacity() * 9 / 10);
            h.assertTrue(key(m).matches(full), "a tenth spent, still three lamps, made a new look");
            m.setBattery(m.batteryCapacity() / 2);
            h.assertTrue(ParkedLook.lamps(m) == 2, "half a battery lights " + ParkedLook.lamps(m) + " lamps");
            h.assertFalse(key(m).matches(full), "a lamp going out did not make a new look");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 80)
    public static void aBlowIsDrawnAsItLandsThenTheSameLookComesBack(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = mecha(h, MechaFrame.HOPPER, FLOOR.east(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS);
        ParkedLook.Key[] before = new ParkedLook.Key[1];
        h.runAfterDelay(5, () -> {
            before[0] = key(m);
            Vec3 from = m.position().add(-2, 0, 0);
            p.moveTo(from.x, from.y, from.z, -90, 0);
            p.attack(m);
            h.assertTrue(m.hurtTime > 0, "the blow did not land");
            h.assertFalse(ParkedLook.parked(m, now(m)), "a machine flinching from a blow counts as parked");
        });
        h.runAfterDelay(6, () -> h.succeedWhen(() -> {
            h.assertTrue(ParkedLook.parked(m, now(m)), "not settled after the blow yet");
            h.assertTrue(key(m).matches(before[0]), "the blow left a new look on a machine it did not turn: " + before[0] + " / " + key(m));
            h.getLevel().getServer().getPlayerList().remove(p);
        }));
    }

    @GameTest(template = "platform", timeoutTicks = 80)
    public static void aTorchSetDownBesideAParkedMachineRelightsIt(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = mecha(h, MechaFrame.HOPPER, FLOOR.east(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS);
        ParkedLook.Key[] before = new ParkedLook.Key[1];
        h.runAfterDelay(5, () -> {
            before[0] = key(m);
            // a torch put on the floor beside it by a click with the torch in hand
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TORCH, 4));
            BlockPos floor = h.absolutePos(FLOOR.east(4).below());
            p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false));
            h.assertTrue(h.getLevel().getBlockState(floor.above()).is(net.minecraft.world.level.block.Blocks.TORCH), "the torch was not placed");
        });
        h.runAfterDelay(6, () -> h.succeedWhen(() -> {
            h.assertFalse(key(m).matches(before[0]), "the torch's light did not make a new look");
            h.assertTrue(ParkedLook.parked(m, now(m)), "a torch beside it stopped it being parked");
            h.getLevel().getServer().getPlayerList().remove(p);
        }));
    }

    @GameTest(template = "platform", timeoutTicks = 20)
    public static void aKeptLookIsCheckedEveryFewSecondsAndAFlickeringOneRests(GameTestHelper h) {
        // each machine is checked once in every VERIFY frames, and two machines not on the same frame
        for (int id : new int[]{3, 4, 1000}) {
            int due = 0;
            for (long f = 0; f < ParkedLook.Schedule.VERIFY; f++) if (ParkedLook.Schedule.verifyDue(id, f)) due++;
            h.assertTrue(due == 1, "machine " + id + " is checked " + due + " times in " + ParkedLook.Schedule.VERIFY + " frames");
        }
        boolean same = false;
        for (long f = 0; f < ParkedLook.Schedule.VERIFY; f++) same |= ParkedLook.Schedule.verifyDue(3, f) && ParkedLook.Schedule.verifyDue(4, f);
        h.assertFalse(same, "two neighbouring machines are checked on the same frame");
        // a look rebuilt every hundred frames (a door's light now and then) never rests
        ParkedLook.Schedule calm = new ParkedLook.Schedule();
        for (long f = 0; f < 2000; f += 100) h.assertFalse(calm.built(f), "a machine rebuilt every 100 frames was sent to rest at " + f);
        // one rebuilt every few frames (a flickering light) rests, drawn the ordinary way for a while, then is tried again
        ParkedLook.Schedule busy = new ParkedLook.Schedule();
        boolean rested = false;
        long at = 0;
        for (long f = 0; f < 50 && !rested; f += 5) {
            rested = busy.built(f);
            at = f;
        }
        h.assertTrue(rested, "a machine rebuilt every 5 frames never rests");
        h.assertTrue(at <= 5 * ParkedLook.Schedule.BURST, "it rested only at frame " + at);
        h.assertTrue(busy.resting(at + ParkedLook.Schedule.REST - 1), "it stopped resting early");
        h.assertFalse(busy.resting(at + ParkedLook.Schedule.REST), "it rests for ever");
        h.succeed();
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void aLookKeptUnderOneShaderPackIsANewLookUnderAnother(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = mecha(h, MechaFrame.HOPPER, FLOOR.east(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS);
        h.runAfterDelay(5, () -> {
            int light = ParkedLook.light(m);
            ParkedLook.Key none = ParkedLook.key(m, light, 0, m.yBodyRot, 0, 0, 0);
            ParkedLook.Key bsl = ParkedLook.key(m, light, 0, m.yBodyRot, 0, 0, 0, "BSLClassic_v10.0.zip");
            ParkedLook.Key complementary = ParkedLook.key(m, light, 0, m.yBodyRot, 0, 0, 0, "ComplementaryReimagined_r5.5.1.zip");
            h.assertTrue(none.matches(ParkedLook.key(m, light, 0, m.yBodyRot, 0, 0, 0, null)), "the same look without a pack does not match itself");
            h.assertTrue(bsl.matches(ParkedLook.key(m, light, 0, m.yBodyRot, 0, 0, 0, "BSLClassic_v10.0.zip")), "the same look under BSL does not match itself");
            h.assertFalse(bsl.matches(none), "a look kept without a shader pack is used under BSL (the pack's vertices are longer)");
            h.assertFalse(complementary.matches(bsl), "a look kept under BSL is used under Complementary (another pack's entity ids)");
            h.assertTrue("shader pack".equals(bsl.differs(none)), "the change of pack is reported as " + bsl.differs(none));
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 20)
    public static void onlyTheIrisAndShaderPacksItWasCheckedWithKeepParkedMachines(GameTestHelper h) {
        h.assertTrue(ParkedLook.ShaderPacks.knownIris("1.8.12-snapshot+mc1.21.1-local"), "the Iris jar of the pack (1.8.12) is not known");
        h.assertTrue(ParkedLook.ShaderPacks.knownIris("1.8.12+mc1.21.1"), "Iris 1.8.12 as Modrinth names it is not known");
        for (String v : new String[]{"1.8.11+mc1.21.1", "1.8.13", "1.8.1", "1.8.120", "1.9.0", "", null})
            h.assertFalse(ParkedLook.ShaderPacks.knownIris(v), "Iris " + v + " counts as checked");
        h.assertTrue(ParkedLook.ShaderPacks.knownPack("BSLClassic_v10.0.zip"), "BSL is not known");
        h.assertTrue(ParkedLook.ShaderPacks.knownPack("ComplementaryReimagined_r5.5.1.zip"), "Complementary is not known");
        for (String pack : new String[]{"BSLClassic_v8.4.01.2.zip", "ComplementaryReimagined_r5.4.zip", "BSLClassic_v10.0", "bslclassic_v10.0.zip",
                "Sildur's Enhanced Default v1.171 Fast.zip", "(internal)", "", null})
            h.assertFalse(ParkedLook.ShaderPacks.knownPack(pack), "the pack " + pack + " counts as checked");
        h.succeed();
    }

    @GameTest(template = "platform", timeoutTicks = 20)
    public static void aCheckComparesEveryByteAShaderPackAddsToAVertex(GameTestHelper h) {
        // two quads' worth of vertices in Iris's entity layout: the game's 36 bytes, then the entity ids, the middle of the
        // texture and the tangent (54 bytes a vertex)
        int size = 54;
        java.nio.ByteBuffer a = java.nio.ByteBuffer.allocate(size * 8).order(java.nio.ByteOrder.nativeOrder());
        java.util.Random r = new java.util.Random(7);
        for (int v = 0; v < 8; v++) {
            for (int c = 0; c < 3; c++) a.putFloat(v * size + 4 * c, r.nextFloat() * 4 - 2);
            for (int b = 12; b < size; b++) a.put(v * size + b, (byte) r.nextInt(256));
        }
        a.putFloat(5 * size, 0.5f);
        long kept = ParkedLook.hash(a.duplicate().order(a.order()), size);
        java.util.function.Function<java.util.function.Consumer<java.nio.ByteBuffer>, Long> changed = change -> {
            java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(a.capacity()).order(a.order());
            b.put(a.duplicate().order(a.order())).flip();
            change.accept(b);
            return ParkedLook.hash(b, size);
        };
        h.assertTrue(changed.apply(b -> { }) == kept, "the same vertices hash differently");
        h.assertTrue(changed.apply(b -> b.put(3 * size + 35, (byte) 99)) == kept, "the padding byte (never written) counts");
        h.assertTrue(changed.apply(b -> b.putFloat(5 * size, b.getFloat(5 * size) + 2e-6f)) == kept, "a millionth of a block counts");
        h.assertFalse(changed.apply(b -> b.putShort(2 * size + 36, (short) 10101)) == kept, "another entity id is not seen");
        h.assertFalse(changed.apply(b -> b.putFloat(6 * size + 42, 0.25f)) == kept, "another middle of the texture is not seen");
        h.assertFalse(changed.apply(b -> b.put(7 * size + 52, (byte) (b.get(7 * size + 52) + 1))) == kept, "another tangent is not seen");
        h.assertFalse(changed.apply(b -> b.put(size + 33, (byte) (b.get(size + 33) + 1))) == kept, "another normal is not seen");
        // the game's own layout: what was compared before shader packs (nothing after the 36th byte is read)
        java.nio.ByteBuffer game = java.nio.ByteBuffer.allocate(36 * 4).order(a.order());
        for (int v = 0; v < 4; v++) for (int b = 0; b < 36; b++) game.put(v * 36 + b, a.get(v * size + b));
        long gameHash = ParkedLook.hash(game.duplicate().order(a.order()), 36);
        game.put(35, (byte) 1);
        h.assertTrue(ParkedLook.hash(game.duplicate().order(a.order()), 36) == gameHash, "the game's padding byte counts");
        h.succeed();
    }
}
