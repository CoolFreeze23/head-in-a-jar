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
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
        } else if (state.form() == Form.LINKED) {
            // a headless body: the Collar Cam layer draws what sits on the neck
            event.getRenderer().getModel().head.visible = false;
            event.getRenderer().getModel().hat.visible = false;
        }
    }

    /** A host's head is gone: skip its head part while it draws. */
    @SubscribeEvent
    public static void renderHostPre(net.neoforged.neoforge.client.event.RenderLivingEvent.Pre<?, ?> event) {
        if (com.coolfreeze23.headinajar.host.Hosts.isHost(event.getEntity())) HostHeads.hide(event.getRenderer().getModel());
    }

    @SubscribeEvent
    public static void renderHostPost(net.neoforged.neoforge.client.event.RenderLivingEvent.Post<?, ?> event) {
        HostHeads.reveal();
    }

    @SubscribeEvent
    public static void renderPlayerPost(RenderPlayerEvent.Post event) {
        event.getRenderer().getModel().head.visible = true;
    }

    @SubscribeEvent
    public static void cameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Player player = Minecraft.getInstance().player;
        // the botched cut: the head went in upside down
        if (player != null && state(player).rollTicks() > 0) event.setRoll(180);
        // a jar lying on its side sees the world sideways
        else if (player != null && state(player).tipped() && Minecraft.getInstance().options.getCameraType().isFirstPerson()) event.setRoll(90);
    }

    @SubscribeEvent
    public static void renderHand(RenderHandEvent event) {
        Player player = Minecraft.getInstance().player;
        if (player != null && FormEvents.lockedOut(player)) {
            event.setCanceled(true);
            return;
        }
        // carrying your head in both arms: draw that instead of the hands
        if (player != null && HeadCarry.inArms(player)) {
            if (event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) {
                HeadCarry.drawFirstPerson(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), event.getPartialTick(), event.getEquipProgress());
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void movement(MovementInputUpdateEvent event) {
        Player player = event.getEntity();
        JarState state = state(player);
        Input input = event.getInput();
        if (state.form() == Form.SOCKET) {
            stop(input, true);
        } else if (state.form() == Form.HOSTED) {
            // a host below 25% sync is clumsy: now and then its legs don't listen
            var host = player.getVehicle();
            if (ClientHooks.handshakeActive()) stop(input, true);
            else if (host != null && com.coolfreeze23.headinajar.host.Hosts.isHost(host) && com.coolfreeze23.headinajar.host.Hosts.data(host).effectiveSync() < 25
                    && (player.tickCount / 10 + host.getId()) % 20 == 0) stop(input, false);
        } else if (state.form() == Form.LINKED) {
            // a weak signal makes the body stutter
            int q = state.quality();
            long t = player.level().getGameTime();
            if (q < 20 && t % 20 < 4 || q < 40 && t % 40 < 2) stop(input, true);
        } else if (state.form() == Form.JAR) {
            // in a boat or a minecart the vehicle does the moving
            if (player.getAbilities().flying || player.isPassenger()) return;
            // wheels drive and a tipped jar rolls: both move, neither jumps
            if (state.wheels() && state.has(ModuleType.WHEELS) || state.tipped()) {
                input.jumping = false;
                return;
            }
            if (state.has(ModuleType.SPIDER_LEGS)) return;
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
        if (state.form() == Form.LINKED && state.quality() < 20 && player.getRandom().nextInt(4) == 0) {
            // packet loss: a click doesn't arrive
            event.setCanceled(true);
            event.setSwingHand(false);
            player.displayClientMessage(Component.translatable("hud.headinajar.packet_loss").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!state.form().isJarred()) return;
        if (event.isAttack() && state.heldEntity() >= 0) {
            event.setCanceled(true);
            event.setSwingHand(false);
            PacketDistributor.sendToServer(new Network.JarAction(Network.Action.THROW));
            return;
        }
        if (!FormEvents.lockedOut(player)) return;
        if (state.form() == Form.HOSTED && event.isAttack()) return;
        if (state.form() == Form.PILOT) {
            // the mouse buttons belong to the mecha's arms (sent by PilotClient)
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        // without arms a jar can still reach for its own body, a body it can climb into (a host, a shell, a mecha) and the
        // machines built for heads
        boolean reachable = event.isUseItem() && (mc.hitResult instanceof EntityHitResult hit
                && (hit.getEntity() instanceof HeadlessBody || (com.coolfreeze23.headinajar.host.Hosts.isHost(hit.getEntity())
                        || com.coolfreeze23.headinajar.host.Hosts.headless(hit.getEntity())) && state.form() == Form.JAR
                || FormEvents.jarCanBoard(player, hit.getEntity()))
                || headMachine(mc, player) && !(player.isSecondaryUseActive() && (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty())));
        if (!reachable) {
            event.setCanceled(true);
            event.setSwingHand(false);
            // the Nudge: the server says why (and bystanders hear it), with the line for what the jar reached for
            if (state.form() == Form.JAR) {
                var held = player.getItemInHand(event.getHand());
                boolean refill = event.isUseItem() && (mc.hitResult == null || mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.MISS)
                        && (held.is(ModRegistry.SALINE_BOTTLE.get()) || held.is(ModRegistry.GLASS_PATCH.get()));
                ClientRefusals.deny(refill ? "jar.refill_feed" : event.isUseItem() && headMachine(mc, player) ? com.coolfreeze23.headinajar.feedback.Nudge.USE
                                : com.coolfreeze23.headinajar.feedback.Nudge.reason(mc.level, mc.hitResult, event.isAttack()),
                        mc.hitResult instanceof EntityHitResult hit ? hit.getEntity() : null);
            } else if (state.form() == Form.HOSTED && event.isUseItem()) {
                // riding a host: no hands, and one body at a time
                var target = mc.hitResult instanceof EntityHitResult hit ? hit.getEntity() : null;
                ClientRefusals.deny(target != null && target != player.getVehicle() && com.coolfreeze23.headinajar.host.Hosts.isHost(target)
                        ? com.coolfreeze23.headinajar.host.HostEvents.SECOND_HOST : com.coolfreeze23.headinajar.host.HostEvents.NO_HANDS, target);
            }
        }
    }

    /** Whether the crosshair is on one of the mod's blocks that a head can work without an arm. */
    private static boolean headMachine(Minecraft mc, Player player) {
        return mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult block && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                && mc.level.getBlockState(block.getBlockPos()).is(FormEvents.HEAD_OPERABLE);
    }

    /** Collar Cam optics while linked: the Wide Lens widens the view, the Zoom Optic zooms x3 while its key is held. */
    @SubscribeEvent
    public static void fov(net.neoforged.neoforge.client.event.ComputeFovModifierEvent event) {
        JarState state = state(event.getPlayer());
        if (state.form() != Form.LINKED) return;
        float fov = event.getNewFovModifier();
        if (com.coolfreeze23.headinajar.item.QuantumItems.has(state.collar(), com.coolfreeze23.headinajar.item.QuantumItems.Upgrade.WIDE_LENS)) fov *= 1.15f;
        if (ClientSetup.ZOOM.isDown() && com.coolfreeze23.headinajar.item.QuantumItems.has(state.collar(), com.coolfreeze23.headinajar.item.QuantumItems.Upgrade.ZOOM_OPTIC)) fov /= 3f;
        event.setNewFovModifier(fov);
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        JarState state = state(mc.player);
        while (ClientSetup.JAR_MENU.consumeClick()) {
            // in a mecha, sneak + J is the eject handle
            if (state.form() == Form.SOCKET && mc.options.keyShift.isDown()) {
                PacketDistributor.sendToServer(new Network.SocketInput(2, 0, 0));
            } else if (state.form().isJarred() && !(state.form() == Form.PILOT && mc.options.keyShift.isDown())) {
                PacketDistributor.sendToServer(new Network.JarAction(Network.Action.OPEN_MENU));
            } else if (state.form() == Form.LINKED) {
                ClientRefusals.deny("jar.menu_linked", null);
            }
        }
        while (ClientSetup.ABILITY.consumeClick()) {
            if (ClientHooks.handshakeActive()) ClientHooks.press();
            else if (state.form() == Form.SOCKET) SocketClient.pulse();
            else if (state.form() == Form.JAR && state.heldEntity() < 0 && !state.fitted(ModuleType.TELEKINESIS) && ClientSetup.ABILITY.same(mc.options.keySprint)) {
                // the ability shares its key with sprint and there is no Telekinesis: the press was a sprint, not a request
            } else if (state.form().isJarred() || state.form() == Form.BODY || state.form() == Form.LINKED) {
                // a press of a key that is also sprint says so: the server tells the player once that it does both
                PacketDistributor.sendToServer(new Network.JarAction(ClientSetup.ABILITY.same(mc.options.keySprint) ? Network.Action.ABILITY_SHARED : Network.Action.ABILITY));
            }
        }
        while (ClientSetup.SECONDARY.consumeClick()) {
            if (state.form() == Form.SOCKET) SocketClient.nextFace();
            else if (state.form() == Form.HOSTED || state.form() == Form.BODY || state.form() == Form.LINKED || state.form() == Form.PILOT || state.form() == Form.JAR) {
                PacketDistributor.sendToServer(new Network.JarAction(Network.Action.SECONDARY));
            }
        }
        ClientHooks.tick();
        PilotClient.tick();
        while (ClientSetup.DRIVE.consumeClick()) {
            if (state.form() == Form.JAR) PacketDistributor.sendToServer(new Network.JarAction(Network.Action.DRIVE));
            else if (state.form() == Form.LINKED) ClientRefusals.deny("drive.linked", null);
            else if (state.form() == Form.HOSTED || state.form() == Form.PILOT) ClientRefusals.deny(com.coolfreeze23.headinajar.host.HostEvents.GET_DOWN, null);
        }
        while (ClientSetup.SWAP.consumeClick()) {
            // in a mecha's cockpit the swap key opens the Cargo Hold; on a host it is refused out loud by the server
            if (state.form() == Form.LINKED || state.form() == Form.JAR || state.form() == Form.PILOT || state.form() == Form.HOSTED) {
                PacketDistributor.sendToServer(new Network.JarAction(Network.Action.SWAP));
            } else if (state.form().isJarred()) ClientRefusals.deny("swap.riding", null);
        }
        LinkHud.tick(mc.player);
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
