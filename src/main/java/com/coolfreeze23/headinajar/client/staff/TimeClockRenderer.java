package com.coolfreeze23.headinajar.client.staff;

import com.coolfreeze23.headinajar.block.TimeClockBlockEntity;
import com.coolfreeze23.headinajar.client.MachineRenderers;

/**
 * The Time Clock's moving parts (GeckoLib, the kit machines' renderer): the hour and minute hands, the time card, the
 * lever and the bell with its plunger. Everything else (the stand, the rack and its slips, the cabinet, the awning, the
 * lamp) is the block model, drawn with the chunk; the geo file holds only the seven moving bones, so nothing is drawn
 * twice. The clips and their ticks are the block entity's controllers'. The hands always move, so the clock never
 * hands a rest pose to the chunk. The "ON STRIKE" card on the bell stays hidden in this release.
 */
public class TimeClockRenderer extends MachineRenderers.Base<TimeClockBlockEntity> {
    public TimeClockRenderer() {
        // the bell stands 4 px over the block and the time card comes 3 px out of a flank: a block up and to each side
        super("time_clock", -16, 0, -16, 16, 32, 16);
    }

    @Override
    protected boolean awake(TimeClockBlockEntity clock, double now) {
        return true;
    }

    @Override
    protected void pose(TimeClockBlockEntity clock, MachineRenderers.Model<TimeClockBlockEntity> model, float partial) {
        model.getBone("strike_card").ifPresent(b -> b.setHidden(true));
    }
}
