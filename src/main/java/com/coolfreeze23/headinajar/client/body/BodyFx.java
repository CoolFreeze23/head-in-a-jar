package com.coolfreeze23.headinajar.client.body;

import com.coolfreeze23.headinajar.client.fx.FxManager;
import com.coolfreeze23.headinajar.fx.Fx;

/** The client side of the head-and-body memos: hands each to the part of the wave that draws it. */
public final class BodyFx {
    public static void handle(Fx fx) {
        switch (fx.kind()) {
            case Fx.HOST_CUT -> HostFx.cut(fx);
            case Fx.HOST_SEAT -> HostFx.seat(fx);
            case Fx.HOST_UNSEAT -> HostFx.unseat(fx);
            case Fx.HOST_EMBALMED -> HostFx.embalmed(fx);
            case Fx.TOOL -> ToolFx.used(fx);
            case Fx.CORPSE -> BodyPoses.corpse(fx.entityB());
            default -> {
            }
        }
    }

    /** Runs {@code run} on the client tick {@code ticks} from now. */
    static void later(int ticks, Runnable run) {
        FxManager.later(ticks, run);
    }

    private BodyFx() {
    }
}
