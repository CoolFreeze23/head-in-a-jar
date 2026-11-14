package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.entity.HeadJarEntity;
import com.coolfreeze23.headinajar.entity.HeadlessBody;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The 0.5.3 fixes the server can prove: the Nausea of putting the head back on stays with the body when the mind swaps
 * into the jar, and comes back with the mind; when a jar's HUD shows the head's hearts; a Utility Shell set up from its
 * chassis is heard.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class ReleaseFiveThreeGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);

    private static ServerPlayer player(GameTestHelper h, BlockPos at, float yaw, float pitch) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.spawnInvulnerableTime = 0;
        Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(at));
        player.moveTo(v.x, v.y, v.z, yaw, pitch);
        return player;
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    /** The head off by the chair's own server call, then picked up by a click with the satchel in the bag. */
    private static void separateAndCarry(GameTestHelper h, ServerPlayer p) {
        h.assertTrue(FormActions.separate(p, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()), new ItemStack(ModRegistry.RELAY_GOGGLES.get()),
                new ItemStack(ModRegistry.COLLAR_CAM.get()), h.absoluteVec(Vec3.atBottomCenterOf(FLOOR.west(2))), null), "separate failed");
        p.getInventory().setItem(9, new ItemStack(ModRegistry.HEAD_SATCHEL.get()));
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        List<HeadJarEntity> heads = h.getLevel().getEntitiesOfClass(HeadJarEntity.class, p.getBoundingBox().inflate(8));
        h.assertTrue(heads.size() == 1, "expected one head on the floor, found " + heads.size());
        p.interactOn(heads.get(0), InteractionHand.MAIN_HAND);
        h.assertTrue(FormActions.state(p).carryingHead(), "the head was not picked up");
    }

    /**
     * The final 0.5.2 check's order: the head put back on with the Suture Kit (600 ticks of Nausea), taken off again,
     * carried, and the swap key pressed. The jar wakes without the Nausea (its purple motes were drawn at the jar's own
     * eye); the body left standing has it, as long as was left, without motes; the swap back hands it to the player again.
     */
    @GameTest(template = "platform", timeoutTicks = 40)
    public static void nauseaStaysWithTheBodyWhenTheMindSwapsIntoTheJar(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR, 0, 30);
        separateAndCarry(h, p);
        h.assertTrue(FormActions.suture(p, null), "the suture failed");
        MobEffectInstance sutured = p.getEffect(MobEffects.CONFUSION);
        h.assertTrue(sutured != null && sutured.getDuration() == 600, "the suture gave " + sutured);
        separateAndCarry(h, p);
        h.assertTrue(p.hasEffect(MobEffects.CONFUSION), "the Nausea went before the swap");

        Network.action(p, new Network.JarAction(Network.Action.SWAP));
        h.assertTrue(FormActions.state(p).form() == Form.JAR, "the swap did not wake the jar: " + FormActions.state(p).form());
        h.assertTrue(!p.hasEffect(MobEffects.CONFUSION), "the jar woke with the body's Nausea");
        List<HeadlessBody> bodies = h.getLevel().getEntitiesOfClass(HeadlessBody.class, p.getBoundingBox().inflate(8));
        h.assertTrue(bodies.size() == 1, "expected one body left standing, found " + bodies.size());
        MobEffectInstance kept = bodies.get(0).getEffect(MobEffects.CONFUSION);
        h.assertTrue(kept != null && kept.getDuration() == sutured.getDuration(), "the body kept " + kept + ", the player had " + sutured);
        // no motes from the body either: the jar woke inside the body's box, where they would be born at its eye
        h.assertFalse(kept.isVisible(), "the body's Nausea gives off motes");

        Network.action(p, new Network.JarAction(Network.Action.SWAP));
        h.assertTrue(FormActions.state(p).form() == Form.LINKED, "the swap back did not wake the body: " + FormActions.state(p).form());
        h.assertTrue(bodies.get(0).isRemoved(), "the body did not step into the player");
        MobEffectInstance back = p.getEffect(MobEffects.CONFUSION);
        h.assertTrue(back != null && back.getDuration() == kept.getDuration() && back.isVisible(), "the Nausea did not come back with the mind: " + back);
        done(h, p);
    }

    /**
     * The rule the jar's HUD follows for the head's hearts and armour row: hidden while the glass holds above the "deadly"
     * warning and the head is whole, shown once the glass is that low or the head itself is hurt.
     */
    @GameTest(template = "platform", timeoutTicks = 20)
    public static void aJarShowsItsHeartsOnlyOnceTheyMatter(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR, 0, 0);
        h.assertTrue(FormActions.detach(p, 3), "detach failed");
        var state = FormActions.state(p);
        h.assertTrue(state.integrity() == state.maxIntegrity() && p.getHealth() == p.getMaxHealth(), "the jar did not start whole");
        h.assertFalse(com.coolfreeze23.headinajar.form.JarPeril.lifeShown(p, state), "a whole jar with whole glass shows its hearts");
        state.setIntegrity(com.coolfreeze23.headinajar.form.JarPeril.LOW + 1);
        h.assertFalse(com.coolfreeze23.headinajar.form.JarPeril.lifeShown(p, state), "the hearts show before the glass is low");
        state.setIntegrity(com.coolfreeze23.headinajar.form.JarPeril.LOW);
        h.assertTrue(com.coolfreeze23.headinajar.form.JarPeril.lifeShown(p, state), "the glass is low and the hearts are hidden");
        state.setIntegrity(state.maxIntegrity());
        p.setHealth(p.getMaxHealth() - 3);
        h.assertTrue(com.coolfreeze23.headinajar.form.JarPeril.lifeShown(p, state), "the head is hurt and the hearts are hidden");
        done(h, p);
    }

    /** Each of the three chassis, set up by a use on the floor, plays the servos settling where the shell stands. */
    @GameTest(template = "platform", timeoutTicks = 20)
    public static void aChassisSetUpOnTheFloorIsHeard(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR, 0, 60);
        List<ResourceLocation> heard = new ArrayList<>();
        java.util.function.Consumer<PlayLevelSoundEvent.AtPosition> listener = e -> {
            if (e.getSound() != null) heard.add(e.getSound().value().getLocation());
        };
        NeoForge.EVENT_BUS.addListener(PlayLevelSoundEvent.AtPosition.class, listener);
        try {
            var chassis = List.of(ModRegistry.LUNK_CHASSIS.get(), ModRegistry.ROVER_CHASSIS.get(), ModRegistry.SPIDER_WALKER_CHASSIS.get());
            for (int i = 0; i < chassis.size(); i++) {
                BlockPos floor = h.absolutePos(FLOOR.below().south(2).east(3 * i - 3));
                ItemStack stack = new ItemStack(chassis.get(i));
                p.setItemInHand(InteractionHand.MAIN_HAND, stack);
                p.gameMode.useItemOn(p, h.getLevel(), stack, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false));
                h.assertTrue(stack.isEmpty(), chassis.get(i) + " was not used up");
            }
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        long settled = heard.stream().filter(com.coolfreeze23.headinajar.HeadInAJar.id("mecha.servo_settle")::equals).count();
        h.assertTrue(settled == 3, "three chassis set up, the servos settled " + settled + " times: " + heard);
        int shells = h.getLevel().getEntitiesOfClass(com.coolfreeze23.headinajar.entity.ShellEntity.class, p.getBoundingBox().inflate(10)).size();
        h.assertTrue(shells == 3, "expected three shells standing, found " + shells);
        done(h, p);
    }
}
