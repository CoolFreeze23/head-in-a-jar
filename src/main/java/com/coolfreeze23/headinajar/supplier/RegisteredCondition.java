package com.coolfreeze23.headinajar.supplier;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * {@code {"type": "headinajar:registered", "registry": "entity_type", "id": "minecraft:creaking"}}: true when that id is
 * in that built-in registry. Files for creatures, blocks and items another mod delivers under vanilla's own names carry
 * it, so they load exactly when the thing exists, whoever supplied it, and are skipped silently when it does not.
 */
public record RegisteredCondition(ResourceLocation registry, ResourceLocation id) implements ICondition {
    public static final MapCodec<RegisteredCondition> CODEC = RecordCodecBuilder.mapCodec(b -> b.group(
            ResourceLocation.CODEC.fieldOf("registry").forGetter(RegisteredCondition::registry),
            ResourceLocation.CODEC.fieldOf("id").forGetter(RegisteredCondition::id)
    ).apply(b, RegisteredCondition::new));

    @Override
    public boolean test(IContext context) {
        return Supplier.registered(registry, id);
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }

    @Override
    public String toString() {
        return "registered(\"" + registry + "\", \"" + id + "\")";
    }
}
