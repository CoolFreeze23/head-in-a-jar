package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.genetics.Gene;
import com.coolfreeze23.headinajar.genetics.GeneArchive;
import com.coolfreeze23.headinajar.genetics.Genetics;
import com.coolfreeze23.headinajar.genetics.Serum;
import com.coolfreeze23.headinajar.item.GeneticsItems;
import com.coolfreeze23.headinajar.menu.LabMenu;
import com.coolfreeze23.headinajar.menu.MachineMenu;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** The genetics lab: Centrifuge, DNA Sequencer, Gene Splicer and Injection Rig. */
public final class LabMachines {
    private LabMachines() {
    }

    static Component geneName(Gene gene) {
        return Component.translatable("gene.headinajar." + gene.id());
    }

    // --- Centrifuge ----------------------------------------------------------------------------------

    /** Spins a Blood Sample into a DNA Fraction (same creature and quality) and a Plasma Vial. 3,000 FE over 8 s. */
    public static class Centrifuge extends LabMachine {
        public static final int TICKS = 160, FE_PER_TICK = 19;

        public Centrifuge(BlockPos pos, BlockState state) {
            super(ModRegistry.CENTRIFUGE_BE.get(), pos, state, LabMenu.Kind.CENTRIFUGE, 20_000);
        }

        private static final MachineItems.Role[] ROLES = {MachineItems.Role.INPUT, MachineItems.Role.OUTPUT, MachineItems.Role.OUTPUT};

        @Override
        protected boolean valid(int slot, ItemStack stack) {
            return slot == 0 && (stack.is(ModRegistry.BLOOD_SAMPLE.get()) && GeneticsItems.species(stack) != null || stack.is(Items.DRAGON_BREATH));
        }

        @Override
        public MachineItems.Role[] roles() {
            return ROLES;
        }

        private ItemStack[] results() {
            ItemStack in = items.getStackInSlot(0);
            // the one boss you can sample from a bottle: dragon's breath spins down to quality 60
            if (in.is(Items.DRAGON_BREATH)) {
                return new ItemStack[]{GeneticsItems.sample(ModRegistry.DNA_FRACTION.get(), EntityType.ENDER_DRAGON, 60), new ItemStack(Items.GLASS_BOTTLE)};
            }
            EntityType<?> species = GeneticsItems.species(in);
            if (species == null) return null;
            return new ItemStack[]{GeneticsItems.sample(ModRegistry.DNA_FRACTION.get(), species, GeneticsItems.quality(in)),
                    new ItemStack(ModRegistry.PLASMA_VIAL.get())};
        }

        public static void serverTick(Level level, BlockPos pos, BlockState state, Centrifuge c) {
            ItemStack[] out = c.results();
            if (out == null) {
                // nothing to spin (or a sample of a creature this world doesn't have): start again from zero
                c.progress = 0;
                c.stall = c.items.getStackInSlot(0).isEmpty() ? MachineMenu.RUNNING : MachineMenu.UNREADABLE;
                return;
            }
            // a full output or a flicker of power pauses the spin; it carries on where it was
            if (!c.place(1, out[0], true) || !c.place(2, out[1], true)) {
                c.stall = MachineMenu.OUTPUT_BLOCKED;
                return;
            }
            if (!c.spend(FE_PER_TICK)) {
                c.stall = MachineMenu.NO_POWER;
                return;
            }
            c.stall = MachineMenu.RUNNING;
            if (++c.progress >= TICKS) {
                c.progress = 0;
                c.items.extractItem(0, 1, false);
                c.place(1, out[0], false);
                c.place(2, out[1], false);
                level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.6f, 1.6f);
            }
        }
    }

    // --- DNA Sequencer -------------------------------------------------------------------------------

    /**
     * Reads a DNA Fraction into the gene library. Auto-read: 4,000 FE over 10 s at 60% accuracy. Or call the bases
     * yourself (the screen's minigame): 12 bases, 50% accuracy plus 50% scaled by how many you get right, instantly.
     */
    public static class Sequencer extends LabMachine {
        public static final int TICKS = 200, FE_PER_TICK = 20, COST = TICKS * FE_PER_TICK, BASES = 12;
        public static final int AUTO = 1, CALL = 1 << 26;
        public static final float AUTO_ACCURACY = 0.6f;
        private int seed = new Random().nextInt(1 << 20);
        private boolean auto;

        public Sequencer(BlockPos pos, BlockState state) {
            super(ModRegistry.SEQUENCER_BE.get(), pos, state, LabMenu.Kind.DNA_SEQUENCER, 40_000);
        }

        /** The bases to call for a seed: 0-3 = A, C, G, T. Both sides derive them the same way. */
        public static int[] bases(int seed) {
            Random r = new Random(seed * 31L + 7);
            int[] out = new int[BASES];
            for (int i = 0; i < BASES; i++) out[i] = r.nextInt(4);
            return out;
        }

        public static int encode(int[] calls) {
            int v = 0;
            for (int i = BASES - 1; i >= 0; i--) v = v * 4 + (calls[i] & 3);
            return v;
        }

        public static int correct(int seed, int encoded) {
            int[] want = bases(seed);
            int n = 0;
            for (int i = 0; i < BASES; i++) {
                if ((encoded & 3) == want[i]) n++;
                encoded >>= 2;
            }
            return n;
        }

        @Override
        protected boolean valid(int slot, ItemStack stack) {
            return stack.is(ModRegistry.DNA_FRACTION.get()) && GeneticsItems.species(stack) != null;
        }

        @Override
        public MachineItems.Role[] roles() {
            return new MachineItems.Role[]{MachineItems.Role.INPUT};
        }

        /** Whether the fraction in the slot can be read (its creature exists in this world). */
        private boolean readable() {
            return GeneticsItems.species(items.getStackInSlot(0)) != null;
        }

        @Override
        protected int value(int i) {
            return i == 2 ? seed : i == 3 ? (auto ? 1 : 0) : 0;
        }

        @Override
        public boolean button(ServerPlayer player, int id) {
            if (id != AUTO && (id < CALL || id >= CALL + (1 << 24))) return false;
            // switching Auto off always works
            if (id == AUTO && auto) {
                auto = false;
                progress = 0;
                setChanged();
                return true;
            }
            if (items.getStackInSlot(0).isEmpty()) return refuse(player, "lab.sequencer.empty", Component.translatable("item.headinajar.dna_fraction"));
            // a fraction from a creature this world doesn't have: nothing to read, so nothing is spent
            if (!readable()) return refuse(player, "lab.sequencer.unreadable");
            owner = GeneArchive.key(player);
            if (id == AUTO) {
                auto = !auto;
                progress = 0;
                setChanged();
                return true;
            }
            if (id >= CALL && id < CALL + (1 << 24)) {
                if (!spend(COST)) return refuse(player, "lab.no_power", COST, Component.translatable("block.headinajar.galvanic_pile"));
                int right = correct(seed, id - CALL);
                // a jar with a Cortex Coprocessor gets one miscall forgiven
                if (com.coolfreeze23.headinajar.form.FormActions.state(player).has(com.coolfreeze23.headinajar.module.ModuleType.CORTEX_COPROCESSOR)) {
                    right = Math.min(BASES, right + 1);
                }
                read(player.serverLevel(), 0.5f + 0.5f * right / BASES);
                tell(player, Component.translatable("message.headinajar.called", right, BASES));
                return true;
            }
            return false;
        }

        /** Files one fraction's genes with the owner's library. */
        public boolean read(ServerLevel level, float accuracy) {
            ItemStack in = items.getStackInSlot(0);
            EntityType<?> species = GeneticsItems.species(in);
            if (species == null) return false;
            List<Gene> genes = Gene.of(BuiltInRegistries.ENTITY_TYPE.getKey(species).toString());
            GeneArchive archive = GeneArchive.get(level.getServer());
            float gain = Genetics.readGain(GeneticsItems.quality(in), accuracy, genes.size());
            for (Gene g : genes) archive.add(owner, g, gain);
            for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
                if (!GeneArchive.key(p).equals(owner)) continue;
                for (Gene g : genes) if (g.category == Gene.Category.LEGENDARY && archive.readout(owner, g) >= 100) com.coolfreeze23.headinajar.Advance.grant(p, "legendary");
            }
            items.extractItem(0, 1, false);
            seed = level.random.nextInt(1 << 20);
            setChanged();
            level.playSound(null, worldPosition, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 1, 1.4f);
            for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) if (GeneArchive.key(p).equals(owner)) Network.syncArchive(p);
            return true;
        }

        public static void serverTick(Level level, BlockPos pos, BlockState state, Sequencer s) {
            if (!s.auto || s.items.getStackInSlot(0).isEmpty()) {
                s.progress = 0;
                s.stall = s.auto ? MachineMenu.NO_INPUT : MachineMenu.RUNNING;
                return;
            }
            // checked before any FE is spent: an unreadable fraction never burns power
            if (!s.readable()) {
                s.progress = 0;
                s.stall = MachineMenu.UNREADABLE;
                return;
            }
            if (!s.spend(FE_PER_TICK)) {
                s.stall = MachineMenu.NO_POWER;
                return;
            }
            s.stall = MachineMenu.RUNNING;
            if (++s.progress >= TICKS) {
                s.progress = 0;
                // a Librarian on staff reads at up to 90%
                s.read((ServerLevel) level, AUTO_ACCURACY + 0.3f * HeadJobs.staff(level, pos, "librarian"));
            }
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.putInt("Seed", seed);
            tag.putBoolean("Auto", auto);
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            seed = tag.getInt("Seed");
            auto = tag.getBoolean("Auto");
        }
    }

    // --- Gene Splicer --------------------------------------------------------------------------------

    /**
     * Splices up to three library genes (at least Draft, 30%) into a Serum Cassette. Takes Bio-Gel, a glass bottle and an
     * optional catalyst; 20,000 FE over 60 s. The screen shows stability and mutation odds before you commit.
     */
    public static class Splicer extends LabMachine {
        public static final int TICKS = 1200, FE_PER_TICK = 17, MAX_LOAD = Genetics.Target.MECHA.loadCap;
        public static final int SLOT_CATALYST = 0, SLOT_GEL = 1, SLOT_BOTTLE = 2, SLOT_OUT = 3;
        public static final int SPLICE = 1, PICK = 100, CLEAR = 99;
        private final Gene[] strand = new Gene[3];
        private boolean running;
        private int stability = 100, load;

        public Splicer(BlockPos pos, BlockState state) {
            super(ModRegistry.SPLICER_BE.get(), pos, state, LabMenu.Kind.GENE_SPLICER, 40_000);
        }

        @Override
        protected boolean valid(int slot, ItemStack stack) {
            return switch (slot) {
                case SLOT_CATALYST -> Genetics.catalystBonus(stack) > 0;
                case SLOT_GEL -> stack.is(ModRegistry.BIO_GEL.get());
                case SLOT_BOTTLE -> stack.is(Items.GLASS_BOTTLE);
                default -> false;
            };
        }

        private static final MachineItems.Role[] ROLES = {MachineItems.Role.INPUT, MachineItems.Role.INPUT, MachineItems.Role.INPUT, MachineItems.Role.OUTPUT};

        @Override
        public MachineItems.Role[] roles() {
            return ROLES;
        }

        @Override
        protected int value(int i) {
            return switch (i) {
                case 2, 3, 4 -> strand[i - 2] == null ? 0 : strand[i - 2].ordinal() + 1;
                case 5 -> stability;
                case 6 -> Math.round(Genetics.mutationChance(stability) * 100);
                case 7 -> load;
                default -> 0;
            };
        }

        public List<Gene> strand() {
            List<Gene> out = new ArrayList<>();
            for (Gene g : strand) if (g != null) out.add(g);
            return out;
        }

        private Map<Gene, Float> readouts() {
            return level instanceof ServerLevel server && !owner.isEmpty() ? GeneArchive.get(server.getServer()).library(owner) : Map.of();
        }

        /** Sets strand position {@code slot} to {@code gene} (null clears). The owner must have read it to Draft. */
        public boolean pick(ServerPlayer player, int slot, Gene gene) {
            if (slot < 0 || slot > 2) return false;
            if (running) return refuse(player, "lab.splicer.running");
            owner = GeneArchive.key(player);
            if (gene != null) {
                if (GeneArchive.potency(readouts().getOrDefault(gene, 0f)) <= 0) return refuse(player, "lab.splicer.unread", geneName(gene));
                for (int i = 0; i < 3; i++) if (i != slot && strand[i] == gene) return refuse(player, "lab.splicer.duplicate", geneName(gene));
            }
            strand[slot] = gene;
            refresh();
            setChanged();
            return true;
        }

        /** The catalyst's bonus, plus up to 10 for a Cleric on staff. */
        private int catalyst() {
            int clerk = level == null ? 0 : Math.round(10 * HeadJobs.staff(level, worldPosition, "cleric"));
            return Genetics.catalystBonus(items.getStackInSlot(SLOT_CATALYST)) + clerk;
        }

        private void refresh() {
            List<Gene> genes = strand();
            stability = Genetics.stability(genes, readouts(), catalyst());
            load = genes.stream().mapToInt(g -> g.load).sum();
        }

        @Override
        public boolean button(ServerPlayer player, int id) {
            if (id == SPLICE) {
                if (running) return refuse(player, "lab.splicer.running");
                // the run is the presser's: it builds from their library, so every gene in the strand must be theirs
                owner = GeneArchive.key(player);
                refresh();
                Map<Gene, Float> library = readouts();
                for (Gene g : strand()) {
                    if (GeneArchive.potency(library.getOrDefault(g, 0f)) <= 0) return refuse(player, "lab.splicer.unread", geneName(g));
                }
                String problem = problem();
                if (problem != null) return refuse(player, problem, problemArgs(problem));
                running = true;
                progress = 0;
                setChanged();
                return true;
            }
            if (id >= PICK && id < PICK + 300) {
                int slot = (id - PICK) / 100, ordinal = (id - PICK) % 100;
                return pick(player, slot, ordinal == CLEAR || ordinal >= Gene.values().length ? null : Gene.values()[ordinal]);
            }
            return false;
        }

        /** Why the strand can't be spliced now (a refusal reason), or null. */
        private String problem() {
            if (strand().isEmpty()) return "lab.splicer.no_genes";
            if (load > MAX_LOAD) return "lab.splicer.too_heavy";
            if (items.getStackInSlot(SLOT_GEL).isEmpty()) return "lab.splicer.no_gel";
            if (items.getStackInSlot(SLOT_BOTTLE).isEmpty()) return "lab.splicer.no_bottle";
            if (!items.getStackInSlot(SLOT_OUT).isEmpty()) return "lab.splicer.out_full";
            return null;
        }

        private Object[] problemArgs(String problem) {
            return switch (problem) {
                case "lab.splicer.too_heavy" -> new Object[]{load, MAX_LOAD};
                case "lab.splicer.no_gel" -> new Object[]{Component.translatable("item.headinajar.bio_gel")};
                case "lab.splicer.no_bottle" -> new Object[]{Component.translatable("item.minecraft.glass_bottle")};
                case "lab.splicer.out_full" -> new Object[]{Component.translatable("item.headinajar.serum_cassette")};
                default -> new Object[0];
            };
        }

        public static void serverTick(Level level, BlockPos pos, BlockState state, Splicer s) {
            if (level.getGameTime() % 10 == 0) s.refresh();
            if (!s.running) {
                s.stall = MachineMenu.RUNNING;
                return;
            }
            String problem = s.problem();
            if (problem != null) {
                if (s.strand().isEmpty()) {
                    s.running = false;
                    s.progress = 0;
                    s.setChanged();
                    return;
                }
                // gel or bottle taken, or the output not emptied: the run waits with its progress
                s.stall = problem.equals("lab.splicer.out_full") ? MachineMenu.OUTPUT_BLOCKED : MachineMenu.NO_INPUT;
                return;
            }
            if (!s.spend(FE_PER_TICK)) {
                s.stall = MachineMenu.NO_POWER;
                return;
            }
            s.stall = MachineMenu.RUNNING;
            if (++s.progress >= TICKS) s.finish();
        }

        /** Makes the cassette now (the tick calls this when the run ends; tests call it directly). */
        public ItemStack finish() {
            refresh();
            Serum serum = Genetics.splice(strand(), readouts(), catalyst());
            ItemStack cassette = new ItemStack(ModRegistry.SERUM_CASSETTE.get());
            serum.writeTo(cassette);
            items.extractItem(SLOT_CATALYST, 1, false);
            items.extractItem(SLOT_GEL, 1, false);
            items.extractItem(SLOT_BOTTLE, 1, false);
            place(SLOT_OUT, cassette, false);
            running = false;
            progress = 0;
            refresh();
            if (level != null) {
                level.playSound(null, worldPosition, com.coolfreeze23.headinajar.registry.ModSounds.GENE_SPLICE.get(), SoundSource.BLOCKS, 1, 1);
                for (var p : level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, new net.minecraft.world.phys.AABB(worldPosition).inflate(16))) {
                    if (GeneArchive.key(p).equals(owner)) com.coolfreeze23.headinajar.Advance.grant(p, "spliced");
                }
            }
            return cassette;
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            for (int i = 0; i < 3; i++) tag.putString("Gene" + i, strand[i] == null ? "" : strand[i].id());
            tag.putBoolean("Running", running);
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            for (int i = 0; i < 3; i++) {
                String id = tag.getString("Gene" + i);
                strand[i] = Gene.byId(id);
            }
            running = tag.getBoolean("Running");
        }
    }

    // --- Injection Rig -------------------------------------------------------------------------------

    /** Injects the serum in its slot into your own body (BODY or LINKED form). 5,000 FE; up to two serums per body. */
    public static class InjectionRig extends LabMachine {
        public static final int INJECT = 1, COST = 5_000;

        public InjectionRig(BlockPos pos, BlockState state) {
            super(ModRegistry.RIG_BE.get(), pos, state, LabMenu.Kind.INJECTION_RIG, 20_000);
        }

        @Override
        protected boolean valid(int slot, ItemStack stack) {
            return stack.is(ModRegistry.SERUM_CASSETTE.get()) && Serum.of(stack).isPresent();
        }

        @Override
        public MachineItems.Role[] roles() {
            return new MachineItems.Role[]{MachineItems.Role.INPUT};
        }

        @Override
        public boolean button(ServerPlayer player, int id) {
            if (id != INJECT) return false;
            return inject(player) != null;
        }

        public Genetics.Result inject(ServerPlayer player) {
            Form form = player.getData(ModRegistry.JAR_STATE).form();
            if (form != Form.BODY && form != Form.LINKED) {
                refuse(player, "lab.rig.needs_body");
                return null;
            }
            var serum = Serum.of(items.getStackInSlot(0));
            if (serum.isEmpty()) {
                refuse(player, "lab.rig.empty", Component.translatable("item.headinajar.serum_cassette"));
                return null;
            }
            if (energy.getEnergyStored() < COST) {
                refuse(player, "lab.no_power", COST, Component.translatable("block.headinajar.galvanic_pile"));
                return null;
            }
            Genetics.Result result = Genetics.inject(player, serum.get(), Genetics.Target.BODY, player.getRandom());
            if (result == Genetics.Result.FULL || result == Genetics.Result.TOO_HEAVY) {
                refuse(player, result == Genetics.Result.FULL ? "lab.rig.full" : "lab.rig.too_heavy");
                return result;
            }
            GeneticsItems.SerumItem.report(player, result);
            // the screen hides the action bar: say it over the screen too
            tell(player, Component.translatable("message.headinajar.inject." + result.name().toLowerCase(java.util.Locale.ROOT)));
            if (result == Genetics.Result.OK || result == Genetics.Result.MUTATED) GeneticsItems.SerumItem.injected(player, serum.get(), player);
            spend(COST);
            items.extractItem(0, 1, false);
            com.coolfreeze23.headinajar.form.FormActions.give(player, new ItemStack(Items.GLASS_BOTTLE));
            player.hurt(player.damageSources().generic(), 1);
            Network.syncGenes(player);
            return result;
        }
    }
}
