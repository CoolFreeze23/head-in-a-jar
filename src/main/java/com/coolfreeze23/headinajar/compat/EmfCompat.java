package com.coolfreeze23.headinajar.compat;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.client.HeadCarry;
import net.minecraft.world.entity.player.Player;
import traben.entity_model_features.EMFAnimationApi;

/**
 * Entity Model Features (and resource packs built on it, like Fresh Animations' player extension) animates the player
 * model after the vanilla pose is set, which would swing the arms away from the jar. This puts the carrying pose back
 * once those animations have run, so everything else they animate stays theirs. The first-person hands holding the jar
 * are left out of those animations altogether.
 */
final class EmfCompat {
    private EmfCompat() {
    }

    static void register() {
        try {
            EMFAnimationApi.registerAnimationHook(new EMFAnimationApi.EMFAnimationHook() {
                @Override
                public boolean onAnimationStart(AnimationContext context, boolean isCancelledByHook) {
                    // the first-person hands holding the jar are placed by this mod: leave them unanimated
                    var state = context.activeState();
                    return !(state != null && state.isFirstPersonHand() && state.emfEntity() instanceof Player player && HeadCarry.inArms(player));
                }

                @Override
                public void onAnimationEnd(AnimationContext context, boolean wasCancelledByHook) {
                    var state = context.activeState();
                    if (state != null && !state.isFirstPersonHand() && state.emfEntity() instanceof Player player) {
                        HeadCarry.reposeArms(player);
                    }
                }
            });
        } catch (Exception | LinkageError e) {
            HeadInAJar.LOGGER.warn("Could not hook Entity Model Features animations: {}", e.toString());
        }
    }
}
