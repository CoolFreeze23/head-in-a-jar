package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.entity.HeadJarEntity;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A low plate that charges, at 500 FE/t from FE fed into it, a jar standing on it or its owner's head set down on it.
 * It says when it starts, when the jar is full and when it has nothing to give, and sparks while it charges.
 */
public class ChargingDockBlock extends BaseEntityBlock implements com.coolfreeze23.headinajar.machine.TallMachine {
    public static final MapCodec<ChargingDockBlock> CODEC = simpleCodec(ChargingDockBlock::new);
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;
    /** The plate and the column behind it (facing north); the upper cell: the column, the board, the dial and the switch. */
    private static final VoxelShape LOWER = net.minecraft.world.phys.shapes.Shapes.or(Block.box(1, 0, 1, 15, 4, 15), Block.box(5, 0, 15, 9, 16, 16));
    private static final VoxelShape UPPER = net.minecraft.world.phys.shapes.Shapes.or(Block.box(5, 0, 15, 9, 16, 16), Block.box(3, 8, 9, 11, 16, 16),
            Block.box(12, 4, 12, 16, 16, 16), Block.box(1, 4, 13, 16, 11, 16));
    private final VoxelShape[] lower = new VoxelShape[4];
    public static final int RATE = 500;
    /** Height of the plate's top above the block's floor. */
    public static final double TOP = 4 / 16.0;

    /** What a dock last told each jar (or each owner, about their placed head). Not saved. */
    private enum Told { CHARGING, FULL, EMPTY }
    private static final Map<UUID, Told> JARS = new HashMap<>();
    private static final Map<UUID, Told> HEADS = new HashMap<>();

    public ChargingDockBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH)
                .setValue(com.coolfreeze23.headinajar.machine.MachineLamp.LAMP, com.coolfreeze23.headinajar.machine.MachineLamp.OFF));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, com.coolfreeze23.headinajar.machine.MachineLamp.LAMP);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public com.coolfreeze23.headinajar.machine.MachineTopBlock.Kind topKind() {
        return com.coolfreeze23.headinajar.machine.MachineTopBlock.Kind.CHARGING_DOCK;
    }

    @Override
    public VoxelShape upperShapeNorth() {
        return UPPER;
    }

    /** The lamp is under the dial, in the upper half. */
    @Override
    public boolean lampAbove() {
        return true;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);
        if (!state.is(newState.getBlock())) com.coolfreeze23.headinajar.machine.TallMachine.removeTop(level, pos);
    }

    @Override
    public <E extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<E> getTicker(Level level, BlockState state,
                                                                                                          net.minecraft.world.level.block.entity.BlockEntityType<E> type) {
        if (type != ModRegistry.DOCK_BE.get()) return null;
        if (level.isClientSide) return (l, p, s, be) -> ((Entity) be).clientTick();
        return (l, p, s, be) -> ((Entity) be).serverTick();
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int i = facingOf(state).get2DDataValue();
        if (lower[i] == null) lower[i] = com.coolfreeze23.headinajar.machine.TallMachine.rotate(LOWER, facingOf(state));
        return lower[i];
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    /** A click, in any form: a body carrying its head sets it down on the plate; anyone else hears what it holds. */
    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Entity dock)) return net.minecraft.world.InteractionResult.PASS;
        if (level.isClientSide) return net.minecraft.world.InteractionResult.SUCCESS;
        ServerPlayer server = (ServerPlayer) player;
        JarState jar = FormActions.state(server);
        if (jar.form() == Form.LINKED && jar.carryingHead() && FormActions.setDownHeadAt(server, top(pos))) {
            return net.minecraft.world.InteractionResult.CONSUME;
        }
        int stored = dock.energy.getEnergyStored();
        // an empty dock refuses (its switch hops, its lamp goes red), saying what feeds it
        if (stored <= 0) {
            com.coolfreeze23.headinajar.feedback.Refusals.deny(server, com.coolfreeze23.headinajar.feedback.Refuser.at(dock), "dock.empty",
                    0, dock.energy.getMaxEnergyStored(), Component.translatable("block.headinajar.galvanic_pile"));
            return net.minecraft.world.InteractionResult.CONSUME;
        }
        player.displayClientMessage(Component.translatable(stored > 0 ? "message.headinajar.dock_status" : "message.headinajar.dock_empty",
                stored, dock.energy.getMaxEnergyStored(), Component.translatable("block.headinajar.galvanic_pile")), true);
        return net.minecraft.world.InteractionResult.CONSUME;
    }

    /** Where something set down on this dock stands: the middle of the plate's top. */
    public static Vec3 top(BlockPos pos) {
        return Vec3.atBottomCenterOf(pos).add(0, TOP, 0);
    }

    /** The dock a box rests on (a jar's feet, a placed head), if any: one whose plate it overlaps, the nearest first. */
    public static Entity under(Level level, AABB box) {
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        Vec3 centre = new Vec3(box.getCenter().x, box.minY, box.getCenter().z);
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY - 0.3, box.minZ), BlockPos.containing(box.maxX, box.minY + 0.05, box.maxZ))) {
            if (!(level.getBlockEntity(p) instanceof Entity dock)) continue;
            // standing on the plate or sunk into it, not hovering a hop above it
            if (box.minY > p.getY() + TOP + 0.1 || box.maxY < p.getY()) continue;
            if (box.maxX <= p.getX() + 1 / 16.0 || box.minX >= p.getX() + 15 / 16.0 || box.maxZ <= p.getZ() + 1 / 16.0 || box.minZ >= p.getZ() + 15 / 16.0) continue;
            double d = Vec3.atBottomCenterOf(p).distanceToSqr(centre);
            if (d < bestDist) {
                bestDist = d;
                best = dock;
            }
        }
        return best;
    }

    /** The dock the player stands on, if any. */
    public static Entity under(Player player) {
        return under(player.level(), player.getBoundingBox());
    }

    /** The dock a player is looking at within reach, if any. */
    public static BlockPos aimed(ServerPlayer player) {
        HitResult hit = player.pick(player.blockInteractionRange(), 1, false);
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                && player.level().getBlockEntity(block.getBlockPos()) instanceof Entity) return block.getBlockPos();
        return null;
    }

    /** Moves charge from the dock under the player into their jar (every tick for a jarred player). */
    public static void charge(Player player, JarState state) {
        Entity dock = under(player);
        if (dock == null) {
            JARS.remove(player.getUUID());
            return;
        }
        Told told = settle(JARS.get(player.getUUID()), give(dock, state, RATE), state);
        if (told == JARS.put(player.getUUID(), told) || !(player instanceof ServerPlayer server)) return;
        int percent = Math.round(100f * state.charge() / state.chargeCapacity());
        server.displayClientMessage(switch (told) {
            case CHARGING -> Component.translatable("message.headinajar.dock_charging", percent);
            case FULL -> Component.translatable("message.headinajar.dock_full");
            case EMPTY -> Component.translatable("message.headinajar.dock_empty", 0, dock.energy.getMaxEnergyStored(), Component.translatable("block.headinajar.galvanic_pile"));
        }, true);
        if (told == Told.CHARGING) dock.chime();
    }

    /**
     * A placed head resting on a dock takes charge too (called every {@code ticks} ticks from the head). Its owner, out
     * walking their body, is told when it starts and when it is full.
     */
    public static void chargeHead(HeadJarEntity head, ServerPlayer owner, int ticks) {
        Entity dock = under(head.level(), head.getBoundingBox());
        if (dock == null) {
            HEADS.remove(owner.getUUID());
            return;
        }
        JarState state = FormActions.state(owner);
        if (!state.form().isDecapitated() || state.form().isJarred()) return;
        Told told = settle(HEADS.get(owner.getUUID()), give(dock, state, RATE * ticks), state);
        if (told == HEADS.put(owner.getUUID(), told)) return;
        int percent = Math.round(100f * state.charge() / state.chargeCapacity());
        owner.displayClientMessage(switch (told) {
            case CHARGING -> Component.translatable("message.headinajar.dock_head_charging", percent);
            case FULL -> Component.translatable("message.headinajar.dock_head_full");
            case EMPTY -> Component.translatable("message.headinajar.dock_head_empty", Component.translatable("block.headinajar.galvanic_pile"));
        }, true);
        if (told == Told.CHARGING) dock.chime();
    }

    /** A jar told it is full stays "full" while the dock tops up what a few ticks of upkeep took. */
    private static Told settle(Told before, Told now, JarState state) {
        return before == Told.FULL && now == Told.CHARGING && state.charge() >= state.chargeCapacity() * 0.95 ? Told.FULL : now;
    }

    /** Moves up to {@code most} FE into the jar; returns what the dock is doing. */
    private static Told give(Entity dock, JarState state, int most) {
        int room = state.chargeCapacity() - state.charge();
        if (room <= 0) return Told.FULL;
        int moved = Math.min(Math.min(most, room), dock.energy.getEnergyStored());
        if (moved <= 0) return Told.EMPTY;
        dock.energy.set(dock.energy.getEnergyStored() - moved);
        state.setCharge(state.charge() + moved);
        dock.sparks();
        return Told.CHARGING;
    }

    public static class Entity extends BlockEntity implements com.coolfreeze23.headinajar.machine.KitMachine, software.bernie.geckolib.animatable.GeoBlockEntity {
        private final Buffer energy = new Buffer(20_000, 2_000, 0, this::setChanged);
        private long lastSpark;
        // --- the machine kit: what the dock shows ------------------------------------------------------------------
        /** The switch: nobody on the plate, charging, a jar on and nothing to give, the jar full. */
        public static final int OPEN = 0, SHUT = 1, LOW = 2, FULL = 3;
        private final com.coolfreeze23.headinajar.machine.MachineKit kit = new com.coolfreeze23.headinajar.machine.MachineKit(this);
        private int switchState, charge = -1;
        private long chargeSentAt;
        /** Client: the dial's needle (degrees, 0 at E, 180 at F) and its speed; last tick's too. */
        public float needle, needlePrev, needleVel;

        @Override
        public com.coolfreeze23.headinajar.machine.MachineKit kit() {
            return kit;
        }

        /** OPEN, SHUT, LOW or FULL. */
        public int switchState() {
            return switchState;
        }

        /** The occupant's charge in percent (-1 with nobody on the plate). */
        public int charge() {
            return charge;
        }

        /** Who stands on the plate (a jar or a placed head), what it holds, and so the switch and the lamp. */
        void serverTick() {
            if (level == null) return;
            if (level.getGameTime() % 5 == 0) {
                JarState occupant = null;
                AABB above = new AABB(worldPosition).expandTowards(0, 1, 0);
                for (net.minecraft.world.entity.Entity e : level.getEntities((net.minecraft.world.entity.Entity) null, above,
                        e -> e instanceof Player || e instanceof HeadJarEntity)) {
                    if (under(level, e.getBoundingBox()) != this) continue;
                    if (e instanceof Player p && FormActions.state(p).form().isJarred()) occupant = FormActions.state(p);
                    else if (e instanceof HeadJarEntity head && head.ownerId() != null && level.getServer() != null
                            && level.getServer().getPlayerList().getPlayer(head.ownerId()) instanceof ServerPlayer owner) occupant = FormActions.state(owner);
                    if (occupant != null) break;
                }
                int was = switchState;
                if (occupant == null) {
                    switchState = OPEN;
                    charge = -1;
                } else {
                    float f = occupant.chargeCapacity() <= 0 ? 1 : occupant.charge() / (float) occupant.chargeCapacity();
                    if (f >= 1 || switchState == FULL && f >= 0.95f) switchState = FULL;
                    else switchState = energy.getEnergyStored() > 0 ? SHUT : LOW;
                    int percent = Math.round(100 * Math.min(1, f));
                    // the needle's reading: sent on a change of 3 % or more, at most every 10 ticks
                    if (charge < 0 || Math.abs(percent - charge) >= 3 && level.getGameTime() - chargeSentAt >= 10 || percent == 100 && charge != 100) {
                        charge = percent;
                        chargeSentAt = level.getGameTime();
                    }
                }
                if (was != switchState && switchState == FULL) {
                    level.playSound(null, worldPosition, com.coolfreeze23.headinajar.machine.MachineSounds.DOCK_DING.get(), SoundSource.BLOCKS, 1, 1);
                }
            }
            com.coolfreeze23.headinajar.machine.MachineLamp lamp = switch (switchState) {
                case SHUT -> com.coolfreeze23.headinajar.machine.MachineLamp.WORK;
                case LOW -> com.coolfreeze23.headinajar.machine.MachineLamp.LOW;
                case FULL -> com.coolfreeze23.headinajar.machine.MachineLamp.WAIT;
                default -> energy.getEnergyStored() > 0 ? com.coolfreeze23.headinajar.machine.MachineLamp.READY : com.coolfreeze23.headinajar.machine.MachineLamp.OFF;
            };
            kit.tick(lamp, switchState == SHUT, switchState != OPEN, 0);
        }

        @Override
        public void writeSync(CompoundTag tag) {
            if (switchState != OPEN) tag.putByte("sw", (byte) switchState);
            if (charge >= 0) tag.putByte("charge", (byte) charge);
        }

        @Override
        public void readSync(CompoundTag tag) {
            switchState = tag.getByte("sw");
            charge = tag.contains("charge") ? tag.getByte("charge") : -1;
        }

        /** The needle: a damped spring toward the occupant's charge (empty: rest at E; brown-out: below E), pinned at both ends. */
        @Override
        public void clientTick() {
            needlePrev = needle;
            float target = switchState == LOW ? -8 : charge < 0 ? 0 : 180 * charge / 100f;
            double r = kit.since(com.coolfreeze23.headinajar.machine.MachineKit.EV_REFUSE, level == null ? 0 : level.getGameTime());
            if (r >= 0 && r < 8) target += (float) (14 * Math.sin(Math.PI * r / 2));
            needleVel = needleVel * 0.72f + (target - needle) * 0.25f;
            needle += needleVel;
            if (needle < -10 || needle > 190) {
                needle = Math.max(-10, Math.min(190, needle));
                needleVel *= -0.3f;
            }
            if (Math.abs(needleVel) < 0.01f && Math.abs(target - needle) < 0.05f) {
                needle = target;
                needleVel = 0;
            }
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
            com.coolfreeze23.headinajar.client.MachineAnimations3.dock(this, controllers);
        }

        @Override
        public software.bernie.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() {
            return cache;
        }

        @Override
        public double getTick(Object blockEntity) {
            return com.coolfreeze23.headinajar.client.MachineAnimations.now(this);
        }

        public Entity(BlockPos pos, BlockState state) {
            super(ModRegistry.DOCK_BE.get(), pos, state);
        }

        public Buffer energy() {
            return energy;
        }

        /** A few sparks over the plate while it charges, every few ticks. */
        void sparks() {
            if (!(level instanceof ServerLevel server) || server.getGameTime() - lastSpark < 6) return;
            lastSpark = server.getGameTime();
            Vec3 at = top(worldPosition);
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.05, at.z, 3, 0.3, 0.02, 0.3, 0.02);
        }

        /** The sound of charging starting. */
        void chime() {
            if (level != null) level.playSound(null, worldPosition, com.coolfreeze23.headinajar.fx.HouseSounds.DOCK_CHARGING.get(), SoundSource.BLOCKS, 0.5f, 1.8f);
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.put("Energy", energy.serializeNBT(registries));
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            if (tag.contains("Energy")) energy.deserializeNBT(registries, tag.get("Energy"));
        }
    }
}
