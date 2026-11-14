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

    /** An ability's name in the reader's language (its type, as the host's data file names it). */
    static Component abilityName(com.coolfreeze23.headinajar.host.HostDefinition.Ability a) {
        return Component.translatable("ability.headinajar." + a.type());
    }

    /** The second ability's name; when it is the same kind as the first (the enderman's two jumps), it says so. */
    public static Component secondName(com.coolfreeze23.headinajar.host.HostDefinition def) {
        Component name = abilityName(def.secondary().get());
        return def.primary().isPresent() && def.primary().get().type().equals(def.secondary().get().type())
                ? Component.translatable("ability.headinajar.clarity.further", name) : name;
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        float partial = delta.getGameTimeDeltaPartialTick(false);
        if (mc.player == null || mc.options.hideGui) {
            ClientHooks.render(g, partial);
            return;
        }
        Entity vehicle = mc.player.getVehicle();
        if (mc.player.getData(ModRegistry.JAR_STATE).form() == Form.HOSTED && vehicle != null && Hosts.isHost(vehicle)) panel(g, mc, vehicle);
        // the handshake's ring is laid out around this panel, so it comes after it
        ClientHooks.render(g, partial);
    }

    /** The host's panel, a line at a time in the HUD's side column (top left, clear of chat, hotbar and action bar). */
    private static void panel(GuiGraphics g, Minecraft mc, Entity vehicle) {
        HostData data = Hosts.data(vehicle);
        HudStack.gap();
        HudStack.text(g, vehicle.getDisplayName(), 0xFFFFFF);
        if (vehicle instanceof LivingEntity living) HudStack.bar(g, 80, living.getHealth() / living.getMaxHealth(), 0xFFE04848, 0xFF9A2E2E, null);
        if (!data.shell) HudStack.bar(g, 80, data.freshness / 100f, 0xFF8FC060, 0xFF5A7A3A,
                Component.translatable("hud.headinajar.freshness", Math.round(data.freshness)));
        float sync = data.effectiveSync();
        HudStack.bar(g, 80, sync / 100f, 0xFFB47AE8, 0xFF7A3CB0, Component.translatable("hud.headinajar.sync", Math.round(sync)));
        // its own way of moving (flying, climbing, floating) is the rider's from the first second; the rest opens with sync
        if (!data.shell && sync < 25) HudStack.text(g, Component.translatable("hud.headinajar.release5.moves_now", 25), 0x808080);
        // only the abilities this body really has (its data file, as the server sent it), each by name with its key, or
        // the sync it opens at
        var def = Hosts.definition(vehicle);
        boolean primary = def != null && def.primary().isPresent(), secondary = def != null && def.secondary().isPresent();
        if (primary) HudStack.text(g, Component.translatable(sync >= 50 ? "hud.headinajar.clarity.ability_ready" : "hud.headinajar.clarity.ability_locked",
                Component.keybind(ClientSetup.ABILITY.getName()), abilityName(def.primary().get()), 50), sync >= 50 ? 0x55FF77 : 0x808080);
        if (secondary) HudStack.text(g, Component.translatable(sync >= 75 ? "hud.headinajar.clarity.ability_ready" : "hud.headinajar.clarity.ability_locked",
                Component.keybind(ClientSetup.SECONDARY.getName()), secondName(def), 75), sync >= 75 ? 0x55FF77 : 0x808080);
        if (!primary && !secondary) HudStack.text(g, Component.translatable("hud.headinajar.no_abilities"), 0x808080);
        // a Utility Shell runs on the jar's charge: a flat jar can't move it
        if (data.shell && mc.player.getData(ModRegistry.JAR_STATE).charge() <= 0) {
            HudStack.text(g, Component.translatable("hud.headinajar.shell_flat", Component.translatable("block.headinajar.charging_dock"))
                    .withStyle(net.minecraft.ChatFormatting.YELLOW), 0xFFFFFF);
        }
    }
}
