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
    public static class Centrifuge extends LabMachine implements com.coolfreeze23.headinajar.machine.KitMachine, software.bernie.geckolib.animatable.GeoBlockEntity {
        public static final int TICKS = 160, FE_PER_TICK = 19;
        private final com.coolfreeze23.headinajar.machine.MachineKit kit = new com.coolfreeze23.headinajar.machine.MachineKit(this);
        // synced for drawing: samples in the input (0-4), another follows this one, DNA Fractions and vials racked (0-2
        // each), the vials are plain bottles (dragon's breath)
        private int samples, dna, vials;
        private boolean follows, bottles;
        /** Client: rotor speed (0-1), angle (degrees), the hangers' swing (degrees) and its velocity; last tick's too. */
        public float speed, angle, anglePrev, swing, swingPrev, swingVel;
        public Object whine;

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
            c.spin(level, pos);
            c.show();
        }

        private boolean advanced;

        private void spin(Level level, BlockPos pos) {
            advanced = false;
            ItemStack[] out = results();
            if (out == null) {
                // nothing to spin (or a sample of a creature this world doesn't have): start again from zero
                progress = 0;
                stall = items.getStackInSlot(0).isEmpty() ? MachineMenu.RUNNING : MachineMenu.UNREADABLE;
                return;
            }
            // a full output or a flicker of power pauses the spin; it carries on where it was
            if (!place(1, out[0], true) || !place(2, out[1], true)) {
                stall = MachineMenu.OUTPUT_BLOCKED;
                return;
            }
            if (!spend(FE_PER_TICK)) {
                stall = MachineMenu.NO_POWER;
                return;
            }
            stall = MachineMenu.RUNNING;
            advanced = true;
            if (++progress >= TICKS) {
                progress = 0;
                items.extractItem(0, 1, false);
                place(1, out[0], false);
                place(2, out[1], false);
                level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.6f, 1.6f);
                level.playSound(null, pos, com.coolfreeze23.headinajar.registry.ModSounds.FEEDBACK_DONE.get(), SoundSource.BLOCKS, 1, 1);
                kit.event(com.coolfreeze23.headinajar.machine.MachineKit.EV_FINISH, 0);
            }
        }

        private int powerWindow;

        /** Lamp and what is drawn; the kit sends them when they change. */
        private void show() {
            boolean power = stall == MachineMenu.NO_POWER;
            powerWindow = ((powerWindow << 1) | (power ? 1 : 0)) & 0xFFFFF;
            com.coolfreeze23.headinajar.machine.MachineLamp lamp;
            if (advanced) lamp = powerWindow != 0 ? com.coolfreeze23.headinajar.machine.MachineLamp.LOW : com.coolfreeze23.headinajar.machine.MachineLamp.WORK;
            else if (power) lamp = com.coolfreeze23.headinajar.machine.MachineLamp.LOW;
            else if (stall == MachineMenu.OUTPUT_BLOCKED || stall == MachineMenu.UNREADABLE) lamp = com.coolfreeze23.headinajar.machine.MachineLamp.WAIT;
            else lamp = com.coolfreeze23.headinajar.machine.MachineLamp.READY;
            ItemStack in = items.getStackInSlot(0);
            samples = Math.min(4, in.getCount());
            follows = in.getCount() > 1;
            dna = Math.min(2, items.getStackInSlot(1).getCount());
            vials = Math.min(2, items.getStackInSlot(2).getCount());
            bottles = items.getStackInSlot(2).is(Items.GLASS_BOTTLE);
            boolean active = progress > 0 || advanced;
            kit.tick(lamp, advanced, active, progress);
        }

        @Override
        public com.coolfreeze23.headinajar.machine.MachineKit kit() {
            return kit;
        }

        @Override
        public void writeSync(CompoundTag tag) {
            if (samples != 0) tag.putByte("n", (byte) samples);
            if (follows) tag.putBoolean("next", true);
            if (dna != 0) tag.putByte("dna", (byte) dna);
            if (vials != 0) tag.putByte("vials", (byte) vials);
            if (bottles) tag.putBoolean("bottles", true);
        }

        @Override
        public void readSync(CompoundTag tag) {
            samples = tag.getByte("n");
            follows = tag.getBoolean("next");
            dna = tag.getByte("dna");
            vials = tag.getByte("vials");
            bottles = tag.getBoolean("bottles");
        }

        public int samples() {
            return samples;
        }

        public int dna() {
            return dna;
        }

        public int vials() {
            return vials;
        }

        public boolean bottles() {
            return bottles;
        }

        /** Something to collect (the flag). */
        public boolean hasOutput() {
            return dna > 0 || vials > 0;
        }

        /** Spun with one to three samples on four hangers: it wobbles, and the sign asks for balance. */
        public boolean unbalanced() {
            return samples > 0 && samples < 4;
        }

        /**
         * The rotor (code, per client tick): speed chased toward the spin's pace while a sample runs (through its end if
         * another follows, else easing off from 130), the hangers on a damped spring toward 60 degrees times speed
         * squared, so they lift late, swing past and settle.
         */
        @Override
        public void clientTick() {
            anglePrev = angle;
            swingPrev = swing;
            Level level = getLevel();
            if (level == null) return;
            double e = kit.elapsed(level.getGameTime());
            float target = e >= 0 && kit.advancing() && (e < 130 || follows) ? kit.rate() / 4f : 0;
            if (e >= 0 && !kit.advancing() && getBlockState().getValue(com.coolfreeze23.headinajar.machine.MachineLamp.LAMP) == com.coolfreeze23.headinajar.machine.MachineLamp.LOW) target = 0.15f;
            speed += (target - speed) * 0.0625f;
            if (target == 0 && speed < 0.005f) speed = 0;
            angle += 45 * speed;
            swingVel = swingVel * 0.88f + 0.08f * (60 * speed * speed - swing);
            swing += swingVel;
            if (speed == 0 && Math.abs(swing) < 0.05f && Math.abs(swingVel) < 0.05f) {
                swing = 0;
                swingVel = 0;
            }
            com.coolfreeze23.headinajar.client.MachineLoopSound.follow(this, com.coolfreeze23.headinajar.machine.MachineSounds.CENTRIFUGE_WHINE, speed, 0.8f + 0.4f * speed);
        }

        @Override
        public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
            return kit.syncTag();
        }

        @Override
        public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
            return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
        }

        @Override
        public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
            kit.read(tag);
        }

        @Override
        public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
            kit.read(packet.getTag());
        }

        @Override
        public boolean triggerEvent(int id, int param) {
            return kit.triggerEvent(id, param);
        }

        @Override
        public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inventory, net.minecraft.world.entity.player.Player player) {
            kit.startOpen(player);
            return super.createMenu(id, inventory, player);
        }

        @Override
        public net.minecraft.world.phys.Vec3 refusalOrigin() {
            return net.minecraft.world.phys.Vec3.atCenterOf(getBlockPos());
        }

        private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache cache = software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);

        @Override
        public void registerControllers(software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
            com.coolfreeze23.headinajar.client.MachineAnimations.centrifuge(this, controllers);
        }

        @Override
        public software.bernie.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() {
            return cache;
        }

        @Override
        public double getTick(Object blockEntity) {
            return com.coolfreeze23.headinajar.client.MachineAnimations.now(this);
        }
    }

    // --- DNA Sequencer -------------------------------------------------------------------------------

    /**
     * Reads a DNA Fraction into the gene library. Auto-read: 4,000 FE over 10 s at 60% accuracy. Or call the bases
     * yourself (the screen's minigame): 12 bases, 50% accuracy plus 50% scaled by how many you get right, instantly.
     */
    public static class Sequencer extends LabMachine implements com.coolfreeze23.headinajar.machine.KitMachine, software.bernie.geckolib.animatable.GeoBlockEntity {
        public static final int TICKS = 200, FE_PER_TICK = 20, COST = TICKS * FE_PER_TICK, BASES = 12;
        /** Block event: a player's called read, its param the calls that were right (bit i for call i). */
        public static final int EV_TALLY = com.coolfreeze23.headinajar.machine.MachineKit.EV_OWN;
        /** Block event: a key struck in the screen while a player calls a read (param: call index * 4 + base). */
        public static final int EV_BEAT = EV_TALLY + 1;
        /** Menu buttons: a key struck in the screen (KEY + index * 4 + base), for everyone to see the register ring it. */
        public static final int KEY = 16;
        private final com.coolfreeze23.headinajar.machine.MachineKit kit = new com.coolfreeze23.headinajar.machine.MachineKit(this);
        private boolean loaded, autoShown;
        private int seedShown;
        private boolean advanced;
        /** Client: the auto-read's beat now (index 0-11, -1 none) and the game time it was struck. */
        public int beat = -1;
        public long beatAt = Long.MIN_VALUE / 2;
        private double lastProgress = -1;
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
            if (id >= KEY && id < KEY + 4 * BASES) {
                // a key struck while calling: the register rings it (nothing is read until the twelfth)
                kit.event(EV_BEAT, id - KEY);
                return true;
            }
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
                // the register rings up the calls: one slot each, right or wrong (the seed is drawn again by the read)
                int[] want = bases(seed);
                int mask = 0, encoded = id - CALL;
                for (int i = 0; i < BASES; i++) {
                    if ((encoded & 3) == want[i]) mask |= 1 << i;
                    encoded >>= 2;
                }
                kit.event(EV_TALLY, mask);
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

        /** The server's tick, then what the register shows: the lamp, the fraction on top, the auto-read's clock. */
        public static void kitTick(Level level, BlockPos pos, BlockState state, Sequencer s) {
            int before = s.progress;
            serverTick(level, pos, state, s);
            s.advanced = s.progress > before || s.progress == 0 && before >= TICKS - 1;
            boolean running = s.auto && !s.items.getStackInSlot(0).isEmpty() && s.stall == MachineMenu.RUNNING;
            com.coolfreeze23.headinajar.machine.MachineLamp lamp;
            if (s.auto && s.stall == MachineMenu.NO_POWER) lamp = com.coolfreeze23.headinajar.machine.MachineLamp.LOW;
            else if (s.auto && (s.stall == MachineMenu.UNREADABLE || s.stall == MachineMenu.NO_INPUT)) lamp = com.coolfreeze23.headinajar.machine.MachineLamp.WAIT;
            else if (running) lamp = com.coolfreeze23.headinajar.machine.MachineLamp.WORK;
            else lamp = s.energy.getEnergyStored() > 0 ? com.coolfreeze23.headinajar.machine.MachineLamp.READY : com.coolfreeze23.headinajar.machine.MachineLamp.OFF;
            s.loaded = !s.items.getStackInSlot(0).isEmpty();
            s.autoShown = s.auto;
            s.seedShown = s.seed;
            s.kit.tick(lamp, s.advanced, s.auto && s.loaded, s.progress);
        }

        @Override
        public com.coolfreeze23.headinajar.machine.MachineKit kit() {
            return kit;
        }

        public boolean loaded() {
            return loaded;
        }

        public boolean autoOn() {
            return autoShown;
        }

        /** The bases the auto-read strikes (the seed's, as the screen shows them). */
        public int[] shownBases() {
            return bases(seedShown);
        }

        @Override
        public void writeSync(CompoundTag tag) {
            if (loaded) tag.putBoolean("loaded", true);
            if (autoShown) tag.putBoolean("auto", true);
            tag.putInt("seed", seedShown);
        }

        @Override
        public void readSync(CompoundTag tag) {
            loaded = tag.getBoolean("loaded");
            autoShown = tag.getBoolean("auto");
            seedShown = tag.getInt("seed");
        }

        /** The auto-read's beats: struck as its progress crosses each call's tick, then played at full speed (a brown-out spaces them). */
        @Override
        public void clientTick() {
            if (level == null) return;
            double p = kit.elapsed(level.getGameTime());
            if (p < 0) {
                lastProgress = -1;
                return;
            }
            for (int i = 0; i < BASES; i++) {
                int at = 8 + 16 * i;
                if (lastProgress < at && p >= at) {
                    beat = i;
                    beatAt = level.getGameTime();
                }
            }
            if (p < lastProgress) beat = -1;
            lastProgress = p;
        }

        @Override
        public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
            CompoundTag tag = kit.syncTag();
            tag.putBoolean("Synced", true);
            return tag;
        }

        @Override
        public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
            return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
        }

        @Override
        public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
            kit.read(tag);
        }

        @Override
        public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet,
                                 HolderLookup.Provider registries) {
            kit.read(packet.getTag());
        }

        @Override
        public boolean triggerEvent(int id, int param) {
            return kit.triggerEvent(id, param);
        }

        @Override
        public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inventory,
                                                                             net.minecraft.world.entity.player.Player player) {
            kit.startOpen(player);
            return super.createMenu(id, inventory, player);
        }

        private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache cache = software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);

        @Override
        public void registerControllers(software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
            com.coolfreeze23.headinajar.client.MachineAnimations3.sequencer(this, controllers);
        }

        @Override
        public software.bernie.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() {
            return cache;
        }

        @Override
        public double getTick(Object blockEntity) {
            return com.coolfreeze23.headinajar.client.MachineAnimations.now(this);
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
                s.kit.event(com.coolfreeze23.headinajar.machine.MachineKit.EV_FINISH, 0);
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
    public static class Splicer extends LabMachine implements com.coolfreeze23.headinajar.machine.KitMachine, software.bernie.geckolib.animatable.GeoBlockEntity {
        public static final int TICKS = 1200, FE_PER_TICK = 17, MAX_LOAD = Genetics.Target.MECHA.loadCap;
        public static final int SLOT_CATALYST = 0, SLOT_GEL = 1, SLOT_BOTTLE = 2, SLOT_OUT = 3;
        public static final int SPLICE = 1, PICK = 100, CLEAR = 99;
        private final Gene[] strand = new Gene[3];
        private boolean running;
        private int stability = 100, load;

        public Splicer(BlockPos pos, BlockState state) {
            super(ModRegistry.SPLICER_BE.get(), pos, state, LabMenu.Kind.GENE_SPLICER, 40_000);
        }

        // --- the machine kit: what the sewing machine shows (the splice's rule below is untouched) --------------

        private final com.coolfreeze23.headinajar.machine.MachineKit kit = new com.coolfreeze23.headinajar.machine.MachineKit(this);
        /** The risk band of the strand's stability: 0 under 10 %, 1 under 30 %, 2 above (the finish hiccups that many times). */
        public static int band(int stability) {
            float chance = Genetics.mutationChance(stability);
            return chance < 0.1f ? 0 : chance < 0.3f ? 1 : 2;
        }

        // synced: the genes on the spools (ordinal + 1, 0 none), the band, a cassette waiting
        private final int[] spools = new int[3];
        private int band;
        private boolean hasOutput, gelShown;
        private ItemStack catalystShown = ItemStack.EMPTY;
        // server
        private int lastProgress = -1;
        private boolean wasRunning;
        private int lastStitch;
        /** Client: the wheel's phase (in clip ticks of the 80-tick work loop), its speed, last tick's phase; each spool's angle; when each spool was last picked. */
        public float phase, phasePrev, speed;
        public final float[] spoolAngle = new float[3], spoolAnglePrev = new float[3];
        public final long[] pickedAt = {Long.MIN_VALUE / 2, Long.MIN_VALUE / 2, Long.MIN_VALUE / 2};

        @Override
        public com.coolfreeze23.headinajar.machine.MachineKit kit() {
            return kit;
        }

        public int spool(int i) {
            return spools[i];
        }

        public int band() {
            return band;
        }

        public boolean hasOutput() {
            return hasOutput;
        }

        /** What stands behind the head and the pillar (the catalyst, a Bio-Gel), as a client draws it. */
        public ItemStack catalystShown() {
            return catalystShown;
        }

        public boolean gelShown() {
            return gelShown;
        }

        /** The splice's tick, then what the machine shows. */
        public static void kitTick(Level level, BlockPos pos, BlockState state, Splicer s) {
            serverTick(level, pos, state, s);
            s.show(level);
        }

        private int powerWindow;
        /** Client: an update tag has been read before (a spool filled on first sight is not a pick). */
        private boolean seen;

        private void show(Level level) {
            boolean advanced = running && progress > lastProgress && lastProgress >= 0;
            if (wasRunning && !running) {
                // finished (a cassette came out) or cancelled (the strand was emptied)
                boolean made = !items.getStackInSlot(SLOT_OUT).isEmpty();
                kit.event(made ? com.coolfreeze23.headinajar.machine.MachineKit.EV_FINISH : com.coolfreeze23.headinajar.machine.MachineKit.EV_OWN, band);
                if (made) level.playSound(null, worldPosition, com.coolfreeze23.headinajar.registry.ModSounds.FEEDBACK_DONE.get(), SoundSource.BLOCKS, 1, 1);
            }
            wasRunning = running;
            lastProgress = running ? progress : -1;
            for (int i = 0; i < 3; i++) spools[i] = strand[i] == null ? 0 : strand[i].ordinal() + 1;
            if (running || !strand().isEmpty()) band = band(stability);
            hasOutput = !items.getStackInSlot(SLOT_OUT).isEmpty();
            catalystShown = items.getStackInSlot(SLOT_CATALYST).isEmpty() ? ItemStack.EMPTY : new ItemStack(items.getStackInSlot(SLOT_CATALYST).getItem());
            gelShown = !items.getStackInSlot(SLOT_GEL).isEmpty();
            int stitch = running ? Math.min(3, progress * 4 / TICKS) : hasOutput ? 3 : 0;
            if (running && stitch > lastStitch) {
                level.playSound(null, worldPosition, com.coolfreeze23.headinajar.machine.MachineSounds.SPLICER_BELL.get(), SoundSource.BLOCKS, 1, 1);
            }
            lastStitch = stitch;
            boolean power = stall == MachineMenu.NO_POWER;
            powerWindow = ((powerWindow << 1) | (power ? 1 : 0)) & 0xFFFFF;
            com.coolfreeze23.headinajar.machine.MachineLamp lamp;
            if (advanced) lamp = powerWindow != 0 ? com.coolfreeze23.headinajar.machine.MachineLamp.LOW : com.coolfreeze23.headinajar.machine.MachineLamp.WORK;
            else if (running) lamp = power ? com.coolfreeze23.headinajar.machine.MachineLamp.LOW : com.coolfreeze23.headinajar.machine.MachineLamp.WAIT;
            else lamp = energy.getEnergyStored() > 0 ? com.coolfreeze23.headinajar.machine.MachineLamp.READY : com.coolfreeze23.headinajar.machine.MachineLamp.OFF;
            kit.level(GeneSplicerBlock.STITCH, stitch);
            kit.level(GeneSplicerBlock.STAB, band);
            kit.tick(lamp, advanced, running, running ? progress : 0);
        }

        @Override
        public void writeSync(CompoundTag tag) {
            tag.putByteArray("spools", new byte[]{(byte) spools[0], (byte) spools[1], (byte) spools[2]});
            if (band != 0) tag.putByte("band", (byte) band);
            if (hasOutput) tag.putBoolean("out", true);
            if (gelShown) tag.putBoolean("gel", true);
            if (!catalystShown.isEmpty()) tag.putString("cat", BuiltInRegistries.ITEM.getKey(catalystShown.getItem()).toString());
        }

        @Override
        public void readSync(CompoundTag tag) {
            byte[] s = tag.getByteArray("spools");
            long now = level == null ? 0 : level.getGameTime();
            for (int i = 0; i < 3; i++) {
                int v = i < s.length ? s[i] : 0;
                // a gene picked onto a spool while we watched: the spool lifts and turns
                if (v != spools[i] && v != 0 && seen) pickedAt[i] = now;
                spools[i] = v;
            }
            band = tag.getByte("band");
            hasOutput = tag.getBoolean("out");
            gelShown = tag.getBoolean("gel");
            catalystShown = tag.contains("cat") ? new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(tag.getString("cat")))) : ItemStack.EMPTY;
            seen = true;
        }

        /** The wheel's phase chased toward the run's pace (a brown-out slows it, a stall runs it down mid-stitch); spools turn with their genes. */
        @Override
        public void clientTick() {
            phasePrev = phase;
            for (int i = 0; i < 3; i++) spoolAnglePrev[i] = spoolAngle[i];
            if (level == null) return;
            double e = kit.elapsed(level.getGameTime());
            float target = kit.running() && kit.advancing() && e >= 10 ? kit.rate() / 4f : 0;
            speed += (target - speed) * 0.0625f;
            if (target == 0 && speed < 0.005f) speed = 0;
            phase += speed;
            for (int i = 0; i < 3; i++) if (spools[i] != 0) spoolAngle[i] += 9 * speed;
        }

        @Override
        public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
            return kit.syncTag();
        }

        @Override
        public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
            return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
        }

        @Override
        public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
            kit.read(tag);
        }

        @Override
        public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
            kit.read(packet.getTag());
        }

        @Override
        public boolean triggerEvent(int id, int param) {
            return kit.triggerEvent(id, param);
        }

        @Override
        public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inventory, net.minecraft.world.entity.player.Player player) {
            kit.startOpen(player);
            return super.createMenu(id, inventory, player);
        }

        @Override
        public net.minecraft.world.phys.Vec3 refusalOrigin() {
            return net.minecraft.world.phys.Vec3.atCenterOf(getBlockPos());
        }

        private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache cache = software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);

        @Override
        public void registerControllers(software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
            com.coolfreeze23.headinajar.client.MachineAnimations.splicer(this, controllers);
        }

        @Override
        public software.bernie.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() {
            return cache;
        }

        @Override
        public double getTick(Object blockEntity) {
            return com.coolfreeze23.headinajar.client.MachineAnimations.now(this);
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
    public static class InjectionRig extends LabMachine implements com.coolfreeze23.headinajar.machine.KitMachine, software.bernie.geckolib.animatable.GeoBlockEntity {
        public static final int INJECT = 1, COST = 5_000;
        /** The shot: the syringe swings out and jabs at this tick after Inject is pressed; the patient must hold still till then. */
        public static final int JAB = 30;
        /** How far the patient may move from where they stood (blocks) before the shot is called off. */
        public static final double STILL = 1.0;
        /** Block events: the shot (param: the patient's entity id), its result (0 accepted, 1 rejected, 2 mutated), a cancel. */
        public static final int EV_INJECT = com.coolfreeze23.headinajar.machine.MachineKit.EV_OWN, EV_RESULT = EV_INJECT + 1, EV_CANCEL = EV_INJECT + 2;
        private final com.coolfreeze23.headinajar.machine.MachineKit kit = new com.coolfreeze23.headinajar.machine.MachineKit(this);
        private java.util.UUID patient;
        private net.minecraft.world.phys.Vec3 patientAt;
        private long shotAt = Long.MIN_VALUE / 2, resultAt = Long.MIN_VALUE / 2, faultUntil, mutatedUntil;
        private int result = -1;
        private boolean loaded;
        /** Client: who is being given the shot (their entity id), for the arm to aim at. */
        private int patientId = -1;

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
            return start(player);
        }

        /**
         * Inject pressed: the same checks as the shot itself, then the show starts and the shot lands at {@link #JAB}
         * ticks, if the patient has held still (else it is called off and nothing is spent).
         */
        public boolean start(ServerPlayer player) {
            if (patient != null) return refuse(player, "lab.rig.busy");
            if (!ready(player)) return false;
            patient = player.getUUID();
            patientAt = player.position();
            shotAt = level.getGameTime();
            result = -1;
            kit.event(EV_INJECT, player.getId());
            setChanged();
            return true;
        }

        /** What may stop a shot before it starts (a refusal each). */
        private boolean ready(ServerPlayer player) {
            Form form = player.getData(ModRegistry.JAR_STATE).form();
            if (form != Form.BODY && form != Form.LINKED) return refuse(player, "lab.rig.needs_body");
            if (Serum.of(items.getStackInSlot(0)).isEmpty()) return refuse(player, "lab.rig.empty", Component.translatable("item.headinajar.serum_cassette"));
            if (energy.getEnergyStored() < COST) return refuse(player, "lab.no_power", COST, Component.translatable("block.headinajar.galvanic_pile"));
            return true;
        }

        /** The show's server side: the patient checked every tick; the shot at {@link #JAB}; the lamp, the globe, the counter. */
        public static void kitTick(Level level, BlockPos pos, BlockState state, InjectionRig rig) {
            long now = level.getGameTime();
            if (rig.patient != null) {
                ServerPlayer player = level.getServer() == null ? null : level.getServer().getPlayerList().getPlayer(rig.patient);
                boolean still = player != null && player.level() == level && player.position().distanceTo(rig.patientAt) <= STILL;
                if (!still) {
                    // called off: nothing spent; the patient told to hold still (no rattle: the rig did nothing wrong)
                    if (player != null) tell(player, Component.translatable("refusal.headinajar.lab.rig.hold_still"));
                    rig.kit.event(EV_CANCEL, 0);
                    rig.faultUntil = now + 40;
                    rig.patient = null;
                } else if (now - rig.shotAt >= JAB) {
                    rig.patient = null;
                    Genetics.Result r = rig.ready(player) ? rig.inject(player) : null;
                    rig.result = r == Genetics.Result.OK ? 0 : r == Genetics.Result.MUTATED ? 2 : 1;
                    rig.resultAt = now;
                    rig.kit.event(EV_RESULT, rig.result);
                    if (rig.result == 1) rig.faultUntil = now + 40;
                    if (rig.result == 2) rig.mutatedUntil = now + 20;
                    level.playSound(null, pos, com.coolfreeze23.headinajar.machine.MachineSounds.RIG_HISS.get(), net.minecraft.sounds.SoundSource.BLOCKS, 1, 1);
                }
            }
            rig.show(now);
        }

        private void show(long now) {
            ItemStack in = items.getStackInSlot(0);
            var serum = Serum.of(in);
            loaded = serum.isPresent();
            boolean showing = patient != null || now - resultAt < 17;
            com.coolfreeze23.headinajar.machine.MachineLamp lamp;
            if (now < faultUntil) lamp = com.coolfreeze23.headinajar.machine.MachineLamp.FAULT;
            else if (now < mutatedUntil) lamp = com.coolfreeze23.headinajar.machine.MachineLamp.WAIT;
            else if (showing) lamp = com.coolfreeze23.headinajar.machine.MachineLamp.WORK;
            else if (!loaded) lamp = com.coolfreeze23.headinajar.machine.MachineLamp.WAIT;
            else lamp = energy.getEnergyStored() >= COST ? com.coolfreeze23.headinajar.machine.MachineLamp.READY : com.coolfreeze23.headinajar.machine.MachineLamp.LOW;
            int globe = 0;
            if (now < mutatedUntil) globe = 7;
            else if (loaded && !serum.get().genes().isEmpty()) globe = 1 + serum.get().genes().get(0).gene().category.ordinal();
            kit.level(InjectionRigBlock.SERUM, Math.min(7, globe));
            kit.level(InjectionRigBlock.COUNTER, patient != null ? 1 : 0);
            kit.tick(lamp, patient != null, showing, 0);
        }

        @Override
        public com.coolfreeze23.headinajar.machine.MachineKit kit() {
            return kit;
        }

        public boolean loaded() {
            return loaded;
        }

        /** Client: the patient's entity id (-1 none), for the arm's aim. */
        public int patientId() {
            return patientId;
        }

        @Override
        public void writeSync(CompoundTag tag) {
            if (loaded) tag.putBoolean("loaded", true);
        }

        @Override
        public void readSync(CompoundTag tag) {
            loaded = tag.getBoolean("loaded");
        }

        @Override
        public boolean triggerEvent(int id, int param) {
            if (id == EV_INJECT) patientId = param;
            return kit.triggerEvent(id, param);
        }

        @Override
        public void clientTick() {
        }

        @Override
        public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
            CompoundTag tag = kit.syncTag();
            tag.putBoolean("Synced", true);
            return tag;
        }

        @Override
        public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
            return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
        }

        @Override
        public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
            kit.read(tag);
        }

        @Override
        public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet,
                                 HolderLookup.Provider registries) {
            kit.read(packet.getTag());
        }

        @Override
        public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inventory,
                                                                             net.minecraft.world.entity.player.Player player) {
            kit.startOpen(player);
            return super.createMenu(id, inventory, player);
        }

        private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache cache = software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);

        @Override
        public void registerControllers(software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
            com.coolfreeze23.headinajar.client.MachineAnimations3.rig(this, controllers);
        }

        @Override
        public software.bernie.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() {
            return cache;
        }

        @Override
        public double getTick(Object blockEntity) {
            return com.coolfreeze23.headinajar.client.MachineAnimations.now(this);
        }

        /** The shot itself, at the jab (what Inject did at once before the show). */
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
