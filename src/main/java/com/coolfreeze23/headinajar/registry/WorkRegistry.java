package com.coolfreeze23.headinajar.registry;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.TimeClockBlock;
import com.coolfreeze23.headinajar.block.TimeClockBlockEntity;
import com.coolfreeze23.headinajar.item.ClipboardItem;
import com.coolfreeze23.headinajar.item.HardHatItem;
import com.coolfreeze23.headinajar.item.JobCardItem;
import com.coolfreeze23.headinajar.item.SeparationNoticeItem;
import com.coolfreeze23.headinajar.item.StaffLanyardItem;
import com.coolfreeze23.headinajar.item.WhistleItem;
import com.coolfreeze23.headinajar.menu.ClipboardMenu;
import com.coolfreeze23.headinajar.menu.TimeClockMenu;
import com.coolfreeze23.headinajar.staff.work.Order;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;
import java.util.function.Supplier;

/**
 * The workforce's registrations (0.7.0): the Staff Lanyard, the Time Clock, the Clipboard and Job Cards, the Hard Hat,
 * the Supervisor's Whistle and Form 45-S; the worker attachment; the order and card data components; the Hard Hat's
 * armour material. Loaded from {@code ModRegistry.register}, before its registers are handed to the bus.
 */
public final class WorkRegistry {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, HeadInAJar.MOD_ID);
    public static final DeferredRegister<ArmorMaterial> ARMOR = DeferredRegister.create(Registries.ARMOR_MATERIAL, HeadInAJar.MOD_ID);

    /** A creature hired with a Staff Lanyard (saved with it; no attachment: not a worker). */
    public static final Supplier<AttachmentType<WorkerData>> WORKER = ModRegistry.ATTACHMENTS.register("worker",
            () -> AttachmentType.builder(WorkerData::new).serialize(WorkerData.CODEC, d -> d.employer != null).build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Order>> ORDER = COMPONENTS.register("order",
            () -> DataComponentType.<Order>builder().persistent(Order.CODEC).networkSynchronized(Order.STREAM).cacheEncoding().build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Order.Card>> JOB_CARD_DATA = COMPONENTS.register("job_card",
            () -> DataComponentType.<Order.Card>builder().persistent(Order.Card.CODEC).networkSynchronized(Order.Card.STREAM).cacheEncoding().build());

    /** The equip sound is the Chimera's "Hard hat issued". */
    private static final Holder<net.minecraft.sounds.SoundEvent> CLONK = com.coolfreeze23.headinajar.mecha.HopperSounds.holder("chimera.hard_hat_clonk");
    /** A leather helmet's armour, no durability (it never wears out), mended with iron. */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> HARD_HAT_MATERIAL = ARMOR.register("hard_hat", () -> {
        EnumMap<ArmorItem.Type, Integer> defence = new EnumMap<>(ArmorItem.Type.class);
        for (ArmorItem.Type t : ArmorItem.Type.values()) defence.put(t, t == ArmorItem.Type.HELMET ? 1 : 0);
        return new ArmorMaterial(defence, 15, CLONK, () -> Ingredient.of(Items.IRON_INGOT),
                List.of(new ArmorMaterial.Layer(HeadInAJar.id("hard_hat"))), 0, 0);
    });

    // --- the Time Clock ----------------------------------------------------------------------------------------------
    public static final DeferredBlock<TimeClockBlock> TIME_CLOCK = ModRegistry.BLOCKS.register("time_clock",
            () -> new TimeClockBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 6.0f).sound(SoundType.METAL)
                    .requiresCorrectToolForDrops().noOcclusion()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TimeClockBlockEntity>> TIME_CLOCK_BE = ModRegistry.BLOCK_ENTITIES.register("time_clock",
            () -> BlockEntityType.Builder.of(TimeClockBlockEntity::new, TIME_CLOCK.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<TimeClockMenu>> TIME_CLOCK_MENU = ModRegistry.MENUS.register("time_clock",
            () -> IMenuTypeExtension.create(TimeClockMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<ClipboardMenu>> CLIPBOARD_MENU = ModRegistry.MENUS.register("clipboard",
            () -> IMenuTypeExtension.create(ClipboardMenu::new));

    // --- items, in the creative tab's order --------------------------------------------------------------------------
    public static final DeferredItem<StaffLanyardItem> STAFF_LANYARD = item("staff_lanyard", () -> new StaffLanyardItem(new Item.Properties()));
    public static final DeferredItem<BlockItem> TIME_CLOCK_ITEM = item("time_clock", () -> new BlockItem(TIME_CLOCK.get(), new Item.Properties()));
    public static final DeferredItem<ClipboardItem> CLIPBOARD = item("clipboard", () -> new ClipboardItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<JobCardItem> JOB_CARD = item("job_card", () -> new JobCardItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<HardHatItem> HARD_HAT = item("hard_hat", () -> new HardHatItem(HARD_HAT_MATERIAL, ArmorItem.Type.HELMET, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<WhistleItem> SUPERVISORS_WHISTLE = item("supervisors_whistle", () -> new WhistleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<SeparationNoticeItem> SEPARATION_NOTICE = item("separation_notice",
            () -> new SeparationNoticeItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));

    private WorkRegistry() {
    }

    private static <T extends Item> DeferredItem<T> item(String name, Supplier<T> sup) {
        return ModRegistry.tabbed(ModRegistry.ITEMS.register(name, sup));
    }

    /** Form 45-S for a worker just let go (its number and name on the form). */
    public static ItemStack separationNotice(Mob mob) {
        return SeparationNoticeItem.of(mob);
    }

    static void register(IEventBus bus) {
        com.coolfreeze23.headinajar.staff.work.WorkSounds.init();
        COMPONENTS.register(bus);
        ARMOR.register(bus);
        bus.addListener(WorkRegistry::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        // the Pay Drawer: hoppers fill it from any side, nothing is taken out by automation; the rack is not automatable
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, TIME_CLOCK_BE.get(), (be, side) -> be.automation(side));
        // the Broth tank (0.7.1): pipes fill it from any side, nothing drains it
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, TIME_CLOCK_BE.get(), (be, side) -> be.brothPipes());
    }
}
