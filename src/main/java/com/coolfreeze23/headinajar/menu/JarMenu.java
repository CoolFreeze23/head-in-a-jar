package com.coolfreeze23.headinajar.menu;

import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.item.ModuleItem;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The Jar menu: module slots, a feed slot for redstone, and the "Let go" button. */
public class JarMenu extends AbstractContainerMenu {
    public static final int BUTTON_LET_GO = 0;
    public static final int MODULE_X = 44;
    public static final int MODULE_Y = 28;
    public static final int FEED_X = 152;
    public static final int FEED_Y = 56;

    private final Player player;
    private final JarState state;
    private final SimpleContainer feed = new SimpleContainer(1);

    public JarMenu(int id, Inventory inventory) {
        super(ModRegistry.JAR_MENU.get(), id);
        this.player = inventory.player;
        this.state = player.getData(ModRegistry.JAR_STATE);
        Container modules = new ModuleContainer(state);
        for (int i = 0; i < JarState.MAX_SLOTS; i++) {
            addSlot(new ModuleSlot(modules, i, MODULE_X + i * 18, MODULE_Y));
        }
        addSlot(new Slot(feed, 0, FEED_X, FEED_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return chargeValue(stack) > 0;
            }

            @Override
            public void setChanged() {
                super.setChanged();
                feedCharge();
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 142));
    }

    public JarState state() {
        return state;
    }

    public static int chargeValue(ItemStack stack) {
        if (stack.is(Items.REDSTONE)) return 50;
        if (stack.is(Items.REDSTONE_BLOCK)) return 450;
        return 0;
    }

    /** Turns redstone in the feed slot into charge while the jar has room. */
    private void feedCharge() {
        if (player.level().isClientSide) return;
        ItemStack stack = feed.getItem(0);
        int value = chargeValue(stack);
        while (value > 0 && !stack.isEmpty() && state.charge() + value <= JarState.maxCharge()) {
            state.setCharge(state.charge() + value);
            stack.shrink(1);
        }
        if (!stack.isEmpty() && value > 0 && state.charge() < JarState.maxCharge()) {
            state.setCharge(JarState.maxCharge());
            stack.shrink(1);
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_LET_GO && player instanceof ServerPlayer server) {
            server.closeContainer();
            FormActions.letGo(server);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int own = JarState.MAX_SLOTS + 1;
        if (index < own) {
            if (!moveItemStackTo(stack, own, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof ModuleItem) {
            if (!moveItemStackTo(stack, 0, JarState.MAX_SLOTS, false)) return ItemStack.EMPTY;
        } else if (chargeValue(stack) > 0) {
            if (!moveItemStackTo(stack, JarState.MAX_SLOTS, own, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) clearContainer(player, feed);
    }

    @Override
    public boolean stillValid(Player player) {
        return state.form().isJarred();
    }

    /** One module slot; slots past the jar's tier are shut. */
    private class ModuleSlot extends Slot {
        ModuleSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean isActive() {
            return getContainerSlot() < state.slots();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (!isActive() || !(stack.getItem() instanceof ModuleItem module)) return false;
            for (int i = 0; i < state.slots(); i++) {
                if (i != getContainerSlot() && state.module(i).getItem() instanceof ModuleItem other && other.type() == module.type()) return false;
            }
            return true;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /** The fitted modules, read and written straight through to the player's jar. */
    private static class ModuleContainer implements Container {
        private final JarState state;

        ModuleContainer(JarState state) {
            this.state = state;
        }

        @Override
        public int getContainerSize() {
            return JarState.MAX_SLOTS;
        }

        @Override
        public boolean isEmpty() {
            return state.modules().stream().allMatch(ItemStack::isEmpty);
        }

        @Override
        public ItemStack getItem(int slot) {
            return state.module(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack stack = state.module(slot);
            if (stack.isEmpty() || amount <= 0) return ItemStack.EMPTY;
            state.setModule(slot, ItemStack.EMPTY);
            return stack;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return removeItem(slot, 1);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            state.setModule(slot, stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
            state.markDirty();
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            for (int i = 0; i < JarState.MAX_SLOTS; i++) state.setModule(i, ItemStack.EMPTY);
        }
    }
}
