package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.entity.LunkAnimation;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.entity.RoverAnimation;
import com.coolfreeze23.headinajar.entity.ShellEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.genetics.BloodType;
import com.coolfreeze23.headinajar.genetics.Gene;
import com.coolfreeze23.headinajar.genetics.GeneEffects;
import com.coolfreeze23.headinajar.genetics.Genetics;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.MechaModuleItem;
import com.coolfreeze23.headinajar.mecha.Chimera;
import com.coolfreeze23.headinajar.mecha.HopperAnimation;
import com.coolfreeze23.headinajar.mecha.HopperNet;
import com.coolfreeze23.headinajar.mecha.HopperSockets;
import com.coolfreeze23.headinajar.mecha.HopperView;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.MechaLogic;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.mecha.MechaRig;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.commands.arguments.EntityAnchorArgument;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
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
 * The Chimera on GeckoLib and its Graft (the arms' blow remembered, the drive payload's handler, the take at tick 14,
 * the minute, every refusal), and the Lunk drawn from its own model (boarding by a real click, the jar lifted down in
 * front, the toss pushed at its clip's release through the ability key's handler, the refused scoop), and old saves.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class ChimeraLunkGameTests {
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

    /** A Chimera with legs and a fist on each arm, owned by {@code owner}, battery full, facing south. */
    private static MechaEntity chimera(GameTestHelper h, BlockPos at, UUID owner) {
        MechaEntity m = h.spawn(ModRegistry.mechaType(MechaFrame.CHIMERA), at);
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < m.frame().totalSlots(); i++) slots.add(ItemStack.EMPTY);
        slots.set(m.frame().firstIndex(MechaModule.Slot.LEGS), module(MechaModule.HYDRAULIC_LEGS));
        int arm = m.frame().firstIndex(MechaModule.Slot.ARM);
        slots.set(arm, module(MechaModule.HYDRAULIC_FIST));
        slots.set(arm + 1, module(MechaModule.HYDRAULIC_FIST));
        m.setModules(slots);
        m.setOwner(owner);
        m.setBattery(m.batteryCapacity());
        face(m);
        return m;
    }

    private static void face(LivingEntity m) {
        m.setYRot(0);
        m.setYBodyRot(0);
        m.yBodyRotO = 0;
    }

    /** The player, as a jar, climbs on by a real click. */
    private static void board(GameTestHelper h, ServerPlayer p, net.minecraft.world.entity.Mob m, Form form) {
        if (FormActions.state(p).form() == Form.BODY) FormActions.detach(p, 3);
        p.interactOn(m, InteractionHand.MAIN_HAND);
        h.assertTrue(p.getVehicle() == m && FormActions.state(p).form() == form, "the click did not board: " + FormActions.state(p).form());
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

    private static String clip(RoverAnimation.Choice c) {
        return c == null ? "none" : c.clip().name();
    }

    private static String main(MechaEntity m, double at) {
        return clip(HopperAnimation.main(m, new HopperView(), at));
    }

    private static long count(ServerPlayer p, String reason) {
        return Refusals.delivered(p).stream().filter(reason::equals).count();
    }

    private static int kind(MechaEntity m) {
        return m.verbAt() < 0 ? -1 : (int) (m.verbAt() & 7);
    }

    private static int gel(ServerPlayer p) {
        return p.getInventory().countItem(ModRegistry.BIO_GEL.get());
    }

    // --- the Chimera ----------------------------------------------------------------------------------------------

    /**
     * The left fist hits a spider (the arm buttons' handler); the drive key then grafts with the left arm: 2 kFE, it
     * stands still, at tick 14 Spider Climb is taken for a minute, one Bio-Gel is spent into the veins, the veins take
     * the spider's blood colour; when the minute is up the gene goes. The three Gene Ports are untouched.
     */
    @GameTest(template = "platform", timeoutTicks = 80)
    public static void theGraftTakesWhatTheArmJustHit(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        BlockPos at = FLOOR.east(3);
        MechaEntity m = chimera(h, at, p.getUUID());
        board(h, p, m, Form.PILOT);
        p.getInventory().add(new ItemStack(ModRegistry.BIO_GEL.get(), 2));
        Spider spider = h.spawn(EntityType.SPIDER, at.south(2));
        spider.setNoAi(true);
        long[] start = new long[1];
        h.runAfterDelay(2, () -> {
            p.lookAt(EntityAnchorArgument.Anchor.EYES, spider.position().add(0, 0.4, 0));
            Network.arms(p, 1);
        });
        h.runAfterDelay(4, () -> Network.arms(p, 0));
        // the blow lands with the strike clip's contact, ArmAim.STRIKE_CONTACT ticks after the press
        h.runAfterDelay(12, () -> {
            h.assertTrue(m.tasted == EntityType.SPIDER && m.tastedSide == 0, "the left fist's blow was not remembered: " + m.tasted + " " + m.tastedSide
                    + " spider health " + spider.getHealth());
            int battery = m.battery();
            start[0] = now(h);
            press(p);
            h.assertTrue(kind(m) == Chimera.GRAFT_L && m.verbAt() / 8 == start[0], "the drive key did not graft with the left arm: " + Refusals.delivered(p));
            h.assertTrue(battery - m.battery() >= Chimera.GRAFT_FE, "the graft cost nothing");
            h.assertTrue(main(m, start[0] + 10).equals("graft_l"), "no graft_l clip: " + main(m, start[0] + 10));
            p.zza = 1;
            h.assertTrue(MechaLogic.riddenInput(m, p).equals(Vec3.ZERO), "it walks with its arm at its face");
            p.zza = 0;
            h.assertTrue(Chimera.grafted(m) == null && gel(p) == 2, "taken before the gulp");
        });
        h.runAfterDelay(12 + Chimera.TAKE + 2, () -> {
            h.assertTrue(Chimera.grafted(m) == Gene.SPIDER_CLIMB, "Spider Climb was not taken: " + Chimera.grafted(m));
            h.assertTrue(gel(p) == 1, "no Bio-Gel spent: " + gel(p));
            h.assertTrue(m.getPersistentData().getInt(MechaLogic.VEINS) > 1000, "the Bio-Gel is not in the veins");
            h.assertTrue(GeneEffects.has(m, Gene.SPIDER_CLIMB), "the grafted gene does not work");
            h.assertTrue(Genetics.genes(m).serums.isEmpty(), "the graft took a Gene Port");
            h.assertTrue(Chimera.tint(m) == Chimera.colour(BloodType.of(spider)), "the veins are not the spider's colour");
            h.assertTrue(Chimera.secondsLeft(m) >= 58 && Chimera.secondsLeft(m) <= 60, "not a minute: " + Chimera.secondsLeft(m));
            // the minute runs out
            m.setGraft(now(h) - Chimera.LASTS, Gene.SPIDER_CLIMB, Chimera.tint(m));
        });
        h.runAfterDelay(12 + Chimera.TAKE + 4, () -> {
            h.assertTrue(Chimera.grafted(m) == null && m.graftAt() == -1 && !GeneEffects.has(m, Gene.SPIDER_CLIMB), "the graft outlived its minute");
            spider.discard();
            done(h, p);
        });
    }

    /** The right fist's blow grafts with the right arm's clip. */
    @GameTest(template = "platform", timeoutTicks = 40)
    public static void theRightArmGraftsWithItsOwnClip(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        BlockPos at = FLOOR.east(3);
        MechaEntity m = chimera(h, at, p.getUUID());
        board(h, p, m, Form.PILOT);
        p.getInventory().add(new ItemStack(ModRegistry.BIO_GEL.get()));
        Spider spider = h.spawn(EntityType.SPIDER, at.south(2));
        spider.setNoAi(true);
        h.runAfterDelay(2, () -> {
            p.lookAt(EntityAnchorArgument.Anchor.EYES, spider.position().add(0, 0.4, 0));
            Network.arms(p, 2);
        });
        h.runAfterDelay(4, () -> Network.arms(p, 0));
        // the blow lands with the strike clip's contact, ArmAim.STRIKE_CONTACT ticks after the press
        h.runAfterDelay(12, () -> {
            h.assertTrue(m.tasted == EntityType.SPIDER && m.tastedSide == 1, "the right fist's blow was not remembered: " + m.tasted + " " + m.tastedSide);
            press(p);
            h.assertTrue(kind(m) == Chimera.GRAFT_R && main(m, now(h) + 10).equals("graft_r"), "no graft_r: " + Refusals.delivered(p));
            spider.discard();
            done(h, p);
        });
    }

    /**
     * Every refused Graft plays the recoil and says why once: nothing tasted, a taste gone stale, a Legendary gene, a
     * creature with no gene, no Bio-Gel, no charge, venting, a graft still taking; a press mid-gulp says so in a line.
     */
    @GameTest(template = "platform", timeoutTicks = 160)
    public static void everyRefusedGraftSaysWhy(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        BlockPos at = FLOOR.east(3);
        MechaEntity m = chimera(h, at, p.getUUID());
        board(h, p, m, Form.PILOT);
        h.runAfterDelay(2, () -> {
            press(p);
            h.assertTrue(count(p, "mecha.graft.nothing") == 1 && kind(m) == Chimera.REFUSED, "nothing tasted was not refused");
            h.assertTrue(main(m, now(h) + 8).equals("graft_refused"), "no recoil clip");
            // tasted too long ago
            m.tasted = EntityType.SPIDER;
            m.tastedAt = now(h) - Chimera.TASTE - 1;
        });
        h.runAfterDelay(14, () -> {
            press(p);
            h.assertTrue(count(p, "mecha.graft.nothing") == 2, "a stale taste was grafted");
            m.tasted = EntityType.WARDEN;
            m.tastedAt = now(h);
        });
        h.runAfterDelay(26, () -> {
            press(p);
            h.assertTrue(count(p, "mecha.graft.legendary") == 1, "a Legendary gene was not refused: " + Refusals.delivered(p));
            m.tasted = EntityType.COW;
            m.tastedAt = now(h);
        });
        h.runAfterDelay(38, () -> {
            press(p);
            h.assertTrue(count(p, "mecha.graft.no_genes") == 1, "a creature with no gene was not refused");
            m.tasted = EntityType.SPIDER;
            m.tastedAt = now(h);
        });
        h.runAfterDelay(50, () -> {
            press(p);
            h.assertTrue(count(p, "mecha.graft.no_gel") == 1, "no Bio-Gel was not refused");
            p.getInventory().add(new ItemStack(ModRegistry.BIO_GEL.get(), 2));
            m.tastedAt = now(h);
            m.setBattery(500);
        });
        h.runAfterDelay(62, () -> {
            press(p);
            h.assertTrue(count(p, "mecha.graft.no_power") == 1, "no charge was not refused");
            m.setBattery(m.batteryCapacity());
            m.setVent(40);
            m.tastedAt = now(h);
        });
        h.runAfterDelay(74, () -> {
            press(p);
            h.assertTrue(count(p, "mecha.graft.venting") == 1, "venting was not refused");
            m.setVent(0);
            m.tastedAt = now(h);
        });
        h.runAfterDelay(76, () -> {
            press(p);
            h.assertTrue(kind(m) == Chimera.GRAFT_L, "a fit graft was refused: " + Refusals.delivered(p));
            press(p);
            h.assertTrue(count(p, "mecha.graft.busy") == 1 && kind(m) == Chimera.GRAFT_L, "a press mid-gulp did not say busy");
        });
        h.runAfterDelay(76 + Chimera.GRAFT_TICKS + 2, () -> {
            h.assertTrue(Chimera.grafted(m) == Gene.SPIDER_CLIMB, "the fit graft was not taken");
            m.tasted = EntityType.SPIDER;
            m.tastedAt = now(h);
            press(p);
            h.assertTrue(count(p, "mecha.graft.running") == 1 && kind(m) == Chimera.REFUSED, "a second graft over a running one was not refused");
            done(h, p);
        });
    }

    /** The drive key on a Chimera never pogos; its idle tic twitches either forearm. */
    @GameTest(template = "platform", timeoutTicks = 20)
    public static void theChimeraHasNoPogoAndTwitchesEitherArm(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = chimera(h, FLOOR.east(3), p.getUUID());
        board(h, p, m, Form.PILOT);
        h.runAfterDelay(2, () -> {
            HopperNet.drive(p, true);
            h.assertTrue(m.pogoAt() == -1 && m.pogoCharge < 0, "the drive key charged a pogo on a Chimera");
            HopperNet.drive(p, false);
            h.assertTrue(m.leapAt() == -1, "a Chimera leapt");
            Set<String> tics = new HashSet<>();
            for (int t = 0; t < 4000; t += 2) {
                String c = main(m, now(h) + 100 + t);
                h.assertFalse(c.startsWith("pogo"), "a pogo clip on a Chimera");
                if (c.startsWith("idle_tic")) tics.add(c);
            }
            h.assertTrue(tics.contains("idle_tic") && tics.contains("idle_tic_r"), "the tic does not come to both forearms: " + tics);
            done(h, p);
        });
    }

    /** Every slot (the third torso socket too), tracked bone, lamp, locator and clip is in the shipped model; true size. */
    @GameTest(template = "platform")
    public static void theChimeraIsDrawnFromItsOwnModel(GameTestHelper h) {
        MechaFrame frame = MechaFrame.CHIMERA;
        MechaRig rig = MechaRig.of(frame);
        h.assertTrue(frame.animated() && frame.renderScale == 1f && rig.verb == MechaRig.Verb.GRAFT, "not an animated Chimera at true size");
        Set<String> bones = names("geo", "mecha/chimera", "minecraft:geometry");
        for (int i = 0; i < frame.totalSlots(); i++) {
            for (String s : HopperSockets.sockets(frame, i)) {
                h.assertTrue(bones.contains(s), "slot " + i + " socket " + s + " is not in the model");
                h.assertTrue(HopperSockets.slotOf(frame, s) == i, "socket " + s + " maps back to the wrong slot");
            }
        }
        h.assertTrue(bones.contains("socket_torso_2"), "no third torso socket");
        for (String b : rig.tracked) h.assertTrue(bones.contains(b), "tracked bone " + b + " missing");
        for (String b : rig.lamps) h.assertTrue(bones.contains(b), "lamp bone " + b + " missing");
        for (var l : rig.locators.values()) h.assertTrue(bones.contains(l.bone()), "locator bone " + l.bone() + " missing");
        h.assertTrue(bones.contains(rig.hatBone) && bones.contains("growth"), "no hat or growth bone");
        for (int n = 0; n < 3; n++) h.assertTrue(bones.contains("gene_port_" + n), "no gene_port_" + n);
        Set<String> clips = names("animations", "mecha/chimera", "animations");
        for (String c : List.of("parked", "board", "boot", "boot_legs", "shutdown", "idle", "idle_tic", "idle_tic_r", "walk", "limp", "turn_l",
                "turn_r", "jump", "fall", "land", "land_heavy", "vent", "hurt", "eject", "destroyed", "build", "dry_fire_l", "dry_fire_r",
                "graft_l", "graft_r", "graft_refused")) {
            h.assertTrue(clips.contains(c) && rig.has(c), "the Chimera lacks clip " + c);
            h.assertTrue(Math.abs(rig.clip(c).length() - length("mecha/chimera", c)) <= 1, "clip " + c + " has the wrong length in the rig");
        }
        for (String c : clips) h.assertFalse(c.startsWith("pogo"), "the Chimera ships " + c);
        Set<String> lunk = names("geo", "shell/lunk", "minecraft:geometry");
        for (String b : LunkAnimation.TRACKED) h.assertTrue(lunk.contains(b), "the Lunk lacks bone " + b);
        for (Object[] l : LunkAnimation.LOCATORS.values()) h.assertTrue(lunk.contains((String) l[0]), "the Lunk lacks locator bone " + l[0]);
        Set<String> lunkClips = names("animations", "shell/lunk", "animations");
        for (String c : List.of("parked", "board", "alight", "idle", "idle_tic", "walk", "toss", "toss_refused", "hurt", "flat", "destroyed")) {
            h.assertTrue(lunkClips.contains(c) && LunkAnimation.clip(c) != null, "the Lunk lacks clip " + c);
            h.assertTrue(Math.abs(LunkAnimation.clip(c).length() - length("shell/lunk", c)) <= 1, "Lunk clip " + c + " has the wrong length");
        }
        h.succeed();
    }

    private static JsonObject json(String kind, String model) {
        String path = kind.equals("geo") ? "/assets/headinajar/geo/entity/" + model + ".geo.json" : "/assets/headinajar/animations/entity/" + model + ".animation.json";
        var in = ChimeraLunkGameTests.class.getResourceAsStream(path);
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

    // --- the Lunk ---------------------------------------------------------------------------------------------------

    /** A real click puts the jar on the lid (the board clip); getting off lifts it down to the floor in front. */
    @GameTest(template = "platform", timeoutTicks = 40)
    public static void theLunkCupsTheJarUpAndSetsItDownInFront(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(3));
        ShellEntity l = h.spawn(ModRegistry.LUNK.get(), FLOOR);
        face(l);
        h.assertTrue(l.animated(), "the Lunk is not drawn from its model");
        board(h, p, l, Form.HOSTED);
        long on = l.boardedAt();
        h.assertTrue(on == now(h), "boarding not stamped");
        h.assertTrue(clip(LunkAnimation.cockpit(l, on + 10)).equals("board"), "no board clip");
        h.runAfterDelay(2, () -> {
            face(l);
            FormActions.leaveHost(p);
            h.assertTrue(p.getVehicle() == null && (l.gotOffAt() & 3) == ShellEntity.FRONT, "the jar was not put in front: " + l.gotOffAt());
            // facing south: in front is +z
            h.assertTrue(p.getZ() > l.getZ() + 0.5, "the jar is not in front: " + p.position() + " " + l.position());
            h.assertTrue(clip(LunkAnimation.cockpit(l, now(h) + 5)).equals("alight"), "no alight clip");
            h.assertTrue(LunkAnimation.alighting(l, now(h) + 10) >= 0 && LunkAnimation.alighting(l, now(h) + LunkAnimation.ALIGHT_RELEASE + 1) < 0,
                    "the jar is not held in the fists until the release");
            h.assertTrue(clip(LunkAnimation.main(l, l.view, now(h) + 5)).equals("parked"), "not parked when empty");
        });
        // the front walled: the jar goes behind, and the Lunk turns to face there before its clip sets it down
        h.runAfterDelay(6, () -> {
            Vec3 aside = h.absoluteVec(Vec3.atBottomCenterOf(FLOOR.west(2)));
            p.teleportTo(aside.x, aside.y, aside.z);
            h.setBlock(FLOOR.south(1), net.minecraft.world.level.block.Blocks.STONE);
            h.setBlock(FLOOR.south(1).above(), net.minecraft.world.level.block.Blocks.STONE);
            face(l);
            board(h, p, l, Form.HOSTED);
            FormActions.leaveHost(p);
            h.assertTrue((l.gotOffAt() & 3) == ShellEntity.BEHIND && p.getZ() < l.getZ() - 0.5, "not put behind: " + l.gotOffAt() + " " + p.position());
            h.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(l.getYRot() - 180)) < 1 && Math.abs(net.minecraft.util.Mth.wrapDegrees(l.yBodyRot - 180)) < 1,
                    "the Lunk did not turn to face where the jar went: " + l.getYRot());
            h.assertTrue(clip(LunkAnimation.cockpit(l, now(h) + 5)).equals("alight"), "no alight clip");
            done(h, p);
        });
    }

    /**
     * The ability key's handler tosses the cow in front: the toss clip plays from the press and the cow goes up only at
     * the release (tick 19). With nothing in reach, or a flat jar, the scoop is refused with its clip and a line.
     */
    @GameTest(template = "platform", timeoutTicks = 100)
    public static void theLunkTossesAtTheClipsRelease(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(3));
        ShellEntity l = h.spawn(ModRegistry.LUNK.get(), FLOOR);
        face(l);
        board(h, p, l, Form.HOSTED);
        FormActions.state(p).setCharge(FormActions.state(p).chargeCapacity());
        // a chicken with its own physics (a no-AI mob is immobile: it would never leave the ground); small enough to hold
        // overhead under the test area's ceiling
        var cow = h.spawn(EntityType.CHICKEN, FLOOR.south(2));
        long[] start = new long[1];
        h.runAfterDelay(2, () -> {
            Hosts.data(l).primaryCooldown = 0;
            p.lookAt(EntityAnchorArgument.Anchor.EYES, cow.position().add(0, 0.7, 0));
            start[0] = now(h);
            Network.action(p, new Network.JarAction(Network.Action.ABILITY));
            h.assertTrue(l.tossAt() / 4 == start[0] && (l.tossAt() & 3) == ShellEntity.TOSS, "the toss was not stamped: " + Refusals.delivered(p));
            h.assertTrue(clip(LunkAnimation.main(l, l.view, start[0] + 5)).equals("toss"), "no toss clip");
        });
        // between the scoop and the release it rides in the fists: on the clip's grip point, the server's say
        h.runAfterDelay(2 + 11, () -> {
            Vec3 px = LunkAnimation.grip(11);
            Vec3 at = l.position().add(Vec3.directionFromRotation(0, l.yBodyRot).scale(-px.z / 16)).add(0, px.y / 16, 0);
            h.assertTrue(cow.position().distanceTo(at) < 0.3 && l.tossHeldId() == cow.getId(), "the load is not in the fists: " + cow.position() + " " + at);
            h.assertTrue(cow.getDeltaMovement().y < 0.3, "pushed before the release: " + cow.getDeltaMovement());
        });
        h.runAfterDelay(2 + LunkAnimation.TOSS_RELEASE + 2, () -> {
            h.assertTrue(cow.getDeltaMovement().y > 0.3 || cow.getY() > l.getY() + 2.5,
                    "the cow was not tossed at the release: " + cow.position() + " " + cow.getDeltaMovement());
            cow.discard();
        });
        h.runAfterDelay(60, () -> {
            Hosts.data(l).primaryCooldown = 0;
            p.setXRot(-60);
            Network.action(p, new Network.JarAction(Network.Action.ABILITY));
            h.assertTrue(count(p, "host.no_target") == 1 && (l.tossAt() & 3) == ShellEntity.TOSS_REFUSED, "nothing in reach was not refused");
            h.assertTrue(clip(LunkAnimation.main(l, l.view, now(h) + 5)).equals("toss_refused"), "no refused scoop");
            FormActions.state(p).setCharge(0);
        });
        h.runAfterDelay(90, () -> {
            Hosts.data(l).primaryCooldown = 0;
            Network.action(p, new Network.JarAction(Network.Action.ABILITY));
            h.assertTrue(count(p, "host.shell_flat") == 1 && (l.tossAt() & 3) == ShellEntity.TOSS_REFUSED && l.tossAt() / 4 == now(h),
                    "a flat Lunk did not refuse the toss: " + Refusals.delivered(p));
            done(h, p);
        });
    }

    /** It knuckle-walks from its measured pace and slumps when its jar is flat. */
    @GameTest(template = "platform", timeoutTicks = 20)
    public static void theLunkWalksAndGoesFlat(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.west(3));
        ShellEntity l = h.spawn(ModRegistry.LUNK.get(), FLOOR);
        board(h, p, l, Form.HOSTED);
        FormActions.state(p).setCharge(FormActions.state(p).chargeCapacity());
        h.runAfterDelay(2, () -> {
            l.view.lunkSpeed = LunkAnimation.WALK_DESIGN;
            h.assertTrue(clip(LunkAnimation.main(l, l.view, now(h) + 40)).equals("walk"), "no knuckle-walk");
            l.view.lunkSpeed = 0;
            l.view.flatSince = now(h);
            FormActions.state(p).setCharge(0);
            h.assertTrue(LunkAnimation.flat(l) && clip(LunkAnimation.main(l, l.view, now(h) + 10)).equals("flat"), "no slump on a flat jar");
            done(h, p);
        });
    }

    // --- old saves ----------------------------------------------------------------------------------------------

    /** A Chimera and a Lunk as the previous version saved them load with their state and save no new key. */
    @GameTest(template = "platform")
    public static void aChimeraAndALunkSavedBeforeLoad(GameTestHelper h) {
        UUID owner = UUID.randomUUID();
        Vec3 at = h.absoluteVec(Vec3.atBottomCenterOf(FLOOR));
        CompoundTag tag = base("headinajar:chimera", at);
        ListTag modules = new ListTag();
        for (int i = 0; i < MechaFrame.CHIMERA.totalSlots(); i++) {
            ItemStack stack = i == 0 ? module(MechaModule.HYDRAULIC_LEGS) : ItemStack.EMPTY;
            modules.add(stack.saveOptional(h.getLevel().registryAccess()));
        }
        tag.put("Modules", modules);
        tag.put("Cargo", new ListTag());
        tag.putInt("Battery", 150_000);
        tag.putFloat("Heat", 80);
        tag.putUUID("Owner", owner);
        var loaded = EntityType.loadEntityRecursive(tag, h.getLevel(), e -> e);
        h.assertTrue(loaded instanceof MechaEntity, "the old Chimera did not load: " + loaded);
        MechaEntity m = (MechaEntity) loaded;
        h.assertTrue(m.frame() == MechaFrame.CHIMERA && m.frame().animated(), "not an animated Chimera");
        h.assertTrue(m.has(MechaModule.HYDRAULIC_LEGS) && m.battery() == 150_000 && Math.abs(m.heat() - 80) < 0.01 && owner.equals(m.owner())
                && Math.abs(m.getHealth() - 97) < 0.01, "the Chimera's modules, battery, heat, owner or health were lost");
        h.assertTrue(m.verbAt() == -1 && m.graftAt() == -1 && Chimera.grafted(m) == null, "an old Chimera loads mid-graft");
        CompoundTag saved = new CompoundTag();
        m.addAdditionalSaveData(saved);
        for (String key : List.of("Graft", "Grafted", "Verb", "Tasted")) h.assertFalse(saved.contains(key), "new saved key " + key);

        CompoundTag lunk = base("headinajar:lunk", at);
        lunk.putFloat("Health", 45);
        lunk.put("Cargo", new ListTag());
        var shell = EntityType.loadEntityRecursive(lunk, h.getLevel(), e -> e);
        h.assertTrue(shell instanceof ShellEntity s && s.kind() == ShellEntity.Kind.LUNK && s.animated(), "the old Lunk did not load: " + shell);
        ShellEntity s = (ShellEntity) shell;
        h.assertTrue(s.tossAt() == -1 && s.boardedAt() == -1 && Math.abs(s.getHealth() - 45) < 0.01, "an old Lunk loads mid-toss");
        CompoundTag again = new CompoundTag();
        s.addAdditionalSaveData(again);
        for (String key : List.of("Toss", "Tossed", "Boarded")) h.assertFalse(again.contains(key), "new saved key " + key);
        h.succeed();
    }

    private static CompoundTag base(String id, Vec3 at) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
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
        return tag;
    }
}
