package com.coolfreeze23.headinajar.compat;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.item.RelayGogglesItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Goggles worn in a Curios head slot by an older version. Goggles count only on the jar, so no head slot is offered
 * for them any more, and Curios hands back whatever sat in a slot it no longer has: on the player's next tick, into the
 * inventory, dropping what does not fit at the player's feet, where a bare jar cannot pick it up and it would despawn.
 * So the hand-back is run here, as the player logs in: what fits goes into the inventory as Curios puts it, what does
 * not goes into the Ender Chest, and only what fits in neither is dropped, and then it never despawns. The player is
 * told where their goggles went.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID)
public final class OldHeadSlot {
    private OldHeadSlot() {
    }

    /** The player whose items are being handed back right now (the hand-back runs on the server thread, at once). */
    private static ServerPlayer giving;
    /** Goggles put in the Ender Chest, and dropped, during the current hand-back. */
    private static final Map<Item, Integer> TO_CHEST = new LinkedHashMap<>(), TO_FLOOR = new LinkedHashMap<>();

    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && Compat.curios()) {
            giveBack(player, () -> CuriosCompat.giveBackInvalid(player));
        }
    }

    /**
     * Runs a hand-back that gives the player items as Curios does (into the inventory, the rest dropped at their feet)
     * with the safety above, and tells the player where any goggles went.
     */
    public static void giveBack(ServerPlayer player, Runnable handBack) {
        Map<Item, Integer> before = goggles(player.getInventory());
        TO_CHEST.clear();
        TO_FLOOR.clear();
        giving = player;
        try {
            handBack.run();
        } finally {
            giving = null;
        }
        goggles(player.getInventory()).forEach((item, count) -> {
            if (count > before.getOrDefault(item, 0)) tell(player, "inventory", item);
        });
        TO_CHEST.keySet().forEach(item -> tell(player, "ender_chest", item));
        TO_FLOOR.keySet().forEach(item -> tell(player, "floor", item));
        TO_CHEST.clear();
        TO_FLOOR.clear();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void dropped(EntityJoinLevelEvent event) {
        ServerPlayer player = giving;
        if (player == null || !(event.getEntity() instanceof ItemEntity drop) || event.getLevel() != player.level()) return;
        ItemStack stack = drop.getItem().copy();
        ItemStack rest = player.getEnderChestInventory().addItem(stack.copy());
        if (stack.getItem() instanceof RelayGogglesItem && rest.getCount() < stack.getCount()) TO_CHEST.merge(stack.getItem(), 1, Integer::sum);
        if (rest.isEmpty()) {
            event.setCanceled(true);
            return;
        }
        drop.setItem(rest);
        drop.setUnlimitedLifetime();
        if (rest.getItem() instanceof RelayGogglesItem) TO_FLOOR.merge(rest.getItem(), 1, Integer::sum);
    }

    private static Map<Item, Integer> goggles(Inventory inventory) {
        Map<Item, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s.getItem() instanceof RelayGogglesItem) counts.merge(s.getItem(), s.getCount(), Integer::sum);
        }
        return counts;
    }

    private static void tell(ServerPlayer player, String where, Item item) {
        player.sendSystemMessage(Component.translatable("message.headinajar.curios_head." + where, Component.translatable(item.getDescriptionId())));
    }
}
