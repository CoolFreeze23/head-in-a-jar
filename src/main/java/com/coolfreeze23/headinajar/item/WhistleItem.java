package com.coolfreeze23.headinajar.item;

import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.module.ModuleType;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.work.Detail;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The Supervisor's Whistle: takes up to six staff off site as a detail that follows you and fights what you point at.
 * Use on your staff: on or off the detail. Use in the air: Follow (one blast) or Hold (two short). Sneak + Use: the
 * detail stands down. Attack: the fighters go for what you look at (up to 32 blocks). Blown to the lips like a goat horn.
 * <p>
 * A jar has no lips: it whistles only through a Speaker Grille ("The grille whistles for you."), from its hand, as a
 * jar, a host's rider or a pilot; without one, "A jar can't whistle. It has tried."
 */
public class WhistleItem extends Item {
    /** The blow (the horn pose) and the wait before the next. */
    public static final int BLOW_TICKS = 6;
    /** A jar's refusal to whistle (the client asks for it when it cancels the jar's click itself). */
    public static final String JAR = "whistle.jar";

    static {
        Refusals.allowFromClient(JAR, Refusals.Cue.PLAIN);
    }

    public WhistleItem(Properties properties) {
        super(properties);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.TOOT_HORN;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return BLOW_TICKS;
    }

    /** In the air (or on a block): Follow or Hold; with Sneak, the detail stands down. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!mayBlow(player)) {
            if (player instanceof ServerPlayer sp) Refusals.deny(sp, JAR);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        player.getCooldowns().addCooldown(this, BLOW_TICKS);
        if (player instanceof ServerPlayer sp) Detail.blow(sp, player.isSecondaryUseActive());
        return InteractionResultHolder.consume(stack);
    }

    // --- the Speaker Grille (fix 28) ---------------------------------------------------------------------------------

    /** Whether this hand holds a Whistle. */
    public static boolean holding(Player player, InteractionHand hand) {
        return player.getItemInHand(hand).is(WorkRegistry.SUPERVISORS_WHISTLE.get());
    }

    public static boolean holding(Player player) {
        return holding(player, InteractionHand.MAIN_HAND) || holding(player, InteractionHand.OFF_HAND);
    }

    /** Whether this player can blow a whistle: anyone with a head on, or a jar with a Speaker Grille. */
    public static boolean mayBlow(Player player) {
        JarState state = player.getData(ModRegistry.JAR_STATE);
        return !state.form().isJarred() || state.has(ModuleType.SPEAKER_GRILLE);
    }

    /** A jar (in any jarred form) holding a Whistle with a Speaker Grille fitted: its keys blow the whistle. */
    public static boolean throughGrille(Player player) {
        JarState state = player.getData(ModRegistry.JAR_STATE);
        return state.form().isJarred() && state.has(ModuleType.SPEAKER_GRILLE) && holding(player);
    }

    /**
     * The jar's guards ask this before they cancel a jar's click: true when the click is the Whistle's. With a Speaker
     * Grille the click goes on to the Whistle (a block's own use is skipped: the grille only whistles); without one it
     * is cancelled here with "A jar can't whistle. It has tried." False: not the Whistle, the guard carries on.
     */
    public static boolean grille(Player player, ICancellableEvent event) {
        ItemStack held = event instanceof PlayerInteractEvent pie ? pie.getItemStack() : player.getMainHandItem();
        if (!held.is(WorkRegistry.SUPERVISORS_WHISTLE.get()) || !player.getData(ModRegistry.JAR_STATE).form().isJarred()) return false;
        if (mayBlow(player)) {
            if (event instanceof PlayerInteractEvent.RightClickBlock block) {
                block.setUseBlock(TriState.FALSE);
                block.setUseItem(TriState.TRUE);
            }
            return true;
        }
        event.setCanceled(true);
        if (player instanceof ServerPlayer sp) Refusals.deny(sp, JAR);
        return true;
    }
}
