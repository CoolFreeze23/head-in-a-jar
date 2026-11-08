package com.coolfreeze23.headinajar.form;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Relay Masts and Signal Jammers, with an energy snapshot so they keep working (and running down) while their chunk
 * is unloaded: a station counts as powered until its stored energy would have run out at its drain rate.
 */
public final class RelayRegistry extends SavedData {
    public enum Kind { MAST, JAMMER }

    public record Station(Kind kind, ResourceKey<Level> dimension, BlockPos pos, int radius, UUID owner, int energy, long time, int drain) {
        public boolean powered(long now) {
            return energy - (now - time) * (long) drain > 0;
        }
    }

    private static final String NAME = "headinajar_relays";
    private final Map<String, Station> stations = new HashMap<>();

    public static RelayRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(RelayRegistry::new, RelayRegistry::load, null), NAME);
    }

    private static String key(ResourceKey<Level> dim, BlockPos pos) {
        return dim.location() + "@" + pos.asLong();
    }

    public void put(Station station) {
        Station old = stations.put(key(station.dimension(), station.pos()), station);
        if (old == null || old.energy() != station.energy() || old.radius() != station.radius()) setDirty();
    }

    public void remove(ResourceKey<Level> dim, BlockPos pos) {
        if (stations.remove(key(dim, pos)) != null) setDirty();
    }

    public List<Station> powered(Kind kind, ResourceKey<Level> dim, long now) {
        List<Station> out = new ArrayList<>();
        for (Station s : stations.values()) {
            if (s.kind() == kind && s.dimension().equals(dim) && s.powered(now)) out.add(s);
        }
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Station s : stations.values()) {
            CompoundTag t = new CompoundTag();
            t.putString("kind", s.kind().name());
            t.putString("dim", s.dimension().location().toString());
            t.putLong("pos", s.pos().asLong());
            t.putInt("radius", s.radius());
            if (s.owner() != null) t.putUUID("owner", s.owner());
            t.putInt("energy", s.energy());
            t.putLong("time", s.time());
            t.putInt("drain", s.drain());
            list.add(t);
        }
        tag.put("stations", list);
        return tag;
    }

    private static RelayRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        RelayRegistry r = new RelayRegistry();
        for (Tag e : tag.getList("stations", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) e;
            Station s = new Station(Kind.valueOf(t.getString("kind")), ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(t.getString("dim"))),
                    BlockPos.of(t.getLong("pos")), t.getInt("radius"), t.hasUUID("owner") ? t.getUUID("owner") : null,
                    t.getInt("energy"), t.getLong("time"), t.getInt("drain"));
            r.stations.put(key(s.dimension(), s.pos()), s);
        }
        return r;
    }
}
