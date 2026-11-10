package com.coolfreeze23.headinajar.smoke.mixin;

import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Dev-only: lets the smoke harness type characters the way GLFW's char callback does. */
@Mixin(KeyboardHandler.class)
public interface KeyboardHandlerAccess {
    @Invoker("charTyped")
    void hij$charTyped(long window, int codePoint, int modifiers);
}
