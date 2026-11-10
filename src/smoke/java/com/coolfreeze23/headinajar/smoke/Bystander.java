package com.coolfreeze23.headinajar.smoke;

import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.smoke.mixin.ServerGameListenerAccess;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.registration.ChannelAttributes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A stand-in second player on the integrated server (smoke only). It joins through PlayerList.placeNewPlayer like a
 * real player, with a connection that records every packet the server sends it instead of sending it anywhere, and
 * the same NeoForge channel setup as the real client, so the mod's own payloads reach it as they would a second
 * client. Its actions are fed to the server as the packets a client would send (use on a block, use an item,
 * interact with or attack an entity, a container button, a JarAction key payload), so they take the server's real
 * path. It does not fall or walk: place it on the ground.
 */
final class Bystander {
    static final UUID ID = UUID.fromString("5b7d1c1e-0000-4000-8000-00000000b157");
    private static ServerPlayer player;
    private static RecordingConnection connection;
    private static int sequence = 1;

    /** A connection that keeps a record of what was sent to it and drops it. */
    private static final class RecordingConnection extends Connection {
        final List<String> sent = Collections.synchronizedList(new ArrayList<>());

        RecordingConnection() {
            super(PacketFlow.SERVERBOUND);
        }

        @Override
        public void send(Packet<?> packet, PacketSendListener listener, boolean flush) {
            describe(packet, sent);
        }
    }

    private static void describe(Packet<?> packet, List<String> out) {
        if (packet instanceof ClientboundBundlePacket bundle) {
            for (Packet<?> p : bundle.subPackets()) describe(p, out);
        } else if (packet instanceof ClientboundCustomPayloadPacket c) {
            out.add("payload " + c.payload().type().id());
        } else if (packet instanceof ClientboundSoundPacket s) {
            out.add("sound " + s.getSound().value().getLocation());
        } else if (packet instanceof ClientboundSoundEntityPacket s) {
            out.add("sound " + s.getSound().value().getLocation() + " (on entity)");
        } else if (packet instanceof ClientboundLevelParticlesPacket p) {
            out.add("particle " + BuiltInRegistries.PARTICLE_TYPE.getKey(p.getParticle().getType()) + " x" + Math.max(1, p.getCount()));
        } else if (packet instanceof ClientboundSetActionBarTextPacket t) {
            out.add("actionbar \"" + t.text().getString() + "\"");
        } else if (packet instanceof ClientboundSystemChatPacket t) {
            out.add((t.overlay() ? "actionbar \"" : "chat \"") + t.content().getString() + "\"");
        } else if (packet instanceof ClientboundAnimatePacket a) {
            out.add("animate " + a.getAction() + " entity " + a.getId());
        } else if (packet instanceof ClientboundLevelEventPacket e) {
            out.add("levelevent " + e.getType());
        } else {
            out.add(packet.getClass().getSimpleName().replace("Clientbound", "").replace("Packet", ""));
        }
    }

    static boolean present() {
        return player != null && !player.isRemoved();
    }

    static Vec3 position() {
        return present() ? player.position() : null;
    }

    private static void confirmTeleport() {
        ((ServerGameListenerAccess) player.connection).hij$setAwaitingPosition(null);
    }

    private static Entity target(String[] a, int i) {
        // me | nearest TYPE
        if (a[i].equals("me")) return player.server.getPlayerList().getPlayers().stream().filter(p -> p != player).findFirst().orElse(null);
        EntityType<?> type = EntityType.byString(a[i + 1].contains(":") ? a[i + 1] : "minecraft:" + a[i + 1]).orElse(null);
        List<Entity> found = player.serverLevel().getEntities((Entity) null, new AABB(player.blockPosition()).inflate(16),
                e -> e != player && (type == null || e.getType() == type));
        found.sort(java.util.Comparator.comparingDouble(player::distanceToSqr));
        return found.isEmpty() ? null : found.get(0);
    }

    private static BlockPos pos(String[] a, int i) {
        return new BlockPos(Integer.parseInt(a[i]), Integer.parseInt(a[i + 1]), Integer.parseInt(a[i + 2]));
    }

    private static void useBlock(BlockPos pos, Direction face) {
        Vec3 hit = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        player.connection.handleUseItemOn(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, pos, false), sequence++));
        player.connection.handleAnimate(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
    }

    /** Runs on the server thread. Returns a report line. */
    static String run(MinecraftServer server, String[] a) {
        String verb = a[1];
        if (verb.equals("spawn")) {
            if (present()) return "bystander already here";
            GameProfile profile = new GameProfile(ID, "Bystander");
            CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
            var level = server.overworld();
            player = new ServerPlayer(server, level, profile, cookie.clientInformation());
            connection = new RecordingConnection();
            new EmbeddedChannel(connection);
            // the same NeoForge channel setup as the real client, so the mod's payloads may be sent to it
            ServerPlayer real = server.getPlayerList().getPlayers().get(0);
            Connection realConnection = real.connection.getConnection();
            ChannelAttributes.setPayloadSetup(connection, ChannelAttributes.getPayloadSetup(realConnection));
            ChannelAttributes.setConnectionType(connection, ChannelAttributes.getConnectionType(realConnection));
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            float yaw = a.length > 5 ? Float.parseFloat(a[5]) : 0;
            player.teleportTo(level, Double.parseDouble(a[2]) + 0.5, Double.parseDouble(a[3]), Double.parseDouble(a[4]) + 0.5, yaw, 0);
            confirmTeleport();
            return "bystander spawned at " + player.blockPosition().toShortString();
        }
        if (!present()) return "bystander " + verb + " -> no bystander (spawn it first)";
        var level = player.serverLevel();
        switch (verb) {
            case "tp" -> {
                player.stopRiding();
                player.teleportTo(level, Double.parseDouble(a[2]) + 0.5, Double.parseDouble(a[3]), Double.parseDouble(a[4]) + 0.5,
                        a.length > 5 ? Float.parseFloat(a[5]) : player.getYRot(), a.length > 6 ? Float.parseFloat(a[6]) : player.getXRot());
                confirmTeleport();
                return "bystander tp -> " + player.blockPosition().toShortString();
            }
            case "look" -> {
                player.setYRot(Float.parseFloat(a[2]));
                player.setXRot(Float.parseFloat(a[3]));
                player.setYHeadRot(player.getYRot());
                return "bystander look";
            }
            case "gamemode" -> {
                player.setGameMode(GameType.byName(a[2]));
                return "bystander gamemode " + player.gameMode.getGameModeForPlayer();
            }
            case "give", "hold" -> {
                // give ITEM [COUNT] | hold ITEM [COUNT] [offhand]
                var item = BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(a[2].contains(":") ? a[2] : "minecraft:" + a[2]));
                int count = a.length > 3 && a[3].matches("\\d+") ? Integer.parseInt(a[3]) : 1;
                ItemStack stack = new ItemStack(item, count);
                if (verb.equals("give")) player.getInventory().add(stack);
                else player.setItemInHand(line(a).contains("offhand") ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, stack);
                return "bystander " + verb + " " + stack;
            }
            case "useblock" -> {
                // useblock X Y Z [FACE]: right-click a block face (the real ServerboundUseItemOnPacket path)
                Direction face = a.length > 5 ? Direction.byName(a[5]) : Direction.UP;
                useBlock(pos(a, 2), face);
                return "bystander useblock -> vehicle " + vehicle() + ", menu " + player.containerMenu.getClass().getSimpleName();
            }
            case "useitem" -> {
                player.connection.handleUseItem(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, sequence++, player.getYRot(), player.getXRot()));
                return "bystander useitem " + Harness.stackName(player.getMainHandItem());
            }
            case "useentity", "attack" -> {
                // useentity me|nearest TYPE ; attack me|nearest TYPE
                Entity e = target(a, 2);
                if (e == null) return "bystander " + verb + " -> no target";
                if (verb.equals("attack")) {
                    player.connection.handleInteract(ServerboundInteractPacket.createAttackPacket(e, player.isShiftKeyDown()));
                } else {
                    player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(e, player.isShiftKeyDown(), InteractionHand.MAIN_HAND));
                }
                player.connection.handleAnimate(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
                return "bystander " + verb + " " + BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
            }
            case "sneak" -> {
                boolean on = a[2].equals("on");
                player.connection.handlePlayerCommand(new ServerboundPlayerCommandPacket(player,
                        on ? ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY : ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
                player.setShiftKeyDown(on);
                return "bystander sneak " + on;
            }
            case "swing" -> {
                player.connection.handleAnimate(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
                return "bystander swing";
            }
            case "button" -> {
                // button ID: a container button of the menu it has open (as the client's button packet)
                int id = Integer.parseInt(a[2]);
                String menu = player.containerMenu.getClass().getSimpleName();
                player.connection.handleContainerButtonClick(new ServerboundContainerButtonClickPacket(player.containerMenu.containerId, id));
                return "bystander button " + id + " in " + menu;
            }
            case "close" -> {
                player.connection.handleContainerClose(new ServerboundContainerClosePacket(player.containerMenu.containerId));
                return "bystander close";
            }
            case "key" -> {
                // key ACTION: the mod's key payload (open_menu, ability, throw, let_go, swap, secondary, eject, jet, drive)
                var action = Network.Action.valueOf(a[2].toUpperCase(Locale.ROOT));
                player.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(new Network.JarAction(action)));
                return "bystander key " + action + " -> form " + form() + ", vehicle " + vehicle();
            }
            case "chair", "operate" -> {
                // chair X Y Z: stock the chair (jar, goggles, collar, saline, power) and sit in it by a right-click
                // operate X Y Z: the same, then sign Form 27-B with the menu's Sign button
                BlockPos p = pos(a, 2);
                if (!(level.getBlockEntity(p) instanceof com.coolfreeze23.headinajar.block.SeparationChairBlockEntity chair)) return "bystander " + verb + " -> no chair at " + p.toShortString();
                chair.items().setStackInSlot(0, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()));
                chair.items().setStackInSlot(1, new ItemStack(ModRegistry.RELAY_GOGGLES.get()));
                chair.items().setStackInSlot(2, new ItemStack(ModRegistry.COLLAR_CAM.get()));
                chair.items().setStackInSlot(3, new ItemStack(ModRegistry.SALINE_BOTTLE.get(), 4));
                chair.energy().set(50_000);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                useBlock(p, Direction.UP);
                String sat = "sat: vehicle " + vehicle() + ", menu " + player.containerMenu.getClass().getSimpleName();
                if (verb.equals("operate")) {
                    player.connection.handleContainerButtonClick(new ServerboundContainerButtonClickPacket(player.containerMenu.containerId, 0));
                    return "bystander operate -> " + sat + ", running " + chair.running();
                }
                return "bystander chair -> " + sat;
            }
            case "form" -> {
                // form jar|linked|body|compact|wide: setup shortcuts through the mod's own server code
                String r = switch (a[2]) {
                    case "jar" -> "jar " + FormActions.detach(player, 3);
                    case "linked" -> "linked " + FormActions.separate(player, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()),
                            new ItemStack(ModRegistry.RELAY_GOGGLES.get()), new ItemStack(ModRegistry.COLLAR_CAM.get()),
                            player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(1.5)), null);
                    case "body" -> {
                        var bodies = level.getEntitiesOfClass(com.coolfreeze23.headinajar.entity.HeadlessBody.class, player.getBoundingBox().inflate(16));
                        yield "body " + (!bodies.isEmpty() && FormActions.reattach(player, bodies.get(0)));
                    }
                    case "compact", "wide" -> {
                        FormActions.state(player).setCompact(a[2].equals("compact"));
                        player.refreshDimensions();
                        Network.sync(player);
                        yield a[2];
                    }
                    default -> "unknown form " + a[2];
                };
                return "bystander form " + r + " -> form " + form();
            }
            case "fit" -> {
                // fit MODULE,MODULE: put jar modules in its jar (setup), charge N: set its jar charge (setup)
                var state = FormActions.state(player);
                String[] names = a[2].split(",");
                for (int i = 0; i < names.length; i++) {
                    state.setModule(i, new ItemStack(ModRegistry.module(com.coolfreeze23.headinajar.module.ModuleType.valueOf(names[i].trim().toUpperCase(Locale.ROOT)))));
                }
                Network.sync(player);
                return "bystander fit " + a[2];
            }
            case "charge" -> {
                FormActions.state(player).setCharge(Integer.parseInt(a[2]));
                Network.sync(player);
                return "bystander charge " + a[2];
            }
            case "mark" -> {
                connection.sent.clear();
                return "bystander mark";
            }
            case "packets" -> {
                // packets [REGEX]: what the server sent the bystander since the last mark, grouped
                Pattern filter = a.length > 2 ? Pattern.compile(line(a).substring(line(a).indexOf("packets") + 8)) : null;
                Map<String, Integer> counts = new LinkedHashMap<>();
                synchronized (connection.sent) {
                    for (String s : connection.sent) if (filter == null || filter.matcher(s).find()) counts.merge(s, 1, Integer::sum);
                }
                StringBuilder sb = new StringBuilder("bystander packets" + (filter == null ? "" : " /" + filter + "/") + ": ");
                if (counts.isEmpty()) sb.append("NONE");
                counts.forEach((k, v) -> sb.append(k).append(v > 1 ? " x" + v : "").append("; "));
                return sb.toString();
            }
            case "state" -> {
                return "bystander state: at " + player.blockPosition().toShortString() + ", form " + form() + ", vehicle " + vehicle()
                        + ", main " + Harness.stackName(player.getMainHandItem()) + ", health " + player.getHealth()
                        + ", menu " + player.containerMenu.getClass().getSimpleName();
            }
            case "remove" -> {
                remove(server);
                return "bystander removed";
            }
            default -> {
                return "bystander " + verb + " -> unknown";
            }
        }
    }

    private static String line(String[] a) {
        return String.join(" ", a);
    }

    private static String form() {
        try {
            return FormActions.state(player).form().name().toLowerCase(Locale.ROOT);
        } catch (RuntimeException e) {
            return "?";
        }
    }

    private static String vehicle() {
        return player.getVehicle() == null ? "-" : BuiltInRegistries.ENTITY_TYPE.getKey(player.getVehicle().getType()).toString();
    }

    static void remove(MinecraftServer server) {
        if (present()) server.getPlayerList().remove(player);
        player = null;
    }
}
