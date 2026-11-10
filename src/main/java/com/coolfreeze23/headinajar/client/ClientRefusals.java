package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

/**
 * The client twin of {@link Refusals}. Input the client cancels itself (a bare jar's clicks) is sent to the server as a
 * refusal request, so the line, the sound for bystanders and the jar's Nudge all come from one place. A press repeated
 * while the key is held (attack on a block fires every tick) asks once. Lines from the server show on the action bar,
 * and over the open screen, which hides the action bar.
 */
public final class ClientRefusals {
    private static final int SHOWN_TICKS = 60;
    private static final Map<String, Long> ASKED = new HashMap<>();
    private static Component shown;
    private static long shownAt;

    private ClientRefusals() {
    }

    /** Asks the server to refuse this input out loud. {@code target} is the entity aimed at, or null. */
    public static void deny(String reason, Entity target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        Long before = ASKED.put(reason, now);
        // held or repeated: still the same attempt until the key has been left alone for a moment
        if (before != null && now >= before && now - before < Refusals.DEBOUNCE) return;
        PacketDistributor.sendToServer(new Network.RefuseRequest(reason, target == null ? -1 : target.getId()));
    }

    /** A refusal only this player needs, decided by a screen: the line and the refusal sound, here only. */
    public static void local(Component line) {
        Minecraft mc = Minecraft.getInstance();
        show(line);
        mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.FEEDBACK_REFUSED.get(), 1, 0.8f));
    }

    /** A refusal's line, from the server. */
    public static void show(Component line) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setOverlayMessage(line, false);
        shown = line;
        shownAt = mc.level == null ? 0 : mc.level.getGameTime();
    }

    /** The action bar is under the screen's blur: draw the line above the open screen instead. */
    @SubscribeEvent
    public static void screen(ScreenEvent.Render.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (shown == null || mc.level == null) return;
        long age = mc.level.getGameTime() - shownAt;
        if (age < 0 || age > SHOWN_TICKS) {
            shown = null;
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        var screen = event.getScreen();
        // a line wider than the window (a long list of names, Portuguese at a large GUI scale) wraps, growing upwards
        var lines = mc.font.split(shown, Math.max(40, screen.width - 16));
        int y = screen instanceof AbstractContainerScreen<?> container ? container.getGuiTop() - 14 - 10 * (lines.size() - 1) : 8;
        y = Math.max(4, y);
        int alpha = age > SHOWN_TICKS - 10 ? (int) (255 * (SHOWN_TICKS - age) / 10f) : 255;
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        for (var line : lines) {
            int w = mc.font.width(line);
            int x = (screen.width - w) / 2;
            g.fill(x - 3, y - 2, x + w + 3, y + 10, (alpha * 3 / 4) << 24);
            g.drawString(mc.font, line, x, y, 0xFFFFFF | Math.max(alpha, 8) << 24);
            y += 10;
        }
        g.pose().popPose();
    }
}
