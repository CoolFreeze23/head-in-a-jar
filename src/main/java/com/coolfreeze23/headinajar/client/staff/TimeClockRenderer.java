package com.coolfreeze23.headinajar.client.staff;

import com.coolfreeze23.headinajar.block.TimeClockBlockEntity;
import com.coolfreeze23.headinajar.client.MachineRenderers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;

/**
 * The Time Clock's moving parts (GeckoLib, the kit machines' renderer): the hour and minute hands, the time card, the
 * lever and the bell with its plunger. Everything else (the stand, the rack and its slips, the cabinet, the awning, the
 * lamp) is the block model, drawn with the chunk; the geo file holds only the seven moving bones, so nothing is drawn
 * twice. The clips and their ticks are the block entity's controllers'. The hands always move, so the clock never
 * hands a rest pose to the chunk. While its site is on strike (0.7.2, synced) the "ON STRIKE" card hangs on the bell
 * and a red pulse beats over the lamp's lens (the lamp itself the steady red of a fault); hidden otherwise.
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
        model.getBone("strike_card").ifPresent(b -> b.setHidden(!clock.strikeShown()));
    }

    @Override
    public void postRender(PoseStack pose, TimeClockBlockEntity clock, BakedGeoModel model, MultiBufferSource buffers, @Nullable VertexConsumer buffer,
                           boolean isReRender, float partialTick, int light, int overlay, int colour) {
        super.postRender(pose, clock, model, buffers, buffer, isReRender, partialTick, light, overlay, colour);
        // on strike: the red pulse over the lens, in the model's space (drawn at any distance the clock is)
        if (!isReRender && buffers != null && clock.strikeShown()) {
            com.coolfreeze23.headinajar.client.StrikeClient.lens(pose, buffers, clock.getLevel(), partialTick);
        }
    }
}
