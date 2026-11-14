package com.coolfreeze23.headinajar.mecha;

import com.coolfreeze23.headinajar.entity.MechaEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/**
 * The Golden Parachute: a pilot fired clear of a machine that is about to go. One entry point for every way a pilot is
 * thrown clear (the hull dropping under a tenth, or anything else that must get the jar out before the end): the
 * frame's own eject moment where it has one (the klaxon, the restraint blown, the clip timed so its tick 4 is the
 * hand-over), the jar out of the cockpit, thrown clear, slowed, and the canopy's deploy started for everyone who sees it.
 * The canopy itself is {@link HopperNet}'s (its states) and the client's {@code Parachute} (its model and clips).
 */
public final class GoldenParachute {
    private GoldenParachute() {
    }

    /** Fires the pilot clear of the machine under the Golden Parachute. */
    public static void eject(MechaEntity m, ServerPlayer pilot) {
        boolean aloft = !m.onGround();
        MechaRig rig = MechaRig.of(m.frame());
        if (rig != null) Hopper.goldenParachute(m, pilot);
        MechaLogic.eject(m, pilot);
        com.coolfreeze23.headinajar.Advance.grant(pilot, "parachute");
        Vec3 back = m.getLookAngle().multiply(1, 0, 1).normalize().scale(-0.3);
        Vec3 v;
        // a frame on a wall throws the jar away from it (its cage faces out from the wall)
        net.minecraft.core.Direction wall = rig != null && rig.climbs ? Legs.wall(m) : null;
        if (wall != null) {
            v = new Vec3(-wall.getStepX() * 0.6, 0.9, -wall.getStepZ() * 0.6);
        } else if (rig != null && rig.flies()) {
            // the Kite drops the jar out of the back of its lantern, behind the tail: in flight a small push back and
            // down into the parachute, standing the usual throw up and back
            Vec3 exit = Bulwark.world(m, rig.locators.get("fx_jar_exit").px());
            if (pilot.connection != null) pilot.teleportTo(exit.x, exit.y, exit.z);
            else pilot.moveTo(exit.x, exit.y, exit.z);
            v = aloft ? back.add(0, -0.1, 0) : back.add(0, 1.1, 0);
        } else if (rig != null) {
            // an animated frame throws the jar up and back, clear of its own hoop or clamps
            v = back.add(0, 1.1, 0);
        } else {
            v = new Vec3(0, 1.2, 0);
        }
        pilot.setDeltaMovement(v);
        pilot.hurtMarked = true;
        // the throw is given again on the next tick: the pilot's client is still seated when this tick's push reaches it,
        // and a passenger's push is lost
        HopperNet.throwAgain(pilot, v);
        // the deploy is timed to the drop: faster when the air under the throw is short, none when there is no time
        HopperNet.fired(pilot, HopperNet.deployRate(pilot, v));
        pilot.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200));
        pilot.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.headinajar.parachute"), true);
    }
}
