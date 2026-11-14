package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.AssemblyGantryBlockEntity;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.HeadRegistry;
import com.coolfreeze23.headinajar.item.MechaModuleItem;
import com.coolfreeze23.headinajar.mecha.Hopper;
import com.coolfreeze23.headinajar.mecha.HopperAnimation;
import com.coolfreeze23.headinajar.mecha.HopperNet;
import com.coolfreeze23.headinajar.mecha.HopperSockets;
import com.coolfreeze23.headinajar.mecha.HopperView;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.MechaLogic;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.menu.AssemblyGantryMenu;
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

/** The Hopper on GeckoLib: its synced moments and the clips they choose, the Pogo Protocol, the build, the parachute. */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class HopperGameTests {
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

    /** A Hopper with these modules in their slots, owned by {@code owner}, battery full. */
    private static MechaEntity hopper(GameTestHelper h, BlockPos at, UUID owner, MechaModule... mods) {
        MechaEntity m = h.spawn(ModRegistry.HOPPER.get(), at);
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
        return m;
    }

    /** The player, as a jar, climbs in by a real click on the mecha. */
    private static void board(GameTestHelper h, ServerPlayer p, MechaEntity m) {
        if (FormActions.state(p).form() == Form.BODY) FormActions.detach(p, 3);
        p.interactOn(m, InteractionHand.MAIN_HAND);
        h.assertTrue(p.getVehicle() == m && FormActions.state(p).form() == Form.PILOT, "the click did not board: " + FormActions.state(p).form());
        // the pilot's client moves a ridden mecha and the server learns it stands from that; the test has no client
        m.setOnGround(true);
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

    @GameTest(template = "platform", timeoutTicks = 260)
    public static void gantryBuildPlaysAndBoardingSkipsIt(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(3));
        h.setBlock(FLOOR, ModRegistry.ASSEMBLY_GANTRY.get());
        var gantry = (AssemblyGantryBlockEntity) h.getBlockEntity(FLOOR);
        gantry.items().setStackInSlot(AssemblyGantryBlockEntity.SLOT_FRAME, new ItemStack(ModRegistry.frameItem(MechaFrame.HOPPER)));
        gantry.items().setStackInSlot(AssemblyGantryBlockEntity.FIRST_MODULE + MechaFrame.HOPPER.firstIndex(MechaModule.Slot.LEGS), module(MechaModule.HYDRAULIC_LEGS));
        gantry.items().setStackInSlot(AssemblyGantryBlockEntity.FIRST_MODULE + MechaFrame.HOPPER.firstIndex(MechaModule.Slot.ARM), module(MechaModule.DRILL));
        gantry.energy().set(20_000);
        h.assertTrue(gantry.button(p, AssemblyGantryMenu.ASSEMBLE), "assembly refused");
        // the frame stands on the deck once the arm has stood off
        h.runAfterDelay(com.coolfreeze23.headinajar.machine.GantryPlan.LEAD + 1, () -> {
            MechaEntity m = h.getEntities(ModRegistry.HOPPER.get()).get(0);
            long built = m.builtAt();
            h.assertTrue(built == gantry.jobStart() + com.coolfreeze23.headinajar.machine.GantryPlan.LEAD, "the build did not start with the frame standing on the deck: " + built);
            HopperView v = new HopperView();
            h.assertTrue(clip(HopperAnimation.main(m, v, now(h) + 50)).equals("build"), "an empty pad mecha is not building");
            // a second, untouched build ends on its own
            h.runAfterDelay(30, () -> {
                board(h, p, m);
                long at = now(h);
                h.assertTrue(m.builtAt() == -1, "boarding did not skip the build");
                h.assertTrue(m.boardedAt() == at, "boarding time not set: " + m.boardedAt());
                h.assertTrue(clip(HopperAnimation.cockpit(m, at + 5)).equals("board"), "no boarding clip");
                h.assertTrue(clip(HopperAnimation.cockpit(m, at + 30)).equals("boot"), "no boot after the boarding");
                h.assertTrue(HopperAnimation.cockpit(m, at + 80) == null, "the boot never ends");
                h.assertTrue(clip(HopperAnimation.main(m, new HopperView(), at + 30)).equals("boot_legs"), "the legs do not boot");
                // controls are live from the first tick: the ridden input is not locked by the boot
                p.zza = 1;
                h.assertTrue(MechaLogic.riddenInput(m, p).z > 0, "the boot locks the controls");
                p.zza = 0;
                MechaLogic.eject(m, p);
                h.assertTrue(clip(HopperAnimation.main(m, new HopperView(), now(h) + 2)).equals("shutdown"), "no shutdown when the pilot leaves");
                h.assertTrue(clip(HopperAnimation.main(m, new HopperView(), now(h) + 40)).equals("parked"), "not parked after");
                MechaEntity left = hopper(h, FLOOR.west(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS);
                left.startBuild();
                h.runAfterDelay(HopperAnimation.BUILD.length() + 2, () -> {
                    h.assertTrue(left.builtAt() == -1, "a build nobody boarded never ended");
                    done(h, p);
                });
            });
        });
    }

    @GameTest(template = "platform", timeoutTicks = 120)
    public static void clipsFollowTheSyncedState(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = hopper(h, FLOOR.east(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS, MechaModule.DRILL);
        board(h, p, m);
        long t = now(h) + 100;
        HopperView v = new HopperView();
        // the idle tic comes round at a phase fixed per machine, so either may be showing
        String idle = clip(HopperAnimation.main(m, v, t));
        h.assertTrue(idle.equals("idle") || idle.equals("idle_tic"), "not idle: " + idle);
        m.setVent(MechaLogic.VENT_TICKS - 10);
        HopperAnimation.Choice vent = HopperAnimation.main(m, v, t);
        h.assertTrue(clip(vent).equals("vent") && Math.abs(vent.tick() - 10) < 1, "vent clip not at the vent's tick: " + vent);
        m.setVent(0);
        m.setBattery(0);
        v.speed = 0.08;
        h.assertTrue(clip(HopperAnimation.main(m, v, t)).equals("limp"), "a flat battery does not limp");
        m.setBattery(m.batteryCapacity());
        h.assertTrue(clip(HopperAnimation.main(m, v, t)).equals("walk"), "moving is not a walk");
        v.speed = 0;
        // a held arm works its module's verb; a refused press is a dry fire on that arm
        m.armsHeld = 1;
        m.tick();
        h.assertTrue(clip(HopperAnimation.arm(m, v, 0, t)).equals("spin_l"), "the drill arm does not spin up: " + clip(HopperAnimation.arm(m, v, 0, t)));
        h.assertTrue(clip(HopperAnimation.arm(m, v, 0, t + 20)).equals("spin_loop_l"), "the drill arm does not keep spinning");
        m.armsHeld = 0;
        m.tick();
        h.assertTrue(clip(HopperAnimation.arm(m, v, 0, t + 21)).equals("spin_end_l"), "no spin-down on release");
        m.setVent(50);
        m.armsHeld = 2;
        m.tick();
        h.assertTrue(m.dryAt() >= 0 && (m.dryAt() & 1) == 1, "a refused right arm made no dry fire: " + m.dryAt());
        h.assertTrue(clip(HopperAnimation.arm(m, v, 1, now(h) + 3)).equals("dry_fire_r"), "the right arm does not say no");
        m.armsHeld = 0;
        m.setVent(0);
        m.hurt(h.getLevel().damageSources().generic(), 1);
        h.assertTrue(clip(HopperAnimation.hit(m, now(h))).equals("hurt"), "no flinch when hit");
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 120)
    public static void pogoChargesLeapsAndLandsSoftly(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        // the stranger stands as near the landing as the friend, on the side away from the cow the ring knocks back
        ServerPlayer stranger = player(h, FLOOR.east(5).south(2));
        ServerPlayer friend = player(h, FLOOR.east(1).south(2));
        MechaEntity m = hopper(h, FLOOR.east(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS);
        board(h, p, m);
        p.setYRot(0);
        p.setXRot(0);
        HeadRegistry.get(h.getLevel().getServer()).setTrust(friend.getUUID(), p.getUUID(), true);
        Cow cow = h.spawn(EntityType.COW, FLOOR.east(4).north(1));
        float cowHealth = cow.getHealth();
        Vec3 cowAt = cow.position(), strangerAt = stranger.position(), friendAt = friend.position();
        int battery = m.battery();
        h.runAfterDelay(2, () -> {
            h.assertTrue(m.onGround(), "the test mecha is not standing");
            Hopper.input(p, true);
            long start = m.pogoAt();
            h.assertTrue(start == now(h), "the drive key did not start a charge");
            h.assertTrue(MechaLogic.riddenInput(m, p).equals(Vec3.ZERO), "a crouched Hopper still walks");
            h.assertTrue(clip(HopperAnimation.main(m, new HopperView(), now(h) + 10)).equals("pogo_crouch"), "no crouch clip");
            h.assertTrue(clip(HopperAnimation.main(m, new HopperView(), now(h) + 40)).equals("pogo_hold"), "no hold at full charge");
            h.runAfterDelay(Hopper.CHARGE_TICKS + 2, () -> {
                Hopper.input(p, false);
                h.assertTrue(m.pogoAt() == -1 && m.leapAt() == now(h), "the release did not leap");
                h.assertTrue(battery - m.battery() >= Hopper.FULL_JUMP_FE, "a full jump cost " + (battery - m.battery()));
                h.runAfterDelay(1, () -> {
                    // the pilot's client applies the launch (it moves its mecha); the server records it
                    Vec3 v = m.launch;
                    Vec3 expected = Hopper.launch(1, 0, 0);
                    h.assertTrue(m.pogoFlight && Math.abs(v.y - expected.y) < 0.05 && v.z > 0.3, "no launch along the look: " + v);
                    h.assertTrue(Math.abs(Hopper.distance(1) - 9) < 1e-6 && Math.abs(Hopper.distance(0) - 3) < 1e-6, "leap range is not 3 to 9");
                    h.assertFalse(m.causeFallDamage(12, 1, h.getLevel().damageSources().fall()), "its own leap hurts it on landing");
                });
            });
        });
        // the test mecha never leaves the ground (no client moves it): it lands by the 20-tick rule; checked on the tick
        // the landing is seen, before the pushes decay. Scheduled from the top: a test's schedule must not grow while it runs
        boolean[] checked = {false};
        for (int i = 1; i <= 30; i++) {
            h.runAfterDelay(2 + Hopper.CHARGE_TICKS + 2 + 1 + i, () -> {
                if (checked[0] || m.landedAt() < 0) return;
                checked[0] = true;
                h.assertTrue(!m.pogoFlight, "no landing on its springs");
                h.assertTrue(cow.getDeltaMovement().horizontalDistance() > 0.02 || cow.position().distanceTo(cowAt) > 0.2, "the ring did not knock the cow back");
                h.assertTrue(cow.getHealth() == cowHealth, "the ring hurt the cow");
                h.assertTrue(stranger.getDeltaMovement().horizontalDistance() < 1e-6 && stranger.position().distanceTo(strangerAt) < 1e-6,
                        "a player who never trusted the pilot was moved");
                // a player is moved by their own client: the server sends them the push (it stays in their motion here)
                h.assertTrue(friend.getDeltaMovement().horizontalDistance() > 0.05, "a player who trusts the pilot was not knocked back");
                done(h, p, stranger, friend);
            });
        }
    }

    @GameTest(template = "platform", timeoutTicks = 80)
    public static void pogoRefusesOnceAPress(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = hopper(h, FLOOR.east(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS);
        board(h, p, m);
        h.runAfterDelay(2, () -> {
            m.setBattery(100);
            Hopper.input(p, true);
            h.assertTrue(m.pogoAt() == -1 && count(p, "mecha.pogo.no_power") == 1, "a flat pogo did not say so once: " + Refusals.delivered(p));
            Hopper.input(p, false);
            m.setBattery(m.batteryCapacity());
            h.setBlock(FLOOR.east(3).above(4), Blocks.STONE);
            Hopper.input(p, true);
            h.assertTrue(m.pogoAt() == -1 && count(p, "mecha.pogo.no_room") == 1, "a ceiling did not stop the jump: " + Refusals.delivered(p));
            Hopper.input(p, false);
            h.setBlock(FLOOR.east(3).above(4), Blocks.AIR);
            m.setOnGround(false);
            Hopper.input(p, true);
            h.assertTrue(m.pogoAt() == -1 && count(p, "mecha.pogo.airborne") == 1, "a jump from the air was not refused");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 120)
    public static void goldenParachuteFiresTheJarClear(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = hopper(h, FLOOR.east(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS);
        board(h, p, m);
        m.setHealth(m.getMaxHealth() * 0.12f);
        m.invulnerableTime = 0;
        m.hurt(h.getLevel().damageSources().generic(), m.getMaxHealth() * 0.05f);
        h.assertTrue(p.getVehicle() == null && FormActions.state(p).form() == Form.JAR, "the jar was not fired clear");
        // in flight (a real client reports it; the test player has none)
        p.setOnGround(false);
        h.assertTrue(m.ejectedAt() == now(h) - 4, "the eject clip is not at the jar's moment: " + m.ejectedAt());
        h.assertTrue(clip(HopperAnimation.main(m, new HopperView(), now(h))).equals("eject"), "no eject clip");
        h.assertTrue(p.getPersistentData().getInt(HopperNet.CHUTE) == 1, "no parachute waiting to open");
        // a real player is ticked by its connection; the test player has none, so its tick's chute step runs here
        for (int i = 1; i <= 50; i++) h.runAfterDelay(i, () -> HopperNet.Events.tickChute(p));
        h.runAfterDelay(46, () -> {
            h.assertTrue(HopperNet.open(p), "the canopy never opened");
            p.setOnGround(true);
            h.runAfterDelay(3, () -> {
                h.assertTrue(p.getPersistentData().getInt(HopperNet.CHUTE) == 0, "the canopy did not fold on landing");
                done(h, p);
            });
        });
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void aDestroyedHopperCollapsesBeforeItGoes(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = hopper(h, FLOOR.east(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS, MechaModule.DRILL);
        m.hurt(h.getLevel().damageSources().generic(), 1000);
        h.assertTrue(m.isDeadOrDying(), "it did not die");
        h.runAfterDelay(20, () -> {
            h.assertTrue(!m.isRemoved(), "removed before its collapse ended");
            h.assertTrue(clip(HopperAnimation.main(m, new HopperView(), now(h))).equals("destroyed"), "no collapse clip");
            h.runAfterDelay(25, () -> {
                h.assertTrue(m.isRemoved(), "never destroyed after the collapse");
                h.assertTrue(!h.getEntities(EntityType.ITEM).isEmpty(), "the destruction dropped nothing");
                done(h, p);
            });
        });
    }

    @GameTest(template = "platform", timeoutTicks = 120)
    public static void gettingOutOpensTheHoopThenTheMachineStandsStill(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = hopper(h, FLOOR.east(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS);
        board(h, p, m);
        // the eject handle (sneak + jar menu) is an ordinary way out: the JarAction handler
        com.coolfreeze23.headinajar.network.Network.action(p, new com.coolfreeze23.headinajar.network.Network.JarAction(
                com.coolfreeze23.headinajar.network.Network.Action.EJECT));
        long out = now(h);
        h.assertTrue(p.getVehicle() == null, "the jar did not get out");
        HopperAnimation.Choice open = HopperAnimation.unboard(m, out + 5);
        h.assertTrue(clip(open).equals("board") && Math.abs(open.tick() - 17) < 0.01, "the hoop does not open backwards: " + open);
        h.assertTrue(HopperAnimation.unboard(m, out + 30) == null, "the hoop never closes again");
        h.assertFalse(HopperAnimation.still(m, out + 10), "a machine still getting out counts as parked");
        h.assertTrue(HopperAnimation.still(m, out + 80), "an empty machine is not parked and still afterwards");
        HopperAnimation.Choice parked = HopperAnimation.main(m, new HopperView(), out + 80);
        h.assertTrue(clip(parked).equals("parked") && parked.tick() == 0, "a still machine is not shown parked and settled: " + parked);
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void jetsAndHitsReachTheRiddenMachine(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        ServerPlayer hitter = player(h, FLOOR.east(3).west(2));
        MechaEntity m = hopper(h, FLOOR.east(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS, MechaModule.JUMP_JETS);
        board(h, p, m);
        // the jets fire through the jet key's handler, in the air; the push goes to the pilot's client (here, set directly)
        m.setOnGround(false);
        com.coolfreeze23.headinajar.network.Network.action(p, new com.coolfreeze23.headinajar.network.Network.JarAction(
                com.coolfreeze23.headinajar.network.Network.Action.JET));
        h.assertTrue(Math.abs(m.lastImpulse.y - 0.65) < 1e-6 && m.jetsLeft == 2, "the jets gave no lift: " + m.lastImpulse);
        m.setOnGround(true);
        // a blow from the machine's right is recorded as from the right (the flinch is mirrored); the attack path a click takes
        m.setYRot(0);
        m.setYBodyRot(0);
        Vec3 right = m.position().add(-1.5, 0, 0);
        hitter.moveTo(right.x, right.y, right.z, -90, 0);
        hitter.attack(m);
        h.assertTrue(m.hitAt() >= 0 && (m.hitAt() & 1) == 1, "a blow from the right was not recorded as one: " + m.hitAt());
        done(h, p, hitter);
    }

    @GameTest(template = "platform", timeoutTicks = 120)
    public static void aHeavyLandingIsTheServersCall(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = hopper(h, FLOOR.east(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS);
        board(h, p, m);
        // the pilot's client moves a ridden mecha; its move packets set where it is and whether it stands: done here directly
        Vec3 ground = m.position();
        h.runAfterDelay(2, () -> {
            m.setOnGround(false);
            m.setPos(ground.x, ground.y + 6, ground.z);
        });
        h.runAfterDelay(4, () -> m.setPos(ground.x, ground.y + 3, ground.z));
        h.runAfterDelay(8, () -> {
            m.setPos(ground.x, ground.y, ground.z);
            m.setOnGround(true);
        });
        h.runAfterDelay(10, () -> {
            long land = m.landAt();
            h.assertTrue(land >= 0, "no landing");
            h.assertTrue((land & 1) == 1, "a 6-block drop was not a heavy landing: dropped " + m.lastDrop);
            HopperAnimation.Choice c = HopperAnimation.main(m, new HopperView(), land / 2 + 3);
            h.assertTrue(clip(c).equals("land_heavy"), "a late arrival would not see the heavy landing: " + clip(c));
            done(h, p);
        });
    }

    /** Every slot of the Hopper resolves to socket bones the shipped model has; each module model hangs by its mount. */
    @GameTest(template = "platform")
    public static void modulesResolveToSockets(GameTestHelper h) {
        Set<String> bones = bones("hopper");
        MechaFrame frame = MechaFrame.HOPPER;
        for (int i = 0; i < frame.totalSlots(); i++) {
            var sockets = HopperSockets.sockets(frame, i);
            h.assertFalse(sockets.isEmpty(), "slot " + i + " has no socket");
            for (String s : sockets) {
                h.assertTrue(bones.contains(s), "slot " + i + " socket " + s + " is not in the model");
                h.assertTrue(HopperSockets.slotOf(frame, s) == i, "socket " + s + " maps back to the wrong slot");
            }
        }
        String[][] modules = {{"drill", "ARM"}, {"jump_jets", "BACK"}, {"hydraulic_legs", "LEGS"}};
        for (String[] mod : modules) {
            Set<String> mounts = bones("module/" + mod[0]);
            int slot = frame.firstIndex(MechaModule.Slot.valueOf(mod[1]));
            for (String s : HopperSockets.sockets(frame, slot)) {
                h.assertTrue(mounts.contains(HopperSockets.mountOf(s)), mod[0] + " has no " + HopperSockets.mountOf(s) + " for " + s);
            }
        }
        h.succeed();
    }

    private static Set<String> bones(String model) {
        Set<String> out = new HashSet<>();
        var in = HopperGameTests.class.getResourceAsStream("/assets/headinajar/geo/entity/mecha/" + model + ".geo.json");
        if (in == null) throw new AssertionError("no model " + model);
        JsonObject geo = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        for (var b : geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")) {
            out.add(b.getAsJsonObject().get("name").getAsString());
        }
        return out;
    }

    /** A Hopper as the previous version saved it (no new keys) loads with its modules, battery, heat and owner. */
    @GameTest(template = "platform")
    public static void aHopperSavedBeforeLoads(GameTestHelper h) {
        UUID owner = UUID.randomUUID();
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "headinajar:hopper");
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
        tag.putFloat("Health", 41);
        ListTag modules = new ListTag();
        for (int i = 0; i < MechaFrame.HOPPER.totalSlots(); i++) {
            ItemStack stack = i == 0 ? module(MechaModule.HYDRAULIC_LEGS) : i == MechaFrame.HOPPER.firstIndex(MechaModule.Slot.ARM) ? module(MechaModule.DRILL) : ItemStack.EMPTY;
            modules.add(stack.saveOptional(h.getLevel().registryAccess()));
        }
        tag.put("Modules", modules);
        tag.put("Cargo", new ListTag());
        tag.putInt("Battery", 80_000);
        tag.putFloat("Heat", 55);
        tag.putUUID("Owner", owner);
        var loaded = EntityType.loadEntityRecursive(tag, h.getLevel(), e -> e);
        h.assertTrue(loaded instanceof MechaEntity, "the old Hopper did not load: " + loaded);
        MechaEntity m = (MechaEntity) loaded;
        h.assertTrue(m.has(MechaModule.HYDRAULIC_LEGS) && m.arm(0) == MechaModule.DRILL, "modules lost");
        h.assertTrue(m.battery() == 80_000 && Math.abs(m.heat() - 55) < 0.01 && owner.equals(m.owner()) && Math.abs(m.getHealth() - 41) < 0.01,
                "battery, heat, owner or health lost");
        h.assertTrue(m.builtAt() == -1 && m.boardedAt() == -1 && m.pogoAt() == -1, "an old Hopper loads mid-sequence");
        // and what it saves now holds no key the previous version lacked
        CompoundTag saved = new CompoundTag();
        m.addAdditionalSaveData(saved);
        for (String key : List.of("Built", "Boarded", "Pogo", "Leap")) h.assertFalse(saved.contains(key), "new saved key " + key);
        h.succeed();
    }
}
