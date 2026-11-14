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
public final class EmfCompat {
    /** Set on the first error from inside Entity Model Features' render path: the hook then stands aside. */
    private static boolean broken;

    private EmfCompat() {
    }

    private static void failed(Throwable e) {
        if (!broken) HeadInAJar.LOGGER.warn("Entity Model Features hook disabled: {}", e.toString());
        broken = true;
    }

    /** Head parts this hook hid (so only those are shown again; a pack's own hidden parts stay as the pack set them). */
    private static final java.util.Set<net.minecraft.client.model.geom.ModelPart> HIDDEN =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    /**
     * The vanilla head parts, and the extra head parts packs add as their own parts (Fresh Animations' cow, pig, sheep
     * and others keep their real head as "head2" under the body, leaving "head" an empty attachment).
     */
    private static final java.util.Set<String> HEADS = java.util.Set.of("head", "head2", "head3", "headwear", "headwear2", "headpiece", "hat");

    /** For the dev harness: how many head parts the last call hid. */
    public static String lastHost = "";

    private static void hostHead(net.minecraft.client.model.geom.ModelPart root, boolean hide) {
        root.getAllParts().forEach(part -> {
            // a pack's own parts are named after the model file with an "EMF_" in front
            if (part instanceof traben.entity_model_features.models.parts.EMFModelPartCustom custom
                    && HEADS.contains(custom.id.startsWith("EMF_") ? custom.id.substring(4) : custom.id)) show(part, !hide);
            for (String name : HEADS) {
                if (part.hasChild(name)) show(part.getChild(name), !hide);
            }
        });
    }

    private static void show(net.minecraft.client.model.geom.ModelPart head, boolean show) {
        lastHost = "hidden " + HIDDEN.size();
        if (!show) {
            if (head.visible) HIDDEN.add(head);
            head.visible = false;
        } else if (HIDDEN.remove(head)) {
            head.visible = true;
        }
    }

    static void register() {
        try {
            EMFAnimationApi.registerAnimationHook(new EMFAnimationApi.EMFAnimationHook() {
                @Override
                public boolean onAnimationStart(AnimationContext context, boolean isCancelledByHook) {
                    if (broken) return true;
                    try {
                        // a headless body is posed by this mod (it waits, fidgets, sits, lies down): keep the pack's
                        // player animations off it
                        if (context.activeState() != null && context.activeState().emfEntity() instanceof com.coolfreeze23.headinajar.entity.HeadlessBody) return false;
                        // the first-person hands holding the jar are placed by this mod: leave them unanimated
                        var state = context.activeState();
                        return !(state != null && state.isFirstPersonHand() && state.emfEntity() instanceof Player player && HeadCarry.inArms(player));
                    } catch (LinkageError | RuntimeException e) {
                        failed(e);
                        return true;
                    }
                }

                @Override
                public void onAnimationEnd(AnimationContext context, boolean wasCancelledByHook) {
                    if (broken) return;
                    try {
                        var state = context.activeState();
                        if (state != null && !state.isFirstPersonHand() && state.emfEntity() instanceof Player player) {
                            HeadCarry.reposeArms(player);
                        }
                        // a re-modelled creature draws its own parts, not the vanilla head this mod hides: a host's
                        // head (after the saw's pop) is hidden here instead, on the model as the pack built it
                        if (state != null && !state.isFirstPersonHand() && state.emfEntity() instanceof net.minecraft.world.entity.LivingEntity living
                                && context.animatingModelRoot() != null) {
                            hostHead(context.animatingModelRoot(), com.coolfreeze23.headinajar.client.body.HostFx.headHidden(living));
                        }
                    } catch (LinkageError | RuntimeException e) {
                        failed(e);
                    }
                }
            });
        } catch (Exception | LinkageError e) {
            HeadInAJar.LOGGER.warn("Could not hook Entity Model Features animations: {}", e.toString());
        }
    }
}
