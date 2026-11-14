package com.coolfreeze23.headinajar.genetics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The genes a body carries: the serums injected into it and any mutations. Saved on the body (a player in BODY/LINKED
 * form, a Headless Body, a host, a mecha). The same gene twice works at 50%, a third copy at 25%, then not at all.
 */
public final class GeneSet {
    public static final Codec<GeneSet> CODEC = RecordCodecBuilder.create(i -> i.group(
            Serum.CODEC.listOf().optionalFieldOf("serums", List.of()).forGetter(s -> s.serums),
            Codec.STRING.listOf().optionalFieldOf("mutations", List.of()).forGetter(s -> s.mutations.stream().map(Gene::id).toList())
    ).apply(i, (serums, mutations) -> {
        GeneSet set = new GeneSet();
        set.serums.addAll(serums);
        // an unknown gene id (renamed or removed in a later version) is skipped, not fatal
        mutations.forEach(m -> {
            Gene gene = Gene.byId(m);
            if (gene != null) set.mutations.add(gene);
        });
        return set;
    }));

    public final List<Serum> serums = new ArrayList<>();
    public final List<Gene> mutations = new ArrayList<>();
    /** A gene grafted for a while (the Chimera's Graft): it works at full strength, takes no port and is never saved. */
    public Gene graft;
    private Map<Gene, Float> cache;

    public boolean isEmpty() {
        return serums.isEmpty() && mutations.isEmpty();
    }

    public void clear() {
        serums.clear();
        mutations.clear();
        cache = null;
    }

    public void changed() {
        cache = null;
    }

    public void copyFrom(GeneSet other) {
        serums.clear();
        serums.addAll(other.serums);
        mutations.clear();
        mutations.addAll(other.mutations);
        cache = null;
    }

    /** Each gene's working strength (0-1+), after stacking. */
    public Map<Gene, Float> strengths() {
        if (cache != null) return cache;
        Map<Gene, Integer> copies = new EnumMap<>(Gene.class);
        Map<Gene, Float> out = new EnumMap<>(Gene.class);
        for (Serum serum : serums) {
            for (Serum.Entry e : serum.genes()) {
                int n = copies.merge(e.gene(), 1, Integer::sum);
                float factor = n == 1 ? 1f : n == 2 ? 0.5f : n == 3 ? 0.25f : 0f;
                out.merge(e.gene(), e.potency() * factor, Float::sum);
            }
        }
        for (Gene m : mutations) out.merge(m, 1f, Float::sum);
        if (graft != null) out.merge(graft, 1f, Float::sum);
        cache = out;
        return out;
    }

    public float strength(Gene gene) {
        return strengths().getOrDefault(gene, 0f);
    }

    public boolean has(Gene gene) {
        return strength(gene) >= 0.5f;
    }

    public int load() {
        return serums.stream().mapToInt(Serum::load).sum();
    }
}
