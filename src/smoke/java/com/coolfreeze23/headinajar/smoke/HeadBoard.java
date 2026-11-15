package com.coolfreeze23.headinajar.smoke;

import com.coolfreeze23.headinajar.block.JarredHeadBlockEntity;
import com.coolfreeze23.headinajar.client.JarredHeadRenderer;
import com.coolfreeze23.headinajar.fx.Fx;
import com.coolfreeze23.headinajar.staff.Badged;
import com.coolfreeze23.headinajar.staff.Band;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Dev-only setup for the species board and the staff look, before the server side sets these itself (not real input):
 * <ul>
 * <li>{@code staffbadge X Y Z COLOUR}: the block's badge (hex 0xRRGGBB, or -1), set on the server and sent as the block's
 * update would send it;</li>
 * <li>{@code staffband X Y Z BAND}: a placed head's morale band (engaged, content, disengaged, grievance, trophy, none);</li>
 * <li>{@code headsize X Y Z N}: the client's copy of a placed head's record gets a slime size (the server does not send it yet);</li>
 * <li>{@code staffshake TYPE}: the server sends the head-shake memo for the nearest creature of TYPE, as a refused saw would;</li>
 * <li>{@code lanyards on|off}: the client option that shows badges;</li>
 * <li>{@code headlook TYPE [SIZE]}: what the jar drawing found for TYPE (head or whole, its size and scale).</li>
 * <li>{@code bowlforboard X Y Z MB}: sets a placed head's bowl on the server (a board of every broth step without waiting
 * days of eating);</li>
 * <li>{@code momentforfilm X Y Z EVENT [PARAM]}: sends the plinth's block event (2 bell, 3 nod, 4 wince, 5 spoon, 6 ram,
 * 7 admire) as the server's desk would, to film a moment without waiting for its desk pass;</li>
 * <li>{@code lookforboard X Y Z KEY SNBT}: puts one value into a placed head's record on the server and syncs its look
 * (a sheep's {@code Color}, {@code Sheared}, a goat's horns, a mooshroom's {@code Type}, an allay's {@code HandItems}),
 * for the dressed-species board without feeding and dyeing each head;</li>
 * <li>{@code curvesfornotes}: the spoon's tap and the head's moments sampled, as the client reads them;</li>
 * <li>{@code linesforbench X1 Y1 Z1 X2 Y2 Z2}: every placed head in the box says its first bell line now (the speech
 * line's cost bench);</li>
 * <li>{@code bowlforbench on|off}: the client draws the plinths' bowls or not (the bowl's cost bench, A/B).</li>
 * </ul>
 */
final class HeadBoard {
    private HeadBoard() {
    }

    static String run(Minecraft mc, String[] parts) {
        String[] a = parts.length > 1 ? parts[1].trim().split("\s+") : new String[0];
        try {
            return switch (parts[0]) {
                case "staffbadge" -> badge(mc, pos(a), Integer.decode(a[3]));
                case "staffband" -> band(mc, pos(a), a[3]);
                case "headsize" -> size(mc, pos(a), Integer.parseInt(a[3]));
                case "staffshake" -> shake(mc, a[0]);
                case "bowlforboard" -> onServer(mc, pos(a), head -> head.setBowl(Integer.parseInt(a[3])), "bowl " + a[3] + " mB");
                case "momentforfilm" -> onServer(mc, pos(a), head -> head.getLevel().blockEvent(head.getBlockPos(), head.getBlockState().getBlock(),
                        Integer.parseInt(a[3]), a.length > 4 ? Integer.parseInt(a[4]) : 0), "event " + a[3]);
                case "lookforboard" -> {
                    var value = snbt(String.join(" ", java.util.Arrays.copyOfRange(a, 4, a.length)));
                    yield onServer(mc, pos(a), head -> {
                        head.record().put(a[3], value);
                        head.recordChanged();
                    }, a[3] + " = " + value);
                }
                case "linesforbench" -> {
                    // every placed head in the box says its first bell line at once, as forty rung heads would
                    BlockPos from = pos(a), to = new BlockPos(Integer.parseInt(a[3]), Integer.parseInt(a[4]), Integer.parseInt(a[5]));
                    var server = mc.getSingleplayerServer();
                    server.execute(() -> {
                        var level = server.getLevel(mc.level.dimension());
                        if (level == null) return;
                        for (BlockPos p : BlockPos.betweenClosed(from, to)) {
                            if (level.getBlockEntity(p) instanceof JarredHeadBlockEntity head && head.entityType() != null) {
                                var line = com.coolfreeze23.headinajar.block.HeadSpeak.bell(head, 1, null);
                                head.say(line[0], line[1]);
                            }
                        }
                    });
                    yield "linesforbench " + from.toShortString() + " .. " + to.toShortString() + " -> said";
                }
                case "bowlforbench" -> {
                    com.coolfreeze23.headinajar.client.PlinthBowl.drawn = a[0].equals("on");
                    yield "bowlforbench -> " + com.coolfreeze23.headinajar.client.PlinthBowl.drawn;
                }
                case "curvesfornotes" -> "curves -> " + com.coolfreeze23.headinajar.client.PlinthBowl.describe() + "\n"
                        + com.coolfreeze23.headinajar.client.HeadMoments.describe();
                case "headevents" -> {
                    // what the client last saw of a placed head's block events: ticks since each (1 refuse .. 7 admire)
                    if (!(mc.level.getBlockEntity(pos(a)) instanceof com.coolfreeze23.headinajar.block.JarredHeadBlockEntity head)) yield "headevents -> no head";
                    long now = mc.level.getGameTime();
                    StringBuilder out = new StringBuilder("headevents " + a[0] + " " + a[1] + " " + a[2] + " ->");
                    String[] names = {"", "refuse", "bell", "nod", "wince", "spoon", "ram", "admire"};
                    for (int id = 1; id < names.length; id++) {
                        long since = (long) head.since(id, now);
                        out.append(" ").append(names[id]).append(" ").append(since > 1_000_000 ? "never" : since + " ago");
                    }
                    yield out.toString();
                }
                case "lanyards" -> {
                    com.coolfreeze23.headinajar.Config.LANYARDS.set(a[0].equals("on"));
                    yield "lanyards -> " + com.coolfreeze23.headinajar.Config.LANYARDS.get();
                }
                default -> look(a);
            };
        } catch (RuntimeException e) {
            return parts[0] + " -> failed: " + e;
        }
    }

    private static net.minecraft.nbt.Tag snbt(String text) {
        try {
            return net.minecraft.nbt.TagParser.parseTag("{v:" + text + "}").get("v");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    /** Runs {@code change} on the server's head at {@code pos}. */
    private static String onServer(Minecraft mc, BlockPos pos, java.util.function.Consumer<JarredHeadBlockEntity> change, String what) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            var level = server.getLevel(mc.level.dimension());
            if (level != null && level.getBlockEntity(pos) instanceof JarredHeadBlockEntity head) change.accept(head);
        });
        return pos.toShortString() + " -> " + what;
    }

    private static BlockPos pos(String[] a) {
        return new BlockPos(Integer.parseInt(a[0]), Integer.parseInt(a[1]), Integer.parseInt(a[2]));
    }

    private static String badge(Minecraft mc, BlockPos pos, int colour) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            var level = server.getLevel(mc.level.dimension());
            if (level != null && level.getBlockEntity(pos) instanceof Badged b) {
                b.setBadge(colour);
                level.getBlockEntity(pos).setChanged();
                level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
            }
        });
        return "staffbadge " + pos.toShortString() + " -> " + (colour < 0 ? "none" : String.format("#%06X", colour));
    }

    private static String band(Minecraft mc, BlockPos pos, String name) {
        Band band = Band.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            var level = server.getLevel(mc.level.dimension());
            if (level != null && level.getBlockEntity(pos) instanceof JarredHeadBlockEntity head) head.show(head.badge(), band);
        });
        return "staffband " + pos.toShortString() + " -> " + band;
    }

    private static String size(Minecraft mc, BlockPos pos, int size) {
        if (mc.level.getBlockEntity(pos) instanceof JarredHeadBlockEntity head) {
            head.entityTag().putInt("Size", size - 1);
            return "headsize " + pos.toShortString() + " -> " + size;
        }
        return "headsize " + pos.toShortString() + " -> no head there";
    }

    private static String shake(Minecraft mc, String type) {
        var server = mc.getSingleplayerServer();
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(uuid);
            if (player == null) return;
            Entity best = null;
            for (Entity e : player.serverLevel().getEntities(player, player.getBoundingBox().inflate(16))) {
                if (!BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().contains(type)) continue;
                if (best == null || e.distanceToSqr(player) < best.distanceToSqr(player)) best = e;
            }
            if (best != null) Fx.send(player.serverLevel(), new Fx(Fx.STAFF_SHAKE, best.getBoundingBox().getCenter(), Vec3.ZERO, -1, best.getId(), 0, 0));
        });
        return "staffshake " + type + " -> sent";
    }

    private static String look(String[] a) {
        var type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(a[0].contains(":") ? a[0] : "minecraft:" + a[0])).orElse(null);
        if (type == null) return "headlook " + a[0] + " -> no such creature";
        int size = a.length > 1 ? Integer.parseInt(a[1]) : 0;
        return "headlook " + a[0] + (size > 0 ? " " + size : "") + " -> " + JarredHeadRenderer.describe(type, size);
    }
}
