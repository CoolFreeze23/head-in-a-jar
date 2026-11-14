package com.coolfreeze23.headinajar.machine;

import net.minecraft.world.level.block.entity.BlockEntity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

import java.util.HashMap;
import java.util.Map;

/**
 * The kit's seeking controller (the Separation Chair's, generalised). Every frame a {@link Chooser} reads the
 * machine's synced state and names the clip and the tick into it; the controller plays that clip at exactly that tick,
 * never from wherever it happened to start, so a late arrival sees what everyone sees. A choice of null rests the
 * part (the controller stops; GeckoLib returns the bone to its rest pose).
 * <p>
 * Client only (GeckoLib animates what it draws). Code-driven channels never go through a controller: they are written
 * absolutely in the model's {@code setCustomAnimations}.
 */
public class KitController<T extends BlockEntity & GeoAnimatable> extends AnimationController<T> {
    /** A clip and the tick into it (game ticks, 20 a second). */
    public record Choice(String clip, double tick) {
    }

    @FunctionalInterface
    public interface Chooser<T> {
        /** {@code now}: game time plus partial tick. Null: rest. */
        Choice choose(T machine, double now, KitController<?> controller);
    }

    private final Chooser<T> chooser;
    private final Map<String, RawAnimation> raws = new HashMap<>();
    private String clip;
    private double seek;
    /** A chooser's own memory (a toggle's last value, when it changed). */
    public Boolean lastBit;
    public double mark;
    /** A second memory for a chooser that keeps a toggle too: when a held pose was last let go (NaN: never held). */
    public double released = Double.NaN;
    /** A chooser's memory of a many-valued state (-1: not seen yet). */
    public int lastState = -1;
    /** Ticks of blend for the next change of clip only (0: none): a part let go mid-move eases to its new clip. */
    public int blendNext;

    /** {@code loops}: the clips that loop; every other clip plays once and holds its last frame. */
    public KitController(T machine, String name, Chooser<T> chooser, String... loops) {
        super(machine, name, 0, KitController::predicate);
        this.chooser = chooser;
        for (String loop : loops) raws.put(loop, RawAnimation.begin().thenLoop(loop));
    }

    private RawAnimation raw(String name) {
        return raws.computeIfAbsent(name, n -> RawAnimation.begin().thenPlayAndHold(n));
    }

    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity & GeoAnimatable> PlayState predicate(AnimationState<T> state) {
        KitController<T> c = (KitController<T>) state.getController();
        T machine = state.getAnimatable();
        if (machine.getLevel() == null) return PlayState.STOP;
        Choice choice = c.chooser.choose(machine, machine.getTick(machine), c);
        if (choice == null) {
            c.clip = null;
            return PlayState.STOP;
        }
        c.seek = Math.max(0, choice.tick());
        if (!choice.clip().equals(c.clip)) {
            c.clip = choice.clip();
            c.transitionLength = c.blendNext;
            c.blendNext = 0;
            c.forceAnimationReset();
            c.setAnimation(c.raw(c.clip));
        } else if (c.getAnimationState() == State.RUNNING) {
            c.transitionLength = 0;
        }
        return PlayState.CONTINUE;
    }

    public String clip() {
        return clip;
    }

    /** While a clip runs, its tick is the chooser's, never the controller's own count. */
    @Override
    protected double adjustTick(double tick) {
        if (clip != null && (animationState == State.RUNNING || animationState == State.PAUSED)) {
            shouldResetTick = false;
            tickOffset = tick - seek;
            return seek;
        }
        return super.adjustTick(tick);
    }

    /** A clip started with no blend is processed twice on its first frame, so the first frame drawn is at the clock. */
    @Override
    public void process(GeoModel<T> model, AnimationState<T> state, Map<String, GeoBone> bones, Map<String, BoneSnapshot> snapshots,
                        double seekTime, boolean crashWhenCantFindBone) {
        super.process(model, state, bones, snapshots, seekTime, crashWhenCantFindBone);
        if (animationState == State.TRANSITIONING && currentAnimation != null) {
            super.process(model, state, bones, snapshots, seekTime, crashWhenCantFindBone);
        }
    }

    /**
     * A part that follows a yes/no state (a flag up while there is output, a drawer out while the screen is open):
     * {@code on} plays when it turns true, {@code off} when it turns false, each from its start; on first sight it is
     * already where the state says (a flag already up is drawn up).
     */
    public static Choice toggle(KitController<?> c, boolean bit, double now, String on, int onLength, String off, int offLength) {
        if (c.lastBit == null) {
            c.lastBit = bit;
            c.mark = now - Math.max(onLength, offLength);
        } else if (c.lastBit != bit) {
            c.lastBit = bit;
            c.mark = now;
        }
        double t = now - c.mark;
        if (bit) return new Choice(on, Math.min(onLength, t));
        // shut and finished: at rest, nothing to play
        return t >= offLength ? null : new Choice(off, t);
    }

    /** Whether a toggle part is moving or off its rest pose (for the idle rule). */
    public static boolean toggleAwake(KitController<?> c, double now, int length) {
        return c != null && c.lastBit != null && (c.lastBit || now - c.mark < length);
    }
}
