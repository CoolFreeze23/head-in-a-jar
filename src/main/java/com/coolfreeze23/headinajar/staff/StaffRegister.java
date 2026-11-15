package com.coolfreeze23.headinajar.staff;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The staff register: one counter per world (the next staff number, from 1), and the Exit Interview lines waiting for
 * employers who were offline when their staff left (at most {@value #WAITING} each, sent at their next login).
 */
public final class StaffRegister extends SavedData {
    private static final String NAME = "headinajar_staff";
    public static final int WAITING = 8;
    private int next = 1;
    /** Waiting lines per employer, as component JSON, oldest first. */
    private final Map<UUID, List<String>> waiting = new HashMap<>();

    public static StaffRegister get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(StaffRegister::new, StaffRegister::load, null), NAME);
    }

    /** Takes the next staff number. */
    public int take() {
        setDirty();
        return next++;
    }

    /** The number the next creature will get (tests). */
    public int peek() {
        return next;
    }

    public void hold(UUID employer, String json) {
        List<String> lines = waiting.computeIfAbsent(employer, k -> new ArrayList<>());
        lines.add(json);
        while (lines.size() > WAITING) lines.remove(0);
        setDirty();
    }

    /** The lines waiting for an employer, removed from the register. */
    public List<String> release(UUID employer) {
        List<String> lines = waiting.remove(employer);
        if (lines != null) setDirty();
        return lines == null ? List.of() : lines;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("next", next);
        if (!waiting.isEmpty()) {
            CompoundTag w = new CompoundTag();
            waiting.forEach((id, lines) -> {
                ListTag list = new ListTag();
                for (String s : lines) list.add(StringTag.valueOf(s));
                w.put(id.toString(), list);
            });
            tag.put("waiting", w);
        }
        return tag;
    }

    public static StaffRegister load(CompoundTag tag, HolderLookup.Provider registries) {
        StaffRegister r = new StaffRegister();
        r.next = Math.max(1, tag.getInt("next"));
        CompoundTag w = tag.getCompound("waiting");
        for (String key : w.getAllKeys()) {
            try {
                UUID id = UUID.fromString(key);
                List<String> lines = new ArrayList<>();
                for (Tag t : w.getList(key, Tag.TAG_STRING)) lines.add(t.getAsString());
                r.waiting.put(id, lines);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return r;
    }
}
