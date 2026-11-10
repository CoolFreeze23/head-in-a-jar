package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.ChairController;
import com.coolfreeze23.headinajar.block.ChairTimeline;
import com.coolfreeze23.headinajar.block.SeparationChairBlock;
import com.coolfreeze23.headinajar.block.SeparationChairBlockEntity;
import com.coolfreeze23.headinajar.entity.HeadJarEntity;
import com.coolfreeze23.headinajar.entity.SeatEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.menu.SeparationChairMenu;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * The Separation Chair as a machine: its states and clock as a late observer reads them, the locked slots, the jar
 * left exactly where the chair draws it, the room it needs, and what it does when broken, abandoned or clicked.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class ChairGameTests {
    private static final BlockPos CHAIR = new BlockPos(7, 2, 7);

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

    private static SeparationChairBlockEntity chair(GameTestHelper h, Direction facing) {
        h.setBlock(CHAIR, ModRegistry.SEPARATION_CHAIR.get().defaultBlockState().setValue(SeparationChairBlock.FACING, facing));
        SeparationChairBlockEntity chair = (SeparationChairBlockEntity) h.getBlockEntity(CHAIR);
        chair.items().setStackInSlot(0, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()));
        chair.items().setStackInSlot(1, new ItemStack(ModRegistry.RELAY_GOGGLES.get()));
        chair.items().setStackInSlot(2, new ItemStack(ModRegistry.COLLAR_CAM.get()));
        chair.items().setStackInSlot(3, new ItemStack(ModRegistry.SALINE_BOTTLE.get(), 4));
        chair.energy().set(50_000);
        return chair;
    }

    /** Sits down the way a right-click with an empty hand does (the seat the block's use handler makes). */
    private static SeatEntity sit(GameTestHelper h, ServerPlayer p, Direction facing) {
        SeatEntity seat = SeatEntity.at(h.getLevel(), h.absolutePos(CHAIR), facing);
        h.assertTrue(p.startRiding(seat, true), "could not sit");
        return seat;
    }

    /** The chair's console as the player has it open; its Sign button is what the button packet calls. */
    private static SeparationChairMenu menu(SeparationChairBlockEntity chair, ServerPlayer p) {
        return (SeparationChairMenu) chair.createMenu(1, p.getInventory(), p);
    }

    private static boolean pressSign(SeparationChairBlockEntity chair, ServerPlayer p) {
        return menu(chair, p).clickMenuButton(p, SeparationChairMenu.BUTTON_SIGN);
    }

    /** A right-click on the chair through the game's own use path. */
    private static void use(GameTestHelper h, ServerPlayer p) {
        BlockPos abs = h.absolutePos(CHAIR);
        p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false));
    }

    /** What a client that has only ever seen this update tag would compute. */
    private static SeparationChairBlockEntity observer(GameTestHelper h, SeparationChairBlockEntity chair) {
        SeparationChairBlockEntity copy = new SeparationChairBlockEntity(chair.getBlockPos(), chair.getBlockState());
        CompoundTag tag = chair.getUpdateTag(h.getLevel().registryAccess());
        copy.handleUpdateTag(tag, h.getLevel().registryAccess());
        return copy;
    }

    @GameTest(template = "platform", timeoutTicks = 320)
    public static void lateObserverReadsTheSameClockAndClip(GameTestHelper h) {
        ServerPlayer p = player(h, CHAIR);
        SeparationChairBlockEntity chair = chair(h, Direction.NORTH);
        sit(h, p, Direction.NORTH);
        // seated, jar loaded, unsigned: the clerk waits
        SeparationChairBlockEntity before = observer(h, chair);
        h.assertValueEqual(ChairController.choose(before, h.getLevel().getGameTime(), true).clip(), ChairController.Clip.READY, "ready clip");
        h.assertValueEqual(ChairController.choose(before, h.getLevel().getGameTime(), false).clip(), ChairController.Clip.IDLE, "idle clip");
        h.assertTrue(pressSign(chair, p), "the Sign button did not start it");
        long started = h.getLevel().getGameTime();
        h.runAfterDelay(50, () -> {
            // a player arriving now gets one update tag and must land on the server's tick
            SeparationChairBlockEntity late = observer(h, chair);
            long now = h.getLevel().getGameTime();
            float t = late.elapsed(now, 0.5f);
            h.assertTrue(Math.abs(t - (chair.opTick() + 0.5f)) <= 1.01f, "late observer at " + t + ", server at " + chair.opTick());
            h.assertTrue(Math.abs(late.opStart() - started) <= 1, "start " + late.opStart() + " vs " + started);
            ChairController.Choice c = ChairController.choose(late, now + 0.5, true);
            h.assertValueEqual(c.clip(), ChairController.Clip.OPERATION, "operation clip");
            h.assertTrue(Math.abs(c.tick() - t) < 1e-3, "clip tick " + c.tick() + " clock " + t);
        });
        h.runAfterDelay(150, () -> {
            SeparationChairBlockEntity late = observer(h, chair);
            ChairController.Choice c = ChairController.choose(late, h.getLevel().getGameTime(), true);
            h.assertValueEqual(c.clip(), late.botched() ? ChairController.Clip.BOTCH : ChairController.Clip.OPERATION, "clip at 150");
            h.assertTrue(c.tick() > 140 && c.tick() < 160, "late join at 150 lands at " + c.tick());
            h.assertTrue(late.jarKind() == SeparationChairBlockEntity.JAR_WIDE, "the jar in use is still drawn after the cut");
        });
        h.runAfterDelay(250, () -> {
            h.assertFalse(chair.running(), "still running");
            SeparationChairBlockEntity late = observer(h, chair);
            long now = h.getLevel().getGameTime();
            h.assertValueEqual(ChairController.choose(late, now, false).clip(), ChairController.Clip.AFTERCARE, "aftercare");
            h.assertValueEqual(ChairController.choose(late, now + ChairTimeline.AFTERCARE_LENGTH, false).clip(), ChairController.Clip.IDLE, "back to idle");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void abortIsAStateTheObserverSees(GameTestHelper h) {
        ServerPlayer p = player(h, CHAIR);
        SeparationChairBlockEntity chair = chair(h, Direction.NORTH);
        sit(h, p, Direction.NORTH);
        h.assertTrue(pressSign(chair, p), "not started");
        h.runAfterDelay(40, () -> p.stopRiding());
        h.runAfterDelay(45, () -> {
            h.assertFalse(chair.running(), "still running after the patient got up");
            SeparationChairBlockEntity late = observer(h, chair);
            long now = h.getLevel().getGameTime();
            ChairController.Choice c = ChairController.choose(late, now, false);
            h.assertValueEqual(c.clip(), ChairController.Clip.ABORT, "abort clip");
            h.assertValueEqual(ChairController.choose(late, now + ChairTimeline.ABORT_BLEND + ChairTimeline.ABORT_LENGTH, false).clip(),
                    ChairController.Clip.IDLE, "idle after the abort");
            h.assertTrue(!chair.items().getStackInSlot(SeparationChairBlockEntity.SLOT_JAR).isEmpty(), "jar kept on an abort");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 80)
    public static void slotsLockFromTheSignature(GameTestHelper h) {
        ServerPlayer p = player(h, CHAIR);
        ServerPlayer other = player(h, CHAIR.east(2));
        SeparationChairBlockEntity chair = chair(h, Direction.NORTH);
        sit(h, p, Direction.NORTH);
        h.assertTrue(pressSign(chair, p), "not started");
        h.runAfterDelay(20, () -> {
            SeparationChairMenu menu = menu(chair, other);
            // a click on the jar slot (what the container click packet runs), and a shift-click
            menu.clicked(SeparationChairBlockEntity.SLOT_JAR, 0, ClickType.PICKUP, other);
            h.assertTrue(menu.getCarried().isEmpty(), "the jar came out on a click: " + menu.getCarried());
            menu.quickMoveStack(other, SeparationChairBlockEntity.SLOT_GOGGLES);
            menu.clicked(SeparationChairBlockEntity.SLOT_COLLAR, 0, ClickType.QUICK_MOVE, other);
            h.assertTrue(!chair.items().getStackInSlot(SeparationChairBlockEntity.SLOT_JAR).isEmpty(), "jar taken");
            h.assertTrue(!chair.items().getStackInSlot(SeparationChairBlockEntity.SLOT_GOGGLES).isEmpty(), "goggles taken");
            h.assertTrue(!chair.items().getStackInSlot(SeparationChairBlockEntity.SLOT_COLLAR).isEmpty(), "collar taken");
            // and what a hopper or pipe sees through the capability
            h.assertTrue(chair.items().extractItem(SeparationChairBlockEntity.SLOT_JAR, 1, false).isEmpty(), "extracted by a pipe");
            ItemStack extra = new ItemStack(ModRegistry.SALINE_BOTTLE.get(), 4);
            h.assertTrue(chair.items().insertItem(SeparationChairBlockEntity.SLOT_SALINE, extra, false).getCount() == 4, "inserted by a pipe");
        });
        h.runAfterDelay(30, () -> {
            p.stopRiding();
        });
        h.runAfterDelay(35, () -> {
            // after an abort before the cut the slots open again
            SeparationChairMenu menu = menu(chair, other);
            menu.clicked(SeparationChairBlockEntity.SLOT_JAR, 0, ClickType.PICKUP, other);
            h.assertTrue(menu.getCarried().is(ModRegistry.LIFE_SUPPORT_JAR.get()), "the jar is stuck after the abort");
            done(h, p, other);
        });
    }

    private static JsonObject resource(String path) throws java.io.IOException {
        try (var in = SeparationChairBlockEntity.class.getResourceAsStream(path)) {
            if (in == null) throw new java.io.IOException("missing " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    /**
     * Where the shipped model puts the jar with its tray out, in the model's own frame (x as Blockbench shows it): the
     * jar_anchor bone's pivot plus the tray_out clip's last position. Both files store x mirrored.
     */
    private static Vec3 modelOutlet() {
        try {
            JsonObject geo = resource("/assets/headinajar/geo/block/separation_chair.geo.json");
            Vec3 pivot = null;
            for (var b : geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")) {
                JsonObject bone = b.getAsJsonObject();
                if (bone.get("name").getAsString().equals("jar_anchor")) {
                    var p = bone.getAsJsonArray("pivot");
                    pivot = new Vec3(-p.get(0).getAsDouble(), p.get(1).getAsDouble(), p.get(2).getAsDouble());
                }
            }
            JsonObject anim = resource("/assets/headinajar/animations/block/separation_chair.animation.json");
            JsonObject keys = anim.getAsJsonObject("animations").getAsJsonObject("tray_out").getAsJsonObject("bones")
                    .getAsJsonObject("jar_tray").getAsJsonObject("position");
            String last = keys.keySet().stream().max(java.util.Comparator.comparingDouble(Double::parseDouble)).orElseThrow();
            var v = keys.getAsJsonObject(last).getAsJsonArray("vector");
            return pivot.add(-v.get(0).getAsDouble(), v.get(1).getAsDouble(), v.get(2).getAsDouble());
        } catch (Exception e) {
            return null;
        }
    }

    @GameTest(template = "platform", timeoutTicks = 300)
    public static void realJarStandsWhereTheChairDrawsIt(GameTestHelper h) {
        Vec3 anchor = modelOutlet();
        h.assertTrue(anchor != null, "no jar_anchor or tray_out in the shipped model");
        h.assertTrue(anchor.distanceTo(SeparationChairBlockEntity.JAR_ANCHOR) < 1e-4, "code outlet " + SeparationChairBlockEntity.JAR_ANCHOR + " vs model " + anchor);
        ServerPlayer p = player(h, CHAIR);
        SeparationChairBlockEntity chair = chair(h, Direction.EAST);
        sit(h, p, Direction.EAST);
        h.assertTrue(pressSign(chair, p), "not started");
        h.runAfterDelay(250, () -> {
            h.assertTrue(FormActions.state(p).form() == Form.LINKED, "form " + FormActions.state(p).form());
            var jars = h.getEntities(ModRegistry.HEAD_JAR.get());
            h.assertTrue(jars.size() == 1, "jars " + jars.size());
            HeadJarEntity jar = jars.get(0);
            Vec3 expected = SeparationChairBlockEntity.outlet(h.absolutePos(CHAIR), Direction.EAST);
            h.assertTrue(jar.position().distanceTo(expected) < 1e-3, "jar at " + jar.position() + ", chair draws it at " + expected);
            // east-facing: the patient's left is north; the tray puts the jar 1.125 blocks that way, 1 px up
            Vec3 c = Vec3.atBottomCenterOf(h.absolutePos(CHAIR));
            Vec3 d = expected.subtract(c);
            h.assertTrue(Math.abs(d.x) < 1e-6 && Math.abs(d.z + 1.125) < 1e-6 && Math.abs(d.y - 1 / 16.0) < 1e-6, "outlet " + d);
            h.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(jar.getYRot() - Direction.EAST.toYRot())) < 0.01f, "jar yaw " + jar.getYRot());
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void blockedWorkSpaceRefusesWithAReason(GameTestHelper h) {
        ServerPlayer p = player(h, CHAIR);
        SeparationChairBlockEntity chair = chair(h, Direction.NORTH);
        sit(h, p, Direction.NORTH);
        // facing north the patient's left is west: the front-left block, one up (where the hood swings), holds a stone
        h.setBlock(CHAIR.north().west().above(), Blocks.STONE);
        h.assertFalse(pressSign(chair, p), "started with the hood's space blocked");
        h.assertValueEqual(Refusals.lastDelivered(p), "chair.blocked", "refusal");
        h.assertFalse(chair.running(), "running");
        h.setBlock(CHAIR.north().west().above(), Blocks.AIR);
        h.assertTrue(pressSign(chair, p), "refused with the space clear");
        p.stopRiding();
        done(h, p);
    }

    private static int dropped(GameTestHelper h, Item item) {
        int n = 0;
        AABB box = new AABB(h.absolutePos(CHAIR)).inflate(3);
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, box)) if (e.getItem().is(item)) n += e.getItem().getCount();
        return n;
    }

    @GameTest(template = "platform", timeoutTicks = 160)
    public static void chairBrokenAfterTheCutLosesNothing(GameTestHelper h) {
        ServerPlayer p = player(h, CHAIR);
        SeparationChairBlockEntity chair = chair(h, Direction.NORTH);
        sit(h, p, Direction.NORTH);
        h.assertTrue(pressSign(chair, p), "not started");
        h.runAfterDelay(110, () -> {
            h.assertTrue(chair.items().getStackInSlot(SeparationChairBlockEntity.SLOT_JAR).isEmpty(), "jar not taken at the cut");
            // what an explosion or a creative player does: the game's own destroy path
            h.getLevel().destroyBlock(h.absolutePos(CHAIR), true);
        });
        h.runAfterDelay(115, () -> {
            h.assertTrue(dropped(h, ModRegistry.LIFE_SUPPORT_JAR.get()) == 1, "jar lost");
            h.assertTrue(dropped(h, ModRegistry.RELAY_GOGGLES.get()) == 1, "goggles lost");
            h.assertTrue(dropped(h, ModRegistry.COLLAR_CAM.get()) == 1, "collar lost");
            h.assertTrue(FormActions.state(p).form() == Form.BODY, "patient half separated: " + FormActions.state(p).form());
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 400)
    public static void vanishedPatientEndsTheRun(GameTestHelper h) {
        ServerPlayer p = player(h, CHAIR);
        SeparationChairBlockEntity chair = chair(h, Direction.NORTH);
        SeatEntity seat = sit(h, p, Direction.NORTH);
        h.assertTrue(pressSign(chair, p), "not started");
        h.runAfterDelay(105, () -> seat.discard());
        h.runAfterDelay(110, () -> h.assertTrue(chair.running(), "gave up at once"));
        h.runAfterDelay(110 + ChairTimeline.PATIENT_GRACE, () -> {
            h.assertFalse(chair.running(), "still running with nobody in it");
            h.assertTrue(!chair.items().getStackInSlot(SeparationChairBlockEntity.SLOT_JAR).isEmpty(), "jar not given back");
            h.assertTrue(!chair.items().getStackInSlot(SeparationChairBlockEntity.SLOT_COLLAR).isEmpty(), "collar not given back");
            ServerPlayer next = player(h, CHAIR);
            sit(h, next, Direction.NORTH);
            chair.items().setStackInSlot(SeparationChairBlockEntity.SLOT_SALINE, new ItemStack(ModRegistry.SALINE_BOTTLE.get(), 4));
            chair.energy().set(50_000);
            h.assertTrue(pressSign(chair, next), "the chair can't be used again");
            next.stopRiding();
            done(h, p, next);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void bystanderClickingAChairInUseIsAnswered(GameTestHelper h) {
        ServerPlayer p = player(h, CHAIR);
        ServerPlayer other = player(h, CHAIR.east(2));
        SeparationChairBlockEntity chair = chair(h, Direction.NORTH);
        sit(h, p, Direction.NORTH);
        h.assertTrue(pressSign(chair, p), "not started");
        h.runAfterDelay(5, () -> {
            use(h, other);
            h.assertValueEqual(Refusals.lastDelivered(other), "chair.in_use", "plain click");
            h.assertTrue(other.containerMenu == other.inventoryMenu, "the console opened mid-operation");
            other.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.REPLACEMENT_BLADE.get()));
            use(h, other);
            h.assertValueEqual(Refusals.lastDelivered(other), "chair.blade_busy", "blade click");
            h.assertTrue(other.getMainHandItem().getCount() == 1, "blade used up mid-operation");
            p.stopRiding();
            done(h, p, other);
        });
    }

    @GameTest(template = "platform")
    public static void collisionFollowsTheFacing(GameTestHelper h) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState state = ModRegistry.SEPARATION_CHAIR.get().defaultBlockState().setValue(SeparationChairBlock.FACING, facing);
            h.setBlock(CHAIR, state);
            var shape = state.getShape(h.getLevel(), h.absolutePos(CHAIR), CollisionContext.empty());
            // the backrest is the only part above 6 px: it must sit on the side away from the facing
            var top = shape.toAabbs().stream().filter(b -> b.maxY > 0.5).findFirst().orElseThrow();
            Vec3 centre = top.getCenter();
            Vec3 back = new Vec3(0.5 - facing.getStepX() * 0.25, centre.y, 0.5 - facing.getStepZ() * 0.25);
            h.assertTrue(Math.abs(centre.x - back.x) < 0.2 && Math.abs(centre.z - back.z) < 0.2,
                    facing + ": backrest at " + centre + ", expected near " + back);
        }
        h.succeed();
    }

    @GameTest(template = "platform", timeoutTicks = 300)
    public static void botchedHeadStaysUpsideDownInTheRealJar(GameTestHelper h) {
        ServerPlayer p = player(h, CHAIR);
        SeparationChairBlockEntity chair = chair(h, Direction.NORTH);
        sit(h, p, Direction.NORTH);
        h.assertTrue(pressSign(chair, p), "not started");
        h.runAfterDelay(105, () -> {
            // what /data merge does: the botch decided at the point of no return
            CompoundTag tag = chair.saveWithFullMetadata(h.getLevel().registryAccess());
            tag.putBoolean("Botched", true);
            chair.loadWithComponents(tag, h.getLevel().registryAccess());
        });
        h.runAfterDelay(250, () -> {
            var jars = h.getEntities(ModRegistry.HEAD_JAR.get());
            h.assertTrue(jars.size() == 1, "jars " + jars.size());
            HeadJarEntity jar = jars.get(0);
            h.assertTrue(jar.upsideDown(), "the botched head went into the real jar upright");
            h.assertTrue(FormActions.state(p).rollTicks() > 0, "no camera roll");
            // saved and loaded with the jar (an additive key)
            CompoundTag saved = new CompoundTag();
            jar.saveWithoutId(saved);
            h.assertTrue(saved.getInt("UpsideDownTicks") > 0, "not saved");
            // it rights itself when its time is up, as the patient's view does
            jar.setUpsideDown(2);
        });
        h.runAfterDelay(256, () -> {
            h.assertFalse(h.getEntities(ModRegistry.HEAD_JAR.get()).get(0).upsideDown(), "still upside down after its time");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void loadingAJarWithTheTrayBlockedIsRefused(GameTestHelper h) {
        ServerPlayer p = player(h, CHAIR.east(2));
        SeparationChairBlockEntity chair = chair(h, Direction.NORTH);
        chair.items().setStackInSlot(SeparationChairBlockEntity.SLOT_JAR, ItemStack.EMPTY);
        // facing north the tray comes out to the west
        h.setBlock(CHAIR.west(), Blocks.STONE);
        SeparationChairMenu menu = menu(chair, p);
        menu.setCarried(new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()));
        menu.clicked(SeparationChairBlockEntity.SLOT_JAR, 0, ClickType.PICKUP, p);
        h.assertTrue(chair.items().getStackInSlot(SeparationChairBlockEntity.SLOT_JAR).isEmpty(), "loaded into a blocked tray");
        h.assertValueEqual(Refusals.lastDelivered(p), "chair.tray_blocked", "click refusal");
        h.assertTrue(chair.items().insertItem(SeparationChairBlockEntity.SLOT_JAR, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()), false).getCount() == 1,
                "a pipe loaded a blocked tray");
        h.setBlock(CHAIR.west(), Blocks.AIR);
        menu.clicked(SeparationChairBlockEntity.SLOT_JAR, 0, ClickType.PICKUP, p);
        h.assertTrue(!chair.items().getStackInSlot(SeparationChairBlockEntity.SLOT_JAR).isEmpty(), "can't load with the space clear");
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 40)
    public static void hittingARunningChairIsAnswered(GameTestHelper h) {
        ServerPlayer p = player(h, CHAIR);
        ServerPlayer other = player(h, CHAIR.east(1));
        SeparationChairBlockEntity chair = chair(h, Direction.NORTH);
        sit(h, p, Direction.NORTH);
        h.assertTrue(pressSign(chair, p), "not started");
        h.runAfterDelay(5, () -> {
            BlockPos abs = h.absolutePos(CHAIR);
            // what the game's break handler calls on the first hit of a left click (BlockState.attack)
            h.getLevel().getBlockState(abs).attack(h.getLevel(), abs, other);
            h.assertValueEqual(Refusals.lastDelivered(other), "chair.no_break", "a hit on a running chair");
            h.assertTrue(h.getLevel().getBlockState(abs).is(ModRegistry.SEPARATION_CHAIR.get()), "broken mid-operation");
            p.stopRiding();
            done(h, p, other);
        });
    }
}
