package com.coolfreeze23.headinajar.anim;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationProcessor;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

import java.util.Map;
import java.util.Objects;

/**
 * The second form of {@link SeekingController} (the Time Clock's): a GeckoLib controller that plays a clip at a tick taken from synced state every frame (a shared clock), never from
 * the moment GeckoLib happened to start it. A player who joins late, or loads the chunk half way through, sees the
 * same pose as everyone else, on the very first frame drawn.
 *
 * <p>Written for <b>GeckoLib 4.9.3</b> (package {@code software.bernie.geckolib}, Minecraft 1.21.1). Every override
 * below works around a specific condition in 4.9.3's {@code AnimationController}; the comments name them. GeckoLib 5.x
 * rewrote the controller (different state handling and API): do not use this class there unchanged.
 *
 * <h2>Use</h2>
 * The predicate returns {@link #show(String, RawAnimation, double, double)} with the clip and the tick into it, or
 * {@link #none()} for nothing. It runs once per animation frame, before GeckoLib uses the tick, so the pose drawn is
 * the tick returned this frame (no one-frame lag).
 * <pre>{@code
 * static final RawAnimation OPEN = RawAnimation.begin().thenPlayAndHold("open");
 * static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
 *
 * // in the block entity; openedAt is a synced game time (long), -1 when closed
 * public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
 *     controllers.add(new SeekingControllerV2<>(this, "main", (state, c) -> {
 *         Level level = getLevel();
 *         if (level == null) return c.none();
 *         double now = level.getGameTime() + state.getPartialTick();
 *         if (openedAt < 0) return c.show("idle", IDLE, now % IDLE_LENGTH, 0);
 *         double t = now - openedAt;            // ticks since the synced start: the same on every client
 *         int blend = t < 2 ? 4 : 0;            // ease in only when this client saw it start live
 *         return c.show("open", OPEN, t, blend);
 *     }));
 * }
 * }</pre>
 *
 * <h2>Rules</h2>
 * <ul>
 * <li>One stage per {@link RawAnimation} ({@code thenPlay}, {@code thenLoop} or {@code thenPlayAndHold}); the name
 *     passed to {@code show} identifies the clip. Loop ticks may be given unwrapped (they are wrapped by the clip
 *     length here); a hold clip given a tick past its end holds its last frame; a play-once clip past its end draws
 *     nothing (bones go back to rest), as GeckoLib does.</li>
 * <li>{@code blend > 0} lerps from the current pose toward the clip's <b>tick-0</b> pose over {@code blend} ticks, then
 *     jumps to the sought tick (GeckoLib's transition never looks at the seek). So blend only for a clip this client
 *     sees starting live (tick near 0); a late joiner, or any clip already under way, must use 0.</li>
 * <li>Keyframe events (sound, particle, custom instruction) whose time is before the seek point all fire on the first
 *     running frame: a late joiner's burst. Keyframe handlers should drop events more than ~2 ticks late:
 *     {@code if (e.getAnimationTick() - e.getKeyframeData().getStartTick() > 2) return;}</li>
 * <li>Keep the predicate free of side effects (a function of synced state and time). This class asks it once per
 *     processed frame (a clip's first frame is processed twice, see {@link #process}, and reuses that one answer),
 *     but do not count on how often it runs: an older variant of this controller asked it twice on first frames.</li>
 * <li>The controller's speed modifier does not apply to the sought tick (scale the tick yourself), and triggered
 *     animations ({@code triggerableAnim}) are not supported on this controller.</li>
 * <li>A clip shown again with the same name restarts (fresh events, fresh start) when: it was stopped (a play-once
 *     clip that ended, shown again before its end); or a non-loop clip is sought back by more than
 *     {@link #rewindThreshold(double)} ticks (default 2, so small client clock corrections do not replay sounds).
 *     A loop sought back by more than half its length counts as a new pass: its keyframe events fire again.</li>
 * </ul>
 */
public class SeekingControllerV2<T extends GeoAnimatable> extends AnimationController<T> {

    /** A predicate that receives this controller already typed, so it needs no cast. */
    @FunctionalInterface
    public interface SeekHandler<T extends GeoAnimatable> {
        PlayState handle(AnimationState<T> state, SeekingControllerV2<T> controller);
    }

    // What the predicate asked for this frame (filled by show/none while process() asks).
    private String askedName;
    private RawAnimation askedRaw;
    private double askedTick;
    private double askedBlend;

    /** The clip being shown, or null. */
    private String clip;
    /** The tick last handed to GeckoLib for the shown clip. */
    private double drawn;
    /** Decided in process() before GeckoLib runs: start the shown clip again from the asked tick. */
    private boolean restart;
    private double rewindTicks = 2;

    public SeekingControllerV2(T animatable, String name, AnimationStateHandler<T> handler) {
        super(animatable, name, 0, handler);
    }

    /** Same as the other constructor, with a predicate of the form {@code (state, controller) -> PlayState}. */
    @SuppressWarnings("unchecked")
    public SeekingControllerV2(T animatable, String name, SeekHandler<T> handler) {
        // AnimationProcessor.tickAnimation calls state.withController(controller) before controller.process(...),
        // so the state's controller is this one whenever the predicate runs
        this(animatable, name, (AnimationStateHandler<T>) state -> handler.handle(state, (SeekingControllerV2<T>) state.getController()));
    }

    /**
     * Shows {@code raw} (identified by {@code name}) at {@code tick} ticks into it. A clip not already shown starts,
     * blending in over {@code blend} ticks toward its tick-0 pose (use 0 unless the clip is seen starting live).
     * Call only from the predicate, and return its result.
     */
    public PlayState show(String name, RawAnimation raw, double tick, double blend) {
        askedName = Objects.requireNonNull(name, "name");
        askedRaw = Objects.requireNonNull(raw, "raw");
        askedTick = tick >= 0 ? tick : 0; // also maps NaN to 0
        askedBlend = blend > 0 ? blend : 0;
        return PlayState.CONTINUE;
    }

    /** {@link #show(String, RawAnimation, double, double)} with no blend. */
    public PlayState show(String name, RawAnimation raw, double tick) {
        return show(name, raw, tick, 0);
    }

    /** Nothing to show: the controller stops and its bones go back to what the other controllers give them. */
    public PlayState none() {
        askedName = null;
        askedRaw = null;
        return PlayState.STOP;
    }

    /** The name of the clip shown, or null. */
    public String shown() {
        return clip;
    }

    /** The tick last handed to GeckoLib for the shown clip (after wrapping a loop). */
    public double shownTick() {
        return drawn;
    }

    /** How far back (in ticks) a non-loop clip must be sought, under the same name, to start it again. Default 2. */
    public SeekingControllerV2<T> rewindThreshold(double ticks) {
        rewindTicks = Math.max(0, ticks);
        return this;
    }

    /**
     * Asks the predicate first, then lets GeckoLib process, and processes a clip's first frame twice.
     * <p>
     * Asking first: GeckoLib computes the frame's tick ({@code double adjustedTick = adjustTick(seekTime)}, the first
     * line of {@code process}) before it calls the predicate ({@code handleAnimationState}), and a RUNNING clip is drawn
     * at that tick. Reading the tick from a predicate called inside {@code handleAnimationState} would draw every frame
     * at the previous frame's tick. So the predicate runs here, and {@link #handleAnimationState} replays its answer.
     * <p>
     * Second pass: a clip started with no blend spends its first processed frame TRANSITIONING: GeckoLib polls it from
     * the queue only when {@code adjustedTick == 0} ({@code if (lastPollTime != seekTime && (adjustedTick == 0 ||
     * isJustStarting))}) and queues its tick-0 pose. Processing the same frame again moves it to RUNNING
     * ({@code animationState == TRANSITIONING && adjustedTick >= transitionLength}) at the sought tick, and
     * {@code createInitialQueues} discards the tick-0 points, so the first frame drawn is already at the seek.
     */
    @Override
    public void process(GeoModel<T> model, AnimationState<T> state, Map<String, GeoBone> bones,
                        Map<String, BoneSnapshot> snapshots, double seekTime, boolean crashWhenCantFindBone) {
        ask(state);
        restart = askedName != null && askedName.equals(clip) && askedRaw.equals(currentRawAnimation) && mustRestart();
        super.process(model, state, bones, snapshots, seekTime, crashWhenCantFindBone);
        if (animationState == State.TRANSITIONING && transitionLength == 0 && currentAnimation != null) {
            super.process(model, state, bones, snapshots, seekTime, crashWhenCantFindBone);
        }
    }

    private void ask(AnimationState<T> state) {
        askedName = null;
        askedRaw = null;
        // a predicate that returns STOP (or anything but CONTINUE), or never calls show, shows nothing
        if (getStateHandler().handle(state) != PlayState.CONTINUE) {
            askedName = null;
            askedRaw = null;
        }
    }

    /** Whether the clip shown, asked for again under the same name, must start again rather than be sought. */
    private boolean mustRestart() {
        AnimationProcessor.QueuedAnimation current = currentAnimation;
        if (animationState == State.STOPPED) {
            // a play-once clip that ended stays STOPPED: GeckoLib re-enters processCurrentAnimation each frame but,
            // with the same RawAnimation, setAnimation does nothing (its `!rawAnimation.equals(currentRawAnimation)`
            // check), so it would never play again. Start it again unless the tick is still past its end.
            return current == null || loops(current) || askedTick < current.animation().length();
        }
        if (animationState == State.TRANSITIONING || current == null) return false;
        // a hold or play-once clip sought backwards: GeckoLib keeps the keyframe events already fired in its
        // executedKeyFrames set until the clip ends, so they would not fire again on the replayed stretch; and a hold
        // clip cannot be wrapped (HOLD_ON_LAST_FRAME sets PAUSED, which skips the reset). Start it again instead.
        return !loops(current) && askedTick < drawn - rewindTicks;
    }

    /**
     * Replays the predicate's answer (asked in {@link #process}) where GeckoLib expects the predicate, and starts the
     * clip when it is new, changed, or must start again.
     * <p>
     * A start always calls {@code forceAnimationReset()} first: {@code setAnimation} ignores a RawAnimation equal to
     * the current one unless {@code needsAnimationReload} is set, so a clip shown, stopped and shown again would resume
     * at its held last frame instead of starting. A stop sets it too, for the same reason. A reload of GeckoLib's
     * animations ({@code AnimationProcessor.reloadAnimations} calls {@code forceAnimationReset}) restarts the clip at
     * the sought tick, so it picks up the reloaded keyframes.
     */
    @Override
    protected PlayState handleAnimationState(AnimationState<T> state) {
        if (askedName == null) {
            if (clip != null) {
                clip = null;
                forceAnimationReset();
            }
            return PlayState.STOP;
        }
        if (restart || needsAnimationReload || !askedName.equals(clip) || !askedRaw.equals(currentRawAnimation)) {
            restart = false;
            clip = askedName;
            drawn = askedTick;
            transitionLength = askedBlend;
            forceAnimationReset();
            setAnimation(askedRaw);
        } else if (animationState == State.RUNNING || animationState == State.PAUSED) {
            // the blend is over; processCurrentAnimation adds transitionLength to the tick it tests keyframe events
            // against (`adjustedTick += this.transitionLength`), which would fire them early
            transitionLength = 0;
        }
        return PlayState.CONTINUE;
    }

    /**
     * While the shown clip is RUNNING (or PAUSED on its last frame, for a hold clip), its tick is the one asked for
     * this frame, never the controller's own count.
     * <p>
     * Not while TRANSITIONING: GeckoLib polls the queued clip only on a TRANSITIONING frame whose adjusted tick is 0
     * ({@code adjustedTick == 0 || isJustStarting} in {@code process}). Seeking from that first frame (overriding the
     * {@code shouldResetTick} path to return the seek) leaves the clip never started; this falls back to GeckoLib's
     * own count there, and {@link #process} makes up for the frame.
     * <p>
     * A loop's wrap: GeckoLib clears its record of fired keyframe events only when the adjusted tick reaches the clip
     * length ({@code adjustedTick >= length} in {@code processCurrentAnimation}, then {@code resetEventKeyFrames()}).
     * When the sought tick drops by more than half the clip, this returns a tick past the end once: GeckoLib then sees
     * the loop end, sets {@code shouldResetTick}, asks this method again (now given the real tick) and clears the
     * record, so the new pass fires its events again, once each.
     */
    @Override
    protected double adjustTick(double tick) {
        AnimationProcessor.QueuedAnimation current = currentAnimation;
        if (clip != null && current != null && clip.equals(askedName)
                && (animationState == State.RUNNING || animationState == State.PAUSED)) {
            double t = askedTick;
            double length = current.animation().length();
            boolean loop = loops(current);
            if (loop && length > 0) t %= length;
            shouldResetTick = false;
            tickOffset = tick - t; // keeps GeckoLib's own count consistent should it fall back to it
            if (loop && t < drawn - length / 2) {
                drawn = t;
                return Double.MAX_VALUE / 4;
            }
            drawn = t;
            return t;
        }
        return super.adjustTick(tick);
    }

    /** Whether a queued clip loops, resolving DEFAULT (thenPlay) to the loop type in the animation file. */
    private static boolean loops(AnimationProcessor.QueuedAnimation queued) {
        Animation.LoopType type = queued.loopType() == Animation.LoopType.DEFAULT ? queued.animation().loopType() : queued.loopType();
        return type == Animation.LoopType.LOOP;
    }
}
