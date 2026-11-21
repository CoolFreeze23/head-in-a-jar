package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.TimeClockBlockEntity;
import com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.SawTable;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffEvents;
import com.coolfreeze23.headinajar.staff.StaffFile;
import com.coolfreeze23.headinajar.staff.work.Combat;
import com.coolfreeze23.headinajar.staff.work.FriendOrFoe;
import com.coolfreeze23.headinajar.staff.work.Movers;
import com.coolfreeze23.headinajar.staff.work.WorkPause;
import com.coolfreeze23.headinajar.staff.work.WorkRules;
import com.coolfreeze23.headinajar.staff.work.WorkTable;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * The rules that keep a living worker a worker (0.7.0): nobody turns on staff and staff turn on nobody, the notice
 * period, Peaceful, leads, a Brain mob hired mid-fight, death, shots that pass over friends, fireballs that light
 * nothing, kill credit, the saw, griefing, the operators' commands, and old worlds. {@code platform}: 15 x 6 x 15, the
 * floor's top at helper y 1; {@code platform32}: 32 x 10 x 32, the floor's top at helper y 5. Zombies and skeletons
 * wear a Hard Hat so the test's daylight cannot burn them.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class StaffRuleGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);

    static ServerPlayer player(GameTestHelper h, BlockPos at) {
        return TestPlayers.at(h, "rules", at);
    }

    static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    /** Hired through the Lanyard (a hostile worn to half its health first), with a Hard Hat against the sun. */
    static <T extends Mob> T hire(GameTestHelper h, ServerPlayer p, T mob) {
        if (mob instanceof Enemy) mob.setHealth(mob.getMaxHealth() / 2);
        if (!Workforce.useLanyard(p, new ItemStack(WorkRegistry.STAFF_LANYARD.get()), mob)) {
            // the Lanyard refuses a species whose movement family this build does not drive yet (the movers' part);
            // the rule is what is tested here, so it is hired as the Lanyard would once that family is driven
            WorkTable.Spec spec = WorkTable.of(mob);
            h.assertTrue(spec != null && spec.refusal() == null && !Movers.canMove(mob, spec), "the Lanyard refused a " + mob.getType());
            Workforce.hire(p, mob);
        }
        h.assertTrue(Workforce.isWorker(mob), "not hired: " + mob.getType());
        if (mob instanceof Zombie || mob instanceof Skeleton) mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(WorkRegistry.HARD_HAT.get()));
        return mob;
    }

    static <T extends Mob> T hire(GameTestHelper h, ServerPlayer p, EntityType<T> type, BlockPos at) {
        return hire(h, p, h.spawn(type, at));
    }

    /** A wild creature that stands still and does not burn. */
    static <T extends Mob> T wild(GameTestHelper h, EntityType<T> type, BlockPos at) {
        T mob = h.spawn(type, at);
        mob.setNoAi(true);
        if (mob instanceof Zombie || mob instanceof Skeleton) mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        return mob;
    }

    // --- nobody turns on staff --------------------------------------------------------------------------------------

    @GameTest(template = "platform32")
    public static void turretsAndGolemsSpareStaff(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(4, 6, 4));
        Zombie worker = hire(h, p, EntityType.ZOMBIE, new BlockPos(16, 6, 16));
        Zombie wild = wild(h, EntityType.ZOMBIE, new BlockPos(20, 6, 16));
        // the turret's own rule: its owner's and anyone else's turret
        TurretPedestalBlockEntity turret = new TurretPedestalBlockEntity(BlockPos.ZERO, ModRegistry.TURRET_PEDESTAL.get().defaultBlockState());
        turret.setOwner(java.util.UUID.randomUUID());
        h.assertTrue(!turret.mayTarget(worker), "a turret would shoot a lanyarded zombie");
        h.assertTrue(turret.mayTarget(wild), "a turret no longer shoots a wild zombie");
        turret.setOwner(p.getUUID());
        h.assertTrue(!turret.mayTarget(worker) && turret.mayTarget(wild), "its owner's turret: staff spared, monsters not");
        h.assertTrue(!FriendOrFoe.mayTarget(null, h.getLevel(), worker), "another site's Security would go for staff");
        // golems and wolves: never staff, unless the worker hurt them first
        for (EntityType<? extends Mob> type : List.of(EntityType.IRON_GOLEM, EntityType.SNOW_GOLEM, EntityType.WOLF)) {
            Mob m = wild(h, type, new BlockPos(12, 6, 12));
            m.setTarget(worker);
            h.assertTrue(m.getTarget() == null, "a " + type + " picked a member of staff as its target");
            m.setTarget(wild);
            h.assertTrue(m.getTarget() == wild, "a " + type + " no longer picks a wild zombie");
            m.setTarget(null);
            m.setLastHurtByMob(worker);
            m.setTarget(worker);
            h.assertTrue(m.getTarget() == worker, "a " + type + " hurt by a worker may not answer it");
            m.discard();
        }
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 500)
    public static void aHiredZombieAttacksNobody(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(4, 2, 7));
        Villager v = h.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 7));
        Zombie z = hire(h, p, EntityType.ZOMBIE, FLOOR);
        float vHealth = v.getHealth(), pHealth = p.getHealth();
        z.setTarget(v);
        h.assertTrue(z.getTarget() == null, "a worker took a villager as its target");
        z.setTarget(p);
        h.assertTrue(z.getTarget() == null, "a worker took a player as its target");
        h.onEachTick(() -> {
            if (z.getTarget() != null) h.fail("the hired zombie targets " + z.getTarget());
        });
        h.runAtTickTime(400, () -> {
            h.assertTrue(z.isAlive() && Workforce.isWorker(z), "the zombie is gone");
            h.assertTrue(v.isAlive() && v.getHealth() >= vHealth, "the villager was hurt");
            h.assertTrue(p.getHealth() >= pHealth, "the player was hurt");
            done(h, p);
        });
    }

    @GameTest(template = "platform")
    public static void aHiredZombieNeverDespawnsOnPeaceful(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(3, 2, 3));
        Zombie z = hire(h, p, EntityType.ZOMBIE, FLOOR);
        Zombie control = wild(h, EntityType.ZOMBIE, new BlockPos(10, 2, 10));
        var server = h.getLevel().getServer();
        Difficulty was = h.getLevel().getDifficulty();
        // Peaceful only for the length of this call (tests share the server; nothing else ticks in between)
        server.setDifficulty(Difficulty.PEACEFUL, true);
        try {
            z.checkDespawn();
            control.checkDespawn();
        } finally {
            server.setDifficulty(was, true);
        }
        h.assertTrue(!z.isRemoved(), "a hired zombie despawned on Peaceful");
        h.assertTrue(control.isRemoved(), "a wild zombie stayed on Peaceful (the check did nothing)");
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void aBrainMobHiredMidFightCalmsDown(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(3, 2, 3));
        Villager v = wild(h, EntityType.VILLAGER, new BlockPos(10, 2, 7));
        Piglin piglin = h.spawn(EntityType.PIGLIN, FLOOR);
        piglin.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, (LivingEntity) v);
        piglin.getBrain().setMemory(MemoryModuleType.ANGRY_AT, p.getUUID());
        piglin.setAggressive(true);
        hire(h, p, piglin);
        Runnable calm = () -> {
            h.assertTrue(piglin.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isEmpty(), "the piglin still remembers its fight");
            h.assertTrue(piglin.getBrain().getMemory(MemoryModuleType.ANGRY_AT).isEmpty(), "the piglin is still angry at someone");
            h.assertTrue(!piglin.isAggressive() && piglin.getTarget() == null, "the piglin still looks for a fight");
        };
        calm.run();
        h.runAtTickTime(100, () -> {
            calm.run();
            h.assertTrue(v.getHealth() >= v.getMaxHealth(), "the villager was hurt");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void aHiredZombieCanBeLed(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(5, 2, 7));
        Zombie z = hire(h, p, EntityType.ZOMBIE, FLOOR);
        Zombie control = wild(h, EntityType.ZOMBIE, new BlockPos(10, 2, 10));
        Villager v = hire(h, p, EntityType.VILLAGER, new BlockPos(3, 2, 10));
        h.assertTrue(!control.canBeLeashed(), "a wild zombie can be led (vanilla says no)");
        h.assertTrue(v.canBeLeashed(), "a staff villager cannot be led");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD));
        z.interact(p, InteractionHand.MAIN_HAND);
        h.assertTrue(z.isLeashed() && z.getLeashHolder() == p, "the lead did not take on a hired zombie");
        h.runAtTickTime(40, () -> {
            h.assertTrue(z.isLeashed(), "the lead came off the hired zombie");
            done(h, p);
        });
    }

    @GameTest(template = "platform", timeoutTicks = 320)
    public static void noticeTargetsNobodyForTenSeconds(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(3, 2, 3));
        Villager v = wild(h, EntityType.VILLAGER, new BlockPos(10, 2, 10));
        Zombie z = hire(h, p, EntityType.ZOMBIE, FLOOR);
        Workforce.letGo(p, z);
        WorkerData d = Workforce.data(z);
        h.assertTrue(d != null && d.state == WorkerData.State.NOTICE, "Let go did not start the notice");
        for (int t : new int[]{1, 60, 120, 190}) {
            h.runAtTickTime(t, () -> {
                h.assertTrue(Workforce.isWorker(z), "the notice ended early, at tick " + t);
                z.setTarget(v);
                h.assertTrue(z.getTarget() == null, "on notice it took a target at tick " + t);
            });
        }
        h.runAtTickTime(260, () -> {
            h.assertTrue(!Workforce.isWorker(z), "the notice never ended");
            z.setTarget(v);
            h.assertTrue(z.getTarget() == v, "after its notice it is not an ordinary zombie again");
            z.setTarget(null);
            done(h, p);
        });
    }

    // --- death ------------------------------------------------------------------------------------------------------

    @GameTest(template = "platform", timeoutTicks = 100)
    public static void deathDropsEverythingOnce(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(2, 2, 2));
        Zombie z = hire(h, p, EntityType.ZOMBIE, FLOOR);
        Cow colleague = hire(h, p, EntityType.COW, new BlockPos(10, 2, 7));
        ServerPlayer other = player(h, new BlockPos(12, 2, 12));
        Cow stranger = hire(h, other, EntityType.COW, new BlockPos(11, 2, 11));
        int before = Staff.of(colleague).morale, strangerBefore = Staff.of(stranger).morale;
        WorkerData d = Workforce.data(z);
        d.carried.add(new ItemStack(Items.COBBLESTONE, 10));
        d.tool = new ItemStack(Items.SHEARS);
        d.pocket = new ItemStack(Items.GOLD_NUGGET);
        d.card = new ItemStack(WorkRegistry.JOB_CARD.get());
        // a hand showing the carry (as a carry pose might) must not drop a second copy
        z.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.COBBLESTONE, 10));
        z.setGuaranteedDrop(EquipmentSlot.MAINHAND);
        StaffFile f = Staff.of(z);
        int number = f.number;
        z.hurt(h.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
        h.runAtTickTime(3, () -> {
            AABB around = new AABB(h.absolutePos(FLOOR)).inflate(4);
            List<ItemEntity> items = h.getLevel().getEntitiesOfClass(ItemEntity.class, around);
            java.util.function.Function<net.minecraft.world.item.Item, Integer> count = item ->
                    items.stream().filter(e -> e.getItem().is(item)).mapToInt(e -> e.getItem().getCount()).sum();
            h.assertTrue(count.apply(WorkRegistry.STAFF_LANYARD.get()) == 1, "lanyards dropped: " + count.apply(WorkRegistry.STAFF_LANYARD.get()));
            h.assertTrue(count.apply(Items.COBBLESTONE) == 10, "the carry dropped " + count.apply(Items.COBBLESTONE) + " of 10");
            h.assertTrue(count.apply(Items.SHEARS) == 1 && count.apply(Items.GOLD_NUGGET) == 1, "the tool or the pocket did not drop once");
            h.assertTrue(count.apply(WorkRegistry.JOB_CARD.get()) == 1, "the personal card did not drop once");
            h.assertTrue(count.apply(WorkRegistry.HARD_HAT.get()) == 1, "the Hard Hat did not drop");
            h.assertTrue(count.apply(ModRegistry.CONDOLENCE_CARD.get()) == 1, "no Condolence Card");
            h.assertTrue(StaffEvents.exits().stream().anyMatch(s -> s.startsWith(p.getUUID() + "|") && s.endsWith("|" + number)), "no Exit Interview");
            h.assertTrue(Staff.of(colleague).morale == Math.max(0, before + WorkRules.GRIEF), "a colleague within 16 did not lose 5 morale");
            h.assertTrue(Staff.of(stranger).morale == strangerBefore, "someone else's staff grieved");
            h.assertTrue(com.coolfreeze23.headinajar.staff.work.WorkerIndex.get(h.getLevel().getServer()).of(p.getUUID()).size() == 1,
                    "the dead worker is still on Head Office's index");
            done(h, p, other);
        });
    }

    // --- shots ------------------------------------------------------------------------------------------------------

    @GameTest(template = "platform32", timeoutTicks = 200)
    public static void securityArrowsPassOverFriends(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(4, 6, 4));
        Skeleton s = hire(h, p, EntityType.SKELETON, new BlockPos(6, 6, 16));
        Villager v = wild(h, EntityType.VILLAGER, new BlockPos(12, 6, 16));
        Cow colleague = hire(h, p, EntityType.COW, new BlockPos(12, 6, 22));
        Zombie z = wild(h, EntityType.ZOMBIE, new BlockPos(19, 6, 12));
        float vh = v.getHealth(), ch = colleague.getHealth(), zh = z.getHealth();
        // an arrow of the worker's, loosed point-blank at a villager, a colleague and a wild zombie
        for (LivingEntity at : List.of(v, colleague, z)) {
            Arrow a = new Arrow(h.getLevel(), s, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
            Vec3 eye = at.getEyePosition().add(-1.5, 0, 0);
            a.moveTo(eye.x, eye.y, eye.z);
            a.shoot(1, 0, 0, Combat.VELOCITY, 0);
            h.getLevel().addFreshEntity(a);
        }
        // and the real shot: one arrow from the carry, pickup allowed
        WorkerData d = Workforce.data(s);
        d.carried.add(new ItemStack(Items.ARROW, 4));
        h.assertTrue(Combat.fight(s, d, z, null), "a skeleton with arrows would not fight a zombie in range");
        h.assertTrue(d.carried.get(0).getCount() == 3, "the shot did not take one arrow from the carry");
        List<AbstractArrow> shot = h.getLevel().getEntitiesOfClass(AbstractArrow.class, s.getBoundingBox().inflate(3), a -> a.getOwner() == s && a.pickup == AbstractArrow.Pickup.ALLOWED);
        h.assertTrue(!shot.isEmpty(), "no arrow left the skeleton (or it cannot be picked up)");
        h.assertTrue(!Combat.fight(s, d, v, null), "a skeleton would fight a villager");
        d.carried.clear();
        h.runAtTickTime(40, () -> {
            h.assertTrue(!Combat.fight(s, d, z, null) && d.heldUp, "out of arrows it still fights, or is not held up");
            h.assertTrue(v.getHealth() >= vh, "an arrow hit the villager");
            h.assertTrue(colleague.getHealth() >= ch, "an arrow hit a colleague");
            h.assertTrue(z.getHealth() < zh, "the arrow did not hit the wild zombie");
            done(h, p);
        });
    }

    @GameTest(template = "platform32", timeoutTicks = 200)
    public static void staffFireballsLightNothing(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(4, 6, 4));
        Blaze b = hire(h, p, EntityType.BLAZE, new BlockPos(10, 6, 16));
        b.setNoGravity(true);
        BlockPos wall = new BlockPos(16, 6, 16);
        for (int y = 6; y <= 9; y++) for (int z = 14; z <= 18; z++) h.setBlock(new BlockPos(16, y, z), Blocks.OAK_PLANKS);
        SmallFireball ball = new SmallFireball(h.getLevel(), b, new Vec3(1, 0, 0));
        Vec3 from = h.absoluteVec(new Vec3(13.5, 7.5, 16.5));
        ball.moveTo(from.x, from.y, from.z);
        h.getLevel().addFreshEntity(ball);
        h.assertTrue(!EventHooks.canEntityGrief(h.getLevel(), b), "a staff blaze may grief");
        h.runAtTickTime(60, () -> {
            for (BlockPos q : BlockPos.betweenClosed(h.absolutePos(wall.offset(-3, -1, -3)), h.absolutePos(wall.offset(1, 4, 3)))) {
                if (h.getLevel().getBlockState(q).is(Blocks.FIRE)) h.fail("a staff fireball lit a fire at " + q);
            }
            h.assertTrue(ball.isRemoved(), "the fireball is still flying");
            done(h, p);
        });
    }

    // --- credit, the saw, griefing ----------------------------------------------------------------------------------

    @GameTest(template = "platform32")
    public static void killCreditGoesToTheEmployerOnlyWithinThirtyTwo(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(10, 6, 16));
        Zombie z = hire(h, p, EntityType.ZOMBIE, new BlockPos(16, 6, 16));
        h.assertTrue(p.getUUID().equals(Combat.creditFor(z)), "the employer 6 blocks away is not credited");
        Vec3 far = h.absoluteVec(new Vec3(16, 6, 16)).add(40, 0, 0);
        p.teleportTo(far.x, far.y, far.z);
        h.assertTrue(Combat.creditFor(z) == null, "the employer 40 blocks away is still credited");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void theSawAndGriefingRefuseStaff(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(3, 2, 3));
        Zombie z = hire(h, p, EntityType.ZOMBIE, FLOOR);
        Zombie control = wild(h, EntityType.ZOMBIE, new BlockPos(10, 2, 10));
        SawTable.Verdict v = SawTable.verdict(z, p, ItemStack.EMPTY, true);
        h.assertTrue("staff.saw".equals(v.reason()), "the saw on staff says " + v.reason());
        h.assertTrue(!EventHooks.canEntityGrief(h.getLevel(), z), "a hired zombie may pick things up or break doors");
        h.assertTrue(EventHooks.canEntityGrief(h.getLevel(), control) == h.getLevel().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING),
                "a wild zombie's griefing changed");
        done(h, p);
    }

    // --- operators --------------------------------------------------------------------------------------------------

    @GameTest(template = "platform32")
    public static void operatorsListRecallAndPauseStaff(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(10, 6, 16));
        Zombie z = hire(h, p, EntityType.ZOMBIE, new BlockPos(16, 6, 18));
        BlockPos at = new BlockPos(16, 6, 16);
        h.setBlock(at, WorkRegistry.TIME_CLOCK.get());
        TimeClockBlockEntity clock = (TimeClockBlockEntity) h.getBlockEntity(at);
        clock.setOwner(p);
        h.assertTrue(clock.join(z), "the clock would not take the zombie");
        WorkerData d = Workforce.data(z);
        // as the Whistle would leave it: on a detail
        d.state = WorkerData.State.DETAIL;
        d.detail = p.getUUID();
        var server = h.getLevel().getServer();
        var source = server.createCommandSourceStack().withSuppressedOutput();
        var commands = server.getCommands().getDispatcher();
        // a selector names the player (the game-test server keeps no profile cache to look a bare name up in)
        String who = "@a[name=" + p.getGameProfile().getName() + "]";
        try {
            h.assertTrue(commands.execute("headoffice staff list " + who, source) >= 1, "staff list showed nobody");
            h.assertTrue(commands.execute("headoffice staff recall " + who, source) == 1, "recall recalled nobody");
            h.assertTrue(d.state == WorkerData.State.TO_CLOCK && d.detail == null, "the recalled zombie is not on its way to its clock");
            boolean was = WorkPause.get(server).paused();
            // the pause for the length of this call only (tests share the server)
            commands.execute("headoffice staff pause", source);
            boolean on = WorkPause.get(server).paused() && Workforce.paused;
            commands.execute("headoffice staff pause", source);
            h.assertTrue(on != was && WorkPause.get(server).paused() == was && Workforce.paused == was, "the pause did not toggle in the world's data");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            h.fail("a staff command did not parse: " + e.getMessage());
        }
        done(h, p);
    }

    // --- old worlds -------------------------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void vanillaMobsInAnOldWorldAreNotStaff(GameTestHelper h) {
        // a zombie and a villager as an older save holds them: written out and read back, with no worker record
        Zombie made = h.spawn(EntityType.ZOMBIE, FLOOR);
        CompoundTag tag = new CompoundTag();
        made.save(tag);
        made.discard();
        tag.remove("UUID");
        Zombie z = (Zombie) EntityType.create(tag, h.getLevel()).orElseThrow();
        h.getLevel().addFreshEntity(z);
        Villager v = h.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 10));
        h.assertTrue(!Workforce.isWorker(z) && !Workforce.isWorker(v), "a creature from an old world is staff");
        h.assertTrue(!z.canBeLeashed() && !v.canBeLeashed(), "vanilla's lead rule changed for creatures that are not staff");
        h.assertTrue(!WorkRules.sheltered(z), "a wild zombie is sheltered from the sun");
        h.assertTrue(FriendOrFoe.mayTarget(null, h.getLevel(), z) && !FriendOrFoe.mayTarget(null, h.getLevel(), v), "the turret's rule changed for wild creatures");
        IronGolem golem = h.spawn(EntityType.IRON_GOLEM, new BlockPos(4, 2, 4));
        golem.setTarget(z);
        h.assertTrue(golem.getTarget() == z, "an iron golem no longer picks a wild zombie");
        h.assertTrue(EventHooks.canEntityGrief(h.getLevel(), z) == h.getLevel().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING),
                "a wild zombie's griefing changed");
        h.succeed();
    }
}
