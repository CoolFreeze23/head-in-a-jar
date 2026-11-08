package com.coolfreeze23.headinajar.registry;

import com.coolfreeze23.headinajar.HeadInAJar;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.minecraft.core.registries.Registries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** The mod's fluids. Each one has a type, a source and flowing fluid, a block, a bucket, and a tint over vanilla water. */
public final class ModFluids {
    public static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, HeadInAJar.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, HeadInAJar.MOD_ID);
    public static final List<Set> ALL = new ArrayList<>();

    /** One fluid with everything it needs. */
    public static final class Set {
        public final String name;
        public final int tint;
        public final DeferredHolder<FluidType, FluidType> type;
        public final DeferredHolder<Fluid, BaseFlowingFluid.Source> source;
        public final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing;
        public final DeferredBlock<LiquidBlock> block;
        public final DeferredItem<BucketItem> bucket;

        Set(String name, int tint, int density, int viscosity) {
            this.name = name;
            this.tint = tint;
            this.type = TYPES.register(name, () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("fluid_type.headinajar." + name).density(density).viscosity(viscosity).canSwim(true).canDrown(true)));
            Supplier<BaseFlowingFluid.Properties> props = () -> new BaseFlowingFluid.Properties(type, this::source, this::flowing)
                    .bucket(this::bucketItem).block(this::liquidBlock).slopeFindDistance(3).levelDecreasePerBlock(2);
            this.source = FLUIDS.register(name, () -> new BaseFlowingFluid.Source(props.get()));
            this.flowing = FLUIDS.register(name + "_flowing", () -> new BaseFlowingFluid.Flowing(props.get()));
            this.block = ModRegistry.BLOCKS.register(name, () -> new LiquidBlock(source.get(), BlockBehaviour.Properties.ofFullCopy(Blocks.WATER)));
            this.bucket = ModRegistry.ITEMS.register(name + "_bucket", () -> new BucketItem(source.get(),
                    new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
            ALL.add(this);
        }

        private FlowingFluid source() {
            return source.get();
        }

        private FlowingFluid flowing() {
            return flowing.get();
        }

        private Item bucketItem() {
            return bucket.get();
        }

        private LiquidBlock liquidBlock() {
            return block.get();
        }
    }

    public static final Set SALINE = new Set("cranial_saline", 0xFF5FC8C0, 1020, 1100);

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        FLUIDS.register(bus);
    }

    private ModFluids() {
    }
}
