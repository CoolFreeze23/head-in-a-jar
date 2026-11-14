package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * What a change of form does on the player's own client.
 * <ul>
 * <li>Movement keys held when the form changes (a link break, a swap, the body dying, an eject, the operation, getting
 * on or off a host) don't carry into the new form: each reads as released until it is let go and pressed again.</li>
 * <li>Getting into one of the mod's bodies (a host, a shell, a mecha) shows how to get out of that one, with the
 * player's own keys, in place of vanilla's dismount hint.</li>
 * </ul>
 */
public final class FormChangeClient {
    private static Form lastForm;
    private static Entity lastVehicle;
    /** The way-out line for the body just boarded, and how many more ticks it is put back over vanilla's hint. */
    private static Component boardLine;
    private static int boardTicks;
    /** Movement keys still physically held since the last change of form. */
    private static final List<KeyMapping> HELD = new ArrayList<>();

    private FormChangeClient() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            lastForm = null;
            lastVehicle = null;
            HELD.clear();
            return;
        }
        Form form = mc.player.getData(ModRegistry.JAR_STATE).form();
        if (lastForm != null && form != lastForm) hold(mc);
        lastForm = form;
        release(mc);
        boarding(mc);
        // vanilla's hint can land after ours (its passengers packet may come in a later frame): keep ours on top a while
        if (boardTicks > 0) {
            boardTicks--;
            mc.gui.setOverlayMessage(boardLine, false);
        }
    }

    /** Every frame, as soon as the passengers packet that put vanilla's line up has been handled, before it is drawn. */
    @SubscribeEvent
    public static void frame(net.neoforged.neoforge.client.event.RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) boarding(mc);
    }

    private static void boarding(Minecraft mc) {
        Entity vehicle = mc.player.getVehicle();
        if (vehicle == lastVehicle) return;
        lastVehicle = vehicle;
        boardLine = vehicle == null ? null : boarded(vehicle);
        boardTicks = boardLine == null ? 0 : 10;
        if (boardLine != null) mc.gui.setOverlayMessage(boardLine, false);
    }

    private static KeyMapping[] movement(Minecraft mc) {
        var o = mc.options;
        return new KeyMapping[]{o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump, o.keySprint, o.keyShift};
    }

    /** The form just changed: every movement key down now has to be let go before it counts again. */
    private static void hold(Minecraft mc) {
        for (KeyMapping key : movement(mc)) {
            if (key.isDown() && !HELD.contains(key)) HELD.add(key);
        }
        release(mc);
    }

    /** Keeps the held keys up for the game (key repeat and a closing screen set them down again) until they are let go. */
    private static void release(Minecraft mc) {
        HELD.removeIf(key -> {
            if (!physicallyDown(mc, key)) return true;
            key.setDown(false);
            return false;
        });
    }

    private static boolean physicallyDown(Minecraft mc, KeyMapping mapping) {
        InputConstants.Key key = mapping.getKey();
        long window = mc.getWindow().getWindow();
        return switch (key.getType()) {
            case KEYSYM -> key.getValue() != InputConstants.UNKNOWN.getValue() && InputConstants.isKeyDown(window, key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
            default -> false;
        };
    }

    /** True while a key the player held through a change of form is still waiting to be let go (for tests and HUD). */
    public static boolean holding(KeyMapping key) {
        return HELD.contains(key);
    }

    /** The way-out line for what the player has just got into, said in place of vanilla's (null: vanilla's stands). */
    public static Component mountLine() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null || mc.player.getVehicle() == null ? null : boarded(mc.player.getVehicle());
    }

    /**
     * Vanilla says "press sneak to dismount" for any vehicle; a mecha is left by holding sneak with the jar menu key, and a
     * host or shell by sneaking. Put up for the first half second aboard, it stays over vanilla's line.
     */
    private static Component boarded(Entity vehicle) {
        if (vehicle instanceof MechaEntity) {
            return Component.translatable("hud.headinajar.board.mecha", Component.keybind("key.sneak"), Component.keybind(ClientSetup.JAR_MENU.getName()));
        }
        return Hosts.isHost(vehicle) ? Component.translatable("hud.headinajar.board.host", Component.keybind("key.sneak")) : null;
    }
}
