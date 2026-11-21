package com.coolfreeze23.headinajar.item;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.block.TimeClockBlockEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.feedback.Refuser;
import com.coolfreeze23.headinajar.form.FormEvents;
import com.coolfreeze23.headinajar.host.HostEvents;
import com.coolfreeze23.headinajar.menu.ClipboardMenu;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffShake;
import com.coolfreeze23.headinajar.staff.work.Order;
import com.coolfreeze23.headinajar.staff.work.Sites;
import com.coolfreeze23.headinajar.staff.work.WorkSounds;
import com.coolfreeze23.headinajar.staff.work.WorkTable;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import com.coolfreeze23.headinajar.staff.work.jobs.Jobs;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * The Clipboard: binds to a Time Clock, marks Pick-Ups, Drop-Offs and areas inside its site, opens the order screen in
 * the air and prints Job Cards; on a worker, assigns it (Use) or lets it go (Sneak + Use, twice).
 * <ul>
 * <li>Use on a Time Clock: binds to its site (the clock's owner or someone they trust).</li>
 * <li>Use a container inside the site: a Pick-Up; Sneak + Use: a Drop-Off. The clicked face is kept (a furnace's top is
 * its input, a side its fuel, the bottom its output, as a hopper sees them).</li>
 * <li>Use two blocks that are not containers: the corners of an area (at most 16 by 16, any height inside the box).</li>
 * <li>Use a marked stop or a corner of an area again: unmarks it.</li>
 * </ul>
 * Every mark is checked against claims as the Bulwark checks a block it lifts ({@code player.mayBuild()},
 * {@code level.mayInteract}); a container's also by posting the use a claim mod would refuse (a
 * {@code PlayerInteractEvent.RightClickBlock} for that player and block). Handled before the block's own use, so a
 * chest does not open and a clock's screen does not open.
 */
public class ClipboardItem extends Item {
    public ClipboardItem(Properties properties) {
        super(properties);
    }

    // --- the order on the board ---------------------------------------------------------------------------------------

    /** Changes the order on a Clipboard. */
    public static void edit(ItemStack board, UnaryOperator<Order> change) {
        board.set(WorkRegistry.ORDER.get(), change.apply(Order.of(board)));
    }

    /** The state of the order's site for its screen ({@code ClipboardMenu.UNBOUND} ...). Never loads a chunk. */
    public static int siteState(ServerPlayer player, Order o) {
        if (o.clock().isEmpty()) return ClipboardMenu.UNBOUND;
        GlobalPos site = o.clock().get();
        ServerLevel level = player.server.getLevel(site.dimension());
        if (level == null) return ClipboardMenu.CLOSED;
        if (!level.isLoaded(site.pos())) return ClipboardMenu.AWAY;
        return level.getBlockEntity(site.pos()) instanceof TimeClockBlockEntity ? ClipboardMenu.BOUND : ClipboardMenu.CLOSED;
    }

    // --- blocks ---------------------------------------------------------------------------------------------------

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(context.getPlayer() instanceof ServerPlayer player) || !(level instanceof ServerLevel server)) return InteractionResult.PASS;
        mark(player, server, stack, context.getClickedPos(), context.getClickedFace(), context.getHand(),
                new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), context.isInside()));
        player.swing(context.getHand(), true);
        return InteractionResult.CONSUME;
    }

    /** One click of the Clipboard on a block: bind, mark, unmark or refuse. Returns whether the order changed. */
    public static boolean mark(ServerPlayer player, ServerLevel level, ItemStack board, BlockPos pos, Direction face, InteractionHand hand, BlockHitResult hit) {
        Refuser here = at(level, pos);
        if (level.getBlockEntity(pos) instanceof TimeClockBlockEntity clock) return bind(player, board, clock);
        Order o = Order.of(board);
        if (o.clock().isEmpty()) return Refusals.deny(player, here, "clipboard.unbound");
        GlobalPos site = o.clock().get();
        int state = siteState(player, o);
        if (state == ClipboardMenu.CLOSED) return Refusals.deny(player, here, "clipboard.closed");
        if (!site.dimension().equals(level.dimension()) || !Workforce.inBox(site.pos(), pos)) {
            return Refusals.deny(player, here, "clipboard.outside", Workforce.radius());
        }
        // a mark again: unmark it
        Order.Stop stop = o.stopAt(pos);
        if (stop != null) {
            List<Order.Stop> l = new ArrayList<>(o.stops());
            l.remove(stop);
            board.set(WorkRegistry.ORDER.get(), o.withStops(l));
            return said(player, level, pos, "message.headinajar.clipboard.unmarked");
        }
        Order.Area area = o.areaAt(pos);
        if (area != null) {
            List<Order.Area> l = new ArrayList<>(o.areas());
            l.remove(area);
            board.set(WorkRegistry.ORDER.get(), o.withAreas(l));
            return said(player, level, pos, "message.headinajar.clipboard.area_unmarked");
        }
        if (o.corner().isPresent() && o.corner().get().equals(pos)) {
            board.set(WorkRegistry.ORDER.get(), o.withCorner(null));
            return said(player, level, pos, "message.headinajar.clipboard.corner_cleared");
        }
        if (!player.mayBuild() || !level.mayInteract(player, pos)) return Refusals.deny(player, here, "clipboard.not_yours");
        boolean container = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, face) != null
                || level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null) != null;
        if (container) {
            // opening is what a claim protects: ask as if the player were opening it
            PlayerInteractEvent.RightClickBlock ask = new PlayerInteractEvent.RightClickBlock(player, hand, pos, hit);
            NeoForge.EVENT_BUS.post(ask);
            if (ask.isCanceled() || ask.getUseBlock() == net.neoforged.neoforge.common.util.TriState.FALSE) {
                return Refusals.deny(player, here, "clipboard.not_yours");
            }
            if (o.stops().size() >= Order.MAX_STOPS) return Refusals.deny(player, here, "clipboard.full", Order.MAX_STOPS);
            boolean dropOff = player.isSecondaryUseActive();
            List<Order.Stop> l = new ArrayList<>(o.stops());
            l.add(new Order.Stop(pos.immutable(), face, dropOff, ItemStack.EMPTY, false));
            board.set(WorkRegistry.ORDER.get(), o.withStops(l));
            return said(player, level, pos, dropOff ? "message.headinajar.clipboard.drop_off" : "message.headinajar.clipboard.pick_up");
        }
        // a corner
        if (o.corner().isEmpty()) {
            if (o.areas().size() >= Order.MAX_AREAS) return Refusals.deny(player, here, "clipboard.areas_full", Order.MAX_AREAS);
            board.set(WorkRegistry.ORDER.get(), o.withCorner(pos.immutable()));
            return said(player, level, pos, "message.headinajar.clipboard.corner");
        }
        Order.Area a = new Order.Area(o.corner().get(), pos.immutable());
        if (a.sizeX() > Order.FIELD || a.sizeZ() > Order.FIELD) return Refusals.deny(player, here, "clipboard.too_big");
        List<Order.Area> l = new ArrayList<>(o.areas());
        l.add(a);
        board.set(WorkRegistry.ORDER.get(), o.withAreas(l).withCorner(null));
        player.displayClientMessage(Component.translatable("message.headinajar.clipboard.area", a.sizeX(), a.sizeZ()), true);
        scribble(level, pos);
        return true;
    }

    private static boolean bind(ServerPlayer player, ItemStack board, TimeClockBlockEntity clock) {
        if (clock.owner() != null && !com.coolfreeze23.headinajar.form.HeadRegistry.get(player).trusts(clock.owner(), player)) {
            return Refusals.deny(player, Refuser.at(clock), "clock.untrusted", clock.ownerName());
        }
        Order o = Order.of(board);
        GlobalPos site = clock.site();
        // a new site: the old site's marks mean nothing here
        if (o.clock().isEmpty() || !o.clock().get().equals(site)) o = Order.EMPTY.withJob(o.job()).withName(o.name()).withClock(site);
        board.set(WorkRegistry.ORDER.get(), o);
        player.displayClientMessage(Component.translatable("message.headinajar.clipboard.bound"), true);
        scribble((ServerLevel) clock.getLevel(), clock.getBlockPos());
        return true;
    }

    private static boolean said(ServerPlayer player, ServerLevel level, BlockPos pos, String key) {
        player.displayClientMessage(Component.translatable(key), true);
        scribble(level, pos);
        return true;
    }

    /** The pen on paper. */
    private static void scribble(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 0.6f, 1.3f);
    }

    private static Refuser at(Level level, BlockPos pos) {
        var block = level.getBlockEntity(pos);
        if (block != null) return Refuser.at(block);
        Vec3 centre = Vec3.atCenterOf(pos);
        return () -> centre;
    }

    // --- the air: the order screen --------------------------------------------------------------------------------

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new ClipboardMenu(id, inv, hand), Component.translatable("gui.headinajar.clipboard.title")),
                    buf -> buf.writeEnum(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * Print: one Paper from the inventory becomes a Job Card of the order. Refused unbound, for a closed site, with
     * nothing a job needs marked (Hauling and Sorting: a Pick-Up and a Drop-Off, Sorting only one Pick-Up; Farming: a
     * field and a Drop-Off for the harvest; Security may patrol the whole site with nothing marked), or with no paper.
     */
    public static boolean print(ServerPlayer player, ItemStack board) {
        Order o = Order.of(board);
        int state = siteState(player, o);
        if (state == ClipboardMenu.UNBOUND) return Refusals.deny(player, "clipboard.unbound");
        if (state == ClipboardMenu.CLOSED) return Refusals.deny(player, "clipboard.closed");
        boolean nothing = o.stops().isEmpty() && o.areas().isEmpty();
        switch (o.job()) {
            case WorkTable.HAULING, WorkTable.SORTING -> {
                if (nothing) return Refusals.deny(player, "clipboard.empty");
                if (o.pickUps().isEmpty() || o.dropOffs().isEmpty()) return Refusals.deny(player, "clipboard.needs_both");
                if (o.job().equals(WorkTable.SORTING) && o.pickUps().size() > 1) return Refusals.deny(player, "clipboard.one_pick_up");
            }
            case WorkTable.FARMING -> {
                if (nothing) return Refusals.deny(player, "clipboard.empty");
                if (o.areas().isEmpty()) return Refusals.deny(player, "clipboard.no_field");
                if (o.dropOffs().isEmpty()) return Refusals.deny(player, "clipboard.field_drop_off");
            }
            default -> {
            }
        }
        if (!player.getAbilities().instabuild) {
            int slot = -1;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (player.getInventory().getItem(i).is(Items.PAPER)) {
                    slot = i;
                    break;
                }
            }
            if (slot < 0) return Refusals.deny(player, "clipboard.no_paper");
            player.getInventory().getItem(slot).shrink(1);
        }
        ItemStack card = new ItemStack(WorkRegistry.JOB_CARD.get());
        card.set(WorkRegistry.JOB_CARD_DATA.get(), new Order.Card(o.withCorner(null), player.getGameProfile().getName(), Staff.day(player.serverLevel())));
        if (!player.getInventory().add(card)) player.drop(card, false);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), WorkSounds.CARD_PRINTED.get(), SoundSource.PLAYERS, 1, 1);
        player.displayClientMessage(Component.translatable(player.getAbilities().instabuild ? "message.headinajar.clipboard.printed_free" : "message.headinajar.clipboard.printed"), true);
        return true;
    }

    // --- workers ----------------------------------------------------------------------------------------------------

    /** Use on a worker assigns it to the bound site; Sneak + Use is Let go (twice). Before the creature's own use. */
    public static void useOnCreature(PlayerInteractEvent.EntityInteract event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(WorkRegistry.CLIPBOARD.get())) return;
        if (FormEvents.lockedOut(event.getEntity())) return;
        Entity target = HostEvents.whole(event.getTarget());
        if (!(target instanceof Mob mob)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getEntity() instanceof ServerPlayer player) {
            onWorker(player, stack, mob);
            player.swing(event.getHand(), true);
        }
    }

    /** The Clipboard on a creature: assign it to the bound site, or (sneaking) let it go. Returns whether anything changed. */
    public static boolean onWorker(ServerPlayer player, ItemStack board, Mob mob) {
        WorkerData d = Workforce.data(mob);
        if (d == null) return Refusals.deny(player, Refuser.at(mob), "clipboard.not_staff");
        if (player.isSecondaryUseActive()) return Workforce.requestLetGo(player, mob);
        if (!Workforce.mayOrder(mob, player)) {
            return Refusals.deny(player, StaffShake.at(mob), "staff.not_yours", Workforce.name(mob), Workforce.employerName(mob));
        }
        if (d.state == WorkerData.State.NOTICE) return Refusals.deny(player, StaffShake.at(mob), "staff.already_let_go", Workforce.name(mob));
        if (d.state == WorkerData.State.DETAIL) return Refusals.deny(player, StaffShake.at(mob), "clipboard.on_detail", Workforce.name(mob));
        Order o = Order.of(board);
        if (o.clock().isEmpty()) return Refusals.deny(player, StaffShake.at(mob), "clipboard.unbound");
        GlobalPos site = o.clock().get();
        ServerLevel level = (ServerLevel) mob.level();
        if (!site.dimension().equals(level.dimension())) return Refusals.deny(player, StaffShake.at(mob), "clipboard.outside", Workforce.radius());
        TimeClockBlockEntity clock = Sites.clock(level, site);
        if (clock == null) {
            return Refusals.deny(player, StaffShake.at(mob), level.isLoaded(site.pos()) ? "clipboard.closed" : "clipboard.outside", Workforce.radius());
        }
        if (clock.owner() != null && !clock.owner().equals(d.employer)) {
            return Refusals.deny(player, StaffShake.at(mob), "clipboard.not_their_site", clock.ownerName(), Workforce.name(mob), Workforce.employerName(mob));
        }
        if (site.equals(d.clock) && clock.has(mob.getUUID())) return Refusals.deny(player, StaffShake.at(mob), "clipboard.already_here", Workforce.name(mob));
        if (clock.room() <= 0) return Refusals.deny(player, Refuser.at(clock), "clock.full", Config.get(Config.MAX_PER_CLOCK, 8));
        // off the old site: its task ends, a card of the old site's goes back to that rack (or to whoever assigns it)
        Jobs.end(mob, d);
        if (d.clock != null && !d.clock.equals(site)) {
            if (!d.card.isEmpty()) {
                ItemStack old = d.card;
                d.card = ItemStack.EMPTY;
                if (!Sites.returnCard(level, d.clock, old) && !player.getInventory().add(old)) player.drop(old, false);
            }
            Sites.leave(level, d.clock, mob.getUUID());
        }
        if (!clock.join(mob)) return Refusals.deny(player, Refuser.at(clock), "clock.full", Config.get(Config.MAX_PER_CLOCK, 8));
        player.displayClientMessage(Component.translatable("message.headinajar.clipboard.assigned", Workforce.name(mob)), true);
        scribble(level, mob.blockPosition());
        return true;
    }

    // --- the tooltip --------------------------------------------------------------------------------------------------

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        Order o = Order.of(stack);
        if (o.clock().isEmpty()) {
            lines.add(Component.translatable("tooltip.headinajar.clipboard.unbound").withStyle(ChatFormatting.GRAY));
            return;
        }
        JobCardItem.describe(o, lines);
    }
}
