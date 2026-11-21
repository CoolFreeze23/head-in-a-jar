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
        Advance.register(modBus);
        com.coolfreeze23.headinajar.registry.ModSounds.register(modBus);
        com.coolfreeze23.headinajar.fx.FxParticles.register(modBus);
        modBus.addListener(Network::register);
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        NeoForge.EVENT_BUS.register(FormEvents.class);
        NeoForge.EVENT_BUS.register(com.coolfreeze23.headinajar.host.HostEvents.class);
        NeoForge.EVENT_BUS.register(com.coolfreeze23.headinajar.genetics.GeneEvents.class);
        com.coolfreeze23.headinajar.genetics.GentleGenes.register();
        com.coolfreeze23.headinajar.genetics.HostileGenes.register();
        com.coolfreeze23.headinajar.genetics.GeneActives.register();
        com.coolfreeze23.headinajar.host.HostTricks.register();
        NeoForge.EVENT_BUS.register(com.coolfreeze23.headinajar.block.Treadmills.class);
        NeoForge.EVENT_BUS.register(com.coolfreeze23.headinajar.form.BoardEvents.class);
        NeoForge.EVENT_BUS.register(com.coolfreeze23.headinajar.feedback.Refusals.class);
        NeoForge.EVENT_BUS.register(com.coolfreeze23.headinajar.block.MachineEvents.class);
        NeoForge.EVENT_BUS.register(com.coolfreeze23.headinajar.form.FormGuards.class);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent e) ->
                com.coolfreeze23.headinajar.command.HeadOfficeCommand.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.register(com.coolfreeze23.headinajar.staff.StaffEvents.class);
        // the workforce (0.7.0)
        NeoForge.EVENT_BUS.register(com.coolfreeze23.headinajar.item.StaffLanyardItem.class);
        com.coolfreeze23.headinajar.staff.work.WorkRules.register();
        NeoForge.EVENT_BUS.register(com.coolfreeze23.headinajar.staff.work.jobs.JobEvents.class);
        NeoForge.EVENT_BUS.register(com.coolfreeze23.headinajar.staff.work.DetailEvents.class);
        NeoForge.EVENT_BUS.register(com.coolfreeze23.headinajar.clarity.MemoQueue.class);
        NeoForge.EVENT_BUS.register(com.coolfreeze23.headinajar.staff.work.WorkerLooks.class); // what others see of a worker
    }
}
