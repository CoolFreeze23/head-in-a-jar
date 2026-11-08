package com.coolfreeze23.headinajar.genetics;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** The gene library: how much of each gene a team (or a lone player) has read, 0-100%. Saved with the overworld. */
public final class GeneArchive extends SavedData {
    private static final String NAME = "headinajar_genes";
    private final Map<String, EnumMap<Gene, Float>> libraries = new HashMap<>();
    /** The local player's library, as the server last sent it (lab screens read this). */
    public static Map<Gene, Float> clientLibrary = new EnumMap<>(Gene.class);

    public static GeneArchive get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(GeneArchive::new, GeneArchive::load, null), NAME);
    }

    /** Teammates share a library; everyone else has their own. */
    public static String key(Player player) {
        return player.getTeam() != null ? "team:" + player.getTeam().getName() : player.getUUID().toString();
    }

    public Map<Gene, Float> library(String key) {
        return libraries.computeIfAbsent(key, k -> new EnumMap<>(Gene.class));
    }

    public float readout(String key, Gene gene) {
        return library(key).getOrDefault(gene, 0f);
    }

    public void add(String key, Gene gene, float amount) {
        library(key).merge(gene, amount, (a, b) -> Math.min(100f, a + b));
        setDirty();
    }

    /** Potency band from a readout: Draft 50%, Working 75%, Complete 100% (under 30%: unusable). */
    public static float potency(float readout) {
        if (readout >= 100) return 1f;
        if (readout >= 60) return 0.75f;
        if (readout >= 30) return 0.5f;
        return 0f;
    }

    /** Stability cost multiplier for the band. */
    public static float costFactor(float readout) {
        if (readout >= 100) return 1f;
        if (readout >= 60) return 1.2f;
        return 1.5f;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        libraries.forEach((key, lib) -> {
            CompoundTag t = new CompoundTag();
            lib.forEach((gene, v) -> t.putFloat(gene.id(), v));
            tag.put(key, t);
        });
        return tag;
    }

    private static GeneArchive load(CompoundTag tag, HolderLookup.Provider registries) {
        GeneArchive archive = new GeneArchive();
        for (String key : tag.getAllKeys()) {
            CompoundTag t = tag.getCompound(key);
            EnumMap<Gene, Float> lib = new EnumMap<>(Gene.class);
            for (Gene gene : Gene.values()) if (t.contains(gene.id())) lib.put(gene, t.getFloat(gene.id()));
            archive.libraries.put(key, lib);
        }
        return archive;
    }
}
