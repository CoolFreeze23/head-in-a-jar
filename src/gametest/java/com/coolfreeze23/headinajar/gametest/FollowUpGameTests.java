package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.entity.HeadJarEntity;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.item.SurgicalSawItem;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Small repairs made after the earlier ones were tried in the game: a climb onto a host that fails after the head came
 * out of the satchel says so, and a parked mecha with its own generator settles its battery once a second.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class FollowUpGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);
    /** Players whose next mount is refused, as a claim or another mod can refuse one. */
    private static final Set<UUID> NO_MOUNT = new HashSet<>();

    static {
        NeoForge.EVENT_BUS.addListener((EntityMountEvent e) -> {
            if (e.isMounting() && NO_MOUNT.contains(e.getEntityMounting().getUUID())) e.setCanceled(true);
        });
    }

    private static ServerPlayer player(GameTestHelper h, BlockPos at) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.spawnInvulnerableTime = 0;
        Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(at));
        player.moveTo(v.x, v.y, v.z, 0, 0);
        return player;
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) {
            NO_MOUNT.remove(p.getUUID());
            h.getLevel().getServer().getPlayerList().remove(p);
        }
        h.succeed();
    }

    @GameTest(template = "platform")
    public static void aClimbThatFailsAfterTheSwapSaysWhereYouAre(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Pig pig = h.spawn(EntityType.PIG, FLOOR.east(2));
        pig.setHealth(1);
        h.assertTrue(SurgicalSawItem.saw(p, new ItemStack(ModRegistry.SURGICAL_SAW.get()), pig, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME, "saw failed");
        p.getInventory().add(new ItemStack(ModRegistry.HEAD_SATCHEL.get()));
        h.assertTrue(FormActions.separate(p, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()), new ItemStack(ModRegistry.RELAY_GOGGLES.get()),
                new ItemStack(ModRegistry.COLLAR_CAM.get()), h.absoluteVec(Vec3.atBottomCenterOf(FLOOR.west(1))), null), "separate failed");
        HeadJarEntity head = h.getEntities(ModRegistry.HEAD_JAR.get()).get(0);
        h.assertTrue(FormActions.pickUpHead(p, head), "could not pick up own head");
        NO_MOUNT.add(p.getUUID());
        // the click a linked player carrying their head makes on a host
        p.interactOn(pig, InteractionHand.MAIN_HAND);
        h.assertFalse(p.isPassenger(), "climbed on although the mount was refused");
        h.assertValueEqual(FormActions.state(p).form(), Form.JAR, "form after the failed climb");
        h.assertValueEqual(Refusals.lastDelivered(p), "host.mount_failed", "line after the failed climb");
        // the mount allowed again: the same click from the jar climbs on
        NO_MOUNT.remove(p.getUUID());
        p.interactOn(pig, InteractionHand.MAIN_HAND);
        h.assertTrue(p.getVehicle() == pig, "the jar could not climb on afterwards");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void aJarHoldingTheSawClimbsOntoAHost(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Pig pig = h.spawn(EntityType.PIG, FLOOR.east(2));
        pig.setHealth(1);
        ItemStack saw = new ItemStack(ModRegistry.SURGICAL_SAW.get());
        h.assertTrue(SurgicalSawItem.saw(p, saw, pig, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME, "saw failed");
        // the head comes off with the saw still in hand: the jar's click on the host it just made
        p.setItemInHand(InteractionHand.MAIN_HAND, saw);
        h.assertTrue(FormActions.detach(p, 3), "detach failed");
        p.interactOn(pig, InteractionHand.MAIN_HAND);
        h.assertTrue(p.getVehicle() == pig, "a jar holding the saw was refused by the saw instead of climbing on");
        h.assertValueEqual(FormActions.state(p).form(), Form.HOSTED, "form after the climb");
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 80)
    public static void aParkedMechaWithAGeneratorSettlesOnceASecond(GameTestHelper h) {
        MechaEntity m = h.spawn(ModRegistry.mechaType(MechaFrame.HOPPER), FLOOR);
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < MechaFrame.HOPPER.totalSlots(); i++) slots.add(ItemStack.EMPTY);
        slots.set(MechaFrame.HOPPER.firstIndex(MechaModule.HYDRAULIC_LEGS.slot), new ItemStack(ModRegistry.mechaModule(MechaModule.HYDRAULIC_LEGS)));
        slots.set(MechaFrame.HOPPER.firstIndex(MechaModule.GALVANIC_STACK.slot), new ItemStack(ModRegistry.mechaModule(MechaModule.GALVANIC_STACK)));
        m.setModules(slots);
        m.setOwner(UUID.randomUUID());
        m.setBattery(10_000);
        Set<Integer> seen = new HashSet<>();
        for (int t = 1; t <= 40; t++) h.runAfterDelay(t, () -> seen.add(m.battery()));
        h.runAfterDelay(42, () -> {
            h.assertTrue(seen.size() <= 3, "a parked mecha with a Galvanic Stack changed its battery " + seen.size() + " times in 40 ticks");
            int gained = m.battery() - 10_000;
            // 10 FE a tick in, 1 FE a tick out: 180 FE a second
            h.assertTrue(gained >= 180 && gained <= 540, "parked charge changed: " + gained + " FE in 42 ticks");
            h.succeed();
        });
    }
}
