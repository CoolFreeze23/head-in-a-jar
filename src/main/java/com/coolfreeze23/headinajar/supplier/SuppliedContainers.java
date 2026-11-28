package com.coolfreeze23.headinajar.supplier;

import com.coolfreeze23.headinajar.HeadInAJar;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Delivered containers staff can reach: the copper chests and the shelf. The game's own item handlers cover only the
 * containers it knew of, so these are registered here, by id, exactly when the ids are registered (nothing otherwise).
 * Every container read in the company goes through {@code Capabilities.ItemHandler.BLOCK}, so this one place is what
 * lets the Clipboard, Hauling, Sorting, the pay drawer and every desk job use them. A copper chest is read the way the
 * player opens it ({@code ChestBlock.getContainer}): a double chest is one container.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class SuppliedContainers {
    /** The copper chests, in their weathering order, plain then waxed. */
    public static final List<String> COPPER_CHESTS = List.of(
            "copper_chest", "exposed_copper_chest", "weathered_copper_chest", "oxidized_copper_chest",
            "waxed_copper_chest", "waxed_exposed_copper_chest", "waxed_weathered_copper_chest", "waxed_oxidized_copper_chest")
            .stream().map(p -> "minecraft:" + p).toList();
    /** The shelf's block entity (every wood's shelf is one type). */
    public static final String SHELF = "minecraft:shelf";
    /** Copper chests and copper golem statues, by their own tags. */
    public static final TagKey<Block> COPPER_CHEST_TAG = TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("copper_chests"));
    public static final TagKey<Block> STATUES = TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("copper_golem_statues"));

    private SuppliedContainers() {
    }

    /** The copper chest blocks this game has (a chest kind of block, by id). */
    public static List<ChestBlock> copperChests() {
        List<ChestBlock> out = new ArrayList<>();
        for (String id : COPPER_CHESTS) {
            Block block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id)).orElse(null);
            if (block instanceof ChestBlock chest) out.add(chest);
        }
        return out;
    }

    /** The shelf's block entity type, if this game has one. */
    public static BlockEntityType<?> shelf() {
        return BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(ResourceLocation.parse(SHELF)).orElse(null);
    }

    @SubscribeEvent
    static void capabilities(RegisterCapabilitiesEvent event) {
        List<ChestBlock> chests = copperChests();
        if (!chests.isEmpty()) {
            event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, be, side) -> {
                if (!(state.getBlock() instanceof ChestBlock chest)) return null;
                Container c = ChestBlock.getContainer(chest, state, level, pos, true);
                return c == null ? null : new InvWrapper(c);
            }, chests.toArray(new Block[0]));
        }
        registerContainer(event, shelf());
        HeadInAJar.LOGGER.debug("Supplied containers: {} copper chests, shelf {}", chests.size(), shelf() != null);
    }

    /** A block entity type whose block entities are containers: an item handler over the container itself. */
    @SuppressWarnings("unchecked")
    private static void registerContainer(RegisterCapabilitiesEvent event, BlockEntityType<?> type) {
        if (type == null) return;
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, (BlockEntityType<BlockEntity>) type,
                (be, side) -> be instanceof Container c ? new InvWrapper(c) : null);
    }
}
