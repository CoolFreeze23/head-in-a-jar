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
    public static final DeferredRegister<net.minecraft.world.item.crafting.RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, HeadInAJar.MOD_ID);
    public static final DeferredHolder<net.minecraft.world.item.crafting.RecipeSerializer<?>, net.minecraft.world.item.crafting.RecipeSerializer<com.coolfreeze23.headinajar.item.EntangleRecipe>> ENTANGLE_RECIPE =
            RECIPE_SERIALIZERS.register("entangle", () -> new net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<>(com.coolfreeze23.headinajar.item.EntangleRecipe::new));
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, HeadInAJar.MOD_ID);

    /** Creative tab order. */
    private static final List<DeferredItem<? extends Item>> TAB_ORDER = new ArrayList<>();

    public static final Supplier<AttachmentType<com.coolfreeze23.headinajar.host.HostData>> HOST_DATA = ATTACHMENTS.register("host",
            () -> AttachmentType.builder(() -> new com.coolfreeze23.headinajar.host.HostData()).serialize(com.coolfreeze23.headinajar.host.HostData.CODEC,
                    d -> d.host).build());
    public static final Supplier<AttachmentType<JarState>> JAR_STATE = ATTACHMENTS.register("jar_state",
            () -> AttachmentType.builder(() -> new JarState()).serialize(JarState.CODEC).build());

    public static final Supplier<AttachmentType<com.coolfreeze23.headinajar.genetics.GeneSet>> GENES = ATTACHMENTS.register("genes",
            () -> AttachmentType.builder(() -> new com.coolfreeze23.headinajar.genetics.GeneSet()).serialize(com.coolfreeze23.headinajar.genetics.GeneSet.CODEC,
                    g -> !g.isEmpty()).build());

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

    // P2: Life Support
    private static net.minecraft.world.phys.shapes.VoxelShape box(double x0, double y0, double z0, double x1, double y1, double z1) {
        return Block.box(x0, y0, z0, x1, y1, z1);
    }

    public static final DeferredBlock<com.coolfreeze23.headinajar.block.MachineBlock<com.coolfreeze23.headinajar.block.SalineMixerBlockEntity>> SALINE_MIXER =
            BLOCKS.register("saline_mixer", () -> new com.coolfreeze23.headinajar.block.MachineBlock<>(steel().noOcclusion(),
                    () -> ModRegistry.MIXER_BE.get(), com.coolfreeze23.headinajar.block.SalineMixerBlockEntity::new,
                    com.coolfreeze23.headinajar.block.SalineMixerBlockEntity::serverTick, null));
    public static final DeferredBlock<com.coolfreeze23.headinajar.block.MachineBlock<com.coolfreeze23.headinajar.block.BodyPrinterBlockEntity>> BODY_PRINTER =
            BLOCKS.register("body_printer", () -> new com.coolfreeze23.headinajar.block.MachineBlock<>(steel().noOcclusion(),
                    () -> ModRegistry.PRINTER_BE.get(), com.coolfreeze23.headinajar.block.BodyPrinterBlockEntity::new,
                    com.coolfreeze23.headinajar.block.BodyPrinterBlockEntity::serverTick, null));
    public static final DeferredBlock<com.coolfreeze23.headinajar.block.PowerBlock<com.coolfreeze23.headinajar.block.PowerBlockEntities.LeydenJar>> LEYDEN_JAR =
            BLOCKS.register("leyden_jar", () -> new com.coolfreeze23.headinajar.block.PowerBlock<>(steel().noOcclusion().lightLevel(s -> 3),
                    () -> ModRegistry.LEYDEN_BE.get(), com.coolfreeze23.headinajar.block.PowerBlockEntities.LeydenJar::new,
                    com.coolfreeze23.headinajar.block.PowerBlockEntities.LeydenJar::tick, box(3, 0, 3, 13, 15, 13)));
    public static final DeferredBlock<com.coolfreeze23.headinajar.block.PowerBlock<com.coolfreeze23.headinajar.block.PowerBlockEntities.TeslaNode>> TESLA_NODE =
            BLOCKS.register("tesla_node", () -> new com.coolfreeze23.headinajar.block.PowerBlock<>(steel().noOcclusion(),
                    () -> ModRegistry.TESLA_BE.get(), com.coolfreeze23.headinajar.block.PowerBlockEntities.TeslaNode::new,
                    com.coolfreeze23.headinajar.block.PowerBlockEntities.TeslaNode::tick, box(4, 0, 4, 12, 16, 12)));
    public static final DeferredBlock<com.coolfreeze23.headinajar.block.PowerBlock<com.coolfreeze23.headinajar.block.PowerBlockEntities.Station>> RELAY_MAST =
            BLOCKS.register("relay_mast", () -> new com.coolfreeze23.headinajar.block.PowerBlock<>(steel().noOcclusion(),
                    () -> ModRegistry.MAST_BE.get(), com.coolfreeze23.headinajar.block.PowerBlockEntities.Station::mast,
                    com.coolfreeze23.headinajar.block.PowerBlockEntities.Station::tick, box(5, 0, 5, 11, 16, 11)));
    public static final DeferredBlock<com.coolfreeze23.headinajar.block.PowerBlock<com.coolfreeze23.headinajar.block.PowerBlockEntities.Station>> RELAY_MAST_MK2 =
            BLOCKS.register("relay_mast_mk2", () -> new com.coolfreeze23.headinajar.block.PowerBlock<>(steel().noOcclusion(),
                    () -> ModRegistry.MAST2_BE.get(), com.coolfreeze23.headinajar.block.PowerBlockEntities.Station::mast2,
                    com.coolfreeze23.headinajar.block.PowerBlockEntities.Station::tick, box(5, 0, 5, 11, 16, 11)));
    public static final DeferredBlock<com.coolfreeze23.headinajar.block.PowerBlock<com.coolfreeze23.headinajar.block.PowerBlockEntities.Station>> SIGNAL_JAMMER =
            BLOCKS.register("signal_jammer", () -> new com.coolfreeze23.headinajar.block.PowerBlock<>(steel().noOcclusion(),
                    () -> ModRegistry.JAMMER_BE.get(), com.coolfreeze23.headinajar.block.PowerBlockEntities.Station::jammer,
                    com.coolfreeze23.headinajar.block.PowerBlockEntities.Station::tick, box(2, 0, 2, 14, 12, 14)));
    public static final DeferredBlock<com.coolfreeze23.headinajar.block.SafetyPlacardBlock> SAFETY_PLACARD = BLOCKS.register("safety_placard",
            () -> new com.coolfreeze23.headinajar.block.SafetyPlacardBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW)
                    .strength(0.5f).sound(SoundType.METAL).noCollission()));

    // P3: Hostile Takeover
    public static final DeferredBlock<com.coolfreeze23.headinajar.block.JarredHeadBlock> JARRED_HEAD_BLOCK = BLOCKS.register("jarred_head",
            () -> new com.coolfreeze23.headinajar.block.JarredHeadBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(0.6f)
                    .sound(SoundType.GLASS).noOcclusion()));
    public static final DeferredBlock<com.coolfreeze23.headinajar.block.MachineBlock<com.coolfreeze23.headinajar.block.BodyLockerBlockEntity>> BODY_LOCKER =
            BLOCKS.register("body_locker", () -> new com.coolfreeze23.headinajar.block.MachineBlock<>(steel().noOcclusion(),
                    () -> ModRegistry.LOCKER_BE.get(), com.coolfreeze23.headinajar.block.BodyLockerBlockEntity::new, null, null) {
                @Override
                protected net.minecraft.world.InteractionResult useWithoutItem(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.Level level,
                                                                              net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player player,
                                                                              net.minecraft.world.phys.BlockHitResult hit) {
                    if (!level.isClientSide && level.getBlockEntity(pos) instanceof com.coolfreeze23.headinajar.block.BodyLockerBlockEntity locker) locker.use(player);
                    return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
                }

                @Override
                protected void onRemove(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                                        net.minecraft.world.level.block.state.BlockState newState, boolean moved) {
                    if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof com.coolfreeze23.headinajar.block.BodyLockerBlockEntity locker) locker.releaseOnBreak();
                    super.onRemove(state, level, pos, newState, moved);
                }
            });

    // P4: Mecha Division
    public static final DeferredBlock<com.coolfreeze23.headinajar.block.MachineBlock<com.coolfreeze23.headinajar.block.AssemblyGantryBlockEntity>> ASSEMBLY_GANTRY =
            BLOCKS.register("assembly_gantry", () -> new com.coolfreeze23.headinajar.block.MachineBlock<>(steel().noOcclusion(),
                    () -> ModRegistry.GANTRY_BE.get(), com.coolfreeze23.headinajar.block.AssemblyGantryBlockEntity::new, null,
                    Block.box(0, 0, 0, 16, 6, 16)));

    // P5: Genetics Department
    public static final DeferredBlock<com.coolfreeze23.headinajar.block.MachineBlock<com.coolfreeze23.headinajar.block.LabMachines.Centrifuge>> CENTRIFUGE = BLOCKS.register("centrifuge",
            () -> new com.coolfreeze23.headinajar.block.MachineBlock<>(steel().noOcclusion(), () -> ModRegistry.CENTRIFUGE_BE.get(), com.coolfreeze23.headinajar.block.LabMachines.Centrifuge::new,
                    com.coolfreeze23.headinajar.block.LabMachines.Centrifuge::serverTick, null));
    public static final DeferredBlock<com.coolfreeze23.headinajar.block.MachineBlock<com.coolfreeze23.headinajar.block.LabMachines.Sequencer>> DNA_SEQUENCER = BLOCKS.register("dna_sequencer",
            () -> new com.coolfreeze23.headinajar.block.MachineBlock<>(steel().noOcclusion().lightLevel(s -> 4), () -> ModRegistry.SEQUENCER_BE.get(), com.coolfreeze23.headinajar.block.LabMachines.Sequencer::new,
                    com.coolfreeze23.headinajar.block.LabMachines.Sequencer::serverTick, null));
    public static final DeferredBlock<com.coolfreeze23.headinajar.block.MachineBlock<com.coolfreeze23.headinajar.block.LabMachines.Splicer>> GENE_SPLICER = BLOCKS.register("gene_splicer",
            () -> new com.coolfreeze23.headinajar.block.MachineBlock<>(steel().noOcclusion().lightLevel(s -> 6), () -> ModRegistry.SPLICER_BE.get(), com.coolfreeze23.headinajar.block.LabMachines.Splicer::new,
                    com.coolfreeze23.headinajar.block.LabMachines.Splicer::serverTick, null));
    public static final DeferredBlock<com.coolfreeze23.headinajar.block.MachineBlock<com.coolfreeze23.headinajar.block.LabMachines.InjectionRig>> INJECTION_RIG = BLOCKS.register("injection_rig",
            () -> new com.coolfreeze23.headinajar.block.MachineBlock<>(steel().noOcclusion(), () -> ModRegistry.RIG_BE.get(), com.coolfreeze23.headinajar.block.LabMachines.InjectionRig::new,
                    null, null));
    public static final DeferredBlock<com.coolfreeze23.headinajar.block.MachineBlock<com.coolfreeze23.headinajar.block.LeechJarBlockEntity>> LEECH_JAR =
            BLOCKS.register("leech_jar", () -> new com.coolfreeze23.headinajar.block.MachineBlock<>(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(0.6f)
                    .sound(SoundType.GLASS).noOcclusion(), () -> ModRegistry.LEECH_JAR_BE.get(), com.coolfreeze23.headinajar.block.LeechJarBlockEntity::new,
                    com.coolfreeze23.headinajar.block.LeechJarBlockEntity::serverTick, Block.box(4, 0, 4, 12, 10, 12)) {
                @Override
                protected net.minecraft.world.InteractionResult useWithoutItem(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.Level level,
                                                                              net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player player,
                                                                              net.minecraft.world.phys.BlockHitResult hit) {
                    if (!level.isClientSide && level.getBlockEntity(pos) instanceof com.coolfreeze23.headinajar.block.LeechJarBlockEntity jar) jar.empty(player);
                    return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
                }
            });

    // P6: Board of Directors
    public static final DeferredBlock<com.coolfreeze23.headinajar.block.HeadHolderBlock<com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity>> TURRET_PEDESTAL =
            BLOCKS.register("turret_pedestal", () -> new com.coolfreeze23.headinajar.block.HeadHolderBlock<>(steel().noOcclusion(), () -> ModRegistry.TURRET_BE.get(),
                    com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity::new, com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity::serverTick,
                    Block.box(2, 0, 2, 14, 10, 14)));
    public static final DeferredBlock<com.coolfreeze23.headinajar.block.HeadHolderBlock<com.coolfreeze23.headinajar.block.EmployeeMountBlockEntity>> EMPLOYEE_MOUNT =
            BLOCKS.register("employee_mount", () -> new com.coolfreeze23.headinajar.block.HeadHolderBlock<>(steel().noOcclusion(), () -> ModRegistry.MOUNT_BE.get(),
                    com.coolfreeze23.headinajar.block.EmployeeMountBlockEntity::new, com.coolfreeze23.headinajar.block.EmployeeMountBlockEntity::serverTick,
                    Block.box(3, 0, 3, 13, 12, 13)));
    public static final DeferredBlock<com.coolfreeze23.headinajar.block.MachineBlock<com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity>> CEREBRAL_ARRAY =
            BLOCKS.register("cerebral_array", () -> new com.coolfreeze23.headinajar.block.MachineBlock<>(steel().noOcclusion().lightLevel(s -> 7),
                    () -> ModRegistry.ARRAY_BE.get(), com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity::new,
                    com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity::serverTick, null));

    public static final DeferredBlock<com.coolfreeze23.headinajar.block.NeuralSocketBlock> NEURAL_SOCKET = BLOCKS.register("neural_socket",
            () -> new com.coolfreeze23.headinajar.block.NeuralSocketBlock(steel().noOcclusion().lightLevel(s -> 5)));

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
    public static final DeferredItem<BlockItem> SALINE_MIXER_ITEM = blockItem(SALINE_MIXER);
    public static final DeferredItem<BlockItem> BODY_PRINTER_ITEM = blockItem(BODY_PRINTER);
    public static final DeferredItem<BlockItem> LEYDEN_JAR_ITEM = blockItem(LEYDEN_JAR);
    public static final DeferredItem<BlockItem> TESLA_NODE_ITEM = blockItem(TESLA_NODE);
    public static final DeferredItem<BlockItem> RELAY_MAST_ITEM = blockItem(RELAY_MAST);
    public static final DeferredItem<BlockItem> RELAY_MAST_MK2_ITEM = blockItem(RELAY_MAST_MK2);
    public static final DeferredItem<BlockItem> SIGNAL_JAMMER_ITEM = blockItem(SIGNAL_JAMMER);
    public static final DeferredItem<BlockItem> SAFETY_PLACARD_ITEM = blockItem(SAFETY_PLACARD);
    public static final DeferredItem<Item> SYNAPSE_CHIP = plain("synapse_chip");
    public static final DeferredItem<Item> GLASS_PATCH = item("glass_patch", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<com.coolfreeze23.headinajar.item.SurgicalWrenchItem> SURGICAL_WRENCH = item("surgical_wrench",
            () -> new com.coolfreeze23.headinajar.item.SurgicalWrenchItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> REPLACEMENT_BLADE = plain("replacement_blade");
    public static final DeferredItem<BlockItem> BODY_LOCKER_ITEM = blockItem(BODY_LOCKER);
    public static final DeferredItem<com.coolfreeze23.headinajar.item.SurgicalSawItem> SURGICAL_SAW = item("surgical_saw",
            () -> new com.coolfreeze23.headinajar.item.SurgicalSawItem(new Item.Properties().durability(200)));
    public static final DeferredItem<Item> EMBALMING_FLUID = item("embalming_fluid", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<Item> NEURAL_LACE = plain("neural_lace");
    public static final DeferredItem<com.coolfreeze23.headinajar.item.JarredHeadItem> JARRED_HEAD = item("jarred_head",
            () -> new com.coolfreeze23.headinajar.item.JarredHeadItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<com.coolfreeze23.headinajar.item.ShellChassisItem> LUNK_CHASSIS = item("lunk_chassis",
            () -> new com.coolfreeze23.headinajar.item.ShellChassisItem(() -> ModRegistry.LUNK.get(), new Item.Properties().stacksTo(1)));
    public static final DeferredItem<com.coolfreeze23.headinajar.item.ShellChassisItem> ROVER_CHASSIS = item("rover_chassis",
            () -> new com.coolfreeze23.headinajar.item.ShellChassisItem(() -> ModRegistry.ROVER.get(), new Item.Properties().stacksTo(1)));
    public static final DeferredItem<com.coolfreeze23.headinajar.item.ShellChassisItem> SPIDER_WALKER_CHASSIS = item("spider_walker_chassis",
            () -> new com.coolfreeze23.headinajar.item.ShellChassisItem(() -> ModRegistry.SPIDER_WALKER.get(), new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> SALINE_BOTTLE = item("saline_bottle", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<LifeSupportJarItem> LIFE_SUPPORT_JAR = item("life_support_jar",
            () -> new LifeSupportJarItem(3, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<LifeSupportJarItem> COMPACT_JAR = item("compact_life_support_jar",
            () -> new LifeSupportJarItem(3, true, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<LifeSupportJarItem> REINFORCED_JAR = item("reinforced_life_support_jar",
            () -> new LifeSupportJarItem(5, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).fireResistant()));
    public static final DeferredItem<RelayGogglesItem> RELAY_GOGGLES = item("relay_goggles",
            () -> new RelayGogglesItem(1, new Item.Properties().durability(250)));
    public static final DeferredItem<RelayGogglesItem> RELAY_GOGGLES_MK2 = item("relay_goggles_mk2",
            () -> new RelayGogglesItem(2, new Item.Properties().durability(500)));
    public static final DeferredItem<RelayGogglesItem> RELAY_GOGGLES_MK3 = item("relay_goggles_mk3",
            () -> new RelayGogglesItem(3, new Item.Properties().durability(1000).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<CollarCamItem> COLLAR_CAM = item("collar_cam", () -> new CollarCamItem(new Item.Properties().durability(250)));
    public static final DeferredItem<HeadSatchelItem> HEAD_SATCHEL = item("head_satchel", () -> new HeadSatchelItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<SutureKitItem> SUTURE_KIT = item("suture_kit", () -> new SutureKitItem(new Item.Properties().stacksTo(16)));

    // P5
    public static final DeferredItem<BlockItem> CENTRIFUGE_ITEM = blockItem(CENTRIFUGE);
    public static final DeferredItem<BlockItem> DNA_SEQUENCER_ITEM = blockItem(DNA_SEQUENCER);
    public static final DeferredItem<BlockItem> GENE_SPLICER_ITEM = blockItem(GENE_SPLICER);
    public static final DeferredItem<BlockItem> INJECTION_RIG_ITEM = blockItem(INJECTION_RIG);
    public static final DeferredItem<BlockItem> LEECH_JAR_ITEM = blockItem(LEECH_JAR);
    public static final DeferredItem<com.coolfreeze23.headinajar.item.GeneticsItems.SyringeItem> SYRINGE = item("syringe",
            () -> new com.coolfreeze23.headinajar.item.GeneticsItems.SyringeItem(com.coolfreeze23.headinajar.genetics.BloodType.WARM, 30, 60, false, false, new Item.Properties().durability(32)));
    public static final DeferredItem<com.coolfreeze23.headinajar.item.GeneticsItems.SyringeItem> PRECISION_SYRINGE = item("precision_syringe",
            () -> new com.coolfreeze23.headinajar.item.GeneticsItems.SyringeItem(com.coolfreeze23.headinajar.genetics.BloodType.WARM, 60, 90, true, false, new Item.Properties().durability(64)));
    public static final DeferredItem<com.coolfreeze23.headinajar.item.GeneticsItems.SyringeItem> LEVIATHAN_NEEDLE = item("leviathan_needle",
            () -> new com.coolfreeze23.headinajar.item.GeneticsItems.SyringeItem(com.coolfreeze23.headinajar.genetics.BloodType.WARM, 50, 80, false, true, new Item.Properties().durability(16).rarity(Rarity.RARE)));
    public static final DeferredItem<com.coolfreeze23.headinajar.item.GeneticsItems.SyringeItem> MARROW_DRILL = item("marrow_drill",
            () -> new com.coolfreeze23.headinajar.item.GeneticsItems.SyringeItem(com.coolfreeze23.headinajar.genetics.BloodType.MARROW, 40, 80, false, true, new Item.Properties().durability(64)));
    public static final DeferredItem<com.coolfreeze23.headinajar.item.GeneticsItems.SyringeItem> OIL_TAP = item("oil_tap",
            () -> new com.coolfreeze23.headinajar.item.GeneticsItems.SyringeItem(com.coolfreeze23.headinajar.genetics.BloodType.OIL, 40, 80, true, true, new Item.Properties().durability(64)));
    public static final DeferredItem<com.coolfreeze23.headinajar.item.GeneticsItems.SyringeItem> ECTO_TRAP = item("ecto_trap",
            () -> new com.coolfreeze23.headinajar.item.GeneticsItems.SyringeItem(com.coolfreeze23.headinajar.genetics.BloodType.ECTO, 40, 80, true, true, new Item.Properties().durability(64)));
    public static final DeferredItem<com.coolfreeze23.headinajar.item.GeneticsItems.SampleItem> BLOOD_SAMPLE = item("blood_sample", () -> new com.coolfreeze23.headinajar.item.GeneticsItems.SampleItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<com.coolfreeze23.headinajar.item.GeneticsItems.SampleItem> DNA_FRACTION = item("dna_fraction", () -> new com.coolfreeze23.headinajar.item.GeneticsItems.SampleItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<Item> PLASMA_VIAL = plain("plasma_vial");
    public static final DeferredItem<Item> BIO_GEL = plain("bio_gel");
    public static final DeferredItem<com.coolfreeze23.headinajar.item.GeneticsItems.SerumItem> SERUM_CASSETTE = item("serum_cassette", () -> new com.coolfreeze23.headinajar.item.GeneticsItems.SerumItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<com.coolfreeze23.headinajar.item.GeneticsItems.GeneWashItem> GENE_WASH = item("gene_wash", () -> new com.coolfreeze23.headinajar.item.GeneticsItems.GeneWashItem(new Item.Properties().stacksTo(16)));

    // P6
    public static final DeferredItem<BlockItem> TURRET_PEDESTAL_ITEM = blockItem(TURRET_PEDESTAL);
    public static final DeferredItem<BlockItem> EMPLOYEE_MOUNT_ITEM = blockItem(EMPLOYEE_MOUNT);
    public static final DeferredItem<BlockItem> CEREBRAL_ARRAY_ITEM = blockItem(CEREBRAL_ARRAY);
    public static final DeferredItem<BlockItem> NEURAL_SOCKET_ITEM = blockItem(NEURAL_SOCKET);
    public static final DeferredItem<Item> QUANTUM_KNOT = item("quantum_knot", () -> new Item(new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<LifeSupportJarItem> EXECUTIVE_JAR = item("executive_life_support_jar",
            () -> new LifeSupportJarItem(7, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredItem<RelayGogglesItem> QUANTUM_GOGGLES = item("quantum_goggles",
            () -> new RelayGogglesItem(4, new Item.Properties().durability(2000).rarity(Rarity.EPIC)));
    public static final DeferredItem<com.coolfreeze23.headinajar.item.QuantumItems.PairItem> ENTANGLED_PAIR = item("entangled_pair", () -> new com.coolfreeze23.headinajar.item.QuantumItems.PairItem(new Item.Properties().rarity(Rarity.EPIC)));
    public static final DeferredItem<com.coolfreeze23.headinajar.item.QuantumItems.ShardItem> ENTANGLED_SHARD = item("entangled_shard", () -> new com.coolfreeze23.headinajar.item.QuantumItems.ShardItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final DeferredItem<com.coolfreeze23.headinajar.form.Emp.GrenadeItem> EMP_GRENADE = item("emp_grenade",
            () -> new com.coolfreeze23.headinajar.form.Emp.GrenadeItem(new Item.Properties().stacksTo(16)));
    public static final Map<com.coolfreeze23.headinajar.item.QuantumItems.Upgrade, DeferredItem<com.coolfreeze23.headinajar.item.QuantumItems.UpgradeItem>> COLLAR_UPGRADES = new EnumMap<>(com.coolfreeze23.headinajar.item.QuantumItems.Upgrade.class);

    static {
        for (var up : com.coolfreeze23.headinajar.item.QuantumItems.Upgrade.values()) {
            COLLAR_UPGRADES.put(up, item(up.id(), () -> new com.coolfreeze23.headinajar.item.QuantumItems.UpgradeItem(up, new Item.Properties().stacksTo(16))));
        }
    }

    public static final DeferredItem<Item> BOARD_ROOM_CORE = item("board_room_core", () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static final DeferredItem<BlockItem> ASSEMBLY_GANTRY_ITEM = blockItem(ASSEMBLY_GANTRY);
    public static final DeferredItem<Item> MECHA_PLATING = plain("mecha_plating");
    public static final DeferredItem<Item> PLASMA_PELLET = plain("plasma_pellet");
    public static final Map<com.coolfreeze23.headinajar.mecha.MechaFrame, DeferredItem<com.coolfreeze23.headinajar.item.MechaFrameItem>> FRAMES =
            new EnumMap<>(com.coolfreeze23.headinajar.mecha.MechaFrame.class);
    public static final Map<com.coolfreeze23.headinajar.mecha.MechaModule, DeferredItem<com.coolfreeze23.headinajar.item.MechaModuleItem>> MECHA_MODULES =
            new EnumMap<>(com.coolfreeze23.headinajar.mecha.MechaModule.class);

    static {
        for (var frame : com.coolfreeze23.headinajar.mecha.MechaFrame.values()) {
            FRAMES.put(frame, item(frame.id() + "_frame", () -> new com.coolfreeze23.headinajar.item.MechaFrameItem(frame, new Item.Properties().stacksTo(1))));
        }
        for (var module : com.coolfreeze23.headinajar.mecha.MechaModule.values()) {
            MECHA_MODULES.put(module, item(module.id(), () -> new com.coolfreeze23.headinajar.item.MechaModuleItem(module, new Item.Properties().stacksTo(1))));
        }
    }

    public static net.minecraft.world.item.Item frameItem(com.coolfreeze23.headinajar.mecha.MechaFrame frame) {
        return FRAMES.get(frame).get();
    }

    public static net.minecraft.world.item.Item mechaModule(com.coolfreeze23.headinajar.mecha.MechaModule module) {
        return MECHA_MODULES.get(module).get();
    }

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
            () -> EntityType.Builder.<HeadJarEntity>of(HeadJarEntity::new, MobCategory.MISC).sized(0.86f, 0.95f)
                    .clientTrackingRange(10).build("head_jar"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.coolfreeze23.headinajar.entity.MissileEntity>> MISSILE = ENTITIES.register("missile",
            () -> EntityType.Builder.<com.coolfreeze23.headinajar.entity.MissileEntity>of(com.coolfreeze23.headinajar.entity.MissileEntity::new, MobCategory.MISC)
                    .sized(0.3f, 0.3f).clientTrackingRange(8).updateInterval(1).build("missile"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.coolfreeze23.headinajar.form.Emp.Grenade>> EMP_GRENADE_ENTITY = ENTITIES.register("emp_grenade",
            () -> EntityType.Builder.<com.coolfreeze23.headinajar.form.Emp.Grenade>of(com.coolfreeze23.headinajar.form.Emp.Grenade::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f).clientTrackingRange(4).updateInterval(10).build("emp_grenade"));
    public static final DeferredHolder<EntityType<?>, EntityType<SeatEntity>> SEAT = ENTITIES.register("seat",
            () -> EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC).sized(0.01f, 0.01f).noSummon()
                    .clientTrackingRange(10).build("seat"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.coolfreeze23.headinajar.entity.ShellEntity>> LUNK = ENTITIES.register("lunk",
            () -> EntityType.Builder.<com.coolfreeze23.headinajar.entity.ShellEntity>of((t, l) -> new com.coolfreeze23.headinajar.entity.ShellEntity(t, l,
                    com.coolfreeze23.headinajar.entity.ShellEntity.Kind.LUNK), MobCategory.MISC).sized(1.4f, 2.7f).clientTrackingRange(10).build("lunk"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.coolfreeze23.headinajar.entity.ShellEntity>> ROVER = ENTITIES.register("rover",
            () -> EntityType.Builder.<com.coolfreeze23.headinajar.entity.ShellEntity>of((t, l) -> new com.coolfreeze23.headinajar.entity.ShellEntity(t, l,
                    com.coolfreeze23.headinajar.entity.ShellEntity.Kind.ROVER), MobCategory.MISC).sized(1.2f, 0.9f).clientTrackingRange(10).build("rover"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.coolfreeze23.headinajar.entity.ShellEntity>> SPIDER_WALKER = ENTITIES.register("spider_walker",
            () -> EntityType.Builder.<com.coolfreeze23.headinajar.entity.ShellEntity>of((t, l) -> new com.coolfreeze23.headinajar.entity.ShellEntity(t, l,
                    com.coolfreeze23.headinajar.entity.ShellEntity.Kind.SPIDER_WALKER), MobCategory.MISC).sized(1.4f, 0.9f).clientTrackingRange(10).build("spider_walker"));

    public static final Map<com.coolfreeze23.headinajar.mecha.MechaFrame, DeferredHolder<EntityType<?>, EntityType<com.coolfreeze23.headinajar.entity.MechaEntity>>> MECHA =
            new EnumMap<>(com.coolfreeze23.headinajar.mecha.MechaFrame.class);

    static {
        for (var frame : com.coolfreeze23.headinajar.mecha.MechaFrame.values()) {
            MECHA.put(frame, ENTITIES.register(frame.id(), () -> EntityType.Builder.<com.coolfreeze23.headinajar.entity.MechaEntity>of(
                    (t, l) -> new com.coolfreeze23.headinajar.entity.MechaEntity(t, l, frame), MobCategory.MISC)
                    .sized(frame.width, frame.height).clientTrackingRange(10).build(frame.id())));
        }
    }

    public static final DeferredHolder<EntityType<?>, EntityType<com.coolfreeze23.headinajar.entity.MechaEntity>> HOPPER = MECHA.get(com.coolfreeze23.headinajar.mecha.MechaFrame.HOPPER);
    public static final DeferredHolder<EntityType<?>, EntityType<com.coolfreeze23.headinajar.entity.MechaEntity>> BULWARK = MECHA.get(com.coolfreeze23.headinajar.mecha.MechaFrame.BULWARK);
    public static final DeferredHolder<EntityType<?>, EntityType<com.coolfreeze23.headinajar.entity.MechaEntity>> CHIMERA = MECHA.get(com.coolfreeze23.headinajar.mecha.MechaFrame.CHIMERA);

    public static EntityType<com.coolfreeze23.headinajar.entity.MechaEntity> mechaType(com.coolfreeze23.headinajar.mecha.MechaFrame frame) {
        return MECHA.get(frame).get();
    }

    // --- block entities ----------------------------------------------------------------------
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SeparationChairBlockEntity>> CHAIR_BE = BLOCK_ENTITIES.register("separation_chair",
            () -> BlockEntityType.Builder.of(SeparationChairBlockEntity::new, SEPARATION_CHAIR.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LifeSupportCradleBlockEntity>> CRADLE_BE = BLOCK_ENTITIES.register("life_support_cradle",
            () -> BlockEntityType.Builder.of(LifeSupportCradleBlockEntity::new, CRADLE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GalvanicPileBlockEntity>> PILE_BE = BLOCK_ENTITIES.register("galvanic_pile",
            () -> BlockEntityType.Builder.of(GalvanicPileBlockEntity::new, GALVANIC_PILE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChargingDockBlock.Entity>> DOCK_BE = BLOCK_ENTITIES.register("charging_dock",
            () -> BlockEntityType.Builder.of(ChargingDockBlock.Entity::new, CHARGING_DOCK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.coolfreeze23.headinajar.block.SalineMixerBlockEntity>> MIXER_BE = BLOCK_ENTITIES.register("saline_mixer",
            () -> BlockEntityType.Builder.of(com.coolfreeze23.headinajar.block.SalineMixerBlockEntity::new, SALINE_MIXER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.coolfreeze23.headinajar.block.BodyPrinterBlockEntity>> PRINTER_BE = BLOCK_ENTITIES.register("body_printer",
            () -> BlockEntityType.Builder.of(com.coolfreeze23.headinajar.block.BodyPrinterBlockEntity::new, BODY_PRINTER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.coolfreeze23.headinajar.block.PowerBlockEntities.LeydenJar>> LEYDEN_BE = BLOCK_ENTITIES.register("leyden_jar",
            () -> BlockEntityType.Builder.of(com.coolfreeze23.headinajar.block.PowerBlockEntities.LeydenJar::new, LEYDEN_JAR.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.coolfreeze23.headinajar.block.PowerBlockEntities.TeslaNode>> TESLA_BE = BLOCK_ENTITIES.register("tesla_node",
            () -> BlockEntityType.Builder.of(com.coolfreeze23.headinajar.block.PowerBlockEntities.TeslaNode::new, TESLA_NODE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.coolfreeze23.headinajar.block.PowerBlockEntities.Station>> MAST_BE = BLOCK_ENTITIES.register("relay_mast",
            () -> BlockEntityType.Builder.of(com.coolfreeze23.headinajar.block.PowerBlockEntities.Station::mast, RELAY_MAST.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.coolfreeze23.headinajar.block.PowerBlockEntities.Station>> MAST2_BE = BLOCK_ENTITIES.register("relay_mast_mk2",
            () -> BlockEntityType.Builder.of(com.coolfreeze23.headinajar.block.PowerBlockEntities.Station::mast2, RELAY_MAST_MK2.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.coolfreeze23.headinajar.block.PowerBlockEntities.Station>> JAMMER_BE = BLOCK_ENTITIES.register("signal_jammer",
            () -> BlockEntityType.Builder.of(com.coolfreeze23.headinajar.block.PowerBlockEntities.Station::jammer, SIGNAL_JAMMER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.coolfreeze23.headinajar.block.JarredHeadBlockEntity>> JARRED_HEAD_BE = BLOCK_ENTITIES.register("jarred_head",
            () -> BlockEntityType.Builder.of(com.coolfreeze23.headinajar.block.JarredHeadBlockEntity::new, JARRED_HEAD_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.coolfreeze23.headinajar.block.BodyLockerBlockEntity>> LOCKER_BE = BLOCK_ENTITIES.register("body_locker",
            () -> BlockEntityType.Builder.of(com.coolfreeze23.headinajar.block.BodyLockerBlockEntity::new, BODY_LOCKER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.coolfreeze23.headinajar.block.AssemblyGantryBlockEntity>> GANTRY_BE = BLOCK_ENTITIES.register("assembly_gantry",
            () -> BlockEntityType.Builder.of(com.coolfreeze23.headinajar.block.AssemblyGantryBlockEntity::new, ASSEMBLY_GANTRY.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.coolfreeze23.headinajar.block.LabMachines.Centrifuge>> CENTRIFUGE_BE = BLOCK_ENTITIES.register("centrifuge",
            () -> BlockEntityType.Builder.of(com.coolfreeze23.headinajar.block.LabMachines.Centrifuge::new, CENTRIFUGE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.coolfreeze23.headinajar.block.LabMachines.Sequencer>> SEQUENCER_BE = BLOCK_ENTITIES.register("dna_sequencer",
            () -> BlockEntityType.Builder.of(com.coolfreeze23.headinajar.block.LabMachines.Sequencer::new, DNA_SEQUENCER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.coolfreeze23.headinajar.block.LabMachines.Splicer>> SPLICER_BE = BLOCK_ENTITIES.register("gene_splicer",
            () -> BlockEntityType.Builder.of(com.coolfreeze23.headinajar.block.LabMachines.Splicer::new, GENE_SPLICER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.coolfreeze23.headinajar.block.LabMachines.InjectionRig>> RIG_BE = BLOCK_ENTITIES.register("injection_rig",
            () -> BlockEntityType.Builder.of(com.coolfreeze23.headinajar.block.LabMachines.InjectionRig::new, INJECTION_RIG.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.coolfreeze23.headinajar.block.LeechJarBlockEntity>> LEECH_JAR_BE = BLOCK_ENTITIES.register("leech_jar",
            () -> BlockEntityType.Builder.of(com.coolfreeze23.headinajar.block.LeechJarBlockEntity::new, LEECH_JAR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity>> TURRET_BE = BLOCK_ENTITIES.register("turret_pedestal",
            () -> BlockEntityType.Builder.of(com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity::new, TURRET_PEDESTAL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.coolfreeze23.headinajar.block.EmployeeMountBlockEntity>> MOUNT_BE = BLOCK_ENTITIES.register("employee_mount",
            () -> BlockEntityType.Builder.of(com.coolfreeze23.headinajar.block.EmployeeMountBlockEntity::new, EMPLOYEE_MOUNT.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity>> ARRAY_BE = BLOCK_ENTITIES.register("cerebral_array",
            () -> BlockEntityType.Builder.of(com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity::new, CEREBRAL_ARRAY.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.coolfreeze23.headinajar.block.NeuralSocketBlockEntity>> SOCKET_BE = BLOCK_ENTITIES.register("neural_socket",
            () -> BlockEntityType.Builder.of(com.coolfreeze23.headinajar.block.NeuralSocketBlockEntity::new, NEURAL_SOCKET.get()).build(null));

    // --- menus -------------------------------------------------------------------------------
    public static final DeferredHolder<MenuType<?>, MenuType<JarMenu>> JAR_MENU = MENUS.register("jar",
            () -> new MenuType<>(JarMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>, MenuType<SeparationChairMenu>> CHAIR_MENU = MENUS.register("separation_chair",
            () -> IMenuTypeExtension.create(SeparationChairMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<com.coolfreeze23.headinajar.menu.SalineMixerMenu>> MIXER_MENU = MENUS.register("saline_mixer",
            () -> IMenuTypeExtension.create(com.coolfreeze23.headinajar.menu.SalineMixerMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<com.coolfreeze23.headinajar.menu.BodyPrinterMenu>> PRINTER_MENU = MENUS.register("body_printer",
            () -> IMenuTypeExtension.create(com.coolfreeze23.headinajar.menu.BodyPrinterMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<com.coolfreeze23.headinajar.menu.AssemblyGantryMenu>> GANTRY_MENU = MENUS.register("assembly_gantry",
            () -> IMenuTypeExtension.create(com.coolfreeze23.headinajar.menu.AssemblyGantryMenu::new));

    public static final Map<com.coolfreeze23.headinajar.menu.LabMenu.Kind, DeferredHolder<MenuType<?>, MenuType<com.coolfreeze23.headinajar.menu.LabMenu>>> LAB_MENUS =
            new EnumMap<>(com.coolfreeze23.headinajar.menu.LabMenu.Kind.class);

    static {
        for (var kind : com.coolfreeze23.headinajar.menu.LabMenu.Kind.values()) {
            LAB_MENUS.put(kind, MENUS.register(kind.id(), () -> IMenuTypeExtension.create((id, inv, buf) -> new com.coolfreeze23.headinajar.menu.LabMenu(kind, id, inv, buf))));
        }
    }

    public static final Supplier<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.headinajar"))
            .icon(() -> new ItemStack(LIFE_SUPPORT_JAR.get()))
            .displayItems((params, out) -> {
                for (DeferredItem<? extends Item> item : TAB_ORDER) out.accept(item.get());
                for (ModFluids.Set fluid : ModFluids.ALL) out.accept(fluid.bucket.get());
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
        RECIPE_SERIALIZERS.register(bus);
        ModFluids.register(bus);
        bus.addListener(ModRegistry::attributes);
        bus.addListener(ModRegistry::capabilities);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(HEADLESS_BODY.get(), HeadlessBody.createAttributes().build());
        event.put(LUNK.get(), com.coolfreeze23.headinajar.entity.ShellEntity.lunk().build());
        event.put(ROVER.get(), com.coolfreeze23.headinajar.entity.ShellEntity.rover().build());
        event.put(SPIDER_WALKER.get(), com.coolfreeze23.headinajar.entity.ShellEntity.spiderWalker().build());
        for (var type : MECHA.values()) event.put(type.get(), com.coolfreeze23.headinajar.entity.MechaEntity.createAttributes().build());
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, CHAIR_BE.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CHAIR_BE.get(), (be, side) -> be.items());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, PILE_BE.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PILE_BE.get(), (be, side) -> com.coolfreeze23.headinajar.block.MachineItems.sided(be.fuel(), com.coolfreeze23.headinajar.block.MachineItems.all(1, com.coolfreeze23.headinajar.block.MachineItems.Role.INPUT), side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, DOCK_BE.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, CRADLE_BE.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, LOCKER_BE.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, GANTRY_BE.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, GANTRY_BE.get(), (be, side) -> be.items());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CRADLE_BE.get(), (be, side) -> be.saline());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, MIXER_BE.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, MIXER_BE.get(), (be, side) -> com.coolfreeze23.headinajar.block.MachineItems.sided(be.items(), com.coolfreeze23.headinajar.block.SalineMixerBlockEntity.ROLES, side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, MIXER_BE.get(), (be, side) -> be.pipes());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, PRINTER_BE.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PRINTER_BE.get(), (be, side) -> com.coolfreeze23.headinajar.block.MachineItems.sided(be.items(), com.coolfreeze23.headinajar.block.BodyPrinterBlockEntity.ROLES, side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, PRINTER_BE.get(), (be, side) -> be.saline());
        for (var type : java.util.List.of(LEYDEN_BE.get(), TESLA_BE.get())) {
            event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, type, (be, side) -> ((com.coolfreeze23.headinajar.block.PowerBlockEntities.Powered) be).energy());
        }
        for (var type : java.util.List.of(MAST_BE.get(), MAST2_BE.get(), JAMMER_BE.get())) {
            event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, type, (be, side) -> be.energy());
        }
        for (var type : java.util.List.of(CENTRIFUGE_BE.get(), SEQUENCER_BE.get(), SPLICER_BE.get(), RIG_BE.get())) {
            event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, type, (be, side) -> ((com.coolfreeze23.headinajar.block.LabMachine) be).energy());
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, (be, side) -> ((com.coolfreeze23.headinajar.block.LabMachine) be).automation(side));
        }
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, LEECH_JAR_BE.get(), (be, side) -> com.coolfreeze23.headinajar.block.MachineItems.sided(be.items(), com.coolfreeze23.headinajar.block.MachineItems.all(9, com.coolfreeze23.headinajar.block.MachineItems.Role.OUTPUT), side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, TURRET_BE.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ARRAY_BE.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ARRAY_BE.get(), (be, side) -> be.automation(side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ARRAY_BE.get(), (be, side) -> be.broth());
    }

    private ModRegistry() {
    }
}
