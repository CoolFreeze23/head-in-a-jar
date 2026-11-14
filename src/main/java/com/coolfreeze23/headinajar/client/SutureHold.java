package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The use button still held after the head went back on. Once an item stops being used, the game uses it again every
 * few ticks for as long as the button stays down, so a Suture Kit that had just put the head back on (held to sew, or
 * used by the jar on its body) was tried again on a head that is already on, and said so. That repeat is left
 * unanswered until the button is let go; a fresh press is answered as before. A cooldown on the kit would only delay
 * the repeat to the cooldown's end.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID, value = Dist.CLIENT)
public final class SutureHold {
    private static boolean held;
    private static Form last;

    private SutureHold() {
    }

    /** Before the tick's keys are handled, after the server's news of the form has arrived. */
    @SubscribeEvent
    public static void tick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            held = false;
            last = null;
            return;
        }
        Form form = mc.player.getData(ModRegistry.JAR_STATE).form();
        boolean down = mc.options.keyUse.isDown();
        boolean sewing = mc.player.isUsingItem() && mc.player.getUseItem().is(ModRegistry.SUTURE_KIT.get());
        if (sewing || down && form == Form.BODY && last != null && last != Form.BODY) held = true;
        else if (!down) held = false;
        last = form;
    }

    /** Whether the use button has stayed down since this client's player last sewed with a kit or got their head back. */
    public static boolean held() {
        return held;
    }
}
