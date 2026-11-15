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
            case Fx.STAFF_SHAKE -> StaffShake.start(fx);
            case Fx.GENE_ROLL_UP, Fx.GENE_PUFFER, Fx.GENE_SPARE_A, Fx.GENE_SPARE_B -> com.coolfreeze23.headinajar.client.genes.LivestockClient.fx(fx);
            case Fx.GENE_SPARE_C -> com.coolfreeze23.headinajar.client.genes.NightShiftClient.fx(fx);
            case Fx.GENE_PHASE, Fx.GENE_TONGUE, Fx.GENE_POUNCE, Fx.GENE_SPIT, Fx.GENE_FANGS, Fx.GENE_WEEP, Fx.GENE_SPARE_D ->
                    com.coolfreeze23.headinajar.client.genes.ActivesClient.fx(fx);
            case Fx.PRINTED -> com.coolfreeze23.headinajar.client.PrintedFx.fx(fx);
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
