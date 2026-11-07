package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

@Mod(value = HeadInAJar.MOD_ID, dist = Dist.CLIENT)
public class ClientSetup {
    public static final String CATEGORY = "key.categories.headinajar";
    public static final KeyMapping JAR_MENU = new KeyMapping("key.headinajar.jar_menu", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);
    public static final KeyMapping ABILITY = new KeyMapping("key.headinajar.ability", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);

    public ClientSetup(IEventBus modBus, ModContainer container) {
        modBus.addListener(ClientSetup::renderers);
        modBus.addListener(ClientSetup::layers);
        modBus.addListener(ClientSetup::screens);
        modBus.addListener(ClientSetup::keys);
        modBus.addListener(ClientSetup::gui);
        NeoForge.EVENT_BUS.register(ClientEvents.class);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModRegistry.HEADLESS_BODY.get(), HeadlessBodyRenderer::new);
        event.registerEntityRenderer(ModRegistry.HELD_BLOCK.get(), HeldBlockRenderer::new);
    }

    private static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(JarModel.JAR, JarModel::jar);
        event.registerLayerDefinition(JarModel.HEAD, JarModel::head);
        event.registerLayerDefinition(JarModel.STUMP, JarModel::stump);
    }

    private static void screens(RegisterMenuScreensEvent event) {
        event.register(ModRegistry.JAR_MENU.get(), JarScreen::new);
    }

    private static void keys(RegisterKeyMappingsEvent event) {
        event.register(JAR_MENU);
        event.register(ABILITY);
    }

    private static void gui(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.FOOD_LEVEL, HeadInAJar.id("charge"), ChargeHud::render);
    }
}
