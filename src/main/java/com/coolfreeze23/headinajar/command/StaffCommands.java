package com.coolfreeze23.headinajar.command;

import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffFile;
import com.coolfreeze23.headinajar.staff.work.Order;
import com.coolfreeze23.headinajar.staff.work.Sites;
import com.coolfreeze23.headinajar.staff.work.WorkPause;
import com.coolfreeze23.headinajar.staff.work.WorkerBrain;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.WorkerIndex;
import com.coolfreeze23.headinajar.staff.work.WorkerLooks;
import com.coolfreeze23.headinajar.staff.work.WorkerPaths;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The operators' staff commands (permission 2), under {@code /headoffice staff}:
 * <ul>
 * <li>{@code list [player]}: one line per member of staff (number, name, species, site, job, status) from Head Office's
 * index, with the creature itself read only when it is loaded; then what staff thinking costs.</li>
 * <li>{@code recall <player>}: every loaded worker of that player puts a personal card back in its rack, drops what it
 * was doing and goes back to its clock.</li>
 * <li>{@code pause}: a server-wide mandatory break for a lag hunt, saved in the world until lifted by the same command.</li>
 * </ul>
 */
public final class StaffCommands {
    private StaffCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("staff").requires(s -> s.hasPermission(2))
                .then(Commands.literal("list")
                        .executes(c -> list(c, null))
                        .then(Commands.argument("player", GameProfileArgument.gameProfile()).executes(c -> list(c, GameProfileArgument.getGameProfiles(c, "player")))))
                .then(Commands.literal("recall")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile()).executes(c -> recall(c, GameProfileArgument.getGameProfiles(c, "player")))))
                .then(Commands.literal("pause").executes(StaffCommands::pause));
    }

    // --- list -------------------------------------------------------------------------------------------------------

    private static int list(CommandContext<CommandSourceStack> c, Collection<GameProfile> who) throws CommandSyntaxException {
        MinecraftServer server = c.getSource().getServer();
        WorkerIndex index = WorkerIndex.get(server);
        List<UUID> employers = new ArrayList<>();
        if (who == null) employers.addAll(index.all().keySet());
        else for (GameProfile p : who) employers.add(p.getId());
        int n = 0;
        for (UUID employer : employers) {
            Collection<WorkerIndex.Entry> staff = index.of(employer);
            String name = nameOf(server, employer);
            if (staff.isEmpty()) {
                c.getSource().sendSuccess(() -> Component.translatable("command.headinajar.staff.none", name), false);
                continue;
            }
            int count = staff.size();
            c.getSource().sendSuccess(() -> Component.translatable("command.headinajar.staff.header", name, count), false);
            for (WorkerIndex.Entry e : staff) {
                Component line = line(server, e);
                c.getSource().sendSuccess(() -> line, false);
                n++;
            }
        }
        if (employers.isEmpty()) c.getSource().sendSuccess(() -> Component.translatable("command.headinajar.staff.nobody"), false);
        double[] brain = WorkerBrain.readCounters(), paths = WorkerPaths.readCounters();
        String ms = String.format(Locale.ROOT, "%.3f", brain[1]), perSecond = String.format(Locale.ROOT, "%.1f", paths[0]);
        c.getSource().sendSuccess(() -> Component.translatable("command.headinajar.staff.cost", ms, perSecond), false);
        return n;
    }

    /** One worker's line: number, name, species, site, job, status. */
    static Component line(MinecraftServer server, WorkerIndex.Entry e) {
        Mob mob = find(server, e.id);
        ResourceLocation id = ResourceLocation.tryParse(e.species);
        Component species = id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id) ? Component.literal(e.species)
                : BuiltInRegistries.ENTITY_TYPE.get(id).getDescription();
        if (mob == null) {
            return Component.translatable("command.headinajar.staff.list", Staff.number(e.number), species, species, place(e.where), "-",
                    Component.translatable("staff.headinajar.status.unloaded"));
        }
        WorkerData d = Workforce.data(mob);
        StaffFile f = Staff.of(mob);
        String job = mob.level() instanceof ServerLevel level ? Order.jobOf(level, d) : null;
        Component jobText = job == null ? Component.literal("-") : Component.translatable("staff.headinajar.job." + job);
        Component status = d.status != null ? Component.translatable(d.status)
                : Component.translatable("staff.headinajar.status." + switch (d.state) {
                    case TO_CLOCK -> "waiting";
                    case SHUTTLE -> "detail";
                    default -> d.state.getSerializedName();
                });
        return Component.translatable("command.headinajar.staff.list", Staff.number(f == null ? e.number : f.number), Workforce.name(mob),
                mob.getType().getDescription(), d.clock == null ? "-" : place(d.clock), jobText, status);
    }

    private static String place(GlobalPos at) {
        if (at == null) return "-";
        return at.dimension().location().getPath() + " " + at.pos().getX() + " " + at.pos().getY() + " " + at.pos().getZ();
    }

    /** A loaded creature by UUID in any level (never loads anything), or null. */
    static Mob find(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(id);
            if (e instanceof Mob m && Workforce.isWorker(m)) return m;
        }
        return null;
    }

    private static String nameOf(MinecraftServer server, UUID id) {
        var online = server.getPlayerList().getPlayer(id);
        if (online != null) return online.getGameProfile().getName();
        return server.getProfileCache() == null ? id.toString() : server.getProfileCache().get(id).map(GameProfile::getName).orElse(id.toString());
    }

    // --- recall -----------------------------------------------------------------------------------------------------

    private static int recall(CommandContext<CommandSourceStack> c, Collection<GameProfile> who) {
        MinecraftServer server = c.getSource().getServer();
        int n = 0;
        for (GameProfile p : who) {
            for (WorkerIndex.Entry e : List.copyOf(WorkerIndex.get(server).of(p.getId()))) {
                Mob mob = find(server, e.id);
                if (mob != null && recall(mob)) n++;
            }
        }
        int count = n;
        c.getSource().sendSuccess(() -> Component.translatable("command.headinajar.staff.recalled", count), true);
        return n;
    }

    /**
     * One worker recalled: a personal card back in its rack, its task and any detail dropped, and back to its clock.
     * Returns whether it was (a worker on notice, or with no clock, is left as it is).
     */
    public static boolean recall(Mob mob) {
        WorkerData d = Workforce.data(mob);
        if (d == null || d.state == WorkerData.State.NOTICE || d.state == WorkerData.State.SHUTTLE || d.clock == null) return false;
        if (!d.card.isEmpty() && mob.level() instanceof ServerLevel level) {
            ItemStack card = d.card;
            d.card = ItemStack.EMPTY;
            if (!Sites.returnCard(level, d.clock, card)) d.card = card;
        }
        d.dropTask();
        d.detail = null;
        d.state = WorkerData.State.TO_CLOCK;
        d.status = "staff.headinajar.status.waiting";
        WorkerLooks.changed(mob);
        // off any detail; more than 48 blocks from its clock it goes home by the company shuttle (Detail)
        com.coolfreeze23.headinajar.staff.work.Detail.recalled(mob, d);
        return true;
    }

    // --- pause ------------------------------------------------------------------------------------------------------

    private static int pause(CommandContext<CommandSourceStack> c) {
        MinecraftServer server = c.getSource().getServer();
        boolean on = !WorkPause.get(server).paused();
        WorkPause.set(server, on);
        c.getSource().sendSuccess(() -> Component.translatable(on ? "message.headinajar.staff.pause" : "message.headinajar.staff.resume"), true);
        if (on) {
            for (ServerLevel level : server.getAllLevels()) {
                for (Entity e : level.getAllEntities()) if (e instanceof Mob m && Workforce.isWorker(m)) Workforce.say(m, "mandatory_break");
            }
        }
        return on ? 1 : 0;
    }

    /** Head Office's index as a map, for tests. */
    static Map<UUID, Map<UUID, WorkerIndex.Entry>> index(MinecraftServer server) {
        return WorkerIndex.get(server).all();
    }
}
