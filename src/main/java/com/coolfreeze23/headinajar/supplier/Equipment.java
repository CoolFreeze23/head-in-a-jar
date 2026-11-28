package com.coolfreeze23.headinajar.supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.Predicate;

/**
 * The slot adapter: what a creature is wearing, found by what it is, never by where it is kept. A delivered creature
 * may keep its harness, its swallowed block or its flower in a different slot from the game that later registers it
 * itself, so a read names the item (its tag, or the item) and looks in every slot. Server and client alike.
 */
public final class Equipment {
    /** Harnesses (a happy ghast's). */
    public static final TagKey<Item> HARNESSES = tag("harnesses");
    /** The blocks a sulfur cube swallows. */
    public static final TagKey<Item> SULFUR_CUBE_SWALLOWABLE = tag("sulfur_cube_swallowable");
    /** Spears and lances. */
    public static final TagKey<Item> SPEARS = tag("spears");

    /** The worn slots, looked in first to last: armour, then the body (hands are never "worn"). */
    private static final List<EquipmentSlot> WORN = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.BODY);
    /** Every slot: the worn ones, then the hands. */
    private static final List<EquipmentSlot> ALL = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.BODY, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND);

    private Equipment() {
    }

    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace(path));
    }

    /** The first worn item that matches (hands left out), or empty. */
    public static ItemStack worn(LivingEntity e, Predicate<ItemStack> what) {
        return first(e, WORN, what);
    }

    /** The first worn item in the tag, or empty. */
    public static ItemStack worn(LivingEntity e, TagKey<Item> tag) {
        return worn(e, s -> s.is(tag));
    }

    /** The first item anywhere in its equipment, hands included, that matches, or empty. */
    public static ItemStack anywhere(LivingEntity e, Predicate<ItemStack> what) {
        return first(e, ALL, what);
    }

    /** The slot the first matching worn item is in (to take it off), or null when none is worn. */
    public static EquipmentSlot wornSlot(LivingEntity e, Predicate<ItemStack> what) {
        for (EquipmentSlot slot : WORN) {
            ItemStack s = e.getItemBySlot(slot);
            if (!s.isEmpty() && what.test(s)) return slot;
        }
        return null;
    }

    /** Its harness, wherever it is kept, or empty. */
    public static ItemStack harness(LivingEntity e) {
        return worn(e, HARNESSES);
    }

    /** The block a sulfur cube has swallowed, wherever it is kept, or empty. */
    public static ItemStack swallowed(LivingEntity e) {
        return worn(e, SULFUR_CUBE_SWALLOWABLE);
    }

    /** The poppy it wears (a copper golem's on its antenna), wherever it is kept, or empty. */
    public static ItemStack poppy(LivingEntity e) {
        return worn(e, s -> s.is(Items.POPPY));
    }

    private static ItemStack first(LivingEntity e, List<EquipmentSlot> slots, Predicate<ItemStack> what) {
        if (e == null) return ItemStack.EMPTY;
        for (EquipmentSlot slot : slots) {
            ItemStack s = e.getItemBySlot(slot);
            if (!s.isEmpty() && what.test(s)) return s;
        }
        return ItemStack.EMPTY;
    }
}
