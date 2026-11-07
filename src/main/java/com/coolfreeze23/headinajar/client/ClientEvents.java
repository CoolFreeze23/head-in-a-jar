package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.entity.HeadlessBody;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormEvents;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.module.ModuleType;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClientEvents {
    private static JarState state(Player player) {
        return player.getData(ModRegistry.JAR_STATE);
    }

    @SubscribeEvent
    public static void renderPlayer(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player)) return;
        JarState state = state(player);
        if (JarRenderer.isJar(player, state)) {
            event.setCanceled(true);
            JarRenderer.render(player, state, event.getPartialTick(), event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
        }
    }

    @SubscribeEvent
    public static void renderHand(RenderHandEvent event) {
        Player player = Minecraft.getInstance().player;
        if (player != null && FormEvents.lockedOut(player)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void movement(MovementInputUpdateEvent event) {
        Player player = event.getEntity();
        JarState state = state(player);
        Input input = event.getInput();
        if (state.form() == Form.SOCKET) {
            stop(input, true);
        } else if (state.form() == Form.JAR) {
            if (player.getAbilities().flying) return;
            if (state.has(ModuleType.HOP_SPRINGS) || player.isInWater() && state.has(ModuleType.WATER_JET)) {
                // the springs hop the jar along whenever it moves
                boolean moving = input.forwardImpulse != 0 || input.leftImpulse != 0;
                if (moving && player.onGround()) input.jumping = true;
            } else {
                // a bare jar can only start a flight, never walk
                stop(input, !state.has(ModuleType.PROPELLER));
            }
        }
    }

    private static void stop(Input input, boolean jump) {
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.up = input.down = input.left = input.right = false;
        if (jump) input.jumping = false;
    }

    @SubscribeEvent
    public static void interactionKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        JarState state = state(player);
        if (!state.form().isJarred()) return;
        if (event.isAttack() && state.heldEntity() >= 0) {
            event.setCanceled(true);
            event.setSwingHand(false);
            PacketDistributor.sendToServer(new Network.JarAction(Network.Action.THROW));
            return;
        }
        if (!FormEvents.lockedOut(player)) return;
        boolean ownBody = event.isUseItem() && mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof HeadlessBody;
        if (!ownBody) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        JarState state = state(mc.player);
        while (ClientSetup.JAR_MENU.consumeClick()) {
            if (state.form().isJarred()) PacketDistributor.sendToServer(new Network.JarAction(Network.Action.OPEN_MENU));
        }
        while (ClientSetup.ABILITY.consumeClick()) {
            if (state.form().isJarred()) PacketDistributor.sendToServer(new Network.JarAction(Network.Action.ABILITY));
        }
    }

    @SubscribeEvent
    public static void guiLayer(RenderGuiLayerEvent.Pre event) {
        Player player = Minecraft.getInstance().player;
        if (player != null && event.getName().equals(VanillaGuiLayers.FOOD_LEVEL) && state(player).form().isJarred()) {
            event.setCanceled(true);
        }
    }

    private ClientEvents() {
    }
}
