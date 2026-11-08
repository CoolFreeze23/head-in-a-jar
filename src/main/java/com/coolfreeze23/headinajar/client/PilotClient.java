package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.mecha.MechaLogic;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** The pilot's side of a mecha: mouse buttons drive the arms, jump fires jets, sneak + J ejects; the HUD and camera. */
public final class PilotClient {
    private static int sentArms;
    private static boolean wasJumping;
    private static int ejectHold;

    private PilotClient() {
    }

    public static MechaEntity mecha() {
        var player = Minecraft.getInstance().player;
        if (player == null || player.getData(ModRegistry.JAR_STATE).form() != Form.PILOT) return null;
        return player.getVehicle() instanceof MechaEntity m ? m : null;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        MechaEntity m = mecha();
        if (m == null || mc.screen != null) {
            if (sentArms != 0) PacketDistributor.sendToServer(new Network.ArmInput(sentArms = 0));
            ejectHold = 0;
            return;
        }
        int arms = (mc.options.keyAttack.isDown() ? 1 : 0) | (mc.options.keyUse.isDown() ? 2 : 0);
        if (arms != sentArms) PacketDistributor.sendToServer(new Network.ArmInput(sentArms = arms));
        boolean jumping = mc.options.keyJump.isDown();
        if (jumping && !wasJumping && !m.onGround() && m.has(MechaModule.JUMP_JETS)) {
            PacketDistributor.sendToServer(new Network.JarAction(Network.Action.JET));
        }
        wasJumping = jumping;
        if (mc.options.keyShift.isDown() && ClientSetup.JAR_MENU.isDown()) {
            if (++ejectHold == 20) PacketDistributor.sendToServer(new Network.JarAction(Network.Action.EJECT));
        } else {
            ejectHold = 0;
        }
    }

    /** The chase camera sits further back from bigger frames. */
    public static void cameraDistance(CalculateDetachedCameraDistanceEvent event) {
        MechaEntity m = mecha();
        if (m != null) event.setDistance(m.frame().height * 2.0f);
    }

    public static void hud(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        MechaEntity m = mecha();
        if (m == null || mc.options.hideGui) return;
        int x = 8, y = g.guiHeight() - 70;
        g.drawString(mc.font, m.getDisplayName(), x, y - 10, 0xFFFFFF);
        ChargeHud.drawBar(g, x, y, 90, m.getHealth() / m.getMaxHealth(), 0xFFE04848, 0xFF9A2E2E);
        y += 9;
        ChargeHud.drawBar(g, x, y, 90, m.battery() / (float) m.batteryCapacity(), 0xFF3FD8F0, 0xFF1F9AB8);
        g.drawString(mc.font, Component.translatable("hud.headinajar.battery", m.battery() / 1000), x + 94, y - 1, 0xC0C0C0);
        y += 9;
        float heat = m.heat() / m.frame().heatCap;
        int heatColor = heat >= MechaLogic.THROTTLE ? 0xFFE04848 : heat >= 0.6f ? 0xFFE0C040 : 0xFF60C060;
        ChargeHud.drawBar(g, x, y, 90, heat, heatColor, heatColor);
        g.drawString(mc.font, Component.translatable("hud.headinajar.heat", Math.round(heat * 100)), x + 94, y - 1, 0xC0C0C0);
        y += 9;
        float load = m.load();
        g.drawString(mc.font, Component.translatable("hud.headinajar.load", m.mass(), m.frame().load), x, y, load > 1 ? 0xFF5555 : load > 0.6f ? 0xFFD040 : 0xC0C0C0);
        y += 10;
        Component left = arm(m, 0), right = arm(m, 1);
        g.drawString(mc.font, Component.translatable("hud.headinajar.arms", left, right), x, y, 0xA0E0FF);
        y += 10;
        if (m.vent() > 0) g.drawString(mc.font, Component.translatable("hud.headinajar.venting").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), x, y, 0xFFFFFF);
        else if (m.battery() <= 0) g.drawString(mc.font, Component.translatable("hud.headinajar.limp").withStyle(ChatFormatting.YELLOW), x, y, 0xFFFFFF);
        else if (load > 1.25f) g.drawString(mc.font, Component.translatable("hud.headinajar.servos_locked").withStyle(ChatFormatting.RED), x, y, 0xFFFFFF);
        else if (ejectHold > 0) g.drawString(mc.font, Component.translatable("hud.headinajar.ejecting", 100 * ejectHold / 20), x, y, 0xFFD040);
    }

    private static Component arm(MechaEntity m, int side) {
        MechaModule mod = m.arm(side);
        return mod == null ? Component.literal("-") : Component.translatable("item.headinajar." + mod.id());
    }
}
