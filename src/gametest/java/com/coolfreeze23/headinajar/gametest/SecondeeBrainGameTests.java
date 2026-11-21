package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.TimeClockBlockEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.fx.Fx;
import com.coolfreeze23.headinajar.host.HostAbilities;
import com.coolfreeze23.headinajar.host.HostData;
import com.coolfreeze23.headinajar.host.HostDefinition;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.JarredHeadItem;
import com.coolfreeze23.headinajar.item.JobCardItem;
import com.coolfreeze23.headinajar.item.SurgicalSawItem;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.registry.SecondRegistry;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.second.BodyWork;
import com.coolfreeze23.headinajar.staff.second.SecondeeData;
import com.coolfreeze23.headinajar.staff.second.SecondeeEvents;
import com.coolfreeze23.headinajar.staff.second.Secondees;
import com.coolfreeze23.headinajar.staff.second.Secondment;
import com.coolfreeze23.headinajar.staff.second.TeamSync;
import com.coolfreeze23.headinajar.staff.work.Combat;
import com.coolfreeze23.headinajar.staff.work.Detail;
import com.coolfreeze23.headinajar.staff.work.Movers;
import com.coolfreeze23.headinajar.staff.work.Order;
import com.coolfreeze23.headinajar.staff.work.WorkTable;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.WorkerPaths;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.windcharge.WindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 0.7.1, the secondee's brain: team sync (on the job with a card only; half for blood that does not mix; an oil body at
 * full rate; the quarter pulses and Team Spirit), the body's traits, Ability and Secondary opening at 25, 50 and 75 and
 * the 100% bonus, the Neural Lace at 50% team sync, the body's tricks at work through {@code HostAbilities.fireFor}
 * (never a block broken, friends spared, never explode, groan or wind charge), the head's own turret post from the
 * jar, a secondee keeping to its site, and the Whistle's fighters and pets. Collars and heads go on through the real
 * presses ({@code Secondment.instrument}); hosts are made with the saw.
 * <p>
 * {@code platform32}: 32 x 10 x 32, the floor's top at helper y 5 (mobs stand at y 6). {@code site34}: 34 x 18 x 34, the
 * grass top at helper y 4 (mobs stand at y 5).
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class SecondeeBrainGameTests {
    private static final BlockPos AT = new BlockPos(16, 6, 16);

    // --- set-up -------------------------------------------------------------------------------------------------------

    private static ServerPlayer boss(GameTestHelper h, BlockPos at) {
        return TestPlayers.at(h, "Boss", at);
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    /** A host made by the saw (worn down first; a golem made the player's own). */
    private static <T extends Mob> T host(GameTestHelper h, ServerPlayer p, EntityType<T> type, BlockPos at) {
        T mob = h.spawn(type, at);
        if (mob instanceof IronGolem golem) golem.setPlayerCreated(true);
        mob.setHealth(1);
        p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        SurgicalSawItem.saw(p, new ItemStack(ModRegistry.SURGICAL_SAW.get()), mob, InteractionHand.MAIN_HAND);
        h.assertTrue(Hosts.isHost(mob), "setup: the saw made no " + type.getDescription().getString() + " host: " + Refusals.lastDelivered(p));
        return mob;
    }

    /** A jarred head of this species (a creature made for it, never put in the world). */
    private static ItemStack head(GameTestHelper h, EntityType<? extends Mob> type) {
        Mob m = type.create(h.getLevel());
        h.assertTrue(m != null, "setup: no " + type);
        return JarredHeadItem.of(m);
    }

    /** The collar and then the head, each pressed on with the player's hand; the canister filled. */
    private static SecondeeData second(GameTestHelper h, ServerPlayer p, Mob host, EntityType<? extends Mob> headType) {
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SecondRegistry.SECONDMENT_COLLAR.get()));
        Secondment.instrument(p, p, p.getMainHandItem(), host, InteractionHand.MAIN_HAND);
        p.setItemInHand(InteractionHand.MAIN_HAND, head(h, headType));
        Secondment.instrument(p, p, p.getMainHandItem(), host, InteractionHand.MAIN_HAND);
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        h.assertTrue(Secondees.seated(host), "setup: the " + headType.getDescription().getString() + " head did not seat on the "
                + host.getType().getDescription().getString() + ": " + Refusals.lastDelivered(p));
        SecondeeData d = Secondees.data(host);
        d.canister = SecondeeData.CANISTER;
        return d;
    }

    private static <T extends Mob> T secondee(GameTestHelper h, ServerPlayer p, EntityType<T> body, EntityType<? extends Mob> head, BlockPos at) {
        T host = host(h, p, body, at);
        second(h, p, host, head);
        return host;
    }

    /** A Job Card for a site (or none). */
    private static ItemStack card(Optional<net.minecraft.core.GlobalPos> site, String job, List<Order.Stop> stops) {
        ItemStack card = new ItemStack(WorkRegistry.JOB_CARD.get());
        card.set(WorkRegistry.JOB_CARD_DATA.get(), new Order.Card(new Order(site, job, stops, List.of(), "test"), "test", 0));
        return card;
    }

    /** Puts a secondee on the job with a card in its hand (no site needed for what these read). */
    private static WorkerData onTheJob(Mob host, boolean withCard) {
        WorkerData w = Workforce.data(host);
        w.state = WorkerData.State.ON_JOB;
        w.card = withCard ? card(Optional.empty(), WorkTable.HAULING, List.of()) : ItemStack.EMPTY;
        w.rackSlot = -1;
        return w;
    }

    /** {@code ticks} of the collar's own tick, at once (the brain is not given a turn between them). */
    private static void work(GameTestHelper h, Mob host, int ticks) {
        HostData data = Hosts.data(host);
        long t = h.getLevel().getGameTime();
        for (int i = 0; i < ticks; i++) SecondeeEvents.tick(host, data, t + i);
    }

    private static boolean earned(GameTestHelper h, ServerPlayer p, String id) {
        AdvancementHolder holder = h.getLevel().getServer().getAdvancements().get(HeadInAJar.id(id));
        h.assertTrue(holder != null, "no advancement " + id);
        return p.getAdvancements().getOrStartProgress(holder).isDone();
    }

    private static HostDefinition.Ability ability(Mob host, boolean secondary) {
        HostDefinition def = Hosts.definition(host);
        var a = secondary ? def.secondary() : def.primary();
        if (a.isEmpty()) throw new net.minecraft.gametest.framework.GameTestAssertException("setup: the " + host.getType() + " has no " + (secondary ? "Secondary" : "Ability"));
        return a.get();
    }

    /** A foe that holds still and cannot be worn down by what is fired at it. */
    private static <T extends Mob> T dummy(GameTestHelper h, EntityType<T> type, BlockPos at, boolean tough) {
        T foe = h.spawn(type, at);
        foe.setNoAi(true);
        foe.setPersistenceRequired();
        foe.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, false, false));
        if (tough) {
            // nothing fired at it wears it down: resistance for blows, and health for what goes through (a sonic boom)
            foe.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, -1, 4, false, false));
            var max = foe.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (max != null) max.setBaseValue(200);
            foe.setHealth(foe.getMaxHealth());
        }
        return foe;
    }

    // --- 5.1 #6: team sync grows on the job with a card only ---------------------------------------------------------

    @GameTest(template = "platform32", timeoutTicks = 100)
    public static void teamSyncGrowsOnShiftOnly(GameTestHelper h) {
        ServerPlayer p = boss(h, new BlockPos(2, 6, 2));
        // warm and warm mix; grave and warm do not; an iron golem is oil (equipment) and takes an ender head at full rate
        Mob full = secondee(h, p, EntityType.PIG, EntityType.COW, new BlockPos(6, 6, 6));
        Mob mixed = secondee(h, p, EntityType.COW, EntityType.ZOMBIE, new BlockPos(12, 6, 6));
        Mob oil = secondee(h, p, EntityType.IRON_GOLEM, EntityType.ENDERMAN, new BlockPos(18, 6, 6));
        Mob noCard = secondee(h, p, EntityType.PIG, EntityType.COW, new BlockPos(6, 6, 14));
        Mob offShift = secondee(h, p, EntityType.PIG, EntityType.COW, new BlockPos(12, 6, 14));
        Mob dry = secondee(h, p, EntityType.PIG, EntityType.COW, new BlockPos(18, 6, 14));
        for (Mob m : new Mob[]{full, mixed, oil, dry}) onTheJob(m, true);
        onTheJob(noCard, false);
        onTheJob(offShift, true).state = WorkerData.State.OFF_SHIFT;
        Secondees.data(dry).canister = 0;
        for (Mob m : new Mob[]{full, mixed, oil, noCard, offShift, dry}) work(h, m, 4800);
        float[] want = {10, 5, 10, 0, 0, 0};
        String[] name = {"cow head on a pig, a card", "zombie head on a cow (mixed blood)", "ender head on an iron golem (oil)", "no card",
                "off shift", "dry"};
        Mob[] mobs = {full, mixed, oil, noCard, offShift, dry};
        for (int i = 0; i < mobs.length; i++) {
            float got = Secondees.teamSync(mobs[i]);
            h.assertTrue(Math.abs(got - want[i]) < 0.05f, name[i] + ": team sync " + got + " after 4,800 ticks, want " + want[i]);
        }
        done(h, p);
    }

    @GameTest(template = "platform32", timeoutTicks = 100)
    public static void eachQuarterPulsesAndFullEarnsTeamSpirit(GameTestHelper h) {
        ServerPlayer p = boss(h, new BlockPos(14, 6, 14));
        Mob host = secondee(h, p, EntityType.PIG, EntityType.COW, AT);
        SecondeeData d = Secondees.data(host);
        onTheJob(host, true);
        int before = Fx.count(Fx.TEAM_SYNC_PULSE, host.getId());
        d.teamSync = 24.999f;
        work(h, host, 2);
        h.assertTrue(Fx.count(Fx.TEAM_SYNC_PULSE, host.getId()) == before + 1, "no pulse at 25%");
        work(h, host, 2);
        h.assertTrue(Fx.count(Fx.TEAM_SYNC_PULSE, host.getId()) == before + 1, "a second pulse inside the same quarter");
        h.assertFalse(earned(h, p, "team_spirit"), "Team Spirit before 100%");
        d.teamSync = 99.999f;
        work(h, host, 2);
        h.assertTrue(d.teamSync == 100, "team sync " + d.teamSync + ", not 100");
        h.assertTrue(Fx.count(Fx.TEAM_SYNC_PULSE, host.getId()) == before + 2, "no pulse at 100%");
        h.assertTrue(earned(h, p, "team_spirit"), "no Team Spirit at 100%");
        h.assertTrue((d.flags & TeamSync.FLAG_SPIRIT_DUE) == 0, "Team Spirit still owed to an employer who was here");
        done(h, p);
    }

    // --- 5.1 #7: traits at 25, Ability at 50, Secondary at 75, the 100% bonus -----------------------------------------

    @GameTest(template = "platform32", timeoutTicks = 100)
    public static void traitsOpenAt25(GameTestHelper h) {
        ServerPlayer p = boss(h, new BlockPos(2, 6, 2));
        // a squid body breathes out of water (water breathing) only from 25% (a blaze body's fire immunity is the same
        // trait code, but a blaze is fire-proof by nature, so it shows nothing)
        Mob squid = secondee(h, p, EntityType.SQUID, EntityType.ZOMBIE, new BlockPos(8, 6, 8));
        // a goat body takes no fall damage only from 25%
        Mob goat = secondee(h, p, EntityType.GOAT, EntityType.ZOMBIE, new BlockPos(16, 6, 8));
        // a zombie body: wild zombies leave it alone only from 25% (neutral_to)
        Mob zombieBody = secondee(h, p, EntityType.ZOMBIE, EntityType.COW, new BlockPos(24, 6, 8));
        Zombie wild = h.spawn(EntityType.ZOMBIE, new BlockPos(24, 6, 14));
        wild.setNoAi(true);
        for (Mob m : new Mob[]{squid, goat, zombieBody}) Secondees.data(m).teamSync = 24;
        squid.setAirSupply(200);
        float goatHealth = goat.getHealth();
        goat.hurt(goat.damageSources().fall(), 2);
        h.assertTrue(goat.getHealth() < goatHealth, "a goat body at 24% took no fall damage");
        wild.setTarget(zombieBody);
        h.assertTrue(wild.getTarget() == zombieBody, "a wild zombie left a zombie body at 24% alone");
        wild.setTarget(null);
        h.runAfterDelay(2, () -> {
            h.assertTrue(squid.getAirSupply() < 200, "a squid body at 24% kept its air out of water: " + squid.getAirSupply());
            for (Mob m : new Mob[]{squid, goat, zombieBody}) Secondees.data(m).teamSync = 25;
            goat.invulnerableTime = 0;
            float before = goat.getHealth();
            goat.hurt(goat.damageSources().fall(), 2);
            h.assertTrue(goat.getHealth() == before, "a goat body at 25% took fall damage");
            wild.setTarget(zombieBody);
            h.assertTrue(wild.getTarget() == null, "a wild zombie took on a zombie body at 25%");
            h.runAfterDelay(2, () -> {
                h.assertTrue(squid.getAirSupply() == squid.getMaxAirSupply(), "a squid body at 25% is short of air: " + squid.getAirSupply());
                done(h, p);
            });
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 100)
    public static void anEndermanBodyHopsFrom50AndAgainFrom75(GameTestHelper h) {
        ServerPlayer p = boss(h, new BlockPos(2, 6, 2));
        Mob ender = secondee(h, p, EntityType.ENDERMAN, EntityType.COW, new BlockPos(8, 6, 16));
        SecondeeData d = Secondees.data(ender);
        WorkerData w = Workforce.data(ender);
        HostData hd = Hosts.data(ender);
        AABB box = new AABB(h.absolutePos(new BlockPos(1, 0, 1))).expandTowards(30, 9, 30);
        BlockPos stop = h.absolutePos(new BlockPos(20, 6, 16));
        // 49%: it walks
        d.teamSync = 49;
        BodyWork.setOff(ender, stop);
        h.assertTrue(Movers.WALK.equals(Movers.family(ender)), "an enderman body at 49% moves by " + Movers.family(ender));
        // 50%: its Teleport hops it to the stop, inside the box, and cools
        d.teamSync = 50;
        // (once it has landed: a walker plans from the ground)
        h.runAfterDelay(10, () -> {
            w.lastPath = Long.MIN_VALUE / 2;
            WorkerPaths.Result r = WorkerPaths.go(ender, w, stop, 1.0, box, false);
            h.assertTrue(r == WorkerPaths.Result.WALKING, "no way to the stop at 50%: " + r);
            h.assertTrue(ender.position().distanceTo(Vec3.atBottomCenterOf(stop)) <= 3.5, "an enderman body at 50% did not hop to the stop: "
                    + ender.position().distanceTo(Vec3.atBottomCenterOf(stop)) + " away");
            h.assertTrue(box.contains(ender.position()), "the hop left the box");
            h.assertTrue(hd.primaryCooldown == ability(ender, false).cooldown(), "the Teleport did not cool: " + hd.primaryCooldown);
            // its Teleport cooling, at 50% it walks back
            BlockPos back = h.absolutePos(new BlockPos(8, 6, 16));
            BodyWork.setOff(ender, back);
            h.assertTrue(Movers.WALK.equals(Movers.family(ender)), "it hopped again while its Teleport cooled, at 50%");
            // 75%: the Secondary hops it while the Ability cools
            d.teamSync = 75;
            BodyWork.setOff(ender, back);
            h.assertTrue(Movers.TELEPORT.equals(Movers.family(ender)), "no Secondary hop at 75%");
            h.assertTrue(hd.secondaryCooldown == ability(ender, true).cooldown(), "the Secondary did not cool: " + hd.secondaryCooldown);
            done(h, p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 100)
    public static void aBlazeBodyHoversOnlyFrom75ToAHighStop(GameTestHelper h) {
        ServerPlayer p = boss(h, new BlockPos(2, 6, 2));
        Mob blaze = secondee(h, p, EntityType.BLAZE, EntityType.ZOMBIE, new BlockPos(8, 6, 16));
        SecondeeData d = Secondees.data(blaze);
        h.runAfterDelay(5, () -> {
            BlockPos high = blaze.blockPosition().offset(6, 4, 0), low = blaze.blockPosition().offset(6, 0, 0);
            d.teamSync = 74;
            BodyWork.setOff(blaze, high);
            h.assertTrue(Movers.WALK.equals(Movers.family(blaze)), "a blaze body at 74% flies: " + Movers.family(blaze));
            d.teamSync = 75;
            BodyWork.setOff(blaze, low);
            h.assertTrue(Movers.WALK.equals(Movers.family(blaze)), "a blaze body hovers to a stop on its own level");
            BodyWork.setOff(blaze, high);
            h.assertTrue(Movers.FLY_FREE.equals(Movers.family(blaze)), "a blaze body at 75% does not hover to a high stop");
            h.assertTrue(Hosts.data(blaze).secondaryCooldown > 0, "the Hover did not cool");
            done(h, p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 100)
    public static void theFullBonusIsTenPercentSpeedAndCarry(GameTestHelper h) {
        ServerPlayer p = boss(h, new BlockPos(2, 6, 2));
        Mob cow = secondee(h, p, EntityType.COW, EntityType.COW, new BlockPos(8, 6, 8));
        Mob ender = secondee(h, p, EntityType.ENDERMAN, EntityType.COW, new BlockPos(16, 6, 8));
        Secondees.data(cow).teamSync = 99.9f;
        Secondees.data(ender).teamSync = 99.9f;
        WorkTable.Spec cow99 = WorkTable.of(cow), ender99 = WorkTable.of(ender);
        h.assertTrue(cow99.carryLimit(64) == 64 && ender99.carryLimit(64) == 16, "setup: carry " + cow99.carryLimit(64) + ", " + ender99.carryLimit(64));
        Secondees.data(cow).teamSync = 100;
        Secondees.data(ender).teamSync = 100;
        WorkTable.Spec cow100 = WorkTable.of(cow), ender100 = WorkTable.of(ender);
        h.assertTrue(Math.abs(cow100.walk() - cow99.walk() * 1.1f) < 1e-4, "walk " + cow100.walk() + " at 100%, " + cow99.walk() + " at 99.9%");
        h.assertTrue(cow100.carryLimit(64) == 70, "a cow body carries " + cow100.carryLimit(64) + " at 100%, not 70");
        h.assertTrue(ender100.carryLimit(64) == 17, "an enderman body carries " + ender100.carryLimit(64) + " at 100%, not 17");
        h.assertTrue(WorkTable.of(cow) == cow100, "the 100% row is made again each time it is read");
        // and the health and attack bonus a synced host already has, from team sync
        float max = cow.getMaxHealth();
        h.runAfterDelay(2, () -> {
            h.assertTrue(Math.abs(cow.getMaxHealth() - max * 1.1f) < 0.01f, "max health " + cow.getMaxHealth() + " at 100%, was " + max);
            done(h, p);
        });
    }

    // --- 5.1 #8: the lace at 50% team sync ----------------------------------------------------------------------------

    @GameTest(template = "platform32", timeoutTicks = 120)
    public static void laceTakesAtFiftyTeamSync(GameTestHelper h) {
        ServerPlayer p = boss(h, new BlockPos(14, 6, 16));
        Mob host = secondee(h, p, EntityType.PIG, EntityType.COW, AT);
        HostData data = Hosts.data(host);
        Secondees.data(host).teamSync = 49;
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.NEURAL_LACE.get()));
        p.interactOn(host, InteractionHand.MAIN_HAND);
        h.assertFalse(data.laced, "the lace took at 49% team sync");
        h.assertTrue(p.getMainHandItem().getCount() == 1, "the refused lace was used up");
        Secondees.data(host).teamSync = 50;
        p.interactOn(host, InteractionHand.MAIN_HAND);
        h.assertTrue(data.laced, "the lace did not take at 50% team sync");
        h.assertTrue(p.getMainHandItem().isEmpty(), "the lace was not used");
        float fresh = data.freshness;
        h.runAfterDelay(60, () -> {
            h.assertTrue(data.freshness == fresh, "a laced secondee still rots: " + fresh + " to " + data.freshness);
            done(h, p);
        });
    }

    // --- 5.1 #13: the body's tricks on cooldown never break a block ---------------------------------------------------

    /** The box: 17 x 9 x 17 of glass with planks and leaves in its walls, and a fenced corner of fragile things. */
    private static final BlockPos BOX_A = new BlockPos(8, 4, 8), BOX_B = new BlockPos(24, 12, 24);
    private static final BlockPos HOST_AT = new BlockPos(12, 5, 16), FOE_AT = new BlockPos(15, 5, 16);

    private static void buildBox(GameTestHelper h) {
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (BlockPos q : BlockPos.betweenClosed(BOX_A.above(), BOX_B)) {
            boolean wall = q.getX() == BOX_A.getX() || q.getX() == BOX_B.getX() || q.getZ() == BOX_A.getZ() || q.getZ() == BOX_B.getZ();
            boolean roof = q.getY() == BOX_B.getY();
            if (!wall && !roof) continue;
            BlockState s = Blocks.GLASS.defaultBlockState();
            if (wall && !roof && q.getZ() == 12) s = Blocks.OAK_PLANKS.defaultBlockState();
            if (wall && !roof && q.getZ() == 20) s = leaves;
            h.setBlock(q, s);
        }
        // the fenced corner: farmland and ripe wheat by water, turtle eggs on planks, a TNT block, a door
        h.setBlock(new BlockPos(9, 4, 9), Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7));
        h.setBlock(new BlockPos(10, 4, 9), Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7));
        h.setBlock(new BlockPos(9, 5, 9), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        h.setBlock(new BlockPos(10, 5, 9), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        h.setBlock(new BlockPos(11, 4, 9), Blocks.WATER);
        h.setBlock(new BlockPos(9, 5, 11), Blocks.OAK_PLANKS);
        h.setBlock(new BlockPos(9, 6, 11), Blocks.TURTLE_EGG);
        h.setBlock(new BlockPos(10, 5, 11), Blocks.TNT);
        h.setBlock(new BlockPos(11, 5, 11), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        h.setBlock(new BlockPos(11, 6, 11), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        for (int i = 9; i <= 12; i++) {
            for (int y = 5; y <= 6; y++) {
                h.setBlock(new BlockPos(12, y, i), Blocks.GLASS);
                h.setBlock(new BlockPos(i, y, 12), Blocks.GLASS);
            }
        }
    }

    private static Map<BlockPos, BlockState> snapshot(GameTestHelper h) {
        Map<BlockPos, BlockState> out = new HashMap<>();
        for (BlockPos q : BlockPos.betweenClosed(BOX_A, BOX_B)) out.put(q.immutable(), h.getBlockState(q));
        return out;
    }

    private static void sameBlocks(GameTestHelper h, Map<BlockPos, BlockState> was, String what) {
        for (var e : was.entrySet()) {
            BlockState now = h.getBlockState(e.getKey());
            if (!now.equals(e.getValue())) {
                throw new net.minecraft.gametest.framework.GameTestAssertException(what + " changed " + e.getKey() + ": " + e.getValue() + " to " + now);
            }
        }
    }

    /**
     * A {@code body} secondee at 100% team sync in the box fires its trick at a foe three times, at the end of each
     * cooldown (at most 100 ticks apart: a bell's minute is not waited out); after the last has had 230 ticks to land
     * and melt (and at least 300 in all) every block is as it was, and no web is left.
     */
    private static void neverBreaks(GameTestHelper h, EntityType<? extends Mob> body, boolean secondary, EntityType<? extends Mob> foeType) {
        ServerPlayer p = boss(h, new BlockPos(2, 5, 2));
        buildBox(h);
        Mob host = secondee(h, p, body, EntityType.ZOMBIE, HOST_AT);
        host.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, false, false));
        host.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, -1, 4, false, false));
        Secondees.data(host).teamSync = 100;
        Mob foe = dummy(h, foeType, FOE_AT, true);
        HostDefinition.Ability a = ability(host, secondary);
        Map<BlockPos, BlockState> was = snapshot(h);
        // a web's cell is taken until it melts (200 ticks): the next is spun when the last has gone
        int cd = a.type().equals("web") ? 230 : Math.max(20, Math.min(100, a.cooldown()));
        int[] fired = {0};
        String[] last = {null};
        for (int i = 0; i < 3; i++) {
            h.runAfterDelay(5 + (long) i * cd, () -> {
                last[0] = HostAbilities.fireFor(host, foe, a, p.getUUID());
                if (last[0] == null) fired[0]++;
            });
        }
        long end = Math.max(300, 5 + 2L * cd + 230);
        h.runAfterDelay(end, () -> {
            h.assertTrue(fired[0] == 3, "the " + a.type() + " fired " + fired[0] + " times of 3: " + last[0]);
            sameBlocks(h, was, "the " + a.type());
            for (BlockPos q : BlockPos.betweenClosed(BOX_A, BOX_B)) {
                h.assertFalse(h.getBlockState(q).is(Blocks.COBWEB), "a web is left at " + q.immutable());
            }
            done(h, p);
        });
    }

    @GameTest(template = "site34", timeoutTicks = 600)
    public static void arrowsNeverBreakBlocks(GameTestHelper h) {
        neverBreaks(h, EntityType.SKELETON, false, EntityType.ZOMBIE);
    }

    @GameTest(template = "site34", timeoutTicks = 600)
    public static void smallFireballsNeverBreakBlocks(GameTestHelper h) {
        neverBreaks(h, EntityType.BLAZE, false, EntityType.ZOMBIE);
    }

    @GameTest(template = "site34", timeoutTicks = 600)
    public static void aGhastFireballNeverBreaksBlocks(GameTestHelper h) {
        neverBreaks(h, EntityType.GHAST, false, EntityType.ZOMBIE);
    }

    @GameTest(template = "site34", timeoutTicks = 900)
    public static void websNeverBreakBlocks(GameTestHelper h) {
        neverBreaks(h, EntityType.SPIDER, false, EntityType.ZOMBIE);
    }

    @GameTest(template = "site34", timeoutTicks = 600)
    public static void aChargeNeverBreaksBlocks(GameTestHelper h) {
        neverBreaks(h, EntityType.GOAT, false, EntityType.ZOMBIE);
    }

    @GameTest(template = "site34", timeoutTicks = 600)
    public static void aPounceNeverBreaksBlocks(GameTestHelper h) {
        neverBreaks(h, EntityType.WOLF, false, EntityType.ZOMBIE);
    }

    @GameTest(template = "site34", timeoutTicks = 600)
    public static void aTossNeverBreaksBlocks(GameTestHelper h) {
        neverBreaks(h, EntityType.IRON_GOLEM, false, EntityType.ZOMBIE);
    }

    @GameTest(template = "site34", timeoutTicks = 800)
    public static void aRoarNeverBreaksBlocks(GameTestHelper h) {
        neverBreaks(h, EntityType.POLAR_BEAR, false, EntityType.ZOMBIE);
    }

    @GameTest(template = "site34", timeoutTicks = 800)
    public static void inkNeverBreaksBlocks(GameTestHelper h) {
        neverBreaks(h, EntityType.SQUID, false, EntityType.ZOMBIE);
    }

    @GameTest(template = "site34", timeoutTicks = 800)
    public static void echolocationNeverBreaksBlocks(GameTestHelper h) {
        neverBreaks(h, EntityType.BAT, false, EntityType.ZOMBIE);
    }

    @GameTest(template = "site34", timeoutTicks = 800)
    public static void aSonicBoomNeverBreaksBlocks(GameTestHelper h) {
        neverBreaks(h, EntityType.WARDEN, false, EntityType.ZOMBIE);
    }

    @GameTest(template = "site34", timeoutTicks = 600)
    public static void aPotionNeverBreaksBlocks(GameTestHelper h) {
        neverBreaks(h, EntityType.WITCH, false, EntityType.ZOMBIE);
    }

    @GameTest(template = "site34", timeoutTicks = 600)
    public static void spitNeverBreaksBlocks(GameTestHelper h) {
        neverBreaks(h, EntityType.LLAMA, false, EntityType.ZOMBIE);
    }

    @GameTest(template = "site34", timeoutTicks = 800)
    public static void aHissNeverBreaksBlocks(GameTestHelper h) {
        neverBreaks(h, EntityType.CAT, false, EntityType.CREEPER);
    }

    @GameTest(template = "site34", timeoutTicks = 800)
    public static void aRearNeverBreaksBlocks(GameTestHelper h) {
        neverBreaks(h, EntityType.HORSE, true, EntityType.ZOMBIE);
    }

    @GameTest(template = "site34", timeoutTicks = 600)
    public static void theBellNeverBreaksBlocks(GameTestHelper h) {
        neverBreaks(h, EntityType.VILLAGER, false, EntityType.PILLAGER);
    }

    @GameTest(template = "site34", timeoutTicks = 800)
    public static void aSprintNeverBreaksBlocks(GameTestHelper h) {
        neverBreaks(h, EntityType.HORSE, false, EntityType.ZOMBIE);
    }

    @GameTest(template = "site34", timeoutTicks = 800)
    public static void aHoverNeverBreaksBlocks(GameTestHelper h) {
        neverBreaks(h, EntityType.BLAZE, true, EntityType.ZOMBIE);
    }

    /** The enderman body's hop is the movers' (the brain picks the cell): three hops between two stops in the box. */
    @GameTest(template = "site34", timeoutTicks = 600)
    public static void hopsNeverBreakBlocks(GameTestHelper h) {
        ServerPlayer p = boss(h, new BlockPos(2, 5, 2));
        buildBox(h);
        Mob host = secondee(h, p, EntityType.ENDERMAN, EntityType.COW, HOST_AT);
        Secondees.data(host).teamSync = 100;
        WorkerData w = Workforce.data(host);
        AABB box = new AABB(h.absolutePos(BOX_A.offset(1, 1, 1))).expandTowards(BOX_B.getX() - BOX_A.getX() - 2, 6, BOX_B.getZ() - BOX_A.getZ() - 2);
        Map<BlockPos, BlockState> was = snapshot(h);
        BlockPos[] stops = {h.absolutePos(new BlockPos(21, 5, 21)), h.absolutePos(HOST_AT)};
        int cd = ability(host, false).cooldown();
        int[] hops = {0};
        for (int i = 0; i < 3; i++) {
            int n = i;
            h.runAfterDelay(5 + (long) i * (cd + 5), () -> {
                Vec3 from = host.position();
                w.lastPath = Long.MIN_VALUE / 2;
                w.pathFails = 0;
                WorkerPaths.go(host, w, stops[n % 2], 1.0, box, false);
                if (host.position().distanceTo(from) > BodyWork.HOP_FROM) hops[0]++;
                h.assertTrue(box.contains(host.position()), "a hop left the box");
            });
        }
        h.runAfterDelay(300, () -> {
            h.assertTrue(hops[0] == 3, hops[0] + " hops of 3");
            sameBlocks(h, was, "the hops");
            done(h, p);
        });
    }

    @GameTest(template = "site34", timeoutTicks = 200)
    public static void theBrainNeverExplodesGroansOrWindCharges(GameTestHelper h) {
        ServerPlayer p = boss(h, new BlockPos(2, 5, 2));
        Mob creeper = secondee(h, p, EntityType.CREEPER, EntityType.ZOMBIE, new BlockPos(8, 5, 8));
        Mob zombie = secondee(h, p, EntityType.ZOMBIE, EntityType.ZOMBIE, new BlockPos(8, 5, 20));
        Mob breeze = secondee(h, p, EntityType.BREEZE, EntityType.ZOMBIE, new BlockPos(24, 5, 8));
        Mob[] bodies = {creeper, zombie, breeze};
        Mob[] foes = {dummy(h, EntityType.HUSK, new BlockPos(11, 5, 8), true), dummy(h, EntityType.HUSK, new BlockPos(11, 5, 20), true),
                dummy(h, EntityType.HUSK, new BlockPos(27, 5, 8), true)};
        // a wild zombie the groan would set on the husk
        Zombie wild = h.spawn(EntityType.ZOMBIE, new BlockPos(8, 5, 26));
        wild.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, false, false));
        for (Mob m : bodies) {
            Secondees.data(m).teamSync = 100;
            m.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, -1, 4, false, false));
            m.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, false, false));
        }
        // fireFor itself refuses all three
        for (Mob m : bodies) {
            String no = HostAbilities.fireFor(m, foes[0], ability(m, false), p.getUUID());
            h.assertTrue(HostAbilities.NEVER.equals(no), "fireFor fired the " + ability(m, false).type() + ": " + no);
        }
        String[] status = {null};
        h.onEachTick(() -> {
            if (h.getTick() % 10 != 0) return;
            for (int i = 0; i < bodies.length; i++) {
                Combat.fight(bodies[i], Workforce.data(bodies[i]), (LivingEntity) foes[i], null);
                if (i == 0 && status[0] == null) status[0] = Workforce.data(creeper).status;
            }
        });
        h.runAfterDelay(160, () -> {
            h.assertTrue(creeper.isAlive() && !creeper.isRemoved(), "the creeper body went off");
            h.assertTrue(("staff.headinajar.say." + BodyWork.NO_EXPLODE).equals(status[0]), "the creeper body did not say Policy 9.2: " + status[0]);
            h.assertTrue(wild.getTarget() != foes[1], "a wild zombie was set on the foe (a groan)");
            AABB all = new AABB(h.absolutePos(BlockPos.ZERO)).expandTowards(34, 18, 34);
            h.assertTrue(h.getLevel().getEntitiesOfClass(WindCharge.class, all).isEmpty(), "a wind charge was fired");
            for (String type : new String[]{"explode", "groan", "wind_charge"}) h.assertTrue(BodyWork.fired(type) == 0, "the brain used a " + type);
            done(h, p);
        });
    }

    // --- 5.1 #14: staff shots spare friends --------------------------------------------------------------------------

    /**
     * {@code body}'s trick fired at a zombie with the employer, a colleague, a villager and a tamed wolf round it: none
     * of them is hurt or touched; the zombie is.
     */
    private static void sparesFriends(GameTestHelper h, EntityType<? extends Mob> body, boolean secondary, boolean byDamage) {
        ServerPlayer p = boss(h, new BlockPos(17, 6, 14));
        // the villager stands between the body and the zombie, in a charge's way (a ghast, four blocks wide, stands back)
        Mob host = secondee(h, p, body, EntityType.ZOMBIE, new BlockPos(16, 6, body == EntityType.GHAST ? 5 : 9));
        host.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, -1, 4, false, false));
        Secondees.data(host).teamSync = 100;
        Mob zombie = dummy(h, EntityType.ZOMBIE, new BlockPos(16, 6, 13), false);
        Mob colleague = h.spawn(EntityType.COW, new BlockPos(15, 6, 14));
        h.assertTrue(Workforce.useLanyard(p, new ItemStack(WorkRegistry.STAFF_LANYARD.get()), colleague), "setup: the cow was not hired");
        Villager villager = h.spawn(EntityType.VILLAGER, new BlockPos(16, 6, 11));
        villager.setNoAi(true);
        Wolf wolf = h.spawn(EntityType.WOLF, new BlockPos(17, 6, 12));
        wolf.tame(p);
        wolf.setNoAi(true);
        LivingEntity[] friends = {p, colleague, villager, wolf};
        float[] health = new float[friends.length];
        for (int i = 0; i < friends.length; i++) health[i] = friends[i].getHealth();
        float zombieHealth = zombie.getHealth();
        HostDefinition.Ability a = ability(host, secondary);
        String no = HostAbilities.fireFor(host, zombie, a, p.getUUID());
        h.assertTrue(no == null, "the " + a.type() + " did not fire: " + no);
        // effects are looked at while a roar's (2 s) still lasts; damage once a fireball has flown
        h.runAfterDelay(byDamage ? 40 : 10, () -> {
            String[] names = {"the employer", "a colleague", "a villager", "a tamed wolf"};
            for (int i = 0; i < friends.length; i++) {
                h.assertTrue(friends[i].getHealth() >= health[i], "the " + a.type() + " hurt " + names[i]);
                h.assertTrue(friends[i].getActiveEffects().isEmpty(), "the " + a.type() + " touched " + names[i] + ": " + friends[i].getActiveEffects());
            }
            boolean hit = byDamage ? zombie.getHealth() < zombieHealth : !zombie.getActiveEffects().stream().allMatch(e -> e.getEffect() == MobEffects.FIRE_RESISTANCE);
            h.assertTrue(hit, "the " + a.type() + " missed the zombie");
            done(h, p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 120)
    public static void aGhastFireballSparesFriends(GameTestHelper h) {
        sparesFriends(h, EntityType.GHAST, false, true);
    }

    @GameTest(template = "platform32", timeoutTicks = 120)
    public static void aRavagerChargeSparesFriends(GameTestHelper h) {
        sparesFriends(h, EntityType.RAVAGER, false, true);
    }

    @GameTest(template = "platform32", timeoutTicks = 120)
    public static void aPolarBearRoarSparesFriends(GameTestHelper h) {
        sparesFriends(h, EntityType.POLAR_BEAR, false, false);
    }

    @GameTest(template = "platform32", timeoutTicks = 120)
    public static void squidInkSparesFriends(GameTestHelper h) {
        sparesFriends(h, EntityType.SQUID, false, false);
    }

    /** A witch's potion is not thrown with a friend within 4 of the foe; with them out of the way it is, and spares them. */
    @GameTest(template = "platform32", timeoutTicks = 120)
    public static void aWitchPotionWaitsForFriendsToClear(GameTestHelper h) {
        ServerPlayer p = boss(h, new BlockPos(28, 6, 28));
        Mob witch = secondee(h, p, EntityType.WITCH, EntityType.ZOMBIE, new BlockPos(16, 6, 6));
        Secondees.data(witch).teamSync = 100;
        Mob zombie = dummy(h, EntityType.ZOMBIE, new BlockPos(16, 6, 12), false);
        Villager villager = h.spawn(EntityType.VILLAGER, new BlockPos(17, 6, 14));
        villager.setNoAi(true);
        HostDefinition.Ability a = ability(witch, false);
        h.assertTrue(HostAbilities.FRIENDS_NEAR.equals(HostAbilities.fireFor(witch, zombie, a, p.getUUID())), "a potion thrown with a villager 2 from the foe");
        Vec3 away = h.absoluteVec(new Vec3(16.5, 6, 24.5));
        villager.teleportTo(away.x, away.y, away.z);
        h.assertTrue(HostAbilities.fireFor(witch, zombie, a, p.getUUID()) == null, "no potion with the villager away");
        h.runAfterDelay(60, () -> {
            h.assertTrue(zombie.hasEffect(MobEffects.WEAKNESS), "the potion missed the zombie");
            h.assertTrue(villager.getActiveEffects().isEmpty(), "the potion touched the villager");
            done(h, p);
        });
    }

    // --- 5.1 #16: the head's turret post fires from the jar -----------------------------------------------------------

    @GameTest(template = "platform32", timeoutTicks = 100)
    public static void aSecondeeHeadFiresItsTurretPost(GameTestHelper h) {
        ServerPlayer p = boss(h, new BlockPos(2, 6, 2));
        Mob spider = secondee(h, p, EntityType.SPIDER, EntityType.SKELETON, new BlockPos(8, 6, 16));
        Mob zombie = dummy(h, EntityType.ZOMBIE, new BlockPos(17, 6, 16), true);
        WorkerData w = Workforce.data(spider);
        h.assertTrue("bow".equals(WorkTable.of(spider).attack()), "setup: a skeleton head's post is " + WorkTable.of(spider).attack());
        w.carried.add(new ItemStack(Items.ARROW, 16));
        h.runAfterDelay(2, () -> {
            Vec3 jar = BodyWork.jar(spider);
            AABB near = spider.getBoundingBox().inflate(4);
            h.assertTrue(Combat.fight(spider, w, (LivingEntity) zombie, null), "the skeleton head did not take the zombie on");
            List<AbstractArrow> arrows = h.getLevel().getEntitiesOfClass(AbstractArrow.class, near);
            h.assertTrue(arrows.size() == 1, arrows.size() + " arrows shot, not 1");
            double off = arrows.get(0).position().distanceTo(jar);
            h.assertTrue(off < 0.2, "the arrow left " + String.format("%.2f", off) + " from the jar");
            h.assertTrue(arrows.get(0).getOwner() == spider, "the arrow is not the secondee's");
            int left = w.carried.stream().mapToInt(ItemStack::getCount).sum();
            h.assertTrue(left == 15, left + " arrows left in the carry, not 15");
            // no arrows: no shot, held up, the line and one dry click
            w.carried.clear();
            h.runAfterDelay(40, () -> {
                h.assertFalse(Combat.fight(spider, w, (LivingEntity) zombie, null), "it fought on with no arrows");
                h.assertTrue(w.heldUp && "staff.headinajar.say.no_arrows".equals(w.status), "no stop line for no arrows: " + w.status);
                h.assertTrue(BodyWork.clickedDry(spider), "no dry click");
                done(h, p);
            });
        });
    }

    // --- 5.1 #17: a secondee keeps to its site ------------------------------------------------------------------------

    /**
     * A {@code body} secondee hauling between two chests at the very edges of its site box for 600 ticks never leaves
     * the box; it moves, and its team sync grows (real work with a card counts).
     */
    private static void keepsToTheSite(GameTestHelper h, EntityType<? extends Mob> body) {
        ServerPlayer p = boss(h, new BlockPos(17, 5, 30));
        BlockPos clockAt = new BlockPos(17, 5, 17);
        h.setBlock(clockAt, WorkRegistry.TIME_CLOCK.get());
        h.setBlock(clockAt.below(), Blocks.REDSTONE_BLOCK);
        TimeClockBlockEntity clock = (TimeClockBlockEntity) h.getBlockEntity(clockAt);
        clock.setOwner(p);
        AABB box = Workforce.box(h.absolutePos(clockAt));
        BlockPos from = new BlockPos(1, 5, 17), to = new BlockPos(33, 5, 17);
        h.setBlock(from, Blocks.CHEST);
        ((ChestBlockEntity) h.getBlockEntity(from)).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        h.setBlock(to, Blocks.CHEST);
        Mob host = secondee(h, p, body, EntityType.ZOMBIE, new BlockPos(17, 5, 21));
        host.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, false, false));
        h.assertTrue(clock.join(host), "setup: the clock had no room");
        List<Order.Stop> stops = new ArrayList<>();
        stops.add(new Order.Stop(h.absolutePos(from), Direction.UP, false, ItemStack.EMPTY, false));
        stops.add(new Order.Stop(h.absolutePos(to), Direction.UP, true, ItemStack.EMPTY, true));
        ItemStack card = card(Optional.of(clock.site()), WorkTable.HAULING, stops);
        h.assertTrue(JobCardItem.give(p, card, host), "setup: the card was refused: " + Refusals.lastDelivered(p));
        WorkerData w = Workforce.data(host);
        w.shift = WorkerData.Shift.REDSTONE;
        w.state = WorkerData.State.ON_JOB;
        Vec3 start = host.position();
        double[] moved = {0};
        h.onEachTick(() -> {
            if (!box.contains(host.position())) h.fail("the " + body.getDescription().getString() + " body left its site at " + host.position());
            moved[0] = Math.max(moved[0], host.position().distanceTo(start));
        });
        h.runAfterDelay(600, () -> {
            h.assertTrue(moved[0] > 4, "the " + body.getDescription().getString() + " body hardly moved: " + moved[0] + " (" + w.state + ", " + w.status + ")");
            h.assertTrue(Secondees.teamSync(host) > 0.5f, "no team sync from 600 ticks of work: " + Secondees.teamSync(host));
            done(h, p);
        });
    }

    @GameTest(template = "site34", timeoutTicks = 700)
    public static void aSecondeeKeepsToItsSite(GameTestHelper h) {
        keepsToTheSite(h, EntityType.COW);
    }

    @GameTest(template = "site34", timeoutTicks = 700)
    public static void aFlierStaysInsideTheBox(GameTestHelper h) {
        keepsToTheSite(h, EntityType.GHAST);
    }

    @GameTest(template = "site34", timeoutTicks = 700)
    public static void aBatBodyStaysInsideTheBox(GameTestHelper h) {
        keepsToTheSite(h, EntityType.BAT);
    }

    // --- the Whistle: fighters and pets ---------------------------------------------------------------------------------

    @GameTest(template = "platform32", timeoutTicks = 60)
    public static void aSecondeeOnTheWhistleIsAFighterOrAPet(GameTestHelper h) {
        ServerPlayer p = boss(h, new BlockPos(2, 6, 2));
        Mob archer = secondee(h, p, EntityType.COW, EntityType.SKELETON, new BlockPos(6, 6, 6));
        Mob goat = secondee(h, p, EntityType.GOAT, EntityType.COW, new BlockPos(12, 6, 6));
        Mob bat = secondee(h, p, EntityType.BAT, EntityType.COW, new BlockPos(18, 6, 6));
        Mob wolf = secondee(h, p, EntityType.WOLF, EntityType.SKELETON, new BlockPos(24, 6, 6));
        h.assertTrue(Detail.fighter(archer), "a skeleton head on a cow body is not a fighter");
        h.assertTrue(Detail.fighter(wolf), "a skeleton head on a wolf body is a pet (the body's kind)");
        h.assertFalse(Detail.fighter(goat), "a cow head on a goat body at 0% fights");
        Secondees.data(goat).teamSync = 50;
        h.assertTrue(Detail.fighter(goat), "a goat body at 50% (Charge) is not a fighter");
        Secondees.data(bat).teamSync = 50;
        h.assertFalse(Detail.fighter(bat), "a bat body's Echolocation made it a fighter");
        done(h, p);
    }

    // --- the chicken's egg ----------------------------------------------------------------------------------------------

    @GameTest(template = "platform32", timeoutTicks = 60)
    public static void aChickenBodyLaysIntoItsCarry(GameTestHelper h) {
        ServerPlayer p = boss(h, new BlockPos(2, 6, 2));
        Mob chicken = secondee(h, p, EntityType.CHICKEN, EntityType.COW, AT);
        WorkerData w = onTheJob(chicken, true);
        Secondees.data(chicken).teamSync = 49;
        work(h, chicken, 40);
        h.assertTrue(w.carried.isEmpty(), "an egg at 49%");
        Secondees.data(chicken).teamSync = 50;
        work(h, chicken, 40);
        int eggs = w.carried.stream().filter(s -> s.is(Items.EGG)).mapToInt(ItemStack::getCount).sum();
        h.assertTrue(eggs == 1, eggs + " eggs in the carry, not 1");
        h.assertTrue(Hosts.data(chicken).primaryCooldown == ability(chicken, false).cooldown(), "the Lay Egg did not cool");
        done(h, p);
    }
}
