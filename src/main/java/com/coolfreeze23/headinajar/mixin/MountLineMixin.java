package com.coolfreeze23.headinajar.mixin;

import com.coolfreeze23.headinajar.client.FormChangeClient;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Getting into one of the mod's bodies (a mecha, a host, a shell), vanilla's "press sneak to dismount" is never put up,
 * not even for a frame, nor read out by the narrator: the body's own way out is said in its place, at the same moment.
 */
@Mixin(ClientPacketListener.class)
public abstract class MountLineMixin {
    @WrapOperation(method = "handleSetEntityPassengersPacket",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;setOverlayMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private void headinajar$ownMountLine(Gui gui, Component line, boolean animate, Operation<Void> original) {
        Component own = FormChangeClient.mountLine();
        original.call(gui, own != null ? own : line, animate);
    }

    @WrapOperation(method = "handleSetEntityPassengersPacket",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/GameNarrator;sayNow(Lnet/minecraft/network/chat/Component;)V"))
    private void headinajar$ownMountNarration(GameNarrator narrator, Component line, Operation<Void> original) {
        Component own = FormChangeClient.mountLine();
        original.call(narrator, own != null ? own : line);
    }
}
