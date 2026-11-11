package com.coolfreeze23.headinajar.form;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.item.CollarCamItem;
import com.coolfreeze23.headinajar.item.LifeSupportJarItem;
import com.coolfreeze23.headinajar.item.MechaFrameItem;
import com.coolfreeze23.headinajar.item.MechaModuleItem;
import com.coolfreeze23.headinajar.item.ModuleItem;
import com.coolfreeze23.headinajar.item.QuantumItems;
import com.coolfreeze23.headinajar.item.RelayGogglesItem;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The company's things that have no use of their own in the air, or on a colleague, say where they do go: a jar, the
 * goggles and the collar go in the Separation Chair, a frame or a mecha module in the Gantry, a jar module in the jar
 * menu, Embalming Fluid and Neural Lace on a host body, the saw on a weakened creature. A friend's jar takes saline and a
 * patch from someone it trusts, as a placed head does. Only items with no use of their own are answered here.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID)
public final class ItemAnswers {
    /** A click a plugged-in head made (the client stops it): the socket has the jar's attention. */
    public static final String SOCKET_HANDS = "socket.hands";

    static {
        Refusals.allowFromClient(SOCKET_HANDS, Refusals.Cue.PLAIN);
    }

    private ItemAnswers() {
    }

    private static Component name(String key) {
        return Component.translatable(key);
    }

    /** Used at nothing it can work on: what the item is for. Runs last; anything that used the click went first. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void air(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack held = event.getItemStack();
        Form form = FormActions.state(player).form();
        Component chair = name("block.headinajar.separation_chair");
        if (held.getItem() instanceof LifeSupportJarItem) {
            Refusals.deny(player, "item.air.jar", chair, name("item.headinajar.surgical_saw"));
        } else if (held.getItem() instanceof RelayGogglesItem) {
            Refusals.deny(player, "item.air.goggles", chair);
        } else if (held.getItem() instanceof CollarCamItem) {
            Refusals.deny(player, "item.air.collar", chair);
        } else if (held.getItem() instanceof MechaFrameItem || held.getItem() instanceof MechaModuleItem) {
            Refusals.deny(player, "item.air.mecha", name("block.headinajar.assembly_gantry"));
        } else if (held.getItem() instanceof ModuleItem) {
            if (form == Form.JAR) Refusals.deny(player, "item.air.module_jar", Component.keybind("key.headinajar.jar_menu"));
            else Refusals.deny(player, "item.air.module_body", chair);
        } else if (held.getItem() instanceof QuantumItems.ShardItem) {
            Refusals.deny(player, "item.air.shard", name("item.headinajar.relay_goggles"), name("item.headinajar.collar_cam"));
        } else if (held.is(ModRegistry.EMBALMING_FLUID.get()) || held.is(ModRegistry.NEURAL_LACE.get())) {
            Refusals.deny(player, held.is(ModRegistry.NEURAL_LACE.get()) ? "item.air.lace" : "item.air.embalm");
        } else if (held.is(ModRegistry.SURGICAL_SAW.get())) {
            Refusals.deny(player, "item.air.saw");
        } else if (held.is(ModRegistry.SURGICAL_WRENCH.get())) {
            Refusals.deny(player, "item.air.wrench");
        } else if (form == Form.BODY && (held.is(ModRegistry.SALINE_BOTTLE.get()) || held.is(ModRegistry.GLASS_PATCH.get()))) {
            Refusals.deny(player, "item.air.head_on", held.getHoverName());
        }
    }

    /**
     * The saw clicked on an armour stand: the stand takes the click for itself (its "at a point" interaction), so the
     * saw is never asked. Say what the saw is for.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void sawAtAStand(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof net.minecraft.world.entity.decoration.ArmorStand)) return;
        if (!event.getItemStack().is(ModRegistry.SURGICAL_SAW.get())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        Refusals.deny(player, "item.air.saw");
    }

    /**
     * Used on another player: a friend's jar takes saline and a patch from someone it trusts; the company's tools for
     * bodies and creatures say they are not for colleagues.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void colleague(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof ServerPlayer other) || !(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack held = event.getItemStack();
        boolean saline = held.is(ModRegistry.SALINE_BOTTLE.get()), patch = held.is(ModRegistry.GLASS_PATCH.get());
        if (saline || patch) {
            JarState state = FormActions.state(other);
            if (!state.form().isDecapitated()) return;
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.CONSUME);
            if (!HeadRegistry.get(player).trusts(other.getUUID(), player)) {
                Refusals.deny(player, "head.not_yours", other.getGameProfile().getName());
            } else if (saline && state.saline() < state.maxSaline()) {
                state.setSaline(state.saline() + 250);
                held.consume(1, player);
                FormActions.give(player, new ItemStack(Items.GLASS_BOTTLE));
                other.level().playSound(null, other.getX(), other.getY(), other.getZ(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 1, 1);
            } else if (patch && state.integrity() < state.maxIntegrity()) {
                state.setIntegrity(state.integrity() + 5);
                held.consume(1, player);
                other.level().playSound(null, other.getX(), other.getY(), other.getZ(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 1, 1.4f);
            } else {
                Refusals.deny(player, saline ? "head.full" : "head.intact");
            }
            return;
        }
        if (held.is(ModRegistry.SURGICAL_SAW.get()) || held.is(ModRegistry.EMBALMING_FLUID.get()) || held.is(ModRegistry.NEURAL_LACE.get())
                || held.is(ModRegistry.SURGICAL_WRENCH.get())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.CONSUME);
            Refusals.deny(player, "item.on_colleague", held.getHoverName());
        }
    }

    /** Whether a player is plugged into a socket (the client stops its clicks and asks for {@link #SOCKET_HANDS}). */
    public static boolean plugged(Player player) {
        return FormActions.state(player).form() == Form.SOCKET;
    }
}
