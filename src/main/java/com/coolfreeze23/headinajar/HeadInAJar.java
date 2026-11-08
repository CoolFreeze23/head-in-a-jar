package com.coolfreeze23.headinajar;

import com.coolfreeze23.headinajar.form.FormEvents;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

@Mod(HeadInAJar.MOD_ID)
public class HeadInAJar {
    public static final String MOD_ID = "headinajar";
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public HeadInAJar(IEventBus modBus, ModContainer container) {
        ModRegistry.register(modBus);
        modBus.addListener(Network::register);
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        NeoForge.EVENT_BUS.register(FormEvents.class);
        NeoForge.EVENT_BUS.register(com.coolfreeze23.headinajar.host.HostEvents.class);
        NeoForge.EVENT_BUS.register(com.coolfreeze23.headinajar.genetics.GeneEvents.class);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent e) ->
                com.coolfreeze23.headinajar.command.HeadOfficeCommand.register(e.getDispatcher()));
    }
}
