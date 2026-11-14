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

/** Which active genes the ability and secondary keys fire, in the side column, while your body, your host or your mecha carries any. */
public final class GeneHud {
    private GeneHud() {
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        Form form = mc.player.getData(ModRegistry.JAR_STATE).form();
        LivingEntity body = form == Form.BODY || form == Form.LINKED ? mc.player
                : form == Form.PILOT && mc.player.getVehicle() instanceof MechaEntity mecha ? mecha
                : form == Form.HOSTED && mc.player.getVehicle() instanceof net.minecraft.world.entity.Mob host ? host : null;
        if (body == null) return;
        List<Gene> actives = GeneEffects.actives(body);
        if (actives.isEmpty()) return;
        String[] keys = {ClientSetup.ABILITY.getTranslatedKeyMessage().getString(), ClientSetup.SECONDARY.getTranslatedKeyMessage().getString()};
        // in the HUD's side column, under the pilot's panel when there is one: never on top of its lines
        HudStack.gap();
        // a host's own ability keeps its key: its genes fire from a key the host has no ability on
        var def = form == Form.HOSTED ? com.coolfreeze23.headinajar.host.Hosts.definition(body) : null;
        for (int i = 0; i < Math.min(2, actives.size()); i++) {
            if (def != null && (i == 0 ? def.primary() : def.secondary()).isPresent()) continue;
            HudStack.text(g, Component.translatable("hud.headinajar.gene_key", keys[i], LabScreens.geneName(actives.get(i))), 0xFFB080FF);
        }
    }
}
