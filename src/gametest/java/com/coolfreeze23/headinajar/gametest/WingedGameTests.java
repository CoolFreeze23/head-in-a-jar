package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.MechaModuleItem;
import com.coolfreeze23.headinajar.mecha.HopperAnimation;
import com.coolfreeze23.headinajar.mecha.HopperNet;
import com.coolfreeze23.headinajar.mecha.HopperSockets;
import com.coolfreeze23.headinajar.mecha.HopperView;
import com.coolfreeze23.headinajar.mecha.Kite;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.MechaLogic;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.mecha.MechaRig;
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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Sheep;
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
 * The Kite on GeckoLib: its seat in the lantern, its flight clips from synced state, its takeoff, the Talon Lift on the
 * drive key (pressed through the drive payload's handler), and the Golden Parachute from its eject and the walkers'.
 * The pilot's client moves a ridden machine; the test has none, so a Kite in the air is held where that client would
 * hold it, every tick.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class WingedGameTests {
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

    private static MechaEntity mecha(GameTestHelper h, MechaFrame frame, BlockPos at, UUID owner, MechaModule... mods) {
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
        face(m);
        return m;
    }

    private static MechaEntity kite(GameTestHelper h, BlockPos at, UUID owner) {
        return mecha(h, MechaFrame.KITE, at, owner);
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

    /** Holds a Kite in the air at {@code at} (absolute) for ticks 1..{@code ticks}, while {@code aloft[0]} says so. */
    private static void aloft(GameTestHelper h, MechaEntity m, Vec3[] at, boolean[] aloft, int ticks) {
        for (int i = 0; i <= ticks; i++) {
            h.runAfterDelay(i, () -> {
                if (!aloft[0] || m.isRemoved()) return;
                m.setPos(at[0].x, at[0].y, at[0].z);
                m.setOnGround(false);
                face(m);
            });
        }
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

    private static JsonObject resource(String path) {
        var in = WingedGameTests.class.getResourceAsStream("/assets/headinajar/" + path);
        if (in == null) throw new AssertionError("no resource " + path);
        return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static Set<String> bones(String path) {
        Set<String> out = new HashSet<>();
        for (var b : resource(path).getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")) {
            out.add(b.getAsJsonObject().get("name").getAsString());
        }
        return out;
    }

    // --- the frame -------------------------------------------------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void theKiteSeatsTheJarInItsLantern(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = kite(h, FLOOR.east(3), p.getUUID());
        h.assertTrue(MechaRig.of(MechaFrame.KITE) == MechaRig.KITE && m.frame().animated(), "the Kite is not an animated frame");
        board(h, p, m);
        long boarded = m.boardedAt();
        h.assertTrue(boarded == now(h), "boarding was not marked: " + boarded);
        h.assertTrue(Math.abs(Hosts.neck(m).y - 1.04) < 1e-6, "the seat is not the lantern's: " + Hosts.neck(m));
        h.assertTrue(clip(HopperAnimation.cockpit(m, boarded + 5)).equals("board"), "the lift floor does not bring the jar in");
        h.assertTrue(clip(HopperAnimation.cockpit(m, boarded + 30)).equals("boot"), "no boot after boarding");
        // controls are live from the first tick
        p.zza = 1;
        h.assertFalse(MechaLogic.riddenInput(m, p).equals(Vec3.ZERO), "the Kite does not answer the keys while it boots");
        p.zza = 0;
        // every clip the rig names is in the shipped clip file, at its length
        JsonObject clips = resource("animations/entity/mecha/kite.animation.json").getAsJsonObject("animations");
        for (String name : List.of("parked", "board", "boot", "boot_legs", "shutdown", "idle", "idle_tic", "walk", "turn_l", "turn_r", "takeoff",
                "flap", "hover", "glide", "bank_l", "bank_r", "flare", "land", "land_heavy", "stall", "stall_loop", "fall", "talon_grab",
                "talon_carry", "talon_drop", "talon_refused", "hurt", "limp", "vent", "eject", "destroyed", "build", "dry_fire_l", "dry_fire_r")) {
            h.assertTrue(clips.has(name), "the clip file lacks " + name);
            double length = clips.getAsJsonObject(name).get("animation_length").getAsDouble() * 20;
            h.assertTrue(Math.abs(length - MechaRig.KITE.clip(name).length()) < 0.5, name + " is " + length + " ticks, the rig says " + MechaRig.KITE.clip(name).length());
        }
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void modulesResolveToKiteSockets(GameTestHelper h) {
        Set<String> bones = bones("geo/entity/mecha/kite.geo.json");
        MechaFrame frame = MechaFrame.KITE;
        h.assertTrue(frame.slots(MechaModule.Slot.BACK) == 0, "the Kite has a back slot");
        for (int i = 0; i < frame.totalSlots(); i++) {
            for (String s : HopperSockets.sockets(frame, i)) {
                h.assertTrue(bones.contains(s), "slot " + i + " socket " + s + " is not in the Kite's model");
                h.assertTrue(HopperSockets.slotOf(frame, s) == i, "socket " + s + " maps back to the wrong slot");
            }
        }
        for (String b : MechaRig.KITE.tracked) h.assertTrue(bones.contains(b), "tracked bone " + b + " missing");
        for (String b : MechaRig.KITE.lamps) h.assertTrue(bones.contains(b), "lamp bone " + b + " missing");
        for (var l : MechaRig.KITE.locators.values()) h.assertTrue(bones.contains(l.bone()), "locator bone " + l.bone() + " missing");
        h.assertFalse(bones.contains("standin_jar"), "the stand-in jar is shipped");
        Set<String> chute = bones("geo/entity/golden_parachute.geo.json");
        for (String b : List.of("canopy", "cloth", "panel_f", "panel_b", "panel_l", "panel_r", "tassels_l", "tassels_r", "harness", "tag")) {
            h.assertTrue(chute.contains(b), "the parachute lacks " + b);
        }
        h.assertFalse(chute.contains("standin_jar"), "the parachute ships its stand-in jar");
        h.succeed();
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void theTakeoffCrouchesThenTheWingsLift(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = kite(h, FLOOR.east(3), p.getUUID());
        board(h, p, m);
        long[] start = new long[1];
        h.runAfterDelay(2, () -> {
            m.setOnGround(true);
            p.jumping = true;
        });
        h.runAfterDelay(3, () -> {
            start[0] = m.takeoffAt();
            h.assertTrue(start[0] >= 0 && now(h) - start[0] <= 1, "holding the climb on the ground did not start the takeoff: " + start[0]);
            h.assertTrue(m.climbing(), "the held climb is not synced");
            h.assertTrue(clip(HopperAnimation.main(m, new HopperView(), now(h))).equals("takeoff"), "no takeoff crouch");
            h.assertFalse(Kite.mayLift(m), "the wings lift before the crouch's push");
            m.setDeltaMovement(Vec3.ZERO);
            MechaLogic.tickRidden(m, p);
            h.assertTrue(m.getDeltaMovement().y == 0, "the crouch jumped: " + m.getDeltaMovement());
        });
        h.runAfterDelay(3 + Kite.TAKEOFF_PUSH + 1, () -> {
            m.setOnGround(true);
            h.assertTrue(Kite.mayLift(m), "no lift at the takeoff's push");
            m.setDeltaMovement(Vec3.ZERO);
            MechaLogic.tickRidden(m, p);
            h.assertTrue(Math.abs(m.getDeltaMovement().y - 0.08) < 1e-6, "the wings' lift is not the Kite's 0.08: " + m.getDeltaMovement());
            // let go and land: a new press that is let go before the push calls the takeoff off
            p.jumping = false;
        });
        h.runAfterDelay(30, () -> {
            m.setOnGround(true);
            p.jumping = true;
        });
        h.runAfterDelay(32, () -> p.jumping = false);
        h.runAfterDelay(34, () -> {
            h.assertTrue(m.takeoffAt() == -1, "a takeoff let go before its push was not called off: " + m.takeoffAt());
            h.assertFalse(m.climbing(), "the climb is still shown held");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void flightClipsFollowTheFlightState(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = kite(h, FLOOR.east(3), p.getUUID());
        board(h, p, m);
        h.runAfterDelay(2, () -> {
            double t = now(h);
            HopperView v = new HopperView();
            m.setTakeoffAt(-1);
            h.assertTrue(clip(HopperAnimation.main(m, v, t + 100)).startsWith("idle") || clip(HopperAnimation.main(m, v, t + 100)).startsWith("boot"),
                    "on the ground it is not the walker's idle: " + clip(HopperAnimation.main(m, v, t + 100)));
            m.setOnGround(false);
            m.setClimbing(true);
            v.speed = 0.5;
            h.assertTrue(clip(HopperAnimation.main(m, v, t + 100)).equals("flap"), "holding the climb at speed is not the wingbeat");
            v.speed = 0.05;
            h.assertTrue(clip(HopperAnimation.main(m, v, t + 100)).equals("hover"), "holding the climb standing still is not the hover");
            m.setClimbing(false);
            v.vy = -0.3;
            v.speed = 0.5;
            h.assertTrue(clip(HopperAnimation.main(m, v, t + 100)).equals("glide"), "coming down fast is not the glide");
            v.speed = 0.1;
            h.assertTrue(clip(HopperAnimation.main(m, v, t + 100)).equals("fall"), "coming down slowly is not the fall");
            v.flareAt = t + 98;
            h.assertTrue(clip(HopperAnimation.main(m, v, t + 100)).equals("flare"), "near the ground it does not flare");
            v.flareAt = -1;
            m.setBattery(0);
            v.stallAt = t + 90;
            h.assertTrue(clip(HopperAnimation.main(m, v, t + 100)).equals("stall"), "a flat battery aloft is not the stall");
            h.assertTrue(clip(HopperAnimation.main(m, v, t + 140)).equals("stall_loop"), "the stall does not go on falling");
            m.setBattery(m.batteryCapacity());
            v.stallAt = -1;
            // the tail rolls into turns
            m.view.turn = 3;
            h.assertTrue(clip(HopperAnimation.bank(m, t + 100)).equals("bank_r"), "no bank to the right");
            m.view.turn = -3;
            h.assertTrue(clip(HopperAnimation.bank(m, t + 100)).equals("bank_l"), "no bank to the left");
            m.view.turn = 0;
            h.assertTrue(HopperAnimation.bank(m, t + 100) == null, "a straight flight banks");
            // a landing is heavy by how fast it came down, from any height
            m.airTicks = 6;
            m.peakY = m.getY() + 20;
            m.sinkRate = 0.2;
            m.setOnGround(true);
            m.tick();
            h.assertTrue(m.landAt() >= 0 && (m.landAt() & 1) == 0, "a slow touchdown from height was heavy");
        });
        h.runAfterDelay(4, () -> {
            m.setOnGround(false);
            m.tick();
            m.airTicks = 6;
            m.sinkRate = 0.8;
            m.setOnGround(true);
            m.tick();
            h.assertTrue((m.landAt() & 1) == 1, "a fast touchdown was light");
            done(h, p);
        });
    }

    // --- the Talon Lift ----------------------------------------------------------------------------------------------

    /** Where the Kite hovers to reach a creature standing at {@code feet}: its top 1.2 below the Kite's feet, just behind it. */
    private static Vec3 over(Entity e) {
        return new Vec3(e.getX(), e.getY() + e.getBbHeight() + 1.2, e.getZ() - 0.4);
    }

    @GameTest(template = "platform", timeoutTicks = 120)
    public static void theTalonsPickUpCarryAndDropASheep(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = kite(h, FLOOR.east(3), p.getUUID());
        board(h, p, m);
        Sheep sheep = h.spawn(EntityType.SHEEP, FLOOR.east(3).south(3));
        Vec3[] at = {null};
        boolean[] up = {false};
        aloft(h, m, at, up, 60);
        h.runAfterDelay(1, () -> {
            at[0] = over(sheep);
            up[0] = true;
        });
        int[] battery = new int[1];
        h.runAfterDelay(3, () -> {
            battery[0] = m.battery();
            long t = now(h);
            press(p);
            h.assertTrue(kind(m) == Kite.GRAB && m.liftAt() / 4 == t, "the drive key did not start a grab: " + m.liftAt() + " " + Refusals.delivered(p));
            h.assertTrue(battery[0] - m.battery() >= Kite.GRAB_FE, "the grab cost nothing");
            h.assertTrue(clip(HopperAnimation.main(m, new HopperView(), t + 5)).equals("talon_grab"), "no grab clip");
            h.assertTrue(m.heldId() == -1, "it holds before the talons close");
        });
        h.runAfterDelay(3 + Kite.GRAB_CLOSE + 1, () -> h.assertTrue(m.heldId() == sheep.getId(), "the talons did not take the sheep at tick 10: " + m.heldId()));
        h.runAfterDelay(3 + Kite.GRAB_TICKS + 2, () -> {
            Vec3 hang = Kite.feet(m, sheep, now(h), null);
            h.assertTrue(sheep.position().distanceTo(hang) < 0.05, "the sheep does not hang from the cargo point: " + sheep.position() + " vs " + hang);
            h.assertTrue(Math.abs(sheep.getY() + sheep.getBbHeight() - (m.getY() - 6 / 16.0)) < 0.05, "the sheep's back is not under the talons");
            h.assertFalse(sheep.isNoGravity(), "the carry set the saved no-gravity flag");
            HopperView v = new HopperView();
            h.assertTrue(clip(HopperAnimation.main(m, v, now(h))).equals("talon_carry"), "carrying is not the carry beat");
        });
        h.runAfterDelay(3 + Kite.GRAB_TICKS + 6, () -> {
            long t = now(h);
            press(p);
            h.assertTrue(kind(m) == Kite.DROP && m.liftAt() / 4 == t, "the second press did not start the drop");
            h.assertTrue(m.heldId() == sheep.getId(), "let go before the drop's tick 1");
        });
        h.runAfterDelay(3 + Kite.GRAB_TICKS + 8, () -> {
            h.assertTrue(m.heldId() == -1, "the drop's tick 1 did not let go");
            h.assertTrue(clip(HopperAnimation.main(m, new HopperView(), now(h))).equals("talon_drop"), "no drop clip");
        });
        h.runAfterDelay(3 + Kite.GRAB_TICKS + 14, () -> {
            h.assertTrue(sheep.getY() < Kite.feet(m, sheep, now(h), null).y - 0.05 || sheep.onGround(), "the dropped sheep does not fall");
            done(h, p);
        });
    }

    /**
     * The talons go by the one rule for handling a player: refused with the handling line until the player shares the
     * pilot's team, then taken, with nothing on the player's trust list.
     */
    @GameTest(template = "platform", timeoutTicks = 140)
    public static void theTalonsTakeATeammateWithoutTheTrustList(GameTestHelper h) {
        // named players: a team holds names, and every plain mock player has the same one
        ServerPlayer p = TestPlayers.at(h, "pilot", FLOOR);
        BlockPos at = FLOOR.east(3);
        MechaEntity m = kite(h, at, p.getUUID());
        board(h, p, m);
        ServerPlayer mate = TestPlayers.at(h, "mate", at.south(3));
        var board = h.getLevel().getScoreboard();
        String name = "kite_crew_" + mate.getId();
        Vec3[] where = {null};
        boolean[] up = {false};
        aloft(h, m, where, up, 120);
        h.runAfterDelay(1, () -> {
            where[0] = over(mate);
            up[0] = true;
        });
        h.runAfterDelay(3, () -> {
            long t = now(h);
            press(p);
            h.assertTrue(kind(m) == Kite.REFUSED && m.liftAt() / 4 == t, "a player who has not trusted the pilot was taken");
            h.assertTrue(count(p, "mecha.lift.untrusted") == 1, "the refusal was not the handling line: " + Refusals.delivered(p));
            var team = board.addPlayerTeam(name);
            board.addPlayerToTeam(p.getScoreboardName(), team);
            board.addPlayerToTeam(mate.getScoreboardName(), team);
        });
        h.runAfterDelay(5 + Kite.REFUSED_TICKS, () -> {
            h.assertFalse(com.coolfreeze23.headinajar.form.HeadRegistry.get(h.getLevel().getServer()).trusted(mate.getUUID()).contains(p.getUUID()),
                    "the mate trusts the pilot by list");
            long t = now(h);
            press(p);
            h.assertTrue(kind(m) == Kite.GRAB && m.liftAt() / 4 == t, "a teammate was refused: " + Refusals.delivered(p));
        });
        h.runAfterDelay(5 + Kite.REFUSED_TICKS + Kite.GRAB_CLOSE + 2, () -> {
            h.assertTrue(m.heldId() == mate.getId(), "the talons did not take the teammate: " + m.heldId());
            board.removePlayerTeam(board.getPlayerTeam(name));
            done(h, p, mate);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 260)
    public static void everyRefusedTalonPressSaysWhy(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        BlockPos at = FLOOR.east(3);
        MechaEntity m = kite(h, at, p.getUUID());
        board(h, p, m);
        ServerPlayer stranger = player(h, at.south(3));
        Vec3[] where = {null};
        boolean[] up = {false};
        aloft(h, m, where, up, 240);
        String[] steps = {"mecha.talon.grounded", "mecha.lift.untrusted", "mecha.talon.too_heavy", "mecha.talon.nothing", "mecha.talon.venting",
                "mecha.talon.no_power"};
        Entity[] cow = new Entity[1];
        for (int i = 0; i < steps.length; i++) {
            int step = i;
            h.runAfterDelay(2 + 35L * i, () -> {
                switch (step) {
                    case 0 -> {
                        up[0] = false;
                        m.setOnGround(true);
                    }
                    case 1 -> {
                        where[0] = over(stranger);
                        up[0] = true;
                    }
                    case 2 -> {
                        stranger.moveTo(stranger.getX() + 20, stranger.getY(), stranger.getZ());
                        cow[0] = h.spawn(EntityType.COW, at.south(3));
                        where[0] = over(cow[0]);
                    }
                    case 3 -> cow[0].discard();
                    case 4 -> m.setVent(60);
                    case 5 -> {
                        m.setVent(0);
                        m.setBattery(100);
                    }
                    default -> {
                    }
                }
            });
            h.runAfterDelay(4 + 35L * i, () -> {
                if (step != 0) m.setOnGround(false);
                long t = now(h);
                press(p);
                h.assertTrue(kind(m) == Kite.REFUSED && m.liftAt() / 4 == t, steps[step] + ": the press did not play the refused grab " + Refusals.delivered(p));
                h.assertTrue(count(p, steps[step]) == 1, steps[step] + " was not said once: " + Refusals.delivered(p));
                if (step != 4) h.assertTrue(clip(HopperAnimation.main(m, new HopperView(), t + 20)).equals("talon_refused"), steps[step] + ": no shrug");
                if (step == 1) {
                    // a press while the shrug plays is answered too
                    press(p);
                    h.assertTrue(count(p, "mecha.talon.busy") == 1, "a press during the shrug said nothing");
                }
            });
        }
        h.runAfterDelay(4 + 35L * steps.length, () -> done(h, p, stranger));
    }

    @GameTest(template = "platform", timeoutTicks = 220)
    public static void theCarryEndsOnEveryWayOut(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        BlockPos at = FLOOR.east(3);
        MechaEntity m = kite(h, at, p.getUUID());
        board(h, p, m);
        Vec3[] where = {null};
        boolean[] up = {false};
        aloft(h, m, where, up, 200);
        Sheep[] sheep = new Sheep[1];
        // each phase: a fresh sheep, a grab, then one way out
        String[] ways = {"killed", "venting", "flat", "set down", "pilot out"};
        for (int i = 0; i < ways.length; i++) {
            int way = i;
            long base = 2 + 38L * i;
            h.runAfterDelay(base, () -> {
                for (Sheep s : h.getEntities(EntityType.SHEEP)) s.discard();
                m.setVent(0);
                m.setBattery(m.batteryCapacity());
                sheep[0] = h.spawn(EntityType.SHEEP, at.south(3));
                where[0] = over(sheep[0]);
                up[0] = true;
            });
            h.runAfterDelay(base + 2, () -> press(p));
            h.runAfterDelay(base + 2 + Kite.GRAB_TICKS + 2, () -> {
                h.assertTrue(m.heldId() == sheep[0].getId(), ways[way] + ": no hold " + Refusals.delivered(p));
                switch (way) {
                    case 0 -> sheep[0].kill();
                    case 1 -> m.setVent(MechaLogic.VENT_TICKS);
                    case 2 -> m.setBattery(0);
                    // flown down until the sheep's feet meet the grass: set down where it hangs
                    case 3 -> where[0] = where[0].add(0, -(sheep[0].getY() - h.absoluteVec(Vec3.atBottomCenterOf(at)).y) + 0.05, 0);
                    default -> FormActions.leaveHost(p);
                }
            });
            h.runAfterDelay(base + 2 + Kite.GRAB_TICKS + 5, () -> {
                h.assertTrue(m.heldId() == -1, ways[way] + " kept the load");
                if (way == 3) h.assertTrue(kind(m) == Kite.DROP, "a load set down on the ground did not open the talons");
                if (sheep[0].isAlive()) h.assertFalse(sheep[0].isNoGravity(), ways[way] + ": the released sheep floats");
            });
        }
        h.runAfterDelay(2 + 38L * ways.length, () -> done(h, p));
    }

    @GameTest(template = "platform", timeoutTicks = 80)
    public static void anUnpilotedKiteInTheAirComesDown(GameTestHelper h) {
        MechaEntity m = kite(h, FLOOR.east(3).above(2), null);
        m.setOnGround(false);
        double y = m.getY();
        h.runAfterDelay(40, () -> {
            h.assertTrue(m.getY() < y - 1.5, "an empty Kite hangs in the air: " + (y - m.getY()));
            h.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void aKiteSavedBeforeLoads(GameTestHelper h) {
        UUID owner = UUID.randomUUID();
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "headinajar:kite");
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
        tag.putFloat("Health", 50);
        ListTag modules = new ListTag();
        int arm = MechaFrame.KITE.firstIndex(MechaModule.Slot.ARM);
        for (int i = 0; i < MechaFrame.KITE.totalSlots(); i++) {
            ItemStack stack = i == arm ? module(MechaModule.DRILL) : ItemStack.EMPTY;
            modules.add(stack.saveOptional(h.getLevel().registryAccess()));
        }
        tag.put("Modules", modules);
        tag.put("Cargo", new ListTag());
        tag.putInt("Battery", 200_000);
        tag.putFloat("Heat", 80);
        tag.putUUID("Owner", owner);
        var loaded = EntityType.loadEntityRecursive(tag, h.getLevel(), e -> e);
        h.assertTrue(loaded instanceof MechaEntity, "the old Kite did not load: " + loaded);
        MechaEntity m = (MechaEntity) loaded;
        h.assertTrue(m.frame() == MechaFrame.KITE && m.frame().animated(), "not an animated Kite");
        h.assertTrue(m.arm(0) == MechaModule.DRILL, "modules lost");
        h.assertTrue(m.battery() == 200_000 && Math.abs(m.heat() - 80) < 0.01 && owner.equals(m.owner()) && Math.abs(m.getHealth() - 50) < 0.01,
                "battery, heat, owner or health lost");
        h.assertTrue(m.takeoffAt() == -1 && !m.climbing() && m.liftAt() == -1 && m.heldId() == -1 && m.boardedAt() == -1, "an old Kite loads mid-sequence");
        CompoundTag saved = new CompoundTag();
        m.addAdditionalSaveData(saved);
        for (String key : List.of("Takeoff", "Fly", "Climbing", "Lift", "Held", "Talon")) h.assertFalse(saved.contains(key), "new saved key " + key);
        h.succeed();
    }

    // --- the Golden Parachute ----------------------------------------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void theKiteDropsItsJarBehindTheTailIntoTheParachute(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = kite(h, FLOOR.east(3), p.getUUID());
        board(h, p, m);
        Vec3[] where = {h.absoluteVec(Vec3.atBottomCenterOf(FLOOR.east(3))).add(0, 12, 0)};
        boolean[] up = {true};
        aloft(h, m, where, up, 4);
        boolean[] room = new boolean[1];
        h.runAfterDelay(3, () -> {
            m.setOnGround(false);
            m.setHealth(m.getMaxHealth() * 0.12f);
            m.invulnerableTime = 0;
            m.hurt(h.getLevel().damageSources().generic(), m.getMaxHealth() * 0.05f);
            up[0] = false;
            h.assertTrue(p.getVehicle() == null && FormActions.state(p).form() == Form.JAR, "the jar was not dropped clear");
            h.assertTrue(m.ejectedAt() == now(h) - 4, "the eject clip is not at the jar's moment: " + m.ejectedAt());
            h.assertTrue(clip(HopperAnimation.main(m, new HopperView(), now(h))).equals("eject"), "no eject clip");
            h.assertTrue(p.getZ() < m.getZ() - 0.5, "the jar did not leave behind the tail: " + (p.getZ() - m.getZ()));
            h.assertTrue(p.getDeltaMovement().y < 0, "in flight the jar is not pushed down and back: " + p.getDeltaMovement());
            h.assertTrue(HopperNet.state(p) == HopperNet.FIRED, "no parachute deploying");
            p.setOnGround(false);
        });
        for (int i = 4; i <= 30; i++) {
            h.runAfterDelay(i, () -> {
                p.setOnGround(false);
                HopperNet.Events.tickChute(p);
            });
        }
        h.runAfterDelay(3 + HopperNet.CHECK, () -> room[0] = HopperNet.room(p));
        h.runAfterDelay(3 + HopperNet.SNAP + 1, () -> {
            int want = room[0] ? HopperNet.OPEN : HopperNet.TANGLED;
            h.assertTrue(HopperNet.state(p) == want, "the canopy is " + HopperNet.state(p) + ", wanted " + want);
        });
        h.runAfterDelay(32, () -> {
            p.setOnGround(true);
            HopperNet.Events.tickChute(p);
            h.assertTrue(HopperNet.state(p) == 0, "the parachute did not fold on landing");
            done(h, p);
        });
    }

    /**
     * A Kite's eject at a height over the grass, through the real damage path; returns the jar after the eject, its
     * parachute's state and rate checked by {@code check} on the eject's tick, then ticked on for {@code ticks}.
     */
    private static void ejectAt(GameTestHelper h, double above, java.util.function.BiConsumer<ServerPlayer, MechaEntity> check,
                                int ticks, java.util.function.Consumer<ServerPlayer> after) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = kite(h, FLOOR.east(3), p.getUUID());
        board(h, p, m);
        Vec3[] where = {h.absoluteVec(Vec3.atBottomCenterOf(FLOOR.east(3))).add(0, above, 0)};
        boolean[] up = {true};
        aloft(h, m, where, up, 3);
        h.runAfterDelay(2, () -> {
            m.setOnGround(false);
            m.setHealth(m.getMaxHealth() * 0.12f);
            m.invulnerableTime = 0;
            m.hurt(h.getLevel().damageSources().generic(), m.getMaxHealth() * 0.05f);
            up[0] = false;
            h.assertTrue(p.getVehicle() == null, "the jar was not dropped clear");
            check.accept(p, m);
            p.setOnGround(false);
        });
        for (int i = 3; i <= 2 + ticks; i++) {
            h.runAfterDelay(i, () -> {
                p.setOnGround(false);
                HopperNet.Events.tickChute(p);
            });
        }
        h.runAfterDelay(3 + ticks, () -> {
            after.accept(p);
            done(h, p);
        });
    }

    private static float rate(ServerPlayer p) {
        return p.getPersistentData().getFloat(HopperNet.CHUTE + "_rate");
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void aLongDropDeploysAsDrawn(GameTestHelper h) {
        ejectAt(h, 25, (p, m) -> {
            h.assertTrue(HopperNet.ticksToLand(p, p.getDeltaMovement()) > HopperNet.SNAP + HopperNet.OPEN_BEFORE, "a 25-block drop is short");
            h.assertTrue(HopperNet.state(p) == HopperNet.FIRED && Math.abs(rate(p) - 1) < 1e-6, "a long drop's deploy is not as drawn: " + rate(p));
        }, HopperNet.SNAP + 1, p -> h.assertTrue(HopperNet.state(p) != HopperNet.FIRED, "the canopy did not open by the deploy's snap"));
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void aShortDropDeploysFasterAndOpensBeforeTouchdown(GameTestHelper h) {
        int[] land = new int[1];
        float[] r = new float[1];
        ejectAt(h, 4, (p, m) -> {
            land[0] = HopperNet.ticksToLand(p, p.getDeltaMovement());
            r[0] = rate(p);
            h.assertTrue(HopperNet.state(p) == HopperNet.FIRED && r[0] > 1 && r[0] <= HopperNet.FASTEST, "a short drop's deploy is not faster: " + r[0] + ", lands in " + land[0]);
            h.assertTrue(Math.ceil(HopperNet.SNAP / r[0]) <= land[0] - HopperNet.OPEN_BEFORE, "the faster deploy still opens too late");
        }, 19, p -> {
            // open (or, where the test area's roof leaves no room, tangled), never still unfolding, before touchdown
            h.assertTrue(HopperNet.state(p) != HopperNet.FIRED, "the faster canopy had not opened " + Math.ceil(HopperNet.SNAP / r[0]) + " ticks in");
        });
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void tooShortADropTanglesAtOnce(GameTestHelper h) {
        ejectAt(h, 1.5, (p, m) -> {
            int land = HopperNet.ticksToLand(p, p.getDeltaMovement());
            h.assertTrue(land - HopperNet.OPEN_BEFORE < HopperNet.SNAP / HopperNet.FASTEST, "the drop is not too short: " + land);
            h.assertTrue(HopperNet.state(p) == HopperNet.TANGLED, "too short a drop did not tangle at once: " + HopperNet.state(p));
        }, 2, p -> h.assertTrue(HopperNet.state(p) == HopperNet.TANGLED || HopperNet.state(p) == 0, "a tangle opened"));
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void aLowCeilingTanglesTheParachuteAndALongFallStaysSlow(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = mecha(h, MechaFrame.BULWARK, FLOOR.east(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS);
        board(h, p, m);
        BlockPos[] lid = new BlockPos[1];
        h.runAfterDelay(2, () -> {
            m.setHealth(m.getMaxHealth() * 0.12f);
            m.invulnerableTime = 0;
            m.hurt(h.getLevel().damageSources().generic(), m.getMaxHealth() * 0.05f);
            h.assertTrue(p.getVehicle() == null, "the Bulwark did not fire the jar clear");
            h.assertTrue(m.ejectedAt() == now(h) - 4, "the Bulwark's eject is not at its clip's tick 4");
            h.assertTrue(HopperNet.state(p) == HopperNet.FIRED && p.hasEffect(MobEffects.SLOW_FALLING), "no parachute or slow falling");
            // a slab of stone just over the jar: no room to open
            lid[0] = BlockPos.containing(p.getX(), p.getBoundingBox().maxY + 1, p.getZ());
            h.getLevel().setBlockAndUpdate(lid[0], Blocks.STONE.defaultBlockState());
            p.setOnGround(false);
        });
        for (int i = 3; i <= 40; i++) {
            h.runAfterDelay(i, () -> {
                p.setOnGround(false);
                HopperNet.Events.tickChute(p);
            });
        }
        h.runAfterDelay(2 + HopperNet.CHECK + 1, () -> {
            h.assertTrue(HopperNet.state(p) == HopperNet.TANGLED, "under a low ceiling the canopy did not tangle: " + HopperNet.state(p));
            h.getLevel().setBlockAndUpdate(lid[0], Blocks.AIR.defaultBlockState());
            // the effect runs out before a long fall does: it is topped up while the parachute is out
            p.removeEffect(MobEffects.SLOW_FALLING);
        });
        h.runAfterDelay(2 + HopperNet.CHECK + 3, () -> {
            h.assertTrue(p.hasEffect(MobEffects.SLOW_FALLING), "a long fall drops at full speed under the parachute");
            h.assertTrue(HopperNet.state(p) == HopperNet.TANGLED, "a tangled canopy opened after all");
        });
        h.runAfterDelay(42, () -> {
            p.setOnGround(true);
            HopperNet.Events.tickChute(p);
            h.assertTrue(HopperNet.state(p) == 0, "the tangle did not come down");
            done(h, p);
        });
    }
}
