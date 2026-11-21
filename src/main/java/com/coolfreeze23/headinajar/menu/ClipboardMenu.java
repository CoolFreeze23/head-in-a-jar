package com.coolfreeze23.headinajar.menu;

import com.coolfreeze23.headinajar.item.ClipboardItem;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.work.Order;
import com.coolfreeze23.headinajar.staff.work.WorkTable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Clipboard's order screen: the job, the stops and areas, one ghost filter slot for each Drop-Off (0.7.0: one filter
 * a Drop-Off, no grades), "Everything else", the name, Print. The order lives on the held Clipboard (its data component,
 * synced like any item), so the screen reads it from the hand; changes go to the server by {@link #clickMenuButton} (the
 * job, "Everything else", unmarking, Print), a ghost slot click (a filter: the carried item's kind, or empty to clear),
 * or {@code WorkerNet.Text} ORDER (the name, {@link #name}). The site's state comes in a data slot.
 * <p>
 * Slots: 0 to {@link #GHOSTS}-1 are the filters, stop by stop (active only for a Drop-Off); then the player's
 * inventory (27) and hotbar (9). The held Clipboard's hotbar slot cannot be picked up.
 */
public class ClipboardMenu extends AbstractContainerMenu {
    /** Ghost filter slots: one for each possible stop (the stop of the same index). */
    public static final int GHOSTS = Order.MAX_STOPS;

    /** Buttons ({@link #clickMenuButton} ids): a job (+ its index in {@code WorkTable.JOBS}). */
    public static final int JOB = 0;
    /** Toggle "Everything else" on a Drop-Off (+ the stop's index). */
    public static final int EVERYTHING_ELSE = 10;
    /** Unmark a stop (+ its index). */
    public static final int UNMARK_STOP = 30;
    /** Unmark an area (+ its index). */
    public static final int UNMARK_AREA = 50;
    /** Print a Job Card. */
    public static final int PRINT = 70;

    /** The site, as the data slot says: not bound, bound and standing, closed (the clock is gone), out of reach (not loaded). */
    public static final int UNBOUND = 0, BOUND = 1, CLOSED = 2, AWAY = 3;

    /** Where the slots sit (the screen draws to match). */
    public static final int FILTER_X = 387, FILTER_Y = 19, ROW = 18, INV_X = 8, INV_Y = 159;

    public final InteractionHand hand;
    private final Player player;
    private final DataSlot site = DataSlot.standalone();

    public ClipboardMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readEnum(InteractionHand.class));
    }

    public ClipboardMenu(int id, Inventory inventory, InteractionHand hand) {
        super(WorkRegistry.CLIPBOARD_MENU.get(), id);
        this.hand = hand;
        this.player = inventory.player;
        Container ghosts = new SimpleContainer(GHOSTS);
        for (int i = 0; i < GHOSTS; i++) addSlot(new FilterSlot(ghosts, i, FILTER_X, FILTER_Y + i * ROW));
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++) addSlot(new Slot(inventory, 9 + r * 9 + c, INV_X + c * 18, INV_Y + r * 18));
        int held = hand == InteractionHand.MAIN_HAND ? inventory.selected : -1;
        for (int c = 0; c < 9; c++) {
            addSlot(c == held ? new Slot(inventory, c, INV_X + c * 18, INV_Y + 58) {
                @Override
                public boolean mayPickup(Player p) {
                    return false;
                }
            } : new Slot(inventory, c, INV_X + c * 18, INV_Y + 58));
        }
        addDataSlot(site);
        if (player instanceof ServerPlayer sp) site.set(ClipboardItem.siteState(sp, order()));
    }

    /** The held Clipboard. */
    public ItemStack clipboard() {
        return player.getItemInHand(hand);
    }

    /** The order on the held Clipboard. */
    public Order order() {
        return Order.of(clipboard());
    }

    /** The site's state ({@link #UNBOUND}, {@link #BOUND}, {@link #CLOSED}, {@link #AWAY}). */
    public int site() {
        return site.get();
    }

    /** The name typed for the order on the held Clipboard (from the screen's text box, by {@code WorkerNet.Text}). */
    public static void name(ServerPlayer player, String name) {
        if (!(player.containerMenu instanceof ClipboardMenu menu) || !menu.stillValid(player)) return;
        String clean = name == null ? "" : name.replaceAll("[\\p{Cntrl}§]", "").strip();
        ClipboardItem.edit(menu.clipboard(), o -> o.withName(clean));
    }

    @Override
    public boolean clickMenuButton(Player p, int id) {
        if (!(p instanceof ServerPlayer sp) || !stillValid(p)) return false;
        ItemStack board = clipboard();
        Order o = order();
        if (id >= JOB && id < JOB + WorkTable.JOBS.size()) {
            ClipboardItem.edit(board, x -> x.withJob(WorkTable.JOBS.get(id - JOB)));
            return true;
        }
        if (id >= EVERYTHING_ELSE && id < EVERYTHING_ELSE + GHOSTS) {
            int i = id - EVERYTHING_ELSE;
            if (i >= o.stops().size() || !o.stops().get(i).dropOff()) return false;
            boolean on = !o.stops().get(i).everythingElse();
            // one "Everything else" an order: turning one on turns the others off
            ClipboardItem.edit(board, x -> {
                java.util.List<Order.Stop> l = new java.util.ArrayList<>();
                for (int k = 0; k < x.stops().size(); k++) {
                    Order.Stop s = x.stops().get(k);
                    l.add(k == i ? s.withEverythingElse(on) : on && s.everythingElse() ? s.withEverythingElse(false) : s);
                }
                return x.withStops(l);
            });
            return true;
        }
        if (id >= UNMARK_STOP && id < UNMARK_STOP + GHOSTS) {
            int i = id - UNMARK_STOP;
            if (i >= o.stops().size()) return false;
            ClipboardItem.edit(board, x -> {
                java.util.List<Order.Stop> l = new java.util.ArrayList<>(x.stops());
                l.remove(i);
                return x.withStops(l);
            });
            return true;
        }
        if (id >= UNMARK_AREA && id < UNMARK_AREA + Order.MAX_AREAS) {
            int i = id - UNMARK_AREA;
            if (i >= o.areas().size()) return false;
            ClipboardItem.edit(board, x -> {
                java.util.List<Order.Area> l = new java.util.ArrayList<>(x.areas());
                l.remove(i);
                return x.withAreas(l);
            });
            return true;
        }
        if (id == PRINT) return ClipboardItem.print(sp, board);
        return false;
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player p) {
        if (slotId >= 0 && slotId < GHOSTS) {
            // a ghost slot: the carried item's kind becomes the filter (nothing is taken), an empty hand clears it
            if (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) setFilter(slotId, button == 1 ? ItemStack.EMPTY : getCarried());
            return;
        }
        super.clicked(slotId, button, type, p);
    }

    /** Sets (or clears) the filter of the Drop-Off at a stop index. Returns whether it changed. */
    public boolean setFilter(int stop, ItemStack kind) {
        Order o = order();
        if (stop < 0 || stop >= o.stops().size() || !o.stops().get(stop).dropOff()) return false;
        ClipboardItem.edit(clipboard(), x -> x.withStop(stop, x.stops().get(stop).withFilter(kind)));
        return true;
    }

    /** Shift-click from the inventory: that item becomes the filter of the first Drop-Off without one. */
    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        if (index < GHOSTS || index >= slots.size()) return ItemStack.EMPTY;
        ItemStack stack = slots.get(index).getItem();
        if (stack.isEmpty() || stack.is(WorkRegistry.CLIPBOARD.get())) return ItemStack.EMPTY;
        Order o = order();
        for (int i = 0; i < o.stops().size(); i++) {
            Order.Stop s = o.stops().get(i);
            if (s.dropOff() && s.filter().isEmpty()) {
                setFilter(i, stack);
                break;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void broadcastChanges() {
        if (player instanceof ServerPlayer sp) site.set(ClipboardItem.siteState(sp, order()));
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player p) {
        return p.getItemInHand(hand).is(WorkRegistry.CLIPBOARD.get());
    }

    /** A ghost filter slot: shows the filter of the stop of its index; nothing goes in or out. */
    public class FilterSlot extends Slot {
        public final int stop;

        FilterSlot(Container ghosts, int stop, int x, int y) {
            super(ghosts, stop, x, y);
            this.stop = stop;
        }

        @Override
        public ItemStack getItem() {
            Order o = order();
            return stop < o.stops().size() && o.stops().get(stop).dropOff() ? o.stops().get(stop).filter() : ItemStack.EMPTY;
        }

        @Override
        public boolean hasItem() {
            return !getItem().isEmpty();
        }

        @Override
        public void set(ItemStack stack) {
        }

        @Override
        public void setByPlayer(ItemStack stack, ItemStack previous) {
        }

        @Override
        public ItemStack remove(int amount) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player p) {
            return false;
        }

        @Override
        public boolean isActive() {
            Order o = order();
            return stop < o.stops().size() && o.stops().get(stop).dropOff();
        }
    }
}
