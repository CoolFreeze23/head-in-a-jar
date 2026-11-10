package com.coolfreeze23.headinajar.block;

import net.minecraft.world.level.Level;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.model.GeoModel;

import java.util.Map;

/**
 * The chair's one animation controller. Every frame it reads the chair's synced state and picks the clip (never a
 * one-shot trigger, which a player arriving later would not get), and it plays that clip at the tick the shared clock
 * says, not from wherever the controller happened to start it: a player who walks up half way through, or loads the
 * chunk then, sees the machine exactly where everyone else does.
 * <p>
 * Runs on the client only (GeckoLib animates what it draws).
 */
public class ChairController extends AnimationController<SeparationChairBlockEntity> {
    public enum Clip {
        IDLE("idle", true), READY("ready", true), OPERATION("operation", false), BOTCH("botch", false),
        ABORT("abort", false), AFTERCARE("aftercare", false);

        public final RawAnimation raw;

        Clip(String name, boolean loop) {
            raw = loop ? RawAnimation.begin().thenLoop(name) : RawAnimation.begin().thenPlayAndHold(name);
        }
    }

    /** A clip and how far into it the clock is. */
    public record Choice(Clip clip, double tick) {
    }

    private Clip clip;
    private double seek;
    /** Beyond this distance an idle chair stops animating; its idle pose is within a degree of rest. */
    private static final double IDLE_QUIET_DISTANCE = 24;

    public ChairController(SeparationChairBlockEntity chair) {
        super(chair, "main", 0, ChairController::predicate);
        setSoundKeyframeHandler(event -> com.coolfreeze23.headinajar.client.ChairKeyframes.sound(event));
        setParticleKeyframeHandler(event -> com.coolfreeze23.headinajar.client.ChairKeyframes.particle(event));
        setCustomInstructionKeyframeHandler(event -> com.coolfreeze23.headinajar.client.ChairKeyframes.instruction(event));
    }

    /**
     * Which clip the chair is in at this moment, and the tick into it, from its synced state alone. {@code now} is the
     * client's game time plus partial tick; {@code seated} and {@code loaded} say whether a patient sits in it and a jar
     * stands ready.
     */
    public static Choice choose(SeparationChairBlockEntity chair, double now, boolean seated) {
        if (chair.opStart() >= 0) {
            double t = Math.max(0, Math.min(ChairTimeline.LENGTH, now - chair.opStart()));
            // decided at the point of no return; the botch clip is the operation's twin until well after it
            return new Choice(chair.botched() && t >= ChairTimeline.CUT_COMMIT ? Clip.BOTCH : Clip.OPERATION, t);
        }
        if (chair.abortAt() >= 0 && now - chair.abortAt() < ChairTimeline.ABORT_BLEND + ChairTimeline.ABORT_LENGTH) {
            return new Choice(Clip.ABORT, Math.max(0, now - chair.abortAt() - ChairTimeline.ABORT_BLEND));
        }
        if (chair.finishAt() >= 0 && now - chair.finishAt() < ChairTimeline.AFTERCARE_LENGTH) {
            return new Choice(Clip.AFTERCARE, Math.max(0, now - chair.finishAt()));
        }
        if (seated && chair.jarKind() != SeparationChairBlockEntity.JAR_NONE) {
            return new Choice(Clip.READY, now % ChairTimeline.READY_LENGTH);
        }
        return new Choice(Clip.IDLE, now % ChairTimeline.IDLE_LENGTH);
    }

    private static PlayState predicate(AnimationState<SeparationChairBlockEntity> state) {
        ChairController controller = (ChairController) state.getController();
        SeparationChairBlockEntity chair = state.getAnimatable();
        Level level = chair.getLevel();
        if (level == null) return PlayState.STOP;
        double now = com.coolfreeze23.headinajar.client.ChairClock.now(level);
        Choice choice = choose(chair, now, com.coolfreeze23.headinajar.client.ChairClock.seated(chair));
        if (choice.clip() == Clip.IDLE && controller.clip == Clip.IDLE
                && com.coolfreeze23.headinajar.client.ChairClock.cameraDistance(chair) > IDLE_QUIET_DISTANCE) {
            return PlayState.STOP;
        }
        controller.seek = choice.tick();
        if (choice.clip() != controller.clip) {
            Clip from = controller.clip;
            controller.clip = choice.clip();
            // a fresh abort blends from wherever the machine is; the idle and ready loops ease into each other; every
            // other start (and any start seen late) snaps to where the clock is
            if (choice.clip() == Clip.ABORT && now - chair.abortAt() < 2) controller.transitionLength = ChairTimeline.ABORT_BLEND;
            else if ((choice.clip() == Clip.IDLE || choice.clip() == Clip.READY) && (from == Clip.IDLE || from == Clip.READY)) controller.transitionLength = 5;
            else controller.transitionLength = 0;
            controller.forceAnimationReset();
            controller.setAnimation(choice.clip().raw);
        } else if (controller.getAnimationState() == State.RUNNING) {
            // the blend is over: events must not be shifted by it
            controller.transitionLength = 0;
        }
        return PlayState.CONTINUE;
    }

    public Clip clip() {
        return clip;
    }

    /** While a clip runs, its tick is the clock's, never the controller's own count from when it started. */
    @Override
    protected double adjustTick(double tick) {
        if (clip != null && (animationState == State.RUNNING || animationState == State.PAUSED)) {
            shouldResetTick = false;
            tickOffset = tick - seek;
            return seek;
        }
        return super.adjustTick(tick);
    }

    /**
     * A clip that starts with no blend spends its first processed frame posed at its tick 0 (GeckoLib's transition
     * step). Process that frame a second time so the first frame drawn is already at the clock's tick: no flash of the
     * rest pose when a late arrival first sees the chair, or when the operation turns into the botch.
     */
    @Override
    public void process(GeoModel<SeparationChairBlockEntity> model, AnimationState<SeparationChairBlockEntity> state, Map<String, GeoBone> bones,
                        Map<String, BoneSnapshot> snapshots, double seekTime, boolean crashWhenCantFindBone) {
        super.process(model, state, bones, snapshots, seekTime, crashWhenCantFindBone);
        if (animationState == State.TRANSITIONING && transitionLength == 0 && currentAnimation != null) {
            super.process(model, state, bones, snapshots, seekTime, crashWhenCantFindBone);
        }
    }

    /**
     * The jar tray's own controller: out while a jar is loaded, an operation runs, or the finished head jar still stands
     * on it; in otherwise. A chair first seen with its tray out shows it out at once, not sliding out on arrival.
     */
    public static class Tray extends AnimationController<SeparationChairBlockEntity> {
        static final RawAnimation OUT = RawAnimation.begin().thenPlayAndHold("tray_out");
        static final RawAnimation IN = RawAnimation.begin().thenPlayAndHold("tray_in");
        private Boolean out;
        private double changedAt;
        private double seek;

        public Tray(SeparationChairBlockEntity chair) {
            super(chair, "tray", 0, Tray::predicate);
            setSoundKeyframeHandler(event -> com.coolfreeze23.headinajar.client.ChairKeyframes.sound(event));
        }

        private static PlayState predicate(AnimationState<SeparationChairBlockEntity> state) {
            Tray tray = (Tray) state.getController();
            SeparationChairBlockEntity chair = state.getAnimatable();
            Level level = chair.getLevel();
            if (level == null) return PlayState.STOP;
            double now = com.coolfreeze23.headinajar.client.ChairClock.now(level);
            boolean out = com.coolfreeze23.headinajar.client.ChairClock.trayOut(chair);
            if (tray.out == null || out != tray.out) {
                // first sight: already there; a change seen happening: play it from its start
                tray.changedAt = tray.out == null ? now - ChairTimeline.TRAY_LENGTH : now;
                tray.out = out;
                tray.forceAnimationReset();
                tray.setAnimation(out ? OUT : IN);
            }
            tray.seek = Math.min(ChairTimeline.TRAY_LENGTH, now - tray.changedAt);
            return PlayState.CONTINUE;
        }

        @Override
        protected double adjustTick(double tick) {
            if (out != null && (animationState == State.RUNNING || animationState == State.PAUSED)) {
                shouldResetTick = false;
                tickOffset = tick - seek;
                return seek;
            }
            return super.adjustTick(tick);
        }

        @Override
        public void process(GeoModel<SeparationChairBlockEntity> model, AnimationState<SeparationChairBlockEntity> state, Map<String, GeoBone> bones,
                            Map<String, BoneSnapshot> snapshots, double seekTime, boolean crashWhenCantFindBone) {
            super.process(model, state, bones, snapshots, seekTime, crashWhenCantFindBone);
            if (animationState == State.TRANSITIONING && currentAnimation != null) {
                super.process(model, state, bones, snapshots, seekTime, crashWhenCantFindBone);
            }
        }
    }
}
