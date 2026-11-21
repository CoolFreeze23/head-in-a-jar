package com.coolfreeze23.headinajar.staff.work;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.mind.MobMotor;
import com.coolfreeze23.headinajar.mixin.MobAccessor;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffBlocks;
import com.coolfreeze23.headinajar.staff.StaffFile;
import com.coolfreeze23.headinajar.staff.StaffShake;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The workforce: living creatures hired with a Staff Lanyard. Who is a worker and what it holds ({@link WorkerData}),
 * hiring (the Lanyard's checks in the book's order), assigning it to a Time Clock, Let go (two presses, the notice
 * period), the site box, the floating stop lines, and the once-a-tick hook {@code HostAiMixin} calls instead of the mob's
 * own AI. The day itself (clocks, shifts, jobs, details) is the brain's ({@link WorkerBrain}).
 */
public final class Workforce {
    /** Ticks between floating lines from one worker. */
    public static final int SAY_TICKS = 100;
    /** How long a floating line shows (the client keeps it this long). */
    public static final int SAY_SHOW = 100;
    /** The notice period on Let go. */
    public static final int NOTICE_TICKS = 200;
    /** The second press of Let go must come within this many ticks of the first. */
    public static final int CONFIRM_TICKS = 60;
    /** Staff look at whoever hired them this long. */
    public static final int LOOK_TICKS = 40;
    /** Their path search's reach (the follow range a staff mob carries while it works). */
    public static final double FOLLOW_RANGE = 48;
    /** How far a site reaches up and down from its clock. */
    public static final int SITE_HEIGHT = 8;
    /** Distance at which an awaiting worker sees a clock, and colleagues feel a death. */
    public static final int NEAR = 16;
    public static final ResourceLocation RANGE_ID = HeadInAJar.id("worker_range");

    /** Operators' mandatory break ({@code /headoffice staff pause}); kept in world data by the command. */
    public static volatile boolean paused;

    /** First presses of Let go waiting for the second: player -> (worker, game time). */
    private static final Map<UUID, Map.Entry<UUID, Long>> CONFIRM = new HashMap<>();

    private Workforce() {
    }

    // --- who --------------------------------------------------------------------------------------------------------

    public static boolean isWorker(Entity e) {
        return e instanceof Mob && e.hasData(WorkRegistry.WORKER);
    }

    /** On a client, whether an entity is staff as far as that client was told (its worker look); set by the client. */
    public static java.util.function.Predicate<Entity> seenAsWorker = e -> false;

    /** Whether an entity is staff, on either side: the record on a server, the look a client was sent on a client. */
    public static boolean isWorkerOn(net.minecraft.world.level.Level level, Entity e) {
        return level.isClientSide ? e instanceof Mob && seenAsWorker.test(e) : isWorker(e);
    }

    /** Its worker record, or null. */
    public static WorkerData data(Entity e) {
        return isWorker(e) ? e.getData(WorkRegistry.WORKER) : null;
    }

    public static UUID employer(Entity e) {
        WorkerData d = data(e);
        return d == null ? null : d.employer;
    }

    /** Whether {@code player} may give orders to this worker: its employer, or someone the employer trusts. */
    public static boolean mayOrder(Entity worker, Player player) {
        UUID employer = employer(worker);
        return employer != null && (employer.equals(player.getUUID())
                || com.coolfreeze23.headinajar.form.HeadRegistry.get(player).trusts(employer, player));
    }

    public static boolean enabled() {
        return Config.get(Config.STAFF_ENABLED, true);
    }

    // --- the site ---------------------------------------------------------------------------------------------------

    public static int radius() {
        return Math.min(16, Config.get(Config.SITE_RADIUS, 16));
    }

    /** A site's box: {@link #radius} out from the clock on the level (33 x 33 by default), 8 up and 8 down. */
    public static AABB box(BlockPos clock) {
        int r = radius();
        return new AABB(clock.getX() - r, clock.getY() - SITE_HEIGHT, clock.getZ() - r, clock.getX() + r + 1, clock.getY() + SITE_HEIGHT + 1, clock.getZ() + r + 1);
    }

    /** Whether a block is inside the site of the clock at {@code clock}. */
    public static boolean inBox(BlockPos clock, BlockPos pos) {
        int r = radius();
        return Math.abs(pos.getX() - clock.getX()) <= r && Math.abs(pos.getZ() - clock.getZ()) <= r && Math.abs(pos.getY() - clock.getY()) <= SITE_HEIGHT;
    }

    /** Whether the worker's clock is in this level (a clock in another dimension is "off site"). */
    public static boolean clockHere(Mob mob, WorkerData d) {
        return d.clock != null && d.clock.dimension().equals(mob.level().dimension());
    }

    // --- the tick ---------------------------------------------------------------------------------------------------

    /** Each server tick of a worker, from {@code HostAiMixin}, in place of the mob's own AI. */
    public static void tick(Mob mob) {
        WorkerData d = data(mob);
        if (d == null) return;
        if (d.state == WorkerData.State.NOTICE) {
            LetGo.tick(mob, d);
            return;
        }
        if (paused || !enabled()) {
            if (!MobMotor.idle(mob) || Movers.moving(mob)) WorkerPaths.stop(mob);
            return;
        }
        // a Promotion to the Board: the candidate and the staff watching stand still, their heads the ceremony's
        if (com.coolfreeze23.headinajar.staff.Promotion.holds(mob)) {
            if (!MobMotor.idle(mob) || Movers.moving(mob)) WorkerPaths.stop(mob);
            return;
        }
        // a strike or a grievance runs the worker this tick in place of its work
        if (com.coolfreeze23.headinajar.staff.SiteState.held(mob, d)) return;
        if ((mob.tickCount & 31) == 0) keepRange(mob);
        MobMotor.tick(mob);
        Movers.tick(mob);
        if (d.lookUntil > mob.level().getGameTime() && mob.level() instanceof ServerLevel level && d.lookAt != null) {
            Entity hirer = level.getEntity(d.lookAt);
            if (hirer != null) mob.getLookControl().setLookAt(hirer, 30, 30);
        }
        if (Math.floorMod(mob.tickCount + mob.getId(), thinkTicks()) == 0) WorkerBrain.think(mob, d);
    }

    public static int thinkTicks() {
        return Math.max(5, Math.min(40, Config.get(Config.THINK_TICKS, 10)));
    }

    /** The follow range a worker carries (not saved: put back after a load). */
    static void keepRange(Mob mob) {
        AttributeInstance range = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (range == null || range.hasModifier(RANGE_ID)) return;
        double add = FOLLOW_RANGE - range.getBaseValue();
        if (add > 0) range.addTransientModifier(new AttributeModifier(RANGE_ID, add, AttributeModifier.Operation.ADD_VALUE));
    }

    static void dropRange(Mob mob) {
        AttributeInstance range = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (range != null) range.removeModifier(RANGE_ID);
    }

    // --- what they say ----------------------------------------------------------------------------------------------

    /**
     * A floating stop line over the worker for 5 seconds ({@code staff.headinajar.say.<key>}), to the players who can see
     * it, at most one each {@link #SAY_TICKS}; it is also the worker's status on the Roster. Returns whether it was said.
     */
    public static boolean say(Mob mob, String key, Object... args) {
        WorkerData d = data(mob);
        if (d == null) return false;
        d.status = "staff.headinajar.say." + key;
        long now = mob.level().getGameTime();
        if (now - d.lastSaid < SAY_TICKS) return false;
        // one voice for a chorus: a colleague of the same clock is still showing the same line, so this one keeps
        // quiet for the round (the line stays its status)
        if (d.clock != null) {
            String chorus = d.clock + " " + key + " " + java.util.Arrays.toString(args);
            Long said = CHORUS.get(chorus);
            if (said != null && now - said >= 0 && now - said < CHORUS_TICKS) return false;
            if (CHORUS.size() > 512) CHORUS.clear();
            CHORUS.put(chorus, now);
        }
        d.lastSaid = now;
        sayNow(mob, Component.translatable("staff.headinajar.say." + key, args));
        return true;
    }

    /** How long a stop line said by one worker silences the same line from its clock's other staff: while it shows. */
    public static final int CHORUS_TICKS = SAY_SHOW;
    /** The last time each clock's staff said each line (clock, key and arguments), for {@link #say}. */
    private static final java.util.Map<String, Long> CHORUS = new java.util.HashMap<>();

    /** A line over a worker, now, with no limit (the brain's own lines go through {@link #say}). */
    public static void sayNow(Mob mob, Component line) {
        if (!(mob.level() instanceof ServerLevel)) return;
        WorkerNet.Say say = new WorkerNet.Say(mob.getId(), line);
        for (ServerPlayer p : ((ServerLevel) mob.level()).getPlayers(p -> p.distanceToSqr(mob) < 48 * 48)) {
            if (p.connection != null && p.connection.hasChannel(WorkerNet.Say.TYPE)) PacketDistributor.sendToPlayer(p, say);
        }
    }

    // --- hiring -----------------------------------------------------------------------------------------------------

    /** Why {@code player} may not hire {@code target} (a key under {@code refusal.headinajar.} and its arguments), or null. */
    public static Object[] refusal(ServerPlayer player, Entity target) {
        if (target instanceof Player) return new Object[]{"lanyard.player"};
        if (!(target instanceof Mob mob) || target instanceof com.coolfreeze23.headinajar.entity.MechaEntity) return new Object[]{"lanyard.unknown", target.getType().getDescription()};
        if (!enabled()) return new Object[]{"lanyard.disabled"};
        WorkTable.Spec spec = WorkTable.of(mob);
        if (mob.isBaby()) return new Object[]{"lanyard.baby"};
        boolean hostile = mob instanceof Enemy;
        if (hostile && spec != null && spec.refusal() == null && !overqualified(mob)) {
            if (!Config.get(Config.HIRE_HOSTILES, true)) return new Object[]{"lanyard.no_hostiles"};
            if (mob.getHealth() > mob.getMaxHealth() * com.coolfreeze23.headinajar.item.SurgicalSawItem.MAX_HEALTH_FRACTION) return new Object[]{"lanyard.not_ready"};
        }
        WorkerData d = data(mob);
        if (d != null) {
            if (d.employer.equals(player.getUUID())) return new Object[]{"staff.reports_to", name(mob), player.getGameProfile().getName()};
            return new Object[]{"lanyard.poach", employerName(mob)};
        }
        if (mob instanceof OwnableEntity pet && pet.getOwnerUUID() != null && !pet.getOwnerUUID().equals(player.getUUID())) {
            Entity owner = pet.getOwner();
            return new Object[]{"lanyard.poach", owner != null ? owner.getDisplayName() : Component.translatable("staff.headinajar.another_employer")};
        }
        if (mob instanceof IronGolem golem && !golem.isPlayerCreated()) return new Object[]{"lanyard.village_golem"};
        if (Hosts.isHost(mob)) return new Object[]{"lanyard.host"};
        if (overqualified(mob)) return new Object[]{"lanyard.overqualified"};
        if (spec == null) return new Object[]{"lanyard.unknown", mob.getType().getDescription()};
        if (spec.refusal() != null) return new Object[]{spec.refusal(), mob.getType().getDescription()};
        if (!Movers.canMove(mob, spec)) return new Object[]{"lanyard.unknown", mob.getType().getDescription()};
        // 0.7.2: new staff won't cross the picket line
        if (com.coolfreeze23.headinajar.staff.Strikes.hireRefused(player, mob)) return new Object[]{"strike.hire"};
        int max = Config.get(Config.MAX_PER_PLAYER, 24);
        int have = WorkerIndex.get(player.server).count(player.getUUID());
        if (have >= max) return new Object[]{"lanyard.headcount", have};
        return null;
    }

    static boolean overqualified(Mob mob) {
        return mob instanceof EnderDragon || mob instanceof WitherBoss || mob instanceof Warden || mob instanceof ElderGuardian
                || mob.getType().is(net.neoforged.neoforge.common.Tags.EntityTypes.BOSSES);
    }

    /**
     * Use of a Staff Lanyard on a creature: refused (the line, the refusal sound, the candidate's head shake) or hired.
     * Returns whether it was hired.
     */
    public static boolean useLanyard(ServerPlayer player, ItemStack lanyard, Entity target) {
        Object[] no = refusal(player, target);
        if (no != null) {
            Object[] args = java.util.Arrays.copyOfRange(no, 1, no.length);
            Refusals.deny(player, target instanceof Mob m ? StaffShake.at(m) : com.coolfreeze23.headinajar.feedback.Refuser.at(target), (String) no[0], args);
            WorkerLooks.refused(player, target);
            return false;
        }
        hire(player, (Mob) target);
        lanyard.consume(1, player);
        return true;
    }

    /** Hires a creature for {@code player} (every check already passed). */
    public static WorkerData hire(ServerPlayer player, Mob mob) {
        ServerLevel level = player.serverLevel();
        long day = Staff.day(level);
        StaffFile f = Staff.register(mob, player, "hired", true);
        if (f != null) {
            f.left = null;
            if (!f.staff()) {
                f.morale = StaffBlocks.START;
                f.lowDawns = 0;
            }
            f.paidThrough = Math.max(f.paidThrough, day);
            f.lastDawn = Math.max(f.lastDawn, day);
            mob.setData(com.coolfreeze23.headinajar.registry.ModRegistry.STAFF, f);
        }
        WorkerData d = new WorkerData();
        d.employer = player.getUUID();
        d.hiredDay = day;
        d.wasPersistent = ((MobAccessor) mob).headinajar$persistent();
        WorkTable.Spec spec = WorkTable.of(mob);
        d.shift = spec == null ? WorkerData.Shift.DAY : spec.defaultShift();
        d.state = WorkerData.State.AWAITING;
        d.lookAt = player.getUUID();
        d.lookUntil = level.getGameTime() + LOOK_TICKS;
        if (mob instanceof WanderingTrader trader) {
            d.oldDespawnDelay = trader.getDespawnDelay();
            trader.setDespawnDelay(0);
        }
        mob.setData(WorkRegistry.WORKER, d);
        mob.setPersistenceRequired();
        calm(level, mob);
        if (mob instanceof TamableAnimal pet) {
            pet.setOrderedToSit(false);
            pet.setInSittingPose(false);
        }
        keepRange(mob);
        WorkerIndex.get(player.server).seen(player.getUUID(), mob.getUUID(), f == null ? 0 : f.number,
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString(), WorkerIndex.at(mob));
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), WorkSounds.LANYARD_ON.get(), SoundSource.NEUTRAL, 1, 1);
        WorkerLooks.hired(mob);
        com.coolfreeze23.headinajar.genetics.Directory.file(player, mob.getType(), com.coolfreeze23.headinajar.genetics.Directory.Stamp.HIRED);
        com.coolfreeze23.headinajar.Advance.grant(player, "non_human_resources");
        com.coolfreeze23.headinajar.clarity.Memos.send(player, com.coolfreeze23.headinajar.clarity.Memos.Memo.FIRST_HIRE);
        player.displayClientMessage(Component.translatable("message.headinajar.lanyard.hired", name(mob)), true);
        d.status = "staff.headinajar.status.awaiting";
        return d;
    }

    /**
     * Puts a creature on the workforce with no Lanyard: a host whose collar has just taken a jarred head (0.7.1). Its
     * file is already on it (the head's); it awaits assignment as a hired worker does. {@code type} is the species the
     * Roster shows (the head's).
     */
    public static WorkerData enlist(ServerLevel level, Mob mob, UUID employer, net.minecraft.world.entity.EntityType<?> type) {
        WorkerData d = new WorkerData();
        d.employer = employer;
        d.hiredDay = Staff.day(level);
        d.wasPersistent = true;
        WorkTable.Spec spec = WorkTable.of(mob);
        d.shift = spec == null ? WorkerData.Shift.DAY : spec.defaultShift();
        d.state = WorkerData.State.AWAITING;
        mob.setData(WorkRegistry.WORKER, d);
        mob.setPersistenceRequired();
        calm(level, mob);
        keepRange(mob);
        StaffFile f = Staff.of(mob);
        // on the payroll from today, as a hire is (a head already on it keeps its morale and pay record)
        if (f != null) {
            long day = Staff.day(level);
            f.left = null;
            if (!f.staff()) {
                f.morale = StaffBlocks.START;
                f.lowDawns = 0;
            }
            f.paidThrough = Math.max(f.paidThrough, day);
            f.lastDawn = Math.max(f.lastDawn, day);
            mob.setData(com.coolfreeze23.headinajar.registry.ModRegistry.STAFF, f);
        }
        WorkerIndex.get(level.getServer()).seen(employer, mob.getUUID(), f == null ? 0 : f.number,
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(), WorkerIndex.at(mob));
        d.status = "staff.headinajar.status.awaiting";
        WorkerLooks.changed(mob);
        return d;
    }

    /**
     * Takes a worker off the workforce at once, with no notice and no Exit Interview: its carry and tool set down, its
     * personal card back to the rack (else at its feet), off its clock, its detail and the index, then the record
     * removed ({@link #release}). A seconded head being unseated, or its body lost under it.
     */
    public static void discharge(Mob mob) {
        WorkerData d = data(mob);
        if (d == null || !(mob.level() instanceof ServerLevel level)) return;
        setDown(mob, d);
        if (!d.card.isEmpty()) {
            ItemStack card = d.card;
            d.card = ItemStack.EMPTY;
            if (!Sites.returnCard(level, d.clock, card)) mob.spawnAtLocation(card);
        }
        Sites.leave(level, d.clock, mob.getUUID());
        WorkerIndex.get(level.getServer()).remove(d.employer, mob.getUUID());
        if (d.detail != null) {
            ServerPlayer lead = level.getServer().getPlayerList().getPlayer(d.detail);
            if (lead != null) {
                java.util.List<UUID> roll = new java.util.ArrayList<>(Detail.members(lead));
                if (roll.remove(mob.getUUID())) Detail.save(lead, roll, Detail.mode(lead));
            }
        }
        d.dropTask();
        release(mob);
    }

    /** Clears a creature's vanilla mind: no target, no anger, no path, an empty brain (it re-senses after Let go). */
    @SuppressWarnings("unchecked")
    static void calm(ServerLevel level, Mob mob) {
        mob.setTarget(null);
        mob.setAggressive(false);
        mob.setLastHurtByMob(null);
        mob.getNavigation().stop();
        if (mob instanceof net.minecraft.world.entity.NeutralMob neutral) neutral.stopBeingAngry();
        var brain = (net.minecraft.world.entity.ai.Brain<Mob>) (net.minecraft.world.entity.ai.Brain<?>) mob.getBrain();
        brain.stopAll(level, mob);
        brain.clearMemories();
        MobMotor.halt(mob);
    }

    /** Ties a worker to a clock (the clock has already taken it onto its roster). */
    public static void assign(Mob mob, GlobalPos clock) {
        WorkerData d = data(mob);
        if (d == null) return;
        d.clock = clock;
        d.dropTask();
        d.state = WorkerData.State.TO_CLOCK;
        d.status = "staff.headinajar.status.waiting";
        WorkerLooks.changed(mob);
    }

    // --- names ------------------------------------------------------------------------------------------------------

    /** A worker's name as lines say it: its file's given name, or its species. */
    public static Component name(Mob mob) {
        StaffFile f = Staff.of(mob);
        if (mob.hasCustomName()) return mob.getCustomName();
        return f == null ? mob.getType().getDescription() : Staff.name(f);
    }

    public static Component employerName(Mob mob) {
        StaffFile f = Staff.of(mob);
        if (f != null && f.employer != null) return Staff.employer(f);
        return Component.translatable("staff.headinajar.another_employer");
    }

    // --- Let go -----------------------------------------------------------------------------------------------------

    /**
     * Sneak + Use with a Clipboard on a worker: the first press asks ("Let %s go? Do it again to confirm."), a second
     * within {@link #CONFIRM_TICKS} lets it go. Someone else's worker is refused. Returns true (the press always answers).
     */
    public static boolean requestLetGo(ServerPlayer player, Mob mob) {
        WorkerData d = data(mob);
        if (d == null) return false;
        if (!mayOrder(mob, player)) {
            Refusals.deny(player, StaffShake.at(mob), "staff.let_go_not_yours", name(mob), employerName(mob));
            return true;
        }
        if (d.state == WorkerData.State.NOTICE) {
            Refusals.deny(player, StaffShake.at(mob), "staff.already_let_go", name(mob));
            return true;
        }
        // 0.7.2: not during industrial action
        if (com.coolfreeze23.headinajar.staff.Strikes.clockOf(mob) != null) {
            Refusals.deny(player, StaffShake.at(mob), "strike.let_go");
            return true;
        }
        // a seconded head is not let go from its body: it is unseated, file and grade kept
        if (com.coolfreeze23.headinajar.staff.second.Secondees.seated(mob)) {
            Refusals.deny(player, com.coolfreeze23.headinajar.feedback.Refuser.at(mob), "secondment.let_go");
            return true;
        }
        long now = player.serverLevel().getGameTime();
        Map.Entry<UUID, Long> first = CONFIRM.get(player.getUUID());
        if (first != null && first.getKey().equals(mob.getUUID()) && now - first.getValue() <= CONFIRM_TICKS) {
            CONFIRM.remove(player.getUUID());
            letGo(player, mob);
            return true;
        }
        CONFIRM.put(player.getUUID(), Map.entry(mob.getUUID(), now));
        player.displayClientMessage(Component.translatable("message.headinajar.let_go.confirm", name(mob)), true);
        player.serverLevel().playSound(null, mob.getX(), mob.getY(), mob.getZ(), net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1, 0.8f);
        return true;
    }

    /** Lets a worker go now (the second press). {@code by} is who pressed (the employer or someone they trust). */
    public static void letGo(ServerPlayer by, Mob mob) {
        WorkerData d = data(mob);
        if (d == null || !(mob.level() instanceof ServerLevel level)) return;
        ServerPlayer employer = level.getServer().getPlayerList().getPlayer(d.employer);
        ServerPlayer to = employer != null ? employer : by;
        // the badge comes off: the lanyard and Form 45-S to the employer, the Hard Hat with them
        give(to, new ItemStack(WorkRegistry.STAFF_LANYARD.get()));
        give(to, WorkRegistry.separationNotice(mob));
        ItemStack head = mob.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        if (head.is(WorkRegistry.HARD_HAT.get())) {
            give(to, head.copy());
            mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ItemStack.EMPTY);
        }
        setDown(mob, d);
        if (!d.card.isEmpty()) {
            ItemStack card = d.card;
            d.card = ItemStack.EMPTY;
            if (!Sites.returnCard(level, d.clock, card)) give(to, card);
        }
        Sites.leave(level, d.clock, mob.getUUID());
        WorkerIndex.get(level.getServer()).remove(d.employer, mob.getUUID());
        // the file: left, restructuring, and the Exit Interview
        StaffFile f = Staff.of(mob);
        if (f != null) {
            long day = Staff.day(level);
            f.left = new StaffFile.Left(day, LetGo.REASON);
            f.note("left", day, LetGo.REASON);
            mob.setData(com.coolfreeze23.headinajar.registry.ModRegistry.STAFF, f);
            com.coolfreeze23.headinajar.staff.StaffEvents.exitInterview(level, mob, f, LetGo.REASON, false);
        }
        to.displayClientMessage(Component.translatable("message.headinajar.let_go.lanyard", name(mob)), true);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), WorkSounds.LANYARD_OFF.get(), SoundSource.NEUTRAL, 1, 1);
        com.coolfreeze23.headinajar.Advance.grant(by, "restructuring");
        // off its detail at once (its player's roll loses it)
        if (d.detail != null) {
            ServerPlayer lead = level.getServer().getPlayerList().getPlayer(d.detail);
            if (lead != null) {
                java.util.List<UUID> roll = new java.util.ArrayList<>(Detail.members(lead));
                if (roll.remove(mob.getUUID())) Detail.save(lead, roll, Detail.mode(lead));
            }
        }
        Quirks.offDuty(mob);
        d.state = WorkerData.State.NOTICE;
        d.noticeUntil = level.getGameTime() + NOTICE_TICKS;
        d.detail = null;
        d.dropTask();
        d.status = null;
        WorkerLooks.letGo(mob);
        LetGo.start(level, mob, d);
    }

    /** Sets down what a worker carries, its tool and its pocket, at its feet. */
    public static void setDown(Mob mob, WorkerData d) {
        com.coolfreeze23.headinajar.staff.work.jobs.Jobs.hideCarry(mob, d); // jobs: a fox's mouth, an allay's hand, an enderman's barrel
        for (ItemStack s : d.carried) if (!s.isEmpty()) mob.spawnAtLocation(s);
        d.carried.clear();
        if (!d.tool.isEmpty()) mob.spawnAtLocation(d.tool);
        d.tool = ItemStack.EMPTY;
        if (!d.pocket.isEmpty()) mob.spawnAtLocation(d.pocket);
        d.pocket = ItemStack.EMPTY;
    }

    /** The end of a worker: the record removed and everything the hire changed put back (Let go's notice ends). */
    public static void release(Mob mob) {
        WorkerData d = data(mob);
        if (d == null) return;
        Quirks.offDuty(mob);
        mob.removeData(WorkRegistry.WORKER);
        dropRange(mob);
        ((MobAccessor) mob).headinajar$setPersistent(d.wasPersistent);
        if (mob instanceof WanderingTrader trader) trader.setDespawnDelay(Math.max(0, d.oldDespawnDelay));
        MobMotor.release(mob);
        mob.setXRot(0);
        WorkerLooks.changed(mob);
    }

    static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack) && !stack.isEmpty()) player.drop(stack, false);
    }

    /** A test or command: whether a living creature is still the worker it was (alive, in the world). */
    public static boolean present(LivingEntity e) {
        return e != null && e.isAlive() && !e.isRemoved();
    }

    /** Whether a level is the server's (a guard for code shared with the client). */
    static boolean server(Level level) {
        return level instanceof ServerLevel;
    }
}
