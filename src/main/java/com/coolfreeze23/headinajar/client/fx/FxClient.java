package com.coolfreeze23.headinajar.client.fx;

import com.coolfreeze23.headinajar.fx.FxParticles;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Wires the effect kit into the client: sprite sets, the shell layer, the per-tick services and the beam pass. */
public final class FxClient {
    public static void init(IEventBus modBus) {
        modBus.addListener(FxClient::particles);
        modBus.addListener(FxClient::layers);
        NeoForge.EVENT_BUS.addListener(FxClient::tick);
        NeoForge.EVENT_BUS.addListener(SoundLoops::heard);
        NeoForge.EVENT_BUS.addListener(FxClient::render);
        NeoForge.EVENT_BUS.register(CameraDesk.class);
        NeoForge.EVENT_BUS.register(PoseFx.class);
    }

    private static void particles(RegisterParticleProvidersEvent event) {
        sprites(event, FxParticles.MOTE.get(), FxParticle.Look.MOTE);
        sprites(event, FxParticles.SPARK.get(), FxParticle.Look.SPARK);
        sprites(event, FxParticles.BUBBLE.get(), FxParticle.Look.BUBBLE, FxParticle.Look.DROPLET);
        sprites(event, FxParticles.PUFF.get(), FxParticle.Look.PUFF);
        sprites(event, FxParticles.RING.get(), FxParticle.Look.RING);
        sprites(event, FxParticles.STATIC.get(), FxParticle.Look.STATIC);
    }

    private static void sprites(RegisterParticleProvidersEvent event, net.minecraft.core.particles.SimpleParticleType type, FxParticle.Look... kinds) {
        event.registerSpriteSet(type, set -> {
            for (FxParticle.Look k : kinds) k.set = set;
            return new FxParticle.Provider(kinds[0]);
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void layers(EntityRenderersEvent.AddLayers event) {
        for (EntityType<?> type : event.getEntityTypes()) {
            if (event.getRenderer(type) instanceof LivingEntityRenderer living) living.addLayer(new ShellLayer(living));
        }
        for (var skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof LivingEntityRenderer living) living.addLayer(new ShellLayer(living));
        }
    }

    private static void tick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().isPaused()) return;
        FxManager.tick();
        TetherFx.tick();
        SwapFx.tick();
        PoseFx.tick();
        BlockBeams.tick();
        CameraDesk.tick();
        JarSounds.tick();
        ArmBeams.tick();
        com.coolfreeze23.headinajar.client.MechaWear.tick();
        SoundLoops.tick();
    }

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        PoseStack pose = event.getPoseStack() == null ? new PoseStack() : event.getPoseStack();
        var frame = new Beams.Frame(pose, mc.renderBuffers().bufferSource(), event.getCamera().getPosition(), event.getFrustum(), pt,
                mc.level.getGameTime() + pt);
        TetherFx.render(frame);
        FxManager.render(frame);
        SwapFx.render(frame);
        BlockBeams.render(frame);
        ArmBeams.render(frame);
    }

    private FxClient() {
    }
}
