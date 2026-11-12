package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.genetics.BloodType;
import com.coolfreeze23.headinajar.item.GeneticsItems;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * A jar of leeches for mob farms: every 30 s it takes a small, low-quality blood sample from a creature standing on or
 * beside it (never players, bosses or the bloodless). Holds nine samples; use it to empty it.
 */
public class LeechJarBlockEntity extends BlockEntity implements HasItems, com.coolfreeze23.headinajar.machine.KitMachine,
        software.bernie.geckolib.animatable.GeoBlockEntity {
    public static final int INTERVAL = 600, MIN = 15, MAX = 35;
    /** Block events: a sample taken (the nozzle pops, the crank turns, a vial drops), the jar emptied by hand. */
    public static final int EV_FEED = com.coolfreeze23.headinajar.machine.MachineKit.EV_OWN, EV_EMPTY = EV_FEED + 1;
    /** How often it looks for a creature beside it (to wake the leech; sampling keeps its own 30 s). */
    public static final int SCAN = 20;
    private final com.coolfreeze23.headinajar.machine.MachineKit kit = new com.coolfreeze23.headinajar.machine.MachineKit(this);
    /** The side a creature is on (a horizontal direction's 2D value), or -1 for none. Synced. */
    private int side = -1;
    /** Full: the leech sulks. Synced. */
    private boolean full;
    /** Client: the carrier's yaw (degrees, the machine's own frame) and last tick's; the Butcher's hat. */
    public float carrier, carrierPrev;
    public boolean hat;
    /** Client: when the leech last woke or settled, and when the jar last filled or was emptied (game time). */
    public double wakeAt = Double.NaN, fullAt = Double.NaN;
    private boolean wasAwake, wasFull;
    private final ItemStackHandler items = new ItemStackHandler(9) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(ModRegistry.BLOOD_SAMPLE.get());
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private int timer;

    public LeechJarBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.LEECH_JAR_BE.get(), pos, state);
    }

    @Override
    public ItemStackHandler items() {
        return items;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LeechJarBlockEntity jar) {
        if (++jar.timer >= INTERVAL) {
            jar.timer = 0;
            if (jar.feed(level)) jar.kit.event(EV_FEED, 0);
        }
        if (level.getGameTime() % SCAN == Math.floorMod(pos.hashCode(), SCAN)) jar.side = jar.scan(level);
        int count = jar.count();
        jar.full = count >= jar.items.getSlots();
        jar.kit.level(LeechJarBlock.SAMPLES, (count + 2) / 3);
        jar.kit.tick(jar.full ? com.coolfreeze23.headinajar.machine.MachineLamp.WAIT : com.coolfreeze23.headinajar.machine.MachineLamp.READY, false, false, 0);
    }

    /** What could be sampled: a living, bloody creature that is not a player. */
    private static boolean sampleable(LivingEntity e) {
        return e.isAlive() && !(e instanceof Player) && !e.getType().is(BloodType.THICK_HIDE) && !BloodType.of(e).bloodless;
    }

    private AABB reach() {
        return new AABB(worldPosition).inflate(0.6, 1, 0.6);
    }

    /** The side of the first creature the next sampling would take (the same box and order), or -1. */
    int scan(Level level) {
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, reach(), LeechJarBlockEntity::sampleable)) {
            double dx = e.getX() - (worldPosition.getX() + 0.5), dz = e.getZ() - (worldPosition.getZ() + 0.5);
            return net.minecraft.core.Direction.fromYRot(Math.toDegrees(Math.atan2(-dx, dz))).get2DDataValue();
        }
        return -1;
    }

    public int count() {
        int n = 0;
        for (int i = 0; i < items.getSlots(); i++) if (!items.getStackInSlot(i).isEmpty()) n++;
        return n;
    }

    public int side() {
        return side;
    }

    public boolean full() {
        return full;
    }

    @Override
    public com.coolfreeze23.headinajar.machine.MachineKit kit() {
        return kit;
    }

    @Override
    public void writeSync(CompoundTag tag) {
        if (side >= 0) tag.putByte("side", (byte) side);
        if (full) tag.putBoolean("full", true);
    }

    @Override
    public void readSync(CompoundTag tag) {
        side = tag.contains("side") ? tag.getByte("side") : -1;
        full = tag.getBoolean("full");
    }

    /** The machine's own turn in quarter turns clockwise from north. */
    private int turns() {
        var state = getBlockState();
        return state.hasProperty(MachineBlock.FACING) ? Math.floorMod(state.getValue(MachineBlock.FACING).get2DDataValue() - 2, 4) : 0;
    }

    /** Client: the carrier turns toward the creature's side (and stays where it was when the creature goes); the hat. */
    @Override
    public void clientTick() {
        carrierPrev = carrier;
        if (level == null) return;
        long now = level.getGameTime();
        boolean awake = side >= 0;
        // a late joiner sees the leech where it is, not waking
        if (Double.isNaN(wakeAt)) {
            wakeAt = fullAt = now - 1000;
            wasAwake = awake;
            wasFull = full;
        }
        if (awake != wasAwake) wakeAt = now;
        if (full != wasFull) fullAt = now;
        wasAwake = awake;
        wasFull = full;
        if (side >= 0) {
            // the side in the machine's own frame (quarter turns clockwise from its front: 1 its right, +X)
            int local = Math.floorMod(Math.floorMod(side - 2, 4) - turns(), 4);
            float target = 90 * local;
            float err = net.minecraft.util.Mth.wrapDegrees(target - carrier);
            carrier += Math.abs(err) < 0.05f ? err : err * 0.15f;
        }
        if (level != null && level.getGameTime() % SCAN == 0) hat = butcherBeside();
    }

    /** A Butcher's head on an Employee Mount facing this jar (as the server's staff rule counts it). */
    private boolean butcherBeside() {
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(d)) instanceof EmployeeMountBlockEntity mount && mount.facesInto(worldPosition)
                    && "butcher".equals(mount.profession())) return true;
        }
        return false;
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

    private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache cache = software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
        com.coolfreeze23.headinajar.client.MachineAnimations5.leech(this, controllers);
    }

    @Override
    public software.bernie.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object blockEntity) {
        return com.coolfreeze23.headinajar.client.MachineAnimations.now(this);
    }

    /** One feeding: returns whether a sample was taken. */
    public boolean feed(Level level) {
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, reach(), LeechJarBlockEntity::sampleable)) {
            // a Butcher on staff draws half again as much
            int base = Math.round((MIN + level.random.nextInt(MAX - MIN + 1)) * (1 + 0.5f * HeadJobs.staff(level, worldPosition, "butcher")));
            ItemStack sample = GeneticsItems.sample(ModRegistry.BLOOD_SAMPLE.get(), e.getType(), GeneticsItems.SyringeItem.quality(e, base));
            for (int i = 0; i < items.getSlots(); i++) {
                if (items.getStackInSlot(i).isEmpty()) {
                    items.setStackInSlot(i, sample);
                    e.hurt(level.damageSources().cactus(), 0.5f);
                    level.playSound(null, worldPosition, SoundEvents.HONEY_DRINK, SoundSource.BLOCKS, 0.5f, 0.6f);
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    public void empty(Player player) {
        int n = 0;
        for (int i = 0; i < items.getSlots(); i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            FormActions.give((ServerPlayer) player, stack.copy());
            items.setStackInSlot(i, ItemStack.EMPTY);
            n++;
        }
        // the crank turns twice and the vials clatter out
        if (n > 0) kit.event(EV_EMPTY, n);
        player.displayClientMessage(Component.translatable("message.headinajar.leech_jar", n), true);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", items.serializeNBT(registries));
        tag.putInt("Timer", timer);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        MachineItems.load(items, tag.getCompound("Items"), registries);
        timer = tag.getInt("Timer");
    }
}
