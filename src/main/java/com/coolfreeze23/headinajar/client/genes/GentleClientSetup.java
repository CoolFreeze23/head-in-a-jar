package com.coolfreeze23.headinajar.client.genes;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.genetics.GentleGenes;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/** Wires the gentler genes into the client at start-up: Keen Nose's scent sprites and the fleece layer. */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class GentleClientSetup {
    private GentleClientSetup() {
    }

    @SubscribeEvent
    public static void particles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(GentleGenes.SCENT.get(), ScentParticle.Provider::new);
    }

    /** The fleece on every creature's renderer (a host may be any of them) and on both player skins. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @SubscribeEvent
    public static void layers(EntityRenderersEvent.AddLayers event) {
        for (EntityType<?> type : event.getEntityTypes()) {
            if (event.getRenderer(type) instanceof LivingEntityRenderer living) living.addLayer(new FleeceLayer(living, null));
        }
        for (var skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer player) {
                player.addLayer(new FleeceLayer(player, new HumanoidModel<>(event.getContext().bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR))));
            }
        }
    }
}
