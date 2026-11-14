package com.coolfreeze23.headinajar.mixin;

import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.mecha.MechaRig;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A pilot's first-person eye on a frame that says where it is ({@link MechaRig#firstPersonEye}): at the eyes of the head in
 * the jar rather than a standing player's eye height above the jar's base. Only the view moves; the pilot's aim and reach
 * are unchanged. Frames that say nothing keep the player's own eye height.
 */
@Mixin(Camera.class)
public abstract class PilotEyeMixin {
    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    public abstract net.minecraft.world.phys.Vec3 getPosition();

    @Inject(method = "setup", at = @At("TAIL"))
    private void headinajar$pilotEye(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
        if (detached || !(entity instanceof Player player)) return;
        // the Lunk: the eyes of the head in the jar on its lid (the jar's base 30.4 px up the cabinet)
        if (player.getVehicle() instanceof com.coolfreeze23.headinajar.entity.ShellEntity s && s.getFirstPassenger() == player
                && s.kind() == com.coolfreeze23.headinajar.entity.ShellEntity.Kind.LUNK) {
            var at = getPosition();
            setPosition(at.x, net.minecraft.util.Mth.lerp(partialTick, s.yo, s.getY()) + com.coolfreeze23.headinajar.entity.LunkAnimation.JAR_BASE
                    + MechaRig.JAR_EYES, at.z);
            return;
        }
        if (!(player.getVehicle() instanceof MechaEntity m) || m.getFirstPassenger() != player) return;
        MechaRig rig = MechaRig.of(m.frame());
        if (rig == null || Double.isNaN(rig.firstPersonEye)) return;
        var at = getPosition();
        // the eye rides the frame as it is drawn this frame: its interpolated height and the seat's travel at this very
        // moment (a passenger's position is a tick old and would lag a fast rise into the deck, as standing up from Siege)
        double seat = com.coolfreeze23.headinajar.mecha.Siege.seatPx(m, m.level().getGameTime() + partialTick) / 16;
        double neck = com.coolfreeze23.headinajar.host.Hosts.neck(m).y;
        // where the pilot's feet hang under the seat (a standing player's riding offset)
        double attach = -player.getVehicleAttachmentPoint(m).y;
        double y = net.minecraft.util.Mth.lerp(partialTick, m.yo, m.getY()) + neck - seat + attach + rig.firstPersonEye;
        setPosition(at.x, y, at.z);
    }
}
