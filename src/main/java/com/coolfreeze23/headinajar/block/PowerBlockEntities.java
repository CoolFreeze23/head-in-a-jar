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
            if (exclude != null && exclude.isInstance(level.getBlockEntity(at))) continue;
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
    public static class LeydenJar extends Powered {
        public LeydenJar(BlockPos pos, BlockState state) {
            super(ModRegistry.LEYDEN_BE.get(), pos, state, 250_000, 2_000);
        }

        public static void tick(Level level, BlockPos pos, BlockState state, LeydenJar jar) {
            push(level, pos, jar.energy, 2_000, LeydenJar.class);
        }
    }

    /** Tesla Node: sends up to 2,000 FE/t to its linked node (16 blocks, 5% loss); an unlinked node feeds its neighbours. */
    public static class TeslaNode extends Powered {
        public static final int RANGE = 16;
        private BlockPos target;

        public TeslaNode(BlockPos pos, BlockState state) {
            super(ModRegistry.TESLA_BE.get(), pos, state, 10_000, 2_000);
        }

        public BlockPos target() {
            return target;
        }

        public void link(BlockPos target) {
            this.target = target;
            setChanged();
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
            if (level.getGameTime() % 20 == 0 && level instanceof ServerLevel server) arc(server, pos, node.target);
        }

        private static void arc(ServerLevel level, BlockPos from, BlockPos to) {
            Vec3 a = Vec3.atCenterOf(from).add(0, 0.4, 0), b = Vec3.atCenterOf(to).add(0, 0.4, 0);
            int steps = (int) Math.max(4, a.distanceTo(b) * 2);
            var r = level.random;
            for (int i = 0; i <= steps; i++) {
                Vec3 p = a.lerp(b, i / (double) steps);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x + r.nextGaussian() * 0.08, p.y + r.nextGaussian() * 0.08, p.z + r.nextGaussian() * 0.08, 1, 0, 0, 0, 0);
            }
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            if (target != null) tag.putLong("Target", target.asLong());
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            target = tag.contains("Target") ? BlockPos.of(tag.getLong("Target")) : null;
        }
    }

    /** A station in the relay registry: a Relay Mast (extends links) or a Signal Jammer (weakens strangers' links). */
    public static class Station extends Powered {
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
