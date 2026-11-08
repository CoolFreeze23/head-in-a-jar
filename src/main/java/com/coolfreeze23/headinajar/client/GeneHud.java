package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.genetics.Gene;
import com.coolfreeze23.headinajar.genetics.GeneEffects;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/** Which active genes R and G fire, bottom left, while your body (or your Chimera) carries any. */
public final class GeneHud {
    private GeneHud() {
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        Form form = mc.player.getData(ModRegistry.JAR_STATE).form();
        LivingEntity body = form == Form.BODY || form == Form.LINKED ? mc.player
                : form == Form.PILOT && mc.player.getVehicle() instanceof MechaEntity mecha ? mecha : null;
        if (body == null) return;
        List<Gene> actives = GeneEffects.actives(body);
        if (actives.isEmpty()) return;
        String[] keys = {ClientSetup.ABILITY.getTranslatedKeyMessage().getString(), ClientSetup.SECONDARY.getTranslatedKeyMessage().getString()};
        int y = g.guiHeight() - (form == Form.PILOT ? 102 : 34);
        for (int i = 0; i < Math.min(2, actives.size()); i++) {
            g.drawString(mc.font, Component.translatable("hud.headinajar.gene_key", keys[i], LabScreens.geneName(actives.get(i))), 6, y + i * 10, 0xFFB080FF);
        }
    }
}
