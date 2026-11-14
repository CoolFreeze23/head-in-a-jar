package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * A jar in a Neural Socket: scroll picks a face, number keys set its level (1-9 as 1 to 15, 0 off), the ability key pulses
 * it, the secondary key steps to the next face, sneak + the jar menu key unplugs. The HUD shows every face's output and
 * input and what the socket reads.
 */
public final class SocketClient {
    /** Outputs (6), inputs (6), item count, tank fill, tank capacity; from the server. */
    public static List<Integer> status = List.of();
    private static int face;

    private SocketClient() {
    }

    private static boolean socketed() {
        var p = Minecraft.getInstance().player;
        return p != null && p.getData(ModRegistry.JAR_STATE).form() == Form.SOCKET;
    }

    public static void pulse() {
        PacketDistributor.sendToServer(new Network.SocketInput(1, face, 15));
    }

    public static void nextFace() {
        face = (face + 1) % 6;
    }

    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent event) {
        if (!socketed() || Minecraft.getInstance().screen != null) return;
        face = Math.floorMod(face + (event.getScrollDeltaY() > 0 ? -1 : 1), 6);
        event.setCanceled(true);
    }

    /** The 0 key turns the face off (no hotbar key is bound to it by default). */
    @SubscribeEvent
    public static void key(InputEvent.Key event) {
        if (!socketed() || Minecraft.getInstance().screen != null || event.getAction() != GLFW.GLFW_PRESS) return;
        if (event.getKey() == GLFW.GLFW_KEY_0) PacketDistributor.sendToServer(new Network.SocketInput(0, face, 0));
    }

    /**
     * The hotbar keys (however they are bound) set the face's level, 1 to 15; they are taken before the game sees them,
     * so a plugged-in jar's hotbar stays where it was.
     */
    @SubscribeEvent
    public static void hotbarKeys(net.neoforged.neoforge.client.event.ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!socketed() || mc.screen != null) return;
        for (int i = 0; i < mc.options.keyHotbarSlots.length; i++) {
            while (mc.options.keyHotbarSlots[i].consumeClick()) {
                PacketDistributor.sendToServer(new Network.SocketInput(0, face, com.coolfreeze23.headinajar.block.NeuralSocketBlockEntity.levelForKey(i + 1)));
            }
        }
    }

    public static void hud(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!socketed() || mc.options.hideGui) return;
        int x = 8, y = 8;
        g.drawString(mc.font, Component.translatable("hud.headinajar.socket"), x, y, 0xFFB0E0FF);
        y += 12;
        for (Direction d : Direction.values()) {
            int i = d.get3DDataValue();
            int out = status.size() > i ? status.get(i) : 0, in = status.size() > 6 + i ? status.get(6 + i) : 0;
            boolean selected = i == face;
            g.drawString(mc.font, Component.translatable("hud.headinajar.socket.face", Component.translatable("direction.headinajar." + d.getName()), out, in),
                    x + (selected ? 6 : 0), y, selected ? 0xFFFFFF60 : 0xFFC0C0C0);
            if (selected) g.drawString(mc.font, ">", x, y, 0xFFFFFF60);
            y += 10;
        }
        if (status.size() >= 15) {
            g.drawString(mc.font, Component.translatable("hud.headinajar.socket.items", status.get(12)), x, y + 2, 0xFFA0E0A0);
            if (status.get(14) > 0) g.drawString(mc.font, Component.translatable("hud.headinajar.socket.tank", status.get(13), status.get(14)), x, y + 12, 0xFFA0C0E0);
        }
        g.drawString(mc.font, Component.translatable("hud.headinajar.socket.keys", ClientSetup.ABILITY.getTranslatedKeyMessage(), ClientSetup.SECONDARY.getTranslatedKeyMessage()), x, y + 26, 0xFF808080);
    }
}
