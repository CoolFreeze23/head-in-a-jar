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
    /** Whether this client has seen itself on the handshake's host yet, and how long it has waited to. */
    private static boolean seenRiding;
    private static int waited;

    private ClientHooks() {
    }

    public static void startHandshake(int entityId) {
        if (!Config.CLIENT_SPEC.isLoaded() || !Config.HANDSHAKE.get()) {
            PacketDistributor.sendToServer(new Network.Handshake(entityId, -1));
            return;
        }
        handshakeEntity = entityId;
        com.coolfreeze23.headinajar.client.body.HandshakeNotes.start();
        round = 0;
        tick = 0;
        hits = 0;
        pressedThisRound = false;
        seenRiding = false;
        waited = 0;
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
        if (!handshakeActive() || !seenRiding || pressedThisRound) return;
        pressedThisRound = true;
        boolean hit = Math.abs(ring(0) - 20f) <= 6f;
        if (hit) hits++;
        flash = hit ? 6 : -6;
        // each hit plays the next note of the company jingle (C5, E5, G5); a miss buzzes and the tune starts again
        com.coolfreeze23.headinajar.client.body.HandshakeNotes.press(hit);
    }

    public static void tick() {
        if (!handshakeActive()) return;
        // off the host (got down, teleported, the host gone): the handshake is over, and the ability key is the jar's again
        // (the handshake can arrive a tick before this client sees itself riding: it waits for that before it starts)
        var player = net.minecraft.client.Minecraft.getInstance().player;
        boolean riding = player != null && player.getVehicle() != null && player.getVehicle().getId() == handshakeEntity;
        if (riding) seenRiding = true;
        if (player == null || com.coolfreeze23.headinajar.host.HandshakeRule.over(riding, seenRiding, waited)) {
            handshakeEntity = -1;
            return;
        }
        if (!seenRiding) {
            waited++;
            return;
        }
        if (flash > 0) flash--;
        if (flash < 0) flash++;
        if (++tick >= ROUND_TICKS) {
            tick = 0;
            pressedThisRound = false;
            if (++round >= 3) {
                com.coolfreeze23.headinajar.client.body.HandshakeNotes.done(hits);
                PacketDistributor.sendToServer(new Network.Handshake(handshakeEntity, hits));
                handshakeEntity = -1;
            }
        }
    }

    public static void render(GuiGraphics g, float partial) {
        if (!handshakeActive() || !seenRiding) return;
        Minecraft mc = Minecraft.getInstance();
        Component caption = Component.translatable("hud.headinajar.handshake", round + 1, Component.keybind(ClientSetup.ABILITY.getName()));
        Component legs = Component.translatable("hud.headinajar.handshake_legs");
        int w = g.guiWidth(), h = g.guiHeight();
        // one block in the upper part of the screen: the ring, then the caption, then the legs line. It ends well above
        // the middle, where the jar sits in third person and the crosshair in first; the action bar keeps its own place.
        var state = mc.player.getData(com.coolfreeze23.headinajar.registry.ModRegistry.JAR_STATE);
        boolean linkBar = state.form() == com.coolfreeze23.headinajar.form.Form.LINKED;
        int top = linkBar ? 32 : 6, bottom = h / 2 - h / 12 - 2;
        int text = 24; // the two lines under the ring
        int r = Math.max(8, Math.min(60, (bottom - top - text - 4) / 2));
        float scale = r / 60f;
        int textW = Math.max(mc.font.width(caption), mc.font.width(legs));
        // centred; moved right of the side column (the host's panel) when the block would reach into it
        int half = Math.max(r, textW / 2);
        int cx = w / 2;
        if (cx - half < HudStack.right() + 6 && top < HudStack.bottom()) cx = HudStack.right() + 6 + half;
        cx = Math.min(cx, w - half - 2);
        int cy = top + r;
        circle(g, cx, cy, 20 * scale, flash > 0 ? 0xFF55FF77 : flash < 0 ? 0xFFFF5555 : 0xFFFFFFFF);
        circle(g, cx, cy, ring(partial) * scale, 0xFF59E0F4);
        int ty = cy + r + 4;
        g.drawCenteredString(mc.font, caption, cx, ty, 0xFFFFFF);
        g.drawCenteredString(mc.font, legs, cx, ty + 12, 0xDDDDDD);
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
