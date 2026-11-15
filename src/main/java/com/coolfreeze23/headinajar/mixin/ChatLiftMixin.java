package com.coolfreeze23.headinajar.mixin;

import com.coolfreeze23.headinajar.client.ChatRoom;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The chat standing on the jar's bars ({@link ChatRoom}): the open chat is lifted as the closed one is (the chat screen
 * draws it itself, not through the CHAT layer), and the mouse is matched against the lines where they are drawn, so
 * hovering and clicking a memo's link land on the line under the pointer.
 */
@Mixin(ChatComponent.class)
public abstract class ChatLiftMixin {
    @ModifyVariable(method = "screenToChatY", at = @At("HEAD"), argsOnly = true)
    private double headinajar$liftedLines(double y) {
        return y + ChatRoom.drawnLift();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void headinajar$liftOpenChat(GuiGraphics graphics, int tickCount, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
        if (focused) ChatRoom.beginOpen(graphics);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void headinajar$endOpenChat(GuiGraphics graphics, int tickCount, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
        if (focused) ChatRoom.endOpen(graphics);
    }
}
