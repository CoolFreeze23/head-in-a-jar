package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.network.Network;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-side pieces the network code calls, and the Neural Handshake: a ring closes on a target circle three times;
 * press the ability key as it lines up. Each hit is +3% starting sync, each miss 2 s of Nausea.
 */
public final class ClientHooks {
    private static final int ROUND_TICKS = 30;
    private static int handshakeEntity = -1;
    private static int round;
    private static int tick;
    private static int hits;
    private static boolean pressedThisRound;
    private static int flash;

    private ClientHooks() {
    }

    public static void startHandshake(int entityId) {
        if (!Config.CLIENT_SPEC.isLoaded() || !Config.HANDSHAKE.get()) {
            PacketDistributor.sendToServer(new Network.Handshake(entityId, -1));
            return;
        }
        handshakeEntity = entityId;
        round = 0;
        tick = 0;
        hits = 0;
        pressedThisRound = false;
    }

    public static boolean handshakeActive() {
        return handshakeEntity >= 0;
    }

    /** The ring's radius now, from 60 down to 0 over a round. */
    private static float ring(float partial) {
        return 60f * (1 - (tick + partial) / ROUND_TICKS);
    }

    /** Called when the ability key is pressed during a handshake. */
    public static void press() {
        if (!handshakeActive() || pressedThisRound) return;
        pressedThisRound = true;
        boolean hit = Math.abs(ring(0) - 20f) <= 6f;
        if (hit) hits++;
        flash = hit ? 6 : -6;
    }

    public static void tick() {
        if (!handshakeActive()) return;
        // off the host (got down, teleported, the host gone): the handshake is over, and the ability key is the jar's again
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null || player.getVehicle() == null || player.getVehicle().getId() != handshakeEntity) {
            handshakeEntity = -1;
            return;
        }
        if (flash > 0) flash--;
        if (flash < 0) flash++;
        if (++tick >= ROUND_TICKS) {
            tick = 0;
            pressedThisRound = false;
            if (++round >= 3) {
                PacketDistributor.sendToServer(new Network.Handshake(handshakeEntity, hits));
                handshakeEntity = -1;
            }
        }
    }

    public static void render(GuiGraphics g, float partial) {
        if (!handshakeActive()) return;
        Minecraft mc = Minecraft.getInstance();
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2 + 30;
        circle(g, cx, cy, 20, flash > 0 ? 0xFF55FF77 : flash < 0 ? 0xFFFF5555 : 0xFFFFFFFF);
        circle(g, cx, cy, ring(partial), 0xFF59E0F4);
        g.drawCenteredString(mc.font, Component.translatable("hud.headinajar.handshake", round + 1, Component.keybind(ClientSetup.ABILITY.getName())),
                cx, cy + 34, 0xFFFFFF);
    }

    private static void circle(GuiGraphics g, int cx, int cy, float r, int color) {
        if (r < 1) return;
        int steps = Math.max(24, (int) (r * 2));
        for (int i = 0; i < steps; i++) {
            double a = i * Math.PI * 2 / steps;
            int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(x, y, x + 2, y + 2, color);
        }
    }
}
