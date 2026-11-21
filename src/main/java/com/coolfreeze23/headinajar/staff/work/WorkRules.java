package com.coolfreeze23.headinajar.staff.work;

import com.coolfreeze23.headinajar.item.HardHatItem;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffFile;
import com.coolfreeze23.headinajar.staff.StaffBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The rules that keep a living worker a worker: everything vanilla still runs outside the mob's own AI (picking up and
 * breaking, teleporting, burning, converting, choosing targets, trampling, dropping) answered for staff, and the death
 * of a worker (everything it held dropped once, the Exit Interview, the colleagues' grief).
 */
public final class WorkRules {
    /** How close to its own clock a worker stands under the awning (no sun, no rain). */
    public static final int AWNING = 2;
    /** A death is felt by the dead worker's colleagues this close, this much (rel60's morale). */
    public static final int GRIEF_RANGE = 16, GRIEF = -5;
    /** A player's Hard Hat takes a third off a falling block's blow, after vanilla's helmet quarter: half in all. */
    public static final float PLAYER_HAT = 0.667f;

    private WorkRules() {
    }

    /** Registers the rules and the quirks on the game's event bus (one line in {@code HeadInAJar}). */
    public static void register() {
        NeoForge.EVENT_BUS.register(WorkRules.class);
        NeoForge.EVENT_BUS.register(Quirks.class);
        NeoForge.EVENT_BUS.register(HardHatItem.class);
        NeoForge.EVENT_BUS.register(WorkPause.class);
    }

    // --- no block is ever broken, nothing picked up -----------------------------------------------------------------

    /**
     * Staff never break or take a block or an item: no item pickup, no ravager leaf-breaking, no blaze fire, no
     * enderman picking up blocks, no trampling (the one griefing hook vanilla asks). A fox on a detail may pick items up
     * to bring them to its supervisor.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void griefing(EntityMobGriefingEvent event) {
        WorkerData d = Workforce.data(event.getEntity());
        if (d == null) {
            // a staff blaze's fireball may ask with itself, not its owner
            if (event.getEntity() instanceof Projectile p && Workforce.isWorker(p.getOwner())) event.setCanGrief(false);
            return;
        }
        if (event.getEntity() instanceof Fox && d.state == WorkerData.State.DETAIL) return;
        event.setCanGrief(false);
    }

    /** Belt and braces: staff never trample farmland. */
    @SubscribeEvent
    public static void trample(BlockEvent.FarmlandTrampleEvent event) {
        if (Workforce.isWorker(event.getEntity())) event.setCanceled(true);
    }

    // --- targets ----------------------------------------------------------------------------------------------------

    /**
     * Staff target nobody the company spares, and on notice nobody at all; iron golems, snow golems and wolves never
     * pick a member of staff as a target, unless that worker hurt them first.
     */
    @SubscribeEvent
    public static void target(LivingChangeTargetEvent event) {
        LivingEntity chooser = event.getEntity();
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target == null) return;
        WorkerData d = Workforce.data(chooser);
        if (d != null && (d.state == WorkerData.State.NOTICE || !FriendOrFoe.mayTarget(d.employer, chooser.level(), target))) {
            event.setCanceled(true);
            return;
        }
        if (Workforce.isWorker(target) && (chooser instanceof IronGolem || chooser instanceof SnowGolem || chooser instanceof Wolf)
                && chooser.getLastHurtByMob() != target) {
            event.setCanceled(true);
        }
    }

    // --- harm -------------------------------------------------------------------------------------------------------

    /** Falling blocks: no harm to staff in a Hard Hat; a player in one takes a third less, on top of vanilla's helmet. */
    @SubscribeEvent
    public static void damage(LivingIncomingDamageEvent event) {
        LivingEntity e = event.getEntity();
        if (!event.getSource().is(DamageTypeTags.DAMAGES_HELMET) || !HardHatItem.wears(e)) return;
        if (Workforce.isWorker(e)) event.setCanceled(true);
        else if (e instanceof Player) event.setAmount(event.getAmount() * PLAYER_HAT);
    }

    /**
     * A staff arrow, fireball or snowball passes over anyone the company spares (the hit is cancelled and it flies on);
     * a staff fireball that meets a block goes out without lighting it; a hit on anyone else is credited (Combat).
     */
    @SubscribeEvent
    public static void impact(ProjectileImpactEvent event) {
        Projectile p = event.getProjectile();
        if (!(p instanceof AbstractArrow || p instanceof Fireball || p instanceof Snowball)) return;
        if (!(p.getOwner() instanceof Mob owner) || !Workforce.isWorker(owner)) return;
        if (event.getRayTraceResult() instanceof EntityHitResult hit) {
            if (!(hit.getEntity() instanceof LivingEntity target)) return;
            if (target == owner || !FriendOrFoe.mayTarget(Workforce.employer(owner), owner.level(), target)) {
                event.setCanceled(true);
                return;
            }
            Combat.credit(owner, target);
        } else if (event.getRayTraceResult() instanceof BlockHitResult && p instanceof Fireball) {
            event.setCanceled(true);
            p.discard();
        }
    }

    // --- death ------------------------------------------------------------------------------------------------------

    /** What a dying worker leaves, set aside as it dies and dropped with its loot (once). */
    private static final Map<LivingEntity, List<ItemStack>> LEAVINGS = new WeakHashMap<>();

    /**
     * A worker dies: what it held is set aside to drop with its loot (the lanyard, the carry, its tool, the pocket, a
     * personal card; the Hard Hat as a guaranteed drop); an enderman's carried block is cleared first and a hand holding a
     * copy of the carry is emptied, so nothing drops twice. It leaves its clock's roster and Head Office's index, and
     * colleagues within 16 lose 5 morale. The Exit Interview and the Condolence Card are {@code StaffEvents.died}'s.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void died(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) return;
        WorkerData d = Workforce.data(mob);
        if (d == null || LEAVINGS.containsKey(mob)) return;
        if (mob instanceof EnderMan enderman) enderman.setCarriedBlock(null);
        List<ItemStack> out = new ArrayList<>();
        out.add(new ItemStack(WorkRegistry.STAFF_LANYARD.get()));
        for (ItemStack s : d.carried) if (!s.isEmpty()) out.add(s);
        d.carried.clear();
        for (ItemStack s : new ItemStack[]{d.tool, d.pocket, d.card}) if (!s.isEmpty()) out.add(s);
        d.tool = ItemStack.EMPTY;
        d.pocket = ItemStack.EMPTY;
        d.card = ItemStack.EMPTY;
        for (EquipmentSlot hand : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
            ItemStack held = mob.getItemBySlot(hand);
            if (!held.isEmpty() && out.stream().anyMatch(s -> ItemStack.isSameItemSameComponents(s, held))) mob.setItemSlot(hand, ItemStack.EMPTY);
        }
        if (HardHatItem.wears(mob)) mob.setGuaranteedDrop(EquipmentSlot.HEAD);
        LEAVINGS.put(mob, out);
        Sites.leave(level, d.clock, mob.getUUID());
        WorkerIndex.get(level.getServer()).remove(d.employer, mob.getUUID());
        // the colleagues feel it
        for (Mob m : level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(GRIEF_RANGE), Workforce::isWorker)) {
            if (m == mob || !d.employer.equals(Workforce.employer(m)) || m.distanceToSqr(mob) > GRIEF_RANGE * GRIEF_RANGE) continue;
            StaffFile f = Staff.of(m);
            if (f == null) continue;
            StaffBlocks.adjust(f, GRIEF);
            m.setData(ModRegistry.STAFF, f);
        }
    }

    /** The dead worker's leavings join its drops (or, when another rule cancels the drops, fall where it died). */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void drops(LivingDropsEvent event) {
        List<ItemStack> out = LEAVINGS.remove(event.getEntity());
        if (out == null) return;
        LivingEntity dead = event.getEntity();
        for (ItemStack s : out) {
            if (event.isCanceled()) {
                dead.spawnAtLocation(s);
            } else {
                ItemEntity item = new ItemEntity(dead.level(), dead.getX(), dead.getY(), dead.getZ(), s);
                item.setDefaultPickUpDelay();
                event.getDrops().add(item);
            }
        }
    }

    // --- sun and rain -----------------------------------------------------------------------------------------------

    /** Whether a worker is sheltered from the sun: a Hard Hat on, or under its own clock's awning. */
    public static boolean sheltered(Mob mob) {
        if (!Workforce.isWorker(mob)) return false;
        return HardHatItem.wears(mob) || underAwning(mob, Workforce.data(mob));
    }

    /** Whether a worker stands within {@link #AWNING} blocks of its own Time Clock (in the same dimension). */
    public static boolean underAwning(Mob mob, WorkerData d) {
        if (d == null || !Workforce.clockHere(mob, d)) return false;
        BlockPos c = d.clock.pos(), p = mob.blockPosition();
        return Math.abs(p.getX() - c.getX()) <= AWNING && Math.abs(p.getZ() - c.getZ()) <= AWNING && Math.abs(p.getY() - c.getY()) <= AWNING;
    }

    // --- conversion -------------------------------------------------------------------------------------------------

    /**
     * A worker that turns into something else alive (a piglin outside the Nether, a drowning zombie, a skeleton in powder
     * snow, lightning, a cure) stays on staff: its record and its file go with it, and its clock keeps its place on the
     * roster. A creature that converts as it dies (a villager killed by a zombie) has left the company first.
     */
    @SubscribeEvent
    public static void converted(LivingConversionEvent.Post event) {
        if (!(event.getEntity() instanceof Mob old) || !(event.getOutcome() instanceof Mob now) || !(old.level() instanceof ServerLevel level)) return;
        WorkerData d = Workforce.data(old);
        if (d == null || old.isDeadOrDying()) return;
        d.dropTask();
        now.setData(WorkRegistry.WORKER, d);
        // a detail member keeps its place on its player's roll under its new UUID (Detail)
        Detail.converted(old, now, d);
        StaffFile f = Staff.of(old);
        if (f != null) {
            f.note("converted", Staff.day(level), "");
            now.setData(ModRegistry.STAFF, f);
        }
        old.removeData(WorkRegistry.WORKER);
        now.setPersistenceRequired();
        Workforce.calm(level, now);
        Workforce.keepRange(now);
        WorkerIndex index = WorkerIndex.get(level.getServer());
        index.remove(d.employer, old.getUUID());
        index.seen(d.employer, now.getUUID(), f == null ? 0 : f.number,
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(now.getType()).toString(), WorkerIndex.at(now));
        var clock = Sites.clock(level, d.clock);
        if (clock != null && clock.has(old.getUUID())) {
            WorkerData.State was = d.state;
            clock.leave(old.getUUID());
            clock.join(now);
            // a detail member stays on the detail (the clock only takes it back onto its roster)
            if (was == WorkerData.State.DETAIL) d.state = was;
        }
        Workforce.sayNow(now, Component.translatable("staff.headinajar.say.change_noted", Workforce.name(now)));
        WorkerLooks.changed(now);
    }
}
