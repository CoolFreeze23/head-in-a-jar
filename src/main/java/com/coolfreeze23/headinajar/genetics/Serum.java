package com.coolfreeze23.headinajar.genetics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.List;
import java.util.Optional;

/** A spliced serum: genes at their potency, and the stability it was made with. Kept on a Serum Cassette and in bodies. */
public record Serum(List<Entry> genes, int stability) {
    public record Entry(Gene gene, float potency) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.xmap(s -> Gene.valueOf(s.toUpperCase(java.util.Locale.ROOT)), Gene::id).fieldOf("gene").forGetter(Entry::gene),
                Codec.FLOAT.fieldOf("potency").forGetter(Entry::potency)
        ).apply(i, Entry::new));
    }

    public static final Codec<Serum> CODEC = RecordCodecBuilder.create(i -> i.group(
            Entry.CODEC.listOf().fieldOf("genes").forGetter(Serum::genes),
            Codec.INT.fieldOf("stability").forGetter(Serum::stability)
    ).apply(i, Serum::new));

    public int load() {
        return genes.stream().mapToInt(e -> e.gene().load).sum();
    }

    public static Optional<Serum> of(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains("Serum")) return Optional.empty();
        return CODEC.parse(NbtOps.INSTANCE, data.copyTag().get("Serum")).result();
    }

    public void writeTo(ItemStack stack) {
        CODEC.encodeStart(NbtOps.INSTANCE, this).result().ifPresent(tag -> CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.put("Serum", tag)));
    }

    public CompoundTag toTag() {
        return (CompoundTag) CODEC.encodeStart(NbtOps.INSTANCE, this).result().orElse(new CompoundTag());
    }
}
