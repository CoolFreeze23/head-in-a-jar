package com.coolfreeze23.headinajar.item;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.form.FormActions;
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

    /** Hands the handbook to a player who has never had one. Returns whether it was given. */
    public static boolean giveOnce(ServerPlayer player) {
        CompoundTag kept = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (kept.getBoolean(GIVEN)) return false;
        ItemStack book = copy(player.server);
        if (book.isEmpty()) return false;
        kept.putBoolean(GIVEN, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, kept);
        FormActions.give(player, book);
        return true;
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
