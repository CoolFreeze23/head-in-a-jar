package com.coolfreeze23.headinajar.staff.second;

import com.coolfreeze23.headinajar.host.HostAbilities;
import com.coolfreeze23.headinajar.host.HostData;
import com.coolfreeze23.headinajar.host.HostDefinition;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.staff.work.FriendOrFoe;
import com.coolfreeze23.headinajar.staff.work.Movers;
import com.coolfreeze23.headinajar.staff.work.WorkTable;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.WorkerLooks;
import com.coolfreeze23.headinajar.staff.work.WorkerPaths;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The body's tricks at work: which of a seconded host's Ability (from 50% team sync) and Secondary (from 75%) the job
 * brain uses, when, and how ({@link HostAbilities#fireFor}, on the host file's own cooldowns).
 * <ul>
 * <li><b>At a foe</b> ({@link #AT_FOES}), in a fight on Security or on a detail ({@code Combat.fight}): arrows, fireballs
 * (owned by the host: the staff griefing rule keeps every block, and a fireball's blast spares whoever the company
 * spares), a web in the foe's cell if it is air, charge and pounce, toss, roar, ink, sonic boom, a witch's potion (only
 * with no friend within 4 of the foe), a llama's spit, a cat's hiss (at creepers), a horse's rear, the villager's bell
 * (raiders show); every area effect on foes only. Echolocation lights foes up on patrol too ({@link #patrol}).</li>
 * <li><b>Between stops</b> ({@code Movers.go}): an enderman body hops to a stop more than 4 away (Teleport, then its
 * Secondary while the first cools), inside the box, to a standable cell the movers choose; it walks otherwise. A horse,
 * donkey, mule or ocelot body sprints to a stop more than 12 away. A blaze body walks, and hovers (its Secondary) to a
 * stop above it, flying as a blaze does while it is up. A bat body (a kind never hired, so its row walks) flies as a
 * ghast does, held in the box.</li>
 * <li><b>Passing</b>: a chicken body on the job lays its egg into its carry (else at its feet).</li>
 * <li><b>Never</b> ({@link #NEVER}): explode ("Staff do not explode. Policy 9.2.", said once a foe), groan (it would set
 * wild zombies on the site), wind charge (it works doors, levers and bells).</li>
 * </ul>
 * The 0.6.3 tricks the brain leaves alone: herd, rooting, shed, barter, warm up, withdraw, leap, dash, mend, howl and
 * treasure (each wants a pilot: a lure to follow, ores to show, pockets to spend, a look to aim by). At 100% team sync
 * the body works 10% faster and carries 10% more ({@link #atFull}).
 * <p>
 * Cost: nothing here scans; a fight's checks are field reads and one distance, a foe search only for an echolocation
 * that is ready, and the per-host state lives in weak maps.
 */
public final class BodyWork {
    /** Where the Ability and the Secondary open. */
    public static final int ABILITY = TeamSync.ABILITY, SECONDARY = TeamSync.SECONDARY;
    /** Never used by the brain. */
    public static final Set<String> NEVER = Set.of("explode", "groan", "wind_charge");
    /** Used on a foe in a fight. */
    public static final Set<String> AT_FOES = Set.of("arrows", "fireball", "web", "charge", "pounce", "toss", "roar", "ink",
            "echolocation", "sonic_boom", "potion", "spit", "hiss", "rear", "bell");
    /** Of those, the ones that need the foe in sight (the rest reach round corners: a roar, a cloud of ink, a bell). */
    private static final Set<String> SIGHT = Set.of("arrows", "fireball", "web", "charge", "pounce", "toss", "sonic_boom", "potion", "spit");
    /** Of those, the ones that make a body a fighter on a detail (the others only help: a glow, a hiss, a bell). */
    private static final Set<String> FIGHTING = Set.of("arrows", "fireball", "web", "charge", "pounce", "toss", "roar", "ink",
            "sonic_boom", "potion", "spit", "rear");
    /** The floating line for a creeper body asked to do what it would blow up for. */
    public static final String NO_EXPLODE = "secondment.no_explode";
    /** An enderman body hops to a stop farther than this; a sprinting body sprints to one farther than this. */
    public static final double HOP_FROM = 4, SPRINT_FROM = 12;
    /** A stop this far above a blaze body's feet is a high one: it hovers up. */
    public static final double HIGH = 1.5;
    /** At 100% team sync: the walk and the carry times this. */
    public static final float FULL_BONUS = 1.1f;

    private static final boolean[] BOTH = {false, true};

    /** How a body is getting to its current stop with a trick (none: it walks, or moves as its kind does). */
    enum Way {HOP, HOVER}

    private static final Map<Mob, Way> WAY = new WeakHashMap<>();
    /** The foe a creeper body last said its line about (said once a foe). */
    private static final Map<Mob, UUID> TOLD = new WeakHashMap<>();
    /** A secondee with an attacking head and no arrows has clicked once (a turret's dry click), until it shoots again. */
    private static final Map<Mob, Boolean> DRY = new WeakHashMap<>();
    /** Each 100% row, made once from its merged row (the merged rows are cached, so these are too). */
    private static final Map<WorkTable.Spec, WorkTable.Spec> FULL = new IdentityHashMap<>();
    /** What the brain has fired, by type (tests read it). */
    private static final Map<String, Integer> FIRED = new HashMap<>();

    private BodyWork() {
    }

    /** The game-bus handlers: a staff fireball's blast spares friends; a staff spit or potion passes over them. */
    static void register() {
        NeoForge.EVENT_BUS.addListener(BodyWork::blast);
        NeoForge.EVENT_BUS.addListener(BodyWork::passOver);
    }

    // --- what is open --------------------------------------------------------------------------------------------------

    /** The body's Ability ({@code secondary} false, from 50%) or Secondary (from 75%), if it has one and it is open. */
    public static HostDefinition.Ability open(Mob mob, boolean secondary) {
        if (!Secondees.seated(mob)) return null;
        HostDefinition def = Hosts.definition(mob);
        if (def == null) return null;
        var a = secondary ? def.secondary() : def.primary();
        if (a.isEmpty() || Secondees.teamSync(mob) < (secondary ? SECONDARY : ABILITY)) return null;
        return a.get();
    }

    private static boolean ready(Mob mob, boolean secondary) {
        HostData d = Hosts.data(mob);
        return (secondary ? d.secondaryCooldown : d.primaryCooldown) <= 0;
    }

    /** Fired: its cooldown starts (the host file's), and it is counted. */
    private static void used(Mob mob, boolean secondary, HostDefinition.Ability a) {
        HostData d = Hosts.data(mob);
        if (secondary) d.secondaryCooldown = a.cooldown();
        else d.primaryCooldown = a.cooldown();
        synchronized (FIRED) {
            FIRED.merge(a.type(), 1, Integer::sum);
        }
    }

    /** How many times the brain has used an ability of this type since the server started (tests). */
    public static int fired(String type) {
        synchronized (FIRED) {
            return FIRED.getOrDefault(type, 0);
        }
    }

    // --- in a fight -----------------------------------------------------------------------------------------------------

    /**
     * One think of a seconded body's tricks against {@code target} (a foe the company's rule allows, inside the box):
     * each open trick that suits it and is off its cooldown, in reach and (where it needs it) in sight, is used. Returns
     * the farthest reach of the tricks it has for this foe (0: none; the head's attack is all it has), so a head with no
     * attack of its own closes in to use them ({@link #closeIn}).
     */
    public static double fight(Mob mob, WorkerData d, LivingEntity target, AABB box) {
        if (!Secondees.seated(mob) || target == null) return 0;
        double reach = 0;
        for (boolean secondary : BOTH) {
            HostDefinition.Ability a = open(mob, secondary);
            if (a == null) continue;
            String type = a.type();
            if (NEVER.contains(type)) {
                // Policy 9.2: a creeper body says so, once a foe; a groan or a wind charge is simply never used
                if ("explode".equals(type) && !target.getUUID().equals(TOLD.put(mob, target.getUUID()))) Workforce.say(mob, NO_EXPLODE);
                continue;
            }
            if (!AT_FOES.contains(type) || !suits(type, target)) continue;
            double r = reach(a);
            reach = Math.max(reach, r);
            if (!ready(mob, secondary) || gap(mob, target) > r) continue;
            if (SIGHT.contains(type) && !mob.hasLineOfSight(target)) continue;
            mob.getLookControl().setLookAt(target, 30, 30);
            if (HostAbilities.fireFor(mob, target, a, d.employer) == null) used(mob, secondary, a);
        }
        return reach;
    }

    /** A head with no attack of its own, on a body with tricks for this foe: it closes in to use them. Always true. */
    public static boolean closeIn(Mob mob, WorkerData d, LivingEntity target, AABB box, double reach) {
        mob.getLookControl().setLookAt(target, 30, 30);
        if (gap(mob, target) > reach * 0.8 || !mob.hasLineOfSight(target)) WorkerPaths.go(mob, d, target.blockPosition(), 1.2, box, true);
        return true;
    }

    /** A hiss is for creepers, a bell for raiders; the rest for any foe. */
    private static boolean suits(String type, LivingEntity target) {
        return switch (type) {
            case "hiss" -> target instanceof Creeper;
            case "bell" -> target.getType().is(net.minecraft.tags.EntityTypeTags.RAIDERS);
            default -> true;
        };
    }

    /** How far from the foe (box to box) a trick reaches. */
    static double reach(HostDefinition.Ability a) {
        return switch (a.type()) {
            case "arrows", "sonic_boom" -> a.param("range", 15);
            case "fireball", "web", "echolocation" -> a.param("range", 16);
            case "potion" -> 10;
            case "spit" -> a.param("range", 12);
            case "charge", "pounce" -> 3;
            case "toss" -> a.param("range", 4);
            case "roar", "ink" -> a.param("radius", 6);
            case "hiss" -> a.param("radius", 8);
            case "rear" -> a.param("radius", 3);
            case "bell" -> 16;
            default -> 0;
        };
    }

    /** The gap between two creatures' boxes, roughly (centre distance less half of each width). */
    private static double gap(Entity a, Entity b) {
        return Math.max(0, a.distanceTo(b) - a.getBbWidth() / 2 - b.getBbWidth() / 2);
    }

    /** On patrol with nothing in sight: a bat body's Echolocation, when ready and a foe is inside the box within 16. */
    public static void patrol(Mob mob, WorkerData d, AABB box) {
        if (!Secondees.seated(mob) || !(mob.level() instanceof ServerLevel level)) return;
        for (boolean secondary : BOTH) {
            HostDefinition.Ability a = open(mob, secondary);
            if (a == null || !"echolocation".equals(a.type()) || !ready(mob, secondary)) continue;
            double r = a.param("radius", 16);
            AABB look = mob.getBoundingBox().inflate(r).intersect(box);
            List<LivingEntity> foes = level.getEntitiesOfClass(LivingEntity.class, look,
                    e -> e != mob && box.contains(e.position()) && FriendOrFoe.mayTarget(d.employer, level, e));
            if (foes.isEmpty()) continue;
            if (HostAbilities.fireFor(mob, foes.get(0), a, d.employer) == null) used(mob, secondary, a);
        }
    }

    /** Whether a secondee is a fighter on a detail: an attacking head (as any worker), or a body whose open trick fights. */
    public static boolean fighter(Mob mob) {
        WorkTable.Spec spec = WorkTable.of(mob);
        if (spec != null && spec.can(WorkTable.SECURITY) && !"none".equals(spec.attack()) && !"lookout".equals(spec.attack())) return true;
        for (boolean secondary : BOTH) {
            HostDefinition.Ability a = open(mob, secondary);
            if (a != null && FIGHTING.contains(a.type())) return true;
        }
        return false;
    }

    // --- the head's shots, from the jar ---------------------------------------------------------------------------------

    /** A shot of a seconded head's own attack (its turret post) leaves from the jar on the neck, not the body's eyes. */
    public static void fromJar(Mob mob, Projectile shot) {
        if (!Secondees.seated(mob)) return;
        DRY.remove(mob);
        Vec3 jar = jar(mob);
        shot.setPos(jar.x, jar.y, jar.z);
    }

    /** Where a seated jar's middle is (its shots leave from here). */
    public static Vec3 jar(Mob mob) {
        return com.coolfreeze23.headinajar.body.BodyServer.neck(mob).add(0, HostAbilities.JAR_MID, 0);
    }

    /** A seconded bow head with a foe and no arrows: one dry click from the jar (a turret's), then quiet until it shoots. */
    public static void dry(Mob mob) {
        if (!Secondees.seated(mob) || DRY.put(mob, true) != null || !(mob.level() instanceof ServerLevel level)) return;
        Vec3 jar = jar(mob);
        level.playSound(null, jar.x, jar.y, jar.z, com.coolfreeze23.headinajar.fx.HouseSounds.TURRET_DRY_CLICK.get(), SoundSource.NEUTRAL, 1, 1.2f);
    }

    /** Whether it has clicked dry and not shot since (tests). */
    public static boolean clickedDry(Mob mob) {
        return DRY.containsKey(mob);
    }

    // --- between stops --------------------------------------------------------------------------------------------------

    /**
     * Setting off for a stop ({@code Movers.go}): whether the body hops (an enderman's Teleport, then its Secondary while
     * the first cools), sprints, or hovers there. Decided once a route; the movers read it through {@link #family}.
     */
    public static void setOff(Mob mob, BlockPos to) {
        if (!Secondees.seated(mob)) return;
        WAY.remove(mob);
        double far = Math.sqrt(mob.distanceToSqr(Vec3.atCenterOf(to)));
        boolean high = to.getY() > mob.getY() + HIGH;
        for (boolean secondary : BOTH) {
            HostDefinition.Ability a = open(mob, secondary);
            if (a == null) continue;
            switch (a.type()) {
                case "teleport" -> {
                    if (WAY.containsKey(mob) || far <= HOP_FROM || !ready(mob, secondary)) continue;
                    WAY.put(mob, Way.HOP);
                    used(mob, secondary, a);
                }
                case "sprint" -> {
                    if (far <= SPRINT_FROM || !ready(mob, secondary)) continue;
                    if (HostAbilities.fireFor(mob, null, a, Workforce.employer(mob)) == null) used(mob, secondary, a);
                }
                case "hover" -> {
                    // up to a high stop; once up, it flies as a blaze does until it lands (no cooldown asked in the air)
                    boolean aloft = !mob.onGround() && !mob.isInLiquid();
                    if (!high && !aloft) continue;
                    if (!aloft && !ready(mob, secondary)) continue;
                    WAY.put(mob, Way.HOVER);
                    if (!aloft) used(mob, secondary, a);
                }
                default -> {
                }
            }
        }
    }

    /**
     * The movement family a seconded body moves by ({@code Movers.family}): its kind's, except that an enderman body
     * walks unless it is hopping to this stop, and a body whose flight is a trick (the blaze's Hover) walks unless it is
     * hovering. Ghasts, phantoms and every other kind move as their row says.
     */
    public static String family(Mob mob, String move) {
        boolean hop = Movers.TELEPORT.equals(move), fly = Movers.FLY_FREE.equals(move);
        // a body that flies by nature but whose kind is never hired (a bat: "no legs" for a lanyard, so its row walks)
        // flies as a ghast does, held in the box
        if (Movers.WALK.equals(move) && Secondees.seated(mob) && Hosts.definition(mob) != null && Hosts.definition(mob).has("flight")) {
            return Movers.FLY_FREE;
        }
        if (!hop && !fly || !Secondees.seated(mob)) return move;
        Way w = WAY.get(mob);
        if (hop) return w == Way.HOP ? move : Movers.WALK;
        if (hovers(mob)) return w == Way.HOVER ? move : Movers.WALK;
        return move;
    }

    /** Whether the body's flight is one of its tricks (a Hover) rather than its kind's way of getting about. */
    static boolean hovers(Mob mob) {
        HostDefinition def = Hosts.definition(mob);
        if (def == null) return false;
        return def.primary().map(a -> a.type().equals("hover")).orElse(false) || def.secondary().map(a -> a.type().equals("hover")).orElse(false);
    }

    // --- in passing -----------------------------------------------------------------------------------------------------

    /** Each tick of a seated host ({@link TeamSync}): once a second, a chicken body on the job lays into its carry. */
    static void tick(Mob host, SecondeeData sd, long time) {
        if ((time + host.getId()) % 20 != 0 || sd.canister <= 0) return;
        WorkerData w = Workforce.data(host);
        if (w == null || w.state != WorkerData.State.ON_JOB) return;
        for (boolean secondary : BOTH) {
            HostDefinition.Ability a = open(host, secondary);
            if (a == null || !"lay_egg".equals(a.type()) || !ready(host, secondary)) continue;
            WorkTable.Spec spec = WorkTable.of(host);
            if (spec == null || !intoTheCarry(w, spec, new ItemStack(Items.EGG))) {
                HostAbilities.fireFor(host, null, a, w.employer);
            } else {
                host.playSound(SoundEvents.CHICKEN_EGG, 1.0f, 1.0f);
                WorkerLooks.changed(host);
            }
            used(host, secondary, a);
        }
    }

    /** One item into what a worker carries: merged, or a new stack (six at most), within its carry limit in items. */
    static boolean intoTheCarry(WorkerData d, WorkTable.Spec spec, ItemStack one) {
        int items = 0;
        for (ItemStack s : d.carried) items += s.getCount();
        if (items + one.getCount() > spec.carryLimit(one.getMaxStackSize())) return false;
        for (ItemStack s : d.carried) {
            if (ItemStack.isSameItemSameComponents(s, one) && s.getCount() + one.getCount() <= s.getMaxStackSize()) {
                s.grow(one.getCount());
                return true;
            }
        }
        d.carried.removeIf(ItemStack::isEmpty);
        if (d.carried.size() >= WorkerData.MAX_CARRIED) return false;
        d.carried.add(one);
        return true;
    }

    // --- 100% ------------------------------------------------------------------------------------------------------------

    /**
     * The work row a seconded host works by ({@code WorkTable.of}): its merged row, and at 100% team sync the same row
     * 10% faster (its walk) and carrying 10% more: a body with an item cap carries a tenth more items (16 to 17); one
     * that carries by the stack carries a stack more, capped at a tenth more items (1 stack: 70 of a 64-stack item), to
     * the six stacks a worker can hold.
     */
    public static WorkTable.Spec atFull(Mob mob, WorkTable.Spec merged) {
        if (merged == null || Secondees.teamSync(mob) < TeamSync.FULL) return merged;
        synchronized (FULL) {
            if (FULL.size() > 256) FULL.clear();
            return FULL.computeIfAbsent(merged, BodyWork::bonus);
        }
    }

    static WorkTable.Spec bonus(WorkTable.Spec s) {
        int carry = s.carry(), items = s.carryItems();
        if (items > 0) {
            items = (int) Math.floor(items * FULL_BONUS);
        } else if (carry > 0) {
            items = (int) Math.floor(carry * 64 * FULL_BONUS);
            carry = Math.min(WorkerData.MAX_CARRIED, carry + 1);
        }
        return new WorkTable.Spec(s.hire(), s.jobs(), s.cannot(), s.shift(), carry, items, s.hands(), s.walk() * FULL_BONUS, s.move(),
                s.attack(), s.melee(), s.quirks(), s.card(), s.water(), s.professions());
    }

    // --- sparing friends -----------------------------------------------------------------------------------------------

    /** A staff fireball's blast (a ghast body's): it harms only foes, and no block, item or frame. */
    private static void blast(ExplosionEvent.Detonate event) {
        Entity source = event.getExplosion().getDirectSourceEntity();
        if (!(source instanceof Fireball ball) || !(ball.getOwner() instanceof Mob owner) || !Workforce.isWorker(owner)) return;
        UUID employer = Workforce.employer(owner);
        event.getAffectedBlocks().clear();
        event.getAffectedEntities().removeIf(e -> e == owner || !(e instanceof LivingEntity l) || !FriendOrFoe.mayTarget(employer, owner.level(), l));
    }

    /** A staff spit or splash potion passes over anyone the company spares (as its arrows and fireballs do). */
    private static void passOver(ProjectileImpactEvent event) {
        Projectile p = event.getProjectile();
        if (!(p instanceof LlamaSpit || p instanceof ThrownPotion)) return;
        if (!(p.getOwner() instanceof Mob owner) || !Workforce.isWorker(owner)) return;
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit) || !(hit.getEntity() instanceof LivingEntity target)) return;
        if (target == owner || !FriendOrFoe.mayTarget(Workforce.employer(owner), owner.level(), target)) {
            event.setCanceled(true);
            return;
        }
        com.coolfreeze23.headinajar.staff.work.Combat.credit(owner, target);
    }
}
