package com.coolfreeze23.headinajar.host;

import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Using a host: embalm it, lace it, open a shell's cargo, or (as a jar) climb on. */
public final class HostInteractions {
    private HostInteractions() {
    }

    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Mob host)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack held = event.getItemStack();
        HostData data = Hosts.data(host);
        long time = host.level().getGameTime();
        if (held.is(ModRegistry.EMBALMING_FLUID.get()) && !data.shell) {
            data.freshness = 100;
            data.embalmedUntil = time + 24000;
            held.consume(1, player);
            FormActions.give(player, new ItemStack(Items.GLASS_BOTTLE));
            host.playSound(SoundEvents.BOTTLE_EMPTY, 1, 0.8f);
            player.displayClientMessage(Component.translatable("message.headinajar.embalmed"), true);
            Network.syncHost(host);
            return;
        }
        if (held.is(ModRegistry.NEURAL_LACE.get()) && !data.shell) {
            if (data.sync < 50) {
                player.displayClientMessage(Component.translatable("message.headinajar.lace_needs_sync"), true);
                return;
            }
            data.laced = true;
            data.freshness = 100;
            held.consume(1, player);
            host.playSound(SoundEvents.SLIME_BLOCK_PLACE, 1, 1.4f);
            player.displayClientMessage(Component.translatable("message.headinajar.laced"), true);
            Network.syncHost(host);
            return;
        }
        if (host instanceof com.coolfreeze23.headinajar.entity.ShellEntity shell && player.isShiftKeyDown()) {
            shell.openCargo(player);
            return;
        }
        var form = FormActions.state(player).form();
        if (form == Form.JAR || form == Form.LINKED && FormActions.state(player).carryingHead()) {
            if (!FormActions.mountHost(player, host)) player.displayClientMessage(Component.translatable("message.headinajar.host_busy"), true);
        } else if (form == Form.BODY || form == Form.LINKED) {
            player.displayClientMessage(Component.translatable("message.headinajar.host_status",
                    Math.round(data.freshness), Math.round(data.effectiveSync())), true);
        }
    }
}
