package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.host.HostData;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** While hosted: the host's health, freshness and sync, and which abilities sync has unlocked. Also the handshake. */
public final class HostHud {
    private HostHud() {
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        ClientHooks.render(g, delta.getGameTimeDeltaPartialTick(false));
        if (mc.player == null || mc.options.hideGui) return;
        if (mc.player.getData(ModRegistry.JAR_STATE).form() != Form.HOSTED) return;
        Entity vehicle = mc.player.getVehicle();
        if (vehicle == null || !Hosts.isHost(vehicle)) return;
        HostData data = Hosts.data(vehicle);
        int x = 8, y = g.guiHeight() - 56;
        g.drawString(mc.font, vehicle.getDisplayName(), x, y - 10, 0xFFFFFF);
        if (vehicle instanceof LivingEntity living) {
            ChargeHud.drawBar(g, x, y, 80, living.getHealth() / living.getMaxHealth(), 0xFFE04848, 0xFF9A2E2E);
            y += 9;
        }
        if (!data.shell) {
            ChargeHud.drawBar(g, x, y, 80, data.freshness / 100f, 0xFF8FC060, 0xFF5A7A3A);
            g.drawString(mc.font, Component.translatable("hud.headinajar.freshness", Math.round(data.freshness)), x + 84, y - 1, 0xC0C0C0);
            y += 9;
        }
        float sync = data.effectiveSync();
        ChargeHud.drawBar(g, x, y, 80, sync / 100f, 0xFFB47AE8, 0xFF7A3CB0);
        g.drawString(mc.font, Component.translatable("hud.headinajar.sync", Math.round(sync)), x + 84, y - 1, 0xC0C0C0);
        y += 11;
        // only the abilities this body really has (its data file, as the server sent it)
        var def = Hosts.definition(vehicle);
        boolean primary = def != null && def.primary().isPresent(), secondary = def != null && def.secondary().isPresent();
        String r = sync >= 50 ? "hud.headinajar.primary_ready" : "hud.headinajar.primary_locked";
        String gk = sync >= 75 ? "hud.headinajar.secondary_ready" : "hud.headinajar.secondary_locked";
        if (primary) g.drawString(mc.font, Component.translatable(r, Component.keybind(ClientSetup.ABILITY.getName())), x, y, sync >= 50 ? 0x55FF77 : 0x808080);
        if (secondary) g.drawString(mc.font, Component.translatable(gk, Component.keybind(ClientSetup.SECONDARY.getName())), x + 70, y, sync >= 75 ? 0x55FF77 : 0x808080);
        if (!primary && !secondary) g.drawString(mc.font, Component.translatable("hud.headinajar.no_abilities"), x, y, 0x808080);
        // a Utility Shell runs on the jar's charge: a flat jar can't move it
        if (data.shell && mc.player.getData(ModRegistry.JAR_STATE).charge() <= 0) {
            g.drawString(mc.font, Component.translatable("hud.headinajar.shell_flat", Component.translatable("block.headinajar.charging_dock"))
                    .withStyle(net.minecraft.ChatFormatting.YELLOW), x, y + 10, 0xFFFFFF);
        }
    }
}
