package com.coolfreeze23.headinajar.staff.second;

import com.coolfreeze23.headinajar.block.JarredHeadBlock;
import com.coolfreeze23.headinajar.block.JarredHeadBlockEntity;
import com.coolfreeze23.headinajar.entity.RedundancyPackageEntity;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.JarredHeadItem;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.registry.SecondRegistry;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffBlocks;
import com.coolfreeze23.headinajar.staff.StaffEvents;
import com.coolfreeze23.headinajar.staff.StaffFile;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The Redundancy Package: when a seconded head's body dies (killed, burned, drowned, suffocated, fallen, exploded,
 * rotted away, killed by a command), the collar fires the head up and back under a tiny gold canopy and it lands as a
 * placed Jarred Head, file and grade kept, with one line on its file ("Survived the loss of a Cow body"). Lost only if
 * its glass was already gone when the body died, the body died in the void (or below the world), or the package came
 * down in lava: then the employer has the Exit Interview and HR leaves its Condolence Card. A body that dies in lava
 * fires its package higher and toward the nearest bank it can stand on (one 9 x 9 look at the lava's surface, at the
 * death), so most lava deaths are survived. The employer is told where the body was lost.
 * <p>
 * The jar's glass: a hit that lands on the jar costs it 1 glass per 2 damage, rounded up (the body takes the damage as
 * before). On the jar: explosions, falling blocks and anvils, a projectile at or above the neck when it hits, and a
 * player's melee blow when their look meets the jar's box. Everything else is the body's.
 */
public final class RedundancyPackage {
    /** The launch: up and back off the collar (blocks a tick), and up out of lava. */
    public static final double UP = 0.6, BACK = 0.3, UP_FROM_LAVA = 1.2;
    /** How far the lava escape looks for a bank (blocks each way: a 9 x 9 square). */
    public static final int REACH = 4;
    /** The package's sounds: the parachute's, higher. */
    public static final float PITCH = 1.3f;
    /** Damage per point of glass a hit on the jar costs (rounded up). */
    public static final float DAMAGE_PER_GLASS = 2;
    /** Why a head was lost, as the Exit Interview says it. */
    public static final String LAVA = "staff.headinajar.reason.lava", VOID = "staff.headinajar.reason.void";

    /** The jar's glass before this tick's hits ({tick, glass}): a jar broken by the blow that kills its body was not "already gone". */
    private static final Map<Mob, long[]> GLASS_BEFORE = new WeakHashMap<>();
    /** What tests read: each package fired, landed or lost, newest last. */
    private static final List<String> EVENTS = new ArrayList<>();

    private RedundancyPackage() {
    }

    public static List<String> events() {
        return EVENTS;
    }

    static void register() {
        NeoForge.EVENT_BUS.register(RedundancyPackage.class);
    }

    // --- the body dies ------------------------------------------------------------------------------------------------

    /** First of anything that hears of a death: a seated head leaves before the body's own file is dealt with. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void died(LivingDeathEvent event) {
        if (event.getEntity() instanceof Mob host && !host.level().isClientSide) fire(host, event.getSource());
    }

    /** Rot is about to kill this host: the package fires now (natural wastage). */
    static void wastage(Mob host) {
        fire(host, null);
    }

    /** The body is dying ({@code source} null: it rotted away): the head goes, under its canopy or lost. */
    static void fire(Mob host, DamageSource source) {
        SecondeeData d = Secondees.data(host);
        if (d == null || !d.seated() || !(host.level() instanceof ServerLevel level)) return;
        int glass = glassAtDeath(host, d);
        boolean voided = source != null && source.is(DamageTypes.FELL_OUT_OF_WORLD) || host.getY() < level.getMinBuildHeight();
        Component name = Workforce.name(host);
        UUID employer = d.employer;
        Vec3 jar = jar(host);
        String body = BuiltInRegistries.ENTITY_TYPE.getKey(host.getType()).getPath();
        if (voided || glass <= 0) {
            StaffFile f = Staff.of(host);
            String why = voided ? VOID : f == null ? "staff.headinajar.reason.other"
                    : source == null ? "staff.headinajar.reason.wastage" : StaffEvents.reason(host, source, f);
            ItemStack head = Secondment.takeHead(host, d, null);
            lose(level, voided ? host.position() : jar, head, why, host);
            return;
        }
        ItemStack head = Secondment.takeHead(host, d, "redundancy");
        Vec3 at = clear(level, host, jar);
        float yaw = host.yBodyRot;
        Vec3 v;
        if (host.isInLava()) {
            // out of the lava, toward the nearest bank: the drift's drag brings it to rest over the bank
            BlockPos bank = bank(level, host);
            double k = 1 - RedundancyPackageEntity.DRIFT;
            v = bank == null ? new Vec3(0, UP_FROM_LAVA, 0) : new Vec3((bank.getX() + 0.5 - at.x) * k, UP_FROM_LAVA, (bank.getZ() + 0.5 - at.z) * k);
        } else {
            float r = yaw * Mth.DEG_TO_RAD;
            v = new Vec3(Mth.sin(r) * BACK, UP, -Mth.cos(r) * BACK);
        }
        RedundancyPackageEntity.launch(level, at, v, yaw, head, employer);
        EVENTS.add("fired " + body + " " + (source == null ? "wastage" : source.getMsgId()));
        ServerPlayer boss = employer == null ? null : level.getServer().getPlayerList().getPlayer(employer);
        if (boss != null) {
            BlockPos p = host.blockPosition();
            boss.sendSystemMessage(Component.translatable("chat.headinajar.redundancy", name, p.getX(), p.getY(), p.getZ(), name));
        }
    }

    /** The jar's glass when the body died: as it was before this tick's hits on the jar. */
    static int glassAtDeath(Mob host, SecondeeData d) {
        long[] before = GLASS_BEFORE.get(host);
        return before != null && before[0] == host.level().getGameTime() ? (int) before[1] : d.glass;
    }

    /** Where the jar is: on the collar at the host's neck. */
    public static Vec3 jar(Mob host) {
        return host.position().add(Hosts.neck(host).yRot(-host.yBodyRot * Mth.DEG_TO_RAD));
    }

    /**
     * Where the package starts: at the jar; under a low ceiling, as low as it must go to fit (no lower than the body's
     * feet); with the body buried, the first spot above where it is not inside a block (at most 3 up).
     */
    static Vec3 clear(ServerLevel level, Mob host, Vec3 jar) {
        var size = SecondRegistry.REDUNDANCY_PACKAGE.get().getDimensions();
        if (level.noBlockCollision(null, size.makeBoundingBox(jar))) return jar;
        for (double y = jar.y - 0.25; y >= host.getY(); y -= 0.25) {
            Vec3 at = new Vec3(jar.x, y, jar.z);
            if (level.noBlockCollision(null, size.makeBoundingBox(at))) return at;
        }
        for (int up = 1; up <= 3; up++) {
            Vec3 at = jar.add(0, up, 0);
            if (level.noBlockCollision(null, size.makeBoundingBox(at))) return at;
        }
        return jar;
    }

    /**
     * The nearest cell beside a lava death a jar could stand in: one 9 x 9 look at the lava's surface (the layer over
     * the top of the lava the body is in), each cell clear, not lava, with a sturdy top under it. Null if none.
     */
    static BlockPos bank(ServerLevel level, Mob host) {
        BlockPos in = host.blockPosition();
        int y = in.getY();
        for (int i = 0; i < 4 && level.getFluidState(new BlockPos(in.getX(), y + 1, in.getZ())).is(FluidTags.LAVA); i++) y++;
        int stand = y + 1;
        BlockPos best = null;
        int bestD = Integer.MAX_VALUE;
        for (int dx = -REACH; dx <= REACH; dx++) {
            for (int dz = -REACH; dz <= REACH; dz++) {
                int dd = dx * dx + dz * dz;
                if (dd >= bestD) continue;
                BlockPos c = new BlockPos(in.getX() + dx, stand, in.getZ() + dz);
                if (standable(level, c)) {
                    best = c;
                    bestD = dd;
                }
            }
        }
        return best;
    }

    private static boolean standable(ServerLevel level, BlockPos c) {
        BlockState there = level.getBlockState(c);
        if (!there.getCollisionShape(level, c).isEmpty() || level.getFluidState(c).is(FluidTags.LAVA)) return false;
        BlockPos below = c.below();
        return !level.getFluidState(below).is(FluidTags.LAVA) && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    // --- coming down --------------------------------------------------------------------------------------------------

    /**
     * Places the package's head as a Jarred Head where it came down (or the cell above, when its own is taken), placed
     * as any block is so claims can refuse it. False when it could not be placed (the caller leaves the item).
     */
    public static boolean place(ServerLevel level, RedundancyPackageEntity pkg, ItemStack head) {
        BlockPos pos = pkg.blockPosition();
        if (!free(level, pos)) pos = pos.above();
        if (!free(level, pos)) return false;
        BlockState state = ModRegistry.JARRED_HEAD_BLOCK.get().defaultBlockState().setValue(JarredHeadBlock.FACING, Direction.fromYRot(pkg.getYRot()));
        var snapshot = net.neoforged.neoforge.common.util.BlockSnapshot.create(level.dimension(), level, pos);
        level.setBlockAndUpdate(pos, state);
        if (net.neoforged.neoforge.event.EventHooks.onBlockPlace(pkg, snapshot, Direction.UP)) {
            snapshot.restore(3);
            return false;
        }
        if (level.getBlockEntity(pos) instanceof JarredHeadBlockEntity block) {
            CustomData data = head.get(DataComponents.CUSTOM_DATA);
            if (data != null) block.load(data.copyTag());
            block.firstLook();
            StaffBlocks.show(block, block.staff());
        }
        level.playSound(null, pos, SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 1, 1);
        return true;
    }

    private static boolean free(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).canBeReplaced() && level.getFluidState(pos).isEmpty();
    }

    /** The package came down (placed, or the item left in water or on a refusing cell). */
    public static void landed(RedundancyPackageEntity pkg, boolean water) {
        EVENTS.add((water ? "landed in water " : "landed ") + pkg.blockPosition().toShortString());
    }

    /**
     * The head is lost: its file closed ({@code why}: the reason key), the Exit Interview to its employer, and HR's
     * Condolence Card where it happened. {@code body}: the body it was lost with, or null (the package's own loss).
     */
    public static void lose(ServerLevel level, Vec3 at, ItemStack head, String why, Mob body) {
        EVENTS.add("lost " + why);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 1, 0.9f);
        StaffFile f = Staff.of(head);
        if (f == null || f.left != null || f.employer == null) return;
        long day = Staff.day(level);
        f.left = new StaffFile.Left(day, why);
        f.note("left", day, why);
        // the interview speaks of the head's own kind: a stand-in of its species, never added to the world, where it was lost
        EntityType<?> type = JarredHeadItem.type(head);
        LivingEntity leaver = type != null && type.create(level) instanceof LivingEntity l ? l : body;
        if (leaver == null) return;
        if (leaver != body) leaver.moveTo(at.x, at.y, at.z);
        StaffEvents.exitInterview(level, leaver, f, why, true);
    }

    // --- the jar's glass ----------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void hurt(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Mob host) || host.level().isClientSide) return;
        SecondeeData d = Secondees.data(host);
        if (d != null && d.seated()) jarHit(host, d, event.getSource(), event.getOriginalDamage());
    }

    /** A hit on a seated secondee: what it costs the jar's glass (0 when it is the body's). */
    public static int jarHit(Mob host, SecondeeData d, DamageSource source, float amount) {
        if (amount <= 0 || d.glass <= 0 || !onTheJar(host, source)) return 0;
        int take = Math.min(d.glass, Mth.ceil(amount / DAMAGE_PER_GLASS));
        long now = host.level().getGameTime();
        long[] before = GLASS_BEFORE.get(host);
        if (before == null || before[0] != now) GLASS_BEFORE.put(host, new long[]{now, d.glass});
        d.glass -= take;
        Vec3 at = jar(host);
        host.level().playSound(null, at.x, at.y, at.z, d.glass > 0 ? com.coolfreeze23.headinajar.fx.HouseSounds.JAR_KNOCK.get() : SoundEvents.GLASS_BREAK,
                SoundSource.NEUTRAL, 1, 1.1f);
        SecondeeSync.changed(host);
        return take;
    }

    /** Whether a hit lands on the jar: explosions, falling blocks, a projectile at or above the neck, a melee blow aimed at it. */
    public static boolean onTheJar(Mob host, DamageSource source) {
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return true;
        if (source.is(DamageTypes.FALLING_BLOCK) || source.is(DamageTypes.FALLING_ANVIL) || source.is(DamageTypes.FALLING_STALACTITE)) return true;
        Entity direct = source.getDirectEntity();
        if (direct == null) return false;
        if (source.is(DamageTypeTags.IS_PROJECTILE)) return direct.getY() >= host.getY() + Hosts.neck(host).y;
        if (direct instanceof Player p && source.getEntity() == p) {
            Vec3 eye = p.getEyePosition();
            AABB box = jarBox(host);
            return box.contains(eye) || box.clip(eye, eye.add(p.getViewVector(1).scale(p.entityInteractionRange() + 1))).isPresent();
        }
        return false;
    }

    /** The jar on the collar: 0.8 wide and 0.9 tall over the neck point. */
    public static AABB jarBox(Mob host) {
        Vec3 n = jar(host);
        return new AABB(n.x - 0.4, n.y, n.z - 0.4, n.x + 0.4, n.y + 0.9, n.z + 0.4);
    }
}
