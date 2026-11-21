package com.coolfreeze23.headinajar.menu;

import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.item.ClipboardItem;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.work.CardFilters;
import com.coolfreeze23.headinajar.staff.work.Order;
import com.coolfreeze23.headinajar.staff.work.WorkTable;
import net.minecraft.core.BlockPos;
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
 * The Clipboard's order screen: the job, the stops and areas, one ghost filter slot for each Drop-Off (and on a Sorting
 * order, eight more for the Drop-Off picked for them, 0.7.2), "Everything else", the name, Print. The order lives on the
 * held Clipboard (its data components, synced like any item), so the screen reads it from the hand; changes go to the
 * server by {@link #clickMenuButton} (the job, "Everything else", unmarking, Print), a ghost slot click (a filter: the
 * carried item's kind, or empty to clear), or {@code WorkerNet.Text} ORDER (the name, {@link #name}). The site's state
 * comes in a data slot.
 * <p>
 * Slots: 0 to {@link #GHOSTS}-1 are the filters, stop by stop (active only for a Drop-Off); then the player's
 * inventory (27) and hotbar (9); then (0.7.2) the {@link CardFilters#EXTRA} extra filters of the Drop-Off picked for
 * them on a Sorting order (active only while one is picked). The held Clipboard's hotbar slot cannot be picked up. The
 * picked Drop-Off is a data slot: the screen sets it at once and tells the server by the {@link #MORE} button.
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
    /** Pick a Sorting Drop-Off for its extra filters, or put them away when it is picked already (+ the stop's index). */
    public static final int MORE = 80;

    /** The player's inventory and hotbar, then the extra filter slots (appended, so no older slot moves). */
    public static final int INV_START = GHOSTS, INV_END = GHOSTS + 36, EXTRA_START = INV_END, EXTRA_END = EXTRA_START + CardFilters.EXTRA;
    /** Where the extra filter slots sit: a row in the left column, where a Sorting order has no areas. */
    public static final int EXTRA_X = 9, EXTRA_Y = 101;

    /** The site, as the data slot says: not bound, bound and standing, closed (the clock is gone), out of reach (not loaded). */
    public static final int UNBOUND = 0, BOUND = 1, CLOSED = 2, AWAY = 3;

    /** Where the slots sit (the screen draws to match). */
    public static final int FILTER_X = 387, FILTER_Y = 19, ROW = 18, INV_X = 8, INV_Y = 159;

    public final InteractionHand hand;
    private final Player player;
    private final DataSlot site = DataSlot.standalone();
    /** The stop picked for its extra filters, or -1. */
    private final DataSlot picked = DataSlot.standalone();

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
        Container extras = new SimpleContainer(CardFilters.EXTRA);
        for (int k = 0; k < CardFilters.EXTRA; k++) addSlot(new ExtraSlot(extras, k, EXTRA_X + k * 18, EXTRA_Y));
        addDataSlot(site);
        picked.set(-1);
        addDataSlot(picked);
        if (player instanceof ServerPlayer sp) site.set(ClipboardItem.siteState(sp, order()));
    }

    /** The Drop-Off picked for its extra filters (a Sorting order's), or -1. */
    public int picked() {
        int i = picked.get();
        return pickable(order(), i) ? i : -1;
    }

    /** Client: picks a Drop-Off for its extra filters at once, or puts them away (the server is told by the button and agrees). */
    public void pick(int stop) {
        picked.set(picked() == stop ? -1 : stop);
    }

    /** Whether a stop may have extra filters: a Drop-Off of a Sorting order. */
    public static boolean pickable(Order o, int stop) {
        return o.job().equals(WorkTable.SORTING) && stop >= 0 && stop < o.stops().size() && o.stops().get(stop).dropOff();
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
            // the extras' annex follows its Drop-Off up the list, and goes with it
            int was = picked.get();
            picked.set(was == i ? -1 : was > i ? was - 1 : was);
            return true;
        }
        if (id >= MORE && id < MORE + GHOSTS) {
            int i = id - MORE;
            String no = !o.job().equals(WorkTable.SORTING) ? "clipboard.more_sorting" : !pickable(o, i) ? "clipboard.more_drop_off" : null;
            if (no != null) {
                // the screen picked it at once: put it back as it was
                sendAllDataToRemote();
                return Refusals.deny(sp, no);
            }
            // as the screen did at once: the same stop again puts the annex away
            pick(i);
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
        if (slotId >= EXTRA_START && slotId < EXTRA_END) {
            // an extra filter: the same, for the picked Drop-Off
            if (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) setExtra(slotId - EXTRA_START, button == 1 ? ItemStack.EMPTY : getCarried());
            return;
        }
        super.clicked(slotId, button, type, p);
    }

    /** Sets (or clears) one extra filter of the picked Drop-Off. Returns whether it changed. */
    public boolean setExtra(int slot, ItemStack kind) {
        int i = picked();
        if (i < 0 || slot < 0 || slot >= CardFilters.EXTRA) return false;
        ItemStack board = clipboard();
        BlockPos pos = order().stops().get(i).pos();
        CardFilters.put(board, CardFilters.onBoard(board).withExtra(pos, slot, kind));
        CardFilters.tidy(board);
        return true;
    }

    /** Sets (or clears) the filter of the Drop-Off at a stop index. Returns whether it changed. */
    public boolean setFilter(int stop, ItemStack kind) {
        Order o = order();
        if (stop < 0 || stop >= o.stops().size() || !o.stops().get(stop).dropOff()) return false;
        ClipboardItem.edit(clipboard(), x -> x.withStop(stop, x.stops().get(stop).withFilter(kind)));
        return true;
    }

    /**
     * Shift-click from the inventory: that item becomes the filter of the first Drop-Off without one; with a Drop-Off
     * picked for its extras, its own filter if it has none, else its first free extra (all nine full: refused).
     */
    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        if (index < INV_START || index >= INV_END) return ItemStack.EMPTY;
        ItemStack stack = slots.get(index).getItem();
        if (stack.isEmpty() || stack.is(WorkRegistry.CLIPBOARD.get())) return ItemStack.EMPTY;
        Order o = order();
        int at = picked();
        if (at >= 0) {
            Order.Stop s = o.stops().get(at);
            if (s.filter().isEmpty()) {
                setFilter(at, stack);
                return ItemStack.EMPTY;
            }
            java.util.List<ItemStack> extras = CardFilters.onBoard(clipboard()).extras(s.pos());
            for (int k = 0; k < extras.size(); k++) {
                if (extras.get(k).isEmpty()) {
                    setExtra(k, stack);
                    return ItemStack.EMPTY;
                }
            }
            if (p instanceof ServerPlayer sp) Refusals.deny(sp, "clipboard.filters_full");
            return ItemStack.EMPTY;
        }
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
        // a picked stop that is no longer a Sorting Drop-Off (another job, unmarked) is put away
        if (picked.get() >= 0 && picked() < 0) picked.set(-1);
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

    /** An extra ghost filter slot (0.7.2): shows that extra filter of the picked Drop-Off; nothing goes in or out. */
    public class ExtraSlot extends Slot {
        public final int extra;

        ExtraSlot(Container ghosts, int extra, int x, int y) {
            super(ghosts, extra, x, y);
            this.extra = extra;
        }

        @Override
        public ItemStack getItem() {
            int i = picked();
            return i < 0 ? ItemStack.EMPTY : CardFilters.onBoard(clipboard()).extras(order().stops().get(i).pos()).get(extra);
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
            return picked() >= 0;
        }
    }
}
