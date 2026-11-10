package com.coolfreeze23.headinajar.smoke;

import com.coolfreeze23.headinajar.HeadInAJar;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Smoke-only GeckoLib probe: one block whose entity plays the probe model's "cut" animation on a loop.
 * Place it with /setblock X Y Z headinajar:gecko_probe. It never ships: it lives in the smoke source set.
 */
public final class GeckoProbe {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HeadInAJar.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, HeadInAJar.MOD_ID);

    public static final DeferredBlock<ProbeBlock> BLOCK = BLOCKS.register("gecko_probe",
            () -> new ProbeBlock(BlockBehaviour.Properties.of().noOcclusion().strength(1f)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProbeEntity>> BE = BLOCK_ENTITIES.register("gecko_probe",
            () -> BlockEntityType.Builder.of(ProbeEntity::new, BLOCK.get()).build(null));

    private GeckoProbe() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(GeckoProbe::renderers);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BE.get(), context -> new GeoBlockRenderer<>(
                new DefaultedBlockGeoModel<ProbeEntity>(ResourceLocation.fromNamespaceAndPath(HeadInAJar.MOD_ID, "geckoprobe"))));
    }

    public static final class ProbeBlock extends Block implements EntityBlock {
        public ProbeBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected RenderShape getRenderShape(BlockState state) {
            return RenderShape.ENTITYBLOCK_ANIMATED;
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new ProbeEntity(pos, state);
        }
    }

    public static final class ProbeEntity extends BlockEntity implements GeoBlockEntity {
        private static final RawAnimation CUT = RawAnimation.begin().thenLoop("animation.probe.cut");
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

        public ProbeEntity(BlockPos pos, BlockState state) {
            super(BE.get(), pos, state);
        }

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
            controllers.add(new AnimationController<>(this, "cut", 0, state -> state.setAndContinue(CUT)));
        }

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache() {
            return cache;
        }
    }
}
