package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.menu.SalineMixerMenu;
import com.coolfreeze23.headinajar.registry.ModFluids;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Mixes Cranial Saline: 1000 mB water + sugar + kelp + redstone + 2,000 FE over 10 s gives 1000 mB saline.
 * Also bottles saline (250 mB per glass bottle). Water goes in from any side; saline comes out.
 */
public class SalineMixerBlockEntity extends BlockEntity implements MenuProvider, HasItems, com.coolfreeze23.headinajar.machine.KitMachine,
        software.bernie.geckolib.animatable.GeoBlockEntity {
    public static final int SLOT_SUGAR = 0, SLOT_KELP = 1, SLOT_REDSTONE = 2, SLOT_BOTTLE_IN = 3, SLOT_BOTTLE_OUT = 4;
    public static final int TANK = 4000;
    public static final int BATCH_TICKS = 200;
    public static final int FE_PER_TICK = 10;

    private final Buffer energy = new Buffer(20_000, 1_000, 0, this::setChanged);
    private final FluidTank water = new FluidTank(TANK, f -> f.getFluid().isSame(Fluids.WATER)) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final FluidTank saline = new FluidTank(TANK, f -> f.getFluid().isSame(ModFluids.SALINE.source.get())) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final ItemStackHandler items = new ItemStackHandler(5) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case SLOT_SUGAR -> stack.is(Items.SUGAR);
                case SLOT_KELP -> stack.is(Items.KELP) || stack.is(Items.DRIED_KELP);
                case SLOT_REDSTONE -> stack.is(Items.REDSTONE);
                case SLOT_BOTTLE_IN -> stack.is(Items.GLASS_BOTTLE);
                default -> false;
            };
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    /** What pipes see: water in, saline out. */
    private final IFluidHandler pipes = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 2;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return tank == 0 ? water.getFluid() : saline.getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            return TANK;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            // only the water tank is filled from outside; the saline tank is the output
            return tank == 0 && water.isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return water.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return saline.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return saline.drain(maxDrain, action);
        }
    };
    private int progress;
    /** Why mixing is paused (a {@code MachineMenu} stall code). */
    private int stall;
    /** This batch's length in ticks (shorter with a Fisherman on staff). */
    private int batchTicks = BATCH_TICKS;
    private final com.coolfreeze23.headinajar.machine.MachineKit kit = new com.coolfreeze23.headinajar.machine.MachineKit(this);
    /** Ticks of the last 20 the batch waited for power (the brown-out). */
    private int powerWindow;
    private long lastBottled = Long.MIN_VALUE / 2;
    // synced for drawing: ingredient bits (1 sugar, 2 kelp, 4 dried kelp, 8 redstone), empty bottles waiting,
    // filled bottles 0-3, a bottle being filled
    private int ingredients;
    private boolean bottlesIn, bottling;
    private int bottlesOut;
    /** Client: the paddle's speed (0-1) and angle (degrees), this tick and the last. */
    public float paddleSpeed, paddleAngle, paddleAnglePrev;
    /** Client: the paddle's loop sound while it turns. */
    public Object paddleSound;
    public static final MachineItems.Role[] ROLES = {MachineItems.Role.INPUT, MachineItems.Role.INPUT, MachineItems.Role.INPUT,
            MachineItems.Role.INPUT, MachineItems.Role.OUTPUT};

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> energy.getEnergyStored() / 10;
                case 1 -> water.getFluidAmount();
                case 2 -> saline.getFluidAmount();
                case 3 -> progress;
                case 4 -> stall;
                case 5 -> batchTicks;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {
        }

        @Override
        public int getCount() {
            return 6;
        }
    };

    public SalineMixerBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.MIXER_BE.get(), pos, state);
    }

    public Buffer energy() {
        return energy;
    }

    public ItemStackHandler items() {
        return items;
    }

    public IFluidHandler pipes() {
        return pipes;
    }

    public FluidTank water() {
        return water;
    }

    public FluidTank saline() {
        return saline;
    }

    /** What stops a batch now (a {@code MachineMenu} stall code), or 0. */
    private int blocked() {
        boolean stocked = !items.getStackInSlot(SLOT_SUGAR).isEmpty() && !items.getStackInSlot(SLOT_KELP).isEmpty() && !items.getStackInSlot(SLOT_REDSTONE).isEmpty();
        if (water.getFluidAmount() < 1000 || !stocked) return com.coolfreeze23.headinajar.menu.MachineMenu.NO_INPUT;
        if (saline.getSpace() < 1000) return com.coolfreeze23.headinajar.menu.MachineMenu.OUTPUT_BLOCKED;
        if (energy.getEnergyStored() < FE_PER_TICK) return com.coolfreeze23.headinajar.menu.MachineMenu.NO_POWER;
        return 0;
    }

    public int progress() {
        return progress;
    }

    public int batchTicks() {
        return batchTicks;
    }

    @Override
    public com.coolfreeze23.headinajar.machine.MachineKit kit() {
        return kit;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SalineMixerBlockEntity mixer) {
        int blocked = mixer.blocked();
        boolean advanced = false;
        mixer.batchTicks = Math.round(BATCH_TICKS / (1 + 0.25f * HeadJobs.staff(level, pos, "fisherman")));
        // a batch already started waits for what it is missing (power, an ingredient, room) instead of starting over
        mixer.stall = blocked != 0 && (mixer.progress > 0 || blocked != com.coolfreeze23.headinajar.menu.MachineMenu.NO_INPUT) ? blocked : 0;
        if (blocked == 0) {
            mixer.energy.set(mixer.energy.getEnergyStored() - FE_PER_TICK);
            // a Fisherman on staff mixes 25% faster
            advanced = true;
            if (++mixer.progress >= mixer.batchTicks) {
                mixer.progress = 0;
                mixer.kit.event(com.coolfreeze23.headinajar.machine.MachineKit.EV_FINISH, 0);
                level.playSound(null, pos, com.coolfreeze23.headinajar.registry.ModSounds.FEEDBACK_DONE.get(), net.minecraft.sounds.SoundSource.BLOCKS, 1, 1);
                mixer.water.drain(1000, IFluidHandler.FluidAction.EXECUTE);
                for (int slot : new int[]{SLOT_SUGAR, SLOT_KELP, SLOT_REDSTONE}) mixer.items.extractItem(slot, 1, false);
                mixer.saline.fill(new FluidStack(ModFluids.SALINE.source.get(), 1000), IFluidHandler.FluidAction.EXECUTE);
            }
        }
        // bottling
        ItemStack in = mixer.items.getStackInSlot(SLOT_BOTTLE_IN);
        ItemStack out = mixer.items.getStackInSlot(SLOT_BOTTLE_OUT);
        if (!in.isEmpty() && mixer.saline.getFluidAmount() >= 250 && (out.isEmpty() || out.is(ModRegistry.SALINE_BOTTLE.get()) && out.getCount() < out.getMaxStackSize())) {
            mixer.saline.drain(250, IFluidHandler.FluidAction.EXECUTE);
            mixer.items.extractItem(SLOT_BOTTLE_IN, 1, false);
            if (out.isEmpty()) mixer.items.setStackInSlot(SLOT_BOTTLE_OUT, new ItemStack(ModRegistry.SALINE_BOTTLE.get()));
            else out.grow(1);
            mixer.lastBottled = level.getGameTime();
        }
        mixer.show(level, blocked, advanced);
    }

    /** What the machine shows: lamp, levels, and the few things drawn (the kit sends them when they change). */
    private void show(Level level, int blocked, boolean advanced) {
        boolean power = stall == com.coolfreeze23.headinajar.menu.MachineMenu.NO_POWER;
        powerWindow = ((powerWindow << 1) | (power ? 1 : 0)) & 0xFFFFF;
        boolean anyInput = !items.getStackInSlot(SLOT_SUGAR).isEmpty() || !items.getStackInSlot(SLOT_KELP).isEmpty()
                || !items.getStackInSlot(SLOT_REDSTONE).isEmpty() || water.getFluidAmount() > 0;
        com.coolfreeze23.headinajar.machine.MachineLamp lamp;
        if (advanced) lamp = powerWindow != 0 ? com.coolfreeze23.headinajar.machine.MachineLamp.LOW : com.coolfreeze23.headinajar.machine.MachineLamp.WORK;
        else if (stall != 0) lamp = power ? com.coolfreeze23.headinajar.machine.MachineLamp.LOW : com.coolfreeze23.headinajar.machine.MachineLamp.WAIT;
        else lamp = anyInput || blocked == com.coolfreeze23.headinajar.menu.MachineMenu.OUTPUT_BLOCKED
                ? com.coolfreeze23.headinajar.machine.MachineLamp.WAIT : com.coolfreeze23.headinajar.machine.MachineLamp.READY;
        ingredients = (items.getStackInSlot(SLOT_SUGAR).isEmpty() ? 0 : 1)
                | (items.getStackInSlot(SLOT_KELP).is(Items.KELP) ? 2 : 0) | (items.getStackInSlot(SLOT_KELP).is(Items.DRIED_KELP) ? 4 : 0)
                | (items.getStackInSlot(SLOT_REDSTONE).isEmpty() ? 0 : 8);
        bottlesIn = !items.getStackInSlot(SLOT_BOTTLE_IN).isEmpty();
        int out = items.getStackInSlot(SLOT_BOTTLE_OUT).getCount();
        bottlesOut = out <= 0 ? 0 : out == 1 ? 1 : out <= 8 ? 2 : 3;
        bottling = level.getGameTime() - lastBottled < 10;
        kit.level(SalineMixerBlock.SALINE, com.coolfreeze23.headinajar.machine.MachineLamp.step(saline.getFluidAmount(), TANK));
        kit.level(SalineMixerBlock.WATER, com.coolfreeze23.headinajar.machine.MachineLamp.step(water.getFluidAmount(), TANK));
        kit.tick(lamp, advanced, progress > 0 || advanced, progress);
    }

    @Override
    public void writeSync(CompoundTag tag) {
        tag.putShort("len", (short) batchTicks);
        if (ingredients != 0) tag.putByte("ing", (byte) ingredients);
        if (bottlesIn) tag.putBoolean("in", true);
        if (bottlesOut != 0) tag.putByte("out", (byte) bottlesOut);
        if (bottling) tag.putBoolean("bottling", true);
    }

    @Override
    public void readSync(CompoundTag tag) {
        batchTicks = tag.contains("len") ? tag.getShort("len") : BATCH_TICKS;
        ingredients = tag.getByte("ing");
        bottlesIn = tag.getBoolean("in");
        bottlesOut = tag.getByte("out");
        bottling = tag.getBoolean("bottling");
    }

    public int ingredients() {
        return ingredients;
    }

    public boolean bottlesIn() {
        return bottlesIn;
    }

    public int bottlesOut() {
        return bottlesOut;
    }

    /** Something to collect (the flag): saline bottles in the output slot. */
    public boolean hasOutput() {
        return bottlesOut > 0;
    }

    public boolean bottling() {
        return bottling;
    }

    /** The paddle: speed chased toward the batch's pace, a crawl in a brown-out, coasting to rest after. */
    @Override
    public void clientTick() {
        paddleAnglePrev = paddleAngle;
        Level level = getLevel();
        if (level == null) return;
        double e = kit.elapsed(level.getGameTime());
        double a = e < 0 ? -1 : e * 200.0 / Math.max(1, batchTicks);
        boolean low = getBlockState().getValue(com.coolfreeze23.headinajar.machine.MachineLamp.LAMP) == com.coolfreeze23.headinajar.machine.MachineLamp.LOW;
        float target = kit.advancing() && a >= 8 ? kit.rate() / 4f : 0;
        if (low && a >= 0) target = Math.max(target, 0.25f);
        paddleSpeed += (target - paddleSpeed) * 0.0625f;
        if (target == 0 && paddleSpeed < 0.01f) paddleSpeed = 0;
        paddleAngle += 18 * paddleSpeed;
        // seen from ten blocks: bubbles breaking at the surface while it mixes, the tap running or dripping
        long time = level.getGameTime();
        if (kit.advancing() && time % 5 == 0) {
            com.coolfreeze23.headinajar.client.MachineFx.mixerBubble(this, getBlockState().getValue(SalineMixerBlock.SALINE));
        }
        if (bottling && time % 2 == 0) com.coolfreeze23.headinajar.client.MachineFx.mixerDrip(this, true);
        else if (kit.running() && time % 30 == 0) com.coolfreeze23.headinajar.client.MachineFx.mixerDrip(this, false);
        com.coolfreeze23.headinajar.client.MachineLoopSound.follow(this, com.coolfreeze23.headinajar.machine.MachineSounds.MIXER_PADDLE, paddleSpeed, 0.9f + 0.2f * paddleSpeed);
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

    /** Which cap rattles: the canister of what is missing, else the sugar's. */
    @Override
    public int refusalPart(String reason) {
        if (items.getStackInSlot(SLOT_SUGAR).isEmpty()) return 0;
        if (items.getStackInSlot(SLOT_KELP).isEmpty()) return 1;
        if (items.getStackInSlot(SLOT_REDSTONE).isEmpty()) return 2;
        return 0;
    }

    private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache cache = software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
        com.coolfreeze23.headinajar.client.MachineAnimations.mixer(this, controllers);
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Energy", energy.serializeNBT(registries));
        tag.put("Items", items.serializeNBT(registries));
        tag.put("Water", water.writeToNBT(registries, new CompoundTag()));
        tag.put("Saline", saline.writeToNBT(registries, new CompoundTag()));
        tag.putInt("Progress", progress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Energy")) energy.deserializeNBT(registries, tag.get("Energy"));
        MachineItems.load(items, tag.getCompound("Items"), registries);
        water.readFromNBT(registries, tag.getCompound("Water"));
        saline.readFromNBT(registries, tag.getCompound("Saline"));
        progress = tag.getInt("Progress");
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.headinajar.saline_mixer");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        kit.startOpen(player);
        return new SalineMixerMenu(id, inventory, items, data, worldPosition);
    }
}
