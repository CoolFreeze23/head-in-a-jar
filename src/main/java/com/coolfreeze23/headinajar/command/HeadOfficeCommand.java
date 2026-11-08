package com.coolfreeze23.headinajar.command;

import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.HeadRegistry;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.UUID;

/**
 * /headoffice trust add|remove|list: who may open your corpse and handle your head.
 * /headoffice status [player]; and for operators, /headoffice separate &lt;player&gt; and /headoffice restore &lt;player&gt;.
 */
public final class HeadOfficeCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("headoffice")
                .then(Commands.literal("trust")
                        .then(Commands.literal("add").then(Commands.argument("players", GameProfileArgument.gameProfile())
                                .executes(c -> trust(c, true))))
                        .then(Commands.literal("remove").then(Commands.argument("players", GameProfileArgument.gameProfile())
                                .executes(c -> trust(c, false))))
                        .then(Commands.literal("list").executes(HeadOfficeCommand::listTrust)))
                .then(Commands.literal("courier").executes(ctx -> {
                    var player = ctx.getSource().getPlayerOrException();
                    ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable(com.coolfreeze23.headinajar.form.Courier.book(player)), false);
                    return 1;
                }))
                .then(Commands.literal("status")
                        .executes(c -> status(c, c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player()).requires(s -> s.hasPermission(2))
                                .executes(c -> status(c, EntityArgument.getPlayer(c, "player")))))
                .then(Commands.literal("separate").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                            ServerPlayer p = EntityArgument.getPlayer(c, "player");
                            boolean ok = FormActions.separate(p, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()),
                                    new ItemStack(ModRegistry.RELAY_GOGGLES.get()), new ItemStack(ModRegistry.COLLAR_CAM.get()), p.position(), null);
                            c.getSource().sendSuccess(() -> Component.translatable(ok ? "command.headinajar.separated" : "command.headinajar.failed", p.getDisplayName()), true);
                            return ok ? 1 : 0;
                        })))
                .then(Commands.literal("restore").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                            ServerPlayer p = EntityArgument.getPlayer(c, "player");
                            JarState state = FormActions.state(p);
                            if (!state.carryingHead() && state.form() == com.coolfreeze23.headinajar.form.Form.LINKED) state.setCarryingHead(true);
                            boolean ok = FormActions.suture(p, state.body() == null ? null
                                    : p.serverLevel().getEntity(state.body()) instanceof com.coolfreeze23.headinajar.entity.HeadlessBody b ? b : null);
                            c.getSource().sendSuccess(() -> Component.translatable(ok ? "command.headinajar.restored" : "command.headinajar.failed", p.getDisplayName()), true);
                            return ok ? 1 : 0;
                        }))));
    }

    private static int trust(CommandContext<CommandSourceStack> c, boolean add) throws CommandSyntaxException {
        ServerPlayer self = c.getSource().getPlayerOrException();
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(c, "players");
        HeadRegistry registry = HeadRegistry.get(self);
        int n = 0;
        for (GameProfile profile : profiles) {
            if (profile.getId().equals(self.getUUID())) continue;
            if (registry.setTrust(self.getUUID(), profile.getId(), add)) n++;
            c.getSource().sendSuccess(() -> Component.translatable(add ? "command.headinajar.trusted" : "command.headinajar.untrusted", profile.getName()), false);
        }
        return n;
    }

    private static int listTrust(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer self = c.getSource().getPlayerOrException();
        var names = new StringBuilder();
        for (UUID id : HeadRegistry.get(self).trusted(self.getUUID())) {
            String name = c.getSource().getServer().getProfileCache() == null ? id.toString()
                    : c.getSource().getServer().getProfileCache().get(id).map(GameProfile::getName).orElse(id.toString());
            if (!names.isEmpty()) names.append(", ");
            names.append(name);
        }
        c.getSource().sendSuccess(() -> Component.translatable("command.headinajar.trust_list", names.isEmpty() ? "-" : names.toString()), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> c, ServerPlayer player) {
        JarState state = FormActions.state(player);
        HeadRegistry.Head head = HeadRegistry.get(player).head(player.getUUID());
        HeadRegistry.Body body = HeadRegistry.get(player).body(player.getUUID());
        String headText = head == null ? "-" : head.where().name().toLowerCase(java.util.Locale.ROOT) + " " + blocks(head.spot());
        String bodyText = body == null ? "-" : (body.corpse() ? "corpse " : "standing ") + blocks(body.spot());
        c.getSource().sendSuccess(() -> Component.translatable("command.headinajar.status", player.getDisplayName(),
                state.form().getSerializedName(), state.quality(), headText, bodyText, state.charge()), false);
        return 1;
    }

    private static String blocks(HeadRegistry.Spot spot) {
        return String.format("%s %.0f %.0f %.0f", spot.dimension().location(), spot.pos().x, spot.pos().y, spot.pos().z);
    }

    private HeadOfficeCommand() {
    }
}
