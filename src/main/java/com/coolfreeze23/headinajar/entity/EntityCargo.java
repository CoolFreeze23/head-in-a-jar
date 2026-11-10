package com.coolfreeze23.headinajar.entity;

import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A body's cargo as an open screen sees it: the same slots, but the screen closes as soon as the body is stored in a
 * Body Locker, leaves through a portal, dies or is left behind (a plain container never says it has gone).
 */
public final class EntityCargo implements Container {
    private final Container items;
    private final Entity body;

    public EntityCargo(Container items, Entity body) {
        this.items = items;
        this.body = body;
    }

    @Override
    public boolean stillValid(Player player) {
        double reach = 8 + body.getBbWidth();
        return body.isAlive() && !body.isRemoved() && body.level() == player.level() && player.distanceToSqr(body) <= reach * reach;
    }

    @Override
    public int getContainerSize() {
        return items.getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        return items.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return items.removeItem(slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return items.removeItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.setItem(slot, stack);
    }

    @Override
    public int getMaxStackSize() {
        return items.getMaxStackSize();
    }

    @Override
    public void setChanged() {
        items.setChanged();
    }

    @Override
    public void startOpen(Player player) {
        items.startOpen(player);
    }

    @Override
    public void stopOpen(Player player) {
        items.stopOpen(player);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return items.canPlaceItem(slot, stack);
    }

    @Override
    public void clearContent() {
        items.clearContent();
    }
}
