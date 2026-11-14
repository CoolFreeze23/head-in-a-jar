package com.coolfreeze23.headinajar.compat;

import com.github.exopandora.shouldersurfing.api.client.event.ComputeCameraCouplingEvent;
import com.github.exopandora.shouldersurfing.api.client.event.ComputeTargetCameraOffsetEvent;
import com.github.exopandora.shouldersurfing.api.event.IEventBus;
import com.github.exopandora.shouldersurfing.api.plugin.IShoulderSurfingPlugin;
import net.minecraft.world.phys.Vec3;

/**
 * Shoulder Surfing Reloaded, when it is installed (named in shouldersurfing_plugin.json; never loaded otherwise). In a
 * mecha's cockpit or on an animated shell its camera sits as far back as the mod's own chase camera puts it, and the
 * camera stays coupled to the pilot's look, so the arms and the Pogo Protocol aim at the crosshair instead of where the
 * body last walked.
 * <p>
 * Coupled, Shoulder Surfing gives the player the camera's own rotation: the pilot's eye (the jar) then looks parallel to
 * the crosshair's line, beside and below it by the shoulder offset, so a blow, a Graft's taste or a toss went to what lay
 * half a block off the crosshair, and missed a small creature under it. Every client tick, after Shoulder Surfing's own
 * tick and before the player's look is sent, the pilot's look is turned onto the crosshair's target by Shoulder
 * Surfing's camera line (the machine itself is looked through), as Shoulder Surfing does for an uncoupled player who
 * attacks.
 */
public class ShoulderSurfingPlugin implements IShoulderSurfingPlugin {
    /** After Shoulder Surfing's own handlers (they run in rising priority order, its last at 2000). */
    private static final int LAST = 5000;

    @Override
    public void register(IEventBus bus) {
        bus.register(LAST, (com.github.exopandora.shouldersurfing.api.client.event.handler.ComputeTargetCameraOffsetEventHandler) ShoulderSurfingPlugin::offset);
        // the camera pivot sits on the machine's top in vanilla's third person, at two thirds in this view (lifted here)
        com.coolfreeze23.headinajar.client.PilotClient.shoulderView = () -> com.github.exopandora.shouldersurfing.api.client.IShoulderSurfing.getInstance().isShoulderSurfing();
        bus.register(LAST, (com.github.exopandora.shouldersurfing.api.client.event.handler.ComputeCameraCouplingEventHandler) ShoulderSurfingPlugin::coupling);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,
                net.neoforged.neoforge.client.event.ClientTickEvent.Pre.class, e -> aim());
    }

    /** In the shoulder view, from a cockpit or a shell: the pilot looks where the crosshair is. */
    private static void aim() {
        var ss = com.github.exopandora.shouldersurfing.api.client.IShoulderSurfing.getInstance();
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || !ss.isShoulderSurfing() || ss.isFreeLooking()) return;
        double chase = com.coolfreeze23.headinajar.client.PilotClient.chaseNow();
        if (chase <= 0) return;
        // the crosshair's own line from the camera, through the machine the pilot sits on (Shoulder Surfing's picker would
        // stop at its hull, which stands between the camera and anything just ahead of it)
        var player = mc.player;
        var vehicle = player.getVehicle();
        var camera = mc.gameRenderer.getMainCamera();
        Vec3 from = camera.getPosition();
        var look = camera.getLookVector();
        double range = chase + 12;
        Vec3 to = from.add(look.x() * range, look.y() * range, look.z() * range);
        var block = player.level().clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        Vec3 aim = block.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? to : block.getLocation();
        var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player.level(), player, from, aim,
                new net.minecraft.world.phys.AABB(from, aim).inflate(1), e -> e != vehicle && e != player && !e.isSpectator() && e.isPickable()
                        && e.getVehicle() != vehicle, 0.3f);
        if (hit != null) aim = hit.getLocation();
        // the pilot's eye (the jar) turned onto that point: what the server picks along is what the crosshair shows
        Vec3 eye = player.getEyePosition();
        double dx = aim.x - eye.x, dy = aim.y - eye.y, dz = aim.z - eye.z;
        player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        player.setXRot(net.minecraft.util.Mth.clamp((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))), -90, 90));
    }

    private static void offset(ComputeTargetCameraOffsetEvent event) {
        double back = com.coolfreeze23.headinajar.client.PilotClient.chaseNow();
        if (back <= 0) return;
        var vehicle = net.minecraft.client.Minecraft.getInstance().player.getVehicle();
        Vec3 now = event.getResult();
        // raised, so the crosshair's line passes over the machine, not through it: a little for a camera turning about the
        // pilot's eye (the Hopper), a third of the height and a margin for one turning about the machine's two thirds
        var player = net.minecraft.client.Minecraft.getInstance().player;
        Vec3 pivot = com.coolfreeze23.headinajar.client.PilotClient.pivot(player, 1);
        double up;
        if (pivot == null) {
            up = Math.max(now.y, vehicle.getBbHeight() * 0.2);
        } else {
            // the machine's highest point is its box or the jar on it (about a block over the pilot's seat), whichever is higher
            double top = Math.max(vehicle.getY() + vehicle.getBbHeight(), player.getY() + 1.0);
            up = Math.max(now.y, top - pivot.y + 0.3);
        }
        if (now.z < back || now.y < up) event.setResult(new Vec3(now.x, up, Math.max(now.z, back)));
    }

    private static void coupling(ComputeCameraCouplingEvent event) {
        if (com.coolfreeze23.headinajar.client.PilotClient.chaseNow() > 0) event.setResult(true);
    }
}
