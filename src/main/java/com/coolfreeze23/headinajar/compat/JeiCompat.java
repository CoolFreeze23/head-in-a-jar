package com.coolfreeze23.headinajar.compat;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;

import java.util.Map;

/** JEI: an information page for every machine and the items whose use isn't a crafting recipe. */
@JeiPlugin
public class JeiCompat implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return HeadInAJar.id("jei");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        Map<String, ItemLike> pages = new java.util.LinkedHashMap<>();
        pages.put("separation_chair", ModRegistry.SEPARATION_CHAIR_ITEM.get());
        pages.put("life_support_cradle", ModRegistry.CRADLE_ITEM.get());
        pages.put("saline_mixer", ModRegistry.SALINE_MIXER_ITEM.get());
        pages.put("body_printer", ModRegistry.BODY_PRINTER_ITEM.get());
        pages.put("surgical_saw", ModRegistry.SURGICAL_SAW.get());
        pages.put("assembly_gantry", ModRegistry.ASSEMBLY_GANTRY_ITEM.get());
        pages.put("centrifuge", ModRegistry.CENTRIFUGE_ITEM.get());
        pages.put("dna_sequencer", ModRegistry.DNA_SEQUENCER_ITEM.get());
        pages.put("gene_splicer", ModRegistry.GENE_SPLICER_ITEM.get());
        pages.put("injection_rig", ModRegistry.INJECTION_RIG_ITEM.get());
        pages.put("leech_jar", ModRegistry.LEECH_JAR_ITEM.get());
        pages.put("syringe", ModRegistry.SYRINGE.get());
        pages.put("serum_cassette", ModRegistry.SERUM_CASSETTE.get());
        pages.put("turret_pedestal", ModRegistry.TURRET_PEDESTAL_ITEM.get());
        pages.put("cerebral_array", ModRegistry.CEREBRAL_ARRAY_ITEM.get());
        pages.put("employee_mount", ModRegistry.EMPLOYEE_MOUNT_ITEM.get());
        pages.put("neural_socket", ModRegistry.NEURAL_SOCKET_ITEM.get());
        pages.put("entangled_pair", ModRegistry.ENTANGLED_PAIR.get());
        pages.put("emp_grenade", ModRegistry.EMP_GRENADE.get());
        pages.put("jarred_head", ModRegistry.JARRED_HEAD.get());
        pages.forEach((key, item) -> registration.addIngredientInfo(item, Component.translatable("jei.headinajar." + key)));
        // every other item of the mod that has an information page in the language files gets it too
        for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
            String key = "jei.headinajar." + id.getPath();
            if (id.getNamespace().equals(HeadInAJar.MOD_ID) && !pages.containsKey(id.getPath())
                    && net.minecraft.client.resources.language.I18n.exists(key)) {
                registration.addIngredientInfo(item, Component.translatable(key));
            }
        }
        for (MechaFrame frame : MechaFrame.values()) {
            registration.addIngredientInfo(ModRegistry.frameItem(frame), Component.translatable("jei.headinajar.frame"));
        }
    }
}
