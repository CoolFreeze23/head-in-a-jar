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

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public HeadInAJar(IEventBus modBus, ModContainer container) {
        ModRegistry.register(modBus);
        modBus.addListener(Network::register);
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        NeoForge.EVENT_BUS.register(FormEvents.class);
    }
}
