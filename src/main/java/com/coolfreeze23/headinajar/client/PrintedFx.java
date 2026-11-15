package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.fx.Fx;

/** A host printed from a template steps out of the Body Printer ({@link Fx#PRINTED}): only the wet-dog shake. */
public final class PrintedFx {
    private PrintedFx() {
    }

    public static void fx(Fx fx) {
        if (fx.kind() == Fx.PRINTED) com.coolfreeze23.headinajar.client.body.HostFx.printed(fx.entityB());
    }
}
