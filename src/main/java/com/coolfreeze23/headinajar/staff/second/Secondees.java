package com.coolfreeze23.headinajar.staff.second;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.item.JarredHeadItem;
import com.coolfreeze23.headinajar.registry.SecondRegistry;
import com.coolfreeze23.headinajar.staff.work.WorkTable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Secondees: hosts with a Secondment Collar, and the jarred head seated on one. Who is one, what it may do at work
 * (the head's skills on the body's hardware: {@link #spec}), and the server switch.
 * <p>
 * A secondee with a seated head is a worker ({@code staff.work.Workforce}): it has the worker record, the head's
 * Personnel File on the host, and {@code HostAiMixin} runs the staff brain for it. {@code WorkTable.of(Mob)} returns
 * the merged row of {@link #spec}, so the jobs, the movers, the shifts, the detail and combat read one row.
 */
public final class Secondees {
    private Secondees() {
    }

    /** Its collar record, or null (not a host with a collar). */
    public static SecondeeData data(Entity e) {
        if (!(e instanceof Mob) || !e.hasData(SecondRegistry.SECONDEE)) return null;
        SecondeeData d = e.getData(SecondRegistry.SECONDEE);
        return d.collar ? d : null;
    }

    /** Wears a collar (with or without a head). */
    public static boolean collared(Entity e) {
        return data(e) != null;
    }

    /** Wears a collar with a jarred head seated on it. */
    public static boolean seated(Entity e) {
        SecondeeData d = data(e);
        return d != null && d.seated();
    }

    /** The seated head's species, or null. */
    public static EntityType<?> headType(Entity e) {
        SecondeeData d = data(e);
        return d == null || d.head.isEmpty() ? null : JarredHeadItem.type(d.head);
    }

    /** Whose wage a worker draws: a seated head's species (the head is the one on the payroll), else its own. */
    public static EntityType<?> payType(Mob mob) {
        EntityType<?> head = headType(mob);
        return head != null && seated(mob) ? head : mob.getType();
    }

    /** Team sync (0 for anything that is not a seated secondee). */
    public static float teamSync(Entity e) {
        SecondeeData d = data(e);
        return d == null || !d.seated() ? 0 : d.teamSync;
    }

    /** Secondments are approved on this server ({@code staff.secondment}). */
    public static boolean enabled() {
        return Config.get(Config.SECONDMENT, true);
    }

    /** Glass points in a jar: 10 in a Life Support or Compact Jar, 20 Reinforced, 30 Executive. */
    public static int glassFor(String jar) {
        if (jar == null) return 10;
        if (jar.endsWith("executive_jar") || jar.endsWith("executive_life_support_jar")) return 30;
        if (jar.endsWith("reinforced_jar") || jar.endsWith("reinforced_life_support_jar")) return 20;
        return 10;
    }

    /** The stored creature inside a jarred head item (read in place; empty if none). */
    public static CompoundTag entityTag(ItemStack head) {
        CustomData data = head.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.getUnsafe().getCompound("Entity");
    }

    // --- the merged row ---------------------------------------------------------------------------------------------

    private static final Map<String, WorkTable.Spec> MERGED = new HashMap<>();

    /**
     * A seated secondee's work row: from the head (its species' row, a villager's profession over it) the jobs and how
     * fast, what it cannot do, its attack (its turret post), its melee, quirks and whether it needs a card; from the
     * body (the host's own row) carry, hands, walk speed, movement family and water; the shift is Night if the body's
     * own kind burns by day, else any shift (starting on Day). Null when the head has no row (it cannot work: none
     * is seated that way, since the seat refuses it) or the mob is not a seated secondee.
     */
    public static WorkTable.Spec spec(Mob host) {
        SecondeeData d = data(host);
        if (d == null || !d.seated()) return null;
        EntityType<?> headType = JarredHeadItem.type(d.head);
        if (headType == null) return null;
        String profession = "";
        if (headType == EntityType.VILLAGER) profession = entityTag(d.head).getCompound("VillagerData").getString("profession");
        String key = BuiltInRegistries.ENTITY_TYPE.getKey(headType) + "|" + profession + "|" + BuiltInRegistries.ENTITY_TYPE.getKey(host.getType());
        WorkTable.Spec cached = MERGED.get(key);
        if (cached != null) return cached;
        WorkTable.Spec head = WorkTable.of(headType);
        if (head == null) return null;
        if (!profession.isEmpty() && head.professions().containsKey(profession)) head = head.professions().get(profession);
        WorkTable.Spec body = WorkTable.ofSpecies(host);
        WorkTable.Spec merged = merge(head, body, host);
        MERGED.put(key, merged);
        return merged;
    }

    /** Forgets the merged rows (the staff files were reloaded). */
    public static void forget() {
        MERGED.clear();
    }

    /** Quirks that belong to a body, not a head: a seconded head leaves them behind and takes on its body's. */
    static final Set<String> BODY_QUIRKS = Set.of("burns", "zombifies", "witch_regen", "surfaces", "out_of_the_rain", "mouth",
            "carried_inside", "spits_unpaid", "pollinates", "poppy", "trader_stays", "keeps_creepers_out", "sits_on_chests");

    static WorkTable.Spec merge(WorkTable.Spec head, WorkTable.Spec body, Mob host) {
        int carry = body == null ? 1 : body.carry();
        int carryItems = body == null ? 0 : body.carryItems();
        boolean hands = body == null ? host.getBbHeight() > host.getBbWidth() * 1.5f : body.hands();
        float walk = body == null ? 1f : body.walk();
        String move = body == null ? "walk" : body.move();
        boolean water = body != null && body.water();
        String shift = body != null && body.nightShift() ? "night" : "any";
        // the body's habits go with the body (it burns, it carries in its mouth); the head's temper with the head
        java.util.Set<String> quirks = new java.util.HashSet<>();
        for (String q : head.quirks()) if (!BODY_QUIRKS.contains(q)) quirks.add(q);
        if (body != null) for (String q : body.quirks()) if (BODY_QUIRKS.contains(q)) quirks.add(q);
        return new WorkTable.Spec("true", head.jobs(), head.cannot(), shift, carry, carryItems, hands, walk, move, head.attack(),
                head.melee(), Set.copyOf(quirks), head.card(), water, Map.of());
    }
}
