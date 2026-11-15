package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The jar's three bars (charge, saline, glass) stand above the hotbar's right half, where the bottom of the chat also
 * reaches at the default chat width: a memo's lowest lines were drawn across the saline and glass bars. While the bars
 * show and the chat reaches over them, the chat stands on top of them instead, open or closed (so its lines do not
 * jump when it opens), and the mouse finds its lines where they are drawn ({@code ChatLiftMixin}).
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID, value = Dist.CLIENT)
public final class ChatRoom {
    /** Vanilla's gap under the chat (ChatComponent's bottom margin). */
    private static final int CHAT_BOTTOM = 40;
    /** The lift the chat was last drawn with (what the mouse is matched against), and whether a pose is pushed now. */
    private static int drawnLift;
    private static boolean layerLifted, openLifted;
    /** Dev switch: the chat as it was before it stood clear of the bars (to compare). */
    public static boolean debugOff;

    private ChatRoom() {
    }

    /** A new frame: nothing is lifted until the chat is drawn, and the bars count only once they are drawn again. */
    @SubscribeEvent
    public static void frame(RenderGuiEvent.Pre event) {
        drawnLift = 0;
        ChargeHud.forget();
    }

    // last, so no later listener can cancel the layer (and its Post) once the pose is pushed; a cancelled layer is not seen
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void before(RenderGuiLayerEvent.Pre event) {
        if (!event.getName().equals(VanillaGuiLayers.CHAT)) return;
        layerLifted = false;
        int lift = lift();
        drawnLift = lift;
        if (lift <= 0) return;
        event.getGuiGraphics().pose().pushPose();
        event.getGuiGraphics().pose().translate(0, -lift, 0);
        layerLifted = true;
    }

    @SubscribeEvent
    public static void after(RenderGuiLayerEvent.Post event) {
        if (!layerLifted || !event.getName().equals(VanillaGuiLayers.CHAT)) return;
        event.getGuiGraphics().pose().popPose();
        layerLifted = false;
    }

    /** The open chat (drawn by the chat screen itself, not by the CHAT layer) is lifted the same way. */
    public static void beginOpen(GuiGraphics graphics) {
        openLifted = false;
        int lift = lift();
        drawnLift = lift;
        if (lift <= 0) return;
        graphics.pose().pushPose();
        graphics.pose().translate(0, -lift, 0);
        openLifted = true;
    }

    public static void endOpen(GuiGraphics graphics) {
        if (!openLifted) return;
        graphics.pose().popPose();
        openLifted = false;
    }

    /** How far up the chat was last drawn (screen px): what the mouse's height is moved by to find its line. */
    public static int drawnLift() {
        return drawnLift;
    }

    /** How far up the chat has to stand to clear the jar's bars drawn this frame; 0 when they are not in its way. */
    public static int lift() {
        if (debugOff || !ChargeHud.drewBars()) return 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.chatVisibility().get() == net.minecraft.world.entity.player.ChatVisiblity.HIDDEN) return 0;
        ChatComponent chat = mc.gui.getChat();
        double scale = chat.getScale();
        // the right edge of a line's backdrop, as ChatComponent draws it (4 in, the width, 8 of backdrop past the text)
        int chatRight = (int) Math.ceil((Mth.ceil(chat.getWidth() / scale) + 12) * scale);
        if (chatRight <= ChargeHud.barsLeft()) return 0;
        int height = mc.getWindow().getGuiScaledHeight();
        int chatBottom = (int) Math.floor(Mth.floor((height - CHAT_BOTTOM) / scale) * scale);
        // one clear pixel between the lowest line's backdrop and the top bar's frame
        return Math.max(0, chatBottom - ChargeHud.barsTop() + 1);
    }
}
