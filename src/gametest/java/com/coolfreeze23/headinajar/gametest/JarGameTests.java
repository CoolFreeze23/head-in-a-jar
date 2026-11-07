package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.entity.HeadlessBody;
import com.coolfreeze23.headinajar.entity.HeldBlockEntity;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.FormEvents;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.menu.JarMenu;
import com.coolfreeze23.headinajar.module.ModuleEffects;
import com.coolfreeze23.headinajar.module.ModuleType;
import com.coolfreeze23.headinajar.module.Telekinesis;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class JarGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);   // standing on the platform floor

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        Vec3 at = h.absoluteVec(Vec3.atBottomCenterOf(FLOOR));
        player.moveTo(at.x, at.y, at.z, 0, 0);
        return player;
    }

    private static void done(GameTestHelper h, ServerPlayer player) {
        h.getLevel().getServer().getPlayerList().remove(player);
        h.succeed();
    }

    private static JarState state(ServerPlayer player) {
        return FormActions.state(player);
    }

    private static void fit(ServerPlayer player, ModuleType... types) {
        JarState state = state(player);
        for (int i = 0; i < types.length; i++) state.setModule(i, new ItemStack(ModRegistry.module(types[i])));
        ModuleEffects.apply(player, state);
    }

    /** Moves the jar two blocks east of its body (which would block the ray), facing south. */
    private static void faceSouthBesideBody(GameTestHelper h, ServerPlayer player) {
        Vec3 at = h.absoluteVec(Vec3.atBottomCenterOf(FLOOR.east(2)));
        player.moveTo(at.x, at.y, at.z, 0, 0);
    }

    private static HeadlessBody body(GameTestHelper h, ServerPlayer player) {
        return (HeadlessBody) h.getLevel().getEntity(state(player).body());
    }

    @GameTest(template = "platform")
    public static void detachLeavesBodyAndShrinksPlayer(GameTestHelper h) {
        ServerPlayer player = player(h);
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        h.assertTrue(FormActions.detach(player, 3), "detach failed");
        h.assertTrue(state(player).form() == Form.JAR, "not in jar form");
        h.assertTrue(Math.abs(player.getBbHeight() - FormEvents.JAR_SIZE.height()) < 1e-4, "hitbox " + player.getBbHeight());
        h.assertTrue(state(player).charge() == JarState.maxCharge(), "jar not charged");
        HeadlessBody body = body(h, player);
        h.assertTrue(body != null && player.getUUID().equals(body.ownerId()), "no owned body");
        h.assertTrue(body.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "body lacks the chestplate");
        h.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "jar still wears a chestplate");
        h.assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "the helmet should stay with the head");
        h.assertFalse(FormActions.detach(player, 3), "detached twice");
        done(h, player);
    }

    @GameTest(template = "platform")
    public static void reattachRestoresEverything(GameTestHelper h) {
        ServerPlayer player = player(h);
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        FormActions.detach(player, 5);
        fit(player, ModuleType.PROPELLER);
        HeadlessBody body = body(h, player);
        h.assertTrue(body.interact(player, InteractionHand.MAIN_HAND).consumesAction(), "using the body did nothing");
        h.assertTrue(state(player).form() == Form.BODY, "still a jar");
        h.assertTrue(Math.abs(player.getBbHeight() - 1.8f) < 1e-4, "hitbox " + player.getBbHeight());
        h.assertTrue(player.getItemBySlot(EquipmentSlot.LEGS).is(Items.DIAMOND_LEGGINGS), "leggings not back");
        h.assertTrue(body.isRemoved(), "body still standing");
        h.assertTrue(player.getInventory().contains(new ItemStack(ModRegistry.REINFORCED_JAR.get())), "reinforced jar not returned");
        h.assertTrue(player.getInventory().contains(new ItemStack(ModRegistry.module(ModuleType.PROPELLER))), "module not returned");
        ModuleEffects.apply(player, state(player));
        h.assertTrue(player.getAttributeValue(NeoForgeMod.CREATIVE_FLIGHT) == 0, "flight kept after reattaching");
        done(h, player);
    }

    @GameTest(template = "platform")
    public static void othersCannotWearYourBody(GameTestHelper h) {
        ServerPlayer owner = player(h);
        ServerPlayer thief = player(h);
        FormActions.detach(owner, 3);
        FormActions.detach(thief, 3);
        HeadlessBody body = body(h, owner);
        h.assertFalse(body.interact(thief, InteractionHand.MAIN_HAND).consumesAction(), "a stranger took the body");
        h.assertTrue(state(thief).form() == Form.JAR, "thief changed form");
        h.getLevel().getServer().getPlayerList().remove(thief);
        done(h, owner);
    }

    @GameTest(template = "platform")
    public static void bareJarCannotTouchTheWorld(GameTestHelper h) {
        ServerPlayer player = player(h);
        BlockPos lever = FLOOR.east(2);
        h.setBlock(lever, Blocks.LEVER.defaultBlockState());
        FormActions.detach(player, 3);
        h.assertTrue(FormEvents.lockedOut(player), "bare jar not locked out");
        BlockPos abs = h.absolutePos(lever);
        var hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        player.gameMode.useItemOn(player, h.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
        h.assertBlockProperty(lever, LeverBlock.POWERED, false);

        fit(player, ModuleType.MANIPULATOR_ARM);
        h.assertFalse(FormEvents.lockedOut(player), "arm didn't free the jar");
        player.gameMode.useItemOn(player, h.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
        h.assertBlockProperty(lever, LeverBlock.POWERED, true);
        done(h, player);
    }

    @GameTest(template = "platform")
    public static void modulesChangeAttributes(GameTestHelper h) {
        ServerPlayer player = player(h);
        double baseArmor = player.getAttributeValue(Attributes.ARMOR);
        FormActions.detach(player, 3);
        fit(player, ModuleType.ARMOR_PLATING, ModuleType.PROPELLER, ModuleType.HOP_SPRINGS);
        h.assertTrue(player.getAttributeValue(Attributes.ARMOR) == baseArmor + 8, "armour " + player.getAttributeValue(Attributes.ARMOR));
        h.assertTrue(player.getAttributeValue(NeoForgeMod.CREATIVE_FLIGHT) > 0, "propeller gives no flight");
        h.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) < 0.1 * 0.7, "hop springs not slower");
        // with no charge, powered modules stop; plating still works
        state(player).setCharge(0);
        ModuleEffects.apply(player, state(player));
        h.assertTrue(player.getAttributeValue(NeoForgeMod.CREATIVE_FLIGHT) == 0, "propeller flies without charge");
        h.assertTrue(player.getAttributeValue(Attributes.ARMOR) == baseArmor + 8, "plating needs no charge");
        done(h, player);
    }

    @GameTest(template = "platform")
    public static void slotsPastTheTierDoNothing(GameTestHelper h) {
        ServerPlayer player = player(h);
        FormActions.detach(player, 3);
        state(player).setModule(4, new ItemStack(ModRegistry.module(ModuleType.PROPELLER)));
        h.assertFalse(state(player).has(ModuleType.PROPELLER), "a shut slot works");
        done(h, player);
    }

    @GameTest(template = "platform", timeoutTicks = 60)
    public static void telekinesisLiftsAndThrowsBlocks(GameTestHelper h) {
        ServerPlayer player = player(h);
        BlockPos stone = FLOOR.east(2).south(3);
        h.setBlock(stone, Blocks.STONE);
        FormActions.detach(player, 3);
        fit(player, ModuleType.TELEKINESIS);
        faceSouthBesideBody(h, player);
        var held = Telekinesis.grab(player, state(player));
        h.assertTrue(held instanceof HeldBlockEntity, "grabbed " + held);
        h.assertTrue(held.blockPosition().equals(h.absolutePos(stone)), "lifted at " + held.blockPosition() + " not " + h.absolutePos(stone)
                + "; eye " + player.getEyePosition() + " look " + player.getLookAngle() + " state " + ((HeldBlockEntity) held).getBlockState());
        h.assertBlockPresent(Blocks.AIR, stone);
        // a mock player never ticks, so carry the block for it
        h.onEachTick(() -> Telekinesis.tick(player, state(player)));
        h.runAfterDelay(5, () -> {
            Telekinesis.throwHeld(player, state(player));
            h.assertTrue(state(player).heldEntity() < 0, "still holding");
            h.assertTrue(!h.getEntities(net.minecraft.world.entity.EntityType.FALLING_BLOCK).isEmpty(), "no thrown block");
            FallingBlockEntity falling = h.getEntities(net.minecraft.world.entity.EntityType.FALLING_BLOCK).get(0);
            h.assertTrue(falling.getBlockState().is(Blocks.STONE), "threw " + falling.getBlockState());
            done(h, player);
        });
    }

    @GameTest(template = "platform")
    public static void telekinesisRefusesBlockEntities(GameTestHelper h) {
        ServerPlayer player = player(h);
        BlockPos chest = FLOOR.east(2).south(3);
        h.setBlock(chest, Blocks.CHEST);
        FormActions.detach(player, 3);
        fit(player, ModuleType.TELEKINESIS);
        faceSouthBesideBody(h, player);
        var held = Telekinesis.grab(player, state(player));
        h.assertTrue(held == null, "lifted " + held + " at " + (held == null ? "" : held.blockPosition()) + "; eye " + player.getEyePosition() + " look " + player.getLookAngle());
        h.assertBlockPresent(Blocks.CHEST, chest);
        done(h, player);
    }

    @GameTest(template = "platform")
    public static void redstoneFeedsCharge(GameTestHelper h) {
        ServerPlayer player = player(h);
        FormActions.detach(player, 3);
        state(player).setCharge(0);
        JarMenu menu = new JarMenu(1, player.getInventory());
        menu.getSlot(JarState.MAX_SLOTS).set(new ItemStack(Items.REDSTONE, 3));
        h.assertTrue(state(player).charge() == 150, "charge " + state(player).charge());
        h.assertTrue(menu.getSlot(JarState.MAX_SLOTS).getItem().isEmpty(), "redstone left over");
        done(h, player);
    }

    @GameTest(template = "platform")
    public static void jarCannotDrown(GameTestHelper h) {
        ServerPlayer player = player(h);
        FormActions.detach(player, 3);
        player.spawnInvulnerableTime = 0;
        h.assertFalse(player.hurt(player.damageSources().drown(), 4), "jar drowned");
        h.assertTrue(player.hurt(player.damageSources().generic(), 1), "jar can't be hurt at all");
        done(h, player);
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void deathShattersJarAndBody(GameTestHelper h) {
        ServerPlayer player = player(h);
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        FormActions.detach(player, 3);
        fit(player, ModuleType.MAGNET);
        HeadlessBody body = body(h, player);
        FormActions.shatter(player);
        h.assertFalse(body.isAlive(), "body survived its head");
        h.runAfterDelay(2, () -> {
            var items = h.getEntities(net.minecraft.world.entity.EntityType.ITEM);
            h.assertTrue(items.stream().map(ItemEntity::getItem).anyMatch(s -> s.is(Items.IRON_BOOTS)), "boots not dropped");
            h.assertTrue(items.stream().map(ItemEntity::getItem).anyMatch(s -> s.is(ModRegistry.module(ModuleType.MAGNET))), "module not dropped");
            done(h, player);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void orphanBodyCollapses(GameTestHelper h) {
        ServerPlayer player = player(h);
        FormActions.detach(player, 3);
        HeadlessBody body = body(h, player);
        state(player).setBody(null);
        h.succeedWhen(() -> {
            h.assertFalse(body.isAlive(), "orphan body still standing");
            h.getLevel().getServer().getPlayerList().remove(player);
        });
    }
}
