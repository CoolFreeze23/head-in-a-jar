package com.coolfreeze23.headinajar.supplier;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;

/**
 * The supplier contract. Newer creatures, blocks and items may be delivered by another mod under vanilla's own
 * {@code minecraft:} names; Head Office files them under those names, by id string, and never names the supplier. Every
 * row for them is loaded only when its id is registered ({@link RegisteredCondition}), so the same files work when the
 * game itself registers those things.
 */
public final class Supplier {
    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, HeadInAJar.MOD_ID);

    static {
        CONDITIONS.register("registered", () -> RegisteredCondition.CODEC);
    }

    /** The new arrivals Head Office keeps a file for, in the order they are listed and drawn. */
    public static final List<String> NEW_ARRIVALS = List.of(
            "creaking", "happy_ghast", "copper_golem", "nautilus", "zombie_nautilus", "camel_husk", "parched", "sulfur_cube")
            .stream().map(p -> "minecraft:" + p).toList();

    /** Farm animals that come in strains where the warm and cold strains are delivered (the strains' own biome tag). */
    public static final List<String> STRAINS = List.of("cow", "pig", "chicken");
    private static final TagKey<Biome> WARM_STRAINS = TagKey.create(Registries.BIOME,
            ResourceLocation.withDefaultNamespace("spawns_warm_variant_farm_animals"));

    private Supplier() {
    }

    public static void register(IEventBus modBus) {
        CONDITIONS.register(modBus);
    }

    /** Whether the id is in the built-in registry of that name (an unknown registry counts as not registered). */
    public static boolean registered(ResourceLocation registry, ResourceLocation id) {
        Registry<?> reg = BuiltInRegistries.REGISTRY.get(registry);
        return reg != null && reg.containsKey(id);
    }

    /** Whether the entity type with this id string is registered. */
    public static boolean registered(String entityId) {
        ResourceLocation id = ResourceLocation.tryParse(entityId);
        return id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id);
    }

    /** The new arrivals registered in this game, in order. */
    public static List<String> delivered() {
        return NEW_ARRIVALS.stream().filter(Supplier::registered).toList();
    }

    /** Whether this creature is one of the new arrivals (and so has a cell in the New Arrivals row). */
    public static boolean newArrival(EntityType<?> type) {
        return type != null && NEW_ARRIVALS.contains(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
    }

    /** Whether the warm and cold farm strains are delivered: the biome tag that places them is bound and not empty. */
    public static boolean strains(MinecraftServer server) {
        return server.registryAccess().registry(Registries.BIOME)
                .flatMap(r -> r.getTag(WARM_STRAINS)).map(set -> set.size() > 0).orElse(false);
    }
}
