package com.coolfreeze23.headinajar.registry;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.ChargingDockBlock;
import com.coolfreeze23.headinajar.entity.HeadlessBody;
import com.coolfreeze23.headinajar.entity.HeldBlockEntity;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.item.LifeSupportJarItem;
import com.coolfreeze23.headinajar.item.ModuleItem;
import com.coolfreeze23.headinajar.menu.JarMenu;
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
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

public final class ModRegistry {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HeadInAJar.MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HeadInAJar.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, HeadInAJar.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, HeadInAJar.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HeadInAJar.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, HeadInAJar.MOD_ID);

    public static final Supplier<AttachmentType<JarState>> JAR_STATE = ATTACHMENTS.register("jar_state",
            () -> AttachmentType.builder(() -> new JarState()).serialize(JarState.CODEC).build());

    public static final DeferredBlock<ChargingDockBlock> CHARGING_DOCK = BLOCKS.register("charging_dock",
            () -> new ChargingDockBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 6.0f)
                    .sound(SoundType.METAL).requiresCorrectToolForDrops().lightLevel(s -> 4).noOcclusion()));

    public static final DeferredItem<LifeSupportJarItem> LIFE_SUPPORT_JAR = ITEMS.register("life_support_jar",
            () -> new LifeSupportJarItem(3, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<LifeSupportJarItem> REINFORCED_JAR = ITEMS.register("reinforced_life_support_jar",
            () -> new LifeSupportJarItem(5, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).fireResistant()));
    public static final DeferredItem<BlockItem> CHARGING_DOCK_ITEM = ITEMS.registerSimpleBlockItem(CHARGING_DOCK);

    public static final Map<ModuleType, DeferredItem<ModuleItem>> MODULES = new EnumMap<>(ModuleType.class);

    static {
        for (ModuleType type : ModuleType.values()) {
            MODULES.put(type, ITEMS.register(type.id(), () -> new ModuleItem(type, new Item.Properties().stacksTo(1))));
        }
    }

    public static final DeferredHolder<EntityType<?>, EntityType<HeadlessBody>> HEADLESS_BODY = ENTITIES.register("headless_body",
            () -> EntityType.Builder.<HeadlessBody>of(HeadlessBody::new, MobCategory.MISC).sized(0.6f, 1.45f).eyeHeight(1.3f)
                    .clientTrackingRange(10).build("headless_body"));
    public static final DeferredHolder<EntityType<?>, EntityType<HeldBlockEntity>> HELD_BLOCK = ENTITIES.register("held_block",
            () -> EntityType.Builder.<HeldBlockEntity>of(HeldBlockEntity::new, MobCategory.MISC).sized(0.98f, 0.98f)
                    .clientTrackingRange(10).updateInterval(1).build("held_block"));

    public static final DeferredHolder<MenuType<?>, MenuType<JarMenu>> JAR_MENU = MENUS.register("jar",
            () -> new MenuType<>(JarMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final Supplier<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.headinajar"))
            .icon(() -> new ItemStack(LIFE_SUPPORT_JAR.get()))
            .displayItems((params, out) -> {
                out.accept(LIFE_SUPPORT_JAR.get());
                out.accept(REINFORCED_JAR.get());
                out.accept(CHARGING_DOCK_ITEM.get());
                for (ModuleType type : ModuleType.values()) out.accept(MODULES.get(type).get());
            })
            .build());

    public static ModuleItem module(ModuleType type) {
        return MODULES.get(type).get();
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        BLOCKS.register(bus);
        ENTITIES.register(bus);
        MENUS.register(bus);
        TABS.register(bus);
        ATTACHMENTS.register(bus);
        bus.addListener(ModRegistry::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(HEADLESS_BODY.get(), HeadlessBody.createAttributes().build());
    }

    private ModRegistry() {
    }
}
