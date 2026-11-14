package com.coolfreeze23.headinajar.anim;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

import java.util.Map;

/**
 * A controller whose clip and tick come from synced state every frame, never from where GeckoLib happened to start it:
 * a player who arrives late, or loads the chunk half way through, sees the machine exactly where everyone else does.
 * The predicate calls {@link #show} with the clip and the tick into it (within the clip's length), or {@link #none}.
 */
public class SeekingController<T extends GeoAnimatable> extends AnimationController<T> {
    private String clip;
    private double seek;

    public SeekingController(T animatable, String name, AnimationStateHandler<T> handler) {
        super(animatable, name, 0, handler);
    }

    /** Shows {@code raw} (named {@code name}) at {@code tick}; a new clip blends in over {@code blend} ticks. */
    public PlayState show(String name, RawAnimation raw, double tick, int blend) {
        double before = seek;
        seek = Math.max(0, tick);
        // a loop's phase wrapped (or a clip was sought back): its keyframe effects fire again on the new pass
        if (name.equals(clip) && seek < before - 0.5) resetEffects();
        if (!name.equals(clip)) {
            clip = name;
            transitionLength = blend;
            forceAnimationReset();
            setAnimation(raw);
        } else if (animationState == State.RUNNING) {
            // the blend is over: events must not be shifted by it
            transitionLength = 0;
        }
        return PlayState.CONTINUE;
    }

    /** A loop's phase wrapped: the next tick reported is past the clip's end, so GeckoLib starts its next pass. */
    private boolean wrapped;

    private void resetEffects() {
        wrapped = currentAnimation != null && currentAnimation.loopType() == software.bernie.geckolib.animation.Animation.LoopType.LOOP;
    }

    /** Nothing for this controller to show: its bones go back to what the others give them. */
    public PlayState none() {
        clip = null;
        return PlayState.STOP;
    }

    /** The clip shown, or null. */
    public String shown() {
        return clip;
    }

    /** While a clip runs, its tick is the one {@link #show} was given, never the controller's own count. */
    @Override
    protected double adjustTick(double tick) {
        if (clip != null && (animationState == State.RUNNING || animationState == State.PAUSED)) {
            if (wrapped && animationState == State.RUNNING) {
                // GeckoLib sees the clip end, clears its record of fired keyframe effects, and asks again for the tick
                wrapped = false;
                return Double.MAX_VALUE / 4;
            }
            shouldResetTick = false;
            tickOffset = tick - seek;
            return seek;
        }
        return super.adjustTick(tick);
    }

    /**
     * A clip that starts with no blend spends its first processed frame posed at its tick 0 (GeckoLib's transition step).
     * Process that frame a second time so the first frame drawn is already at the right tick: no flash of the rest pose.
     */
    @Override
    public void process(GeoModel<T> model, AnimationState<T> state, Map<String, GeoBone> bones, Map<String, BoneSnapshot> snapshots,
                        double seekTime, boolean crashWhenCantFindBone) {
        super.process(model, state, bones, snapshots, seekTime, crashWhenCantFindBone);
        if (animationState == State.TRANSITIONING && transitionLength == 0 && currentAnimation != null) {
            super.process(model, state, bones, snapshots, seekTime, crashWhenCantFindBone);
        }
    }
}
