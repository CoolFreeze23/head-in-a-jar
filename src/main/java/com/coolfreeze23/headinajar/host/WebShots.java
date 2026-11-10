package com.coolfreeze23.headinajar.host;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/** The cobwebs Web Shot placed in one dimension and when each melts. Saved with the dimension, so a restart melts them too. */
public class WebShots extends SavedData {
    private static final String NAME = "headinajar_web_shots";
    private final Map<BlockPos, Long> webs = new HashMap<>();

    public static WebShots get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(WebShots::new, WebShots::load, null), NAME);
    }

    public void add(BlockPos pos, long meltAt) {
        webs.put(pos.immutable(), meltAt);
        setDirty();
    }

    public int size() {
        return webs.size();
    }

    /** Melts the webs that are due, where their chunk is loaded (others wait until it is). */
    public void tick(ServerLevel level) {
        if (webs.isEmpty()) return;
        long now = level.getGameTime();
        boolean changed = webs.entrySet().removeIf(e -> {
            if (e.getValue() > now || !level.isLoaded(e.getKey())) return false;
            if (level.getBlockState(e.getKey()).is(Blocks.COBWEB)) level.removeBlock(e.getKey(), false);
            return true;
        });
        if (changed) setDirty();
    }

    private static WebShots load(CompoundTag tag, HolderLookup.Provider registries) {
        WebShots out = new WebShots();
        ListTag list = tag.getList("Webs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag web = list.getCompound(i);
            out.webs.put(BlockPos.of(web.getLong("Pos")), web.getLong("MeltAt"));
        }
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        webs.forEach((pos, at) -> {
            CompoundTag web = new CompoundTag();
            web.putLong("Pos", pos.asLong());
            web.putLong("MeltAt", at);
            list.add(web);
        });
        tag.put("Webs", list);
        return tag;
    }
}
