package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.UUID;

/**
 * The Neural Socket: an organic computer. A jar plugged in (SOCKET form) drives six redstone channels, one per face (number
 * keys set the selected face's level, scroll picks the face, R pulses it), and reads each face's input, the item count of a
 * touching inventory and the fill of a touching tank. With no jar, a mob head runs a fixed program: a Zombie is a 1 Hz
 * clock, a Villager steps through an 8-step staircase, an Enderman fires when a player looks at it.
 */
public class NeuralSocketBlockEntity extends BlockEntity implements HasItems, HeadHolderBlock.Holder {
    public static final int CHANNELS = 6, PULSE_TICKS = 2;
    private final int[] out = new int[CHANNELS];
    private final int[] pulse = new int[CHANNELS];
    private final ItemStackHandler items = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    };
    private UUID occupant;

    public NeuralSocketBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.SOCKET_BE.get(), pos, state);
    }

    @Override
    public ItemStackHandler items() {
        return items;
    }

    public ItemStack head() {
        return items.getStackInSlot(0);
    }

    public UUID occupant() {
        return occupant;
    }

    /** The redstone level on a face (pulses count as 15). */
    public int output(Direction face) {
        int i = face.get3DDataValue();
        return pulse[i] > 0 ? 15 : out[i];
    }

    public void setOutput(Direction face, int level) {
        int i = face.get3DDataValue();
        int v = Math.max(0, Math.min(15, level));
        if (out[i] == v) return;
        out[i] = v;
        changed();
    }

    public void pulse(Direction face) {
        pulse[face.get3DDataValue()] = PULSE_TICKS;
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }

    /** The redstone each face receives from outside. */
    public int[] inputs() {
        int[] in = new int[CHANNELS];
        for (Direction d : Direction.values()) in[d.get3DDataValue()] = level.getSignal(worldPosition.relative(d), d);
        return in;
    }

    /** Items in touching inventories and the fill of touching tanks: {items, tank mB, tank capacity}. */
    public int[] reads() {
        int count = 0, fill = 0, cap = 0;
        for (Direction d : Direction.values()) {
            BlockPos at = worldPosition.relative(d);
            var inv = level.getCapability(Capabilities.ItemHandler.BLOCK, at, d.getOpposite());
            if (inv != null) for (int i = 0; i < inv.getSlots(); i++) count += inv.getStackInSlot(i).getCount();
            var tank = level.getCapability(Capabilities.FluidHandler.BLOCK, at, d.getOpposite());
            if (tank != null) {
                for (int i = 0; i < tank.getTanks(); i++) {
                    fill += tank.getFluidInTank(i).getAmount();
                    cap += tank.getTankCapacity(i);
                }
            }
        }
        return new int[]{count, fill, cap};
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, NeuralSocketBlockEntity s) {
        boolean pulsed = false;
        for (int i = 0; i < CHANNELS; i++) {
            if (s.pulse[i] > 0 && --s.pulse[i] == 0) pulsed = true;
        }
        if (pulsed) s.changed();
        if (s.occupant != null) {
            Player p = level.getPlayerByUUID(s.occupant);
            if (!(p instanceof ServerPlayer sp) || FormActions.state(sp).form() != Form.SOCKET || sp.distanceToSqr(Vec3.atCenterOf(pos)) > 4) {
                s.occupant = null;
                s.clear();
            } else if (level.getGameTime() % 10 == 0) {
                Network.socketStatus(sp, s);
            }
            return;
        }
        EntityType<?> type = HeadJobs.type(s.head());
        if (type == null) return;
        long t = level.getGameTime();
        int program;
        if (type == EntityType.ZOMBIE || type == EntityType.HUSK || type == EntityType.DROWNED) program = (t % 20) < 10 ? 15 : 0;
        else if (type == EntityType.VILLAGER) program = (int) ((t / 10) % 8) * 2 + 1;
        else if (type == EntityType.ENDERMAN) program = stared(level, pos) ? 15 : 0;
        else return;
        for (Direction d : Direction.values()) s.setOutput(d, program);
    }

    /** Whether any player within 16 blocks is looking straight at the socket. */
    private static boolean stared(Level level, BlockPos pos) {
        Vec3 c = Vec3.atCenterOf(pos);
        for (Player p : level.getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(pos).inflate(16))) {
            Vec3 to = c.subtract(p.getEyePosition());
            if (to.length() < 16 && to.normalize().dot(p.getLookAngle()) > 0.99) return true;
        }
        return false;
    }

    private void clear() {
        for (int i = 0; i < CHANNELS; i++) {
            out[i] = 0;
            pulse[i] = 0;
        }
        changed();
    }

    /** A jar plugs in (JAR form), or a mob head goes in, or an empty hand takes the head back out. */
    @Override
    public void use(ServerPlayer player, ItemStack held) {
        JarState state = FormActions.state(player);
        if (state.form() == Form.JAR && occupant == null && head().isEmpty()) {
            plugIn(player);
            return;
        }
        if (head().isEmpty() && HeadJobs.type(held) != null && occupant == null) {
            items.setStackInSlot(0, held.split(1));
            return;
        }
        if (held.isEmpty() && !head().isEmpty()) {
            FormActions.give(player, items.extractItem(0, 1, false));
            clear();
        }
    }

    public boolean plugIn(ServerPlayer player) {
        JarState state = FormActions.state(player);
        if (state.form() != Form.JAR || occupant != null) return false;
        occupant = player.getUUID();
        state.setForm(Form.SOCKET);
        Vec3 top = Vec3.atBottomCenterOf(worldPosition.above());
        player.teleportTo((ServerLevel) level, top.x, top.y, top.z, player.getYRot(), player.getXRot());
        player.refreshDimensions();
        Network.sync(player);
        level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6f, 1.6f);
        player.displayClientMessage(Component.translatable("socket.headinajar.plugged"), true);
        setChanged();
        return true;
    }

    /** Sneak + J (or the socket breaking): back to a plain jar. */
    public static boolean unplug(ServerPlayer player) {
        JarState state = FormActions.state(player);
        if (state.form() != Form.SOCKET) return false;
        state.setForm(Form.JAR);
        player.refreshDimensions();
        Network.sync(player);
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", items.serializeNBT(registries));
        tag.putIntArray("Out", out);
        if (occupant != null) tag.putUUID("Occupant", occupant);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.deserializeNBT(registries, tag.getCompound("Items"));
        int[] saved = tag.getIntArray("Out");
        for (int i = 0; i < CHANNELS && i < saved.length; i++) out[i] = saved[i];
        occupant = tag.hasUUID("Occupant") ? tag.getUUID("Occupant") : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("Items", items.serializeNBT(registries));
        tag.putBoolean("Synced", true);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
