package com.coolfreeze23.headinajar.staff.work;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Head Office's list of who is employed by whom with a lanyard (saved once, in the Overworld's storage, as
 * {@code headinajar_workforce}): each worker's UUID, staff number, species and where it was last seen. It is what the
 * headcount cap counts and what {@code /headoffice staff list} reads; nothing in it is ever loaded to look at a worker.
 */
public final class WorkerIndex extends SavedData {
    private static final String NAME = "headinajar_workforce";

    /** One worker as the index last saw it. */
    public static final class Entry {
        public final UUID id;
        public int number;
        public String species = "";
        public GlobalPos where;

        Entry(UUID id) {
            this.id = id;
        }
    }

    private final Map<UUID, Map<UUID, Entry>> byEmployer = new HashMap<>();

    public static WorkerIndex get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(WorkerIndex::new, WorkerIndex::load, null), NAME);
    }

    /** How many workers a player employs. */
    public int count(UUID employer) {
        Map<UUID, Entry> m = byEmployer.get(employer);
        return m == null ? 0 : m.size();
    }

    public Collection<Entry> of(UUID employer) {
        Map<UUID, Entry> m = byEmployer.get(employer);
        return m == null ? java.util.List.of() : m.values();
    }

    public Map<UUID, Map<UUID, Entry>> all() {
        return byEmployer;
    }

    /** A worker hired, or seen where it is now (cheap: only marks the data dirty when the block it stands on changed). */
    public void seen(UUID employer, UUID worker, int number, String species, GlobalPos where) {
        Entry e = byEmployer.computeIfAbsent(employer, k -> new LinkedHashMap<>()).computeIfAbsent(worker, Entry::new);
        if (e.number == number && e.species.equals(species) && where.equals(e.where)) return;
        e.number = number;
        e.species = species;
        e.where = where;
        setDirty();
    }

    public void remove(UUID employer, UUID worker) {
        Map<UUID, Entry> m = byEmployer.get(employer);
        if (m != null && m.remove(worker) != null) {
            if (m.isEmpty()) byEmployer.remove(employer);
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        byEmployer.forEach((employer, workers) -> workers.values().forEach(e -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("Employer", employer);
            t.putUUID("Id", e.id);
            t.putInt("Number", e.number);
            t.putString("Species", e.species);
            if (e.where != null) GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, e.where).result().ifPresent(w -> t.put("Where", w));
            list.add(t);
        }));
        tag.put("Workers", list);
        return tag;
    }

    private static WorkerIndex load(CompoundTag tag, HolderLookup.Provider registries) {
        WorkerIndex index = new WorkerIndex();
        for (Tag t : tag.getList("Workers", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            if (!c.hasUUID("Employer") || !c.hasUUID("Id")) continue;
            Entry e = index.byEmployer.computeIfAbsent(c.getUUID("Employer"), k -> new LinkedHashMap<>()).computeIfAbsent(c.getUUID("Id"), Entry::new);
            e.number = c.getInt("Number");
            e.species = c.getString("Species");
            e.where = c.contains("Where") ? GlobalPos.CODEC.parse(NbtOps.INSTANCE, c.get("Where")).result().orElse(null) : null;
        }
        return index;
    }

    /** A position as the index keeps it (the block a worker stands on). */
    public static GlobalPos at(net.minecraft.world.entity.Entity e) {
        return GlobalPos.of(e.level().dimension(), BlockPos.containing(e.position()));
    }
}
