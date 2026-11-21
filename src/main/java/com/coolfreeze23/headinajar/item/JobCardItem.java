package com.coolfreeze23.headinajar.item;

import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.feedback.Refuser;
import com.coolfreeze23.headinajar.form.FormEvents;
import com.coolfreeze23.headinajar.host.HostEvents;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.StaffShake;
import com.coolfreeze23.headinajar.staff.work.Order;
import com.coolfreeze23.headinajar.staff.work.Sites;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.WorkerLooks;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import com.coolfreeze23.headinajar.staff.work.jobs.Jobs;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;

/**
 * A Job Card: an order printed on paper. It goes in a Time Clock's Job Rack (a posting idle staff take), or is given by
 * hand to one worker of its site (Use on the worker): that worker works it alone, before anything on the rack.
 */
public class JobCardItem extends Item {
    public JobCardItem(Properties properties) {
        super(properties);
    }

    /** Use on a worker gives it the card. Before the creature's own use (a villager would open its trades). */
    public static void useOnCreature(PlayerInteractEvent.EntityInteract event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(WorkRegistry.JOB_CARD.get())) return;
        if (FormEvents.lockedOut(event.getEntity())) return;
        Entity target = HostEvents.whole(event.getTarget());
        if (!(target instanceof Mob mob)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getEntity() instanceof ServerPlayer player) {
            give(player, stack, mob);
            player.swing(event.getHand(), true);
        }
    }

    /**
     * Gives a card to a worker: refused for a creature that is not staff, someone else's, a blank card, a card of
     * another site or of a site that has closed, or a card it cannot do ({@code Jobs.refusal}). Returns whether it was given.
     */
    public static boolean give(ServerPlayer player, ItemStack stack, Mob mob) {
        WorkerData d = Workforce.data(mob);
        if (d == null) return Refusals.deny(player, Refuser.at(mob), "clipboard.not_staff");
        if (!Workforce.mayOrder(mob, player)) {
            return Refusals.deny(player, StaffShake.at(mob), "staff.not_yours", Workforce.name(mob), Workforce.employerName(mob));
        }
        if (d.state == WorkerData.State.NOTICE) return Refusals.deny(player, StaffShake.at(mob), "staff.already_let_go", Workforce.name(mob));
        Order.Card card = Order.card(stack);
        if (card == null || card.order().clock().isEmpty()) return Refusals.deny(player, StaffShake.at(mob), "card.blank");
        GlobalPos site = card.order().clock().get();
        ServerLevel level = (ServerLevel) mob.level();
        if (closed(level, site)) return Refusals.deny(player, StaffShake.at(mob), "card.closed");
        if (d.clock == null) return Refusals.deny(player, StaffShake.at(mob), "card.no_site", Workforce.name(mob));
        if (!site.equals(d.clock)) return Refusals.deny(player, StaffShake.at(mob), "card.other_site", where(site.pos()));
        Object[] no = Jobs.refusal(mob, card);
        if (no != null) return Refusals.deny(player, StaffShake.at(mob), (String) no[0], java.util.Arrays.copyOfRange(no, 1, no.length));
        // the old personal card goes back to the rack (or to the giver); a rack posting is let go
        Jobs.end(mob, d);
        if (!d.card.isEmpty()) {
            ItemStack old = d.card;
            d.card = ItemStack.EMPTY;
            if (!Sites.returnCard(level, d.clock, old) && !player.getInventory().add(old)) player.drop(old, false);
        }
        var clock = Sites.clock(level, d.clock);
        if (clock != null) clock.take(mob.getUUID(), -1);
        d.card = stack.copyWithCount(1);
        stack.shrink(1);
        // on the job it simply goes on with the new card; otherwise the site's day brings it to the card (a personal card first)
        d.rackSlot = -1;
        Jobs.read(mob, Order.card(d.card));
        WorkerLooks.changed(mob);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 1, 1.1f);
        player.displayClientMessage(Component.translatable("message.headinajar.card.given", Workforce.name(mob)), true);
        com.coolfreeze23.headinajar.Advance.grant(player, "in_writing");
        return true;
    }

    /** Whether a card's site has closed (its clock is gone; a clock whose chunk is not loaded may still be there). */
    public static boolean closed(ServerLevel any, GlobalPos site) {
        ServerLevel level = any.getServer().getLevel(site.dimension());
        if (level == null) return true;
        if (!level.isLoaded(site.pos())) return false;
        return !(level.getBlockEntity(site.pos()) instanceof com.coolfreeze23.headinajar.block.TimeClockBlockEntity);
    }

    /** A site's position as lines say it. */
    public static String where(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    /** Nothing to give a card to in the air or on a block (the Job Rack takes it in the clock's screen): it says so. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp) Refusals.deny(sp, "card.how");
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer sp) Refusals.deny(sp, "card.how");
        return InteractionResult.CONSUME;
    }

    // --- the tooltip --------------------------------------------------------------------------------------------------

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        Order.Card card = Order.card(stack);
        if (card == null) {
            lines.add(Component.translatable("tooltip.headinajar.job_card.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        describe(card.order(), lines);
        if (!card.printer().isEmpty()) {
            lines.add(Component.translatable("tooltip.headinajar.job_card.printed", card.printer(),
                    com.coolfreeze23.headinajar.staff.Staff.shown(card.day())).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** An order's lines: its name, job, stops and areas, and site. */
    public static void describe(Order o, List<Component> lines) {
        if (!o.name().isEmpty()) lines.add(Component.literal("“" + o.name() + "”").withStyle(ChatFormatting.WHITE));
        lines.add(Component.translatable("tooltip.headinajar.job_card.job", Component.translatable("staff.headinajar.job." + o.job()))
                .withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("tooltip.headinajar.job_card.stops", o.pickUps().size(), o.dropOffs().size(), o.areas().size())
                .withStyle(ChatFormatting.GRAY));
        o.clock().ifPresent(c -> lines.add(Component.translatable("tooltip.headinajar.job_card.site", where(c.pos())).withStyle(ChatFormatting.GRAY)));
    }
}
