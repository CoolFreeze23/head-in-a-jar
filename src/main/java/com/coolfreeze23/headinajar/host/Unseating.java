package com.coolfreeze23.headinajar.host;

import com.coolfreeze23.headinajar.Advance;
import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.SeparationChairBlockEntity;
import com.coolfreeze23.headinajar.clarity.Memos;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.entity.SeatEntity;
import com.coolfreeze23.headinajar.entity.ShellEntity;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffFile;
import com.coolfreeze23.headinajar.staff.second.Secondees;
import com.coolfreeze23.headinajar.supplier.Equipment;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Unseating Policy. A lance (anything in {@code #minecraft:spears}) that knocks a rider off its mount does so by
 * dismounting it in the same tick it hits. The hit marks its target for that tick; a dismount of a marked rider in that
 * tick is the lance's, and the company decides who holds:
 * <ul>
 * <li>a mecha's pilot, a Lunk's jar and anyone in a Passenger Pod hold;</li>
 * <li>a hosted jar (on a body, a Rover or a Spider Walker) is unseated, as a host's rider: it lands beside the body,
 * takes one knock (never the last) and is told by what; the body stands, its sync kept, and the charge that took its
 * jar does not hurt it as it passes ({@code combat.lanceUnseatsHosts} off: hosts hold too);</li>
 * <li>a patient lanced out of the Separation Chair before the cut has the procedure called off;</li>
 * <li>anyone else (a vanilla mount, the Wheelhouse's bench) gets off as the game says.</li>
 * </ul>
 * A Socket's jar and a secondee's head ride nothing, so a charge at them only gets a line (and the Socket's clamps take
 * the knock). Keyed to the tag and the
 * game's own dismount event, so it covers any weapon in the tag that unseats by hitting. A hit that unseats without
 * hurting (a target fleeing nearly as fast as the lance) still knocks it back first; that knock, with a spear being
 * charged close by, marks the target too.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID)
public final class Unseating {
    /** How near a charging spear must be for a knock to count as its hit (its reach and a running start). */
    public static final double CHARGE_REACH = 8;
    /** A Socket's line at most this often for one jar (ticks). */
    private static final int SOCKET_QUIET = 40;
    /** Remembered per player once the collar line has been said (survives death). */
    public static final String COLLAR_TOLD = "headinajar_collar_told";

    /** A lance's hit: the game time it landed, who held it (may be null) and the weapon. */
    public record Lance(long time, Entity by, ItemStack weapon) {
    }

    private static final Map<UUID, Lance> HIT = new HashMap<>();
    /** How long after a lance took a body's jar that lance's charge still passes over the body (ticks: the pass). */
    public static final int PASS_TICKS = 20;
    /** Bodies whose jar a lance took off: when (game time), and who held that lance (may be null). */
    private record Unseated(long time, UUID by) {
    }
    private static final Map<UUID, Unseated> UNSEATED_FROM = new HashMap<>();
    /** The last time each body was spared a charge aimed at its rider (game time), for tests. */
    private static final Map<UUID, Long> SPARED = new HashMap<>();
    private static final Map<UUID, Long> SOCKET_TOLD = new HashMap<>();
    /** The last policy line each player was told (its key's last part), for tests. */
    private static final Map<UUID, String> SAID = new HashMap<>();

    private Unseating() {
    }

    // --- the mark ------------------------------------------------------------------------------------------------------

    /** The lance in a hit: the weapon the hit was made with, or the spear its maker is charging; else empty. */
    public static ItemStack lanceOf(DamageSource source) {
        ItemStack weapon = source.getWeaponItem();
        if (weapon != null && weapon.is(Equipment.SPEARS)) return weapon;
        if (source.getDirectEntity() instanceof LivingEntity l && charging(l)) return l.getUseItem();
        return ItemStack.EMPTY;
    }

    /** Whether it is charging with a spear (holding Use with one). */
    public static boolean charging(LivingEntity e) {
        return e.isUsingItem() && e.getUseItem().is(Equipment.SPEARS);
    }

    /**
     * A host body (not a mecha) with a jar riding it, or whose jar this charger's lance took off in the last
     * {@value #PASS_TICKS} ticks (a mounted charge reaches the body a tick or two after the jar).
     */
    static boolean carriesJar(LivingEntity body, LivingEntity charger) {
        if (body instanceof MechaEntity || !Hosts.isHost(body)) return false;
        Unseated off = UNSEATED_FROM.get(body.getUUID());
        long now = body.level().getGameTime();
        if (off != null && now >= off.time() && now - off.time() <= PASS_TICKS && charger.getUUID().equals(off.by())) return true;
        for (Entity rider : body.getPassengers()) {
            if (rider instanceof Player p && FormActions.state(p).form() == Form.HOSTED) return true;
        }
        return false;
    }

    /** Whether a charge at this body's rider passed over the body this tick or since (game time of the last), for tests. */
    public static long spared(Entity body) {
        return SPARED.getOrDefault(body.getUUID(), -1L);
    }

    /** Marks a hit by a lance on this entity for this tick. */
    public static void mark(Entity target, Entity by, ItemStack weapon) {
        long now = target.level().getGameTime();
        if (HIT.size() > 64) HIT.values().removeIf(l -> now - l.time() > 200 || l.time() > now);
        HIT.put(target.getUUID(), new Lance(now, by, weapon.copy()));
    }

    /** Whether a lance hit this entity within the last {@code ticks} ticks. */
    public static boolean recentlyLanced(Entity e, int ticks) {
        Lance l = e == null ? null : HIT.get(e.getUUID());
        long now = e == null ? 0 : e.level().getGameTime();
        return l != null && now >= l.time() && now - l.time() <= ticks;
    }

    /** The lance that hit this entity this tick, or null. */
    public static Lance lanced(Entity e) {
        Lance l = e == null ? null : HIT.get(e.getUUID());
        return l != null && l.time() == e.level().getGameTime() ? l : null;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    static void hit(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;
        ItemStack lance = lanceOf(event.getSource());
        if (lance.isEmpty()) return;
        Entity by = event.getSource().getEntity() != null ? event.getSource().getEntity() : event.getSource().getDirectEntity();
        // a charge at a hosted jar is the jar's: the body under it (or just left by it) stands, unhurt by that charge
        if (by instanceof LivingEntity l && charging(l) && carriesJar(target, l)) {
            event.setCanceled(true);
            if (SPARED.size() > 64) SPARED.clear();
            SPARED.put(target.getUUID(), target.level().getGameTime());
            return;
        }
        mark(target, by, lance);
        // a charge at something with nothing to unseat: the Socket's clamps, a secondee's collar
        if (by instanceof LivingEntity l && charging(l)) charged(target);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    static void knocked(LivingKnockBackEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;
        // a Socket's jar is clamped: a lance's knock does not carry it off the socket
        if (target instanceof ServerPlayer p && FormActions.state(p).form() == Form.SOCKET) {
            if (lanced(target) != null || charger(target) != null) event.setCanceled(true);
            return;
        }
        if (!target.isPassenger() || lanced(target) != null) return;
        LivingEntity charger = charger(target);
        if (charger != null) mark(target, charger, charger.getUseItem());
    }

    /** The nearest creature charging a spear within {@value #CHARGE_REACH} blocks (not riding with it), or null. */
    public static LivingEntity charger(LivingEntity target) {
        return target.level().getNearestEntity(LivingEntity.class,
                net.minecraft.world.entity.ai.targeting.TargetingConditions.forNonCombat().ignoreLineOfSight()
                        .selector(e -> e != target && charging(e) && !e.isPassengerOfSameVehicle(target)),
                target, target.getX(), target.getY(), target.getZ(), target.getBoundingBox().inflate(CHARGE_REACH));
    }

    // --- the policy ----------------------------------------------------------------------------------------------------

    /** Why a lanced rider stays on, as its line's key ({@code message.headinajar.lance.<why>}), or null when it falls. */
    public static String holds(Entity rider, Entity vehicle) {
        if (vehicle instanceof MechaEntity mecha) {
            if (rider == mecha.getFirstPassenger() && rider instanceof Player p && FormActions.state(p).form() == Form.PILOT) return "pilot";
            int seat = mecha.getPassengers().indexOf(rider);
            if (seat > 0 && mecha.has(MechaModule.PASSENGER_POD) && seat - 1 >= mecha.frame().seats()) return "pod";
            return null;
        }
        if (!(rider instanceof Player p) || FormActions.state(p).form() != Form.HOSTED || !Hosts.isHost(vehicle)) return null;
        if (vehicle instanceof ShellEntity shell && shell.kind() == ShellEntity.Kind.LUNK) return "lunk";
        return Config.get(Config.LANCE_UNSEATS_HOSTS, true) ? null : "held";
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void dismount(EntityMountEvent event) {
        if (!event.isDismounting() || event.getLevel().isClientSide()) return;
        Entity rider = event.getEntityMounting(), vehicle = event.getEntityBeingMounted();
        if (rider == null || vehicle == null || !rider.isAlive() || !vehicle.isAlive() || lanced(rider) == null) return;
        String why = holds(rider, vehicle);
        if (why == null) return;
        event.setCanceled(true);
        if (rider instanceof ServerPlayer p) say(p, why);
    }

    /** One of the policy's lines on the action bar ({@code message.headinajar.lance.<key>}). */
    private static void say(ServerPlayer p, String key, Object... args) {
        p.displayClientMessage(com.coolfreeze23.headinajar.supplier.Lines.of("message.headinajar.lance." + key, args), true);
        SAID.put(p.getUUID(), key);
    }

    /** The last policy line this player was told ("pilot", "unseated", ...), or null (tests). */
    public static String lastSaid(Player p) {
        return SAID.get(p.getUUID());
    }

    /** The lance has taken a rider off (no one held it): what that means for a jar, and for a chair's patient. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void unseated(EntityMountEvent event) {
        if (!event.isDismounting() || event.getLevel().isClientSide()) return;
        if (!(event.getEntityMounting() instanceof ServerPlayer p) || !p.isAlive()) return;
        Entity vehicle = event.getEntityBeingMounted();
        Lance lance = lanced(p);
        if (lance == null || vehicle == null) return;
        HIT.remove(p.getUUID());
        // lanced out of the chair before the cut: called off now, and the patient is told (they have left the seat)
        if (vehicle instanceof SeatEntity seat && seat.chair() != null && seat.chair().running()
                && seat.chair().opTick() < SeparationChairBlockEntity.CUT_TICK) {
            seat.chair().abort(p, "abort");
            return;
        }
        JarState state = FormActions.state(p);
        if (state.form() != Form.HOSTED || !Hosts.isHost(vehicle) || vehicle instanceof MechaEntity) return;
        knock(p, state);
        if (UNSEATED_FROM.size() > 64) UNSEATED_FROM.clear();
        UNSEATED_FROM.put(vehicle.getUUID(), new Unseated(vehicle.level().getGameTime(), lance.by() == null ? null : lance.by().getUUID()));
        if (vehicle instanceof ShellEntity shell) say(p, "unseated_shell", shell.getType().getDescription());
        else say(p, "unseated", by(lance), vehicle.getDisplayName());
        Memos.send(p, Memos.Memo.UNSEATED, p.getBlockX(), p.getBlockY(), p.getBlockZ());
        Advance.grant(p, "relieved_of_your_post");
    }

    /** "Zombie's Iron Spear", or the spear alone when nobody held it. */
    public static Component by(Lance lance) {
        if (lance.by() == null) return lance.weapon().getHoverName();
        return Component.translatable("message.headinajar.lance.by", lance.by().getDisplayName(), lance.weapon().getHoverName());
    }

    /** The jar hits the ground: one point of glass, never the last one. */
    static void knock(ServerPlayer p, JarState state) {
        if (state.integrity() > 1) state.setIntegrity(state.integrity() - 1);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), com.coolfreeze23.headinajar.fx.HouseSounds.JAR_KNOCK.get(),
                SoundSource.PLAYERS, 1, 0.9f);
    }

    /** A charge at something that rides nothing: the Socket's jar, a seated secondee. */
    static void charged(LivingEntity target) {
        if (target instanceof ServerPlayer p && FormActions.state(p).form() == Form.SOCKET) {
            long now = p.level().getGameTime();
            Long last = SOCKET_TOLD.put(p.getUUID(), now);
            if (last == null || now - last >= SOCKET_QUIET || now < last) say(p, "socket");
        } else if (target instanceof Mob mob && Secondees.seated(mob) && mob.level() instanceof ServerLevel level) {
            StaffFile f = Staff.of(mob);
            ServerPlayer employer = f == null || f.employer == null ? null : level.getServer().getPlayerList().getPlayer(f.employer);
            if (employer != null) collarOnce(employer);
        }
    }

    /** DOLORES, once to each employer: a secondee's head is fitted, not riding. Returns whether it was said now. */
    public static boolean collarOnce(ServerPlayer employer) {
        CompoundTag kept = employer.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (kept.getBoolean(COLLAR_TOLD)) return false;
        kept.putBoolean(COLLAR_TOLD, true);
        employer.getPersistentData().put(Player.PERSISTED_NBT_TAG, kept);
        employer.sendSystemMessage(Component.translatable("memo.headinajar.from").withStyle(ChatFormatting.GOLD).append(" ")
                .append(Component.translatable("message.headinajar.lance.collar").withStyle(ChatFormatting.WHITE)));
        return true;
    }
}
