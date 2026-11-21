package com.coolfreeze23.headinajar.staff.work;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Staff fighting with their species' attack (the staff file's {@code attack}): melee; a bow or crossbow with arrows from
 * its carry (taken from the Pick-Up: they were paid for); a blaze's fireballs, which light nothing; a snow golem's
 * snowballs; a phantom's swoop; and the lookout's alarm (a parrot or an armadillo raises it and makes the intruder glow,
 * as a Lookout head does, and does not fight). An axolotl fights only in water. Used by Security on site and by a
 * detail's fighters. Friends are never struck: whoever the company's {@link FriendOrFoe} rule spares, and a shot passes
 * over them ({@link WorkRules#impact}).
 */
public final class Combat {
    /** Ticks between two melee strikes. */
    public static final int STRIKE_TICKS = 20;
    /** Ticks between two shots: a bow, a crossbow, a fireball, a snowball. */
    public static final int BOW_TICKS = 30, CROSSBOW_TICKS = 40, FIREBALL_TICKS = 30, SNOWBALL_TICKS = 20;
    /** A ranged worker shoots between these distances (closer: a melee blow if it has one; further: it closes in). */
    public static final double RANGED_MIN = 4, RANGED_MAX = 15;
    /** Arrows and snowballs leave at the skeleton's and snow golem's speed. */
    public static final float VELOCITY = 1.6f;
    /** A lookout's alarm repeats this often and the intruder glows this long (a Lookout head's numbers). */
    public static final int ALARM_TICKS = 100, GLOW_TICKS = 100;
    /** A blow counts as the employer's only while the employer is this close (plan fix 22, lead ruling). */
    public static final double CREDIT_RANGE = 32;

    private static final Map<Mob, Long> LAST_STRIKE = new WeakHashMap<>();
    private static final Map<Mob, Long> LAST_SHOT = new WeakHashMap<>();

    private Combat() {
    }

    /**
     * One think of fighting {@code target}: close in (one path a think) and strike or shoot when in reach. {@code box}
     * keeps it inside its site (null: anywhere, a detail). Returns false when it cannot fight this target at all (no
     * attack, a friend, an axolotl out of water, out of arrows, a target outside its site).
     */
    public static boolean fight(Mob mob, WorkerData d, LivingEntity target, AABB box) {
        WorkTable.Spec spec = WorkTable.of(mob);
        if (spec == null || target == null || !target.isAlive() || target == mob) return false;
        if (!FriendOrFoe.mayTarget(d.employer, mob.level(), target)) return false;
        if (box != null && !box.contains(target.position())) return false;
        String attack = spec.attack();
        if (attack == null || "none".equals(attack)) return false;
        mob.getLookControl().setLookAt(target, 30, 30);
        return switch (attack) {
            case "melee" -> melee(mob, d, spec, target, box);
            case "bow", "crossbow" -> ranged(mob, d, spec, target, box, "crossbow".equals(attack));
            case "fireball" -> fireball(mob, d, target, box);
            case "snowball" -> snowball(mob, d, target, box);
            case "swoop" -> swoop(mob, spec, target, box);
            case "lookout" -> lookout(mob, target);
            default -> false;
        };
    }

    // --- melee ------------------------------------------------------------------------------------------------------

    private static boolean melee(Mob mob, WorkerData d, WorkTable.Spec spec, LivingEntity target, AABB box) {
        if (mob instanceof net.minecraft.world.entity.animal.axolotl.Axolotl && (!mob.isInWaterOrBubble() || !target.isInWaterOrBubble())) return false;
        if (mob.isWithinMeleeAttackRange(target)) {
            if (ready(LAST_STRIKE, mob, STRIKE_TICKS)) strike(mob, spec, target);
            return true;
        }
        WorkerPaths.go(mob, d, target.blockPosition(), 1.2, box, true);
        return true;
    }

    /** A melee blow: the species' attack attribute, or its staff file's {@code melee} when it has none; credited. */
    static void strike(Mob mob, WorkTable.Spec spec, LivingEntity target) {
        mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        credit(mob, target);
        if (mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            mob.doHurtTarget(target);
        } else if (mob.level() instanceof ServerLevel) {
            target.hurt(mob.damageSources().mobAttack(mob), Math.max(1, spec.melee()));
        }
    }

    // --- bows and crossbows -----------------------------------------------------------------------------------------

    private static boolean ranged(Mob mob, WorkerData d, WorkTable.Spec spec, LivingEntity target, AABB box, boolean crossbow) {
        double dist = mob.distanceTo(target);
        if (dist < RANGED_MIN && mob.isWithinMeleeAttackRange(target)) {
            if (ready(LAST_STRIKE, mob, STRIKE_TICKS)) strike(mob, spec, target);
            return true;
        }
        if (dist > RANGED_MAX || !mob.hasLineOfSight(target)) {
            WorkerPaths.go(mob, d, target.blockPosition(), 1.0, box, true);
            return true;
        }
        int slot = arrowSlot(d);
        if (slot < 0) {
            d.heldUp = true;
            Workforce.say(mob, "no_arrows");
            return false;
        }
        d.heldUp = false;
        if (!(mob.level() instanceof ServerLevel level) || !ready(LAST_SHOT, mob, crossbow ? CROSSBOW_TICKS : BOW_TICKS)) return true;
        shoot(level, mob, d, slot, target, crossbow);
        return true;
    }

    /** One arrow from carry slot {@code slot} at {@code target}, as a skeleton looses one (pickup allowed). */
    static AbstractArrow shoot(ServerLevel level, Mob mob, WorkerData d, int slot, LivingEntity target, boolean crossbow) {
        ItemStack arrows = d.carried.get(slot);
        ItemStack one = arrows.split(1);
        if (arrows.isEmpty()) d.carried.remove(slot);
        WorkerLooks.changed(mob);
        ItemStack held = mob.getMainHandItem();
        ItemStack weapon = held.getItem() instanceof ProjectileWeaponItem ? held : new ItemStack(crossbow ? Items.CROSSBOW : Items.BOW);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(mob, one, 1.0f, weapon);
        arrow.pickup = AbstractArrow.Pickup.ALLOWED;
        double dx = target.getX() - mob.getX(), dy = target.getY(1 / 3.0) - arrow.getY(), dz = target.getZ() - mob.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + flat * 0.2, dz, VELOCITY, 14 - level.getDifficulty().getId() * 4);
        mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), crossbow ? SoundEvents.CROSSBOW_SHOOT : SoundEvents.SKELETON_SHOOT,
                mob.getSoundSource(), 1, 1 / (mob.getRandom().nextFloat() * 0.4f + 0.8f));
        level.addFreshEntity(arrow);
        return arrow;
    }

    /** The carry slot holding arrows, or -1. */
    public static int arrowSlot(WorkerData d) {
        for (int i = 0; i < d.carried.size(); i++) if (d.carried.get(i).getItem() instanceof ArrowItem) return i;
        return -1;
    }

    // --- the blaze, the snow golem, the phantom ---------------------------------------------------------------------

    private static boolean fireball(Mob mob, WorkerData d, LivingEntity target, AABB box) {
        double dist = mob.distanceTo(target);
        if (dist > RANGED_MAX + 1 || !mob.hasLineOfSight(target)) {
            WorkerPaths.go(mob, d, target.blockPosition(), 1.0, box, true);
            return true;
        }
        if (!(mob.level() instanceof ServerLevel level) || !ready(LAST_SHOT, mob, FIREBALL_TICKS)) return true;
        Vec3 aim = new Vec3(target.getX() - mob.getX(), target.getY(0.5) - mob.getY(0.5), target.getZ() - mob.getZ());
        double spread = Math.sqrt(Math.sqrt(dist)) * 0.5;
        Vec3 v = new Vec3(mob.getRandom().triangle(aim.x, 2.297 * spread), aim.y, mob.getRandom().triangle(aim.z, 2.297 * spread));
        SmallFireball ball = new SmallFireball(level, mob, v.normalize());
        ball.setPos(ball.getX(), mob.getY(0.5) + 0.5, ball.getZ());
        level.levelEvent(null, 1018, mob.blockPosition(), 0);
        level.addFreshEntity(ball);
        return true;
    }

    private static boolean snowball(Mob mob, WorkerData d, LivingEntity target, AABB box) {
        double dist = mob.distanceTo(target);
        if (dist > RANGED_MAX || !mob.hasLineOfSight(target)) {
            WorkerPaths.go(mob, d, target.blockPosition(), 1.0, box, true);
            return true;
        }
        if (!(mob.level() instanceof ServerLevel level) || !ready(LAST_SHOT, mob, SNOWBALL_TICKS)) return true;
        Snowball ball = new Snowball(level, mob);
        double dx = target.getX() - mob.getX(), dy = target.getEyeY() - 1.1 - ball.getY(), dz = target.getZ() - mob.getZ();
        ball.setItem(new ItemStack(Items.SNOWBALL));
        ball.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.2, dz, VELOCITY, 12);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SNOW_GOLEM_SHOOT, mob.getSoundSource(), 1,
                0.4f / (mob.getRandom().nextFloat() * 0.4f + 0.8f));
        level.addFreshEntity(ball);
        return true;
    }

    /** A phantom dives at its target and strikes when it touches; it never leaves its site to do it. */
    private static boolean swoop(Mob mob, WorkTable.Spec spec, LivingEntity target, AABB box) {
        if (mob.getBoundingBox().inflate(0.4).intersects(target.getBoundingBox())) {
            if (ready(LAST_STRIKE, mob, STRIKE_TICKS)) {
                strike(mob, spec, target);
                mob.setDeltaMovement(mob.getDeltaMovement().add(0, 0.6, 0));
            }
            return true;
        }
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(mob.position());
        if (to.lengthSqr() < 1e-4) return true;
        Vec3 next = mob.position().add(to.normalize().scale(0.6));
        if (box != null && !box.contains(next)) return false;
        mob.setDeltaMovement(to.normalize().scale(0.45));
        mob.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        return true;
    }

    // --- the lookout ------------------------------------------------------------------------------------------------

    /**
     * A parrot or an armadillo on Security: it watches the intruder and, every 5 s, raises its species' alarm (the
     * Lookout head's sound and ding) and makes the intruder glow for everyone. It never fights.
     */
    private static boolean lookout(Mob mob, LivingEntity target) {
        if (!(mob.level() instanceof ServerLevel level)) return true;
        if (!mob.hasLineOfSight(target)) return true;
        if (!ready(LAST_SHOT, mob, ALARM_TICKS)) return true;
        var sound = com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity.alarmSound(level, mob.getType());
        if (sound != null) level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), sound, SoundSource.NEUTRAL, 1.5f, 1);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), com.coolfreeze23.headinajar.fx.HeadSounds.LOOKOUT_ALARM.get(), SoundSource.NEUTRAL, 1.5f, 1);
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0, false, false));
        return true;
    }

    // --- credit -----------------------------------------------------------------------------------------------------

    /**
     * Who is credited with a worker's blow: its employer while within {@link #CREDIT_RANGE} blocks in the same level
     * (their kill, their XP and loot), else nobody but the worker itself (as an iron golem's kill). Marks the target and
     * returns the employer credited, or null.
     */
    public static ServerPlayer credit(Mob mob, LivingEntity target) {
        if (target == null) return null;
        ServerPlayer p = creditor(mob);
        if (p != null) target.setLastHurtByPlayer(p);
        return p;
    }

    /** Who is credited with a worker's blow (the employer within 32 blocks; else the worker's own: null). */
    public static java.util.UUID creditFor(Mob mob) {
        ServerPlayer p = creditor(mob);
        return p == null ? null : p.getUUID();
    }

    private static ServerPlayer creditor(Mob mob) {
        if (!(mob.level() instanceof ServerLevel level)) return null;
        java.util.UUID employer = Workforce.employer(mob);
        ServerPlayer p = employer == null ? null : level.getServer().getPlayerList().getPlayer(employer);
        if (p == null || p.level() != level || p.isSpectator() || p.distanceToSqr(mob) > CREDIT_RANGE * CREDIT_RANGE) return null;
        return p;
    }

    private static boolean ready(Map<Mob, Long> last, Mob mob, int ticks) {
        long now = mob.level().getGameTime();
        Long at = last.get(mob);
        if (at != null && now - at < ticks) return false;
        last.put(mob, now);
        return true;
    }
}
