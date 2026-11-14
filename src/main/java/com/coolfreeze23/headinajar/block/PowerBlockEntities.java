package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.form.RelayRegistry;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.UUID;

/** The small power and link blocks: Leyden Jar battery, Tesla Node, Relay Mast and Signal Jammer. */
public final class PowerBlockEntities {
    private PowerBlockEntities() {
    }

    /**
     * Shares energy among the neighbours that are not of the excluded kind and can take some, at most {@code max} each:
     * an even share each, and what a full one leaves is offered to the rest. The first in line changes every tick, so
     * a remainder too small to split doesn't always go the same way.
     */
    static void push(Level level, BlockPos pos, Buffer energy, int max, Class<?> exclude) {
        if (energy.getEnergyStored() <= 0) return;
        java.util.List<net.neoforged.neoforge.energy.IEnergyStorage> takers = new java.util.ArrayList<>(6);
        for (Direction dir : Direction.values()) {
            BlockPos at = pos.relative(dir);
            // a two-cell machine's upper half answers for the machine below it: its kind is the machine's
            if (exclude != null && exclude.isInstance(level.getBlockEntity(com.coolfreeze23.headinajar.machine.MachineTopBlock.machineAt(level, at)))) continue;
            var target = level.getCapability(Capabilities.EnergyStorage.BLOCK, at, dir.getOpposite());
            if (target != null && target.canReceive() && target.receiveEnergy(1, true) > 0) takers.add(target);
        }
        if (takers.isEmpty()) return;
        java.util.Collections.rotate(takers, (int) (level.getGameTime() % takers.size()));
        int[] given = new int[takers.size()];
        boolean[] full = new boolean[takers.size()];
        int open = takers.size();
        while (open > 0 && energy.getEnergyStored() > 0) {
            int share = Math.max(1, energy.getEnergyStored() / open);
            boolean moved = false;
            for (int i = 0; i < takers.size() && energy.getEnergyStored() > 0; i++) {
                if (full[i]) continue;
                int offer = Math.min(Math.min(share, max - given[i]), energy.getEnergyStored());
                int sent = offer > 0 ? takers.get(i).receiveEnergy(offer, false) : 0;
                if (sent > 0) {
                    energy.set(energy.getEnergyStored() - sent);
                    given[i] += sent;
                    moved = true;
                }
                if (sent < offer || given[i] >= max) {
                    full[i] = true;
                    open--;
                }
            }
            if (!moved) return;
        }
    }

    /** {@link #push} for any block's buffer: the Galvanic Pile and the Cerebral Array share their output the same way. */
    public static void share(Level level, BlockPos pos, Buffer energy, int max) {
        push(level, pos, energy, max, null);
    }

    /** Base: a block entity with an FE buffer that is saved and has a capability. */
    public abstract static class Powered extends BlockEntity {
        protected final Buffer energy;

        protected Powered(BlockEntityType<?> type, BlockPos pos, BlockState state, int capacity, int receive) {
            super(type, pos, state);
            this.energy = new Buffer(capacity, receive, 0, this::setChanged);
        }

        public Buffer energy() {
            return energy;
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

    /** Leyden Jar: a 250 kFE battery that feeds every neighbour except other Leyden Jars. */
    public static class LeydenJar extends Powered implements com.coolfreeze23.headinajar.machine.KitMachine {
        /** A level steps up only this far past its line, and down only this far below it (of the capacity). */
        static final double HYSTERESIS = 0.03;
        private final com.coolfreeze23.headinajar.machine.MachineKit kit = new com.coolfreeze23.headinajar.machine.MachineKit(this);
        /** The charge at each of the last 20 ticks (net inflow over a second lights the lamp's work). */
        private final int[] history = new int[20];
        private boolean historyFilled;
        private int shown = -1;

        public LeydenJar(BlockPos pos, BlockState state) {
            super(ModRegistry.LEYDEN_BE.get(), pos, state, 250_000, 2_000);
        }

        public static void tick(Level level, BlockPos pos, BlockState state, LeydenJar jar) {
            push(level, pos, jar.energy, 2_000, LeydenJar.class);
        }

        @Override
        public com.coolfreeze23.headinajar.machine.MachineKit kit() {
            return kit;
        }

        /** The battery's tick, then what it shows: the charge cells (with hysteresis), the lamp, the spark at full. */
        public static void kitTick(Level level, BlockPos pos, BlockState state, LeydenJar jar) {
            tick(level, pos, state, jar);
            int stored = jar.energy.getEnergyStored(), cap = jar.energy.getMaxEnergyStored();
            int slot = (int) (level.getGameTime() % jar.history.length);
            int before = jar.history[slot];
            boolean charging = jar.historyFilled && stored > before;
            jar.history[slot] = stored;
            if (slot == jar.history.length - 1) jar.historyFilled = true;
            int was = jar.shown < 0 && state.hasProperty(LeydenJarBlock.LEVEL) ? state.getValue(LeydenJarBlock.LEVEL) : jar.shown;
            jar.shown = level(stored, cap, Math.max(0, was));
            if (jar.shown == 4 && was == 3 && level instanceof ServerLevel server) full(server, pos, state);
            jar.kit.level(LeydenJarBlock.LEVEL, jar.shown);
            var lamp = stored <= 0 ? com.coolfreeze23.headinajar.machine.MachineLamp.OFF
                    : charging ? com.coolfreeze23.headinajar.machine.MachineLamp.WORK : com.coolfreeze23.headinajar.machine.MachineLamp.READY;
            jar.kit.tick(lamp, charging, false, 0);
        }

        /** The charge cells 0-4 for {@code stored}, moving off {@code current} only {@link #HYSTERESIS} past a line. */
        public static int level(int stored, int cap, int current) {
            int raw = com.coolfreeze23.headinajar.machine.MachineLamp.step(stored, cap);
            if (raw > current) {
                // up: past the line by the margin (the first cell lights with any charge at all)
                int up = current;
                for (int n = current + 1; n <= raw; n++) if (n == 1 ? stored > 0 : stored >= ((n - 1) / 4.0 + HYSTERESIS) * cap) up = n;
                return up;
            }
            if (raw < current) {
                int down = current;
                for (int n = current; n > raw; n--) {
                    // level n holds while the charge is above (n-1)/4 less the margin (and the last cell until empty)
                    if (n == 1 ? stored <= 0 : stored < ((n - 1) / 4.0 - HYSTERESIS) * cap) down = n - 1;
                    else break;
                }
                return down;
            }
            return current;
        }

        /** Full: one spark from the knob to the handle, and its snap. */
        private static void full(ServerLevel level, BlockPos pos, BlockState state) {
            Vec3 knob = com.coolfreeze23.headinajar.machine.TallMachine.local(pos, state, 0, 29.5, 0);
            Vec3 handle = com.coolfreeze23.headinajar.machine.TallMachine.local(pos, state, 0, 31, 0);
            for (int i = 0; i <= 3; i++) {
                Vec3 p = knob.lerp(handle, i / 3.0);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.04, 0.02, 0.04, 0);
            }
            level.playSound(null, knob.x, knob.y, knob.z, com.coolfreeze23.headinajar.machine.MachineSounds.LEYDEN_FULL.get(),
                    net.minecraft.sounds.SoundSource.BLOCKS, 1, 0.94f + level.random.nextFloat() * 0.12f);
        }

        /** Nothing but its blockstate shows: no fields of its own to send. */
        @Override
        public void writeSync(CompoundTag tag) {
        }

        @Override
        public void readSync(CompoundTag tag) {
        }
    }

    /** Tesla Node: sends up to 2,000 FE/t to its linked node (16 blocks, 5% loss); an unlinked node feeds its neighbours. */
    public static class TeslaNode extends Powered implements com.coolfreeze23.headinajar.machine.KitMachine, software.bernie.geckolib.animatable.GeoBlockEntity {
        public static final int RANGE = 16;
        private BlockPos target;
        // --- the machine kit: what the node shows --------------------------------------------------------------------
        private final com.coolfreeze23.headinajar.machine.MachineKit kit = new com.coolfreeze23.headinajar.machine.MachineKit(this);
        /** The node that sends to this one (set when it links here), for the receiver's ball to face it. */
        private BlockPos source;
        private long sentAt = Long.MIN_VALUE / 2;
        /** Server: the game time power last moved over the link (from the plain tick). */
        private long lastFlow = -100;
        private boolean sending, targetFull;
        /** Client: the ring's spin (degrees) and speed, the ball's drawn aim (degrees, world-relative) and its speed; last tick's too. */
        public float spin, spinPrev, spinSpeed, aim, aimPrev, aimVel;
        /** Client: the ring's height (px) as last drawn, for the arc's start. */
        public float torusY;

        @Override
        public com.coolfreeze23.headinajar.machine.MachineKit kit() {
            return kit;
        }

        public BlockPos source() {
            return source;
        }

        public boolean sending() {
            return sending;
        }

        /** The server's tick, then what the coil shows: the lamp, sending (held 15 ticks), the link. */
        public static void kitTick(Level level, BlockPos pos, BlockState state, TeslaNode node) {
            // a pair linked before receivers knew their sender (no Source saved): tell the receiver, so its ball faces us
            if (node.target != null && level.getBlockEntity(node.target) instanceof TeslaNode other && other.source == null) {
                other.source = pos;
                other.setChanged();
                level.sendBlockUpdated(node.target, other.getBlockState(), other.getBlockState(), 3);
            }
            int before = node.energy.getEnergyStored();
            tick(level, pos, state, node);
            long now = level.getGameTime();
            boolean sent = node.target != null && node.energy.getEnergyStored() < before;
            if (sent) node.sentAt = now;
            node.sending = node.target != null && now - node.sentAt < 15;
            node.targetFull = node.target != null && !sent && level.getBlockEntity(node.target) instanceof TeslaNode other
                    && other.energy.getEnergyStored() >= other.energy.getMaxEnergyStored();
            com.coolfreeze23.headinajar.machine.MachineLamp lamp;
            if (node.sending) lamp = node.kit.rate() > 0 && node.kit.rate() < 4 ? com.coolfreeze23.headinajar.machine.MachineLamp.LOW : com.coolfreeze23.headinajar.machine.MachineLamp.WORK;
            else if (node.targetFull) lamp = com.coolfreeze23.headinajar.machine.MachineLamp.WAIT;
            else lamp = node.energy.getEnergyStored() > 0 || node.target != null ? com.coolfreeze23.headinajar.machine.MachineLamp.READY : com.coolfreeze23.headinajar.machine.MachineLamp.OFF;
            node.kit.tick(lamp, sent, node.sending, 0);
        }

        /** The sender's first wrench click: the coil looks round for its partner. */
        public void perk() {
            kit.event(com.coolfreeze23.headinajar.machine.MachineKit.EV_OWN, 0);
        }

        @Override
        public void writeSync(CompoundTag tag) {
            if (target != null) tag.putLong("to", target.asLong());
            if (source != null) tag.putLong("from", source.asLong());
            if (sending) tag.putBoolean("send", true);
            // the effect kit's names for the link and the flow
            if (target != null) tag.putLong("Target", target.asLong());
            tag.putBoolean("Flowing", flowing());
        }

        @Override
        public void readSync(CompoundTag tag) {
            target = tag.contains("to") ? BlockPos.of(tag.getLong("to")) : null;
            source = tag.contains("from") ? BlockPos.of(tag.getLong("from")) : null;
            sending = tag.getBoolean("send");
        }

        /**
         * The ring's spin chased toward the sending rate (a turn per 40 ticks at full rate, coasting down when it stops),
         * and the ball's aim a spring toward the partner (a sender's target, else the node sending here; 45 unlinked).
         */
        @Override
        public void clientTick() {
            spinPrev = spin;
            aimPrev = aim;
            float rate = sending ? kit.rate() : 0;
            // the coil hums while power goes over the link, louder and higher the more it sends
            com.coolfreeze23.headinajar.client.MachineLoopSound.follow(this, com.coolfreeze23.headinajar.machine.MachineSounds.TESLA_HUM,
                    rate / 4, 0.85f + 0.15f * rate / 4);
            spinSpeed += (9 * rate / 4 - spinSpeed) * 0.0625f;
            if (!sending && spinSpeed < 0.05f) spinSpeed = 0;
            spin += spinSpeed;
            float want = 45;
            BlockPos partner = target != null ? target : source;
            if (partner != null) {
                double dx = partner.getX() - worldPosition.getX(), dz = partner.getZ() - worldPosition.getZ();
                // world-relative: 0 = the ball toward +Z, +90 toward +X
                want = (float) Math.toDegrees(Math.atan2(dx, dz));
            } else {
                want = 45 + turn();
            }
            float err = net.minecraft.util.Mth.wrapDegrees(want - aim);
            aimVel = net.minecraft.util.Mth.clamp(aimVel * 0.5f + 0.15f * err, -20, 20);
            aim += aimVel;
            if (Math.abs(err) < 0.05f && Math.abs(aimVel) < 0.05f) {
                aim = want;
                aimVel = 0;
            }
        }

        /** The machine's own turn (north 0, south 180, west 90, east 270). */
        public float turn() {
            Direction facing = getBlockState().hasProperty(TeslaNodeBlock.FACING) ? getBlockState().getValue(TeslaNodeBlock.FACING) : Direction.NORTH;
            return switch (facing) {
                case SOUTH -> 180;
                case WEST -> 90;
                case EAST -> 270;
                default -> 0;
            };
        }

        /**
         * Where an arc leaves this node: the ball's face toward its aim, 7 px from the axis at the ball's height, in the world.
         * For the effects branch's arc renderer (it draws the arc; this only says where it starts and ends).
         */
        public Vec3 arcStart(float partial) {
            float theta = (float) Math.toRadians(net.minecraft.util.Mth.rotLerp(partial, aimPrev, aim));
            var p = worldPosition;
            return new Vec3(p.getX() + 0.5 + 7 / 16.0 * Math.sin(theta), p.getY() + (27 + torusY) / 16.0, p.getZ() + 0.5 + 7 / 16.0 * Math.cos(theta));
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
            com.coolfreeze23.headinajar.client.MachineAnimations3.tesla(this, controllers);
        }

        @Override
        public software.bernie.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() {
            return cache;
        }

        @Override
        public double getTick(Object blockEntity) {
            return com.coolfreeze23.headinajar.client.MachineAnimations.now(this);
        }

        public TeslaNode(BlockPos pos, BlockState state) {
            super(ModRegistry.TESLA_BE.get(), pos, state, 10_000, 2_000);
        }

        public BlockPos target() {
            return target;
        }

        public void link(BlockPos target) {
            // the receiver is told who sends to it (its ball turns to face the sender); an unlink tells the old one too
            if (level != null && this.target != null && level.getBlockEntity(this.target) instanceof TeslaNode old && worldPosition.equals(old.source)) {
                old.source = null;
                old.setChanged();
            }
            this.target = target;
            if (level != null && target != null && level.getBlockEntity(target) instanceof TeslaNode next) {
                next.source = worldPosition;
                next.setChanged();
            }
            setChanged();
            if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }

        /**
         * Power is moving over this node's link now: on the client the kit's synced send (held 15 ticks after the last
         * packet of power), on the server power moved within the last 10 ticks.
         */
        public boolean flowing() {
            if (level == null || level.isClientSide) return sending;
            return level.getGameTime() - lastFlow < 10;
        }

        /** Client: the node at the other end is loaded here too, so the arc has both ends. */
        public boolean linkedOnClient() {
            return target != null && level != null && level.getBlockEntity(target) instanceof TeslaNode;
        }

        @Override
        public void onLoad() {
            super.onLoad();
            if (level != null && level.isClientSide) com.coolfreeze23.headinajar.client.fx.BlockBeams.loaded(this);
        }

        @Override
        public void setRemoved() {
            super.setRemoved();
            if (level != null && level.isClientSide) com.coolfreeze23.headinajar.client.fx.BlockBeams.unloaded(this);
        }

        public static void tick(Level level, BlockPos pos, BlockState state, TeslaNode node) {
            if (node.target == null) {
                push(level, pos, node.energy, 2_000, TeslaNode.class);
                return;
            }
            if (node.target.distSqr(pos) > RANGE * RANGE || !(level.getBlockEntity(node.target) instanceof TeslaNode other)) return;
            int send = Math.min(2_000, node.energy.getEnergyStored());
            int room = other.energy.getMaxEnergyStored() - other.energy.getEnergyStored();
            int arriving = Math.min(room, send * 95 / 100);
            if (arriving <= 0) return;
            node.energy.set(node.energy.getEnergyStored() - arriving * 100 / 95);
            other.energy.set(other.energy.getEnergyStored() + arriving);
            // the arc is drawn by every watching client from the link and the kit's synced send: no particle packets
            node.lastFlow = level.getGameTime();
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            if (target != null) tag.putLong("Target", target.asLong());
            if (source != null) tag.putLong("Source", source.asLong());
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            target = tag.contains("Target") ? BlockPos.of(tag.getLong("Target")) : null;
            source = tag.contains("Source") ? BlockPos.of(tag.getLong("Source")) : null;
        }
    }

    /** A station in the relay registry: a Relay Mast (extends links) or a Signal Jammer (weakens strangers' links). */
    public static class Station extends Powered implements com.coolfreeze23.headinajar.machine.KitMachine, software.bernie.geckolib.animatable.GeoBlockEntity {
        private final com.coolfreeze23.headinajar.machine.MachineKit kit = new com.coolfreeze23.headinajar.machine.MachineKit(this);
        /** A mast: the next hop of the link it last carried (a mast, or the body), and until when it carries it. */
        private BlockPos aim;
        private long carryingUntil, jamUntil;
        private boolean powered, carrying, jamming;
        /** Client: the dish's yaw (degrees, world-relative) as drawn, and its turn: from, to, started; the droop's change. */
        public float yaw = Float.NaN, yawFrom, yawTo;
        public long turnAt = Long.MIN_VALUE / 2, poweredAt = Long.MIN_VALUE / 2;
        public boolean wasPowered = true, wasJamming;
        /** Client: the Jammer's turbine angle and speed (degrees a tick); last tick's angle. */
        public float angle, anglePrev, speed;
        /** Block event: lightning struck near this mast (its dish flinches). */
        public static final int EV_FLINCH = com.coolfreeze23.headinajar.machine.MachineKit.EV_OWN;
        private final RelayRegistry.Kind kind;
        private final int radius;
        private final int drain;
        private UUID owner;

        public Station(BlockEntityType<?> type, BlockPos pos, BlockState state, RelayRegistry.Kind kind, int radius, int drain) {
            super(type, pos, state, 100_000, 2_000);
            this.kind = kind;
            this.radius = radius;
            this.drain = drain;
        }

        public static Station mast(BlockPos pos, BlockState state) {
            return new Station(ModRegistry.MAST_BE.get(), pos, state, RelayRegistry.Kind.MAST, 96, 1);
        }

        public static Station mast2(BlockPos pos, BlockState state) {
            return new Station(ModRegistry.MAST2_BE.get(), pos, state, RelayRegistry.Kind.MAST, 192, 1);
        }

        public static Station jammer(BlockPos pos, BlockState state) {
            return new Station(ModRegistry.JAMMER_BE.get(), pos, state, RelayRegistry.Kind.JAMMER, 16, 4);
        }

        public void setOwner(UUID owner) {
            this.owner = owner;
            setChanged();
        }

        @Override
        public com.coolfreeze23.headinajar.machine.MachineKit kit() {
            return kit;
        }

        public boolean mast() {
            return kind == RelayRegistry.Kind.MAST;
        }

        public BlockPos aim() {
            return aim;
        }

        public boolean powered() {
            return powered;
        }

        public boolean carrying() {
            return carrying;
        }

        public boolean jamming() {
            return jamming;
        }

        /** A link was routed through this mast: its dish turns to the next hop (a mast, or the body). */
        public void route(BlockPos next, long now) {
            aim = next;
            carryingUntil = now + 40;
        }

        /** This Jammer has just cut a link: it spins up (held 20 ticks). */
        public void jammed(long now) {
            jamUntil = now + 20;
        }

        /** The server's tick, then what the station shows: the lamp, powered, carrying or jamming. */
        public static void kitTick(Level level, BlockPos pos, BlockState state, Station station) {
            tick(level, pos, state, station);
            long now = level.getGameTime();
            station.powered = station.energy.getEnergyStored() > 0;
            station.carrying = station.powered && now < station.carryingUntil;
            station.jamming = station.powered && now < station.jamUntil;
            boolean busy = station.carrying || station.jamming;
            com.coolfreeze23.headinajar.machine.MachineLamp lamp = busy ? com.coolfreeze23.headinajar.machine.MachineLamp.WORK : station.powered ? com.coolfreeze23.headinajar.machine.MachineLamp.READY : com.coolfreeze23.headinajar.machine.MachineLamp.OFF;
            station.kit.tick(lamp, busy, busy, 0);
        }

        @Override
        public void writeSync(CompoundTag tag) {
            if (aim != null) tag.putLong("aim", aim.asLong());
            if (powered) tag.putBoolean("on", true);
            if (carrying) tag.putBoolean("carry", true);
            if (jamming) tag.putBoolean("jam", true);
        }

        @Override
        public void readSync(CompoundTag tag) {
            aim = tag.contains("aim") ? BlockPos.of(tag.getLong("aim")) : null;
            powered = tag.getBoolean("on");
            carrying = tag.getBoolean("carry");
            jamming = tag.getBoolean("jam");
        }

        /** The dish's turn starts on a change of aim; the droop on a change of power; the turbine is chased toward its speed. */
        @Override
        public void clientTick() {
            if (level == null) return;
            long now = level.getGameTime();
            if (aim != null) {
                float want = (float) Math.toDegrees(Math.atan2(aim.getX() - worldPosition.getX(), aim.getZ() - worldPosition.getZ()));
                if (Float.isNaN(yaw)) {
                    yaw = yawFrom = yawTo = want;
                } else if (Math.abs(net.minecraft.util.Mth.wrapDegrees(want - yawTo)) > 0.5f) {
                    yawFrom = yaw;
                    yawTo = yawFrom + net.minecraft.util.Mth.wrapDegrees(want - yawFrom);
                    turnAt = now;
                    com.coolfreeze23.headinajar.client.MachineKeyframes.play(this, "mast.swing", 1, null);
                }
            }
            if (powered != wasPowered) {
                wasPowered = powered;
                poweredAt = now;
                if (!powered && mast()) com.coolfreeze23.headinajar.client.MachineKeyframes.play(this, "mast.droop", 1, null);
            }
            if (jamming && !wasJamming) com.coolfreeze23.headinajar.client.MachineKeyframes.play(this, "jammer.static", 1, null);
            wasJamming = jamming;
            anglePrev = angle;
            float target = jamming ? 36 : powered ? 4.5f : 0;
            speed += (target - speed) * (target > speed ? 0.08f : 0.04f);
            if (target == 0 && speed < 0.01f) speed = 0;
            angle += speed;
            if (!mast()) {
                float u = speed / 36;
                com.coolfreeze23.headinajar.client.MachineLoopSound.follow(this, com.coolfreeze23.headinajar.machine.MachineSounds.JAMMER_WHIRR,
                        speed > 0 ? 0.25f + 0.75f * u : 0, 0.5f + 0.5f * u);
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
            if (id == EV_FLINCH && level != null && level.isClientSide) com.coolfreeze23.headinajar.client.MachineKeyframes.play(this, "mast.flinch", 1, null);
            return kit.triggerEvent(id, param);
        }

        /** Lightning struck within a mast's reach while it rains: every loaded mast within 32 blocks flinches. */
        public static void lightning(ServerLevel level, Vec3 at) {
            if (!level.getLevelData().isRaining()) return;
            int cx = net.minecraft.core.SectionPos.blockToSectionCoord(at.x), cz = net.minecraft.core.SectionPos.blockToSectionCoord(at.z);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (!level.hasChunk(cx + dx, cz + dz)) continue;
                    for (BlockEntity be : level.getChunk(cx + dx, cz + dz).getBlockEntities().values()) {
                        if (be instanceof Station s && s.mast() && Vec3.atCenterOf(be.getBlockPos()).distanceTo(at) <= 32) s.kit.event(EV_FLINCH, 0);
                    }
                }
            }
        }

        private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache cache = software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);

        @Override
        public void registerControllers(software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
            com.coolfreeze23.headinajar.client.MachineAnimations4.station(this, controllers);
        }

        @Override
        public software.bernie.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() {
            return cache;
        }

        @Override
        public double getTick(Object blockEntity) {
            return com.coolfreeze23.headinajar.client.MachineAnimations.now(this);
        }

        public static void tick(Level level, BlockPos pos, BlockState state, Station station) {
            if (station.energy.getEnergyStored() > 0) station.energy.set(station.energy.getEnergyStored() - station.drain);
            if (level.getGameTime() % 20 == 0 && level.getServer() != null) {
                RelayRegistry.get(level.getServer()).put(new RelayRegistry.Station(station.kind, level.dimension(), pos, (int) (station.radius * (station.kind == RelayRegistry.Kind.MAST ? 1 + 0.5f * HeadJobs.staff(level, pos, "cartographer") : 1)),
                        station.owner, station.energy.getEnergyStored(), level.getGameTime(), station.drain));
            }
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            if (owner != null) tag.putUUID("Owner", owner);
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        }
    }
}
