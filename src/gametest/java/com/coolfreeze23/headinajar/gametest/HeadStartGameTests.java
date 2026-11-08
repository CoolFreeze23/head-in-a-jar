package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.SeparationChairBlockEntity;
import com.coolfreeze23.headinajar.entity.HeadJarEntity;
import com.coolfreeze23.headinajar.entity.HeadlessBody;
import com.coolfreeze23.headinajar.entity.SeatEntity;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.FormEvents;
import com.coolfreeze23.headinajar.form.HeadRegistry;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.form.NeuroLink;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Phase 1, Head Start: the Separation Chair, the neuro-link, swapping, and what happens when bodies die. */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class HeadStartGameTests {
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

    /** Separates the player the way the chair does, with the head placed at {@code headAt}. */
    private static void separate(GameTestHelper h, ServerPlayer p, BlockPos headAt) {
        boolean ok = FormActions.separate(p, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()), new ItemStack(ModRegistry.RELAY_GOGGLES.get()),
                new ItemStack(ModRegistry.COLLAR_CAM.get()), h.absoluteVec(Vec3.atBottomCenterOf(headAt)), null);
        h.assertTrue(ok, "separate failed");
    }

    private static SeparationChairBlockEntity chair(GameTestHelper h, BlockPos pos, int energy) {
        BlockState state = ModRegistry.SEPARATION_CHAIR.get().defaultBlockState();
        h.setBlock(pos, state);
        SeparationChairBlockEntity chair = (SeparationChairBlockEntity) h.getBlockEntity(pos);
        chair.items().setStackInSlot(0, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()));
        chair.items().setStackInSlot(1, new ItemStack(ModRegistry.RELAY_GOGGLES.get()));
        chair.items().setStackInSlot(2, new ItemStack(ModRegistry.COLLAR_CAM.get()));
        chair.items().setStackInSlot(3, new ItemStack(ModRegistry.SALINE_BOTTLE.get(), 4));
        chair.energy().set(energy);
        return chair;
    }

    private static void sit(GameTestHelper h, ServerPlayer p, BlockPos chairPos) {
        SeatEntity seat = SeatEntity.at(h.getLevel(), h.absolutePos(chairPos), Direction.NORTH);
        h.assertTrue(p.startRiding(seat, true), "could not sit");
    }

    // --- the chair -------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void boothRefusesWithoutFullBuffer(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        SeparationChairBlockEntity chair = chair(h, FLOOR, 30_000);
        sit(h, p, FLOOR);
        h.assertFalse(chair.sign(p), "started with 30 kFE");
        h.assertTrue(chair.missing().contains("power"), "power not listed as missing");
        chair.energy().set(50_000);
        h.assertTrue(chair.sign(p), "refused with a full buffer");
        p.stopRiding();
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void onlySeatedPlayerCanSign(GameTestHelper h) {
        ServerPlayer seated = player(h, FLOOR);
        ServerPlayer other = player(h, FLOOR.east(2));
        SeparationChairBlockEntity chair = chair(h, FLOOR, 50_000);
        h.assertFalse(chair.sign(other), "an empty chair started");
        sit(h, seated, FLOOR);
        h.assertFalse(chair.sign(other), "someone else signed for the seated player");
        h.assertTrue(chair.sign(seated), "the seated player could not sign");
        seated.stopRiding();
        done(h, seated, other);
    }

    @GameTest(template = "platform", timeoutTicks = 120)
    public static void boothAbortsBeforeCut(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        SeparationChairBlockEntity chair = chair(h, FLOOR, 50_000);
        sit(h, p, FLOOR);
        chair.sign(p);
        h.runAfterDelay(50, () -> {
            p.stopRiding();
            h.assertTrue(p.getVehicle() == null, "restraints held before the cut");
        });
        h.runAfterDelay(55, () -> {
            h.assertFalse(chair.running(), "op still running after the patient left");
            h.assertTrue(chair.energy().getEnergyStored() == 40_000, "energy " + chair.energy().getEnergyStored());
            h.assertTrue(!chair.items().getStackInSlot(SeparationChairBlockEntity.SLOT_GOGGLES).isEmpty(), "parts spent on an abort");
            h.assertTrue(state(p).form() == Form.BODY, "patient lost their head anyway");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 300)
    public static void boothFinishesAfterCut(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        SeparationChairBlockEntity chair = chair(h, FLOOR, 50_000);
        sit(h, p, FLOOR);
        chair.sign(p);
        h.runAfterDelay(110, () -> {
            p.stopRiding();
            h.assertTrue(p.getVehicle() instanceof SeatEntity, "restraints let go after the cut");
            h.assertTrue(chair.items().getStackInSlot(SeparationChairBlockEntity.SLOT_JAR).isEmpty(), "jar not consumed at the cut");
        });
        h.runAfterDelay(250, () -> {
            h.assertTrue(state(p).form() == Form.LINKED, "form " + state(p).form());
            h.assertTrue(p.getVehicle() == null, "still seated");
            h.assertTrue(chair.energy().getEnergyStored() == 10_000, "energy " + chair.energy().getEnergyStored());
            h.assertTrue(chair.wear() == 5, "wear " + chair.wear());
            h.assertTrue(!h.getEntities(ModRegistry.HEAD_JAR.get()).isEmpty(), "no head on the outlet");
            h.assertTrue(p.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && p.getInventory().contains(new ItemStack(Items.IRON_HELMET)),
                    "the helmet should come off a headless body");
            done(h, p);
        });
    }

    // --- the link --------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void linkedPlayerHasCollarEyeHeight(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        separate(h, p, FLOOR.east(2));
        h.assertTrue(Math.abs(p.getBbHeight() - FormEvents.LINKED_SIZE.height()) < 1e-4, "height " + p.getBbHeight());
        h.assertTrue(Math.abs(p.getEyeHeight() - 1.32f) < 1e-4, "eye " + p.getEyeHeight());
        h.assertFalse(FormEvents.lockedOut(p), "a linked body is locked out");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void qualityFallsWithDistance(GameTestHelper h) {
        h.assertTrue(NeuroLink.fromDistance(10, 48) == 100, "near");
        h.assertTrue(NeuroLink.fromDistance(36, 48) == 50, "three quarters " + NeuroLink.fromDistance(36, 48));
        h.assertTrue(NeuroLink.fromDistance(48, 48) == 0, "edge");
        ServerPlayer p = player(h, FLOOR);
        separate(h, p, FLOOR);
        Vec3 here = p.position();
        int near = NeuroLink.quality(p, state(p), new HeadRegistry.Spot(h.getLevel().dimension(), here.add(5, 0, 0)));
        int far = NeuroLink.quality(p, state(p), new HeadRegistry.Spot(h.getLevel().dimension(), here.add(40, 0, 0)));
        int otherWorld = NeuroLink.quality(p, state(p), new HeadRegistry.Spot(Level.NETHER, here));
        h.assertTrue(near > far && far > 0, "near " + near + " far " + far);
        h.assertTrue(otherWorld == 0, "other dimension " + otherWorld);
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void carriedHeadIsPerfectLink(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        p.getInventory().add(new ItemStack(ModRegistry.HEAD_SATCHEL.get()));
        separate(h, p, FLOOR.east(1));
        HeadJarEntity head = h.getEntities(ModRegistry.HEAD_JAR.get()).get(0);
        h.assertTrue(FormActions.pickUpHead(p, head), "could not pick up own head");
        h.assertTrue(head.isRemoved() && state(p).carryingHead(), "head not carried");
        int q = NeuroLink.quality(p, state(p), new HeadRegistry.Spot(Level.NETHER, Vec3.ZERO));
        h.assertTrue(q == 100, "carried quality " + q);
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void linkBreaksAndSwapsHome(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        separate(h, p, FLOOR.east(2));
        // pretend the head is far beyond the goggles' range
        Vec3 far = p.position().add(0, 0, 90);
        HeadRegistry.get(p).setHead(p.getUUID(), new HeadRegistry.Head(HeadRegistry.Where.ENTITY, new HeadRegistry.Spot(h.getLevel().dimension(), far),
                java.util.UUID.randomUUID()));
        for (int i = 0; i < 11; i++) NeuroLink.tick(p, state(p));
        h.assertTrue(state(p).form() == Form.JAR, "form " + state(p).form());
        h.assertTrue(p.position().distanceTo(far) < 1, "not moved into the head");
        HeadlessBody body = (HeadlessBody) h.getLevel().getEntity(state(p).body());
        h.assertTrue(body != null && body.hasCollar() && !body.isCorpse(), "no standing body");
        h.assertTrue(body.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "body lost the chestplate");
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void swapRoundTrip(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        separate(h, p, FLOOR.east(3));
        Vec3 bodyAt = p.position();
        h.assertTrue(FormActions.linkedToJar(p), "linked to jar failed");
        h.assertTrue(state(p).form() == Form.JAR && p.position().distanceTo(bodyAt) > 2, "not in the head");
        h.assertTrue(FormActions.jarToLinked(p), "jar to linked failed");
        h.assertTrue(state(p).form() == Form.LINKED && p.position().distanceTo(bodyAt) < 0.1, "not back in the body");
        h.assertTrue(p.getItemBySlot(EquipmentSlot.LEGS).is(Items.IRON_LEGGINGS), "leggings not back on");
        h.assertTrue(h.getEntities(ModRegistry.HEADLESS_BODY.get()).stream().noneMatch(b -> !b.isRemoved()), "body left behind");
        HeadRegistry.Head head = HeadRegistry.get(p).head(p.getUUID());
        h.assertTrue(head != null && head.where() == HeadRegistry.Where.ENTITY, "head not set down");
        done(h, p);
    }

    // --- dying ------------------------------------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void bodyDeathMakesCorpseNotDeath(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        p.getInventory().add(new ItemStack(Items.DIAMOND, 3));
        separate(h, p, FLOOR.east(3));
        Vec3 bodyAt = p.position();
        p.hurt(p.damageSources().generic(), 1000);
        h.assertTrue(p.isAlive() && p.getHealth() == p.getMaxHealth(), "the player died with their body");
        h.assertTrue(state(p).form() == Form.JAR, "form " + state(p).form());
        h.assertFalse(p.getInventory().contains(new ItemStack(Items.DIAMOND)), "diamonds stayed with the jar");
        HeadlessBody corpse = (HeadlessBody) h.getLevel().getEntity(state(p).body());
        h.assertTrue(corpse != null && corpse.isCorpse() && corpse.position().distanceTo(bodyAt) < 0.1, "no corpse");
        h.assertTrue(corpse.belongings().countItem(Items.DIAMOND) == 3, "corpse lacks the diamonds");
        h.assertFalse(FormActions.jarToLinked(p), "linked into a corpse");
        // the Suture Kit revives it
        h.assertTrue(FormActions.suture(p, corpse), "suture on the corpse failed");
        h.assertTrue(state(p).form() == Form.BODY && p.getInventory().contains(new ItemStack(Items.DIAMOND)), "not revived with belongings");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void jarDeathIsRealDeath(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        FormActions.detach(p, 3);
        p.hurt(p.damageSources().generic(), 1000);
        h.assertTrue(p.isDeadOrDying(), "a jar survived lethal damage");
        h.assertTrue(HeadRegistry.get(p).head(p.getUUID()) == null, "registry kept a dead head");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void destroyedHeadKillsLinkedPlayer(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        separate(h, p, FLOOR.east(2));
        HeadJarEntity head = h.getEntities(ModRegistry.HEAD_JAR.get()).get(0);
        head.hurt(h.getLevel().damageSources().generic(), 100);
        h.assertTrue(head.isRemoved(), "head survived");
        h.assertTrue(p.isDeadOrDying(), "player survived losing their head");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void portalBlockedWhileLinked(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        p.getInventory().add(new ItemStack(ModRegistry.HEAD_SATCHEL.get()));
        separate(h, p, FLOOR.east(1));
        h.assertTrue(NeoForge.EVENT_BUS.post(new EntityTravelToDimensionEvent(p, Level.NETHER)).isCanceled(), "portal not blocked");
        FormActions.pickUpHead(p, h.getEntities(ModRegistry.HEAD_JAR.get()).get(0));
        h.assertFalse(NeoForge.EVENT_BUS.post(new EntityTravelToDimensionEvent(p, Level.NETHER)).isCanceled(), "blocked with the head carried");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void sutureWhileCarryingHead(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        p.getInventory().add(new ItemStack(ModRegistry.HEAD_SATCHEL.get()));
        separate(h, p, FLOOR.east(1));
        FormActions.pickUpHead(p, h.getEntities(ModRegistry.HEAD_JAR.get()).get(0));
        h.assertTrue(FormActions.suture(p, null), "suture failed");
        h.assertTrue(state(p).form() == Form.BODY && Math.abs(p.getBbHeight() - 1.8f) < 1e-4, "not whole again");
        ItemStack goggles = p.getInventory().items.stream().filter(s -> s.is(ModRegistry.RELAY_GOGGLES.get())).findFirst().orElse(ItemStack.EMPTY);
        h.assertTrue(!goggles.isEmpty() && goggles.getDamageValue() > 0, "goggles not returned worn");
        h.assertTrue(HeadRegistry.get(p).head(p.getUUID()) == null, "registry still has a head");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void cradleDocksAndProtectsHead(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        BlockPos cradlePos = FLOOR.east(2);
        h.setBlock(cradlePos, ModRegistry.CRADLE.get());
        var cradle = (com.coolfreeze23.headinajar.block.LifeSupportCradleBlockEntity) h.getBlockEntity(cradlePos);
        FormActions.separate(p, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()), new ItemStack(ModRegistry.RELAY_GOGGLES.get()),
                new ItemStack(ModRegistry.COLLAR_CAM.get()), p.position(), cradle);
        h.assertTrue(p.getUUID().equals(cradle.owner()), "head not docked");
        h.assertTrue(HeadRegistry.get(p).head(p.getUUID()).where() == HeadRegistry.Where.CRADLE, "registry not CRADLE");
        h.assertTrue(h.getEntities(ModRegistry.HEAD_JAR.get()).isEmpty(), "a loose head was spawned too");
        // breaking the Cradle sets the head down instead of losing it
        h.destroyBlock(cradlePos);
        h.assertTrue(!h.getEntities(ModRegistry.HEAD_JAR.get()).isEmpty(), "head lost with the Cradle");
        done(h, p);
    }
}
