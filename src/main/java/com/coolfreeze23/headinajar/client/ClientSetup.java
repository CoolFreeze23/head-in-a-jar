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
    public static final KeyMapping SWAP = new KeyMapping("key.headinajar.swap", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);
    public static final KeyMapping SECONDARY = new KeyMapping("key.headinajar.secondary", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
    /** Collar Cam Zoom Optic: hold for x3 zoom while linked. */
    public static final KeyMapping ZOOM = new KeyMapping("key.headinajar.zoom", org.lwjgl.glfw.GLFW.GLFW_KEY_C, "key.categories.headinajar");
    public static final KeyMapping ABILITY = new KeyMapping("key.headinajar.ability", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);

    public ClientSetup(IEventBus modBus, ModContainer container) {
        modBus.addListener(ClientSetup::renderers);
        modBus.addListener(ClientSetup::layers);
        modBus.addListener(ClientSetup::screens);
        modBus.addListener(ClientSetup::keys);
        modBus.addListener(ClientSetup::gui);
        modBus.addListener(ClientSetup::playerLayers);
        modBus.addListener(ClientSetup::fluids);
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent e) -> e.enqueueWork(() -> {
            for (var fluid : com.coolfreeze23.headinajar.registry.ModFluids.ALL) {
                net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(fluid.source.get(), net.minecraft.client.renderer.RenderType.translucent());
                net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(fluid.flowing.get(), net.minecraft.client.renderer.RenderType.translucent());
            }
        }));
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, com.coolfreeze23.headinajar.Config.CLIENT_SPEC);
        NeoForge.EVENT_BUS.register(ClientEvents.class);
        NeoForge.EVENT_BUS.register(SocketClient.class);
        NeoForge.EVENT_BUS.addListener(PilotClient::cameraDistance);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModRegistry.HEADLESS_BODY.get(), HeadlessBodyRenderer::new);
        event.registerEntityRenderer(ModRegistry.HELD_BLOCK.get(), HeldBlockRenderer::new);
        event.registerEntityRenderer(ModRegistry.HEAD_JAR.get(), HeadJarRenderer::new);
        event.registerEntityRenderer(ModRegistry.SEAT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(ModRegistry.MISSILE.get(), net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
        event.registerEntityRenderer(ModRegistry.EMP_GRENADE_ENTITY.get(), net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
        event.registerBlockEntityRenderer(ModRegistry.CRADLE_BE.get(), CradleRenderer::new);
        event.registerBlockEntityRenderer(ModRegistry.JARRED_HEAD_BE.get(), JarredHeadRenderer::new);
        event.registerBlockEntityRenderer(ModRegistry.TURRET_BE.get(), HeadWorkRenderers.Turret::new);
        event.registerBlockEntityRenderer(ModRegistry.MOUNT_BE.get(), HeadWorkRenderers.Mount::new);
        event.registerBlockEntityRenderer(ModRegistry.ARRAY_BE.get(), HeadWorkRenderers.Array::new);
        event.registerBlockEntityRenderer(ModRegistry.SOCKET_BE.get(), HeadWorkRenderers.Socket::new);
        for (var frame : com.coolfreeze23.headinajar.mecha.MechaFrame.values()) {
            event.registerEntityRenderer(ModRegistry.mechaType(frame), c -> new MechaRenderer(c, frame));
        }
        event.registerEntityRenderer(ModRegistry.LUNK.get(), c -> new ShellRenderer(c, ShellRenderer.LUNK, "lunk", 0.8f));
        event.registerEntityRenderer(ModRegistry.ROVER.get(), c -> new ShellRenderer(c, ShellRenderer.ROVER, "rover", 0.7f));
        event.registerEntityRenderer(ModRegistry.SPIDER_WALKER.get(), c -> new ShellRenderer(c, ShellRenderer.SPIDER, "spider_walker", 0.8f));
    }

    private static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(JarModel.JAR, JarModel::jar);
        event.registerLayerDefinition(JarModel.HEAD, JarModel::head);
        event.registerLayerDefinition(JarModel.STUMP, JarModel::stump);
        event.registerLayerDefinition(JarModel.GOGGLES, JarModel::goggles);
        event.registerLayerDefinition(JarModel.COLLAR, JarModel::collar);
        event.registerLayerDefinition(ShellRenderer.LUNK, ShellRenderer::lunk);
        event.registerLayerDefinition(MechaRenderer.HOPPER, MechaRenderer::hopper);
        event.registerLayerDefinition(MechaRenderer.BULWARK, MechaRenderer::bulwark);
        event.registerLayerDefinition(MechaRenderer.WHEELHOUSE, MechaRenderer::wheelhouse);
        event.registerLayerDefinition(MechaRenderer.SKITTER, MechaRenderer::skitter);
        event.registerLayerDefinition(MechaRenderer.KITE, MechaRenderer::kite);
        event.registerLayerDefinition(MechaRenderer.MODULES, MechaRenderer::modules);
        event.registerLayerDefinition(ShellRenderer.ROVER, ShellRenderer::rover);
        event.registerLayerDefinition(ShellRenderer.SPIDER, ShellRenderer::spiderWalker);
    }

    private static void screens(RegisterMenuScreensEvent event) {
        event.register(ModRegistry.JAR_MENU.get(), JarScreen::new);
        event.register(ModRegistry.CHAIR_MENU.get(), SeparationChairScreen::new);
        event.register(ModRegistry.GANTRY_MENU.get(), AssemblyGantryScreen::new);
        LabScreens.register(event);
        event.register(ModRegistry.MIXER_MENU.get(), (com.coolfreeze23.headinajar.menu.SalineMixerMenu menu, net.minecraft.world.entity.player.Inventory inv, net.minecraft.network.chat.Component title) ->
                new MachineScreen<>(menu, inv, title).energy(20_000)
                        .gauge(52, 17, 12, 56, 1, 4000, 0xFF3F76E4, "gui.headinajar.water")
                        .arrow(70, 37, 3, 200)
                        .gauge(100, 17, 12, 56, 2, 4000, 0xFF5FC8C0, "gui.headinajar.saline"));
        event.register(ModRegistry.PRINTER_MENU.get(), (com.coolfreeze23.headinajar.menu.BodyPrinterMenu menu, net.minecraft.world.entity.player.Inventory inv, net.minecraft.network.chat.Component title) ->
                new MachineScreen<>(menu, inv, title).energy(250_000)
                        .gauge(72, 17, 12, 56, 1, 4000, 0xFF5FC8C0, "gui.headinajar.saline")
                        .arrow(92, 30, 2, 1200)
                        .line(30, 62, "gui.headinajar.printer.need")
                        .button(120, 26, 48, 20, "gui.headinajar.printer.print", 0));
    }

    private static void keys(RegisterKeyMappingsEvent event) {
        event.register(JAR_MENU);
        event.register(ABILITY);
        event.register(SWAP);
        event.register(SECONDARY);
        event.register(ZOOM);
    }

    private static void gui(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.FOOD_LEVEL, HeadInAJar.id("charge"), ChargeHud::render);
        event.registerAboveAll(HeadInAJar.id("neuro_link"), LinkHud::render);
        event.registerAboveAll(HeadInAJar.id("host"), HostHud::render);
        event.registerAboveAll(HeadInAJar.id("pilot"), PilotClient::hud);
        event.registerAboveAll(HeadInAJar.id("genes"), GeneHud::render);
        event.registerAboveAll(HeadInAJar.id("socket"), SocketClient::hud);
    }

    private static void fluids(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        for (var fluid : com.coolfreeze23.headinajar.registry.ModFluids.ALL) {
            int tint = fluid.tint;
            event.registerFluidType(new net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions() {
                @Override
                public net.minecraft.resources.ResourceLocation getStillTexture() {
                    return net.minecraft.resources.ResourceLocation.withDefaultNamespace("block/water_still");
                }

                @Override
                public net.minecraft.resources.ResourceLocation getFlowingTexture() {
                    return net.minecraft.resources.ResourceLocation.withDefaultNamespace("block/water_flow");
                }

                @Override
                public int getTintColor() {
                    return tint;
                }
            }, fluid.type.get());
        }
    }

    @SuppressWarnings("unchecked")
    private static void playerLayers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer renderer) {
                renderer.addLayer(new CollarLayer(renderer));
            }
        }
    }
}
