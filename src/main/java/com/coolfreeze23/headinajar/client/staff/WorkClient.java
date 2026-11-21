package com.coolfreeze23.headinajar.client.staff;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.work.Order;
import com.coolfreeze23.headinajar.staff.work.WorkTable;
import net.minecraft.client.renderer.item.ItemProperties;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

/** The workforce on the client: item looks (a Job Card's printed job, the Whistle at the lips), the Time Clock's screen and layers. */
public final class WorkClient {
    private WorkClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent e) -> e.enqueueWork(WorkClient::properties));
        modBus.addListener(WorkClient::screens);
        // the Time Clock's moving parts
        modBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers e) ->
                e.registerBlockEntityRenderer(WorkRegistry.TIME_CLOCK_BE.get(), c -> new TimeClockRenderer()));
        // the Clipboard's chalk, for its holder alone
        NeoForge.EVENT_BUS.addListener(ChalkOutlines::render);
        // the Whistle's point in the air (and a jar's grille)
        WhistleClient.init();
        looks(modBus);
        // staff as this client was told: a helmet used on one is refused here too, not worn
        com.coolfreeze23.headinajar.staff.work.Workforce.seenAsWorker = e -> WorkerLines.look(e) != null;
    }

    private static void screens(RegisterMenuScreensEvent event) {
        event.register(WorkRegistry.CLIPBOARD_MENU.get(), ClipboardScreen::new);
        event.register(WorkRegistry.TIME_CLOCK_MENU.get(), TimeClockScreen::new);
    }

    // --- what everyone sees of a worker (the look part) ------------------------------------------------------------

    /** The worn look's registrations: the layer on every creature, the fit table, the baked hats and crate, the armour hat. */
    private static void looks(IEventBus modBus) {
        modBus.addListener(WorkClient::layers);
        modBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions e) ->
                {
                    e.registerLayerDefinition(HardHatModel.LAYER, HardHatModel::create);
                    e.registerLayerDefinition(HardHatModel.LAYER_TALL, HardHatModel::createTall);
                });
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent e) -> {
            e.registerReloadListener(new StaffFit.Loader());
            e.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager ->
                    StaffLayer.artSheet = manager.getResource(StaffLayer.LANYARD).isPresent());
        });
        modBus.addListener((net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional e) -> {
            for (String size : StaffLayer.HAT_SIZES) e.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(StaffLayer.hatModel(size)));
            e.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(StaffLayer.CRATE));
        });
        modBus.addListener((net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent e) ->
                e.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
                    @Override
                    public net.minecraft.client.model.HumanoidModel<?> getHumanoidArmorModel(net.minecraft.world.entity.LivingEntity entity,
                            net.minecraft.world.item.ItemStack stack, net.minecraft.world.entity.EquipmentSlot slot, net.minecraft.client.model.HumanoidModel<?> original) {
                        var hat = HardHatModel.model(entity);
                        return hat == null ? original : hat;
                    }
                }, WorkRegistry.HARD_HAT.get()));
        var bus = net.neoforged.neoforge.common.NeoForge.EVENT_BUS;
        bus.addListener(WorkerLines::render);
        bus.addListener(WorkerLines::tick);
        bus.addListener(WorkerLines::nameTag);
        bus.addListener(WorkerLines::loggedOut);
        bus.addListener(WorkerLines::left);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void layers(net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers event) {
        HardHatModel.bake(event.getEntityModels());
        for (net.minecraft.world.entity.EntityType<?> type : event.getEntityTypes()) {
            if (event.getRenderer(type) instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer living) living.addLayer(new StaffLayer(living));
        }
    }

    private static void properties() {
        // a Job Card's look: 0 blank, then hauling, sorting, farming, security in quarters
        ItemProperties.register(WorkRegistry.JOB_CARD.get(), HeadInAJar.id("job"), (stack, level, entity, seed) -> {
            Order.Card c = Order.card(stack);
            if (c == null) return 0;
            int i = WorkTable.JOBS.indexOf(c.order().job());
            return i < 0 ? 0 : (i + 1) * 0.25f;
        });
        // the Whistle held to the lips while it blows, as vanilla's goat horn
        ItemProperties.register(WorkRegistry.SUPERVISORS_WHISTLE.get(), HeadInAJar.id("tooting"),
                (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1 : 0);
    }
}
