package com.coolfreeze23.headinajar.item;

import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.FormEvents;
import com.coolfreeze23.headinajar.host.HostEvents;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The Staff Lanyard: Use it on a living creature to hire it (a string loop and a brass badge over its head). The checks
 * are {@code Workforce.refusal}'s, in the book's order; a refused candidate shakes its head and the badge spins back.
 * Handled before the creature's own use (a villager would otherwise open its trades).
 */
public class StaffLanyardItem extends Item {
    public StaffLanyardItem(Properties properties) {
        super(properties);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void use(PlayerInteractEvent.EntityInteract event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(WorkRegistry.STAFF_LANYARD.get())) return;
        // a bare jar has no hands: the jar's own line answers (FormEvents)
        if (FormEvents.lockedOut(event.getEntity())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getEntity() instanceof ServerPlayer player) {
            var target = HostEvents.whole(event.getTarget());
            Workforce.useLanyard(player, stack, target);
            player.swing(event.getHand(), true);
        }
    }

    /** In the air or on a block there is nobody to hire: it says so. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp) Refusals.deny(sp, "lanyard.nobody");
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer sp) Refusals.deny(sp, "lanyard.nobody");
        return InteractionResult.CONSUME;
    }
}
