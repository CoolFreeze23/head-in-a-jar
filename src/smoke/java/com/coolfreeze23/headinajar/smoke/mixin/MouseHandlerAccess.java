package com.coolfreeze23.headinajar.smoke.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Dev-only: lets the smoke harness feed the mouse callbacks GLFW would call for a real mouse. */
@Mixin(MouseHandler.class)
public interface MouseHandlerAccess {
    @Invoker("onPress")
    void hij$press(long window, int button, int action, int modifiers);

    @Invoker("onScroll")
    void hij$scroll(long window, double xOffset, double yOffset);

    @Invoker("onMove")
    void hij$move(long window, double x, double y);

    @Accessor("mouseGrabbed")
    void hij$setGrabbed(boolean grabbed);
}
