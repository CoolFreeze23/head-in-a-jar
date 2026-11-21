package com.coolfreeze23.headinajar.staff.work;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.block.TimeClockBlockEntity;
import com.coolfreeze23.headinajar.clarity.Memos;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.feedback.Refuser;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.mind.MobMotor;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.staff.StaffShake;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * A player's detail (the Supervisor's Whistle): up to {@code staff.detailSize} (6) staff off site that follow, hold,
 * fight what the player points at and go home (on foot within {@link #WALK_HOME} blocks, else by the company shuttle,
 * {@link Shuttle}).
 * <p>
 * The roll (members' UUIDs in order, and Follow or Hold) is kept in the player's persisted data under {@link #KEY}, so
 * it survives death and logging out; a member is a worker with {@code state = DETAIL} and {@code detail = player}. A
 * DETAIL worker with no player ({@code detail == null}) is walking home: inside its site's box it becomes the site's
 * again ({@code TO_CLOCK}). Everything else a member knows (its last path, its target, its time away) is not saved.
 */
public final class Detail {
    /** The player's persisted data key: {@code {Members: [uuid...], Mode: "follow" | "hold"}}. */
    public static final String KEY = "headinajar_detail";
    /** Follow paths only when farther than this from the player. */
    public static final double FOLLOW_NEAR = 4;
    /** One follow path a member each this many ticks. */
    public static final int PATH_TICKS = 20;
    /** Farther behind than this (following): the catch-up shuttle. */
    public static final double CATCH_UP = 32;
    /** Within this of home it walks; farther, the company shuttle. */
    public static final double WALK_HOME = 48;
    /** This many of its own ticks away from its player (another dimension, or farther than {@link #CATCH_UP}): home. */
    public static final int AWAY_TICKS = 6000;
    /** A walk home that has not reached the site in this many ticks takes the shuttle instead. */
    public static final int HOME_WALK_TICKS = 1200;
    /** Away Day: all six farther than this from their sites' boxes. */
    public static final int AWAY_DAY = 16;
    /** Fighters chase what they were sent at while it is within this of their player. */
    public static final double FIGHT_RANGE = 40;
    /** The whistle's reach: what the player points at. */
    public static final double REACH = 32;

    /** Follow (one blast) or Hold (two short). */
    public enum Mode {
        FOLLOW, HOLD
    }

    /** What a member knows that is not saved. */
    static final class Member {
        long lastPath = Long.MIN_VALUE / 2;
        int away;
        UUID target;
        long homeSince = -1;
        long born = -1;
        boolean portal;
    }

    private static final Map<Mob, Member> MEMBERS = new WeakHashMap<>();

    private Detail() {
    }

    static Member member(Mob mob) {
        Member m = MEMBERS.computeIfAbsent(mob, k -> new Member());
        if (m.born < 0) m.born = mob.level().getGameTime();
        return m;
    }

    /** Forgets every member's unsaved state (the server stopped). */
    static void clear() {
        MEMBERS.clear();
    }

    public static int size() {
        return Config.get(Config.DETAIL_SIZE, 6);
    }

    static boolean shuttles() {
        return Config.get(Config.DETAIL_SHUTTLE, true);
    }

    // --- the roll ---------------------------------------------------------------------------------------------------

    private static CompoundTag kept(Player p) {
        return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(KEY);
    }

    /** The members of this player's detail, in order (some may be unloaded, on a shoulder or in the shuttle). */
    public static List<UUID> members(Player p) {
        List<UUID> out = new ArrayList<>();
        for (Tag t : kept(p).getList("Members", Tag.TAG_INT_ARRAY)) out.add(NbtUtils.loadUUID(t));
        return out;
    }

    public static Mode mode(Player p) {
        return "hold".equals(kept(p).getString("Mode")) ? Mode.HOLD : Mode.FOLLOW;
    }

    static void save(Player p, List<UUID> members, Mode mode) {
        CompoundTag all = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (UUID id : members) list.add(NbtUtils.createUUID(id));
        tag.put("Members", list);
        tag.putString("Mode", mode == Mode.HOLD ? "hold" : "follow");
        all.put(KEY, tag);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, all);
    }

    /** Whether this worker is on {@code p}'s detail now (its record and the roll agree). */
    public static boolean on(Entity e, Player p) {
        WorkerData d = Workforce.data(e);
        return d != null && d.state == WorkerData.State.DETAIL && p.getUUID().equals(d.detail) && members(p).contains(e.getUUID());
    }

    /** Whether a fighter: in Security with an attack of its own, and not one of the pets. */
    public static boolean fighter(Mob mob) {
        if (Pets.isPet(mob)) return false;
        WorkTable.Spec spec = WorkTable.of(mob);
        return spec != null && spec.can(WorkTable.SECURITY) && !"none".equals(spec.attack()) && !"lookout".equals(spec.attack());
    }

    /** The loaded entity with this UUID in any level, or null. */
    static Entity find(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(id);
            if (e != null) return e;
        }
        return null;
    }

    /** Whether a worker is on a player's shoulder now (a parrot): the Roster's "On your shoulder". */
    public static boolean onShoulder(MinecraftServer server, UUID worker) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (shoulder(p.getShoulderEntityLeft(), worker) || shoulder(p.getShoulderEntityRight(), worker)) return true;
        }
        return false;
    }

    private static boolean shoulder(CompoundTag tag, UUID id) {
        return tag != null && tag.hasUUID("UUID") && tag.getUUID("UUID").equals(id);
    }

    /** Drops from the roll the members found loaded that are no longer this player's (let go, sent home, rehired). */
    static List<UUID> prune(ServerPlayer p, List<UUID> roll) {
        List<UUID> out = new ArrayList<>();
        for (UUID id : roll) {
            Entity e = find(p.server, id);
            if (e != null) {
                WorkerData d = Workforce.data(e);
                if (d == null || d.state != WorkerData.State.DETAIL || !p.getUUID().equals(d.detail) || !e.isAlive()) continue;
            }
            out.add(id);
        }
        if (out.size() != roll.size()) save(p, out, mode(p));
        return out;
    }

    // --- the whistle ------------------------------------------------------------------------------------------------

    /** The whistle at the player: everyone around hears it. */
    static void blowSound(ServerPlayer p, SoundEvent sound) {
        p.serverLevel().playSound(null, p.getX(), p.getEyeY(), p.getZ(), sound, SoundSource.PLAYERS, 1, 1);
    }

    /** A line on the player's action bar; a jar's says the grille did it. */
    static void tell(ServerPlayer p, Component line) {
        Form form = p.getData(ModRegistry.JAR_STATE).form();
        p.displayClientMessage(form.isJarred() ? Component.translatable("message.headinajar.grille").append(" ").append(line) : line, true);
    }

    /**
     * Use with the Whistle on a creature: one of your staff joins your detail, or leaves it if it is on it; anything
     * else is refused. Returns true (the press always answers).
     */
    public static boolean whistleAt(ServerPlayer p, Entity target) {
        if (!(target instanceof Mob mob) || !Workforce.isWorker(mob)) {
            Refusals.deny(p, Refuser.at(target), "whistle.not_staff");
            return true;
        }
        WorkerData d = Workforce.data(mob);
        if (!Workforce.mayOrder(mob, p)) {
            Refusals.deny(p, StaffShake.at(mob), "staff.reports_to", Workforce.name(mob), Workforce.employerName(mob));
            return true;
        }
        if (d.state == WorkerData.State.NOTICE) {
            Refusals.deny(p, StaffShake.at(mob), "staff.already_let_go", Workforce.name(mob));
            return true;
        }
        List<UUID> roll = prune(p, members(p));
        if (d.state == WorkerData.State.DETAIL && p.getUUID().equals(d.detail) && roll.contains(mob.getUUID())) {
            roll.remove(mob.getUUID());
            save(p, roll, mode(p));
            sendHome(mob, d);
            blowSound(p, WorkSounds.WHISTLE_TWICE.get());
            tell(p, Component.translatable("message.headinajar.whistle.left", Workforce.name(mob)));
            return true;
        }
        if (d.state == WorkerData.State.DETAIL && d.detail != null && !p.getUUID().equals(d.detail)) {
            ServerPlayer other = p.server.getPlayerList().getPlayer(d.detail);
            Refusals.deny(p, StaffShake.at(mob), "whistle.other_detail", Workforce.name(mob),
                    other != null ? other.getDisplayName() : Component.translatable("staff.headinajar.another_supervisor"));
            return true;
        }
        if (roll.size() >= size()) {
            Refusals.deny(p, StaffShake.at(mob), "whistle.full", size());
            return true;
        }
        join(p, mob, d, roll);
        blowSound(p, WorkSounds.WHISTLE.get());
        tell(p, Component.translatable("message.headinajar.whistle.joined", Workforce.name(mob), roll.size(), size()));
        return true;
    }

    /** Puts a worker on the player's detail (every check passed). {@code roll} is the player's, and gains it. */
    public static void join(ServerPlayer p, Mob mob, WorkerData d, List<UUID> roll) {
        if (mob.level() instanceof ServerLevel level) {
            // its rack posting goes back to the others; a personal card and whatever it carries go with it
            TimeClockBlockEntity clock = Sites.clock(level, d.clock);
            if (clock != null) clock.take(mob.getUUID(), -1);
        }
        d.dropTask();
        d.state = WorkerData.State.DETAIL;
        d.detail = p.getUUID();
        d.status = "staff.headinajar.status.detail";
        Member m = member(mob);
        m.target = null;
        m.away = 0;
        m.homeSince = -1;
        MobMotor.halt(mob);
        if (!roll.contains(mob.getUUID())) roll.add(mob.getUUID());
        save(p, roll, mode(p));
        WorkerLooks.changed(mob);
    }

    /**
     * Use with the Whistle in the air: toggles Follow and Hold (one blast, two short); with Sneak it stands the detail
     * down. No detail: refused.
     */
    public static void blow(ServerPlayer p, boolean sneaking) {
        List<UUID> roll = prune(p, members(p));
        if (roll.isEmpty()) {
            Refusals.deny(p, "whistle.empty");
            return;
        }
        if (sneaking) {
            int n = standDown(p);
            blowSound(p, WorkSounds.WHISTLE.get());
            tell(p, Component.translatable("message.headinajar.whistle.stand_down", n));
            return;
        }
        Mode mode = mode(p) == Mode.FOLLOW ? Mode.HOLD : Mode.FOLLOW;
        save(p, roll, mode);
        if (mode == Mode.HOLD) {
            for (UUID id : roll) {
                if (p.serverLevel().getEntity(id) instanceof Mob mob && on(mob, p)) MobMotor.halt(mob);
            }
        }
        blowSound(p, mode == Mode.FOLLOW ? WorkSounds.WHISTLE.get() : WorkSounds.WHISTLE_TWICE.get());
        tell(p, Component.translatable(mode == Mode.FOLLOW ? "message.headinajar.whistle.follow" : "message.headinajar.whistle.hold"));
    }

    /** Stands the whole detail down: each one loaded walks home or takes the shuttle; the rest find out when they load. */
    public static int standDown(ServerPlayer p) {
        List<UUID> roll = members(p);
        save(p, List.of(), mode(p));
        for (UUID id : roll) {
            if (shoulder(p.getShoulderEntityLeft(), id) || shoulder(p.getShoulderEntityRight(), id)) {
                // the parrot comes off the shoulder (and then finds it is no longer wanted)
                ((com.coolfreeze23.headinajar.mixin.PlayerShoulderInvoker) p).headinajar$dropShoulders();
                continue;
            }
            Entity e = find(p.server, id);
            WorkerData d = Workforce.data(e);
            if (d != null && d.state == WorkerData.State.DETAIL && p.getUUID().equals(d.detail)) sendHome((Mob) e, d);
        }
        return roll.size();
    }

    /** An attack with the Whistle in hand, or its point in the air: the fighters go for {@code entityId}; the pets stay. */
    public static void point(ServerPlayer p, int entityId) {
        List<UUID> roll = prune(p, members(p));
        if (roll.isEmpty()) {
            Refusals.deny(p, "whistle.empty");
            return;
        }
        Entity e = entityId >= 0 ? p.level().getEntity(entityId) : null;
        if (e != null) e = com.coolfreeze23.headinajar.host.HostEvents.whole(e);
        if (!(e instanceof LivingEntity t) || !t.isAlive() || t.distanceToSqr(p) > (REACH + 2) * (REACH + 2)) {
            Refusals.deny(p, "whistle.nothing");
            return;
        }
        if (!FriendOrFoe.mayTarget(p.getUUID(), p.level(), t)) {
            Refusals.deny(p, Refuser.at(t), "whistle.friendly", t.getDisplayName());
            return;
        }
        blowSound(p, WorkSounds.WHISTLE.get());
        int fighters = 0;
        for (UUID id : roll) {
            if (!(p.serverLevel().getEntity(id) instanceof Mob mob) || !on(mob, p) || mob.distanceToSqr(p) > FIGHT_RANGE * FIGHT_RANGE) continue;
            if (fighter(mob)) {
                member(mob).target = t.getUUID();
                fighters++;
            } else {
                // a pet stays, and says so
                Workforce.data(mob).lastSaid = mob.level().getGameTime();
                Workforce.sayNow(mob, Component.translatable("staff.headinajar.say.not_security", Workforce.name(mob)));
            }
        }
        if (fighters > 0) tell(p, Component.translatable("message.headinajar.whistle.sic", t.getDisplayName()));
        else Refusals.deny(p, "whistle.no_fighters");
    }

    /** Its ticks away from its player so far (tests). */
    public static int away(Mob mob) {
        return member(mob).away;
    }

    /** Sets its ticks away (tests: five minutes are long). */
    public static void setAway(Mob mob, int ticks) {
        member(mob).away = ticks;
    }

    /** Who a member is fighting now (tests), or null. */
    public static UUID target(Mob mob) {
        Member m = MEMBERS.get(mob);
        return m == null ? null : m.target;
    }

    /** The detail's player was hurt: its fighters answer whoever did it (inside the friend-or-foe rule). */
    static void hurt(ServerPlayer p, DamageSource source) {
        Entity by = source.getEntity();
        if (by instanceof Projectile shot && shot.getOwner() != null) by = shot.getOwner();
        if (!(by instanceof LivingEntity attacker) || attacker == p || !FriendOrFoe.mayTarget(p.getUUID(), p.level(), attacker)) return;
        List<UUID> roll = members(p);
        if (roll.isEmpty()) return;
        for (UUID id : roll) {
            if (!(p.serverLevel().getEntity(id) instanceof Mob mob) || !on(mob, p) || !fighter(mob)) continue;
            Member m = member(mob);
            if (m.target == null) m.target = attacker.getUUID();
        }
    }

    /** The player went through a portal: the detail holds, and the first one says why. */
    static void changedDimension(ServerPlayer p, ResourceKey<Level> from) {
        List<UUID> roll = members(p);
        if (roll.isEmpty()) return;
        save(p, roll, Mode.HOLD);
        ServerLevel old = p.server.getLevel(from);
        if (old == null) return;
        for (UUID id : roll) {
            if (old.getEntity(id) instanceof Mob mob && on(mob, p)) {
                member(mob).portal = true;
                MobMotor.halt(mob);
                Workforce.say(mob, "portal");
                return;
            }
        }
    }

    /** The player logged out: the detail stands where it is, on Hold. */
    static void loggedOut(ServerPlayer p) {
        List<UUID> roll = members(p);
        if (!roll.isEmpty()) save(p, roll, Mode.HOLD);
    }

    /** The player died: the detail goes home (and the office says so). */
    static void supervisorDied(ServerPlayer p) {
        if (members(p).isEmpty()) return;
        standDown(p);
        Memos.send(p, Memos.Memo.DETAIL_HOME);
    }

    /** A member died: it leaves the detail (an offline player's roll is put right when they log in). */
    static void memberDied(Mob mob, WorkerData d) {
        if (d.detail == null || !(mob.level() instanceof ServerLevel level)) return;
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(d.detail);
        if (p != null) {
            List<UUID> roll = members(p);
            if (roll.remove(mob.getUUID())) save(p, roll, mode(p));
        } else {
            Shuttle.data(level.getServer()).leaver(d.detail, mob.getUUID());
        }
        MEMBERS.remove(mob);
    }

    /** The player logged in: members that converted while they were away keep their place, the dead come off. */
    static void loggedIn(ServerPlayer p) {
        Shuttle.Data data = Shuttle.data(p.server);
        var renamed = data.renames(p.getUUID());
        var gone = data.leavers(p.getUUID());
        if (gone.isEmpty() && renamed.isEmpty()) return;
        List<UUID> roll = members(p);
        roll.replaceAll(id -> renamed.getOrDefault(id, id));
        roll.removeAll(gone);
        save(p, roll, mode(p));
    }

    /**
     * A member converted into something else alive (a piglin zombified, a zombie drowned): the new creature has a new
     * UUID and takes the old one's place on the roll (an offline player's roll is put right when they log in).
     */
    public static void converted(Mob old, Mob now, WorkerData d) {
        Member m = MEMBERS.remove(old);
        Pets.forget(old);
        if (d.detail == null || d.state != WorkerData.State.DETAIL || !(now.level() instanceof ServerLevel level)) return;
        if (m != null) MEMBERS.put(now, m);
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(d.detail);
        if (p != null) {
            List<UUID> roll = members(p);
            int i = roll.indexOf(old.getUUID());
            if (i >= 0) {
                roll.set(i, now.getUUID());
                save(p, roll, mode(p));
            }
        } else {
            Shuttle.data(level.getServer()).rename(d.detail, old.getUUID(), now.getUUID());
        }
    }

    /**
     * {@code /headoffice staff recall}: off any detail, and one more than {@link #WALK_HOME} from its clock (or with its
     * clock in another dimension) goes home by the company shuttle, as a stood-down detail does.
     */
    public static void recalled(Mob mob, WorkerData d) {
        if (mob.level() instanceof ServerLevel level) {
            for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
                List<UUID> roll = members(p);
                if (roll.remove(mob.getUUID())) save(p, roll, mode(p));
            }
        }
        MEMBERS.remove(mob);
        Pets.forget(mob);
        if (d.clock == null || mob.isRemoved()) return;
        if (!Workforce.clockHere(mob, d) || mob.blockPosition().distSqr(d.clock.pos()) > WALK_HOME * WALK_HOME) {
            if (shuttles()) Shuttle.home(mob, d);
        }
    }

    // --- a member's think -------------------------------------------------------------------------------------------

    /** A think for a worker on a detail (every {@code staff.thinkTicks}). */
    static void think(Mob mob, WorkerData d) {
        if (!(mob.level() instanceof ServerLevel level)) return;
        Member m = member(mob);
        if (d.detail == null) {
            homeward(mob, d, m);
            return;
        }
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(d.detail);
        if (p == null) {
            // logged out: Hold where they are
            hold(mob);
            return;
        }
        List<UUID> roll = members(p);
        int index = roll.indexOf(mob.getUUID());
        if (index < 0) {
            // stood down (or the supervisor died) while it was not loaded
            sendHome(mob, d);
            return;
        }
        d.status = "staff.headinajar.status.detail";
        boolean here = p.level() == level && p.isAlive();
        double dist = here ? mob.distanceTo(p) : Double.MAX_VALUE;
        if (!here || dist > CATCH_UP) {
            m.away += Workforce.thinkTicks();
            if (m.away >= AWAY_TICKS) {
                roll.remove(mob.getUUID());
                save(p, roll, mode(p));
                sendHome(mob, d);
                Memos.send(p, Memos.Memo.DETAIL_HOME);
                return;
            }
        } else {
            m.away = 0;
        }
        if (!here) {
            // another dimension: a detail never follows through a portal
            hold(mob);
            if (m.portal) Workforce.say(mob, "portal");
            return;
        }
        m.portal = false;
        Mode mode = mode(p);
        if (fight(mob, d, m, p, level)) return;
        if (mode == Mode.FOLLOW && dist > CATCH_UP) {
            if (shuttles() && Shuttle.catchUp(mob, d, p)) return;
            hold(mob);
            return;
        }
        if (index == 0 && (mob.tickCount & 63) < Workforce.thinkTicks()) awayDay(p, roll);
        if (Pets.think(mob, d, m, p, mode, dist)) return;
        if (mode == Mode.FOLLOW) follow(mob, d, m, p, index, dist);
        else hold(mob);
    }

    /** Stands still. */
    static void hold(Mob mob) {
        if (!MobMotor.idle(mob)) MobMotor.halt(mob);
    }

    /** One think of fighting the member's target; false when it has none (or it is no longer fair game). */
    private static boolean fight(Mob mob, WorkerData d, Member m, ServerPlayer p, ServerLevel level) {
        if (m.target == null) return false;
        Entity e = level.getEntity(m.target);
        if (!(e instanceof LivingEntity t) || !t.isAlive() || t.distanceToSqr(p) > FIGHT_RANGE * FIGHT_RANGE
                || !FriendOrFoe.mayTarget(p.getUUID(), level, t) || !Combat.fight(mob, d, t, null)) {
            m.target = null;
            return false;
        }
        return true;
    }

    /** Follow: a path only when farther than 4 from the player, to its wedge spot, one a {@link #PATH_TICKS}. */
    static void follow(Mob mob, WorkerData d, Member m, ServerPlayer p, int index, double dist) {
        if (dist <= FOLLOW_NEAR) {
            hold(mob);
            mob.getLookControl().setLookAt(p, 30, 30);
            return;
        }
        long now = mob.level().getGameTime();
        if (now - m.lastPath < PATH_TICKS) return;
        m.lastPath = now;
        PATHS_ASKED++;
        WorkerPaths.go(mob, d, wedge(p, index), p.isSprinting() ? 1.35 : 1.1, null, true);
    }

    /** Follow paths asked for since the server started (tests). */
    public static long PATHS_ASKED;

    /** A loose wedge behind the player: two a row, the first row 2.5 behind, rows 1.5 apart and 1.2 wider each. */
    public static BlockPos wedge(Player p, int index) {
        int row = index / 2 + 1;
        int side = index % 2 == 0 ? -1 : 1;
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        Vec3 at = p.position().subtract(fwd.scale(1 + row * 1.5)).add(right.scale(side * row * 1.2));
        BlockPos stand = stand(p.level(), BlockPos.containing(at), 3);
        return stand != null ? stand : p.blockPosition();
    }

    /** A spot a creature can stand on near {@code pos} (searching {@code dy} up and down), loaded; or null. */
    static BlockPos stand(Level level, BlockPos pos, int dy) {
        for (int i = 0; i <= dy * 2; i++) {
            int y = pos.getY() + (i % 2 == 0 ? i / 2 : -(i + 1) / 2);
            BlockPos at = new BlockPos(pos.getX(), y, pos.getZ());
            if (!level.isLoaded(at)) return null;
            BlockPos below = at.below();
            if (level.getBlockState(at).getCollisionShape(level, at).isEmpty() && level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()
                    && !level.getBlockState(below).getCollisionShape(level, below).isEmpty() && level.getFluidState(at).isEmpty()) {
                return at;
            }
        }
        return null;
    }

    /** Away Day: all six on the detail are more than 16 from their sites' boxes. */
    private static void awayDay(ServerPlayer p, List<UUID> roll) {
        if (roll.size() < 6) return;
        for (UUID id : roll) {
            Entity e = p.serverLevel().getEntity(id);
            WorkerData d = Workforce.data(e);
            if (d == null || d.clock == null) return;
            if (d.clock.dimension().equals(e.level().dimension()) && Workforce.box(d.clock.pos()).inflate(AWAY_DAY).contains(e.position())) return;
        }
        com.coolfreeze23.headinajar.Advance.grant(p, "away_day");
    }

    // --- home -------------------------------------------------------------------------------------------------------

    /** Off the detail: it walks home (within {@link #WALK_HOME}) or takes the company shuttle. */
    public static void sendHome(Mob mob, WorkerData d) {
        Member m = member(mob);
        m.target = null;
        m.away = 0;
        m.homeSince = mob.level().getGameTime();
        d.detail = null;
        d.dropTask();
        MobMotor.halt(mob);
        if (mob instanceof TamableAnimal pet) pet.setInSittingPose(false);
        Pets.forget(mob);
        if (d.clock == null) {
            // never had a site: it waits for one where it is
            d.state = WorkerData.State.AWAITING;
            d.status = "staff.headinajar.status.awaiting";
            WorkerLooks.changed(mob);
            return;
        }
        d.state = WorkerData.State.DETAIL;
        d.status = "staff.headinajar.status.detail";
        WorkerLooks.changed(mob);
        if (!Workforce.clockHere(mob, d) || mob.blockPosition().distSqr(d.clock.pos()) > WALK_HOME * WALK_HOME) farHome(mob, d);
    }

    /** Too far to walk: the shuttle, or (shuttle off) left where it is, off site. */
    private static void farHome(Mob mob, WorkerData d) {
        if (shuttles() && Shuttle.home(mob, d)) return;
        d.state = WorkerData.State.OFF_SITE;
        d.status = "staff.headinajar.status.off_site";
    }

    /** Walking home: inside the site's box it is the site's again; too far, too long or no way: the shuttle. */
    private static void homeward(Mob mob, WorkerData d, Member m) {
        if (d.clock == null) {
            d.state = WorkerData.State.AWAITING;
            return;
        }
        if (!Workforce.clockHere(mob, d)) {
            farHome(mob, d);
            return;
        }
        BlockPos clock = d.clock.pos();
        if (Workforce.inBox(clock, mob.blockPosition())) {
            MobMotor.halt(mob);
            d.state = WorkerData.State.TO_CLOCK;
            d.dropTask();
            d.status = "staff.headinajar.status.waiting";
            WorkerLooks.changed(mob);
            return;
        }
        long now = mob.level().getGameTime();
        if (m.homeSince < 0) m.homeSince = now;
        if (mob.blockPosition().distSqr(clock) > WALK_HOME * WALK_HOME || now - m.homeSince > HOME_WALK_TICKS) {
            farHome(mob, d);
            return;
        }
        if (!MobMotor.idle(mob) && now - d.lastPath < WorkerPaths.PATH_TICKS * 3) return;
        // a spot inside the box, on the way from here to the clock
        Vec3 from = Vec3.atBottomCenterOf(clock);
        Vec3 dir = mob.position().subtract(from);
        double len = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        double in = Workforce.radius() - 4;
        Vec3 aim = len < 1e-3 ? from : from.add(dir.x / len * in, 0, dir.z / len * in);
        BlockPos spot = stand(mob.level(), BlockPos.containing(aim.x, clock.getY(), aim.z), Workforce.SITE_HEIGHT);
        if (spot == null) {
            if (++d.pathFails > WorkerPaths.RETRIES) farHome(mob, d);
            return;
        }
        WorkerPaths.Result r = WorkerPaths.go(mob, d, spot, 1.0, null, false);
        if (r == WorkerPaths.Result.FAILED && WorkerPaths.givenUp(d)) farHome(mob, d);
    }
}
