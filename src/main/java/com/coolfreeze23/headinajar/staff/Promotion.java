package com.coolfreeze23.headinajar.staff;

import com.coolfreeze23.headinajar.Advance;
import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.block.DoloresVoice;
import com.coolfreeze23.headinajar.block.Operations;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.fx.Fx;
import com.coolfreeze23.headinajar.host.HostData;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.JarredHeadItem;
import com.coolfreeze23.headinajar.item.LifeSupportJarItem;
import com.coolfreeze23.headinajar.item.SurgicalSawItem;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.staff.second.Secondees;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Promotion to the Board. The Surgical Saw on your own living Team Lead, with an empty jar big enough for its head in
 * your off hand, plays the ceremony (eight seconds): DOLORES makes the offer, staff near by stop and turn to watch, the
 * candidate nods, confetti, the jingle, the cut (the head into the jar, the body kept), the desk bell, and
 * "Congratulations on your promotion." You keep both halves: the head, which makes half again at a Cerebral Array, and
 * the body, a host already half synced to you. Nothing else the saw does changes: this is a branch of its own, read
 * before the saw's table, and only for living staff.
 * <p>
 * The ceremony never overlaps an operation: it will not start with a Separation Chair running within
 * {@value Operations#RADIUS} blocks, and a chair that starts near one cancels it before the cut (nothing spent) or
 * silences what is left of it after. Running ceremonies are a list per server, ticked from the level tick (no entity
 * scan); nothing is saved: a ceremony cut short by a stop or an unload has spent nothing.
 */
public final class Promotion {
    /** The timeline, in ticks from the start. */
    public static final int NOD = 70, NOD_TICKS = 10, CONFETTI = 85, JINGLE = 90, COMMIT = 120, BELL = COMMIT + 11, DONE_LINE = 145, LENGTH = 160;
    /** Staff this near the candidate stop and watch; players this near hear the offer; the presser must stay this near. */
    public static final double WATCH = 16, HEAR = 24, LEASH = 16;
    /** A seconded Team Lead is promoted from the roster only within this many blocks of its clock. */
    public static final double ROSTER_REACH = 8;
    /** Morale for each member of staff that watched to the end (Chapter 3's table). */
    public static final int WATCHED = 5;
    /** How far the candidate's head dips at the nod, in degrees. */
    public static final float NOD_PITCH = 20;

    /** One ceremony. */
    public static final class Ceremony {
        public final ServerLevel level;
        public final UUID candidate, player;
        public final long start;
        /** The candidate's Time Clock (its site), or null. */
        public final BlockPos clock;
        /** Staff who stopped to watch. */
        public final Set<UUID> watchers = new HashSet<>();
        public boolean committed, silenced;
        /** Where the neck was at the cut (the bell and the closing line come from there), or null before it. */
        public Vec3 committedAt;
        /** Why it was cancelled (a language key), or null. */
        public String cancelled;
        /** Promoted from the Roster (a seconded Team Lead, {@link RosterPromotion}): the commit unclips the collar. */
        public boolean roster;

        Ceremony(ServerLevel level, UUID candidate, UUID player, long start, BlockPos clock) {
            this.level = level;
            this.candidate = candidate;
            this.player = player;
            this.start = start;
            this.clock = clock;
        }

        public int tick() {
            return (int) (level.getGameTime() - start);
        }

        public Mob mob() {
            return level.getEntity(candidate) instanceof Mob m ? m : null;
        }

        public ServerPlayer presser() {
            return level.getServer().getPlayerList().getPlayer(player);
        }
    }

    private static final List<Ceremony> RUNNING = new ArrayList<>();
    /** What each ceremony did, for tests: "gameTime candidate event" (newest last). */
    public static final List<String> HISTORY = new ArrayList<>();

    private Promotion() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.register(Promotion.class);
        Operations.listen(Promotion::chairStarted);
    }

    // --- the saw's branch --------------------------------------------------------------------------------------------

    /**
     * The saw on a living member of staff: the staff rules decide, and the creature is never cut the ordinary way.
     * Returns null for anything that is not living staff (the saw goes on exactly as it always did), else the result.
     * A seconded head is not living staff here: it keeps the secondment's own line (it is promoted from the roster).
     */
    public static InteractionResult saw(ServerPlayer player, ItemStack saw, Mob mob, InteractionHand hand) {
        if (!Workforce.isWorker(mob) || Secondees.seated(mob)) return null;
        String why = refusal(player, mob);
        if (why != null) return InteractionResult.FAIL;
        start(player, mob);
        return InteractionResult.CONSUME;
    }

    /** The waiver's rules in order (each refusal says its line, plays the refusal sound and shakes the head); null to go. */
    static String refusal(ServerPlayer player, Mob mob) {
        ItemStack offhand = player.getOffhandItem();
        EntityType<?> type = mob.getType();
        Component name = Workforce.name(mob);
        String reason;
        Object[] args = {};
        StaffFile f = Staff.of(mob);
        WorkerData d = Workforce.data(mob);
        boolean jar = offhand.getItem() instanceof LifeSupportJarItem;
        if (mob.isBaby() && !Config.get(Config.ALLOW_BABIES, false)) reason = "saw.baby";
        else if (type == EntityType.ENDER_DRAGON) reason = "saw.dragon";
        else if (type == EntityType.WITHER) reason = "saw.wither";
        else if (type.is(Tags.EntityTypes.BOSSES)) reason = "saw.boss";
        else if (type.is(Hosts.NOT_A_HOST) || type.is(SawTable.NEVER)) {
            reason = "item.on_colleague";
            args = new Object[]{ModRegistry.SURGICAL_SAW.get().getDescription()};
        } else if (mob.hasCustomName()) reason = "saw.named";
        else if (d == null || !player.getUUID().equals(d.employer)) {
            reason = "promotion.others";
            args = new Object[]{name, Workforce.employerName(mob)};
        } else if (Grades.of(f) < Grades.TEAM_LEAD) {
            if (!jar) {
                reason = "staff.saw";
                args = new Object[]{name};
            } else if (Grades.of(f) == Grades.INTERN) reason = "promotion.intern";
            else {
                reason = "promotion.below";
                args = new Object[]{name, Grades.name(f)};
            }
        } else if (!jar) reason = "promotion.no_jar";
        else if (SawTable.rank(offhand) < SawTable.needs(mob)) {
            reason = SawTable.needs(mob) >= 3 ? "saw.jar_small.executive" : "saw.jar_small.reinforced";
            args = new Object[]{type.getDescription()};
        } else reason = gate(player, mob, d == null ? null : d.clock == null ? null : d.clock.pos());
        if (reason == null) return null;
        Refusals.deny(player, StaffShake.at(mob), reason, args);
        return reason;
    }

    /** The rules that hold for both ways in (the saw and the roster): the server's switch, a strike, an operation, a ceremony. */
    static String gate(ServerPlayer player, Mob mob, BlockPos clock) {
        if (!Config.get(Config.PROMOTION, true)) return "promotion.disabled";
        if (clock != null && striking.test(mob.level(), clock)) return "promotion.strike";
        if (Operations.near(mob.level(), mob.position(), Operations.RADIUS)) return "promotion.operation";
        for (Ceremony c : RUNNING) {
            if (c.player.equals(player.getUUID()) || c.candidate.equals(mob.getUUID())) return "promotion.busy";
            Mob other = c.mob();
            if (c.level == mob.level() && other != null && other.distanceToSqr(mob) <= Operations.RADIUS * Operations.RADIUS) return "promotion.busy";
        }
        return null;
    }

    /** Whether the site of the clock at this position is on strike (the strikes set it; none until then). */
    public static java.util.function.BiPredicate<Level, BlockPos> striking = (level, pos) -> false;

    /**
     * The Roster's Promote on a seconded Team Lead (book line 2336), pressed by {@code player} at {@code clock}'s screen.
     * Returns whether anything happened (a refusal counts).
     */
    public static boolean fromRoster(ServerPlayer player, com.coolfreeze23.headinajar.block.TimeClockBlockEntity clock, Mob mob) {
        return RosterPromotion.press(player, clock, mob);
    }

    // --- the ceremony ------------------------------------------------------------------------------------------------

    /** Starts the ceremony: tick 0 (the offer, the watchers, the candidate held still and unhurtable). */
    public static Ceremony start(ServerPlayer player, Mob mob) {
        ServerLevel level = player.serverLevel();
        WorkerData d = Workforce.data(mob);
        Ceremony c = new Ceremony(level, mob.getUUID(), player.getUUID(), level.getGameTime(), d == null || d.clock == null ? null : d.clock.pos());
        RUNNING.add(c);
        note(c, "start");
        com.coolfreeze23.headinajar.staff.work.WorkerPaths.stop(mob);
        // the staff near by stop what they are doing and turn to the candidate
        for (Mob m : level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(WATCH), m -> m != mob && Workforce.isWorker(m) && m.isAlive())) {
            if (m.distanceToSqr(mob) > WATCH * WATCH) continue;
            c.watchers.add(m.getUUID());
            com.coolfreeze23.headinajar.staff.work.WorkerPaths.stop(m);
        }
        Fx.send(level, Fx.of(Fx.CEREMONY_LOOK, mob.position(), player.getEyePosition(), mob.getId(), player.getId(), LENGTH));
        // DOLORES, aloud (her voice never says the name: it is on the action bar) and in writing
        DoloresVoice.say(level, mob.blockPosition(), "promotion_offer", DoloresVoice.BOTH);
        Component name = Workforce.name(mob);
        for (ServerPlayer p : hearers(level, mob)) {
            p.sendSystemMessage(Component.translatable("dolores.headinajar.promotion_offer", name).withStyle(ChatFormatting.AQUA));
            p.displayClientMessage(Component.translatable("message.headinajar.promotion.candidate", name), true);
        }
        return c;
    }

    private static List<ServerPlayer> hearers(ServerLevel level, Entity at) {
        return level.getPlayers(p -> p.distanceToSqr(at) <= HEAR * HEAR);
    }

    static void note(Ceremony c, String what) {
        HISTORY.add(c.level.getGameTime() + " " + c.candidate + " " + what);
        if (HISTORY.size() > 256) HISTORY.remove(0);
    }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post event) {
        if (RUNNING.isEmpty() || !(event.getLevel() instanceof ServerLevel level)) return;
        for (Iterator<Ceremony> it = RUNNING.iterator(); it.hasNext(); ) {
            Ceremony c = it.next();
            if (c.level != level) continue;
            if (c.cancelled != null || step(c)) it.remove();
        }
    }

    /** One tick of a ceremony; true when it is over. */
    static boolean step(Ceremony c) {
        int t = c.tick();
        Mob mob = c.mob();
        ServerPlayer player = c.presser();
        if (!c.committed) {
            // before the cut it stops at the first thing wrong, and nothing has been spent
            String why = null;
            if (mob == null || !mob.isAlive() || mob.isRemoved()) why = "candidate";
            else if (player == null || player.level() != c.level || player.distanceToSqr(mob) > LEASH * LEASH) why = "player";
            if (why != null) {
                cancel(c, why);
                return true;
            }
        }
        if (mob != null && mob.isAlive()) pose(c, mob, player, t);
        holdWatchers(c, mob);
        if (t == CONFETTI && !c.silenced && mob != null) {
            Fx.send(c.level, Fx.of(Fx.CONFETTI, mob.position().add(0, mob.getBbHeight() + 0.3, 0), mob.position(), mob.getId(), -1, 0));
            note(c, "confetti");
        }
        if (t == JINGLE && !c.silenced && mob != null) {
            c.level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), com.coolfreeze23.headinajar.fx.HouseSounds.JINGLE.get(), SoundSource.NEUTRAL, 1, 1);
            note(c, "jingle");
        }
        if (t == COMMIT && !c.committed) {
            if (!commit(c, mob, player)) return true;
        }
        if (t == BELL && !c.silenced) {
            Vec3 at = c.committedAt;
            if (at != null) c.level.playSound(null, at.x, at.y, at.z, com.coolfreeze23.headinajar.fx.HouseSounds.DESK_DING.get(), SoundSource.PLAYERS, 1, 1);
            note(c, "bell");
        }
        if (t == DONE_LINE && !c.silenced && c.committedAt != null) {
            DoloresVoice.say(c.level, BlockPos.containing(c.committedAt), "promotion_done", DoloresVoice.BOTH);
            for (ServerPlayer p : c.level.getPlayers(p -> p.position().distanceToSqr(c.committedAt) <= HEAR * HEAR)) {
                p.displayClientMessage(Component.translatable("dolores.headinajar.promotion_done").withStyle(ChatFormatting.AQUA), true);
            }
            note(c, "done_line");
        }
        if (t >= LENGTH) {
            finish(c);
            return true;
        }
        return false;
    }

    /** The candidate faces whoever is promoting it, holds still, and nods once at {@link #NOD}. */
    private static void pose(Ceremony c, Mob mob, ServerPlayer player, int t) {
        if (c.committed) return;
        mob.getNavigation().stop();
        if (player != null) turnHead(mob, player.getEyePosition());
        if (t >= NOD && t <= NOD + NOD_TICKS) {
            float k = (t - NOD) / (float) NOD_TICKS;
            mob.setXRot(NOD_PITCH * Mth.sin(k * Mth.PI));
        } else if (t == NOD + NOD_TICKS + 1) {
            mob.setXRot(0);
        }
    }

    /** Staff who watch: they stand where they are, heads to the candidate (their brain yields: {@link #holds}). */
    private static void holdWatchers(Ceremony c, Mob candidate) {
        Vec3 at = candidate != null && candidate.isAlive() ? candidate.getEyePosition() : c.committedAt;
        if (at == null) return;
        for (UUID id : c.watchers) {
            if (c.level.getEntity(id) instanceof Mob m && m.isAlive()) turnHead(m, at);
        }
    }

    /** Turns a head towards a point, 12 degrees a tick at most (sent to clients as the head's rotation). */
    static void turnHead(Mob mob, Vec3 at) {
        double dx = at.x - mob.getX(), dz = at.z - mob.getZ(), dy = at.y - mob.getEyeY();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90;
        float head = Mth.approachDegrees(mob.getYHeadRot(), yaw, 12);
        mob.setYHeadRot(head);
        mob.yHeadRotO = head;
        // a body more than its head can turn round follows, as a mob's body does
        float body = mob.yBodyRot, diff = Mth.wrapDegrees(head - body);
        if (Math.abs(diff) > mob.getMaxHeadYRot()) mob.setYBodyRot(head - Math.signum(diff) * mob.getMaxHeadYRot());
        if (!isCandidate(mob)) {
            float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
            mob.setXRot(Mth.approach(mob.getXRot(), Mth.clamp(pitch, -40, 40), 6));
        }
    }

    /** Before the cut: cancelled, nothing spent ("The Board will reconvene. Nothing was spent."). */
    public static void cancel(Ceremony c, String why) {
        if (c.committed || c.cancelled != null) return;
        c.cancelled = why;
        note(c, "cancelled " + why);
        ServerPlayer player = c.presser();
        if (player != null) player.displayClientMessage(Component.translatable("message.headinajar.promotion.cancelled"), true);
        release(c);
    }

    /** The watchers go back to work, and the candidate's head comes level. */
    private static void release(Ceremony c) {
        Mob mob = c.mob();
        if (mob != null) mob.setXRot(0);
        for (UUID id : c.watchers) if (c.level.getEntity(id) instanceof Mob m) m.setXRot(0);
    }

    /** The end, at {@link #LENGTH}: the watchers go back to work, a little happier. */
    private static void finish(Ceremony c) {
        note(c, "finished");
        for (UUID id : c.watchers) {
            if (!(c.level.getEntity(id) instanceof Mob m) || !m.isAlive()) continue;
            StaffFile f = Staff.of(m);
            if (f != null && f.staff()) {
                StaffBlocks.adjust(f, WATCHED);
                m.setData(ModRegistry.STAFF, f);
            }
        }
        release(c);
    }

    // --- the commit --------------------------------------------------------------------------------------------------

    /**
     * Tick {@link #COMMIT}: the cut. The jar and a use of the saw are spent, the head taken (before the body is
     * touched), and the body made a host already half synced to the presser; a creature that is all head goes into the
     * jar whole. False when it could not happen (it is cancelled instead, and nothing is spent).
     */
    static boolean commit(Ceremony c, Mob mob, ServerPlayer player) {
        if (mob == null || player == null) {
            cancel(c, "candidate");
            return false;
        }
        // a seconded Team Lead promoted from the Roster: the collar's unclip, no jar or saw
        if (c.roster) return RosterPromotion.commit(c, mob, player);
        ItemStack jar = player.getOffhandItem();
        InteractionHand sawHand = player.getMainHandItem().getItem() instanceof SurgicalSawItem ? InteractionHand.MAIN_HAND : null;
        if (!(jar.getItem() instanceof LifeSupportJarItem) || SawTable.rank(jar) < SawTable.needs(mob)) {
            cancel(c, "jar");
            return false;
        }
        if (sawHand == null) {
            cancel(c, "saw");
            return false;
        }
        c.committed = true;
        ServerLevel level = c.level;
        long day = Staff.day(level);
        boolean whole = SawTable.headOnly(mob);
        Vec3 neck = com.coolfreeze23.headinajar.body.BodyServer.neck(mob);
        c.committedAt = neck;
        // the head first, as it is now (its file comes with it, marked)
        ItemStack head = JarredHeadItem.of(mob, jar);
        // the stored creature is no longer on the workforce (its record would otherwise carry its worker data)
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, head, tag -> {
            net.minecraft.nbt.CompoundTag attached = tag.getCompound("Entity").getCompound(Staff.ATTACHED);
            attached.remove(com.coolfreeze23.headinajar.HeadInAJar.MOD_ID + ":worker");
            if (attached.isEmpty()) tag.getCompound("Entity").remove(Staff.ATTACHED);
        });
        StaffFile file = Staff.of(mob);
        if (file == null) file = Staff.register(mob, player, "jarred", true);
        StaffFile headFile = file.copy();
        headFile.career.promotedDay = day;
        headFile.career.link = file.number;
        headFile.note("promoted", day, "");
        Staff.write(head, headFile);
        // off the workforce: the lanyard and a Hard Hat come back to the presser; the roster and the index forget it
        Workforce.discharge(mob);
        com.coolfreeze23.headinajar.form.FormActions.give(player, new ItemStack(com.coolfreeze23.headinajar.registry.WorkRegistry.STAFF_LANYARD.get()));
        ItemStack hat = mob.getItemBySlot(EquipmentSlot.HEAD);
        if (hat.is(com.coolfreeze23.headinajar.registry.WorkRegistry.HARD_HAT.get())) {
            com.coolfreeze23.headinajar.form.FormActions.give(player, hat.copy());
            mob.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        }
        // the cut: the cork, the snip, the head into the jar with the body kept (the cut's second flag), the bell at +11
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), com.coolfreeze23.headinajar.registry.ModSounds.BOOTH_CUT.get(), SoundSource.PLAYERS, 0.8f, 1);
        level.playSound(null, neck.x, neck.y, neck.z, com.coolfreeze23.headinajar.fx.BodySounds.CORK.get(), SoundSource.NEUTRAL, 1, 1);
        com.coolfreeze23.headinajar.body.BodyServer.later(level, 3, () -> {
            if (c.silenced) return;
            level.playSound(null, neck.x, neck.y, neck.z, com.coolfreeze23.headinajar.fx.HouseSounds.SNIP.get(), SoundSource.PLAYERS, 0.8f, 1.2f);
            com.coolfreeze23.headinajar.feedback.Sanitary.puff(level, neck.x, neck.y, neck.z);
        });
        Fx.send(level, Fx.of(Fx.HOST_CUT, neck, player.getEyePosition(), player.getId(), mob.getId(), whole ? 1 : CUT_BODY_KEPT));
        player.getItemInHand(sawHand).hurtAndBreak(1, player, sawHand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        jar.consume(1, player);
        com.coolfreeze23.headinajar.form.FormActions.give(player, head);
        if (whole) {
            // nothing is left behind: no body, no drops, no split slime
            if (mob instanceof net.minecraft.world.entity.monster.Slime slime) slime.setSize(1, false);
            mob.discard();
            player.displayClientMessage(Component.translatable("message.headinajar.promotion.whole", Staff.name(file)), true);
        } else {
            // the body: a plain host, with no loot and no death; the file, the name and the number went up to the Board
            // with the head, so the body carries none. It remembers its training (sync 50 or more, handshake done).
            mob.removeData(ModRegistry.STAFF);
            HostData data = Hosts.data(mob);
            data.host = true;
            data.freshness = 100;
            data.sync = Math.max(data.sync, 50);
            data.handshaken = true;
            mob.setPersistenceRequired();
            mob.setTarget(null);
            mob.getNavigation().stop();
            mob.setXRot(0);
            mob.setHealth(Math.max(mob.getHealth(), mob.getMaxHealth() * SurgicalSawItem.HOST_HEALTH_FLOOR));
            Network.syncHost(mob);
            Component name = Staff.name(file);
            com.coolfreeze23.headinajar.body.BodyServer.later(level, 30, () -> player.displayClientMessage(Component.translatable("message.headinajar.promotion.body", name), true));
        }
        com.coolfreeze23.headinajar.genetics.Directory.file(player, mob.getType(), com.coolfreeze23.headinajar.genetics.Directory.Stamp.JARRED);
        Advance.grant(player, "promoted");
        // the advancement also asks for a Board Meeting: one already held counts
        var board = player.server.getAdvancements().get(com.coolfreeze23.headinajar.HeadInAJar.id("board_meeting"));
        if (board != null && player.getAdvancements().getOrStartProgress(board).isDone()) Advance.grant(player, "board_meeting");
        com.coolfreeze23.headinajar.clarity.Memos.send(player, com.coolfreeze23.headinajar.clarity.Memos.Memo.PROMOTION);
        note(c, "commit");
        return true;
    }

    /** {@code Fx.HOST_CUT}'s argument for a cut into a jar that keeps the body (an older game reads it as "into a jar"). */
    public static final int CUT_BODY_KEPT = 2;

    // --- what else has to know ---------------------------------------------------------------------------------------

    /** A chair has started near by: a ceremony before its cut is cancelled; one after it goes silent. */
    static void chairStarted(Level level, BlockPos chair) {
        Vec3 at = Vec3.atCenterOf(chair);
        for (Ceremony c : RUNNING) {
            if (c.level != level || c.cancelled != null) continue;
            Mob mob = c.mob();
            Vec3 where = mob != null ? mob.position() : c.committedAt;
            if (where == null || where.distanceToSqr(at) > Operations.RADIUS * Operations.RADIUS) continue;
            if (!c.committed) cancel(c, "operation");
            else if (!c.silenced) {
                c.silenced = true;
                note(c, "silenced");
            }
        }
    }

    /** A strike has started at this clock: a ceremony there before its cut is cancelled. */
    public static void strikeStarted(Level level, BlockPos clock) {
        for (Ceremony c : RUNNING) if (c.level == level && clock.equals(c.clock)) cancel(c, "strike");
    }

    /** Whether this creature is in a ceremony right now (the candidate or a watcher): its brain yields to the ceremony. */
    public static boolean holds(Mob mob) {
        if (RUNNING.isEmpty()) return false;
        UUID id = mob.getUUID();
        for (Ceremony c : RUNNING) {
            if (c.cancelled != null || c.level != mob.level()) continue;
            if (!c.committed && c.candidate.equals(id)) return true;
            if (c.watchers.contains(id)) return true;
        }
        return false;
    }

    static boolean isCandidate(Mob mob) {
        for (Ceremony c : RUNNING) if (!c.committed && c.candidate.equals(mob.getUUID())) return true;
        return false;
    }

    /** The running ceremonies (tests). */
    public static List<Ceremony> running() {
        return RUNNING;
    }

    /** The candidate cannot be hurt during it; a player who tries is asked for quiet. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void hurt(LivingIncomingDamageEvent event) {
        if (RUNNING.isEmpty() || !(event.getEntity() instanceof Mob mob) || !isCandidate(mob)) return;
        event.setCanceled(true);
        if (event.getSource().getEntity() instanceof ServerPlayer p) p.displayClientMessage(Component.translatable("message.headinajar.promotion.quiet"), true);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        RUNNING.clear();
    }
}
