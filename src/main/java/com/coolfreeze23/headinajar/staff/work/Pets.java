package com.coolfreeze23.headinajar.staff.work;

import com.coolfreeze23.headinajar.entity.HeadlessBody;
import com.coolfreeze23.headinajar.form.HeadRegistry;
import com.coolfreeze23.headinajar.mind.MobMotor;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The detail's pets: members that keep you company instead of fighting, each with its vanilla gift. A cat (or ocelot)
 * keeps creepers off you; a wolf sits by your idle body while you are a jar and growls at untrusted players; a parrot
 * rides your shoulder; a fox brings you what it finds on the ground (the one creature on staff that picks items up: it
 * takes them itself, not by vanilla's pickup).
 */
public final class Pets {
    /** How near creepers may come to a player with a cat on the detail. */
    public static final double CREEPER_OFF = 8;
    /** A wolf guards your body when it is within this of you, in your dimension. */
    public static final double BODY_RANGE = 32;
    /** A wolf growls at untrusted players this near. */
    public static final double GROWL = 4;
    /** A fox looks this far for things on the ground, and brings them while they are within 16 of you. */
    public static final double FETCH = 8;
    /** A parrot off a shoulder waits this long before it lands again (vanilla's own wait). */
    public static final int SHOULDER_WAIT = 100;

    /** Unsaved: the body a wolf guards, the item a fox is after, when a wolf last growled. */
    private static final class Pet {
        UUID body;
        long bodyLooked = Long.MIN_VALUE / 2;
        UUID item;
        long itemLooked = Long.MIN_VALUE / 2;
        long growled = Long.MIN_VALUE / 2;
        long creepers = Long.MIN_VALUE / 2;
        long lastPath = Long.MIN_VALUE / 2;
    }

    private static final Map<Mob, Pet> PETS = new WeakHashMap<>();

    private Pets() {
    }

    /** The species that are pets on a detail (they never fight). */
    public static boolean isPet(Entity e) {
        return e instanceof Cat || e instanceof Ocelot || e instanceof Wolf || e instanceof Parrot || e instanceof Fox;
    }

    static void forget(Mob mob) {
        PETS.remove(mob);
    }

    static void clear() {
        PETS.clear();
    }

    /** Whether a fox on a detail is fetching (it may hold an item in its mouth: the rules' no-pickup rule excepts it). */
    public static boolean fetching(Mob mob) {
        return mob instanceof Fox && Workforce.data(mob) != null && Workforce.data(mob).state == WorkerData.State.DETAIL;
    }

    /**
     * A pet's gift, each think of a member near its player. True when the gift took the think (the wolf at the body, the
     * fox on an errand, the parrot landing); false lets it follow or hold as usual.
     */
    static boolean think(Mob mob, WorkerData d, Detail.Member m, ServerPlayer p, Detail.Mode mode, double dist) {
        if (!isPet(mob)) return false;
        Pet pet = PETS.computeIfAbsent(mob, k -> new Pet());
        long now = mob.level().getGameTime();
        if (mob instanceof Cat || mob instanceof Ocelot) {
            creepers(mob, p, pet, now);
            return false;
        }
        if (mob instanceof Wolf wolf) return wolf(wolf, d, p, pet, now);
        if (mob instanceof Parrot parrot) return parrot(parrot, m, p, mode, dist, now);
        if (mob instanceof Fox fox) return fox(fox, d, p, mode, pet, now);
        return false;
    }

    /** Creepers near you lose interest and back off, once a second (vanilla's creepers already avoid cats within 6). */
    private static void creepers(Mob cat, ServerPlayer p, Pet pet, long now) {
        if (now - pet.creepers < 20) return;
        pet.creepers = now;
        for (Creeper c : p.serverLevel().getEntitiesOfClass(Creeper.class, p.getBoundingBox().inflate(CREEPER_OFF))) {
            if (c.distanceToSqr(p) > CREEPER_OFF * CREEPER_OFF) continue;
            c.setSwellDir(-1);
            if (c.getTarget() == p) c.setTarget(null);
            Vec3 away = c.position().subtract(p.position()).multiply(1, 0, 1);
            away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
            Vec3 to = p.position().add(away.scale(CREEPER_OFF + 4));
            c.getNavigation().moveTo(to.x, to.y, to.z, 1.2);
        }
    }

    /** A wolf by your idle body while you are a jar (true while it guards); it growls at untrusted players near it. */
    private static boolean wolf(Wolf wolf, WorkerData d, ServerPlayer p, Pet pet, long now) {
        growl(wolf, p, pet, now);
        HeadlessBody body = body(wolf, p, pet, now);
        if (body == null) {
            if (wolf.isInSittingPose()) wolf.setInSittingPose(false);
            return false;
        }
        if (wolf.distanceToSqr(body) > 2.5 * 2.5) {
            if (wolf.isInSittingPose()) wolf.setInSittingPose(false);
            if (now - pet.lastPath >= Detail.PATH_TICKS) {
                pet.lastPath = now;
                WorkerPaths.go(wolf, d, body.blockPosition(), 1.1, null, true);
            }
        } else {
            Detail.hold(wolf);
            wolf.getLookControl().setLookAt(body, 30, 30);
            if (!wolf.isInSittingPose()) wolf.setInSittingPose(true);
        }
        return true;
    }

    /** Your idle body within {@link #BODY_RANGE} while you are a jar, or null (looked for once in 40 ticks). */
    private static HeadlessBody body(Wolf wolf, ServerPlayer p, Pet pet, long now) {
        if (!p.getData(ModRegistry.JAR_STATE).form().isJarred()) {
            pet.body = null;
            return null;
        }
        ServerLevel level = p.serverLevel();
        if (pet.body != null && level.getEntity(pet.body) instanceof HeadlessBody b && b.isAlive() && b.distanceToSqr(p) <= BODY_RANGE * BODY_RANGE) return b;
        pet.body = null;
        if (now - pet.bodyLooked < 40) return null;
        pet.bodyLooked = now;
        HeadlessBody best = null;
        for (HeadlessBody b : level.getEntitiesOfClass(HeadlessBody.class, p.getBoundingBox().inflate(BODY_RANGE),
                b -> p.getUUID().equals(b.ownerId()) && !b.isCorpse())) {
            if (b.distanceToSqr(p) <= BODY_RANGE * BODY_RANGE && (best == null || b.distanceToSqr(p) < best.distanceToSqr(p))) best = b;
        }
        if (best != null) pet.body = best.getUUID();
        return best;
    }

    /** A low growl at an untrusted player within {@link #GROWL}, at most once in 2 seconds. */
    private static void growl(Wolf wolf, ServerPlayer p, Pet pet, long now) {
        if (now - pet.growled < 40) return;
        HeadRegistry trust = HeadRegistry.get(p);
        for (Player other : wolf.level().getEntitiesOfClass(Player.class, wolf.getBoundingBox().inflate(GROWL))) {
            if (other == p || other.isSpectator() || other.isCreative() || trust.trusts(p.getUUID(), other)) continue;
            if (other.distanceToSqr(wolf) > GROWL * GROWL) continue;
            pet.growled = now;
            wolf.getLookControl().setLookAt(other, 30, 30);
            wolf.level().playSound(null, wolf.getX(), wolf.getY(), wolf.getZ(), SoundEvents.WOLF_GROWL, SoundSource.NEUTRAL, 1, 1);
            return;
        }
    }

    /** A parrot following near you, with a free shoulder, lands on it (vanilla stores it on you; the record goes along). */
    private static boolean parrot(Parrot parrot, Detail.Member m, ServerPlayer p, Detail.Mode mode, double dist, long now) {
        // a parrot set down from a shoulder is given its owner by vanilla: staff have none
        if (p.getUUID().equals(parrot.getOwnerUUID()) && !parrot.isTame()) parrot.setOwnerUUID(null);
        if (mode != Detail.Mode.FOLLOW || dist > 3 || now - m.born < SHOULDER_WAIT) return false;
        if (p.getData(ModRegistry.JAR_STATE).form().isDecapitated()) return false;
        if (p.isSpectator() || p.getAbilities().flying || p.isInWater() || p.isInPowderSnow || p.isSleeping() || p.isFallFlying()) return false;
        if (!p.getShoulderEntityLeft().isEmpty() && !p.getShoulderEntityRight().isEmpty()) return false;
        WorkerData d = Workforce.data(parrot);
        if (d != null) d.status = "staff.headinajar.status.shoulder";
        MobMotor.halt(parrot);
        return parrot.setEntityOnShoulder(p);
    }

    /** A fox brings you what it finds on the ground: picked up in its mouth, set down at your feet. */
    private static boolean fox(Fox fox, WorkerData d, ServerPlayer p, Detail.Mode mode, Pet pet, long now) {
        ItemStack mouth = fox.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!mouth.isEmpty()) {
            if (fox.distanceToSqr(p) <= 2.5 * 2.5) {
                ItemEntity dropped = new ItemEntity(fox.level(), p.getX(), p.getY() + 0.2, p.getZ(), mouth.copy());
                dropped.setThrower(fox);
                dropped.setDefaultPickUpDelay();
                fox.level().addFreshEntity(dropped);
                fox.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                fox.level().playSound(null, fox.getX(), fox.getY(), fox.getZ(), SoundEvents.FOX_SPIT, SoundSource.NEUTRAL, 1, 1);
                return false;
            }
            if (mode == Detail.Mode.HOLD) return false;
            go(fox, d, pet, p.blockPosition(), now);
            return true;
        }
        if (mode == Detail.Mode.HOLD) {
            pet.item = null;
            return false;
        }
        ItemEntity item = pet.item == null ? null : fox.level() instanceof ServerLevel l && l.getEntity(pet.item) instanceof ItemEntity ie ? ie : null;
        if (item == null || !item.isAlive() || item.distanceToSqr(p) > 16 * 16) {
            pet.item = null;
            item = look(fox, p, pet, now);
            if (item == null) return false;
        }
        if (fox.distanceToSqr(item) <= 1.6 * 1.6) {
            fox.setItemSlot(EquipmentSlot.MAINHAND, item.getItem().copy());
            item.discard();
            pet.item = null;
            fox.level().playSound(null, fox.getX(), fox.getY(), fox.getZ(), SoundEvents.FOX_EAT, SoundSource.NEUTRAL, 0.6f, 1.4f);
            return true;
        }
        go(fox, d, pet, item.blockPosition(), now);
        return true;
    }

    private static void go(Mob mob, WorkerData d, Pet pet, net.minecraft.core.BlockPos to, long now) {
        if (now - pet.lastPath < Detail.PATH_TICKS) return;
        pet.lastPath = now;
        WorkerPaths.go(mob, d, to, 1.2, null, true);
    }

    /** Something on the ground within {@link #FETCH} of the fox that neither you nor a fox has just put there. */
    private static ItemEntity look(Fox fox, ServerPlayer p, Pet pet, long now) {
        if (now - pet.itemLooked < 20) return null;
        pet.itemLooked = now;
        ItemEntity best = null;
        for (ItemEntity ie : fox.level().getEntitiesOfClass(ItemEntity.class, fox.getBoundingBox().inflate(FETCH))) {
            if (!ie.isAlive() || ie.hasPickUpDelay() || ie.getItem().isEmpty()) continue;
            Entity by = ie.getOwner();
            if (by == p || by instanceof Fox) continue;
            if (best == null || ie.distanceToSqr(fox) < best.distanceToSqr(fox)) best = ie;
        }
        if (best != null) pet.item = best.getUUID();
        return best;
    }
}
