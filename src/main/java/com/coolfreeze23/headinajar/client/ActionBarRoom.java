package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The action bar (DOLORES's lines at the chair, the mod's refusals) sits just above the hotbar, where the bottom of a
 * tall chat message (a memo of four or five lines) also reaches: drawn there, each crossed the other. While chat lines
 * are showing under it, the action bar steps up to just above them; with no chat in the way it stays where vanilla puts
 * it. Never higher than a little under the crosshair.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID, value = Dist.CLIENT)
public final class ActionBarRoom {
    /** Vanilla's gap under the chat (ChatComponent's bottom margin) and the action bar's lowest place over the bottom. */
    private static final int CHAT_BOTTOM = 40, BAR_BOTTOM = 68;
    private static boolean lifted;

    private ActionBarRoom() {
    }

    // last, so no later listener can cancel the layer (and its Post) once the pose is pushed
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void before(RenderGuiLayerEvent.Pre event) {
        if (!event.getName().equals(VanillaGuiLayers.OVERLAY_MESSAGE)) return;
        lifted = false;
        int lift = lift(event.getGuiGraphics().guiWidth(), event.getGuiGraphics().guiHeight());
        if (lift <= 0) return;
        event.getGuiGraphics().pose().pushPose();
        event.getGuiGraphics().pose().translate(0, -lift, 0);
        lifted = true;
    }

    @SubscribeEvent
    public static void after(RenderGuiLayerEvent.Post event) {
        if (!lifted || !event.getName().equals(VanillaGuiLayers.OVERLAY_MESSAGE)) return;
        event.getGuiGraphics().pose().popPose();
        lifted = false;
    }

    /** How far up the action bar has to go to clear the chat lines showing now; 0 when nothing is in the way. */
    static int lift(int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        Gui gui = mc.gui;
        if (gui.overlayMessageString == null || gui.overlayMessageTime <= 0) return 0;
        int top = chatTop(mc, height);
        if (top >= height - CHAT_BOTTOM) return 0;
        ChatComponent chat = gui.getChat();
        double scale = chat.getScale();
        int chatRight = (int) Math.ceil((Mth.ceil(chat.getWidth() / scale) + 12) * scale);
        int text = mc.font.width(gui.overlayMessageString);
        // the bar as vanilla (with NeoForge's lift over taller HUD columns) draws it: its backdrop 6 over and 7 under its line
        int line = height - Math.max(Math.max(gui.leftHeight, gui.rightHeight) + 9, BAR_BOTTOM);
        int left = width / 2 - text / 2 - 2, bottom = line + 7;
        if (left >= chatRight || bottom <= top) return 0;
        int lift = bottom - top + 2;
        // never up past a little under the crosshair
        int highest = line - (height / 2 + 24);
        return Math.max(0, Math.min(lift, highest));
    }

    /** The top of the chat lines drawn now (screen y), or the chat's bottom when none is showing. */
    private static int chatTop(Minecraft mc, int height) {
        ChatComponent chat = mc.gui.getChat();
        if (mc.options.chatVisibility().get() == net.minecraft.world.entity.player.ChatVisiblity.HIDDEN) return height - CHAT_BOTTOM;
        double scale = chat.getScale();
        int lineHeight = (int) (9.0 * (mc.options.chatLineSpacing().get() + 1.0));
        int ticks = mc.gui.getGuiTicks();
        boolean focused = chat.isChatFocused();
        int shown = 0;
        for (int i = 0; i < chat.getLinesPerPage() && i + chat.chatScrollbarPos < chat.trimmedMessages.size(); i++) {
            GuiMessage.Line line = chat.trimmedMessages.get(i + chat.chatScrollbarPos);
            // as ChatComponent: a line shows for 200 ticks after it came, fading over its last 20 (counted until it is gone)
            if (line != null && (focused || ticks - line.addedTime() < 200)) shown = i + 1;
        }
        if (shown == 0) return height - CHAT_BOTTOM;
        int bottom = Mth.floor((height - CHAT_BOTTOM) / scale);
        // standing on the jar's bars when they are in its way (ChatRoom)
        return (int) Math.floor((bottom - shown * lineHeight) * scale) - ChatRoom.lift();
    }
}
