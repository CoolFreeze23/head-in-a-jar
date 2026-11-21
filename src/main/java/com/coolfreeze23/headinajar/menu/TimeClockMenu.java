package com.coolfreeze23.headinajar.menu;

import com.coolfreeze23.headinajar.block.TimeClockBlockEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.work.ClockNet;
import com.coolfreeze23.headinajar.staff.work.Order;
import com.coolfreeze23.headinajar.staff.work.Shifts;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.UUID;

/**
 * The Time Clock's screen (176 x 222): four tabs along the top, changed by {@link #clickMenuButton} (no payload of its
 * own): the Roster (rows sent by {@link ClockNet}; Rename through {@code WorkerNet.Text}, Recall), the Job Rack (8
 * slots, only this site's cards), the Pay Drawer (9 slots) and the Shifts (Day, Night or Redstone per worker). The rack
 * and drawer slots are live only on their own tab; the player's inventory is below on every tab.
 * <p>
 * Buttons: {@link #TAB} + tab (0-3). Orders for one worker (Recall, a shift) come as {@code ClockNet.ClockOrder} by the
 * worker's UUID, a rename as {@code WorkerNet.Text} by its entity id, so a roster that changes under the screen never
 * sends an order to the wrong worker.
 * the shift's ordinal, acts on the picked row. Every id stays under 128.
 */
public class TimeClockMenu extends AbstractContainerMenu {
    public static final int ROSTER = 0, RACK = 1, DRAWER = 2, SHIFTS = 3;
    public static final int TAB = 0;
    public static final int WIDTH = 176, HEIGHT = 222;
    /** Where the slots sit on the 176 x 222 screen. */
    public static final int RACK_X = 17, RACK_Y = 38, DRAWER_X = 8, DRAWER_Y = 38, INV_X = 8, INV_Y = 140, HOTBAR_Y = 198;
    /** Slot indexes: the rack, then the drawer, then the player's inventory (27) and hotbar (9). */
    public static final int RACK_START = 0, DRAWER_START = RACK_START + TimeClockBlockEntity.RACK, INV_START = DRAWER_START + TimeClockBlockEntity.DRAWER,
            INV_END = INV_START + 36;

    private final TimeClockBlockEntity clock;
    private final BlockPos pos;
    private final Player player;
    private final DataSlot tab = DataSlot.standalone();
    /** Client: the roster row picked for Rename and Recall, or -1 (the screen's own; the server never needs it). */
    private int selected = -1;
    /** Client: the rows as last sent (null until the first arrives, or for a game without the channel). */
    public ClockNet.ClockRoster roster;
    /** Server: the rows last sent, and the ticks since the screen opened. */
    private ClockNet.ClockRoster sent;
    private int ticks;

    /** The client's side, from the clock's position. */
    public TimeClockMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos(), null);
    }

    /** The server's side. */
    public TimeClockMenu(int id, Inventory inventory, TimeClockBlockEntity clock) {
        this(id, inventory, clock.getBlockPos(), clock);
    }

    private TimeClockMenu(int id, Inventory inventory, BlockPos pos, TimeClockBlockEntity serverClock) {
        super(WorkRegistry.TIME_CLOCK_MENU.get(), id);
        this.pos = pos;
        this.player = inventory.player;
        TimeClockBlockEntity c = serverClock;
        if (c == null && player.level().getBlockEntity(pos) instanceof TimeClockBlockEntity there) c = there;
        this.clock = c;
        IItemHandler rack = c != null ? c.rack() : new ItemStackHandler(TimeClockBlockEntity.RACK);
        IItemHandler drawer = c != null ? c.drawer() : new ItemStackHandler(TimeClockBlockEntity.DRAWER);
        for (int i = 0; i < TimeClockBlockEntity.RACK; i++) {
            addSlot(new SlotItemHandler(rack, i, RACK_X + i * 18, RACK_Y) {
                @Override
                public boolean isActive() {
                    return tab.get() == RACK;
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return clock != null && clock.forHere(stack);
                }
            });
        }
        for (int i = 0; i < TimeClockBlockEntity.DRAWER; i++) {
            addSlot(new SlotItemHandler(drawer, i, DRAWER_X + i * 18, DRAWER_Y) {
                @Override
                public boolean isActive() {
                    return tab.get() == DRAWER;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, INV_X + col * 18, HOTBAR_Y));
        addDataSlot(tab);
    }

    /** The clock (the server's; on the client, the client's copy, which holds no staff). */
    public TimeClockBlockEntity clock() {
        return clock;
    }

    public BlockPos pos() {
        return pos;
    }

    public int tab() {
        return tab.get();
    }

    /** Client: shows a tab at once (the server is told by the button and agrees). */
    public void showTab(int t) {
        tab.set(t);
    }

    /** The roster row picked for Rename, Recall and the Shifts buttons, or -1. */
    public int selected() {
        return selected;
    }

    public void select(int row) {
        selected = row;
    }

    // --- buttons ------------------------------------------------------------------------------------------------------

    @Override
    public boolean clickMenuButton(Player p, int id) {
        if (!(p instanceof ServerPlayer sp) || clock == null || !clock.trusts(p)) return false;
        if (id >= TAB && id < TAB + 4) {
            tab.set(id - TAB);
            return true;
        }
        return false;
    }

    /** An order for one worker of this clock's (by UUID): Recall, or a shift. A worker not here is refused with its name. */
    public void order(ServerPlayer sp, int kind, UUID id, int arg) {
        if (clock == null || !clock.trusts(sp) || !clock.has(id)) return;
        Mob mob = worker(id);
        if (mob == null) {
            Refusals.deny(sp, clock, "clock.not_here", rowName(id));
            return;
        }
        if (kind == ClockNet.RECALL) Shifts.recall(sp, clock, mob);
        else if (kind == ClockNet.SHIFT && arg >= 0 && arg < WorkerData.Shift.values().length) Shifts.setShift(sp, clock, mob, WorkerData.Shift.values()[arg]);
        sent = null; // the rows go again on the next tick
    }

    /** A worker of this clock's, if it is in this level and still this clock's; else null. */
    private Mob worker(UUID id) {
        if (clock == null || !(clock.getLevel() instanceof ServerLevel level)) return null;
        Entity e = level.getEntity(id);
        WorkerData d = e instanceof Mob ? Workforce.data(e) : null;
        return d != null && d.clock != null && d.clock.equals(clock.site()) ? (Mob) e : null;
    }

    private Component rowName(UUID id) {
        if (sent != null) for (ClockNet.Row r : sent.rows()) if (r.id().equals(id)) return r.name();
        return Component.translatable("staff.headinajar.another_employer");
    }

    // --- clicks -------------------------------------------------------------------------------------------------------

    @Override
    public void clicked(int slotId, int button, ClickType type, Player p) {
        if (p instanceof ServerPlayer sp && clock != null && slotId >= RACK_START && slotId < DRAWER_START && tab.get() == RACK) {
            ItemStack carried = getCarried();
            if (!carried.isEmpty() && !clock.forHere(carried) && (type == ClickType.PICKUP || type == ClickType.QUICK_CRAFT)) refuseCard(sp, carried);
        }
        super.clicked(slotId, button, type, p);
    }

    private void refuseCard(ServerPlayer sp, ItemStack stack) {
        Order.Card card = Order.card(stack);
        if (card == null) {
            Refusals.deny(sp, clock, "clock.rack_cards_only");
            return;
        }
        BlockPos at = card.order().clock().map(g -> g.pos()).orElse(null);
        if (at == null) Refusals.deny(sp, clock, "clock.rack_cards_only");
        else Refusals.deny(sp, clock, "clock.card_elsewhere", at.getX() + ", " + at.getY() + ", " + at.getZ());
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < INV_START) {
            if (!moveItemStackTo(stack, INV_START, INV_END, true)) return ItemStack.EMPTY;
        } else if (tab.get() == RACK) {
            if (clock == null || !clock.forHere(stack)) {
                if (p instanceof ServerPlayer sp && clock != null) refuseCard(sp, stack);
                return ItemStack.EMPTY;
            }
            if (!moveItemStackTo(stack, RACK_START, DRAWER_START, false)) return ItemStack.EMPTY;
        } else if (tab.get() == DRAWER) {
            if (!moveItemStackTo(stack, DRAWER_START, INV_START, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    // --- the rows -----------------------------------------------------------------------------------------------------

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!(player instanceof ServerPlayer sp) || clock == null) return;
        if (ticks++ % 20 != 0 && sent != null) return;
        ClockNet.ClockRoster now = ClockNet.rows(containerId, clock);
        if (now.equals(sent)) return;
        sent = now;
        ClockNet.send(sp, now);
    }

    @Override
    public boolean stillValid(Player p) {
        if (p.level().isClientSide) return true;
        if (clock == null || clock.isRemoved() || p.level().getBlockEntity(pos) != clock) return false;
        return p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64 && clock.trusts(p) && Workforce.enabled();
    }
}
