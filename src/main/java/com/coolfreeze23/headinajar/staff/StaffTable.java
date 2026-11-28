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

    /**
     * One species' row: its wage, its noise, how many bell lines it has ({@code bell.headinajar.<ns>.<path>.<n>}, 0 for
     * none), its desk job's id (null for none), the sound its Lookout raises the alarm with (null: its ambient sound),
     * and what it dislikes at dawn ({@code rain}, {@code thunder}, {@code open_sky}, {@code hot}, {@code warm},
     * {@code not_nether}, {@code warped_fungus}, {@code noise}, {@code water}). Its head's work at the stations (0.8.0):
     * {@code array}, FE/t in a Cerebral Array; {@code turret}, its attack at a Turret Pedestal ({@code lookout} for
     * none); {@code socket}, its Neural Socket program ({@code none} for none). Each is null when the row does not say,
     * and {@code HeadJobs} then answers from its own table.
     */
    public record Row(Wage wage, String noise, int bells, String desk, ResourceLocation alarm, java.util.Set<String> dislikes,
                      Map<String, Integer> lines, com.coolfreeze23.headinajar.staff.work.WorkTable.Spec work,
                      Integer array, String turret, String socket) {
        public Row(Wage wage, String noise) {
            this(wage, noise, 0, null, null, java.util.Set.of(), Map.of(), null, null, null, null);
        }
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

    /** How many bell lines the species has (0: a head of a creature with no row keeps its old line). */
    public static int bells(EntityType<?> type) {
        Row row = row(type);
        return row == null ? 0 : row.bells();
    }

    /** The species' desk job id, or null. */
    public static String desk(EntityType<?> type) {
        Row row = row(type);
        return row == null ? null : row.desk();
    }

    /** The species' Lookout alarm sound id, or null (use its ambient sound). */
    public static ResourceLocation alarm(EntityType<?> type) {
        Row row = row(type);
        return row == null ? null : row.alarm();
    }

    /**
     * How many lines of a kind the species has of its own ({@code "bells.nitwit"}, {@code "complaints"},
     * {@code "goodbye"}, {@code "lookout"}); 0 when it has none (the default lines are used).
     */
    public static int lines(EntityType<?> type, String kind) {
        Row row = row(type);
        return row == null ? 0 : row.lines().getOrDefault(kind, 0);
    }

    public static boolean dislikes(EntityType<?> type, String what) {
        Row row = row(type);
        return row != null && row.dislikes().contains(what);
    }

    /** Whether a row is on file for this id (a row for a creature that is not registered is never loaded). */
    public static boolean hasRow(ResourceLocation id) {
        return rows.containsKey(id);
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
        int bells = o.has("bells") ? Math.max(0, o.get("bells").getAsInt()) : 0;
        String desk = o.has("desk") ? o.get("desk").getAsString() : null;
        ResourceLocation alarm = o.has("alarm") ? ResourceLocation.tryParse(o.get("alarm").getAsString()) : null;
        java.util.Set<String> dislikes = new java.util.HashSet<>();
        if (o.has("dislikes") && o.get("dislikes").isJsonArray()) for (JsonElement d : o.getAsJsonArray("dislikes")) dislikes.add(d.getAsString());
        Map<String, Integer> lines = new HashMap<>();
        if (o.has("lines") && o.get("lines").isJsonObject()) {
            for (var e : o.getAsJsonObject("lines").entrySet()) lines.put(e.getKey(), Math.max(0, e.getValue().getAsInt()));
        }
        // what it does at work, if HR has a job description for it (0.7.0)
        var work = com.coolfreeze23.headinajar.staff.work.WorkTable.parse(o.get("work"), null);
        // what its head does at the stations (0.8.0); a row that does not say leaves it to HeadJobs' own table
        Integer array = o.has("array") ? Math.max(0, o.get("array").getAsInt()) : null;
        String turret = o.has("turret") ? o.get("turret").getAsString() : null;
        if (turret != null && !com.coolfreeze23.headinajar.block.HeadJobs.TURRET_ATTACKS.contains(turret)) {
            HeadInAJar.LOGGER.warn("Staff row {}: no turret attack called {}; its head keeps watch instead", o.get("entity"), turret);
            turret = com.coolfreeze23.headinajar.block.HeadJobs.LOOKOUT;
        }
        String socket = o.has("socket") ? o.get("socket").getAsString() : null;
        if (socket != null && !com.coolfreeze23.headinajar.block.HeadJobs.SOCKET_PROGRAMS.contains(socket)) {
            HeadInAJar.LOGGER.warn("Staff row {}: no Neural Socket program called {}; its head runs none", o.get("entity"), socket);
            socket = com.coolfreeze23.headinajar.block.HeadJobs.NO_PROGRAM;
        }
        return new Row(wage, o.has("noise") ? o.get("noise").getAsString() : "", bells, desk, alarm, java.util.Set.copyOf(dislikes), Map.copyOf(lines), work,
                array, turret, socket);
    }

    /** Loads {@code data/<ns>/headinajar/staff/*.json}. */
    public static class Loader extends SimpleJsonResourceReloadListener {
        public Loader() {
            super(new com.google.gson.Gson(), "headinajar/staff");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, Row> out = new HashMap<>();
            var ops = makeConditionalOps();
            files.forEach((file, json) -> {
                try {
                    // a row whose neoforge:conditions fail (a creature or wage not registered) is skipped
                    if (!net.neoforged.neoforge.common.conditions.ICondition.conditionsMatched(ops, json)) return;
                    JsonObject o = json.getAsJsonObject();
                    ResourceLocation entity = ResourceLocation.tryParse(o.get("entity").getAsString());
                    if (entity != null) out.put(entity, parse(o));
                } catch (RuntimeException e) {
                    HeadInAJar.LOGGER.warn("Staff file {}: {}", file, e.getMessage());
                }
            });
            rows = Map.copyOf(out);
            com.coolfreeze23.headinajar.staff.second.Secondees.forget();
            HeadInAJar.LOGGER.info("Loaded {} staff table rows", out.size());
        }
    }
}
