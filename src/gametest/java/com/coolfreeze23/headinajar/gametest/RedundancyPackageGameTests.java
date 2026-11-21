package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.JarredHeadBlockEntity;
import com.coolfreeze23.headinajar.entity.RedundancyPackageEntity;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.JarredHeadItem;
import com.coolfreeze23.headinajar.item.SurgicalSawItem;
import com.coolfreeze23.headinajar.mecha.HopperNet;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.registry.SecondRegistry;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffEvents;
import com.coolfreeze23.headinajar.staff.StaffFile;
import com.coolfreeze23.headinajar.staff.second.RedundancyPackage;
import com.coolfreeze23.headinajar.staff.second.SecondeeData;
import com.coolfreeze23.headinajar.staff.second.Secondees;
import com.coolfreeze23.headinajar.staff.second.Secondment;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.function.Consumer;

/**
 * The Redundancy Package (plan 5.1 #10-#12): a seconded head survives every death of its body (one test per cause, so a
 * failure names it) and lands within 200 ticks as a placed Jarred Head (the item, in water) with its file kept plus one
 * line, the advancement given; it is lost only with no glass left, in the void, or coming down in lava (the Exit
 * Interview and the card). A package under a low ceiling tangles and still lands; one in flight survives a save. And
 * the jar's glass: what lands on the jar and what is the body's, and what a creeper 2 blocks off leaves of it. Every
 * test has the sky open over it (a test's own barrier roof would be a ceiling, and every package would tangle).
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class RedundancyPackageGameTests {
    /** Where the host stands (the platform's grass is its y 0). */
    private static final BlockPos AT = new BlockPos(7, 1, 7);

    // --- set-up -------------------------------------------------------------------------------------------------------

    /** A secondee made the real way: the saw, the collar and a zombie's jarred head, each used by the employer. */
    private static Mob secondee(GameTestHelper h, ServerPlayer boss, EntityType<? extends Mob> body, BlockPos at) {
        Mob mob = h.spawn(body, at);
        mob.setHealth(1);
        h.assertTrue(SurgicalSawItem.saw(boss, new ItemStack(ModRegistry.SURGICAL_SAW.get()), mob, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME,
                "the saw would not make a host of " + body);
        h.assertTrue(Hosts.isHost(mob), "no host");
        use(boss, new ItemStack(SecondRegistry.SECONDMENT_COLLAR.get()), mob);
        Zombie z = EntityType.ZOMBIE.create(h.getLevel());
        use(boss, JarredHeadItem.of(z, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get())), mob);
        h.assertTrue(Secondees.seated(mob), "the head did not seat on the " + body);
        mob.setHealth(mob.getMaxHealth());
        return mob;
    }

    private static void use(ServerPlayer p, ItemStack stack, Mob host) {
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Secondment.instrument(p, p, p.getMainHandItem(), host, InteractionHand.MAIN_HAND);
    }

    private static ServerPlayer boss(GameTestHelper h) {
        return TestPlayers.at(h, "pkg", AT.south(5));
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    private static boolean earned(GameTestHelper h, ServerPlayer p, String id) {
        AdvancementHolder holder = h.getLevel().getServer().getAdvancements().get(HeadInAJar.id(id));
        if (holder == null) throw new GameTestAssertException("no advancement " + id);
        return p.getAdvancements().getOrStartProgress(holder).isDone();
    }

    /** The seated head as it should come back: its file and its stored creature, taken just before the body dies. */
    private record Seated(StaffFile file, CompoundTag entity) {
        static Seated of(Mob host) {
            SecondeeData d = Secondees.data(host);
            return new Seated(StaffFile.load(Staff.of(host).save()), Secondees.entityTag(d.head).copy());
        }
    }

    /** The test area, and the sky above it the packages fly in. */
    private static AABB area(GameTestHelper h) {
        return h.getBounds().inflate(6, 24, 6);
    }

    /**
     * Kills the body by {@code death} and then, within 200 ticks, a placed Jarred Head (or, {@code water}, the item)
     * carries the seated head's file with one more line ("redundancy", the body's kind) and its stored creature; the
     * employer has the advancement.
     */
    private static void survives(GameTestHelper h, ServerPlayer boss, Mob host, boolean water, Consumer<Mob> death) {
        Seated seated = Seated.of(host);
        String body = host.getType().getDescriptionId();
        long start = h.getTick();
        death.accept(host);
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() - start <= 200, "nothing landed within 200 ticks");
            StaffFile f;
            CompoundTag entity;
            if (water) {
                ItemEntity item = item(h, key(seated.file()));
                if (item == null) throw new GameTestAssertException("no Jarred Head item yet");
                f = Staff.of(item.getItem());
                entity = Secondees.entityTag(item.getItem());
            } else {
                JarredHeadBlockEntity block = placed(h, key(seated.file()));
                if (block == null) throw new GameTestAssertException("no placed Jarred Head yet");
                f = block.staff();
                entity = block.record();
            }
            h.assertTrue(mine(f, key(seated.file())), "the landed head is not the seated one's file");
            List<StaffFile.Entry> was = seated.file().history, now = f.history;
            h.assertTrue(now.size() >= 1 && now.get(now.size() - 1).k().equals("redundancy") && now.get(now.size() - 1).a().equals(body),
                    "its last history line is not the redundancy: " + now);
            int keep = Math.min(was.size(), now.size() - 1);
            h.assertTrue(now.subList(now.size() - 1 - keep, now.size() - 1).equals(was.subList(was.size() - keep, was.size())), "its earlier history changed");
            h.assertTrue(f.left == null, "the head was let go");
            h.assertTrue(entity.equals(seated.entity()), "the stored creature changed");
            h.assertTrue(earned(h, boss, "redundancy"), "no Redundancy Package advancement");
            done(h, boss);
        });
    }

    /** The file with this {@link #key}. */
    private static boolean mine(StaffFile f, long key) {
        return f != null && f.employer != null && key(f) == key;
    }

    /** A file's key: its number and its employer (every employer's numbers start at 1, and tests run side by side). */
    private static long key(StaffFile f) {
        return f.employer.getMostSignificantBits() * 31 + f.number;
    }

    /** The placed Jarred Head with this file (tests run side by side: never a neighbour's), or null. */
    private static JarredHeadBlockEntity placed(GameTestHelper h, long key) {
        AABB a = area(h);
        for (BlockPos p : BlockPos.betweenClosed(Mth.floor(a.minX), Mth.floor(h.getBounds().minY), Mth.floor(a.minZ),
                Mth.floor(a.maxX), Mth.floor(h.getBounds().maxY) + 4, Mth.floor(a.maxZ))) {
            if (h.getLevel().getBlockEntity(p) instanceof JarredHeadBlockEntity b && mine(b.staff(), key)) return b;
        }
        return null;
    }

    /** The Jarred Head item lying about with this file, or null. */
    private static ItemEntity item(GameTestHelper h, long key) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, area(h), i -> i.getItem().is(ModRegistry.JARRED_HEAD.get())
                && mine(Staff.of(i.getItem()), key)).stream().findFirst().orElse(null);
    }

    /** The package carrying this file (the head while it flies; once down it carries nothing), or null. */
    private static RedundancyPackageEntity flying(GameTestHelper h, long key) {
        return h.getLevel().getEntitiesOfClass(RedundancyPackageEntity.class, area(h), e -> mine(Staff.of(e.head()), key)).stream().findFirst().orElse(null);
    }

    /** Kills the body by {@code death} with glass {@code glass}; then the head is lost: the Exit Interview gives {@code why}. */
    private static void lost(GameTestHelper h, ServerPlayer boss, Mob host, String why, boolean card, Consumer<Mob> death) {
        int number = Staff.of(host).number;
        long key = key(Staff.of(host));
        String exit = boss.getUUID() + "|" + why + "|" + number;
        death.accept(host);
        h.succeedWhen(() -> {
            h.assertTrue(StaffEvents.exits().contains(exit), "no Exit Interview " + why + " for the head");
            h.assertTrue(placed(h, key) == null, "the head was placed");
            h.assertTrue(item(h, key) == null, "the head was left as an item");
            h.assertTrue(flying(h, key) == null, "the head is still flying");
            if (card) h.assertTrue(!h.getLevel().getEntitiesOfClass(ItemEntity.class, area(h), i -> i.getItem().is(ModRegistry.CONDOLENCE_CARD.get())).isEmpty(),
                    "no Condolence Card");
            done(h, boss);
        });
    }

    private static void fill(GameTestHelper h, int x0, int y0, int z0, int x1, int y1, int z1, net.minecraft.world.level.block.Block block) {
        for (BlockPos p : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) h.setBlock(p, block);
    }

    // --- every death of the body (plan 5.1 #10) -----------------------------------------------------------------------

    @GameTest(template = "platform", skyAccess = true, timeoutTicks = 260)
    public static void aSecondedHeadSurvivesAZombieAttack(GameTestHelper h) {
        ServerPlayer boss = boss(h);
        Mob host = secondee(h, boss, EntityType.COW, AT);
        Zombie zombie = h.spawn(EntityType.ZOMBIE, AT.east(1));
        survives(h, boss, host, false, m -> {
            m.setHealth(1);
            zombie.doHurtTarget(m);
        });
    }

    @GameTest(template = "platform", skyAccess = true, timeoutTicks = 260)
    public static void aSecondedHeadSurvivesAPlayerSword(GameTestHelper h) {
        ServerPlayer boss = boss(h);
        Mob host = secondee(h, boss, EntityType.COW, AT);
        ServerPlayer other = TestPlayers.at(h, "sword", AT.west(2));
        other.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_SWORD));
        survives(h, boss, host, false, m -> {
            m.setHealth(1);
            other.attack(m);
            h.getLevel().getServer().getPlayerList().remove(other);
        });
    }

    @GameTest(template = "platform", skyAccess = true, timeoutTicks = 260)
    public static void aSecondedHeadSurvivesAnArrow(GameTestHelper h) {
        ServerPlayer boss = boss(h);
        Mob host = secondee(h, boss, EntityType.COW, AT);
        survives(h, boss, host, false, m -> {
            m.setHealth(1);
            Arrow arrow = EntityType.ARROW.create(h.getLevel());
            arrow.setPos(m.getX() - 3, m.getY() + 0.5, m.getZ());
            arrow.shoot(1, 0.05, 0, 2.5f, 0);
            h.getLevel().addFreshEntity(arrow);
        });
    }

    @GameTest(template = "platform", skyAccess = true, timeoutTicks = 300)
    public static void aSecondedHeadSurvivesAFallFrom20(GameTestHelper h) {
        ServerPlayer boss = boss(h);
        Mob host = secondee(h, boss, EntityType.COW, AT);
        survives(h, boss, host, false, m -> m.teleportTo(m.getX(), m.getY() + 20, m.getZ()));
    }

    @GameTest(template = "platform", skyAccess = true, timeoutTicks = 260)
    public static void aSecondedHeadSurvivesFire(GameTestHelper h) {
        ServerPlayer boss = boss(h);
        Mob host = secondee(h, boss, EntityType.COW, AT);
        survives(h, boss, host, false, m -> {
            m.setHealth(1);
            m.igniteForSeconds(10);
        });
    }

    @GameTest(template = "platform", skyAccess = true, timeoutTicks = 260)
    public static void aSecondedHeadSurvivesDrowning(GameTestHelper h) {
        ServerPlayer boss = boss(h);
        // a glass well three deep, full of water, the host at its bottom
        fill(h, 5, 1, 5, 9, 3, 9, Blocks.GLASS);
        fill(h, 6, 1, 6, 8, 3, 8, Blocks.WATER);
        Mob host = secondee(h, boss, EntityType.COW, AT);
        survives(h, boss, host, true, m -> {
            m.setHealth(1);
            m.setAirSupply(-19);
        });
    }

    @GameTest(template = "platform", skyAccess = true, timeoutTicks = 260)
    public static void aSecondedHeadSurvivesSuffocationInSand(GameTestHelper h) {
        ServerPlayer boss = boss(h);
        Mob host = secondee(h, boss, EntityType.COW, AT);
        survives(h, boss, host, false, m -> {
            m.setHealth(1);
            fill(h, 7, 1, 7, 7, 2, 7, Blocks.SAND);
        });
    }

    @GameTest(template = "platform", skyAccess = true, timeoutTicks = 260)
    public static void aSecondedHeadSurvivesACactus(GameTestHelper h) {
        ServerPlayer boss = boss(h);
        Mob host = secondee(h, boss, EntityType.COW, AT);
        survives(h, boss, host, false, m -> {
            m.setHealth(1);
            h.setBlock(AT.below(), Blocks.SAND);
            h.setBlock(AT, Blocks.CACTUS);
        });
    }

    @GameTest(template = "platform", skyAccess = true, timeoutTicks = 260)
    public static void aSecondedHeadSurvivesAnExplosion(GameTestHelper h) {
        ServerPlayer boss = boss(h);
        Mob host = secondee(h, boss, EntityType.COW, AT);
        survives(h, boss, host, false, m -> {
            m.setHealth(1);
            h.getLevel().explode(null, m.getX() + 1, m.getY() + 0.5, m.getZ(), 3, Level.ExplosionInteraction.NONE);
        });
    }

    @GameTest(template = "platform", skyAccess = true, timeoutTicks = 260)
    public static void aSecondedHeadSurvivesRottingAway(GameTestHelper h) {
        ServerPlayer boss = boss(h);
        Mob host = secondee(h, boss, EntityType.COW, AT);
        survives(h, boss, host, false, m -> Hosts.data(m).freshness = 0.0001f);
    }

    @GameTest(template = "platform", skyAccess = true, timeoutTicks = 260)
    public static void aSecondedHeadSurvivesKillOfItsBody(GameTestHelper h) {
        ServerPlayer boss = boss(h);
        Mob host = secondee(h, boss, EntityType.COW, AT);
        survives(h, boss, host, false, Mob::kill);
    }

    /** A 5 x 5 pool of lava three deep inside a stone rim: the body dies in it, the head lands on the rim. */
    @GameTest(template = "platform", skyAccess = true, timeoutTicks = 260)
    public static void aSecondedHeadSurvivesDeathInALavaPool(GameTestHelper h) {
        ServerPlayer boss = boss(h);
        fill(h, 4, 1, 4, 10, 3, 10, Blocks.STONE);
        Mob host = secondee(h, boss, EntityType.COW, AT.above(3));
        survives(h, boss, host, false, m -> {
            fill(h, 5, 1, 5, 9, 3, 9, Blocks.LAVA);
            m.teleportTo(m.getX(), h.absolutePos(AT).getY(), m.getZ());
            m.setHealth(1);
        });
    }

    // --- the three losses ---------------------------------------------------------------------------------------------

    @GameTest(template = "platform", skyAccess = true, timeoutTicks = 100)
    public static void aSecondedHeadWithNoGlassIsLostWithItsBody(GameTestHelper h) {
        ServerPlayer boss = boss(h);
        Mob host = secondee(h, boss, EntityType.COW, AT);
        Secondees.data(host).glass = 0;
        lost(h, boss, host, "staff.headinajar.reason.other", true, Mob::kill);
    }

    @GameTest(template = "platform", skyAccess = true, timeoutTicks = 100)
    public static void aSecondedHeadIsLostInTheVoid(GameTestHelper h) {
        ServerPlayer boss = boss(h);
        Mob host = secondee(h, boss, EntityType.COW, AT);
        lost(h, boss, host, RedundancyPackage.VOID, false, m -> {
            m.setHealth(1);
            m.teleportTo(m.getX(), h.getLevel().getMinBuildHeight() - 70, m.getZ());
        });
    }

    /** An 11 x 11 lava sea: no bank within the 9 x 9 look, so the package goes straight up and comes down in lava. */
    @GameTest(template = "platform", skyAccess = true, timeoutTicks = 260)
    public static void aSecondedHeadIsLostComingDownInLava(GameTestHelper h) {
        ServerPlayer boss = boss(h);
        fill(h, 1, 1, 1, 13, 1, 13, Blocks.STONE);
        Mob host = secondee(h, boss, EntityType.COW, AT.above(1));
        lost(h, boss, host, RedundancyPackage.LAVA, false, m -> {
            fill(h, 2, 1, 2, 12, 1, 12, Blocks.LAVA);
            m.teleportTo(m.getX(), h.absolutePos(AT).getY(), m.getZ());
            m.setHealth(1);
        });
    }

    // --- the flight (plan 5.1 #11, #12) -------------------------------------------------------------------------------

    /** Two blocks of air over the body: the canopy tangles, the package drops straight down, and the head still lands. */
    @GameTest(template = "platform", skyAccess = true, timeoutTicks = 200)
    public static void thePackageIsTangledUnderALowCeiling(GameTestHelper h) {
        ServerPlayer boss = boss(h);
        fill(h, 4, 3, 4, 10, 3, 10, Blocks.STONE);
        Mob host = secondee(h, boss, EntityType.COW, AT);
        Vec3 jar = RedundancyPackage.jar(host);
        long number = key(Staff.of(host));
        host.kill();
        RedundancyPackageEntity pkg = flying(h, number);
        h.assertTrue(pkg != null, "no package");
        h.assertTrue(pkg.state() == HopperNet.TANGLED, "the canopy opened under a two-block ceiling (state " + pkg.state() + ")");
        h.succeedWhen(() -> {
            JarredHeadBlockEntity block = placed(h, number);
            if (block == null) throw new GameTestAssertException("no placed head yet");
            BlockPos p = block.getBlockPos();
            h.assertTrue(p.getX() == Mth.floor(jar.x) && p.getZ() == Mth.floor(jar.z), "it did not drop straight down: " + p + " under " + jar);
            done(h, boss);
        });
    }

    /** A package in flight saved and loaded again (as its chunk would be) still lands, and its head with it. */
    @GameTest(template = "platform", skyAccess = true, timeoutTicks = 360)
    public static void aPackageInFlightSurvivesASave(GameTestHelper h) {
        ServerPlayer boss = boss(h);
        fill(h, 7, 1, 7, 7, 5, 7, Blocks.STONE);
        Mob host = secondee(h, boss, EntityType.COW, AT.above(5));
        long number = key(Staff.of(host));
        Vec3 body = host.position();
        host.kill();
        h.assertTrue(flying(h, number) != null, "no package");
        h.runAfterDelay(40, () -> {
            RedundancyPackageEntity pkg = flying(h, number);
            h.assertTrue(pkg != null && !pkg.down(), "no package in flight at tick 40 (" + RedundancyPackage.events() + ")");
            h.assertTrue(pkg.state() == HopperNet.OPEN, "the canopy is not open at tick 40");
            h.assertTrue(pkg.shouldBeSaved(), "a package in flight would not be saved with its chunk");
            CompoundTag saved = new CompoundTag();
            h.assertTrue(pkg.save(saved), "the package did not save");
            pkg.discard();
            var again = EntityType.loadEntityRecursive(saved, h.getLevel(), e -> e);
            h.assertTrue(again instanceof RedundancyPackageEntity p && !p.head().isEmpty() && p.state() == HopperNet.OPEN, "it did not load as it was");
            h.assertTrue(h.getLevel().addFreshEntity(again), "the loaded package could not be added");
            h.succeedWhen(() -> {
                JarredHeadBlockEntity block = placed(h, number);
                if (block == null) throw new GameTestAssertException("no placed head yet");
                // from 6 blocks up it comes down within 4 blocks of the body (plan 4)
                Vec3 down = Vec3.atBottomCenterOf(block.getBlockPos());
                double off = Math.sqrt((down.x - body.x) * (down.x - body.x) + (down.z - body.z) * (down.z - body.z));
                h.assertTrue(off <= 4, "it came down " + off + " blocks from the body");
                done(h, boss);
            });
        });
    }

    /** A head that landed with a cracked jar, broken off its plinth and seated again, has the same glass. */
    @GameTest(template = "platform", skyAccess = true, timeoutTicks = 260)
    public static void aLandedHeadKeepsItsGlass(GameTestHelper h) {
        ServerPlayer boss = boss(h);
        Mob host = secondee(h, boss, EntityType.COW, AT);
        Secondees.data(host).glass = 4;
        long key = key(Staff.of(host));
        host.kill();
        h.succeedWhen(() -> {
            JarredHeadBlockEntity block = placed(h, key);
            if (block == null) throw new GameTestAssertException("no placed head yet");
            BlockPos at = block.getBlockPos();
            h.getLevel().destroyBlock(at, true);
            ItemEntity dropped = item(h, key);
            h.assertTrue(dropped != null, "breaking the plinth dropped no head");
            Mob next = h.spawn(EntityType.COW, AT.east(3));
            next.setHealth(1);
            h.assertTrue(SurgicalSawItem.saw(boss, new ItemStack(ModRegistry.SURGICAL_SAW.get()), next, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME,
                    "no second host");
            use(boss, new ItemStack(SecondRegistry.SECONDMENT_COLLAR.get()), next);
            use(boss, dropped.getItem().copy(), next);
            SecondeeData d = Secondees.data(next);
            h.assertTrue(d != null && d.seated(), "the head did not seat again");
            h.assertTrue(d.glass == 4 && d.glassMax == 10, "it seats with " + (d == null ? "?" : d.glass + "/" + d.glassMax) + " glass, not 4/10");
            dropped.discard();
            done(h, boss);
        });
    }

    // --- the jar's glass ----------------------------------------------------------------------------------------------

    /** What lands on the jar costs 1 glass per 2 damage, rounded up; the rest is the body's. */
    @GameTest(template = "platform", skyAccess = true, timeoutTicks = 100)
    public static void hitsOnTheJarCostGlass(GameTestHelper h) {
        ServerPlayer boss = boss(h);
        Mob host = secondee(h, boss, EntityType.COW, AT);
        SecondeeData d = Secondees.data(host);
        var hits = new Object() {
            void expect(String what, net.minecraft.world.damagesource.DamageSource source, float amount, int glass) {
                int before = d.glass;
                host.invulnerableTime = 0;
                host.setHealth(host.getMaxHealth());
                host.hurt(source, amount);
                h.assertTrue(before - d.glass == glass, what + " took " + (before - d.glass) + " glass, not " + glass);
            }
        };
        var sources = host.damageSources();
        hits.expect("an anvil (3)", sources.anvil(null), 3, 2);
        hits.expect("a falling block (1)", sources.fallingBlock(null), 1, 1);
        hits.expect("an explosion (3)", sources.explosion(null, null), 3, 2);
        Arrow low = EntityType.ARROW.create(h.getLevel());
        low.setPos(host.getX(), host.getY() + 0.5, host.getZ());
        hits.expect("an arrow under the neck", sources.arrow(low, null), 4, 0);
        Arrow high = EntityType.ARROW.create(h.getLevel());
        high.setPos(host.getX(), host.getY() + Hosts.neck(host).y + 0.1, host.getZ());
        hits.expect("an arrow at the jar", sources.arrow(high, null), 2, 1);
        Zombie zombie = h.spawn(EntityType.ZOMBIE, AT.east(1));
        hits.expect("a zombie's blow", sources.mobAttack(zombie), 3, 0);
        // a player looking at the jar, then at the cow's legs
        ServerPlayer other = TestPlayers.at(h, "aim", AT.west(2));
        Vec3 jar = RedundancyPackage.jarBox(host).getCenter();
        look(other, jar);
        hits.expect("a blow aimed at the jar", sources.playerAttack(other), 2, 1);
        look(other, host.position().add(0, 0.2, 0));
        hits.expect("a blow at the legs", sources.playerAttack(other), 2, 0);
        // the glass runs out: nothing below 0
        d.glass = 1;
        hits.expect("an anvil on the last glass", sources.anvil(null), 9, 1);
        h.assertTrue(d.glass == 0, "glass went below 0");
        h.getLevel().getServer().getPlayerList().remove(other);
        done(h, boss);
    }

    private static void look(ServerPlayer p, Vec3 at) {
        Vec3 eye = p.getEyePosition(), to = at.subtract(eye);
        float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90;
        float pitch = (float) -(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG);
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.setYHeadRot(yaw);
    }

    /**
     * A real creeper lit 2 blocks from a secondee (an iron golem body, which lives through it) with a Life Support Jar
     * of 10 glass: the glass left is 10 less 1 per 2 of the blast's damage, rounded up (logged as "[glass number]").
     */
    @GameTest(template = "platform", skyAccess = true, timeoutTicks = 120)
    public static void aCreeperTwoBlocksOffAndTheJarsGlass(GameTestHelper h) {
        ServerPlayer boss = boss(h);
        Mob host = secondee(h, boss, EntityType.IRON_GOLEM, AT);
        SecondeeData d = Secondees.data(host);
        h.assertTrue(d.glass == 10 && d.glassMax == 10, "a Life Support Jar seats with " + d.glass + "/" + d.glassMax + " glass");
        Creeper creeper = h.spawn(EntityType.CREEPER, AT.east(2));
        creeper.setNoAi(true);
        float health = host.getHealth();
        double off = creeper.distanceTo(host);
        creeper.ignite();
        h.succeedWhen(() -> {
            h.assertTrue(creeper.isRemoved(), "the creeper has not gone off yet");
            float damage = health - host.getHealth();
            int expected = Math.max(0, 10 - Mth.ceil(damage / 2));
            System.out.printf("[glass number] creeper %.2f blocks off: the body took %.1f damage, glass 10 -> %d%n", off, damage, d.glass);
            h.assertTrue(damage > 0 && host.isAlive(), "the golem took no damage, or died");
            h.assertTrue(d.glass == expected, "glass " + d.glass + ", not " + expected + " for " + damage + " damage");
            done(h, boss);
        });
    }
}
