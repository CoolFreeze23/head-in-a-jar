package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.fx.Fx;
import com.coolfreeze23.headinajar.item.MechaModuleItem;
import com.coolfreeze23.headinajar.mecha.ArmAim;
import com.coolfreeze23.headinajar.mecha.HopperAnimation;
import com.coolfreeze23.headinajar.mecha.HopperView;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The arm tools at work, through the arm buttons' payload handler: a ray leaves the tool on the arm's own side, not the
 * pilot's eye, and lands where the pilot looks; the Arc Cannon's shot and the Hydraulic Fist's blow wait for their arm
 * clip's contact; the arc jumps two ticks apart; a Tractor Beam's hold is synced to every watcher; each tool is worked
 * with the verb of its kind (a guard jolts when a blow lands on it); a machine below a quarter of its health limps.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class ArmVerbGameTests {
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

    /** A machine of {@code frame} with these modules (each in the first free slot of its kind), full battery, facing south. */
    private static MechaEntity mecha(GameTestHelper h, MechaFrame frame, BlockPos at, UUID owner, MechaModule... mods) {
        MechaEntity m = h.spawn(ModRegistry.mechaType(frame), at);
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < m.frame().totalSlots(); i++) slots.add(ItemStack.EMPTY);
        Set<Integer> used = new HashSet<>();
        for (MechaModule mod : mods) {
            int i = m.frame().firstIndex(mod.slot);
            while (used.contains(i)) i++;
            used.add(i);
            ItemStack stack = new ItemStack(ModRegistry.mechaModule(mod));
            MechaModuleItem.setSlider(stack, 50);
            slots.set(i, stack);
        }
        m.setModules(slots);
        m.setOwner(owner);
        m.setBattery(m.batteryCapacity());
        face(m, 0);
        return m;
    }

    private static void face(MechaEntity m, float yaw) {
        m.setYRot(yaw);
        m.setYBodyRot(yaw);
        m.yBodyRotO = yaw;
    }

    /** The player, as a jar, climbs in by a real click on the machine. */
    private static void board(GameTestHelper h, ServerPlayer p, MechaEntity m) {
        if (FormActions.state(p).form() == Form.BODY) FormActions.detach(p, 3);
        p.interactOn(m, InteractionHand.MAIN_HAND);
        h.assertTrue(p.getVehicle() == m && FormActions.state(p).form() == Form.PILOT, "the click did not board: " + FormActions.state(p).form());
        m.setOnGround(true);
    }

    /** The machine's left, horizontally (its facing turned a quarter to the left). */
    private static Vec3 left(MechaEntity m) {
        double yaw = Math.toRadians(m.yBodyRot);
        return new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
    }

    private static List<Fx> arcs() {
        List<Fx> out = new ArrayList<>();
        for (Fx.Sent s : Fx.sent()) if (s.fx().kind() == Fx.ARM_ARC) out.add(s.fx());
        return out;
    }

    private static List<Long> arcTimes() {
        List<Long> out = new ArrayList<>();
        for (Fx.Sent s : Fx.sent()) if (s.fx().kind() == Fx.ARM_ARC) out.add(s.gameTime());
        return out;
    }

    private static long now(GameTestHelper h) {
        return h.getLevel().getGameTime();
    }

    private static String clip(HopperAnimation.Choice c) {
        return c == null ? "none" : c.clip().name();
    }

    /**
     * The left arm's Arc Cannon, tapped while the pilot looks at a cow: nothing is hit at the press (the capacitor
     * charges while the arm winds up); with the recoil's contact one arc goes out, from the cannon on the machine's
     * left, well away from the pilot's eye, to the cow, and the cow is hurt.
     */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void theArcLeavesTheCannonAndLandsWithTheRecoil(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = mecha(h, MechaFrame.HOPPER, FLOOR.east(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS, MechaModule.ARC_CANNON);
        board(h, p, m);
        Cow cow = h.spawn(EntityType.COW, FLOOR.east(3).south(5));
        cow.setNoAi(true);
        float[] health = new float[1];
        long[] pressed = new long[1];
        h.runAfterDelay(2, () -> {
            Fx.clearSent();
            p.lookAt(EntityAnchorArgument.Anchor.EYES, cow.getBoundingBox().getCenter());
            health[0] = cow.getHealth();
            pressed[0] = now(h);
            Network.arms(p, 1);
        });
        h.runAfterDelay(3, () -> Network.arms(p, 0));
        h.runAfterDelay(2 + ArmAim.RECOIL_CONTACT - 2, () -> {
            h.assertTrue(arcs().isEmpty(), "the arc went out at the press, not with the recoil: " + arcTimes() + " pressed " + pressed[0]);
            h.assertTrue(cow.getHealth() == health[0], "the cow was hurt before the recoil's contact");
        });
        h.runAfterDelay(2 + ArmAim.RECOIL_CONTACT + 3, () -> {
            List<Fx> arcs = arcs();
            h.assertTrue(arcs.size() == 1, "not one arc for one cow: " + arcs.size());
            Fx arc = arcs.getFirst();
            h.assertTrue(arc.entityA() == m.getId() && arc.entityB() == cow.getId(), "the arc does not run from the machine to the cow");
            h.assertTrue(Math.round(arc.arg()) == ArmAim.slot(m, 0), "the arc does not name the cannon's slot: " + arc.arg());
            Vec3 from = arc.a();
            h.assertTrue(from.subtract(m.position()).dot(left(m)) > 0.3, "the arc does not leave from the machine's left: " + from.subtract(m.position()));
            h.assertTrue(from.distanceTo(p.getEyePosition()) > 0.5, "the arc leaves from the pilot's eye: " + from.distanceTo(p.getEyePosition()));
            h.assertTrue(arc.b().distanceTo(cow.getBoundingBox().getCenter()) < 0.6, "the arc does not land on the cow");
            h.assertTrue(cow.getHealth() < health[0], "the cow was not hurt by the shot");
            long shot = arcTimes().getFirst();
            h.assertTrue(shot - pressed[0] >= ArmAim.RECOIL_CONTACT - 1 && shot - pressed[0] <= ArmAim.RECOIL_CONTACT + 1,
                    "the shot is not on the recoil's contact: " + (shot - pressed[0]));
            cow.discard();
            done(h, p);
        });
    }

    /** The arc jumps from the cow to the two spiders beside it, a jump every 2 ticks, each its own memo, numbered. */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void theArcJumpsTwoTicksApart(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = mecha(h, MechaFrame.HOPPER, FLOOR.east(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS, MechaModule.ARC_CANNON);
        board(h, p, m);
        Cow cow = h.spawn(EntityType.COW, FLOOR.east(3).south(5));
        cow.setNoAi(true);
        Spider a = h.spawn(EntityType.SPIDER, FLOOR.east(5).south(5)), b = h.spawn(EntityType.SPIDER, FLOOR.east(7).south(5));
        a.setNoAi(true);
        b.setNoAi(true);
        h.runAfterDelay(2, () -> {
            Fx.clearSent();
            p.lookAt(EntityAnchorArgument.Anchor.EYES, cow.getBoundingBox().getCenter());
            Network.arms(p, 1);
        });
        h.runAfterDelay(3, () -> Network.arms(p, 0));
        h.runAfterDelay(2 + ArmAim.RECOIL_CONTACT + 10, () -> {
            List<Fx> arcs = arcs();
            List<Long> times = arcTimes();
            h.assertTrue(arcs.size() == 3, "not three jumps (cow, two spiders): " + arcs.size());
            for (int i = 0; i < 3; i++) {
                h.assertTrue(Math.round(arcs.get(i).arg()) / 64 == i, "jump " + i + " is numbered " + Math.round(arcs.get(i).arg()) / 64);
                if (i > 0) {
                    h.assertTrue(times.get(i) - times.get(i - 1) == 2, "jumps not 2 ticks apart: " + times);
                    h.assertTrue(arcs.get(i).entityA() == arcs.get(i - 1).entityB(), "a jump does not leave from the last one hit");
                }
            }
            h.assertTrue(arcs.get(0).entityB() == cow.getId() && arcs.get(1).entityB() == a.getId() && arcs.get(2).entityB() == b.getId(),
                    "the chain is not cow, the near spider, the far spider");
            h.assertTrue(a.getHealth() < a.getMaxHealth() && b.getHealth() < b.getMaxHealth(), "a spider jumped to was not hurt");
            done(h, p);
        });
    }

    /** With nothing in its reach but a wall, the arc lands where the pilot looks: on the wall's face. */
    @GameTest(template = "platform", timeoutTicks = 40)
    public static void aMissedArcLandsWhereThePilotLooks(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = mecha(h, MechaFrame.HOPPER, FLOOR.east(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS, MechaModule.ARC_CANNON);
        board(h, p, m);
        BlockPos wall = FLOOR.east(3).south(6).above(2);
        h.setBlock(wall, net.minecraft.world.level.block.Blocks.STONE);
        Vec3 face = h.absoluteVec(Vec3.atCenterOf(wall)).subtract(0, 0, 0.5);
        h.runAfterDelay(2, () -> {
            Fx.clearSent();
            p.lookAt(EntityAnchorArgument.Anchor.EYES, face);
            Network.arms(p, 1);
        });
        h.runAfterDelay(3, () -> Network.arms(p, 0));
        h.runAfterDelay(2 + ArmAim.RECOIL_CONTACT + 3, () -> {
            List<Fx> arcs = arcs();
            h.assertTrue(arcs.size() == 1 && arcs.getFirst().entityB() < 0, "not one arc to no creature: " + arcs.size());
            h.assertTrue(arcs.getFirst().b().distanceTo(face) < 0.3, "the miss does not land on the wall where the pilot looks: " + arcs.getFirst().b() + " vs " + face);
            done(h, p);
        });
    }

    /** Aim: the end of a ray is the creature the pilot looks at, or the block; it never starts at the pilot's eye. */
    @GameTest(template = "platform", timeoutTicks = 40)
    public static void aRayGoesWhereThePilotLooksFromTheArmsOwnSide(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = mecha(h, MechaFrame.HOPPER, FLOOR.east(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS, MechaModule.PLASMA_LANCE);
        board(h, p, m);
        Cow cow = h.spawn(EntityType.COW, FLOOR.east(3).south(6));
        cow.setNoAi(true);
        h.runAfterDelay(2, () -> {
            p.lookAt(EntityAnchorArgument.Anchor.EYES, cow.getBoundingBox().getCenter());
            h.assertTrue(ArmAim.aim(p, m, 32, 1) instanceof EntityHitResult e && e.getEntity() == cow, "the aim does not meet the cow");
            h.assertTrue(ArmAim.end(p, m, 32, 1).distanceTo(cow.getBoundingBox().getCenter()) < 0.05, "the ray does not end on the cow's middle");
            // looking down at the floor beside it
            p.lookAt(EntityAnchorArgument.Anchor.EYES, h.absoluteVec(new Vec3(FLOOR.getX() + 0.5, FLOOR.getY(), FLOOR.getZ() + 3.5)));
            Vec3 end = ArmAim.end(p, m, 32, 1);
            h.assertTrue(Math.abs(end.y - h.absoluteVec(Vec3.atLowerCornerOf(FLOOR)).y) < 0.05, "the ray does not end on the floor: " + end);
            for (int side = 0; side < 2; side++) {
                Vec3 from = ArmAim.origin(m, side, 1).subtract(m.position());
                double l = from.dot(left(m));
                h.assertTrue(side == 0 ? l > 0.3 : l < -0.3, "arm " + side + " is on the wrong side: " + l);
                h.assertTrue(from.y > 0.3 && from.y < m.getBbHeight() + 0.5, "arm " + side + " is not at arm height: " + from.y);
            }
            done(h, p);
        });
    }

    /** Every animated frame with two arms: its left arm's muzzle is on its left and its right's on its right, as it turns. */
    @GameTest(template = "platform", timeoutTicks = 30)
    public static void everyFramesArmsAreOnTheirOwnSides(GameTestHelper h) {
        StringBuilder wrong = new StringBuilder();
        for (MechaFrame frame : MechaFrame.values()) {
            if (!frame.animated() || frame.slots(MechaModule.Slot.ARM) < 2) continue;
            MechaEntity m = mecha(h, frame, FLOOR.east(3), UUID.randomUUID());
            for (float yaw : new float[]{0, 90, 200}) {
                face(m, yaw);
                double l = ArmAim.origin(m, 0, 1).subtract(m.position()).dot(left(m)), r = ArmAim.origin(m, 1, 1).subtract(m.position()).dot(left(m));
                if (!(l > 0.2 && r < -0.2)) wrong.append(frame.id()).append(" at ").append(yaw).append(": ").append(l).append(" / ").append(r).append("; ");
            }
            m.discard();
        }
        h.assertTrue(wrong.isEmpty(), "arms on the wrong side: " + wrong);
        h.succeed();
    }

    /** The fist's blow waits for the strike clip's contact: the cow is untouched at the press, hurt with the contact. */
    @GameTest(template = "platform", timeoutTicks = 40)
    public static void theFistsBlowLandsWithItsContact(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = mecha(h, MechaFrame.HOPPER, FLOOR.east(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS, MechaModule.HYDRAULIC_FIST);
        board(h, p, m);
        Cow cow = h.spawn(EntityType.COW, FLOOR.east(3).south(2));
        cow.setNoAi(true);
        float[] health = new float[1];
        h.runAfterDelay(2, () -> {
            p.lookAt(EntityAnchorArgument.Anchor.EYES, cow.getBoundingBox().getCenter());
            health[0] = cow.getHealth();
            Network.arms(p, 1);
        });
        h.runAfterDelay(3, () -> Network.arms(p, 0));
        h.runAfterDelay(2 + ArmAim.STRIKE_CONTACT - 3, () -> h.assertTrue(cow.getHealth() == health[0], "the cow was hurt before the fist arrived"));
        h.runAfterDelay(2 + ArmAim.STRIKE_CONTACT + 3, () -> {
            h.assertTrue(cow.getHealth() < health[0], "the fist's blow never landed");
            cow.discard();
            done(h, p);
        });
    }

    /** The Tractor Beam's hold reaches every watcher: what the left arm holds is in the synced state, and gone on release. */
    @GameTest(template = "platform", timeoutTicks = 40)
    public static void theTractorBeamsHoldIsSynced(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = mecha(h, MechaFrame.HOPPER, FLOOR.east(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS, MechaModule.TRACTOR_BEAM);
        board(h, p, m);
        Cow cow = h.spawn(EntityType.COW, FLOOR.east(3).south(5));
        h.runAfterDelay(2, () -> {
            p.lookAt(EntityAnchorArgument.Anchor.EYES, cow.getBoundingBox().getCenter());
            Network.arms(p, 1);
        });
        h.runAfterDelay(6, () -> {
            h.assertTrue(m.beamHeld(0) == cow.getId(), "the hold is not in the synced state: " + m.beamHeld(0));
            h.assertTrue(m.beamHeld(1) == -1, "the right arm holds something");
            Network.arms(p, 0);
        });
        h.runAfterDelay(9, () -> {
            h.assertTrue(m.beamHeld(0) == -1, "the hold outlived the release");
            cow.discard();
            done(h, p);
        });
    }

    /**
     * The Tractor Beam and the Grapple Hook go by the one rule for handling a player: a player who has not trusted the
     * pilot is neither held nor yanked, and the pilot hears the handling line; once that player shares the pilot's team
     * (nothing on their trust list), the beam holds them and the hook yanks them toward the machine.
     */
    @GameTest(template = "platform", timeoutTicks = 120)
    public static void theBeamAndTheHookTakeAPlayerOnlyWithTheirTrust(GameTestHelper h) {
        // named players: a team holds names, and every plain mock player has the same one
        ServerPlayer p = TestPlayers.at(h, "pilot", FLOOR);
        MechaEntity m = mecha(h, MechaFrame.HOPPER, FLOOR.east(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS, MechaModule.TRACTOR_BEAM,
                MechaModule.GRAPPLE_HOOK);
        board(h, p, m);
        h.assertTrue(m.arm(0) == MechaModule.TRACTOR_BEAM && m.arm(1) == MechaModule.GRAPPLE_HOOK, "the arms are not beam left, hook right");
        BlockPos stand = FLOOR.east(3).south(5);
        ServerPlayer q = TestPlayers.at(h, "mate", stand);
        var board = h.getLevel().getScoreboard();
        String name = "arm_crew_" + q.getId();
        Runnable aim = () -> {
            Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(stand));
            q.teleportTo(v.x, v.y, v.z);
            q.setDeltaMovement(Vec3.ZERO);
            p.lookAt(EntityAnchorArgument.Anchor.EYES, q.getBoundingBox().getCenter());
        };
        int[] battery = new int[1];
        // a stranger: the beam takes nothing
        h.runAfterDelay(2, () -> {
            aim.run();
            Network.arms(p, 1);
        });
        h.runAfterDelay(5, () -> {
            h.assertTrue(m.beamHeld(0) == -1, "the beam holds a player who has not trusted the pilot");
            h.assertValueEqual(com.coolfreeze23.headinajar.feedback.Refusals.lastDelivered(p), "mecha.lift.untrusted", "the beam's answer");
            Network.arms(p, 0);
        });
        // nor does the hook, and the throw costs nothing
        h.runAfterDelay(20, () -> {
            aim.run();
            battery[0] = m.battery();
            Network.arms(p, 2);
        });
        h.runAfterDelay(23, () -> {
            h.assertTrue(q.getDeltaMovement().lengthSqr() < 0.01, "the hook yanked a player who has not trusted the pilot: " + q.getDeltaMovement());
            h.assertValueEqual(com.coolfreeze23.headinajar.feedback.Refusals.lastDelivered(p), "mecha.lift.untrusted", "the hook's answer");
            h.assertTrue(battery[0] - m.battery() < 800, "a refused throw was paid for");
            Network.arms(p, 0);
            var team = board.addPlayerTeam(name);
            board.addPlayerToTeam(p.getScoreboardName(), team);
            board.addPlayerToTeam(q.getScoreboardName(), team);
        });
        // a teammate: held by the beam, then yanked by the hook
        h.runAfterDelay(26, () -> {
            aim.run();
            Network.arms(p, 1);
        });
        h.runAfterDelay(29, () -> {
            h.assertTrue(m.beamHeld(0) == q.getId(), "the beam did not take a teammate: " + com.coolfreeze23.headinajar.feedback.Refusals.delivered(p));
            Network.arms(p, 0);
        });
        h.runAfterDelay(32, () -> {
            aim.run();
            Network.arms(p, 2);
        });
        h.runAfterDelay(35, () -> {
            h.assertTrue(q.getDeltaMovement().z < -0.5, "the hook did not yank a teammate toward the machine: " + q.getDeltaMovement());
            Network.arms(p, 0);
            board.removePlayerTeam(board.getPlayerTeam(name));
            done(h, p, q);
        });
    }

    /**
     * Each arm tool is worked with the verb of its kind; held, the frame plays that verb's clip (a lance and a
     * flamethrower are held braced, not kicked); a blow landing on a raised Riot Shield jolts the arm back.
     */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void eachToolIsWorkedByItsKind(GameTestHelper h) {
        h.assertTrue(HopperAnimation.verb(MechaModule.DRILL) == HopperAnimation.Verb.SPIN, "drill");
        h.assertTrue(HopperAnimation.verb(MechaModule.CHAINSAW) == HopperAnimation.Verb.SPIN, "chainsaw");
        h.assertTrue(HopperAnimation.verb(MechaModule.RIVET_GUN) == HopperAnimation.Verb.RECOIL, "rivet gun");
        h.assertTrue(HopperAnimation.verb(MechaModule.ARC_CANNON) == HopperAnimation.Verb.RECOIL, "arc cannon");
        h.assertTrue(HopperAnimation.verb(MechaModule.PLASMA_LANCE) == HopperAnimation.Verb.SUSTAIN, "lance");
        h.assertTrue(HopperAnimation.verb(MechaModule.FLAMETHROWER) == HopperAnimation.Verb.SUSTAIN, "flamethrower");
        h.assertTrue(HopperAnimation.verb(MechaModule.TRACTOR_BEAM) == HopperAnimation.Verb.SUSTAIN, "tractor");
        h.assertTrue(HopperAnimation.verb(MechaModule.HYDRAULIC_FIST) == HopperAnimation.Verb.STRIKE, "fist");
        h.assertTrue(HopperAnimation.verb(MechaModule.HEMO_SYRINGE) == HopperAnimation.Verb.STRIKE, "syringe");
        h.assertTrue(HopperAnimation.verb(MechaModule.RIOT_SHIELD) == HopperAnimation.Verb.GUARD, "shield");
        h.assertTrue(HopperAnimation.verb(MechaModule.MANIPULATOR_HAND) == HopperAnimation.Verb.GUARD, "hand");
        ServerPlayer p = player(h, FLOOR);
        ServerPlayer hitter = player(h, FLOOR.east(3).south(2));
        MechaEntity m = mecha(h, MechaFrame.HOPPER, FLOOR.east(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS, MechaModule.PLASMA_LANCE,
                MechaModule.RIOT_SHIELD);
        board(h, p, m);
        HopperView v = new HopperView();
        h.runAfterDelay(2, () -> {
            p.setXRot(0);
            Network.arms(p, 3);
        });
        h.runAfterDelay(4, () -> {
            h.assertValueEqual(clip(HopperAnimation.arm(m, v, 0, now(h))), "sustain_l", "the held lance's arm clip");
            h.assertValueEqual(clip(HopperAnimation.arm(m, v, 1, now(h))), "guard_r", "the raised shield's arm clip");
        });
        h.runAfterDelay(20, () -> {
            h.assertValueEqual(clip(HopperAnimation.arm(m, v, 1, now(h))), "guard_loop_r", "the shield held up");
            hitter.lookAt(EntityAnchorArgument.Anchor.EYES, m.getBoundingBox().getCenter());
            hitter.attack(m);
            h.assertTrue(m.hurtTime > 0, "the blow did not land on the machine");
            h.assertValueEqual(clip(HopperAnimation.arm(m, v, 1, now(h))), "guard_hit_r", "the guard did not jolt with the blow");
            Network.arms(p, 0);
            done(h, p, hitter);
        });
    }

    /** Below a quarter of its health a machine walks with its limp (a leg dragged); above it, its own walk. */
    @GameTest(template = "platform", timeoutTicks = 30)
    public static void aBadlyDamagedMachineLimps(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = mecha(h, MechaFrame.HOPPER, FLOOR.east(3), p.getUUID(), MechaModule.HYDRAULIC_LEGS);
        board(h, p, m);
        h.runAfterDelay(2, () -> {
            HopperView v = new HopperView();
            v.speed = 0.25;
            double t = now(h) + 200;
            h.assertValueEqual(clip(HopperAnimation.main(m, v, t)), "walk", "a sound machine's walk");
            m.setHealth(m.getMaxHealth() * 0.2f);
            // never struck from either side: the left leg drags (the side the clips are authored for)
            h.assertValueEqual(clip(HopperAnimation.main(m, v, t)), "limp_l", "a machine at a fifth of its health");
            done(h, p);
        });
    }
}
