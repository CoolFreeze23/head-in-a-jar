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
    /** The mecha the arm state was last sent for: a new one is always told what is held, even if nothing is. */
    private static int sentFor = -1;

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
            if (m == null) sentFor = -1;
            return;
        }
        int arms = (mc.options.keyAttack.isDown() ? 1 : 0) | (mc.options.keyUse.isDown() ? 2 : 0);
        if (arms != sentArms || sentFor != m.getId()) PacketDistributor.sendToServer(new Network.ArmInput(sentArms = arms));
        sentFor = m.getId();
        boolean jumping = mc.options.keyJump.isDown();
        if (jumping && !wasJumping && !m.onGround() && m.has(MechaModule.JUMP_JETS)) {
            PacketDistributor.sendToServer(new Network.JarAction(Network.Action.JET));
        }
        wasJumping = jumping;
        boolean cortex = mc.player.getData(ModRegistry.JAR_STATE).has(com.coolfreeze23.headinajar.module.ModuleType.CORTEX_COPROCESSOR);
        if ((m.has(MechaModule.TARGET_LOCK) || cortex) && m.battery() > 0) lockOn(mc, m);
        if (mc.options.keyShift.isDown() && ClientSetup.JAR_MENU.isDown()) {
            if (++ejectHold == 20) PacketDistributor.sendToServer(new Network.JarAction(Network.Action.EJECT));
        } else {
            ejectHold = 0;
        }
    }

    /** Target Lock: a hostile within 24 blocks and about 12 degrees of your aim pulls the aim gently onto it. */
    private static void lockOn(Minecraft mc, MechaEntity m) {
        var player = mc.player;
        var eye = player.getEyePosition();
        var look = player.getLookAngle();
        net.minecraft.world.entity.LivingEntity best = null;
        double bestDot = 0.978;
        for (var e : player.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, m.getBoundingBox().inflate(24),
                e -> e instanceof net.minecraft.world.entity.monster.Enemy && e.isAlive())) {
            var to = e.getBoundingBox().getCenter().subtract(eye).normalize();
            double dot = to.dot(look);
            if (dot > bestDot) {
                bestDot = dot;
                best = e;
            }
        }
        if (best == null) return;
        var to = best.getBoundingBox().getCenter().subtract(eye);
        float yaw = (float) (net.minecraft.util.Mth.atan2(to.z, to.x) * (180 / Math.PI)) - 90;
        float pitch = (float) -(net.minecraft.util.Mth.atan2(to.y, to.horizontalDistance()) * (180 / Math.PI));
        player.setYRot(player.getYRot() + net.minecraft.util.Mth.wrapDegrees(yaw - player.getYRot()) * 0.2f);
        player.setXRot(player.getXRot() + (pitch - player.getXRot()) * 0.2f);
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
        var utility = com.coolfreeze23.headinajar.mecha.MechaSystems.selectedUtility(m);
        if (utility != null) {
            g.drawString(mc.font, Component.translatable("hud.headinajar.utility", ClientSetup.ABILITY.getTranslatedKeyMessage(),
                    Component.translatable("item.headinajar." + utility.id()), ClientSetup.SECONDARY.getTranslatedKeyMessage()), x, y - 20, 0xFFD080);
        }
        if (m.has(MechaModule.CARGO_HOLD)) {
            // the swap key opens the hold from the cockpit
            g.drawString(mc.font, Component.translatable("hud.headinajar.cargo", ClientSetup.SWAP.getTranslatedKeyMessage(),
                    Component.translatable("item.headinajar." + MechaModule.CARGO_HOLD.id())), x, utility != null ? y - 30 : y - 20, 0xFFD080);
        }
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
        else if (!MechaLogic.canWalk(m) && !MechaLogic.canFly(m) && !(m.isInWater() && m.has(MechaModule.AQUA_FINS))) {
            g.drawString(mc.font, Component.translatable("hud.headinajar.no_legs", Component.translatable("block.headinajar.assembly_gantry")).withStyle(ChatFormatting.YELLOW), x, y, 0xFFFFFF);
        }
        else if (ejectHold > 0) g.drawString(mc.font, Component.translatable("hud.headinajar.ejecting", 100 * ejectHold / 20), x, y, 0xFFD040);
    }

    private static Component arm(MechaEntity m, int side) {
        MechaModule mod = m.arm(side);
        return mod == null ? Component.literal("-") : Component.translatable("item.headinajar." + mod.id());
    }
}
