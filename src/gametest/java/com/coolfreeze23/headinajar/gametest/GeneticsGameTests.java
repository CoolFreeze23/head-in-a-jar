package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.AssemblyGantryBlockEntity;
import com.coolfreeze23.headinajar.block.LabMachines;
import com.coolfreeze23.headinajar.block.LeechJarBlockEntity;
import com.coolfreeze23.headinajar.entity.HeadlessBody;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.genetics.Gene;
import com.coolfreeze23.headinajar.genetics.GeneArchive;
import com.coolfreeze23.headinajar.genetics.GeneEffects;
import com.coolfreeze23.headinajar.genetics.GeneSet;
import com.coolfreeze23.headinajar.genetics.Genetics;
import com.coolfreeze23.headinajar.genetics.Serum;
import com.coolfreeze23.headinajar.item.GeneticsItems;
import com.coolfreeze23.headinajar.item.MechaFrameItem;
import com.coolfreeze23.headinajar.item.SurgicalSawItem;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.menu.AssemblyGantryMenu;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.windcharge.WindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Phase 5, Genetics Department: collection, the lab, splicing math, injection, rejection and where genes live. */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class GeneticsGameTests {
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

    /** A random source whose float rolls are always {@code roll} (rejection and mutation checks). */
    private static LegacyRandomSource rolls(float roll) {
        return new LegacyRandomSource(1) {
            @Override
            public float nextFloat() {
                return roll;
            }
        };
    }

    private static Serum serum(int stability, Gene... genes) {
        return new Serum(java.util.Arrays.stream(genes).map(g -> new Serum.Entry(g, 1f)).toList(), stability);
    }

    private static ItemStack cassette(Serum serum) {
        ItemStack stack = new ItemStack(ModRegistry.SERUM_CASSETTE.get());
        serum.writeTo(stack);
        return stack;
    }

    private static <T extends Mob> T host(GameTestHelper h, EntityType<T> type, BlockPos at, ServerPlayer sawyer) {
        T mob = h.spawn(type, at);
        mob.setHealth(1);
        h.assertTrue(SurgicalSawItem.saw(sawyer, new ItemStack(ModRegistry.SURGICAL_SAW.get()), mob, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME,
                "saw failed on " + type);
        return mob;
    }

    // --- collection ----------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void bloodlessNeedsRightTool(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        var skeleton = h.spawn(EntityType.SKELETON, FLOOR.east(2));
        ItemStack syringe = new ItemStack(ModRegistry.SYRINGE.get()), drill = new ItemStack(ModRegistry.MARROW_DRILL.get());
        h.assertTrue(ModRegistry.SYRINGE.get().draw(p, syringe, skeleton, InteractionHand.MAIN_HAND) == InteractionResult.FAIL, "syringe drew from a skeleton");
        h.assertTrue(ModRegistry.MARROW_DRILL.get().draw(p, drill, skeleton, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME, "marrow drill failed");
        var pig = h.spawn(EntityType.PIG, FLOOR.west(2));
        h.assertTrue(ModRegistry.MARROW_DRILL.get().draw(p, drill, pig, InteractionHand.MAIN_HAND) == InteractionResult.FAIL, "marrow drill drew from a pig");
        ItemStack got = p.getInventory().items.stream().filter(s -> s.is(ModRegistry.BLOOD_SAMPLE.get())).findFirst().orElse(ItemStack.EMPTY);
        h.assertTrue(GeneticsItems.species(got) == EntityType.SKELETON && GeneticsItems.quality(got) >= 20, "no skeleton sample");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void qualityScalesWithHealth(GameTestHelper h) {
        var healthy = h.spawn(EntityType.PIG, FLOOR);
        var hurt = h.spawn(EntityType.PIG, FLOOR.east(2));
        hurt.setHealth(1);
        var sick = h.spawn(EntityType.PIG, FLOOR.west(2));
        sick.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
        int a = GeneticsItems.SyringeItem.quality(healthy, 80), b = GeneticsItems.SyringeItem.quality(hurt, 80), c = GeneticsItems.SyringeItem.quality(sick, 80);
        h.assertTrue(a == 80 && b == 40 && c == 60, "quality " + a + " / " + b + " / " + c);
        h.succeed();
    }

    @GameTest(template = "platform")
    public static void bossesNeedLeviathanNeedle(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        var guardian = h.spawn(EntityType.ELDER_GUARDIAN, FLOOR.east(3).above());
        ItemStack syringe = new ItemStack(ModRegistry.PRECISION_SYRINGE.get()), needle = new ItemStack(ModRegistry.LEVIATHAN_NEEDLE.get());
        h.assertTrue(ModRegistry.PRECISION_SYRINGE.get().draw(p, syringe, guardian, InteractionHand.MAIN_HAND) == InteractionResult.FAIL, "syringe pierced a boss");
        h.assertTrue(ModRegistry.LEVIATHAN_NEEDLE.get().draw(p, needle, guardian, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME, "needle failed on a boss");
        guardian.discard();
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void leechJarSamplesNeighbours(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.LEECH_JAR.get());
        var jar = (LeechJarBlockEntity) h.getBlockEntity(FLOOR);
        var skeleton = h.spawn(EntityType.SKELETON, FLOOR.east());
        h.assertFalse(jar.feed(h.getLevel()), "leeches fed on a skeleton");
        skeleton.discard();
        h.spawn(EntityType.COW, FLOOR.east());
        h.assertTrue(jar.feed(h.getLevel()), "leeches did not feed on a cow");
        ItemStack s = jar.items().getStackInSlot(0);
        h.assertTrue(GeneticsItems.species(s) == EntityType.COW && GeneticsItems.quality(s) <= LeechJarBlockEntity.MAX, "bad leech sample");
        h.succeed();
    }

    // --- the lab -------------------------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void centrifugeSplitsSample(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.CENTRIFUGE.get());
        var c = (LabMachines.Centrifuge) h.getBlockEntity(FLOOR);
        c.energy().set(10_000);
        c.items().setStackInSlot(0, GeneticsItems.sample(ModRegistry.BLOOD_SAMPLE.get(), EntityType.SPIDER, 77));
        for (int i = 0; i < LabMachines.Centrifuge.TICKS; i++) LabMachines.Centrifuge.serverTick(h.getLevel(), c.getBlockPos(), c.getBlockState(), c);
        ItemStack dna = c.items().getStackInSlot(1);
        h.assertTrue(dna.is(ModRegistry.DNA_FRACTION.get()) && GeneticsItems.species(dna) == EntityType.SPIDER && GeneticsItems.quality(dna) == 77, "no DNA fraction");
        h.assertTrue(c.items().getStackInSlot(2).is(ModRegistry.PLASMA_VIAL.get()) && c.items().getStackInSlot(0).isEmpty(), "no plasma or sample left");
        h.assertTrue(c.energy().getEnergyStored() == 10_000 - LabMachines.Centrifuge.TICKS * LabMachines.Centrifuge.FE_PER_TICK, "wrong energy use");
        h.succeed();
    }

    @GameTest(template = "platform")
    public static void readoutAccumulates(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(2));
        h.setBlock(FLOOR, ModRegistry.DNA_SEQUENCER.get());
        var s = (LabMachines.Sequencer) h.getBlockEntity(FLOOR);
        s.energy().set(40_000);
        for (int i = 0; i < 2; i++) {
            s.items().setStackInSlot(0, GeneticsItems.sample(ModRegistry.DNA_FRACTION.get(), EntityType.CAVE_SPIDER, 80));
            if (i == 0) h.assertTrue(s.button(p, LabMachines.Sequencer.AUTO), "auto did not start");
            for (int t = 0; t < LabMachines.Sequencer.TICKS; t++) LabMachines.Sequencer.serverTick(h.getLevel(), s.getBlockPos(), s.getBlockState(), s);
        }
        var archive = GeneArchive.get(h.getLevel().getServer());
        float climb = archive.readout(GeneArchive.key(p), Gene.SPIDER_CLIMB), venom = archive.readout(GeneArchive.key(p), Gene.VENOM_SAC);
        // 80 quality x 0.6 accuracy x 0.25 / 2 genes = 6% per read
        h.assertTrue(Math.abs(climb - 12) < 0.01 && Math.abs(venom - 12) < 0.01, "readouts " + climb + " / " + venom);
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void basecallingBeatsAuto(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(2));
        h.setBlock(FLOOR, ModRegistry.DNA_SEQUENCER.get());
        var s = (LabMachines.Sequencer) h.getBlockEntity(FLOOR);
        s.energy().set(40_000);
        s.items().setStackInSlot(0, GeneticsItems.sample(ModRegistry.DNA_FRACTION.get(), EntityType.RABBIT, 80));
        int seed = s.saveWithoutMetadata(h.getLevel().registryAccess()).getInt("Seed");
        int[] bases = LabMachines.Sequencer.bases(seed);
        h.assertTrue(LabMachines.Sequencer.correct(seed, LabMachines.Sequencer.encode(bases)) == LabMachines.Sequencer.BASES, "perfect calls did not round-trip");
        h.assertTrue(s.button(p, LabMachines.Sequencer.CALL + LabMachines.Sequencer.encode(bases)), "calls refused");
        // perfect calls: accuracy 1.0, so 80 x 1.0 x 0.25 / 1 = 20%
        float r = GeneArchive.get(h.getLevel().getServer()).readout(GeneArchive.key(p), Gene.RABBIT_SPRING);
        h.assertTrue(Math.abs(r - 20) < 0.01, "perfect read gave " + r);
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void draftGeneHalfPotency(GameTestHelper h) {
        h.assertTrue(GeneArchive.potency(29) == 0 && GeneArchive.potency(30) == 0.5f && GeneArchive.potency(59) == 0.5f
                && GeneArchive.potency(60) == 0.75f && GeneArchive.potency(100) == 1f, "potency bands");
        h.succeed();
    }

    @GameTest(template = "platform")
    public static void stabilityMath(GameTestHelper h) {
        Map<Gene, Float> full = new EnumMap<>(Gene.class);
        for (Gene g : Gene.values()) full.put(g, 100f);
        h.assertTrue(Genetics.stability(List.of(Gene.ENDER_BLINK), full, 0) == 80, "single gene");
        h.assertTrue(Genetics.stability(List.of(Gene.ENDER_BLINK), Map.of(Gene.ENDER_BLINK, 40f), 0) == 70, "draft costs 1.5x");
        h.assertTrue(Genetics.stability(List.of(Gene.BLAZE_CORE, Gene.GILLS), full, 0) == 60, "conflict costs 15");
        h.assertTrue(Genetics.stability(List.of(Gene.SPIDER_CLIMB, Gene.VENOM_SAC), full, 0) == 90, "synergy gives 10");
        h.assertTrue(Genetics.stability(List.of(Gene.SONIC_BOOM), full, 40) == 100, "catalyst caps at 100");
        h.succeed();
    }

    @GameTest(template = "platform")
    public static void mutationChanceFormula(GameTestHelper h) {
        h.assertTrue(Genetics.mutationChance(60) == 0 && Genetics.mutationChance(100) == 0, "no mutation at 60+");
        h.assertTrue(Math.abs(Genetics.mutationChance(20) - 0.6f) < 1e-4 && Math.abs(Genetics.mutationChance(0) - 0.9f) < 1e-4, "mutation odds");
        h.succeed();
    }

    @GameTest(template = "platform")
    public static void splicerNeedsDraftAndMakesSerum(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(2));
        var archive = GeneArchive.get(h.getLevel().getServer());
        archive.add(GeneArchive.key(p), Gene.SPIDER_CLIMB, 100);
        archive.add(GeneArchive.key(p), Gene.VENOM_SAC, 35);
        archive.add(GeneArchive.key(p), Gene.GILLS, 20);
        h.setBlock(FLOOR, ModRegistry.GENE_SPLICER.get());
        var s = (LabMachines.Splicer) h.getBlockEntity(FLOOR);
        h.assertFalse(s.pick(p, 0, Gene.GILLS), "picked a gene under Draft");
        h.assertTrue(s.pick(p, 0, Gene.SPIDER_CLIMB) && s.pick(p, 1, Gene.VENOM_SAC), "could not pick read genes");
        h.assertFalse(s.pick(p, 2, Gene.SPIDER_CLIMB), "picked the same gene twice");
        h.assertFalse(s.button(p, LabMachines.Splicer.SPLICE), "spliced without Bio-Gel");
        s.items().setStackInSlot(LabMachines.Splicer.SLOT_GEL, new ItemStack(ModRegistry.BIO_GEL.get()));
        s.items().setStackInSlot(LabMachines.Splicer.SLOT_BOTTLE, new ItemStack(Items.GLASS_BOTTLE));
        s.items().setStackInSlot(LabMachines.Splicer.SLOT_CATALYST, new ItemStack(Items.AMETHYST_SHARD));
        s.energy().set(40_000);
        h.assertTrue(s.button(p, LabMachines.Splicer.SPLICE), "splice refused");
        for (int t = 0; t < LabMachines.Splicer.TICKS; t++) LabMachines.Splicer.serverTick(h.getLevel(), s.getBlockPos(), s.getBlockState(), s);
        var serum = Serum.of(s.items().getStackInSlot(LabMachines.Splicer.SLOT_OUT));
        // 100 - 10 (climb) - 10 x 1.5 (venom, draft) + 10 (synergy) + 10 (amethyst) = 95
        h.assertTrue(serum.isPresent() && serum.get().stability() == 95 && serum.get().genes().size() == 2, "serum " + serum);
        h.assertTrue(serum.get().genes().get(1).potency() == 0.5f, "draft gene not at half potency");
        h.assertTrue(s.items().getStackInSlot(LabMachines.Splicer.SLOT_GEL).isEmpty() && s.items().getStackInSlot(LabMachines.Splicer.SLOT_CATALYST).isEmpty(),
                "inputs not used");
        done(h, p);
    }

    // --- injection and rejection ---------------------------------------------------------------------

    @GameTest(template = "platform")
    public static void sameGeneDiminishes(GameTestHelper h) {
        GeneSet set = new GeneSet();
        for (int i = 0; i < 4; i++) {
            set.serums.add(serum(100, Gene.RABBIT_SPRING));
            set.changed();
            float want = new float[]{1f, 1.5f, 1.75f, 1.75f}[i];
            h.assertTrue(Math.abs(set.strength(Gene.RABBIT_SPRING) - want) < 1e-4, "copy " + (i + 1) + " gave " + set.strength(Gene.RABBIT_SPRING));
        }
        h.succeed();
    }

    @GameTest(template = "platform")
    public static void hostRejectsWrongBloodType(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        var zombie = host(h, EntityType.ZOMBIE, FLOOR.east(2), p);
        // a golem gene is oil; a zombie is grave blood: a 10% roll is under the 30% rejection chance
        h.assertTrue(Genetics.inject(zombie, serum(100, Gene.GOLEM_FRAME), Genetics.Target.HOST, rolls(0.1f)) == Genetics.Result.REJECTED, "not rejected");
        h.assertTrue(Genetics.inject(zombie, serum(100, Gene.GOLEM_FRAME), Genetics.Target.HOST, rolls(0.5f)) == Genetics.Result.OK, "a good roll was rejected");
        var pig = host(h, EntityType.PIG, FLOOR.west(2), p);
        h.assertTrue(Genetics.inject(pig, serum(100, Gene.GOLEM_FRAME, Gene.HEROS_BLOOD), Genetics.Target.HOST, rolls(0.1f)) == Genetics.Result.OK,
                "Hero's Blood did not prevent rejection");
        h.assertTrue(Genetics.inject(pig, serum(100, Gene.RABBIT_SPRING), Genetics.Target.HOST, rolls(0.5f)) == Genetics.Result.FULL, "a host took two serums");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void mechaNeverRejects(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        MechaEntity chimera = h.spawn(ModRegistry.CHIMERA.get(), FLOOR.east(3));
        chimera.setOwner(p.getUUID());
        for (int i = 0; i < 3; i++) {
            ItemStack stack = cassette(serum(100, Gene.GOLEM_FRAME, Gene.WITHER_WARD));
            h.assertTrue(ModRegistry.SERUM_CASSETTE.get().inject(p, stack, chimera) == InteractionResult.CONSUME, "port " + i + " refused");
        }
        h.assertTrue(Genetics.genes(chimera).serums.size() == 3, "chimera does not hold three serums");
        h.assertTrue(ModRegistry.SERUM_CASSETTE.get().inject(p, cassette(serum(100, Gene.GILLS)), chimera) == InteractionResult.FAIL, "a fourth serum fit");
        MechaEntity hopper = h.spawn(ModRegistry.HOPPER.get(), FLOOR.west(3));
        hopper.setOwner(p.getUUID());
        h.assertTrue(ModRegistry.SERUM_CASSETTE.get().inject(p, cassette(serum(100, Gene.GILLS)), hopper) == InteractionResult.FAIL, "a Hopper took a serum");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void bodyLimits(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        h.assertTrue(Genetics.inject(p, serum(100, Gene.ENDER_BLINK), Genetics.Target.BODY, rolls(0.5f)) == Genetics.Result.TOO_HEAVY, "a load-4 serum fit a body");
        h.assertTrue(Genetics.inject(p, serum(100, Gene.RABBIT_SPRING), Genetics.Target.BODY, rolls(0.5f)) == Genetics.Result.OK, "first serum");
        h.assertTrue(Genetics.inject(p, serum(100, Gene.CAT_EYES), Genetics.Target.BODY, rolls(0.5f)) == Genetics.Result.OK, "second serum");
        h.assertTrue(Genetics.inject(p, serum(100, Gene.GLOW_CELLS), Genetics.Target.BODY, rolls(0.5f)) == Genetics.Result.FULL, "a third serum fit");
        h.assertTrue(Genetics.inject(p, serum(10, Gene.FEATHER_BONES), Genetics.Target.MECHA, rolls(0.0f)) != Genetics.Result.OK, "unstable serum never mutated");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void injectionRigNeedsBody(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(1));
        h.setBlock(FLOOR, ModRegistry.INJECTION_RIG.get());
        var rig = (LabMachines.InjectionRig) h.getBlockEntity(FLOOR);
        rig.energy().set(20_000);
        rig.items().setStackInSlot(0, cassette(serum(100, Gene.CAT_EYES)));
        h.assertTrue(rig.inject(p) == Genetics.Result.OK && rig.items().getStackInSlot(0).isEmpty(), "rig did not inject");
        h.assertTrue(Genetics.genes(p).has(Gene.CAT_EYES) && GeneEffects.has(p, Gene.CAT_EYES), "gene not working");
        FormActions.detach(p, 3);
        h.assertFalse(GeneEffects.has(p, Gene.CAT_EYES), "a jar kept its body's genes working");
        rig.items().setStackInSlot(0, cassette(serum(100, Gene.GLOW_CELLS)));
        h.assertTrue(rig.inject(p) == null, "rig injected a jar");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void genesStayWithBody(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Genetics.inject(p, serum(100, Gene.RABBIT_SPRING), Genetics.Target.BODY, rolls(0.5f));
        FormActions.detach(p, 3);
        h.assertTrue(Genetics.genes(p).isEmpty(), "genes stayed in the head");
        HeadlessBody body = h.getLevel().getEntitiesOfClass(HeadlessBody.class, p.getBoundingBox().inflate(3)).stream().findFirst().orElse(null);
        h.assertTrue(body != null && Genetics.genes(body).has(Gene.RABBIT_SPRING), "body lost its genes");
        h.assertFalse(GeneEffects.active(body), "an idle body's genes are working");
        h.assertTrue(FormActions.reattach(p, body), "reattach failed");
        h.assertTrue(Genetics.genes(p).has(Gene.RABBIT_SPRING), "genes did not come back with the body");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void activeGeneFiresAndCoolsDown(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR);
        Genetics.inject(p, serum(100, Gene.BREEZE_UPDRAFT), Genetics.Target.BODY, rolls(0.5f));
        h.assertTrue(GeneEffects.actives(p).equals(List.of(Gene.BREEZE_UPDRAFT)), "actives " + GeneEffects.actives(p));
        h.assertTrue(GeneEffects.activate(p, p, 0), "updraft did not fire");
        h.assertFalse(GeneEffects.activate(p, p, 0), "updraft ignored its cooldown");
        h.assertFalse(GeneEffects.activate(p, p, 1), "an empty G slot fired");
        h.assertTrue(!h.getLevel().getEntitiesOfClass(WindCharge.class, p.getBoundingBox().inflate(8)).isEmpty(), "no wind charge");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void gantryKeepsChimeraGenes(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(3));
        h.setBlock(FLOOR, ModRegistry.ASSEMBLY_GANTRY.get());
        var gantry = (AssemblyGantryBlockEntity) h.getBlockEntity(FLOOR);
        gantry.energy().set(100_000);
        MechaEntity chimera = h.spawn(ModRegistry.CHIMERA.get(), FLOOR.above());
        chimera.setOwner(p.getUUID());
        Genetics.inject(chimera, serum(100, Gene.SPIDER_CLIMB), Genetics.Target.MECHA, rolls(0.5f));
        h.assertTrue(gantry.button(p, AssemblyGantryMenu.DISASSEMBLE), "disassemble failed");
        ItemStack frame = gantry.items().getStackInSlot(AssemblyGantryBlockEntity.SLOT_FRAME);
        h.assertTrue(MechaFrameItem.genes(frame).map(g -> g.has(Gene.SPIDER_CLIMB)).orElse(false), "frame lost the genes");
        // a frame on legs is only built with legs that walk
        gantry.items().setStackInSlot(AssemblyGantryBlockEntity.FIRST_MODULE + MechaFrame.CHIMERA.firstIndex(com.coolfreeze23.headinajar.mecha.MechaModule.Slot.LEGS),
                new ItemStack(ModRegistry.mechaModule(com.coolfreeze23.headinajar.mecha.MechaModule.HYDRAULIC_LEGS)));
        h.assertTrue(gantry.button(p, AssemblyGantryMenu.ASSEMBLE), "assemble failed");
        MechaEntity rebuilt = gantry.onPad();
        h.assertTrue(rebuilt != null && rebuilt.frame() == MechaFrame.CHIMERA && Genetics.genes(rebuilt).has(Gene.SPIDER_CLIMB), "rebuilt chimera has no genes");
        done(h, p);
    }
}
