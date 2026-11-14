package com.coolfreeze23.headinajar.item;

import com.coolfreeze23.headinajar.HeadInAJar;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * The employee handbook: every new player is handed one on their first day (once, remembered across deaths and
 * dimensions), and a lost copy is crafted from a book and a Surgical Steel Blend. With Patchouli installed it is the
 * Patchouli book; without it, a plain written book with the opening chapters, whose pages are translated by each
 * reader's own client. The copy is whatever the loaded recipe makes, so the gift and the recipe can't disagree, and no
 * Patchouli class is ever touched (the Patchouli book is only an item id and a data component).
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID)
public final class Handbook {
    /** Remembered in the part of a player's data that survives death. */
    public static final String GIVEN = "headinajar_handbook_given";
    public static final net.minecraft.resources.ResourceLocation PATCHOULI_RECIPE = HeadInAJar.id("handbook_copy"),
            WRITTEN_RECIPE = HeadInAJar.id("handbook_copy_written");

    private Handbook() {
    }

    public static boolean patchouli() {
        return ModList.get() != null && ModList.get().isLoaded("patchouli");
    }

    /** A fresh copy of the handbook, as the loaded replacement recipe makes it (empty if neither recipe loaded). */
    public static ItemStack copy(MinecraftServer server) {
        for (var id : patchouli() ? new net.minecraft.resources.ResourceLocation[]{PATCHOULI_RECIPE, WRITTEN_RECIPE}
                : new net.minecraft.resources.ResourceLocation[]{WRITTEN_RECIPE}) {
            var recipe = server.getRecipeManager().byKey(id);
            if (recipe.isPresent()) {
                ItemStack result = recipe.get().value().getResultItem(server.registryAccess());
                if (!result.isEmpty()) return result.copy();
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Set with the gift for a player who shows no sign of having worked here before (see {@link #veteran}): only they
     * are welcomed as a new employee. A player handed the book by an older version never has it.
     */
    public static final String NEW_EMPLOYEE = "headinajar_new_employee";

    /**
     * Hands the handbook to a player who has never had one. Returns whether it was given. A player who already carries
     * a copy is not handed a second one.
     */
    public static boolean giveOnce(ServerPlayer player) {
        CompoundTag kept = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (kept.getBoolean(GIVEN)) return false;
        ItemStack book = copy(player.server);
        if (book.isEmpty()) return false;
        boolean carried = carries(player);
        kept.putBoolean(GIVEN, true);
        if (!carried && !veteran(player)) kept.putBoolean(NEW_EMPLOYEE, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, kept);
        if (carried) return false;
        place(player, book);
        return true;
    }

    /** Whether a player is a new employee: handed the first-day book on a first day with no history at the company. */
    public static boolean newEmployee(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(NEW_EMPLOYEE);
    }

    /** Whether the player carries a handbook already (either edition). */
    public static boolean carries(ServerPlayer player) {
        ItemStack fresh = copy(player.server);
        for (ItemStack s : player.getInventory().items) {
            if (s.isEmpty()) continue;
            if (ItemStack.isSameItemSameComponents(s, fresh)) return true;
            if (s.is(net.minecraft.world.item.Items.WRITTEN_BOOK)) {
                var content = s.get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT);
                if (content != null && content.author().equals("Noggin & Sons")) return true;
            }
        }
        return false;
    }

    /**
     * Signs that a player has worked here before: their head is off (any form but a whole body), or they have earned
     * any of the company's advancements (the first is smelting Surgical Steel; separation, hosts and mecha follow).
     */
    public static boolean veteran(ServerPlayer player) {
        if (com.coolfreeze23.headinajar.form.FormActions.state(player).form() != com.coolfreeze23.headinajar.form.Form.BODY) return true;
        for (var adv : player.server.getAdvancements().getAllAdvancements()) {
            if (adv.id().getNamespace().equals(HeadInAJar.MOD_ID) && player.getAdvancements().getOrStartProgress(adv).isDone()) return true;
        }
        return false;
    }

    /**
     * Where a gift goes: the first free hotbar slot from the right, never the one in hand, then the first free slot of
     * the inventory from the right; only a full inventory drops it at the player's feet. So nothing held is pushed aside.
     * Returns the inventory slot used, or -1 when it was dropped.
     */
    public static int place(ServerPlayer player, ItemStack stack) {
        var inv = player.getInventory();
        for (int i = 8; i >= 0; i--) {
            if (i != inv.selected && inv.getItem(i).isEmpty()) {
                inv.setItem(i, stack);
                return i;
            }
        }
        for (int i = 35; i >= 9; i--) {
            if (inv.getItem(i).isEmpty()) {
                inv.setItem(i, stack);
                return i;
            }
        }
        player.drop(stack, false);
        return -1;
    }

    /**
     * The GameTest server's mock players are not new employees (a book in every test player's pockets would change what
     * the other tests count); a test that wants the first-day book switches this on around its own login.
     */
    public static boolean giveToTestPlayers;

    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof net.neoforged.neoforge.common.util.FakePlayer) return;
        if (player.server instanceof net.minecraft.gametest.framework.GameTestServer && !giveToTestPlayers) return;
        giveOnce(player);
    }
}
