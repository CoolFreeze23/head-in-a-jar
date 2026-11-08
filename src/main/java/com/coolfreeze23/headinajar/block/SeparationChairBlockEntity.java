package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.entity.SeatEntity;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.item.CollarCamItem;
import com.coolfreeze23.headinajar.item.LifeSupportJarItem;
import com.coolfreeze23.headinajar.item.RelayGogglesItem;
import com.coolfreeze23.headinajar.menu.SeparationChairMenu;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Separation Chair: holds the parts and a full energy buffer, and runs the 12-second operation on the player
 * who sat down and signed. Once the cut has started it always finishes.
 */
public class SeparationChairBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOT_JAR = 0, SLOT_GOGGLES = 1, SLOT_COLLAR = 2, SLOT_SALINE = 3;
    public static final int SALINE_NEEDED = 4;
    public static final int CAPACITY = 50_000;
    public static final int OP_TICKS = 240;
    public static final int CUT_TICK = 100;

    private final Buffer energy = new Buffer(CAPACITY, 2_000, 0, this::setChanged);
    private final ItemStackHandler items = new ItemStackHandler(4) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return validFor(slot, stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == SLOT_SALINE ? 16 : 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private int wear;
    private int opTick = -1;
    private UUID patient;
    private ItemStack inJar = ItemStack.EMPTY, inGoggles = ItemStack.EMPTY, inCollar = ItemStack.EMPTY;
    /** Client copy of the operation clock, advanced locally between syncs. */
    private int clientTick = -1;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energy.getEnergyStored() / 10;
                case 1 -> opTick;
                case 2 -> wear;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    public SeparationChairBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.CHAIR_BE.get(), pos, state);
    }

    public static boolean validFor(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_JAR -> stack.getItem() instanceof LifeSupportJarItem;
            case SLOT_GOGGLES -> stack.getItem() instanceof RelayGogglesItem;
            case SLOT_COLLAR -> stack.getItem() instanceof CollarCamItem;
            case SLOT_SALINE -> stack.is(ModRegistry.SALINE_BOTTLE.get());
            default -> false;
        };
    }

    public Buffer energy() {
        return energy;
    }

    public ItemStackHandler items() {
        return items;
    }

    public int wear() {
        return wear;
    }

    public void setWear(int wear) {
        this.wear = Math.max(0, Math.min(100, wear));
        setChanged();
    }

    public boolean running() {
        return opTick >= 0;
    }

    public int opTick() {
        return level != null && level.isClientSide ? clientTick : opTick;
    }

    public UUID patient() {
        return patient;
    }

    public static int energyCost() {
        return Config.get(Config.BOOTH_ENERGY, 40_000);
    }

    /** What still stops the operation, as translation keys; empty means ready. */
    public List<String> missing() {
        List<String> out = new ArrayList<>();
        if (energy.getEnergyStored() < energyCost()) out.add("power");
        if (items.getStackInSlot(SLOT_JAR).isEmpty()) out.add("jar");
        if (items.getStackInSlot(SLOT_GOGGLES).isEmpty()) out.add("goggles");
        if (items.getStackInSlot(SLOT_COLLAR).isEmpty()) out.add("collar");
        if (items.getStackInSlot(SLOT_SALINE).getCount() < SALINE_NEEDED) out.add("saline");
        return out;
    }

    /** The player in the chair, if any. */
    public ServerPlayer seated() {
        if (!(level instanceof ServerLevel server)) return null;
        for (SeatEntity seat : server.getEntitiesOfClass(SeatEntity.class, new net.minecraft.world.phys.AABB(worldPosition).inflate(0.5))) {
            if (seat.getFirstPassenger() instanceof ServerPlayer player) return player;
        }
        return null;
    }

    /** The player signed Form 27-B. Only the seated player can, and only when nothing is missing. */
    public boolean sign(ServerPlayer player) {
        if (running() || seated() != player) return false;
        List<String> missing = missing();
        if (!missing.isEmpty()) {
            player.displayClientMessage(Component.translatable("dolores.headinajar.refuse." + missing.get(0)), true);
            return false;
        }
        if (FormActions.state(player).form().isDecapitated()) {
            player.displayClientMessage(Component.translatable("dolores.headinajar.refuse.already"), true);
            return false;
        }
        patient = player.getUUID();
        opTick = 0;
        player.closeContainer();
        changed();
        say(player, "start");
        return true;
    }

    private void say(ServerPlayer player, String line) {
        player.displayClientMessage(Component.translatable("dolores.headinajar." + line).withStyle(net.minecraft.ChatFormatting.AQUA), true);
    }

    private void sound(SoundEvent sound, float volume, float pitch) {
        level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, volume, pitch);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SeparationChairBlockEntity chair) {
        if (!chair.running()) return;
        ServerPlayer player = chair.seated();
        if (player == null || !player.getUUID().equals(chair.patient)) {
            // the patient got up (only possible before the cut) or vanished
            if (chair.opTick < CUT_TICK) chair.abort(player, "abort");
            return;
        }
        chair.step(player);
    }

    private void step(ServerPlayer player) {
        ServerLevel server = (ServerLevel) level;
        Vec3 c = Vec3.atCenterOf(worldPosition);
        switch (opTick) {
            case 0 -> {
                sound(SoundEvents.IRON_TRAPDOOR_CLOSE, 1, 0.6f);
                sound(SoundEvents.PISTON_EXTEND, 0.6f, 0.8f);
            }
            case 10 -> sound(SoundEvents.IRON_TRAPDOOR_CLOSE, 1, 0.7f);
            case 20 -> {
                sound(SoundEvents.FIRE_EXTINGUISH, 0.5f, 0.5f);
                say(player, "gas");
            }
            case 60 -> {
                sound(SoundEvents.BEACON_ACTIVATE, 0.7f, 1.8f);
                say(player, "scan");
            }
            case CUT_TICK -> {
                // the critical phase: everything is spent here, and nothing can stop it now
                consume();
                say(player, "cut");
            }
            case 120 -> {
                sound(SoundEvents.SHEEP_SHEAR, 1.2f, 0.5f);
                sound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 0.6f);
                server.sendParticles(new DustParticleOptions(new Vector3f(0.6f, 0.05f, 0.05f), 1.2f), c.x, c.y + 1.4, c.z, 12, 0.2, 0.1, 0.2, 0);
            }
            case 140 -> {
                sound(SoundEvents.BOTTLE_FILL, 1, 0.7f);
                say(player, "jar");
            }
            case 165 -> sound(SoundEvents.BOTTLE_FILL, 1, 0.5f);
            case 180, 190, 200 -> sound(SoundEvents.CHAIN_PLACE, 1, 0.8f + (opTick - 180) * 0.02f);
            case 210 -> sound(SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.6f);
            default -> {
            }
        }
        if (opTick >= 20 && opTick < 60 && opTick % 4 == 0) {
            server.sendParticles(ParticleTypes.EFFECT, c.x, c.y + 1.2, c.z, 3, 0.3, 0.3, 0.3, 0);
        }
        if (opTick >= OP_TICKS) {
            finish(player);
            return;
        }
        opTick++;
        if (opTick % 10 == 0) changed();
    }

    private void consume() {
        inJar = items.extractItem(SLOT_JAR, 1, false);
        inGoggles = items.extractItem(SLOT_GOGGLES, 1, false);
        inCollar = items.extractItem(SLOT_COLLAR, 1, false);
        items.extractItem(SLOT_SALINE, SALINE_NEEDED, false);
        energy.set(energy.getEnergyStored() - energyCost());
    }

    /** Finishes at once (the patient is leaving the server after the cut). */
    public void finishNow(ServerPlayer player) {
        if (!running() || !player.getUUID().equals(patient)) return;
        if (opTick < CUT_TICK) {
            abort(player, "abort");
            return;
        }
        finish(player);
    }

    private void finish(ServerPlayer player) {
        ServerLevel server = (ServerLevel) level;
        // the op is over: release the restraints before standing the patient up
        opTick = -1;
        patient = null;
        player.stopRiding();
        boolean botched = Config.get(Config.BOTCH_ENABLED, true) && wear > 80 && server.random.nextInt(100) < (wear - 80) * 2;
        if (botched) CollarCamItem.setCrooked(inCollar, true);
        Direction front = getBlockState().getValue(SeparationChairBlock.FACING);
        Vec3 outlet = Vec3.atBottomCenterOf(worldPosition.relative(front));
        LifeSupportCradleBlockEntity cradle = LifeSupportCradleBlockEntity.nearbyEmpty(server, worldPosition, 3);
        Vec3 stand = Vec3.atBottomCenterOf(worldPosition).add(front.getStepX() * 0.9, 0, front.getStepZ() * 0.9);
        player.teleportTo(stand.x, stand.y, stand.z);
        FormActions.separate(player, inJar, inGoggles, inCollar, outlet, cradle);
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160));
        if (botched) {
            FormActions.state(player).setRollTicks(600);
            say(player, "oopsie");
        } else {
            say(player, "done");
        }
        sound(SoundEvents.PLAYER_LEVELUP, 0.6f, 0.6f);
        // a Mason on staff halves blade wear
        setWear(wear + Math.round(5 * (1 - 0.5f * HeadJobs.staff(level, worldPosition, "mason"))));
        inJar = inGoggles = inCollar = ItemStack.EMPTY;
        opTick = -1;
        patient = null;
        changed();
    }

    /** Stops before the cut: everything is kept except a quarter of the energy. */
    public void abort(ServerPlayer player, String why) {
        if (!running()) return;
        if (opTick >= CUT_TICK) {
            // after the cut the parts are spent; give back what the chair still holds
            putBack(inJar, SLOT_JAR);
            putBack(inGoggles, SLOT_GOGGLES);
            putBack(inCollar, SLOT_COLLAR);
        } else {
            energy.set(energy.getEnergyStored() - energyCost() / 4);
        }
        inJar = inGoggles = inCollar = ItemStack.EMPTY;
        opTick = -1;
        patient = null;
        sound(SoundEvents.IRON_TRAPDOOR_OPEN, 1, 0.7f);
        if (player != null) say(player, why);
        changed();
    }

    private void putBack(ItemStack stack, int slot) {
        if (stack.isEmpty()) return;
        ItemStack left = items.insertItem(slot, stack, false);
        if (!left.isEmpty() && level != null) {
            net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY() + 1, worldPosition.getZ(), left);
        }
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, SeparationChairBlockEntity chair) {
        if (chair.clientTick >= 0 && chair.clientTick < OP_TICKS) chair.clientTick++;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Energy", energy.serializeNBT(registries));
        tag.put("Items", items.serializeNBT(registries));
        tag.putInt("Wear", wear);
        tag.putInt("OpTick", opTick);
        if (patient != null) tag.putUUID("Patient", patient);
        tag.put("InJar", inJar.saveOptional(registries));
        tag.put("InGoggles", inGoggles.saveOptional(registries));
        tag.put("InCollar", inCollar.saveOptional(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Energy")) energy.deserializeNBT(registries, tag.get("Energy"));
        items.deserializeNBT(registries, tag.getCompound("Items"));
        wear = tag.getInt("Wear");
        opTick = tag.contains("OpTick") ? tag.getInt("OpTick") : -1;
        patient = tag.hasUUID("Patient") ? tag.getUUID("Patient") : null;
        inJar = ItemStack.parseOptional(registries, tag.getCompound("InJar"));
        inGoggles = ItemStack.parseOptional(registries, tag.getCompound("InGoggles"));
        inCollar = ItemStack.parseOptional(registries, tag.getCompound("InCollar"));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("OpTick", opTick);
        tag.putInt("Wear", wear);
        if (patient != null) tag.putUUID("Patient", patient);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        opTick = tag.getInt("OpTick");
        wear = tag.getInt("Wear");
        patient = tag.hasUUID("Patient") ? tag.getUUID("Patient") : null;
        // only resync the client clock when it drifted or the op started or stopped
        if (opTick < 0 || clientTick < 0 || Math.abs(clientTick - opTick) > 5) clientTick = opTick;
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        handleUpdateTag(packet.getTag(), registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.headinajar.separation_chair");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new SeparationChairMenu(id, inventory, this, data);
    }
}
