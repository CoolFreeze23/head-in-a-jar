package com.coolfreeze23.headinajar.staff;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * The staff table: per species, its wage (an item or an item tag and a count, or none) and its noise (the word it says
 * on the bell and in an Exit Interview). Files under {@code data/<ns>/headinajar/staff/}; a compat datapack adds rows.
 */
public final class StaffTable {
    /** A wage: an item or a tag, and how many a day. A row with neither takes no wage (it counts as paid). */
    public record Wage(Item item, TagKey<Item> tag, int count) {
        public static final Wage NONE = new Wage(null, null, 0);

        public boolean none() {
            return item == null && tag == null;
        }

        public boolean matches(ItemStack stack) {
            if (stack.isEmpty() || none()) return false;
            return item != null ? stack.is(item) : stack.is(tag);
        }

        /** The wage's name as the file card and the refusal say it: the item, or the tag's first item. */
        public Component name() {
            if (item != null) return item.getDescription();
            if (tag == null) return Component.empty();
            var first = BuiltInRegistries.ITEM.getTag(tag).flatMap(set -> set.stream().findFirst());
            return first.map(h -> h.value().getDescription()).orElse(Component.literal("#" + tag.location()));
        }
    }

    public record Row(Wage wage, String noise) {
    }

    private static Map<ResourceLocation, Row> rows = Map.of();

    private StaffTable() {
    }

    /** The species' row, or null for a creature with none (a mod creature: no wage it can be hired with). */
    public static Row row(EntityType<?> type) {
        return type == null ? null : rows.get(BuiltInRegistries.ENTITY_TYPE.getKey(type));
    }

    public static Wage wage(EntityType<?> type) {
        Row row = row(type);
        return row == null ? Wage.NONE : row.wage();
    }

    /** Whether this stack is any species' wage (so a wrong wage is told so, and anything else does what it always did). */
    public static boolean anyWage(ItemStack stack) {
        for (Row row : rows.values()) if (row.wage().matches(stack)) return true;
        return false;
    }

    public static String noise(EntityType<?> type) {
        Row row = row(type);
        return row == null || row.noise().isEmpty() ? "staff.headinajar.noise.default" : row.noise();
    }

    public static int size() {
        return rows.size();
    }

    static Row parse(JsonObject o) {
        Wage wage = Wage.NONE;
        JsonElement w = o.get("wage");
        if (w != null && w.isJsonObject()) {
            JsonObject wo = w.getAsJsonObject();
            int count = wo.has("count") ? Math.max(1, wo.get("count").getAsInt()) : 1;
            if (wo.has("item")) {
                ResourceLocation id = ResourceLocation.tryParse(wo.get("item").getAsString());
                Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
                if (item != null) wage = new Wage(item, null, count);
            } else if (wo.has("tag")) {
                ResourceLocation id = ResourceLocation.tryParse(wo.get("tag").getAsString());
                if (id != null) wage = new Wage(null, TagKey.create(Registries.ITEM, id), count);
            }
        }
        return new Row(wage, o.has("noise") ? o.get("noise").getAsString() : "");
    }

    /** Loads {@code data/<ns>/headinajar/staff/*.json}. */
    public static class Loader extends SimpleJsonResourceReloadListener {
        public Loader() {
            super(new com.google.gson.Gson(), "headinajar/staff");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, Row> out = new HashMap<>();
            files.forEach((file, json) -> {
                try {
                    JsonObject o = json.getAsJsonObject();
                    ResourceLocation entity = ResourceLocation.tryParse(o.get("entity").getAsString());
                    if (entity != null) out.put(entity, parse(o));
                } catch (RuntimeException e) {
                    HeadInAJar.LOGGER.warn("Staff file {}: {}", file, e.getMessage());
                }
            });
            rows = Map.copyOf(out);
            HeadInAJar.LOGGER.info("Loaded {} staff table rows", out.size());
        }
    }
}
