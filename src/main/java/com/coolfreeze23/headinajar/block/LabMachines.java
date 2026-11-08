package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.genetics.Gene;
import com.coolfreeze23.headinajar.genetics.GeneArchive;
import com.coolfreeze23.headinajar.genetics.Genetics;
import com.coolfreeze23.headinajar.genetics.Serum;
import com.coolfreeze23.headinajar.item.GeneticsItems;
import com.coolfreeze23.headinajar.menu.LabMenu;
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

    // --- Centrifuge ----------------------------------------------------------------------------------

    /** Spins a Blood Sample into a DNA Fraction (same creature and quality) and a Plasma Vial. 3,000 FE over 8 s. */
    public static class Centrifuge extends LabMachine {
        public static final int TICKS = 160, FE_PER_TICK = 19;

        public Centrifuge(BlockPos pos, BlockState state) {
            super(ModRegistry.CENTRIFUGE_BE.get(), pos, state, LabMenu.Kind.CENTRIFUGE, 20_000);
        }

        @Override
        protected boolean valid(int slot, ItemStack stack) {
            return slot == 0 && stack.is(ModRegistry.BLOOD_SAMPLE.get());
        }

        private ItemStack[] results() {
            ItemStack in = items.getStackInSlot(0);
            EntityType<?> species = GeneticsItems.species(in);
            if (species == null) return null;
            return new ItemStack[]{GeneticsItems.sample(ModRegistry.DNA_FRACTION.get(), species, GeneticsItems.quality(in)),
                    new ItemStack(ModRegistry.PLASMA_VIAL.get())};
        }

        public static void serverTick(Level level, BlockPos pos, BlockState state, Centrifuge c) {
            ItemStack[] out = c.results();
            if (out == null || !c.place(1, out[0], true) || !c.place(2, out[1], true) || !c.spend(FE_PER_TICK)) {
                c.progress = 0;
                return;
            }
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
            return stack.is(ModRegistry.DNA_FRACTION.get());
        }

        @Override
        protected int value(int i) {
            return i == 2 ? seed : i == 3 ? (auto ? 1 : 0) : 0;
        }

        @Override
        public boolean button(ServerPlayer player, int id) {
            if (items.getStackInSlot(0).isEmpty()) return false;
            owner = GeneArchive.key(player);
            if (id == AUTO) {
                auto = !auto;
                progress = 0;
                setChanged();
                return true;
            }
            if (id >= CALL && id < CALL + (1 << 24)) {
                if (!spend(COST)) {
                    player.displayClientMessage(Component.translatable("gantry.headinajar.no_power"), true);
                    return false;
                }
                int right = correct(seed, id - CALL);
                read(player.serverLevel(), 0.5f + 0.5f * right / BASES);
                player.displayClientMessage(Component.translatable("message.headinajar.called", right, BASES), true);
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
            items.extractItem(0, 1, false);
            seed = level.random.nextInt(1 << 20);
            setChanged();
            level.playSound(null, worldPosition, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 1, 1.4f);
            for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) if (GeneArchive.key(p).equals(owner)) Network.syncArchive(p);
            return true;
        }

        public static void serverTick(Level level, BlockPos pos, BlockState state, Sequencer s) {
            if (!s.auto || s.items.getStackInSlot(0).isEmpty() || !s.spend(FE_PER_TICK)) {
                s.progress = 0;
                return;
            }
            if (++s.progress >= TICKS) {
                s.progress = 0;
                s.read((ServerLevel) level, AUTO_ACCURACY);
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
            if (running || slot < 0 || slot > 2) return false;
            owner = GeneArchive.key(player);
            if (gene != null) {
                if (GeneArchive.potency(readouts().getOrDefault(gene, 0f)) <= 0) return false;
                for (int i = 0; i < 3; i++) if (i != slot && strand[i] == gene) return false;
            }
            strand[slot] = gene;
            refresh();
            setChanged();
            return true;
        }

        private void refresh() {
            List<Gene> genes = strand();
            stability = Genetics.stability(genes, readouts(), Genetics.catalystBonus(items.getStackInSlot(SLOT_CATALYST)));
            load = genes.stream().mapToInt(g -> g.load).sum();
        }

        @Override
        public boolean button(ServerPlayer player, int id) {
            if (id == SPLICE) {
                if (running) return false;
                owner = GeneArchive.key(player);
                refresh();
                String problem = problem();
                if (problem != null) {
                    player.displayClientMessage(Component.translatable(problem), true);
                    return false;
                }
                running = true;
                progress = 0;
                return true;
            }
            if (id >= PICK && id < PICK + 300) {
                int slot = (id - PICK) / 100, ordinal = (id - PICK) % 100;
                return pick(player, slot, ordinal == CLEAR || ordinal >= Gene.values().length ? null : Gene.values()[ordinal]);
            }
            return false;
        }

        private String problem() {
            if (strand().isEmpty()) return "splicer.headinajar.no_genes";
            if (load > MAX_LOAD) return "splicer.headinajar.too_heavy";
            if (items.getStackInSlot(SLOT_GEL).isEmpty()) return "splicer.headinajar.no_gel";
            if (items.getStackInSlot(SLOT_BOTTLE).isEmpty()) return "splicer.headinajar.no_bottle";
            if (!items.getStackInSlot(SLOT_OUT).isEmpty()) return "splicer.headinajar.out_full";
            return null;
        }

        public static void serverTick(Level level, BlockPos pos, BlockState state, Splicer s) {
            if (level.getGameTime() % 10 == 0) s.refresh();
            if (!s.running) return;
            if (s.problem() != null) {
                s.running = false;
                s.progress = 0;
                return;
            }
            if (!s.spend(FE_PER_TICK)) return;
            if (++s.progress >= TICKS) s.finish();
        }

        /** Makes the cassette now (the tick calls this when the run ends; tests call it directly). */
        public ItemStack finish() {
            refresh();
            Serum serum = Genetics.splice(strand(), readouts(), Genetics.catalystBonus(items.getStackInSlot(SLOT_CATALYST)));
            ItemStack cassette = new ItemStack(ModRegistry.SERUM_CASSETTE.get());
            serum.writeTo(cassette);
            items.extractItem(SLOT_CATALYST, 1, false);
            items.extractItem(SLOT_GEL, 1, false);
            items.extractItem(SLOT_BOTTLE, 1, false);
            place(SLOT_OUT, cassette, false);
            running = false;
            progress = 0;
            refresh();
            if (level != null) level.playSound(null, worldPosition, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1, 0.8f);
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
                strand[i] = id.isEmpty() ? null : Gene.valueOf(id.toUpperCase(java.util.Locale.ROOT));
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
        public boolean button(ServerPlayer player, int id) {
            if (id != INJECT) return false;
            return inject(player) != null;
        }

        public Genetics.Result inject(ServerPlayer player) {
            Form form = player.getData(ModRegistry.JAR_STATE).form();
            if (form != Form.BODY && form != Form.LINKED) {
                player.displayClientMessage(Component.translatable("message.headinajar.rig_needs_body"), true);
                return null;
            }
            var serum = Serum.of(items.getStackInSlot(0));
            if (serum.isEmpty()) return null;
            if (energy.getEnergyStored() < COST) {
                player.displayClientMessage(Component.translatable("gantry.headinajar.no_power"), true);
                return null;
            }
            Genetics.Result result = Genetics.inject(player, serum.get(), Genetics.Target.BODY, player.getRandom());
            GeneticsItems.SerumItem.report(player, result);
            if (result == Genetics.Result.FULL || result == Genetics.Result.TOO_HEAVY) return result;
            spend(COST);
            items.extractItem(0, 1, false);
            com.coolfreeze23.headinajar.form.FormActions.give(player, new ItemStack(Items.GLASS_BOTTLE));
            player.hurt(player.damageSources().generic(), 1);
            Network.syncGenes(player);
            return result;
        }
    }
}
