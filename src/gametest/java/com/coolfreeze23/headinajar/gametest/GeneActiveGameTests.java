package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.fx.Fx;
import com.coolfreeze23.headinajar.genetics.Gene;
import com.coolfreeze23.headinajar.genetics.GeneActives;
import com.coolfreeze23.headinajar.genetics.GeneEffects;
import com.coolfreeze23.headinajar.genetics.GeneNumbers;
import com.coolfreeze23.headinajar.genetics.Genetics;
import com.coolfreeze23.headinajar.genetics.Serum;
import com.coolfreeze23.headinajar.item.MechaModuleItem;
import com.coolfreeze23.headinajar.item.SurgicalSawItem;
import com.coolfreeze23.headinajar.mecha.Chimera;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The 0.6.2 actives (Phase, Snow Pounce, Tongue, Spit Gland, Fang Line, Levitation Bolt) and the two heavy passives
 * (Bulldoze, Weeping Glands): each fires, sets its cooldown and refuses with its line; the numbers the plan names; the
 * Chimera's Graft leaving load 4 and up alone; every one of the eight on a cow host for 200 ticks.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class GeneActiveGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);

    private static ServerPlayer player(GameTestHelper h, BlockPos at) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.spawnInvulnerableTime = 0;
        Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(at));
        player.moveTo(v.x, v.y, v.z, 0, 0);
        return player;
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    /** A random source whose float rolls are always {@code roll} (no rejection at 0.5). */
    private static LegacyRandomSource rolls(float roll) {
        return new LegacyRandomSource(1) {
            @Override
            public float nextFloat() {
                return roll;
            }
        };
    }

    private static Serum serum(Gene... genes) {
        return new Serum(java.util.Arrays.stream(genes).map(g -> new Serum.Entry(g, 1f)).toList(), 100);
    }

    private static void give(GameTestHelper h, LivingEntity body, Genetics.Target target, Gene... genes) {
        h.assertTrue(Genetics.inject(body, serum(genes), target, rolls(0.5f)) == Genetics.Result.OK, "setup: the serum did not go in " + List.of(genes));
        h.assertTrue(GeneEffects.has(body, genes[0]), "setup: " + genes[0] + " does not work");
    }

    /** A sawn host at full health, facing south. */
    private static <T extends Mob> T host(GameTestHelper h, EntityType<T> type, BlockPos at, ServerPlayer sawyer) {
        T mob = h.spawn(type, at);
        mob.setHealth(1);
        h.assertTrue(SurgicalSawItem.saw(sawyer, new ItemStack(ModRegistry.SURGICAL_SAW.get()), mob, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME,
                "setup: the saw failed on " + type);
        mob.setHealth(mob.getMaxHealth());
        face(mob, 0);
        return mob;
    }

    private static void face(LivingEntity e, float yaw) {
        e.setYRot(yaw);
        e.setYHeadRot(yaw);
        e.setYBodyRot(yaw);
        e.yBodyRotO = yaw;
        e.setXRot(0);
    }

    /** {@code p}, as a jar, rides {@code host}, looking south. */
    private static void ride(GameTestHelper h, ServerPlayer p, Mob host) {
        FormActions.detach(p, 3);
        h.assertTrue(FormActions.mountHost(p, host) && FormActions.state(p).form() == Form.HOSTED, "setup: could not ride the host");
        face(p, 0);
    }

    private static void wall(GameTestHelper h, BlockPos from, int thick, Block block) {
        for (int z = 0; z < thick; z++) {
            for (int x = -2; x <= 2; x++) for (int y = 0; y < 3; y++) h.setBlock(from.offset(x, y, z), block);
        }
    }

    /** A protection mod's claim over {@code box} (absolute): every break posted inside it is cancelled. */
    private static Consumer<BlockEvent.BreakEvent> claim(AABB box) {
        Consumer<BlockEvent.BreakEvent> c = e -> {
            if (box.contains(Vec3.atCenterOf(e.getPos()))) e.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(c);
        return c;
    }

    private static void expect(GameTestHelper h, String want, String got, String what) {
        h.assertTrue(java.util.Objects.equals(want, got), what + ": " + got);
    }

    private static String fire(LivingEntity body, LivingEntity aim) {
        return GeneEffects.fire(body, aim, 0);
    }

    // --- Phase -------------------------------------------------------------------------------------------------------

    /**
     * A cow host phases south through two blocks of stone into the open beyond: 5 blocks, 2 health, a 6 s cooldown, the
     * trail memo. Before that, refused: bedrock goes on forever, four blocks is no room, a claimed wall is protected.
     */
    @GameTest(template = "platform")
    public static void phaseGoesThroughTwoBlocksAndRefusesWithItsLines(GameTestHelper h) {
        ServerPlayer sawyer = player(h, FLOOR.west(3)), p = player(h, FLOOR.east(3));
        Cow cow = host(h, EntityType.COW, FLOOR, sawyer);
        give(h, cow, Genetics.Target.HOST, Gene.PHASE);
        ride(h, p, cow);
        BlockPos front = FLOOR.south(2);
        wall(h, front, 1, Blocks.BEDROCK);
        expect(h, "gene.phase.forever", fire(cow, p), "bedrock did not go on forever");
        wall(h, front, 4, Blocks.STONE);
        expect(h, "gene.phase.no_room", fire(cow, p), "four blocks of stone were phased");
        wall(h, front, 4, Blocks.AIR);
        wall(h, front, 2, Blocks.STONE);
        Consumer<BlockEvent.BreakEvent> claim = claim(new AABB(h.absolutePos(front)).inflate(3));
        try {
            expect(h, "gene.phase.claim", fire(cow, p), "a claimed wall was phased");
        } finally {
            NeoForge.EVENT_BUS.unregister(claim);
        }
        h.assertTrue(GeneEffects.cooldown(cow, 0) == 0, "a refusal started the cooldown");
        double z0 = cow.getZ();
        float health = cow.getHealth();
        int trails = Fx.count(Fx.GENE_PHASE, cow.getId());
        expect(h, null, fire(cow, p), "the phase did not fire");
        double moved = cow.getZ() - z0;
        h.assertTrue(moved > 3.9 && moved <= GeneNumbers.PHASE_REACH + 1e-3, "phased " + moved + " blocks, not through the wall");
        h.assertTrue(h.getLevel().noCollision(cow, cow.getBoundingBox()), "arrived inside the wall");
        h.assertTrue(Math.abs(health - cow.getHealth() - GeneNumbers.PHASE_HOST_COST) < 1e-3, "the host paid " + (health - cow.getHealth()));
        h.assertTrue(GeneEffects.cooldown(cow, 0) == GeneNumbers.PHASE_COOLDOWN, "cooldown " + GeneEffects.cooldown(cow, 0));
        h.assertTrue(Fx.count(Fx.GENE_PHASE, cow.getId()) == trails + 1, "no trail memo");
        h.assertTrue("gene.cooling".equals(fire(cow, p)), "phased again at once");
        done(h, sawyer, p);
    }

    // --- Snow Pounce -------------------------------------------------------------------------------------------------

    /** A body pounces at the floor five blocks ahead: thrown up and forward, no fall damage meanwhile, 4 s cooldown (8 s by day in the open). */
    @GameTest(template = "platform")
    public static void snowPounceLeapsAtTheBlockLookedAt(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.north(3));
        give(h, p, Genetics.Target.BODY, Gene.SNOW_POUNCE);
        p.lookAt(EntityAnchorArgument.Anchor.EYES, h.absoluteVec(Vec3.atBottomCenterOf(FLOOR.north(3).south(5))));
        expect(h, null, fire(p, p), "the pounce did not fire");
        Vec3 v = p.getDeltaMovement();
        h.assertTrue(v.z > 0.1 && v.y > 0.1, "not thrown up and forward: " + v);
        h.assertTrue(GeneActives.pouncing(p), "not pouncing");
        p.fallDistance = 6;
        float health = p.getHealth();
        p.causeFallDamage(6, 1, h.getLevel().damageSources().fall());
        h.assertTrue(p.getHealth() == health, "the pounce's fall hurt");
        boolean day = h.getLevel().isDay() && h.getLevel().canSeeSky(BlockPos.containing(p.getEyePosition()));
        int want = day ? GeneNumbers.POUNCE_COOLDOWN_DAY : GeneNumbers.POUNCE_COOLDOWN;
        h.assertTrue(GeneEffects.cooldown(p, 0) == want, "cooldown " + GeneEffects.cooldown(p, 0) + ", want " + want);
        done(h, p);
    }

    /**
     * The leap flown tick by tick as the game moves a body that takes off from grass: the first tick still on the ground
     * (its grip 0.6 x 0.91 on the run), then the air (0.91, gravity 0.08, drag 0.98). It lands where it was aimed, at 4, 6
     * and 8 blocks (it used to come down at about two thirds of the way).
     */
    @GameTest(template = "platform")
    public static void aPounceLandsWhereItWasAimed(GameTestHelper h) {
        for (double reach : new double[]{4, 6, 8}) {
            int t = 6 + (int) Math.ceil(reach);
            Vec3 v = GeneActives.launch(new Vec3(0, 0, reach), t, 0.6 * 0.91);
            double z = 0, y = 0, vz = v.z, vy = v.y;
            for (int i = 0; i < t; i++) {
                z += vz;
                y += vy;
                vz *= i == 0 ? 0.6 * 0.91 : 0.91;
                vy = (vy - 0.08) * 0.98;
            }
            h.assertTrue(Math.abs(z - reach) < 0.05 && Math.abs(y) < 0.05, "aimed " + reach + ", landed at " + z + " (height " + y + ")");
        }
        h.succeed();
    }

    /** Looking straight up under a high ceiling, nothing is in reach: the pounce is refused and does not cool down. */
    @GameTest(template = "tall_platform")
    public static void snowPounceWithNothingInReachIsRefused(GameTestHelper h) {
        ServerPlayer q = player(h, FLOOR);
        give(h, q, Genetics.Target.BODY, Gene.SNOW_POUNCE);
        q.setXRot(-90);
        expect(h, "gene.no_target", fire(q, q), "pounced at the ceiling");
        h.assertTrue(GeneEffects.cooldown(q, 0) == 0 && !GeneActives.pouncing(q), "a refused pounce went off");
        done(h, q);
    }

    /** Six pillagers round the landing: each takes 2.1 from a body (3 from a mecha), is knocked back and slowed (IV, 1 s); none dies. */
    @GameTest(template = "platform")
    public static void snowPounceLandsOnSixPillagers(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        give(h, p, Genetics.Target.BODY, Gene.SNOW_POUNCE);
        List<Pillager> raid = ring(h, FLOOR);
        h.assertTrue(GeneActives.land(p, h.getLevel()) == 6, "not six caught");
        for (Pillager pillager : raid) {
            h.assertTrue(pillager.isAlive() && Math.abs(pillager.getMaxHealth() - pillager.getHealth() - 2.1f) < 1e-3,
                    "a pillager took " + (pillager.getMaxHealth() - pillager.getHealth()));
            MobEffectInstance slow = pillager.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
            h.assertTrue(slow != null && slow.getAmplifier() == 3 && slow.getDuration() <= GeneNumbers.POUNCE_SLOW_TICKS, "not Slowness IV for a second: " + slow);
            Vec3 away = pillager.position().subtract(p.position()).multiply(1, 0, 1);
            h.assertTrue(pillager.getDeltaMovement().multiply(1, 0, 1).dot(away) > 0, "not knocked back");
        }
        for (Pillager pillager : raid) pillager.discard();
        MechaEntity chimera = h.spawn(ModRegistry.CHIMERA.get(), FLOOR.east(4));
        give(h, chimera, Genetics.Target.MECHA, Gene.SNOW_POUNCE);
        List<Pillager> second = ring(h, FLOOR.east(4));
        h.assertTrue(GeneActives.land(chimera, h.getLevel()) >= 6, "the mecha caught fewer than six");
        for (Pillager pillager : second) {
            h.assertTrue(Math.abs(pillager.getMaxHealth() - pillager.getHealth() - 3f) < 1e-3, "a mecha's pounce did " + (pillager.getMaxHealth() - pillager.getHealth()));
        }
        done(h, p);
    }

    private static List<Pillager> ring(GameTestHelper h, BlockPos centre) {
        List<Pillager> out = new ArrayList<>();
        Vec3 c = Vec3.atBottomCenterOf(centre);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            Pillager pillager = h.spawn(EntityType.PILLAGER, c.add(Math.cos(a) * 1.2, 0, Math.sin(a) * 1.2));
            pillager.setNoAi(true);
            out.add(pillager);
        }
        return out;
    }

    // --- Tongue ------------------------------------------------------------------------------------------------------

    /** The tongue brings an item, pulls a chicken, eats a small magma cube into a froglight; refuses a cow and a colleague. */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void theTongueLicksAndRefuses(GameTestHelper h) {
        ServerPlayer[] p = new ServerPlayer[5];
        for (int i = 0; i < 5; i++) {
            p[i] = player(h, new BlockPos(2 + i * 2, 2, 3));
            give(h, p[i], Genetics.Target.BODY, Gene.TONGUE);
        }
        Vec3 itemAt = h.absoluteVec(new Vec3(2.5, 2, 7.5));
        ItemEntity apple = new ItemEntity(h.getLevel(), itemAt.x, itemAt.y, itemAt.z, new ItemStack(Items.APPLE));
        apple.setDeltaMovement(Vec3.ZERO);
        h.getLevel().addFreshEntity(apple);
        Chicken chicken = h.spawn(EntityType.CHICKEN, new BlockPos(4, 2, 7));
        chicken.setNoAi(true);
        MagmaCube cube = h.spawn(EntityType.MAGMA_CUBE, new BlockPos(6, 2, 7));
        cube.setSize(1, true);
        cube.setNoAi(true);
        Cow cow = h.spawn(EntityType.COW, new BlockPos(8, 2, 7));
        cow.setNoAi(true);
        ServerPlayer colleague = player(h, new BlockPos(10, 2, 7));
        h.runAfterDelay(10, () -> {
            p[0].lookAt(EntityAnchorArgument.Anchor.EYES, apple.position().add(0, 0.12, 0));
            p[1].lookAt(EntityAnchorArgument.Anchor.EYES, chicken.getBoundingBox().getCenter());
            p[2].lookAt(EntityAnchorArgument.Anchor.EYES, cube.getBoundingBox().getCenter());
            p[3].lookAt(EntityAnchorArgument.Anchor.EYES, cow.getBoundingBox().getCenter());
            p[4].lookAt(EntityAnchorArgument.Anchor.EYES, colleague.getBoundingBox().getCenter());
            h.assertTrue(fire(p[0], p[0]) == null, "no lick at the apple");
            h.assertTrue(apple.position().distanceTo(p[0].position()) < 1, "the apple did not come: " + apple.position().distanceTo(p[0].position()));
            h.assertTrue(GeneEffects.cooldown(p[0], 0) == GeneNumbers.TONGUE_COOLDOWN, "cooldown " + GeneEffects.cooldown(p[0], 0));
            h.assertTrue(fire(p[1], p[1]) == null, "no lick at the chicken");
            h.assertTrue(chicken.getDeltaMovement().z < -0.05, "the chicken was not pulled: " + chicken.getDeltaMovement());
            Vec3 at = p[2].position();
            int licks = Fx.count(Fx.GENE_TONGUE, p[2].getId());
            h.assertTrue(fire(p[2], p[2]) == null, "no lick at the magma cube");
            h.assertTrue(Fx.count(Fx.GENE_TONGUE, p[2].getId()) == licks + 1, "no tongue memo");
            h.assertTrue(cube.isRemoved(), "the magma cube was not eaten");
            boolean light = !h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(2), i -> i.getItem().is(Items.OCHRE_FROGLIGHT)
                    || i.getItem().is(Items.PEARLESCENT_FROGLIGHT) || i.getItem().is(Items.VERDANT_FROGLIGHT)).isEmpty();
            h.assertTrue(light, "no froglight for the magma cube");
            expect(h, "gene.tongue.too_big", fire(p[3], p[3]), "a cow was licked");
            expect(h, "gene.tongue.player", fire(p[4], p[4]), "a colleague was licked");
            h.assertTrue(GeneEffects.cooldown(p[3], 0) == 0 && GeneEffects.cooldown(p[4], 0) == 0, "a refusal started the cooldown");
            chicken.discard();
            cow.discard();
            done(h, p[0], p[1], p[2], p[3], p[4], colleague);
        });
    }

    // --- Spit Gland --------------------------------------------------------------------------------------------------

    /** A husk spat at loses interest in you for 4 s; a second spit inside 12 s does not calm it again, so it comes back at 4 s. */
    @GameTest(template = "platform", timeoutTicks = 140)
    public static void aCreatureSpatAtIsCalmedOnceInTwelveSeconds(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.north(3)), q = player(h, FLOOR.north(3).east(2));
        give(h, p, Genetics.Target.BODY, Gene.SPIT_GLAND);
        give(h, q, Genetics.Target.BODY, Gene.SPIT_GLAND);
        Husk husk = h.spawn(EntityType.HUSK, FLOOR);
        husk.setNoAi(true);
        husk.setTarget(p);
        long[] t0 = new long[1];
        h.runAfterDelay(2, () -> {
            p.lookAt(EntityAnchorArgument.Anchor.EYES, husk.getBoundingBox().getCenter());
            t0[0] = h.getLevel().getGameTime();
            h.assertTrue(fire(p, p) == null, "no spit");
            h.assertTrue(GeneEffects.cooldown(p, 0) == GeneNumbers.SPIT_COOLDOWN, "cooldown " + GeneEffects.cooldown(p, 0));
        });
        h.runAfterDelay(12, () -> {
            h.assertTrue(GeneActives.calmed(husk) && husk.getTarget() == null, "the husk was not calmed: target " + husk.getTarget());
            h.assertTrue(husk.getHealth() < husk.getMaxHealth(), "the spit did no damage");
            husk.setTarget(p);
            h.assertTrue(husk.getTarget() == null, "a calm husk took you back as its target");
        });
        h.runAfterDelay(42, () -> {
            q.lookAt(EntityAnchorArgument.Anchor.EYES, husk.getBoundingBox().getCenter());
            h.assertTrue(fire(q, q) == null, "no second spit");
        });
        h.runAfterDelay(2 + GeneNumbers.SPIT_CALM + 4, () -> {
            h.assertTrue(!GeneActives.calmed(husk), "the second spit calmed the husk again");
            husk.setTarget(p);
            h.assertTrue(husk.getTarget() == p, "the husk did not come back after 4 s");
            husk.discard();
            done(h, p, q);
        });
    }

    // --- Fang Line ---------------------------------------------------------------------------------------------------

    /** A cow host casts eight fangs at a pig: 4.2 (a host's 70% of vanilla's 6), a 4 s cooldown; a blue sheep nearby turns red. */
    @GameTest(template = "platform", timeoutTicks = 60)
    public static void theFangLineBitesAtSeventyPercentInAHost(GameTestHelper h) {
        ServerPlayer sawyer = player(h, FLOOR.west(4)), p = player(h, FLOOR.east(4));
        // the line runs 13.5 blocks south of the cow: all eight fangs land on the platform
        Cow cow = host(h, EntityType.COW, FLOOR.north(6), sawyer);
        give(h, cow, Genetics.Target.HOST, Gene.FANG_LINE);
        ride(h, p, cow);
        Pig pig = h.spawn(EntityType.PIG, FLOOR.north(1));
        pig.setNoAi(true);
        Sheep sheep = h.spawn(EntityType.SHEEP, FLOOR.east(5).north(3));
        sheep.setNoAi(true);
        sheep.setColor(DyeColor.BLUE);
        p.lookAt(EntityAnchorArgument.Anchor.EYES, pig.getBoundingBox().getCenter());
        expect(h, null, fire(cow, p), "the fangs did not fire");
        h.assertTrue(GeneEffects.cooldown(cow, 0) == GeneNumbers.FANG_COOLDOWN, "cooldown " + GeneEffects.cooldown(cow, 0));
        int fangs = h.getLevel().getEntitiesOfClass(EvokerFangs.class, cow.getBoundingBox().inflate(16)).size();
        h.assertTrue(fangs == GeneNumbers.FANG_COUNT, fangs + " fangs");
        h.assertTrue(sheep.getColor() == DyeColor.RED, "the blue sheep stayed blue");
        h.runAfterDelay(30, () -> {
            float took = pig.getMaxHealth() - pig.getHealth();
            h.assertTrue(Math.abs(took - 6 * 0.7f) < 1e-3, "the pig took " + took);
            h.assertTrue(cow.getHealth() == cow.getMaxHealth() && p.getHealth() == p.getMaxHealth(), "the fangs bit their own caster");
            pig.discard();
            sheep.discard();
            done(h, sawyer, p);
        });
    }

    /** On a Chimera, Fang Line adds 15% of the frame's heat limit and Levitation Bolt 10%. */
    @GameTest(template = "platform")
    public static void aChimeraPaysItsHeatShare(GameTestHelper h) {
        MechaEntity chimera = h.spawn(ModRegistry.CHIMERA.get(), FLOOR.north(3));
        give(h, chimera, Genetics.Target.MECHA, Gene.FANG_LINE, Gene.LEVITATION_BOLT);
        face(chimera, 0);
        Pig pig = h.spawn(EntityType.PIG, FLOOR.south(3));
        pig.setNoAi(true);
        int cap = chimera.frame().heatCap;
        float before = chimera.heat();
        h.assertTrue(GeneEffects.fire(chimera, chimera, 0) == null, "the fangs did not fire");
        float fangs = chimera.heat() - before;
        h.assertTrue(Math.abs(fangs - cap * GeneNumbers.FANG_HEAT_SHARE) < 0.01f, "fangs added " + fangs + " heat of " + cap);
        chimera.lookAt(EntityAnchorArgument.Anchor.EYES, pig.getBoundingBox().getCenter());
        float mid = chimera.heat();
        expect(h, null, GeneEffects.fire(chimera, chimera, 1), "the bolt did not fire");
        float bolt = chimera.heat() - mid;
        h.assertTrue(Math.abs(bolt - cap * GeneNumbers.BOLT_HEAT_SHARE) < 0.01f, "the bolt added " + bolt + " heat of " + cap);
        pig.discard();
        h.succeed();
    }

    /** A Chimera still booting after its pilot climbs in fires nothing and spends no cooldown; after the boot it fires. */
    @GameTest(template = "platform", timeoutTicks = 120)
    public static void aBootingChimeraWaitsForItsSelfTest(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(4));
        MechaEntity chimera = h.spawn(ModRegistry.CHIMERA.get(), FLOOR.north(3));
        chimera.setOwner(p.getUUID());
        give(h, chimera, Genetics.Target.MECHA, Gene.FANG_LINE);
        face(chimera, 0);
        h.assertTrue(p.startRiding(chimera, true), "the pilot did not climb in");
        expect(h, "gene.booting", GeneEffects.fire(chimera, p, 0), "fired while booting");
        h.assertTrue(GeneEffects.cooldown(chimera, 0) == 0, "the boot spent the cooldown");
        h.runAfterDelay(com.coolfreeze23.headinajar.mecha.HopperAnimation.BOOT_END + 1, () -> {
            expect(h, null, GeneEffects.fire(chimera, p, 0), "did not fire after the boot");
            p.stopRiding();
            done(h, p);
        });
    }

    // --- Levitation Bolt ---------------------------------------------------------------------------------------------

    /** A host's bolt homes in on a pig: 4 damage and Levitation trimmed to 3 s; a 3 s cooldown; nothing aimed at is refused. */
    @GameTest(template = "platform", timeoutTicks = 120)
    public static void theLevitationBoltLiftsForThreeSeconds(GameTestHelper h) {
        ServerPlayer sawyer = player(h, FLOOR.west(4)), p = player(h, FLOOR.east(4));
        Cow cow = host(h, EntityType.COW, FLOOR.north(3), sawyer);
        give(h, cow, Genetics.Target.HOST, Gene.LEVITATION_BOLT);
        ride(h, p, cow);
        Pig pig = h.spawn(EntityType.PIG, FLOOR.south(2));
        pig.setNoAi(true);
        p.setXRot(-80);
        h.assertTrue("gene.no_target".equals(fire(cow, p)), "a bolt went off at the sky");
        p.lookAt(EntityAnchorArgument.Anchor.EYES, pig.getBoundingBox().getCenter());
        expect(h, null, fire(cow, p), "the bolt did not fire");
        h.assertTrue(GeneEffects.cooldown(cow, 0) == GeneNumbers.BOLT_COOLDOWN, "cooldown " + GeneEffects.cooldown(cow, 0));
        h.assertTrue(!h.getLevel().getEntitiesOfClass(ShulkerBullet.class, cow.getBoundingBox().inflate(4)).isEmpty(), "no bullet");
        h.succeedWhen(() -> {
            MobEffectInstance lift = pig.getEffect(MobEffects.LEVITATION);
            h.assertTrue(lift != null, "not hit yet");
            h.assertTrue(lift.getDuration() <= GeneNumbers.BOLT_LEVITATION, "Levitation for " + lift.getDuration() + " ticks");
            h.assertTrue(pig.getMaxHealth() - pig.getHealth() > 3.9f, "the bolt did " + (pig.getMaxHealth() - pig.getHealth()));
            pig.discard();
            h.getLevel().getServer().getPlayerList().remove(sawyer);
            h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    // --- Bulldoze ----------------------------------------------------------------------------------------------------

    /** A bulldozing host tramples a poppy and leaves; on claimed ground nothing breaks and the rider is told. Knockback on it is a third weaker. */
    @GameTest(template = "platform")
    public static void bulldozeTramplesButNotOnAClaim(GameTestHelper h) {
        ServerPlayer sawyer = player(h, FLOOR.west(4)), p = player(h, FLOOR.east(4));
        Cow cow = host(h, EntityType.COW, FLOOR, sawyer);
        give(h, cow, Genetics.Target.HOST, Gene.BULLDOZE);
        ride(h, p, cow);
        BlockPos at = cow.blockPosition();
        h.getLevel().setBlockAndUpdate(at.below(), Blocks.GRASS_BLOCK.defaultBlockState());
        h.getLevel().setBlockAndUpdate(at.east(), Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
        h.getLevel().setBlockAndUpdate(at, Blocks.POPPY.defaultBlockState());
        Consumer<BlockEvent.BreakEvent> claim = claim(new AABB(at).inflate(2));
        try {
            h.assertTrue(GeneActives.trample(cow, h.getLevel()) == 0, "claimed ground was trampled");
        } finally {
            NeoForge.EVENT_BUS.unregister(claim);
        }
        h.assertTrue(h.getLevel().getBlockState(at).is(Blocks.POPPY), "the claimed poppy went");
        h.assertTrue(Refusals.delivered(p).contains("gene.bulldoze.claim"), "the rider was not told: " + Refusals.delivered(p));
        h.assertTrue(GeneActives.trample(cow, h.getLevel()) == 2, "the poppy and the leaves were not both trampled");
        h.assertTrue(h.getLevel().getBlockState(at).isAir() && h.getLevel().getBlockState(at.east()).isAir(), "something soft is still standing");
        // knockback on a bulldozer is a third weaker than on a cow without it
        Cow plain = h.spawn(EntityType.COW, FLOOR.north(4));
        cow.setDeltaMovement(Vec3.ZERO);
        plain.setDeltaMovement(Vec3.ZERO);
        cow.knockback(0.5, 1, 0);
        plain.knockback(0.5, 1, 0);
        double ratio = cow.getDeltaMovement().x / plain.getDeltaMovement().x;
        h.assertTrue(Math.abs(ratio - GeneNumbers.BULLDOZE_KNOCK_IN) < 0.02, "knockback ratio " + ratio);
        plain.discard();
        done(h, sawyer, p);
    }

    // --- Weeping Glands ----------------------------------------------------------------------------------------------

    /** A weeping host drops a Ghast Tear at its feet when its 20 minutes are up, and sobs. */
    @GameTest(template = "platform")
    public static void weepingGlandsDropATear(GameTestHelper h) {
        ServerPlayer sawyer = player(h, FLOOR.west(4));
        Cow cow = host(h, EntityType.COW, FLOOR, sawyer);
        give(h, cow, Genetics.Target.HOST, Gene.WEEPING_GLANDS);
        cow.getPersistentData().putInt(GeneActives.WEEP_WORN, GeneNumbers.WEEP_TEAR - 3);
        int sobs = Fx.count(Fx.GENE_WEEP, cow.getId());
        GeneActives.sob(cow, h.getLevel());
        h.assertTrue(Fx.count(Fx.GENE_WEEP, cow.getId()) == sobs + 1, "no sob memo");
        h.runAfterDelay(6, () -> {
            boolean tear = !h.getLevel().getEntitiesOfClass(ItemEntity.class, cow.getBoundingBox().inflate(2), i -> i.getItem().is(Items.GHAST_TEAR)).isEmpty();
            h.assertTrue(tear, "no Ghast Tear");
            h.assertTrue(cow.getPersistentData().getInt(GeneActives.WEEP_WORN) < 10, "the timer did not start again");
            done(h, sawyer);
        });
    }

    // --- the Chimera's Graft -----------------------------------------------------------------------------------------

    /** The Graft takes no gene of load 4 or more: a vex, an evoker, a shulker, a ravager and a ghast are refused as too heavy; a fox still gives Snow Pounce. */
    @GameTest(template = "platform")
    public static void theGraftLeavesTheHeavyGenesAlone(GameTestHelper h) {
        for (EntityType<?> heavy : List.of(EntityType.VEX, EntityType.EVOKER, EntityType.SHULKER, EntityType.RAVAGER, EntityType.GHAST)) {
            boolean[] why = {false, false};
            h.assertTrue(Chimera.geneOf(heavy, why) == null && !why[0] && why[1], "the Graft would take from " + heavy);
        }
        boolean[] why = {false, false};
        h.assertTrue(Chimera.geneOf(EntityType.FOX, why) == Gene.SNOW_POUNCE, "a fox gives no Snow Pounce");
        h.assertTrue(Chimera.geneOf(EntityType.SPIDER, why) == Gene.SPIDER_CLIMB, "a spider gives no Spider Climb");
        h.assertTrue(Chimera.geneOf(EntityType.WARDEN, why) == null && why[0] && !why[1], "a warden is not above our pay grade");
        // the drive key on a Chimera that just hit a vex: refused with the heavy line
        ServerPlayer p = player(h, FLOOR);
        MechaEntity m = h.spawn(ModRegistry.mechaType(MechaFrame.CHIMERA), FLOOR.east(3));
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < m.frame().totalSlots(); i++) slots.add(ItemStack.EMPTY);
        ItemStack legs = new ItemStack(ModRegistry.mechaModule(MechaModule.HYDRAULIC_LEGS));
        MechaModuleItem.setSlider(legs, 50);
        slots.set(m.frame().firstIndex(MechaModule.Slot.LEGS), legs);
        m.setModules(slots);
        m.setOwner(p.getUUID());
        m.setBattery(m.batteryCapacity());
        FormActions.detach(p, 3);
        p.interactOn(m, InteractionHand.MAIN_HAND);
        h.assertTrue(p.getVehicle() == m && FormActions.state(p).form() == Form.PILOT, "setup: not in the cockpit");
        m.tasted = EntityType.VEX;
        m.tastedAt = h.getLevel().getGameTime();
        Chimera.input(p, true);
        h.assertTrue("mecha.graft.heavy".equals(Refusals.lastDelivered(p)), "the vex was not refused as too heavy: " + Refusals.delivered(p));
        done(h, p);
    }

    // --- every gene on a host ----------------------------------------------------------------------------------------

    /** Each of the eight on its own cow host for 200 ticks, each fired once at tick 100 (no pilot): no error, every host alive. */
    @GameTest(template = "platform", timeoutTicks = 240)
    public static void everyActiveGeneTicksOnACowHost(GameTestHelper h) {
        ServerPlayer sawyer = player(h, new BlockPos(1, 2, 1));
        Gene[] genes = {Gene.PHASE, Gene.SNOW_POUNCE, Gene.TONGUE, Gene.SPIT_GLAND, Gene.FANG_LINE, Gene.LEVITATION_BOLT, Gene.BULLDOZE, Gene.WEEPING_GLANDS};
        List<Cow> cows = new ArrayList<>();
        for (int i = 0; i < genes.length; i++) {
            Cow cow = host(h, EntityType.COW, new BlockPos(3 + i, 2, 3), sawyer);
            give(h, cow, Genetics.Target.HOST, genes[i]);
            cows.add(cow);
        }
        h.runAfterDelay(100, () -> {
            for (Cow cow : cows) GeneEffects.fire(cow, cow, 0);
        });
        h.runAfterDelay(200, () -> {
            for (int i = 0; i < genes.length; i++) {
                Cow cow = cows.get(i);
                h.assertTrue(cow.isAlive() && Genetics.genes(cow).has(genes[i]), genes[i] + "'s host did not last 200 ticks");
            }
            done(h, sawyer);
        });
    }
}
