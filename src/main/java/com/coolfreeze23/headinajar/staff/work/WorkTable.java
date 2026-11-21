package com.coolfreeze23.headinajar.staff.work;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.VillagerDataHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * What each species does at work: the {@code work} object of its staff file ({@code data/<ns>/headinajar/staff/<species>.json},
 * beside its wage and noise; read by {@code StaffTable.Loader}). A species with no {@code work} object cannot be hired
 * ("HR has no job description for a %s yet.").
 * <pre>
 * "work": {
 *   "hire": true | "lanyard.creeper",            a refusal key under refusal.headinajar. instead of true
 *   "jobs": {"hauling": 1.25, "security": 1},     the jobs it can do and how fast (x1 base); a job absent cannot be done
 *   "cannot": {"sorting": "card.cannot.sorting_undead"},  the refusal for a job it cannot do (default card.cannot)
 *   "shift": "day" | "night" | "any",             its default shift ("any" starts on Day)
 *   "carry": 1, "carry_items": 16,                stacks it carries, and a cap on items (0: none)
 *   "hands": true,                                 shearing, milking and holding a weapon need hands
 *   "walk": 1.0,                                   speed modifier passed to its navigation
 *   "move": "walk",                                movement family (Movers)
 *   "attack": "melee",                             melee, bow, crossbow, fireball, snowball, swoop, lookout, none
 *   "melee": 3,                                    melee damage when it has no attack attribute
 *   "quirks": ["shy"],                             quirk ids (Quirks)
 *   "card": true,                                  false: works with no card (cat, ocelot, witch, bee)
 *   "water": false,                                works in water only (stops must be in or touching water)
 *   "professions": {"minecraft:farmer": {...}}     villagers: a profession's own fields over the species' ones
 * }
 * </pre>
 */
public final class WorkTable {
    public static final String HAULING = "hauling", SORTING = "sorting", FARMING = "farming", SECURITY = "security";
    public static final java.util.List<String> JOBS = java.util.List.of(HAULING, SORTING, FARMING, SECURITY);

    /** One species' (or profession's) work row. */
    public record Spec(String hire, Map<String, Float> jobs, Map<String, String> cannot, String shift, int carry, int carryItems,
                       boolean hands, float walk, String move, String attack, float melee, Set<String> quirks, boolean card,
                       boolean water, Map<String, Spec> professions) {
        /** Null when it may be hired, else the refusal key. */
        public String refusal() {
            return hire == null || hire.equals("true") ? null : hire;
        }

        public boolean can(String job) {
            return jobs.containsKey(job);
        }

        /** The speed it does a job at (1 if it can do it with no listed aptitude, 0 if it cannot). */
        public float speed(String job) {
            return jobs.getOrDefault(job, 0f);
        }

        /** The refusal key for a job it cannot do. */
        public String cannotKey(String job) {
            return cannot.getOrDefault(job, "card.cannot");
        }

        public boolean quirk(String id) {
            return quirks.contains(id);
        }

        /** Whether its kind burns by day (its default shift is Night). */
        public boolean nightShift() {
            return "night".equals(shift);
        }

        public WorkerData.Shift defaultShift() {
            return nightShift() ? WorkerData.Shift.NIGHT : WorkerData.Shift.DAY;
        }

        /** Most items it may carry in one trip: {@code carry} stacks, capped by {@code carry_items}. */
        public int carryLimit(int stackSize) {
            int n = Math.max(0, carry) * Math.max(1, stackSize);
            return carryItems > 0 ? Math.min(n == 0 ? carryItems : n, carryItems) : n;
        }
    }

    private WorkTable() {
    }

    /**
     * The work row of a living creature: its species' row, with a villager's profession's fields over it; for a host
     * with a seconded head, the head's skills on the body's hardware ({@code staff.second.Secondees.spec}). Null: none.
     */
    public static Spec of(Mob mob) {
        // at 100% team sync the pair works 10% faster and carries 10% more (0.7.1, staff.second.BodyWork.atFull)
        if (com.coolfreeze23.headinajar.staff.second.Secondees.seated(mob)) {
            return com.coolfreeze23.headinajar.staff.second.BodyWork.atFull(mob, com.coolfreeze23.headinajar.staff.second.Secondees.spec(mob));
        }
        return ofSpecies(mob);
    }

    /** The creature's own row (its species', a villager's profession over it), whatever is seated on it. */
    public static Spec ofSpecies(Mob mob) {
        Spec spec = of(mob.getType());
        if (spec == null || spec.professions().isEmpty() || !(mob instanceof VillagerDataHolder v)) return spec;
        var key = BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().getProfession());
        Spec p = spec.professions().get(key.toString());
        return p == null ? spec : p;
    }

    public static Spec of(EntityType<?> type) {
        var row = com.coolfreeze23.headinajar.staff.StaffTable.row(type);
        return row == null ? null : row.work();
    }

    // --- reading -----------------------------------------------------------------------------------------------------

    /** Reads a {@code work} object; null for none. A profession reads over its species' row. */
    public static Spec parse(JsonElement e, Spec base) {
        if (e == null || !e.isJsonObject()) return base;
        JsonObject o = e.getAsJsonObject();
        String hire = base == null ? "true" : base.hire();
        if (o.has("hire")) hire = o.get("hire").isJsonPrimitive() && o.get("hire").getAsJsonPrimitive().isBoolean()
                ? (o.get("hire").getAsBoolean() ? "true" : "lanyard.unknown") : o.get("hire").getAsString();
        Map<String, Float> jobs = base == null ? Map.of() : base.jobs();
        if (o.has("jobs")) {
            Map<String, Float> m = new HashMap<>();
            for (var j : o.getAsJsonObject("jobs").entrySet()) m.put(j.getKey(), j.getValue().getAsFloat());
            jobs = Map.copyOf(m);
        }
        Map<String, String> cannot = base == null ? Map.of() : base.cannot();
        if (o.has("cannot")) {
            Map<String, String> m = new HashMap<>(cannot);
            for (var j : o.getAsJsonObject("cannot").entrySet()) m.put(j.getKey(), j.getValue().getAsString());
            cannot = Map.copyOf(m);
        }
        Set<String> quirks = base == null ? Set.of() : base.quirks();
        if (o.has("quirks")) {
            java.util.Set<String> s = new java.util.HashSet<>();
            for (JsonElement q : o.getAsJsonArray("quirks")) s.add(q.getAsString());
            quirks = Set.copyOf(s);
        }
        Spec spec = new Spec(hire, jobs, cannot,
                str(o, "shift", base == null ? "day" : base.shift()),
                o.has("carry") ? o.get("carry").getAsInt() : base == null ? 1 : base.carry(),
                o.has("carry_items") ? o.get("carry_items").getAsInt() : base == null ? 0 : base.carryItems(),
                o.has("hands") ? o.get("hands").getAsBoolean() : base != null && base.hands(),
                o.has("walk") ? o.get("walk").getAsFloat() : base == null ? 1f : base.walk(),
                str(o, "move", base == null ? "walk" : base.move()),
                str(o, "attack", base == null ? "none" : base.attack()),
                o.has("melee") ? o.get("melee").getAsFloat() : base == null ? 0f : base.melee(),
                quirks,
                o.has("card") ? o.get("card").getAsBoolean() : base == null || base.card(),
                o.has("water") ? o.get("water").getAsBoolean() : base != null && base.water(),
                Map.of());
        if (base == null && o.has("professions")) {
            Map<String, Spec> profs = new HashMap<>();
            for (var p : o.getAsJsonObject("professions").entrySet()) profs.put(p.getKey(), parse(p.getValue(), spec));
            spec = new Spec(spec.hire(), spec.jobs(), spec.cannot(), spec.shift(), spec.carry(), spec.carryItems(), spec.hands(), spec.walk(),
                    spec.move(), spec.attack(), spec.melee(), spec.quirks(), spec.card(), spec.water(), Map.copyOf(profs));
        }
        return spec;
    }

    private static String str(JsonObject o, String key, String fallback) {
        return o.has(key) ? o.get(key).getAsString() : fallback;
    }
}
