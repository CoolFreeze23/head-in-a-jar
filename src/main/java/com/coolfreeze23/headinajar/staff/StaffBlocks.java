package com.coolfreeze23.headinajar.staff;

import com.coolfreeze23.headinajar.block.JarredHeadBlockEntity;
import com.coolfreeze23.headinajar.item.JarredHeadItem;
import com.coolfreeze23.headinajar.machine.MachineSounds;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.UUID;

/**
 * Staff on blocks: a placed head's file (numbered when placed or, for heads placed before 0.6.0, on its first tick),
 * its pay and morale (a head on the payroll takes its wage at dawn from a container beside it), and the badge worn by
 * plinth heads on the payroll and by every station that holds a head. Nothing here scans: each block looks at itself on
 * its own tick.
 */
public final class StaffBlocks {
    /** How often a block works out its badge's colour again (staggered by position). */
    public static final int BADGE_TICKS = 200;
    /** How often a station looks for a head with no file (staggered by position). */
    public static final int FILE_TICKS = 20;
    public static final int START = 50, PAID = 10, UNPAID = -15;

    private StaffBlocks() {
    }

    private static boolean every(Level level, BlockPos pos, int ticks) {
        return Math.floorMod(level.getGameTime() + pos.asLong(), (long) ticks) == 0;
    }

    // --- the badge ---------------------------------------------------------------------------------------------------

    /** The badge colour for an employer: their scoreboard team's colour, or company teal (no team, or a team with none). */
    public static int colour(Level level, StaffFile file) {
        if (file == null || file.employer == null || file.employerName.isEmpty()) return Badged.TEAL;
        PlayerTeam team = level.getScoreboard().getPlayersTeam(file.employerName);
        Integer c = team == null ? null : team.getColor().getColor();
        return c == null ? Badged.TEAL : c & 0xFFFFFF;
    }

    // --- placed heads ------------------------------------------------------------------------------------------------

    /** A head just placed by {@code by}: on file (numbered now if it never was), the placer its employer if nobody is. */
    public static void placed(JarredHeadBlockEntity head, ServerPlayer by) {
        Level level = head.getLevel();
        if (level == null || head.entityType() == null) return;
        StaffFile f = head.staff();
        boolean fresh = f == null;
        if (fresh) f = Staff.create(by.server, level, head.entityType());
        if (f.employer == null) Staff.employ(f, by);
        f.note("placed", Staff.day(level), by.getGameProfile().getName());
        head.setStaff(f);
        head.firstLook();
        if (fresh) Staff.numbered(by, f);
        show(head, f);
    }

    /** What clients see of a plinth head: the badge while on the payroll, and the band. */
    public static void show(JarredHeadBlockEntity head, StaffFile f) {
        Level level = head.getLevel();
        if (level == null) return;
        int badge = f != null && f.staff() ? colour(level, f) : -1;
        head.show(badge, Band.of(f));
    }

    /** The plinth's own tick: the first look after a load, the dawn, and the badge now and then. */
    public static void tick(Level level, BlockPos pos, JarredHeadBlockEntity head) {
        if (head.entityType() == null) return;
        StaffFile f = head.staff();
        if (head.firstLook()) {
            if (f == null && level instanceof ServerLevel server) {
                // placed before 0.6.0: registered now, as a trophy with nobody on file as its employer
                f = Staff.create(server.getServer(), level, head.entityType());
                f.note("retro", Staff.day(level), "");
                head.setStaff(f);
            }
            show(head, f);
        }
        if (f == null) return;
        if (f.staff() && Staff.day(level) > f.lastDawn) dawn(level, pos, head, f);
        if (every(level, pos, BADGE_TICKS)) {
            Staff.refresh(f, level.getServer());
            show(head, f);
        }
    }

    /**
     * One dawn for a head on the payroll: it takes its wage from a container beside it (one wage, however many days it
     * was away), and its morale moves. A head whose species takes no wage counts as paid.
     */
    public static void dawn(Level level, BlockPos pos, JarredHeadBlockEntity head, StaffFile f) {
        long day = Staff.day(level);
        f.lastDawn = day;
        StaffTable.Wage wage = StaffTable.wage(head.entityType());
        boolean paid = f.paidThrough >= day || wage.none() || take(level, pos, wage);
        if (paid) {
            f.paidThrough = Math.max(f.paidThrough, day);
            move(f, PAID);
            cheer(level, pos);
        } else {
            move(f, UNPAID);
            level.playSound(null, pos, MachineSounds.MOUNT_SIGH.get(), SoundSource.BLOCKS, 1, 0.95f + level.random.nextFloat() * 0.1f);
            if (level instanceof ServerLevel server) server.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 8, 0.15, 0.1, 0.15, 0.01);
            ServerPlayer employer = f.employer == null || level.getServer() == null ? null : level.getServer().getPlayerList().getPlayer(f.employer);
            if (employer != null) com.coolfreeze23.headinajar.clarity.Memos.send(employer, com.coolfreeze23.headinajar.clarity.Memos.Memo.UNPAID_DAWN, Staff.name(f));
        }
        f.lowDawns = f.morale < Band.DISENGAGED_AT ? f.lowDawns + 1 : 0;
        head.setStaff(f);
        show(head, f);
    }

    private static void move(StaffFile f, int by) {
        if (!com.coolfreeze23.headinajar.Config.get(com.coolfreeze23.headinajar.Config.MORALE, true)) return;
        f.morale = Math.max(0, Math.min(100, f.morale + by));
    }

    private static void cheer(Level level, BlockPos pos) {
        level.playSound(null, pos, MachineSounds.MOUNT_CLINK.get(), SoundSource.BLOCKS, 1, 1);
        if (level instanceof ServerLevel server) server.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 6, 0.25, 0.2, 0.25, 0);
    }

    /** Takes one wage from any item handler on the six sides (as the Employee Mount takes its emerald). */
    static boolean take(Level level, BlockPos pos, StaffTable.Wage wage) {
        for (Direction d : Direction.values()) {
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos.relative(d), d.getOpposite());
            if (handler == null) continue;
            // enough of it here, counted first so a short pile is never half taken
            int have = 0;
            for (int i = 0; i < handler.getSlots() && have < wage.count(); i++) {
                ItemStack s = handler.getStackInSlot(i);
                if (wage.matches(s)) have += handler.extractItem(i, wage.count() - have, true).getCount();
            }
            if (have < wage.count()) continue;
            int left = wage.count();
            for (int i = 0; i < handler.getSlots() && left > 0; i++) {
                if (wage.matches(handler.getStackInSlot(i))) left -= handler.extractItem(i, left, false).getCount();
            }
            return true;
        }
        return false;
    }

    /** The wage as a line names it: "Wheat", or "2 Rotten Flesh". */
    public static Component wageName(StaffTable.Wage wage) {
        return wage.count() > 1 ? Component.literal(wage.count() + " ").append(wage.name()) : wage.name();
    }

    /**
     * A wage used on a plinth head by hand. The first payment hires it (it is a trophy until then): it joins the payroll
     * at 50 morale, the payer is its employer. Returns whether anything happened (a refusal counts).
     */
    public static boolean payByHand(JarredHeadBlockEntity head, ServerPlayer player, ItemStack stack) {
        Level level = head.getLevel();
        EntityType<?> type = head.entityType();
        if (level == null || type == null) return false;
        StaffTable.Wage wage = StaffTable.wage(type);
        StaffFile f = head.staff();
        if (f == null) {
            f = Staff.create(player.server, level, type);
            f.note("retro", Staff.day(level), "");
        }
        long day = Staff.day(level);
        Component name = Staff.name(f);
        if (f.staff() && f.paidThrough >= day) {
            com.coolfreeze23.headinajar.feedback.Refusals.deny(player, com.coolfreeze23.headinajar.feedback.Refuser.at(head), "staff.paid_today");
            return true;
        }
        if (stack.getCount() < wage.count()) {
            com.coolfreeze23.headinajar.feedback.Refusals.deny(player, com.coolfreeze23.headinajar.feedback.Refuser.at(head), "staff.wrong_wage", name, wageName(wage));
            return true;
        }
        stack.consume(wage.count(), player);
        if (!f.staff()) {
            f.morale = START;
            f.lowDawns = 0;
            Staff.employ(f, player);
            f.note("hired", day, player.getGameProfile().getName());
            player.displayClientMessage(Component.translatable("message.headinajar.staff.hired", name), true);
            com.coolfreeze23.headinajar.Advance.grant(player, "pay_grade");
        } else {
            player.displayClientMessage(Component.translatable("message.headinajar.staff.paid", name), true);
        }
        move(f, PAID);
        f.paidThrough = day;
        f.lastDawn = Math.max(f.lastDawn, day);
        head.setStaff(f);
        cheer(level, head.getBlockPos());
        show(head, f);
        return true;
    }

    // --- stations ----------------------------------------------------------------------------------------------------

    /** A head fitted to a station by {@code by}: on file, with the fitter as its employer and a history line. */
    public static void fitted(BlockEntity station, ItemStack head, ServerPlayer by) {
        Level level = station.getLevel();
        if (level == null || !head.is(ModRegistry.JARRED_HEAD.get())) return;
        StaffFile f = Staff.of(head);
        boolean fresh = f == null;
        if (fresh) f = Staff.create(by.server, level, JarredHeadItem.type(head));
        Staff.employ(f, by);
        String where = station.getBlockState().getBlock().getDescriptionId();
        if (station instanceof com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity) f.note("board", Staff.day(level), "");
        else f.note("fitted", Staff.day(level), where + "\n" + by.getGameProfile().getName());
        Staff.write(head, f);
        station.setChanged();
        if (fresh) Staff.numbered(by, f);
        if (station instanceof Badged badged) badged.setBadge(colour(level, f));
    }

    /**
     * A station's look at itself on its own tick: any head with no file is registered (heads fitted before 0.6.0, or put
     * in through a menu), with {@code owner} as employer if known (the Turret's owner); and the badge, now and then.
     * Nothing a station does with its heads reads the file, so no station's output changes.
     */
    public static void stationTick(BlockEntity station, net.neoforged.neoforge.items.IItemHandler items, int slots, UUID owner) {
        Level level = station.getLevel();
        if (level == null || level.isClientSide) return;
        BlockPos pos = station.getBlockPos();
        boolean look = every(level, pos, FILE_TICKS), badge = every(level, pos, BADGE_TICKS);
        if (!look && !badge) return;
        StaffFile first = null;
        boolean any = false, changed = false;
        for (int i = 0; i < slots; i++) {
            ItemStack head = items.getStackInSlot(i);
            if (!head.is(ModRegistry.JARRED_HEAD.get())) continue;
            any = true;
            StaffFile f = Staff.of(head);
            if (f == null && look) {
                f = Staff.create(level.getServer(), level, JarredHeadItem.type(head));
                ServerPlayer employer = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
                if (owner != null) {
                    f.employer = owner;
                    f.employerName = employer != null ? employer.getGameProfile().getName() : ownerName(level, owner);
                }
                f.note(station instanceof com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity ? "board" : "retro", Staff.day(level), "");
                Staff.write(head, f);
                changed = true;
            }
            if (f != null && first == null) first = f;
        }
        if (changed) station.setChanged();
        if ((badge || changed) && station instanceof Badged badged) {
            if (first != null) Staff.refresh(first, level.getServer());
            badged.setBadge(any ? colour(level, first) : -1);
        }
    }

    private static String ownerName(Level level, UUID owner) {
        var cache = level.getServer() == null ? null : level.getServer().getProfileCache();
        if (cache == null) return "";
        return cache.get(owner).map(com.mojang.authlib.GameProfile::getName).orElse("");
    }
}
