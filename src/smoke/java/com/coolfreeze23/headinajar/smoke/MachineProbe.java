package com.coolfreeze23.headinajar.smoke;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Smoke-only render-cost probes for animated machines (assets from tools/machine_probes.py). Never shipped.
 * <ul>
 * <li>anim_probe_a / anim_probe_b: baked models with a 4-frame animated texture (is it animated under Sodium and Iris?).</li>
 * <li>lamp_probe[lamp=0..5]: a 48-element baked model; "flip" cycles its lamp value on the server to cost chunk rebuilds.</li>
 * <li>gecko_loop / gecko_stop / gecko_skip: an 8-bone GeckoLib model playing a loop, stopped, or not drawn at all.</li>
 * </ul>
 * Verbs: "perf NAME TICKS" records frame times without capturing (and lifts the frame cap);
 * "flip sync|stagger|off PERIOD X1 Y1 Z1 X2 Y2 Z2" cycles every lamp_probe in the box, all in one tick every PERIOD
 * ticks, or each once per PERIOD spread over the ticks.
 */
public final class MachineProbe {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HeadInAJar.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, HeadInAJar.MOD_ID);

    public static final IntegerProperty LAMP = IntegerProperty.create("lamp", 0, 5);

    public static final DeferredBlock<Block> ANIM_A = BLOCKS.register("anim_probe_a",
            () -> new Block(BlockBehaviour.Properties.of().strength(1f)));
    public static final DeferredBlock<SlabBlock> ANIM_B = BLOCKS.register("anim_probe_b",
            () -> new SlabBlock(BlockBehaviour.Properties.of().strength(1f)));
    public static final DeferredBlock<LampBlock> LAMP_BLOCK = BLOCKS.register("lamp_probe",
            () -> new LampBlock(BlockBehaviour.Properties.of().noOcclusion().strength(1f)));
    public static final DeferredBlock<GeoProbeBlock> LOOP = BLOCKS.register("gecko_loop",
            () -> new GeoProbeBlock(0, BlockBehaviour.Properties.of().noOcclusion().strength(1f)));
    public static final DeferredBlock<GeoProbeBlock> STOP = BLOCKS.register("gecko_stop",
            () -> new GeoProbeBlock(1, BlockBehaviour.Properties.of().noOcclusion().strength(1f)));
    public static final DeferredBlock<GeoProbeBlock> SKIP = BLOCKS.register("gecko_skip",
            () -> new GeoProbeBlock(2, BlockBehaviour.Properties.of().noOcclusion().strength(1f)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GeoProbeEntity>> BE = BLOCK_ENTITIES.register("gecko_machine_probe",
            () -> BlockEntityType.Builder.of(GeoProbeEntity::new, LOOP.get(), STOP.get(), SKIP.get()).build(null));

    private MachineProbe() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(MachineProbe::renderers);
        NeoForge.EVENT_BUS.addListener(MachineProbe::serverTick);
        NeoForge.EVENT_BUS.addListener(PERF::onFrame);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BE.get(), context -> new ProbeRenderer());
    }

    // ---- lamp flipping, on the server thread ----

    private static final List<BlockPos> FLIP = new ArrayList<>();
    private static volatile String flipMode = "off";
    private static volatile int flipPeriod = 20;
    private static long flipTick;
    static int flips;

    /** Called on the client thread; the scan and the flipping run on the server thread. */
    static String flip(MinecraftServer server, String[] a) {
        String mode = a[0];
        if (mode.equals("off")) {
            flipMode = "off";
            return "flip off after " + flips + " block changes";
        }
        int period = Integer.parseInt(a[1]);
        int[] c = Arrays.stream(a, 2, 8).mapToInt(Integer::parseInt).toArray();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            FLIP.clear();
            for (BlockPos p : BlockPos.betweenClosed(Math.min(c[0], c[3]), Math.min(c[1], c[4]), Math.min(c[2], c[5]),
                    Math.max(c[0], c[3]), Math.max(c[1], c[4]), Math.max(c[2], c[5]))) {
                if (level.getBlockState(p).is(LAMP_BLOCK.get())) FLIP.add(p.immutable());
            }
            flips = 0;
            flipTick = 0;
            flipPeriod = period;
            flipMode = mode;
        });
        return "flip " + mode + " every " + period + " ticks";
    }

    static int flipCount() {
        return FLIP.size();
    }

    private static void serverTick(ServerTickEvent.Post event) {
        if (flipMode.equals("off") || FLIP.isEmpty()) return;
        ServerLevel level = event.getServer().overworld();
        long t = flipTick++;
        boolean sync = flipMode.equals("sync");
        for (int i = 0; i < FLIP.size(); i++) {
            boolean due = sync ? t % flipPeriod == 0 : (t + (long) i * flipPeriod / FLIP.size()) % flipPeriod == 0;
            if (!due) continue;
            BlockPos p = FLIP.get(i);
            BlockState s = level.getBlockState(p);
            if (!s.is(LAMP_BLOCK.get())) continue;
            level.setBlock(p, s.setValue(LAMP, (s.getValue(LAMP) + 1) % 6), Block.UPDATE_ALL);
            flips++;
        }
    }

    // ---- frame timing ----

    static final Perf PERF = new Perf();

    static final class Perf {
        private String name;
        private int ticksLeft;
        private long last;
        private final List<Long> gaps = new ArrayList<>();
        private List<String> report;

        void start(List<String> report, String name, int ticks) {
            Minecraft mc = Minecraft.getInstance();
            mc.options.framerateLimit().set(260);
            mc.options.enableVsync().set(false);
            mc.getWindow().updateVsync(false);
            this.report = report;
            this.name = name;
            this.ticksLeft = ticks;
            this.last = 0;
            this.gaps.clear();
        }

        void onFrame(RenderFrameEvent.Pre event) {
            if (name == null) return;
            long now = System.nanoTime();
            if (last != 0) gaps.add(now - last);
            last = now;
        }

        void tick() {
            if (name != null && --ticksLeft <= 0) stop();
        }

        void stop() {
            if (name == null) return;
            long[] g = gaps.stream().mapToLong(Long::longValue).sorted().toArray();
            if (g.length < 10) {
                report.add("perf " + name + " -> too few frames (" + g.length + ")");
            } else {
                double sum = 0;
                for (long v : g) sum += v;
                double mean = sum / g.length / 1e6;
                double p50 = g[g.length / 2] / 1e6, p95 = g[(int) (g.length * 0.95)] / 1e6, p99 = g[(int) (g.length * 0.99)] / 1e6;
                double max = g[g.length - 1] / 1e6;
                int spikes = 0;
                for (long v : g) if (v > 2 * g[g.length / 2]) spikes++;
                report.add(String.format(Locale.ROOT,
                        "perf %s -> %d frames, mean %.2f ms (%.1f fps), p50 %.2f, p95 %.2f, p99 %.2f, max %.2f ms, %d frames over 2x median",
                        name, g.length, mean, 1000 / mean, p50, p95, p99, max, spikes));
            }
            name = null;
        }
    }

    // ---- blocks and the GeckoLib probe ----

    public static final class LampBlock extends Block {
        public LampBlock(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(LAMP, 1));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(LAMP);
        }
    }

    public static final class GeoProbeBlock extends Block implements EntityBlock {
        final int mode;

        public GeoProbeBlock(int mode, Properties properties) {
            super(properties);
            this.mode = mode;
        }

        @Override
        protected RenderShape getRenderShape(BlockState state) {
            return RenderShape.ENTITYBLOCK_ANIMATED;
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new GeoProbeEntity(pos, state);
        }
    }

    public static final class GeoProbeEntity extends BlockEntity implements GeoBlockEntity {
        private static final RawAnimation LOOP_CLIP = RawAnimation.begin().thenLoop("animation.machprobe.loop");
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

        public GeoProbeEntity(BlockPos pos, BlockState state) {
            super(BE.get(), pos, state);
        }

        int mode() {
            return getBlockState().getBlock() instanceof GeoProbeBlock b ? b.mode : 0;
        }

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
            controllers.add(new AnimationController<>(this, "main", 0,
                    state -> mode() == 0 ? state.setAndContinue(LOOP_CLIP) : PlayState.STOP));
        }

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache() {
            return cache;
        }
    }

    static final class ProbeRenderer extends GeoBlockRenderer<GeoProbeEntity> {
        ProbeRenderer() {
            super(new DefaultedBlockGeoModel<>(ResourceLocation.fromNamespaceAndPath(HeadInAJar.MOD_ID, "machprobe")));
        }

        @Override
        public void render(GeoProbeEntity animatable, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
                           int packedLight, int packedOverlay) {
            if (animatable.mode() == 2) return;
            super.render(animatable, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        }
    }
}
