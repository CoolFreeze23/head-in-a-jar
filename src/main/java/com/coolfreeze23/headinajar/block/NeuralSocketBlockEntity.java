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
        public boolean isItemValid(int slot, ItemStack stack) {
            return HeadJobs.type(stack) != null;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    };
    private UUID occupant;
    private final HeadInfo.Cache info = new HeadInfo.Cache();

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

    /** What the head is (cached until the slot changes). */
    public HeadInfo headInfo() {
        return info.get(head());
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
                // gone (offline, unplugged, moved): a jar that logs back in is unplugged then (see reconcile)
                s.occupant = null;
                s.clear();
            } else if (level.getGameTime() % 10 == 0) {
                Network.socketStatus(sp, s);
            }
            return;
        }
        EntityType<?> type = s.headInfo().type();
        if (type == null || !HeadJobs.socketJob(type)) {
            // the head came out (by hand or by a hopper), or it has no program: the outputs go quiet
            if (s.lit()) s.clear();
            return;
        }
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

    /** Whether any face gives a signal. */
    private boolean lit() {
        for (int i = 0; i < CHANNELS; i++) if (out[i] != 0 || pulse[i] != 0) return true;
        return false;
    }

    private void clear() {
        for (int i = 0; i < CHANNELS; i++) {
            out[i] = 0;
            pulse[i] = 0;
        }
        changed();
    }

    /** A jar plugs in (JAR form), or a mob head goes in, or an empty hand takes the head back out. Anything else says why not. */
    @Override
    public void use(ServerPlayer player, ItemStack held) {
        JarState state = FormActions.state(player);
        var refuser = com.coolfreeze23.headinajar.feedback.Refuser.at(this);
        if (occupant != null && !occupant.equals(player.getUUID())) {
            Player in = level.getPlayerByUUID(occupant);
            com.coolfreeze23.headinajar.feedback.Refusals.deny(player, refuser, "socket.occupied", in == null ? Component.literal("?") : in.getDisplayName());
            return;
        }
        if (state.form() == Form.JAR && occupant == null && head().isEmpty()) {
            plugIn(player);
            return;
        }
        if (!head().isEmpty()) {
            if (held.isEmpty()) {
                FormActions.give(player, items.extractItem(0, 1, false));
                clear();
                return;
            }
            EntityType<?> type = headInfo().type();
            com.coolfreeze23.headinajar.feedback.Refusals.deny(player, refuser, "socket.has_head", type == null ? head().getHoverName() : type.getDescription());
            return;
        }
        EntityType<?> type = HeadJobs.type(held);
        if (type == null) {
            com.coolfreeze23.headinajar.feedback.Refusals.deny(player, refuser, "socket.needs_head", Component.translatable("item.headinajar.jarred_head"));
            return;
        }
        // only some heads run a program on their own; any other would sit there giving nothing
        if (!HeadJobs.socketJob(type)) {
            com.coolfreeze23.headinajar.feedback.Refusals.deny(player, refuser, "socket.no_program", type.getDescription());
            return;
        }
        items.setStackInSlot(0, held.split(1));
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
        player.displayClientMessage(Component.translatable("socket.headinajar.plugged", Component.keybind("key.headinajar.ability"),
                Component.keybind("key.sneak"), Component.keybind("key.headinajar.jar_menu")), true);
        setChanged();
        return true;
    }

    /** Sneak + J: back to a plain jar. */
    public static boolean unplug(ServerPlayer player) {
        return unplug(player, "socket.headinajar.unplugged");
    }

    /** Back to a plain jar, with a line saying why ({@code line}: a lang key) and the socket's power-down sound. */
    public static boolean unplug(ServerPlayer player, String line) {
        JarState state = FormActions.state(player);
        if (state.form() != Form.SOCKET) return false;
        state.setForm(Form.JAR);
        player.refreshDimensions();
        Network.sync(player);
        player.displayClientMessage(Component.translatable(line), true);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.6f, 1.6f);
        return true;
    }

    /** The socket a plugged-in jar sits on, if it still holds that jar. */
    private static NeuralSocketBlockEntity under(ServerPlayer player) {
        return player.level().getBlockEntity(player.blockPosition().below()) instanceof NeuralSocketBlockEntity socket
                && player.getUUID().equals(socket.occupant) ? socket : null;
    }

    /**
     * A jar in socket form with no socket under it that holds it (the socket was broken or carried off, or it never
     * claimed this jar back after a relog) is put back as a plain jar, and told.
     */
    public static void reconcile(ServerPlayer player) {
        if (FormActions.state(player).form() == Form.SOCKET && under(player) == null) unplug(player, "socket.headinajar.lost");
    }

    /** The SocketInput payload's handler: 0 set a face's level, 1 pulse a face, 2 unplug. */
    public static void input(ServerPlayer player, int action, int face, int value) {
        if (FormActions.state(player).form() != Form.SOCKET) return;
        if (action == 2) {
            unplug(player);
            return;
        }
        NeuralSocketBlockEntity socket = under(player);
        if (socket == null) {
            // the keys would do nothing: say why, and give the jar its controls back
            unplug(player, "socket.headinajar.lost");
            return;
        }
        var side = Direction.from3DDataValue(Math.floorMod(face, 6));
        if (action == 0) socket.setOutput(side, value);
        else if (action == 1) {
            socket.pulse(side);
            com.coolfreeze23.headinajar.Advance.grant(player, "socket_pulse");
        }
    }

    /** The block is going (broken, blown up, carried off): its jar is unplugged on the spot. */
    void removed() {
        if (occupant == null || level == null) return;
        if (level.getPlayerByUUID(occupant) instanceof ServerPlayer player && FormActions.state(player).form() == Form.SOCKET) {
            occupant = null;
            unplug(player, "socket.headinajar.lost");
        }
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
        MachineItems.load(items, tag.getCompound("Items"), registries);
        int[] saved = tag.getIntArray("Out");
        for (int i = 0; i < CHANNELS && i < saved.length; i++) out[i] = saved[i];
        occupant = tag.hasUUID("Occupant") ? tag.getUUID("Occupant") : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("Items", HeadInfo.syncTag(items, registries));
        tag.putBoolean("Synced", true);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
