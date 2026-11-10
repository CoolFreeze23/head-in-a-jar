package com.coolfreeze23.headinajar.smoke.mixin;

import com.coolfreeze23.headinajar.smoke.Observer;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dev-only: chat lines the client shows. */
@Mixin(ChatComponent.class)
public class ChatObserveMixin {
    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V", at = @At("HEAD"))
    private void hij$chat(Component component, MessageSignature signature, GuiMessageTag tag, CallbackInfo callback) {
        Observer.text("chat", component);
    }
}
