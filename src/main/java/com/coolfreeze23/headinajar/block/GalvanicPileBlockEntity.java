package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.machine.KitMachine;
import com.coolfreeze23.headinajar.machine.MachineKit;
import com.coolfreeze23.headinajar.machine.MachineLamp;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.items.ItemStackHandler;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

/** A stack of copper discs and frog legs: burns meat into 20 FE/t and pushes it into neighbouring machines. */
public class GalvanicPileBlockEntity extends BlockEntity implements KitMachine, GeoBlockEntity {
    public static final int CAPACITY = 50_000;
    public static final int RATE = 20;
    public static final int PUSH = 1_000;

    private final Buffer energy = new Buffer(CAPACITY, 0, PUSH, this::setChanged);
    private final ItemStackHandler fuel = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return fuelValue(stack) > 0;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private int burning;
    /** What the piece now burning was worth when it was pulled (saved; an older save takes what is left). */
    private int burnTotal;
    /** The kind of meat last pulled (drawn while the pan is otherwise empty); not saved. */
    private Item lastFuel = Items.ROTTEN_FLESH;
    private final MachineKit kit = new MachineKit(this);

    // synced, for drawing: 8ths of the piece left, pieces in the pan (0-3), the meat
    private int burnStep;
    private int lumps;
    private Item shown = Items.AIR;
    /** Client: the press's chased squeeze (0 at rest, 1 spent), this tick and the last. */
    public float squeeze, squeezePrev;
    private int lastLoopTick = -1;

    public GalvanicPileBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.PILE_BE.get(), pos, state);
    }

    /** FE one item of fuel gives. */
    public static int fuelValue(ItemStack stack) {
        if (stack.is(Items.ROTTEN_FLESH)) return 4_000;
        if (stack.is(Items.SPIDER_EYE)) return 6_000;
        if (stack.is(Tags.Items.FOODS_RAW_MEAT) || stack.is(Tags.Items.FOODS_RAW_FISH)) return 3_000;
        return 0;
    }

    public Buffer energy() {
        return energy;
    }

    public ItemStackHandler fuel() {
        return fuel;
    }

    public int burning() {
        return burning;
    }

    @Override
    public MachineKit kit() {
        return kit;
    }

    /** FE the pile will still make from what it holds: what is burning now and the fuel waiting. */
    public int fuelLeft() {
        ItemStack stack = fuel.getStackInSlot(0);
        return burning + stack.getCount() * fuelValue(stack);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GalvanicPileBlockEntity pile) {
        boolean wasBurning = pile.burning > 0;
        if (pile.burning <= 0 && pile.energy.getEnergyStored() < CAPACITY) {
            ItemStack stack = pile.fuel.getStackInSlot(0);
            int value = fuelValue(stack);
            if (value > 0) {
                pile.lastFuel = stack.getItem();
                pile.fuel.extractItem(0, 1, false);
                pile.burning = value;
                pile.burnTotal = value;
            }
        }
        boolean made = false;
        if (pile.burning > 0 && pile.energy.getEnergyStored() < CAPACITY) {
            int amount = Math.min(RATE, pile.burning);
            pile.burning -= amount;
            pile.energy.set(pile.energy.getEnergyStored() + amount);
            made = true;
        }
        ItemStack stack = pile.fuel.getStackInSlot(0);
        if (wasBurning && pile.burning <= 0 && stack.isEmpty()) pile.kit.event(MachineKit.EV_FINISH, 0);
        // what is drawn: the pieces in the pan (the one burning counts), how much of this one is left
        int pieces = stack.getCount() + (pile.burning > 0 ? 1 : 0);
        pile.lumps = pieces <= 0 ? 0 : pieces == 1 ? 1 : pieces <= 8 ? 2 : 3;
        pile.shown = !stack.isEmpty() ? stack.getItem() : pile.burning > 0 ? pile.lastFuel : Items.AIR;
        pile.burnStep = pile.burning <= 0 || pile.burnTotal <= 0 ? 0 : (int) Math.ceil(8.0 * pile.burning / pile.burnTotal);
        MachineLamp lamp = made ? MachineLamp.WORK
                : pile.burning > 0 ? MachineLamp.WAIT
                : !stack.isEmpty() || pile.energy.getEnergyStored() > 0 ? MachineLamp.READY : MachineLamp.OFF;
        int elapsed = pile.burning > 0 ? (pile.burnTotal - pile.burning) / RATE : 0;
        pile.kit.tick(lamp, made, pile.burning > 0, elapsed);
        pushEnergy(level, pos, pile.energy);
    }

    /** Shares energy among the machines on every side that can take it (not first come, first served). */
    public static void pushEnergy(Level level, BlockPos pos, Buffer energy) {
        PowerBlockEntities.share(level, pos, energy, PUSH);
    }

    // --- what the client draws -----------------------------------------------------------------------------

    @Override
    public void writeSync(CompoundTag tag) {
        tag.putByte("step", (byte) burnStep);
        tag.putByte("lumps", (byte) lumps);
        if (shown != Items.AIR) tag.putString("meat", BuiltInRegistries.ITEM.getKey(shown).toString());
    }

    @Override
    public void readSync(CompoundTag tag) {
        burnStep = tag.getByte("step");
        lumps = tag.getByte("lumps");
        shown = tag.contains("meat") ? BuiltInRegistries.ITEM.get(ResourceLocation.parse(tag.getString("meat"))) : Items.AIR;
    }

    public int burnStep() {
        return burnStep;
    }

    public int lumps() {
        return lumps;
    }

    public Item shown() {
        return shown;
    }

    /** The press's squeeze follows the burn only while the screw turns (the work loop's two quarter turns). */
    @Override
    public void clientTick() {
        squeezePrev = squeeze;
        Level level = getLevel();
        if (level == null) return;
        float spent = burnStep <= 0 ? 0 : 1 - burnStep / 8f;
        double t = kit.elapsed(level.getGameTime());
        MachineLamp lamp = getBlockState().getValue(MachineLamp.LAMP);
        if (t < 0 || burnStep <= 0) {
            // spent and released (the finish unwinds it), or never started
            squeeze += (0 - squeeze) / 4;
        } else if (t < 24) {
            // the refuel turn winds the press back up with the handle (ticks 3-17)
            float k = (float) Math.min(1, Math.max(0, (t - 3) / 14));
            squeeze = squeeze * (1 - k);
        } else if (lamp == MachineLamp.WORK) {
            if (t % 50 >= 26) squeeze += (spent - squeeze) / 4;
        }
        // what says "working" from ten blocks: a bright spark across the terminals at each crank, a wisp off the meat
        if (lamp == MachineLamp.WORK && t >= 24) {
            int loop = (int) Math.floor(t) % 50;
            if (loop != lastLoopTick && (loop == 6 || loop == 35 || loop == 47)) com.coolfreeze23.headinajar.client.MachineFx.pileSpark(this);
            lastLoopTick = loop;
        }
        if (lamp == MachineLamp.WORK && level.getGameTime() % 12 == 0) com.coolfreeze23.headinajar.client.MachineFx.pileWisp(this);
        // the meat twitches every 30 s while it works (offset per pile)
        if (lamp == MachineLamp.WORK && Math.floorMod(level.getGameTime() + getBlockPos().hashCode(), 600L) == 0) {
            com.coolfreeze23.headinajar.client.MachineKeyframes.play(this, "pile.bzzt", 1,
                    com.coolfreeze23.headinajar.client.MachineKeyframes.locator(this, "pan"));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return kit.syncTag();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        kit.read(tag);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        kit.read(packet.getTag());
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        return kit.triggerEvent(id, param);
    }

    // --- GeckoLib ------------------------------------------------------------------------------------------

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
        com.coolfreeze23.headinajar.client.MachineAnimations.pile(this, controllers);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object blockEntity) {
        return com.coolfreeze23.headinajar.client.MachineAnimations.now(this);
    }

    // --- saved ----------------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Energy", energy.serializeNBT(registries));
        tag.put("Fuel", fuel.serializeNBT(registries));
        tag.putInt("Burning", burning);
        tag.putInt("BurnTotal", burnTotal);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Energy")) energy.deserializeNBT(registries, tag.get("Energy"));
        MachineItems.load(fuel, tag.getCompound("Fuel"), registries);
        burning = tag.getInt("Burning");
        burnTotal = tag.contains("BurnTotal") ? tag.getInt("BurnTotal") : burning;
        if (burnTotal < burning) burnTotal = burning;
    }
}
