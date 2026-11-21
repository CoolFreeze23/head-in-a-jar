package com.coolfreeze23.headinajar.item;

import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.FormEvents;
import com.coolfreeze23.headinajar.staff.StaffShake;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.WorkerLooks;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The Hard Hat: a leather helmet's armour that never wears out (it has no durability: a zombie in the sun would
 * otherwise wear it away by the hour). <b>Use</b> it on a member of staff to put it on: any helmet it wore comes to you,
 * and the first time Health and Safety thanks you. Undead staff wearing one may work the Day shift and do not burn;
 * staff wearing one take no harm from falling blocks, a player wearing one half ({@code WorkRules.damage}). Only a Hard
 * Hat counts for staff: any other helmet offered to staff is refused ("Policy 4.1: hard hats, not helmets."), and a hat
 * offered to anyone who is not staff is refused too. Worn by a player in a jar, it sits on a Hat Mount as any helmet does.
 */
public class HardHatItem extends ArmorItem {
    /** Saved on the worker: it has been issued a Hard Hat before (the thanks are said once). */
    public static final int FLAG_HAT_THANKED = WorkerData.FLAG_HAT_THANKED;

    public HardHatItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
    }

    /** Whether a creature wears a Hard Hat (only a Hard Hat counts for the Day shift: Policy 4.1). */
    public static boolean wears(LivingEntity e) {
        return e.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof HardHatItem;
    }

    /** Whether a stack is something worn on the head other than a Hard Hat (a helmet, a carved pumpkin, a skull). */
    public static boolean otherHelmet(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() instanceof HardHatItem) return false;
        Equipable e = Equipable.get(stack);
        return e != null && e.getEquipmentSlot() == EquipmentSlot.HEAD;
    }

    /** A Hard Hat (or another helmet) used on a creature: handled before the creature's own use. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void use(PlayerInteractEvent.EntityInteract event) {
        if (event.isCanceled()) return;
        ItemStack stack = event.getItemStack();
        Entity target = event.getTarget();
        boolean hat = stack.getItem() instanceof HardHatItem;
        // the client asks too (by the worker look it was sent), so a refused helmet is not worn by the player instead
        if (!hat && !(otherHelmet(stack) && Workforce.isWorkerOn(event.getLevel(), target))) return;
        if (target instanceof ArmorStand || !(target instanceof LivingEntity)) return;
        // a bare jar has no hands: the jar's own line answers (FormEvents)
        if (FormEvents.lockedOut(event.getEntity())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        player.swing(event.getHand(), true);
        if (!hat) {
            Refusals.deny(player, StaffShake.at((Mob) target), "hard_hat.helmet");
            return;
        }
        if (!(target instanceof Mob mob) || !Workforce.isWorker(mob)) {
            Refusals.deny(player, target instanceof Mob m ? StaffShake.at(m) : com.coolfreeze23.headinajar.feedback.Refuser.at(target), "hard_hat.not_staff");
            return;
        }
        issue(player, mob, stack);
    }

    /** Puts a Hard Hat from {@code stack} on a worker; false (refused, with its line) when it already wears one. */
    public static boolean issue(ServerPlayer player, Mob mob, ItemStack stack) {
        WorkerData d = Workforce.data(mob);
        if (d == null) return false;
        if (wears(mob)) {
            Refusals.deny(player, StaffShake.at(mob), "hard_hat.already", Workforce.name(mob));
            return false;
        }
        ItemStack old = mob.getItemBySlot(EquipmentSlot.HEAD);
        mob.setItemSlot(EquipmentSlot.HEAD, stack.copyWithCount(1));
        mob.setGuaranteedDrop(EquipmentSlot.HEAD);
        stack.consume(1, player);
        if (!old.isEmpty() && !player.getInventory().add(old)) player.drop(old, false);
        if (stack.getItem() instanceof ArmorItem armor) {
            mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), armor.getMaterial().value().equipSound().value(), SoundSource.NEUTRAL, 1, 1);
        }
        if (!d.has(FLAG_HAT_THANKED)) {
            d.set(FLAG_HAT_THANKED);
            player.displayClientMessage(Component.translatable("message.headinajar.hard_hat.first"), true);
        }
        WorkerLooks.changed(mob);
        return true;
    }
}
