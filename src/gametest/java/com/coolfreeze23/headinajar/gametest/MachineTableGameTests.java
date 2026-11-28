package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.SurgicalSawItem;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.supplier.SuppliedMachines;
import com.coolfreeze23.headinajar.supplier.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * The machine tables of 0.8.0 as a game without the supplier sees them (and with it): the supplied tags are empty
 * without it, a vanilla creature's body meets none of the new lines, a jarred head still fits a shulker box, and a
 * Lookout's alarm with an id the game has no sound for falls back to the creature's own. Pass both ways.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class MachineTableGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);

    /** The tags the new lines are keyed to hold nothing without the supplier, and something with it. */
    @GameTest(template = "platform")
    public static void theSuppliedTagsAreTheSuppliers(GameTestHelper h) {
        boolean delivered = !Supplier.delivered().isEmpty();
        for (TagKey<Item> tag : List.of(SuppliedMachines.STATUE_ITEMS, SuppliedMachines.BUNDLES, SuppliedMachines.HARNESSES,
                SuppliedMachines.COPPER_BLOCKS, SuppliedMachines.RESIN, SuppliedMachines.COPPER_PARTS, SuppliedMachines.NAUTILUS_ARMOR)) {
            boolean filled = BuiltInRegistries.ITEM.getTag(tag).map(set -> set.size() > 0).orElse(false);
            h.assertValueEqual(filled, delivered, "#" + tag.location() + (delivered ? " is empty with the supplier" : " has items without the supplier"));
        }
        h.assertFalse(new ItemStack(Items.COPPER_INGOT).is(SuppliedMachines.COPPER_PARTS), "a copper ingot is not counted as the supplier's copper");
        h.succeed();
    }

    /** A cow's body: honeycomb, the fluid, the lace and the locker answer as they always did. */
    @GameTest(template = "platform")
    public static void aVanillaBodyMeetsNoneOfTheNewLines(GameTestHelper h) {
        ServerPlayer p = TestPlayers.at(h, "vanilla", FLOOR.south(4));
        Cow cow = h.spawn(EntityType.COW, FLOOR);
        cow.setHealth(2);
        SurgicalSawItem.saw(p, new ItemStack(ModRegistry.SURGICAL_SAW.get()), cow, InteractionHand.MAIN_HAND);
        h.assertTrue(Hosts.isHost(cow), "setup: a cow host");
        ItemStack comb = new ItemStack(Items.HONEYCOMB);
        h.assertTrue(SuppliedMachines.honeycombRefusal(comb, cow) == null && !SuppliedMachines.waxesItself(p, comb, cow), "honeycomb on a cow's body");
        h.assertTrue(SuppliedMachines.embalmRefusal(cow) == null && SuppliedMachines.laceRefusal(cow) == null, "the fluid and the lace on a cow's body");
        h.assertTrue(SuppliedMachines.lockerRefusal(cow) == null, "the locker on a cow's body");
        h.assertValueEqual(SuppliedMachines.storedLine(cow), "message.headinajar.locker_stored", "the locker's line for a cow");
        h.assertTrue(SuppliedMachines.mechaRefusal(new ItemStack(Items.IRON_INGOT)) == null, "an iron ingot at a mecha");
        h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    /** A jarred head still goes in a shulker box: the bundle's refusal is the bundle's own, not the item's. */
    @GameTest(template = "platform")
    public static void aJarredHeadStillFitsAShulkerBox(GameTestHelper h) {
        h.assertTrue(new ItemStack(ModRegistry.JARRED_HEAD.get()).canFitInsideContainerItems(), "a jarred head no longer fits a shulker box");
        h.succeed();
    }

    /** An alarm id the game has no sound for (a row whose supplier took the sound away) falls back to the creature's own. */
    @GameTest(template = "platform")
    public static void anUnknownAlarmFallsBackToTheCreaturesOwn(GameTestHelper h) {
        h.assertValueEqual(TurretPedestalBlockEntity.alarmSound(h.getLevel(), EntityType.COW, HeadInAJar.id("no_such_sound")), SoundEvents.COW_AMBIENT,
                "a cow with an unknown alarm");
        h.succeed();
    }
}
