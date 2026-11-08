package com.coolfreeze23.headinajar.registry;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.ChargingDockBlock;
import com.coolfreeze23.headinajar.block.GalvanicPileBlock;
import com.coolfreeze23.headinajar.block.GalvanicPileBlockEntity;
import com.coolfreeze23.headinajar.block.LifeSupportCradleBlock;
import com.coolfreeze23.headinajar.block.LifeSupportCradleBlockEntity;
import com.coolfreeze23.headinajar.block.SeparationChairBlock;
import com.coolfreeze23.headinajar.block.SeparationChairBlockEntity;
import com.coolfreeze23.headinajar.entity.HeadJarEntity;
import com.coolfreeze23.headinajar.entity.HeadlessBody;
import com.coolfreeze23.headinajar.entity.HeldBlockEntity;
import com.coolfreeze23.headinajar.entity.SeatEntity;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.item.CollarCamItem;
import com.coolfreeze23.headinajar.item.HeadSatchelItem;
import com.coolfreeze23.headinajar.item.LifeSupportJarItem;
import com.coolfreeze23.headinajar.item.ModuleItem;
import com.coolfreeze23.headinajar.item.RelayGogglesItem;
import com.coolfreeze23.headinajar.item.SutureKitItem;
import com.coolfreeze23.headinajar.menu.JarMenu;
import com.coolfreeze23.headinajar.menu.SeparationChairMenu;
import com.coolfreeze23.headinajar.module.ModuleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class ModRegistry {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HeadInAJar.MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HeadInAJar.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, HeadInAJar.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, HeadInAJar.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, HeadInAJar.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HeadInAJar.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, HeadInAJar.MOD_ID);

    /** Creative tab order. */
    private static final List<DeferredItem<? extends Item>> TAB_ORDER = new ArrayList<>();

    public static final Supplier<AttachmentType<JarState>> JAR_STATE = ATTACHMENTS.register("jar_state",
            () -> AttachmentType.builder(() -> new JarState()).serialize(JarState.CODEC).build());

    // --- blocks ------------------------------------------------------------------------------
    private static BlockBehaviour.Properties steel() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 6.0f).sound(SoundType.METAL).requiresCorrectToolForDrops();
    }

    public static final DeferredBlock<Block> SURGICAL_STEEL_BLOCK = BLOCKS.register("surgical_steel_block", () -> new Block(steel().strength(5.0f, 6.0f)));
    public static final DeferredBlock<SeparationChairBlock> SEPARATION_CHAIR = BLOCKS.register("separation_chair",
            () -> new SeparationChairBlock(steel().noOcclusion().explosionResistance(1200f)));
    public static final DeferredBlock<LifeSupportCradleBlock> CRADLE = BLOCKS.register("life_support_cradle",
            () -> new LifeSupportCradleBlock(steel().noOcclusion().lightLevel(s -> 5)));
    public static final DeferredBlock<GalvanicPileBlock> GALVANIC_PILE = BLOCKS.register("galvanic_pile",
            () -> new GalvanicPileBlock(steel().noOcclusion().lightLevel(s -> s.getValue(GalvanicPileBlock.LIT) ? 6 : 0)));
    public static final DeferredBlock<ChargingDockBlock> CHARGING_DOCK = BLOCKS.register("charging_dock",
            () -> new ChargingDockBlock(steel().lightLevel(s -> 4).noOcclusion()));

    // --- items -------------------------------------------------------------------------------
    private static <T extends Item> DeferredItem<T> item(String name, Supplier<T> sup) {
        DeferredItem<T> item = ITEMS.register(name, sup);
        TAB_ORDER.add(item);
        return item;
    }

    private static DeferredItem<Item> plain(String name) {
        return item(name, () -> new Item(new Item.Properties()));
    }

    private static DeferredItem<BlockItem> blockItem(DeferredBlock<? extends Block> block) {
        DeferredItem<BlockItem> item = ITEMS.registerSimpleBlockItem(block);
        TAB_ORDER.add(item);
        return item;
    }

    public static final DeferredItem<Item> SURGICAL_STEEL_BLEND = plain("surgical_steel_blend");
    public static final DeferredItem<Item> SURGICAL_STEEL_INGOT = plain("surgical_steel_ingot");
    public static final DeferredItem<Item> NEURO_COPPER_WIRE = plain("neuro_copper_wire");
    public static final DeferredItem<Item> LENS = plain("lens");
    public static final DeferredItem<Item> SERVO = plain("servo");
    public static final DeferredItem<Item> EXCISION_HEAD = plain("excision_head");
    public static final DeferredItem<Item> BOOTH_CONSOLE = plain("booth_console");
    public static final DeferredItem<BlockItem> SURGICAL_STEEL_BLOCK_ITEM = blockItem(SURGICAL_STEEL_BLOCK);
    public static final DeferredItem<BlockItem> SEPARATION_CHAIR_ITEM = blockItem(SEPARATION_CHAIR);
    public static final DeferredItem<BlockItem> CRADLE_ITEM = blockItem(CRADLE);
    public static final DeferredItem<BlockItem> GALVANIC_PILE_ITEM = blockItem(GALVANIC_PILE);
    public static final DeferredItem<BlockItem> CHARGING_DOCK_ITEM = blockItem(CHARGING_DOCK);
    public static final DeferredItem<Item> REPLACEMENT_BLADE = plain("replacement_blade");
    public static final DeferredItem<Item> SALINE_BOTTLE = item("saline_bottle", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<LifeSupportJarItem> LIFE_SUPPORT_JAR = item("life_support_jar",
            () -> new LifeSupportJarItem(3, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<LifeSupportJarItem> REINFORCED_JAR = item("reinforced_life_support_jar",
            () -> new LifeSupportJarItem(5, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).fireResistant()));
    public static final DeferredItem<RelayGogglesItem> RELAY_GOGGLES = item("relay_goggles",
            () -> new RelayGogglesItem(1, new Item.Properties().durability(250)));
    public static final DeferredItem<CollarCamItem> COLLAR_CAM = item("collar_cam", () -> new CollarCamItem(new Item.Properties().durability(250)));
    public static final DeferredItem<HeadSatchelItem> HEAD_SATCHEL = item("head_satchel", () -> new HeadSatchelItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<SutureKitItem> SUTURE_KIT = item("suture_kit", () -> new SutureKitItem(new Item.Properties().stacksTo(16)));

    public static final Map<ModuleType, DeferredItem<ModuleItem>> MODULES = new EnumMap<>(ModuleType.class);

    static {
        for (ModuleType type : ModuleType.values()) {
            MODULES.put(type, item(type.id(), () -> new ModuleItem(type, new Item.Properties().stacksTo(1))));
        }
    }

    // --- entities ----------------------------------------------------------------------------
    public static final DeferredHolder<EntityType<?>, EntityType<HeadlessBody>> HEADLESS_BODY = ENTITIES.register("headless_body",
            () -> EntityType.Builder.<HeadlessBody>of(HeadlessBody::new, MobCategory.MISC).sized(0.6f, 1.45f).eyeHeight(1.3f)
                    .clientTrackingRange(10).build("headless_body"));
    public static final DeferredHolder<EntityType<?>, EntityType<HeldBlockEntity>> HELD_BLOCK = ENTITIES.register("held_block",
            () -> EntityType.Builder.<HeldBlockEntity>of(HeldBlockEntity::new, MobCategory.MISC).sized(0.98f, 0.98f)
                    .clientTrackingRange(10).updateInterval(1).build("held_block"));
    public static final DeferredHolder<EntityType<?>, EntityType<HeadJarEntity>> HEAD_JAR = ENTITIES.register("head_jar",
            () -> EntityType.Builder.<HeadJarEntity>of(HeadJarEntity::new, MobCategory.MISC).sized(0.62f, 0.72f)
                    .clientTrackingRange(10).build("head_jar"));
    public static final DeferredHolder<EntityType<?>, EntityType<SeatEntity>> SEAT = ENTITIES.register("seat",
            () -> EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC).sized(0.01f, 0.01f).noSummon()
                    .clientTrackingRange(10).build("seat"));

    // --- block entities ----------------------------------------------------------------------
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SeparationChairBlockEntity>> CHAIR_BE = BLOCK_ENTITIES.register("separation_chair",
            () -> BlockEntityType.Builder.of(SeparationChairBlockEntity::new, SEPARATION_CHAIR.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LifeSupportCradleBlockEntity>> CRADLE_BE = BLOCK_ENTITIES.register("life_support_cradle",
            () -> BlockEntityType.Builder.of(LifeSupportCradleBlockEntity::new, CRADLE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GalvanicPileBlockEntity>> PILE_BE = BLOCK_ENTITIES.register("galvanic_pile",
            () -> BlockEntityType.Builder.of(GalvanicPileBlockEntity::new, GALVANIC_PILE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChargingDockBlock.Entity>> DOCK_BE = BLOCK_ENTITIES.register("charging_dock",
            () -> BlockEntityType.Builder.of(ChargingDockBlock.Entity::new, CHARGING_DOCK.get()).build(null));

    // --- menus -------------------------------------------------------------------------------
    public static final DeferredHolder<MenuType<?>, MenuType<JarMenu>> JAR_MENU = MENUS.register("jar",
            () -> new MenuType<>(JarMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>, MenuType<SeparationChairMenu>> CHAIR_MENU = MENUS.register("separation_chair",
            () -> IMenuTypeExtension.create(SeparationChairMenu::new));

    public static final Supplier<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.headinajar"))
            .icon(() -> new ItemStack(LIFE_SUPPORT_JAR.get()))
            .displayItems((params, out) -> {
                for (DeferredItem<? extends Item> item : TAB_ORDER) out.accept(item.get());
            })
            .build());

    public static ModuleItem module(ModuleType type) {
        return MODULES.get(type).get();
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        BLOCKS.register(bus);
        ENTITIES.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        TABS.register(bus);
        ATTACHMENTS.register(bus);
        bus.addListener(ModRegistry::attributes);
        bus.addListener(ModRegistry::capabilities);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(HEADLESS_BODY.get(), HeadlessBody.createAttributes().build());
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, CHAIR_BE.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CHAIR_BE.get(), (be, side) -> be.items());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, PILE_BE.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PILE_BE.get(), (be, side) -> be.fuel());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, DOCK_BE.get(), (be, side) -> be.energy());
    }

    private ModRegistry() {
    }
}
