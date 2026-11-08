package com.coolfreeze23.headinajar.item;

import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Relay Goggles or a Collar Cam + an Entangled Shard = the same item, entangled with the shard's twin. */
public class EntangleRecipe extends CustomRecipe {
    public EntangleRecipe(CraftingBookCategory category) {
        super(category);
    }

    private static boolean target(ItemStack stack) {
        return stack.getItem() instanceof RelayGogglesItem || stack.getItem() instanceof CollarCamItem;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        ItemStack target = ItemStack.EMPTY, shard = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) continue;
            if (target(s) && target.isEmpty()) target = s;
            else if (s.is(ModRegistry.ENTANGLED_SHARD.get()) && shard.isEmpty() && QuantumItems.entangled(s).isPresent()) shard = s;
            else return false;
        }
        return !target.isEmpty() && !shard.isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack target = ItemStack.EMPTY, shard = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (target(s)) target = s;
            else if (s.is(ModRegistry.ENTANGLED_SHARD.get())) shard = s;
        }
        if (target.isEmpty() || shard.isEmpty()) return ItemStack.EMPTY;
        ItemStack out = target.copyWithCount(1);
        QuantumItems.entangled(shard).ifPresent(id -> QuantumItems.entangle(out, id));
        return out;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRegistry.ENTANGLE_RECIPE.get();
    }
}
