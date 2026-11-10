package com.coolfreeze23.headinajar.smoke.mixin;

import com.coolfreeze23.headinajar.smoke.Observer;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dev-only: action-bar text and titles the client shows. */
@Mixin(Gui.class)
public class GuiObserveMixin {
    @Inject(method = "setOverlayMessage", at = @At("HEAD"))
    private void hij$actionBar(Component component, boolean animateColor, CallbackInfo callback) {
        Observer.text("actionbar", component);
    }

    @Inject(method = "setTitle", at = @At("HEAD"))
    private void hij$title(Component component, CallbackInfo callback) {
        Observer.text("title", component);
    }

    @Inject(method = "setSubtitle", at = @At("HEAD"))
    private void hij$subtitle(Component component, CallbackInfo callback) {
        Observer.text("subtitle", component);
    }
}
