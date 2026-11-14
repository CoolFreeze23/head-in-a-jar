package com.coolfreeze23.headinajar.host;

import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.entity.ShellEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.feedback.Refuser;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.FormEvents;
import com.coolfreeze23.headinajar.item.GeneticsItems;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Using a host, a shell or a mecha. Instruments come first: a held tool or consumable of the mod (a syringe, a serum,
 * Embalming Fluid, Neural Lace, plating, fuel) does its own job before anything else. Then the few vanilla items that
 * make sense on a body (a lead, a name tag) work as they would, and the ones that don't (a saddle, food) are refused.
 * Only then a jar climbs on, a passenger takes a seat, a cargo hold opens, or the status line is shown. With an empty
 * hand this is the same path as it always was.
 */
public final class HostInteractions {
    private HostInteractions() {
    }

    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Mob host)) return;
        Player clicker = event.getEntity();
        ServerPlayer player = clicker instanceof ServerPlayer sp ? sp : null;
        ItemStack held = event.getItemStack();
        InteractionResult tool = instrument(clicker, player, held, host, event.getHand());
        if (tool != null) {
            event.setCanceled(true);
            event.setCancellationResult(tool);
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (player == null) return;
        HostData data = Hosts.data(host);
        if (host instanceof ShellEntity shell && player.isShiftKeyDown()) {
            shell.openCargo(player);
            return;
        }
        var form = FormActions.state(player).form();
        boolean jarLike = form == Form.JAR || form == Form.LINKED && FormActions.state(player).carryingHead();
        if (host instanceof MechaEntity mecha && !jarLike) {
            // the owner opens a parked mecha's Cargo Hold by sneak-using it
            if (player.isShiftKeyDown() && !mecha.isVehicle()) {
                mecha.openCargo(player);
                return;
            }
            // anyone can ride in a Passenger Pod or the Wheelhouse's own seats
            if (mecha.isVehicle() && (mecha.frame().seats() > 0 || mecha.has(MechaModule.PASSENGER_POD))) {
                if (!player.startRiding(mecha)) Refusals.deny(player, Refuser.at(mecha), "mecha.seats_full");
            } else {
                player.displayClientMessage(Component.translatable("message.headinajar.mecha_status", mecha.battery() / 1000,
                        Math.round(mecha.getHealth()), Math.round(mecha.getMaxHealth())), true);
            }
            return;
        }
        if (jarLike) {
            if (host instanceof MechaEntity mecha && !mecha.isOwnedBy(player)) {
                Refusals.deny(player, Refuser.at(mecha), "mecha.not_yours");
            } else if (!FormActions.mountHost(player, host) && (form == Form.JAR || host.isVehicle())) {
                // a linked player whose climb failed after the swap has been told so by mountHost
                player.displayClientMessage(Component.translatable("message.headinajar.host_busy"), true);
            }
        } else if (form == Form.BODY || form == Form.LINKED) {
            player.displayClientMessage(Component.translatable("message.headinajar.host_status",
                    Math.round(data.freshness), Math.round(data.effectiveSync())), true);
        }
    }

    /**
     * The held item's own use on this body, or null when the item has nothing to do here (the mount and status path
     * follows). Decided the same way on both sides; only the server acts. {@code player} is null on the client.
     */
    private static InteractionResult instrument(Player clicker, ServerPlayer player, ItemStack held, Mob host, InteractionHand hand) {
        HostData data = Hosts.data(host);
        // whoever holds the lead can take it off again, whatever they hold
        if (host.isLeashed() && host.getLeashHolder() == clicker) {
            if (player != null) host.dropLeash(true, !clicker.hasInfiniteMaterials());
            return InteractionResult.SUCCESS;
        }
        if (held.isEmpty()) return null;
        // as always: Embalming Fluid and Neural Lace act whoever holds them
        if (held.is(ModRegistry.EMBALMING_FLUID.get())) {
            if (data.shell) return refuse(player, host, "host.embalm_shell", held.getHoverName());
            // a laced body never decays: the fluid would be poured away for nothing
            if (data.laced) return refuse(player, host, "host.embalm_laced", held.getHoverName());
            if (player != null) {
                data.freshness = 100;
                data.embalmedUntil = host.level().getGameTime() + 24000;
                held.consume(1, player);
                FormActions.give(player, new ItemStack(Items.GLASS_BOTTLE));
                host.playSound(SoundEvents.BOTTLE_EMPTY, 1, 0.8f);
                player.displayClientMessage(Component.translatable("message.headinajar.embalmed"), true);
                Network.syncHost(host);
                com.coolfreeze23.headinajar.body.BodyServer.embalmed(host);
            }
            return InteractionResult.SUCCESS;
        }
        if (held.is(ModRegistry.NEURAL_LACE.get())) {
            if (data.shell) return refuse(player, host, "host.lace_shell", held.getHoverName());
            if (data.laced) return refuse(player, host, "host.lace_again", held.getHoverName());
            if (player != null) {
                if (data.sync < 50) {
                    player.displayClientMessage(Component.translatable("message.headinajar.lace_needs_sync"), true);
                } else {
                    data.laced = true;
                    data.freshness = 100;
                    held.consume(1, player);
                    host.playSound(com.coolfreeze23.headinajar.fx.HouseSounds.LACE.get(), 1, 1.4f);
                    player.displayClientMessage(Component.translatable("message.headinajar.laced"), true);
                    Network.syncHost(host);
                }
            }
            return InteractionResult.SUCCESS;
        }
        // a bare jar has no hands to use anything else with: it climbs on
        if (FormEvents.lockedOut(clicker)) return null;
        if (held.getItem() instanceof GeneticsItems.SyringeItem syringe) {
            return player == null ? InteractionResult.SUCCESS : syringe.draw(player, held, host, hand);
        }
        if (held.getItem() instanceof GeneticsItems.SerumItem serum && com.coolfreeze23.headinajar.genetics.Serum.of(held).isPresent()) {
            return player == null ? InteractionResult.SUCCESS : serum.inject(player, held, host);
        }
        if (held.is(ModRegistry.MECHA_PLATING.get()) && host instanceof MechaEntity mecha) {
            if (player == null) return InteractionResult.SUCCESS;
            if (!mecha.isOwnedBy(player)) return refuse(player, host, "mecha.not_yours");
            if (mecha.getHealth() >= mecha.getMaxHealth()) return refuse(player, host, "mecha.plating_full");
            // a plate patches 20 HP, as at the Gantry
            held.consume(1, player);
            mecha.heal(20);
            host.playSound(com.coolfreeze23.headinajar.fx.HouseSounds.GANTRY_PATCH.get(), 1, com.coolfreeze23.headinajar.fx.HouseSounds.pitch(host.getRandom()));
            player.displayClientMessage(Component.translatable("message.headinajar.mecha_status", mecha.battery() / 1000,
                    Math.round(mecha.getHealth()), Math.round(mecha.getMaxHealth())), true);
            return InteractionResult.SUCCESS;
        }
        if ((held.is(ModRegistry.PLASMA_PELLET.get()) || held.is(ModRegistry.BIO_GEL.get())) && host instanceof MechaEntity mecha) {
            Component hold = Component.translatable(ModRegistry.mechaModule(MechaModule.CARGO_HOLD).getDescriptionId());
            if (!mecha.has(MechaModule.CARGO_HOLD)) return refuse(player, host, "mecha.no_cargo", hold, Component.translatable("block.headinajar.assembly_gantry"));
            return refuse(player, host, "mecha.feed_hold", hold);
        }
        var state = FormActions.state(clicker);
        boolean jarLike = state.form() == Form.JAR || state.form() == Form.LINKED && state.carryingHead();
        // carrying your own head with a Suture Kit: the kit's own hold-to-sew, not a climb onto the host
        if (held.is(ModRegistry.SUTURE_KIT.get()) && state.form() == Form.LINKED && state.carryingHead()) return InteractionResult.PASS;
        // a jar climbing on keeps whatever it happens to hold; a person's vanilla items do what they would
        if (jarLike) return null;
        boolean machine = host instanceof MechaEntity || host instanceof ShellEntity;
        if (held.is(Items.LEAD)) {
            if (machine) return refuse(player, host, "host.lead_machine", held.getHoverName());
            if (!host.canHaveALeashAttachedToIt()) return refuse(player, host, "host.no_leash", held.getHoverName());
            if (player != null) {
                host.setLeashedTo(player, true);
                held.consume(1, player);
            }
            return InteractionResult.SUCCESS;
        }
        if (held.is(Items.NAME_TAG)) {
            if (host instanceof MechaEntity mecha && player != null && !mecha.isOwnedBy(player)) return refuse(player, host, "mecha.not_yours");
            InteractionResult named = held.interactLivingEntity(clicker, host, hand);
            return named.consumesAction() ? named : null;
        }
        if (held.is(Items.SADDLE)) return refuse(player, host, "host.saddle", held.getHoverName());
        if (!machine && (held.has(DataComponents.FOOD) || host instanceof Animal animal && animal.isFood(held))) {
            return refuse(player, host, "host.no_mouth", Component.translatable(ModRegistry.EMBALMING_FLUID.get().getDescriptionId()));
        }
        return null;
    }

    /**
     * Refuses at the body (server); the client just stops the click there. The click is used up, so the item's own
     * use in the air (eating the food, say) doesn't follow it.
     */
    private static InteractionResult refuse(ServerPlayer player, Mob host, String reason, Object... args) {
        if (player != null) Refusals.deny(player, Refuser.at(host), reason, args);
        return InteractionResult.CONSUME;
    }
}
