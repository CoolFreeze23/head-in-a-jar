package com.coolfreeze23.headinajar.staff.work;

import com.coolfreeze23.headinajar.item.HardHatItem;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffBlocks;
import com.coolfreeze23.headinajar.staff.StaffFile;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * What others see of a worker: the loop and badge in its employer's colour, the hat, the crate, the hire's drop and
 * Let go's pop ({@code WorkerNet.Look}, sent when a player starts seeing it and on each change). The look is worked out
 * here on the server and drawn by the client's {@code StaffLayer}; the given name travels with it only to viewers the
 * employer lets see it (rel60's rule), for the nameplate.
 */
public final class WorkerLooks {
    /** The loop's drop over the head on hire, and how long a card is read (the client plays these from the bit). */
    public static final int HIRE_TICKS = 10, READ_TICKS = 20;

    /** Per worker, server side: when it was hired and last read a card, and what was last sent (so changes only go out). */
    private static final class Moments {
        long hired = Long.MIN_VALUE / 2, reading = Long.MIN_VALUE / 2;
        int colour = -2, bits = -1;
        String name;
    }

    private static final Map<Mob, Moments> MOMENTS = new WeakHashMap<>();

    private WorkerLooks() {
    }

    private static Moments moments(Mob mob) {
        return MOMENTS.computeIfAbsent(mob, m -> new Moments());
    }

    // --- what it looks like -----------------------------------------------------------------------------------------

    /** The badge's colour (the employer's team colour, or company teal), or -1 for no worker. */
    public static int colour(Mob mob) {
        WorkerData d = Workforce.data(mob);
        if (d == null) return -1;
        return StaffBlocks.colour(mob.level(), Staff.of(mob));
    }

    /** The look's bits as they are now ({@link WorkerNet#CARRYING}, {@link WorkerNet#NOTICE}, ...); 0 for no worker. */
    public static int bits(Mob mob) {
        WorkerData d = Workforce.data(mob);
        if (d == null) return 0;
        int bits = 0;
        if (d.carrying()) bits |= WorkerNet.CARRYING;
        if (d.state == WorkerData.State.NOTICE) bits |= WorkerNet.NOTICE;
        if (HardHatItem.wears(mob)) bits |= WorkerNet.HAT;
        Moments m = MOMENTS.get(mob);
        long now = mob.level().getGameTime();
        if (m != null && now - m.hired < HIRE_TICKS) bits |= WorkerNet.HIRED;
        if (m != null && now - m.reading < READ_TICKS) bits |= WorkerNet.READING;
        return bits;
    }

    /** The name {@code viewer} may see over it (empty: none; a creature with a vanilla name tag shows that instead). */
    public static Component name(Mob mob, ServerPlayer viewer) {
        if (mob.hasCustomName() || Workforce.data(mob) == null) return Component.empty();
        StaffFile f = Staff.of(mob);
        if (f == null || !Staff.maySeeName(f, viewer)) return Component.empty();
        return Staff.name(f);
    }

    /** The payload {@code viewer} is sent for this worker. */
    public static WorkerNet.Look look(Mob mob, ServerPlayer viewer) {
        return new WorkerNet.Look(mob.getId(), colour(mob), bits(mob), name(mob, viewer));
    }

    /** What the name depends on (the file's own name), to tell a change. */
    private static String nameKey(Mob mob) {
        StaffFile f = Staff.of(mob);
        if (f == null) return "";
        return (mob.hasCustomName() ? "!" : "") + (f.customName == null ? "" : f.customName) + "#" + f.nameIndex + "@" + f.employer;
    }

    // --- sending ----------------------------------------------------------------------------------------------------

    /** To one player, if their game hears the workforce. */
    public static void send(Mob mob, ServerPlayer viewer) {
        if (WorkerNet.hears(viewer)) PacketDistributor.sendToPlayer(viewer, look(mob, viewer));
    }

    /** To every player close enough to be tracking it (each with the name they may see). */
    private static void broadcast(Mob mob) {
        if (!(mob.level() instanceof ServerLevel level)) return;
        Moments m = moments(mob);
        m.colour = colour(mob);
        m.bits = bits(mob);
        m.name = nameKey(mob);
        double range = Math.max(2, mob.getType().clientTrackingRange()) * 16 + 16;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(mob) < range * range) send(mob, p);
        }
    }

    /** Just hired: the loop drops over its head. */
    public static void hired(Mob mob) {
        moments(mob).hired = mob.level().getGameTime();
        broadcast(mob);
    }

    /** Let go: the badge pops off (a wolf shakes it off, as vanilla's wet wolf does). */
    public static void letGo(Mob mob) {
        if (mob instanceof Wolf && mob.level() instanceof ServerLevel level) level.broadcastEntityEvent(mob, (byte) 8);
        broadcast(mob);
    }

    /** A Job Card read (held to the face by bipeds, sniffed by animals) for {@link #READ_TICKS}. */
    public static void reading(Mob mob) {
        moments(mob).reading = mob.level().getGameTime();
        broadcast(mob);
    }

    /** Something others see changed (carrying, a hat, its state, its name): sent only if the look did change. */
    public static void changed(Mob mob) {
        if (mob.level().isClientSide) return;
        if (Workforce.data(mob) == null) {
            // no longer staff: the loop comes off for everyone who saw it
            broadcast(mob);
            MOMENTS.remove(mob);
            return;
        }
        Moments m = moments(mob);
        if (m.colour == colour(mob) && m.bits == bits(mob) && Objects.equals(m.name, nameKey(mob))) return;
        broadcast(mob);
    }

    /** A refused hire: the badge spins back into the hand (the Lanyard's swing is the item's own) with a glint. */
    public static void refused(ServerPlayer player, Entity target) {
        InteractionHand hand = player.getMainHandItem().is(WorkRegistry.STAFF_LANYARD.get()) ? InteractionHand.MAIN_HAND
                : player.getOffhandItem().is(WorkRegistry.STAFF_LANYARD.get()) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        boolean right = (hand == InteractionHand.MAIN_HAND) == (player.getMainArm() == HumanoidArm.RIGHT);
        Vec3 look = player.getLookAngle();
        Vec3 side = new Vec3(-look.z, 0, look.x).normalize().scale(right ? 0.35 : -0.35);
        Vec3 at = player.getEyePosition().add(look.scale(0.6)).add(side).add(0, -0.35, 0);
        player.serverLevel().sendParticles(ParticleTypes.WAX_OFF, at.x, at.y, at.z, 4, 0.08, 0.08, 0.08, 0.6);
    }

    // --- events -----------------------------------------------------------------------------------------------------

    /** A player starts seeing a worker: it gets its look. */
    @SubscribeEvent
    public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof Mob mob && Workforce.isWorker(mob) && event.getEntity() instanceof ServerPlayer p) send(mob, p);
    }

    /** A worker's helmet changed (a Hard Hat on or off, however it happened). */
    @SubscribeEvent
    public static void equipment(LivingEquipmentChangeEvent event) {
        if (event.getSlot() == EquipmentSlot.HEAD && event.getEntity() instanceof Mob mob && !mob.level().isClientSide && Workforce.isWorker(mob)) {
            changed(mob);
        }
    }
}
