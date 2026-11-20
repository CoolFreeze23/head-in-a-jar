package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.LabMachines;
import com.coolfreeze23.headinajar.block.SafetyPlacardBlock;
import com.coolfreeze23.headinajar.clarity.Memos;
import com.coolfreeze23.headinajar.genetics.Gene;
import com.coolfreeze23.headinajar.genetics.GeneArchive;
import com.coolfreeze23.headinajar.genetics.Genetics;
import com.coolfreeze23.headinajar.genetics.Samples;
import com.coolfreeze23.headinajar.item.GeneticsItems;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 0.6.2's gene library: a gene for every natural creature, load limits, old samples, the Nitwit, the cow, pairs, the Splicer's ids. */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class GeneLibraryGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);
    /** The three vanilla creatures that carry no gene: never natural, or off the books. */
    private static final Set<EntityType<?>> NONE = Set.of(EntityType.GIANT, EntityType.ILLUSIONER, EntityType.ZOMBIE_HORSE);

    private static ServerPlayer player(GameTestHelper h, BlockPos at) {
        return SawTableGameTests.player(h, at);
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        SawTableGameTests.done(h, players);
    }

    /** The 82 vanilla creatures the company keeps a cell for (the Staff Directory's list). */
    private static List<EntityType<?>> vanillaCreatures() {
        List<EntityType<?>> out = new ArrayList<>();
        for (String id : com.coolfreeze23.headinajar.genetics.Directory.VANILLA) {
            out.add(BuiltInRegistries.ENTITY_TYPE.get(net.minecraft.resources.ResourceLocation.parse(id)));
        }
        return out;
    }

    private static boolean earned(GameTestHelper h, ServerPlayer p, String id) {
        var holder = h.getLevel().getServer().getAdvancements().get(HeadInAJar.id(id));
        if (holder == null) throw new net.minecraft.gametest.framework.GameTestAssertException("no advancement " + id);
        return p.getAdvancements().getOrStartProgress(holder).isDone();
    }

    @GameTest(template = "platform")
    public static void everyNaturalSpeciesCarriesAGene(GameTestHelper h) {
        List<EntityType<?>> all = vanillaCreatures();
        h.assertTrue(all.size() == 82, "vanilla creatures counted: " + all.size());
        List<String> bare = new ArrayList<>(), wrong = new ArrayList<>();
        for (EntityType<?> type : all) {
            boolean genes = !Gene.of(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()).isEmpty();
            if (NONE.contains(type) == genes) (genes ? wrong : bare).add(BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath());
        }
        h.assertTrue(bare.isEmpty(), "carry no gene: " + bare);
        h.assertTrue(wrong.isEmpty(), "should carry none: " + wrong);
        h.assertValueEqual(Gene.values().length, 89, "genes in the library");
        // the 49 genes of 0.6.1 keep their ordinals (the Splicer and saved strands use them)
        h.assertValueEqual(Gene.CHIMERIC_HARMONY.ordinal(), 48, "Chimeric Harmony's ordinal");
        h.assertValueEqual(Gene.RUMINANT_GUT.ordinal(), 49, "the first new gene's ordinal");
        h.succeed();
    }

    @GameTest(template = "platform")
    public static void noGeneBreaksALoadLimit(GameTestHelper h) {
        for (Gene g : Gene.values()) {
            Genetics.Target fits = Genetics.Target.lightestFor(g.load);
            h.assertTrue(fits != null, g + " fits nothing");
            if (g.load <= 3) h.assertValueEqual(fits, Genetics.Target.BODY, g + " (load " + g.load + ")");
            else if (g.load <= 6) h.assertValueEqual(fits, Genetics.Target.HOST, g + " (load " + g.load + ")");
            else h.assertValueEqual(fits, Genetics.Target.MECHA, g + " (load " + g.load + ")");
            if (g.hasCostLine()) {
                h.assertValueEqual(g.stability, 5 * g.load, g + "'s stability");
                h.assertTrue(g.load >= 1 && g.load <= 4, g + "'s load " + g.load);
            }
        }
        // the five load-4 genes go into hosts and mecha only, by load alone
        for (Gene g : List.of(Gene.PHASE, Gene.FANG_LINE, Gene.BULLDOZE, Gene.WEEPING_GLANDS, Gene.LEVITATION_BOLT)) {
            h.assertValueEqual(Genetics.Target.lightestFor(g.load), Genetics.Target.HOST, g.name());
        }
        h.succeed();
    }

    @GameTest(template = "platform")
    public static void anOldCowSampleSequencesIntoRuminantGut(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(2));
        // a Blood Sample as 0.5.4 made it: species and quality, nothing else
        ItemStack old = new ItemStack(ModRegistry.BLOOD_SAMPLE.get());
        CompoundTag tag = new CompoundTag();
        tag.putString("Species", "minecraft:cow");
        tag.putInt("Quality", 70);
        CustomData.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, old, tag);
        h.setBlock(FLOOR.west(2), ModRegistry.CENTRIFUGE.get());
        var c = (LabMachines.Centrifuge) h.getBlockEntity(FLOOR.west(2));
        c.energy().set(20_000);
        c.items().setStackInSlot(0, old);
        for (int i = 0; i < LabMachines.Centrifuge.TICKS; i++) LabMachines.Centrifuge.serverTick(h.getLevel(), c.getBlockPos(), c.getBlockState(), c);
        ItemStack fraction = c.items().getStackInSlot(1);
        h.assertTrue(fraction.is(ModRegistry.DNA_FRACTION.get()) && GeneticsItems.species(fraction) == EntityType.COW, "no cow fraction");
        h.setBlock(FLOOR, ModRegistry.DNA_SEQUENCER.get());
        var s = (LabMachines.Sequencer) h.getBlockEntity(FLOOR);
        s.energy().set(40_000);
        s.items().setStackInSlot(0, fraction);
        h.assertTrue(s.button(p, LabMachines.Sequencer.AUTO), "auto did not start");
        for (int t = 0; t < LabMachines.Sequencer.TICKS; t++) LabMachines.Sequencer.serverTick(h.getLevel(), s.getBlockPos(), s.getBlockState(), s);
        float read = GeneArchive.get(h.getLevel().getServer()).readout(GeneArchive.key(p), Gene.RUMINANT_GUT);
        h.assertTrue(read > 0, "the cow's sample read nothing: " + read);
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void aNitwitCarriesNoReadableGenes(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(2));
        Villager nitwit = h.spawn(EntityType.VILLAGER, FLOOR.west(2));
        nitwit.setVillagerData(nitwit.getVillagerData().setProfession(VillagerProfession.NITWIT));
        Villager idle = h.spawn(EntityType.VILLAGER, FLOOR.north(2));
        idle.setVillagerData(idle.getVillagerData().setProfession(VillagerProfession.NONE));
        ItemStack syringe = new ItemStack(ModRegistry.SYRINGE.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, syringe);
        var tool = (GeneticsItems.SyringeItem) syringe.getItem();
        tool.draw(p, syringe, nitwit, InteractionHand.MAIN_HAND);
        tool.draw(p, syringe, idle, InteractionHand.MAIN_HAND);
        ItemStack fromNitwit = ItemStack.EMPTY, fromIdle = ItemStack.EMPTY;
        for (ItemStack stack : p.getInventory().items) {
            if (!stack.is(ModRegistry.BLOOD_SAMPLE.get())) continue;
            if (Samples.nitwit(stack)) fromNitwit = stack;
            else fromIdle = stack;
        }
        h.assertTrue(!fromNitwit.isEmpty(), "the Nitwit's sample has no flag");
        h.assertTrue(Samples.genes(fromNitwit).isEmpty(), "the Nitwit's sample carries genes");
        h.assertValueEqual(Samples.genes(fromIdle), List.of(Gene.HEROS_BLOOD), "an unemployed villager's genes");
        List<Component> tip = new ArrayList<>();
        fromNitwit.getItem().appendHoverText(fromNitwit, net.minecraft.world.item.Item.TooltipContext.EMPTY, tip, TooltipFlag.NORMAL);
        h.assertTrue(tip.stream().anyMatch(c -> c.getContents() instanceof TranslatableContents t && t.getKey().equals("item.headinajar.sample.nitwit")),
                "the tooltip does not say HR has checked twice");
        // the Centrifuge keeps the flag, and a read of the fraction adds nothing to the library
        h.setBlock(FLOOR.west(4), ModRegistry.CENTRIFUGE.get());
        var c = (LabMachines.Centrifuge) h.getBlockEntity(FLOOR.west(4));
        c.energy().set(20_000);
        c.items().setStackInSlot(0, fromNitwit.copy());
        for (int i = 0; i < LabMachines.Centrifuge.TICKS; i++) LabMachines.Centrifuge.serverTick(h.getLevel(), c.getBlockPos(), c.getBlockState(), c);
        ItemStack fraction = c.items().getStackInSlot(1);
        h.assertTrue(Samples.nitwit(fraction), "the Centrifuge dropped the Nitwit flag");
        h.setBlock(FLOOR, ModRegistry.DNA_SEQUENCER.get());
        var s = (LabMachines.Sequencer) h.getBlockEntity(FLOOR);
        s.energy().set(40_000);
        s.items().setStackInSlot(0, fraction);
        h.assertTrue(s.button(p, LabMachines.Sequencer.AUTO), "auto did not start");
        for (int t = 0; t < LabMachines.Sequencer.TICKS; t++) LabMachines.Sequencer.serverTick(h.getLevel(), s.getBlockPos(), s.getBlockState(), s);
        h.assertValueEqual(GeneArchive.get(h.getLevel().getServer()).readout(GeneArchive.key(p), Gene.HEROS_BLOOD), 0f, "Hero's Blood read from a Nitwit");
        done(h, p);
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void theCowApologyComesOnce(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(2));
        Cow first = h.spawn(EntityType.COW, FLOOR.west(2)), second = h.spawn(EntityType.COW, FLOOR.north(2));
        var pig = h.spawn(EntityType.PIG, FLOOR.south(2));
        ItemStack syringe = new ItemStack(ModRegistry.SYRINGE.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, syringe);
        var tool = (GeneticsItems.SyringeItem) syringe.getItem();
        tool.draw(p, syringe, pig, InteractionHand.MAIN_HAND);
        h.assertFalse(Memos.sent(p, Memos.Memo.COW_APOLOGY), "a pig brought the cow's apology");
        tool.draw(p, syringe, first, InteractionHand.MAIN_HAND);
        h.assertTrue(Memos.sent(p, Memos.Memo.COW_APOLOGY), "no apology to the cow");
        h.assertTrue(earned(h, p, "apology_to_the_cow"), "no hidden advancement");
        h.assertTrue(h.getLevel().getServer().getAdvancements().get(HeadInAJar.id("apology_to_the_cow")).value().display().orElseThrow().isHidden(),
                "the advancement is not hidden");
        // a second cow: the memo is a one-time memo (its bit is set), the advancement stays done
        tool.draw(p, syringe, second, InteractionHand.MAIN_HAND);
        h.assertFalse(Memos.send(p, Memos.Memo.COW_APOLOGY), "the apology could be sent twice");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void pairsAreCounted(GameTestHelper h) {
        h.assertValueEqual(Gene.SYNERGIES.size(), 27, "synergies");
        h.assertValueEqual(Gene.CONFLICTS.size(), 18, "conflicts");
        Set<String> seen = new HashSet<>();
        for (boolean synergy : new boolean[]{true, false}) {
            for (Gene[] pair : synergy ? Gene.SYNERGIES : Gene.CONFLICTS) {
                h.assertTrue(seen.add(pair[0] + "+" + pair[1]) && seen.add(pair[1] + "+" + pair[0]), "a pair twice: " + pair[0] + " " + pair[1]);
                Map<Gene, Float> full = Map.of(pair[0], 100f, pair[1], 100f);
                int alone = 100 - pair[0].stability - pair[1].stability;
                int got = Genetics.stability(List.of(pair[0], pair[1]), full, 0);
                int want = Math.max(0, Math.min(100, alone + (synergy ? 10 : -15)));
                h.assertValueEqual(got, want, (synergy ? "synergy " : "conflict ") + pair[0] + " + " + pair[1]);
                h.assertTrue(net.minecraft.locale.Language.getInstance().has(Gene.pairKey(pair, synergy)), "no name for " + Gene.pairKey(pair, synergy));
            }
        }
        h.succeed();
    }

    @GameTest(template = "platform")
    public static void aPickByDepartmentIsATodayPick(GameTestHelper h) {
        ServerPlayer p = player(h, FLOOR.east(2));
        var archive = GeneArchive.get(h.getLevel().getServer());
        archive.add(GeneArchive.key(p), Gene.FETCH, 100);
        archive.add(GeneArchive.key(p), Gene.RUMINANT_GUT, 35);
        archive.add(GeneArchive.key(p), Gene.TONGUE, 20);
        h.setBlock(FLOOR, ModRegistry.GENE_SPLICER.get());
        var s = (LabMachines.Splicer) h.getBlockEntity(FLOOR);
        // the screen sends PICK + slot x 100 + ordinal: Fetch is ordinal 88, the last the encoding holds below CLEAR (99)
        h.assertValueEqual(Gene.FETCH.ordinal(), 88, "Fetch's ordinal");
        h.assertTrue(s.button(p, LabMachines.Splicer.PICK + 2 * 100 + Gene.FETCH.ordinal()), "a pick of ordinal 88 was refused");
        h.assertTrue(s.button(p, LabMachines.Splicer.PICK + Gene.RUMINANT_GUT.ordinal()), "a Draft gene was refused");
        h.assertFalse(s.button(p, LabMachines.Splicer.PICK + 100 + Gene.TONGUE.ordinal()), "a gene under Draft was picked");
        // the department of each new gene is its species' department
        h.assertValueEqual(Gene.RUMINANT_GUT.department(), "livestock", "Ruminant Gut's department");
        h.assertValueEqual(Gene.FETCH.department(), "logistics", "Fetch's department");
        h.assertValueEqual(Gene.DECAY_AURA.department(), "senior_management", "Decay Aura's department");
        h.assertValueEqual(Gene.DRACONIC_WINGS.department(), "board", "Draconic Wings' department");
        h.assertValueEqual(Gene.HICCUPS.department(), "general", "a mutation's department");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void thePlacardCyclesThroughTheShippedNotices(GameTestHelper h) {
        List<Integer> seen = new ArrayList<>();
        int n = 0;
        for (int i = 0; i < SafetyPlacardBlock.SHIPPED.length; i++) {
            n = SafetyPlacardBlock.next(n);
            seen.add(n);
        }
        h.assertValueEqual(seen, List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 16, 18, 0), "the cycle (12 from 0.6.3)");
        // a notice not yet shipped (set by command) goes back to the first
        h.assertValueEqual(SafetyPlacardBlock.next(13), 0, "an unshipped notice's next");
        h.assertTrue(SafetyPlacardBlock.NOTICE.getPossibleValues().size() == 21, "the property is not 0..20");
        h.succeed();
    }
}
