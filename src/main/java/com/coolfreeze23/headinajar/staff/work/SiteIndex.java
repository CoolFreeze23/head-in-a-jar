package com.coolfreeze23.headinajar.staff.work;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Head Office's register of sites in one dimension (saved in that level's own storage as {@code headinajar_sites}):
 * every Time Clock's position and owner. It is what the spacing between clocks is checked against, where an awaiting
 * worker looks for a clock, and what {@code /headoffice staff list} reads. Nothing in it loads a chunk: a clock is only
 * ever read through {@link Sites#clock}, which looks only at loaded ones.
 */
public final class SiteIndex extends SavedData {
    private static final String NAME = "headinajar_sites";

    /** One registered site. */
    public record Site(BlockPos pos, UUID owner, String ownerName) {
    }

    private final Map<Long, Site> sites = new LinkedHashMap<>();

    public static SiteIndex get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(SiteIndex::new, SiteIndex::load, null), NAME);
    }

    public List<Site> all() {
        return List.copyOf(sites.values());
    }

    public Site at(BlockPos pos) {
        return sites.get(pos.asLong());
    }

    /** A clock placed (or found on its first load): registered, or its owner brought up to date. */
    public void add(BlockPos pos, UUID owner, String ownerName) {
        Site was = sites.get(pos.asLong());
        Site now = new Site(pos.immutable(), owner, ownerName == null ? "" : ownerName);
        if (now.equals(was)) return;
        sites.put(pos.asLong(), now);
        setDirty();
    }

    public void remove(BlockPos pos) {
        if (sites.remove(pos.asLong()) != null) setDirty();
    }

    /**
     * The nearest other clock too close to {@code pos} for a new one: within {@code spacing} blocks on x or z (the
     * Chebyshev distance on the level; any height), or null.
     */
    public Site tooClose(BlockPos pos, int spacing) {
        Site best = null;
        int bestD = Integer.MAX_VALUE;
        for (Site s : sites.values()) {
            if (s.pos.equals(pos)) continue;
            int d = Math.max(Math.abs(s.pos.getX() - pos.getX()), Math.abs(s.pos.getZ() - pos.getZ()));
            if (d <= spacing && d < bestD) {
                best = s;
                bestD = d;
            }
        }
        return best;
    }

    /** The clocks of {@code owner} whose site box holds {@code at}, nearest first. */
    public List<Site> around(BlockPos at, UUID owner) {
        List<Site> out = new ArrayList<>();
        for (Site s : sites.values()) if (owner.equals(s.owner) && Workforce.inBox(s.pos, at)) out.add(s);
        if (out.size() > 1) out.sort(Comparator.comparingDouble(s -> s.pos.distSqr(at)));
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Site s : sites.values()) {
            CompoundTag t = new CompoundTag();
            t.putLong("Pos", s.pos.asLong());
            if (s.owner != null) t.putUUID("Owner", s.owner);
            t.putString("OwnerName", s.ownerName);
            list.add(t);
        }
        tag.put("Sites", list);
        return tag;
    }

    private static SiteIndex load(CompoundTag tag, HolderLookup.Provider registries) {
        SiteIndex index = new SiteIndex();
        for (Tag t : tag.getList("Sites", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            BlockPos pos = BlockPos.of(c.getLong("Pos"));
            index.sites.put(pos.asLong(), new Site(pos, c.hasUUID("Owner") ? c.getUUID("Owner") : null, c.getString("OwnerName")));
        }
        return index;
    }
}
