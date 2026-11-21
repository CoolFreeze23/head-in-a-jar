package com.coolfreeze23.headinajar.staff.second;

import com.coolfreeze23.headinajar.item.JarredHeadItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.List;

/**
 * How a secondee reads in words: its Jade lines ("Zombie head on a Cow body · team sync 34%", "Broth 320 mB · Glass
 * 8/10"; an empty collar's "position vacant") from the server data written here, and its kind on Head Office's staff
 * list ("Zombie head, Cow body, team sync 34%"). No client classes.
 */
public final class SecondeeLook {
    private SecondeeLook() {
    }

    /** The Jade keys this writes. */
    static final String COLLAR = "SecCollar", HEAD = "SecHead", SYNC = "SecSync", BROTH = "SecBroth", GLASS = "SecGlass", GLASS_MAX = "SecGlassMax";

    /** Jade's server half: what the lines need of a collared host (nothing for any other creature). */
    public static void jadeData(CompoundTag data, Mob mob) {
        SecondeeData d = Secondees.data(mob);
        if (d == null) return;
        data.putBoolean(COLLAR, true);
        data.putInt(BROTH, d.canister);
        EntityType<?> head = d.seated() ? JarredHeadItem.type(d.head) : null;
        if (head == null) return;
        data.putString(HEAD, BuiltInRegistries.ENTITY_TYPE.getKey(head).toString());
        data.putInt(SYNC, Math.round(d.teamSync));
        data.putInt(GLASS, d.glass);
        data.putInt(GLASS_MAX, d.glassMax);
    }

    /** The lines Jade shows from that data, under the host line; {@code body}: the host's own kind. */
    public static List<Component> jadeLines(CompoundTag data, EntityType<?> body) {
        List<Component> out = new ArrayList<>();
        if (!data.getBoolean(COLLAR)) return out;
        EntityType<?> head = type(data.getString(HEAD));
        if (head == null) {
            out.add(Component.translatable("jade.headinajar.secondee.vacant", data.getInt(BROTH)));
            return out;
        }
        out.add(Component.translatable("jade.headinajar.secondee", head.getDescription(), body.getDescription(), data.getInt(SYNC)));
        out.add(Component.translatable("jade.headinajar.secondee.upkeep", data.getInt(BROTH), data.getInt(GLASS), data.getInt(GLASS_MAX)));
        return out;
    }

    /** A secondee's kind on the staff list ("Zombie head, Cow body, team sync 34%"), or null for any other worker. */
    public static Component listKind(Mob mob) {
        SecondeeData d = Secondees.data(mob);
        EntityType<?> head = d == null || !d.seated() ? null : JarredHeadItem.type(d.head);
        if (head == null) return null;
        return Component.translatable("command.headinajar.staff.secondee", head.getDescription(), mob.getType().getDescription(), Math.round(d.teamSync));
    }

    private static EntityType<?> type(String id) {
        ResourceLocation rl = id.isEmpty() ? null : ResourceLocation.tryParse(id);
        return rl == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null);
    }
}
