package com.coolfreeze23.headinajar.mecha;

import com.coolfreeze23.headinajar.entity.MechaEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Where an arm tool's ray or stream comes from and where it goes. It goes where the pilot looks, which is where the
 * crosshair is (in a shoulder camera the pilot's look is turned onto the crosshair's target every tick), picked the same
 * way the arm picks what it hurts: the first creature along the look, else the block, else the end of its reach. It
 * comes from the tool: on the client from the muzzle on the module's own model as last drawn; where that is not known
 * (the server, a machine not drawn this frame), from the arm's wrist on the frame's rest pose, turned with the body.
 * <p>
 * The tap tools' blows wait for the arm's clip: the frame's strike lands its blow {@link #STRIKE_CONTACT} ticks after
 * the press and its recoil fires {@link #RECOIL_CONTACT} ticks after it (the clips' {@code arm:hit} and {@code arm:fire}
 * keys), so the blow and its sound land when the arm arrives.
 */
public final class ArmAim {
    /** Ticks from the press to the strike clip's contact ({@code arm:hit}, 0.4 s) and the recoil clip's shot ({@code arm:fire}, 0.3 s). */
    public static final int STRIKE_CONTACT = 8, RECOIL_CONTACT = 6;

    private ArmAim() {
    }

    /** The locator a module's ray or stream leaves from on its own model, or null if it fires none. */
    public static String muzzle(MechaModule module) {
        if (module == null) return null;
        return switch (module) {
            case PLASMA_LANCE, FLAMETHROWER, WELDING_TORCH, RIVET_GUN -> "fx_tip";
            case ARC_CANNON, TESLA_COIL -> "fx_arc";
            case TRACTOR_BEAM -> "fx_beam";
            default -> null;
        };
    }

    /** The locator a module holds an item at (the Manipulator Hand's claw), or null. */
    public static String grip(MechaModule module) {
        return module == MechaModule.MANIPULATOR_HAND ? "fx_tip" : null;
    }

    /** How far each beam tool reaches (what it hurts or holds, and what is drawn). */
    public static double reach(MechaModule module) {
        return switch (module) {
            case PLASMA_LANCE -> 32;
            case ARC_CANNON -> 16;
            case TRACTOR_BEAM -> 24;
            case FLAMETHROWER -> 7;
            case WELDING_TORCH -> 5;
            default -> 6;
        };
    }

    /** The flat slot of arm {@code side} (0 left, 1 right, 2 and 3 the shoulders). */
    public static int slot(MechaEntity m, int side) {
        return m.frame().firstIndex(MechaModule.Slot.ARM) + side;
    }

    /**
     * The arm's muzzle on the frame's rest pose: the wrist locator of its side, a little out along the look, turned with
     * the body; a shoulder sits higher. For a frame with no wrist locators: beside the hull at two thirds of its height.
     */
    public static Vec3 origin(MechaEntity m, int side, float partial) {
        double x = Mth.lerp(partial, m.xo, m.getX()), y = Mth.lerp(partial, m.yo, m.getY()), z = Mth.lerp(partial, m.zo, m.getZ());
        float yaw = Mth.rotLerp(partial, m.yBodyRotO, m.yBodyRot);
        MechaRig rig = MechaRig.of(m.frame());
        MechaRig.Locator wrist = rig == null ? null : rig.locators.get(side % 2 == 0 ? "fx_wrist_l" : "fx_wrist_r");
        Vec3 px = wrist != null ? wrist.px() : new Vec3((side % 2 == 0 ? -1 : 1) * m.getBbWidth() * 8, m.getBbHeight() * 16 * 0.66, -4);
        Vec3 local = new Vec3(px.x / 16, px.y / 16 + (side >= 2 ? 0.45 : 0), px.z / 16 - 0.35);
        // the model faces north (-z) and is turned by 180 - body yaw, as the entity renderer turns it
        Vec3 off = local.yRot((float) Math.toRadians(180 - yaw));
        return new Vec3(x + off.x, y + off.y, z + off.z);
    }

    /** What the pilot's look meets within {@code range}: a creature first (as the arms pick), else a block, else the end. */
    public static HitResult aim(Player pilot, MechaEntity m, double range, float partial) {
        Vec3 eye = pilot.getEyePosition(partial), end = eye.add(pilot.getViewVector(partial).scale(range));
        EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(pilot, eye, end, new AABB(eye, end).inflate(1),
                e -> e != pilot && e != m && !e.isSpectator() && e.isPickable() && e.getVehicle() != m, range * range);
        if (hit != null) return hit;
        BlockHitResult block = pilot.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, pilot));
        return block;
    }

    /** The point a beam lands on: a creature's middle, the block face it meets, or the end of its reach. */
    public static Vec3 end(Player pilot, MechaEntity m, double range, float partial) {
        HitResult hit = aim(pilot, m, range, partial);
        if (hit instanceof EntityHitResult e) {
            Entity t = e.getEntity();
            return t.getPosition(partial).add(0, t.getBbHeight() / 2, 0);
        }
        return hit.getLocation();
    }

    /** Whether the hit is a creature (for the impact's look). */
    public static boolean onCreature(HitResult hit) {
        return hit instanceof EntityHitResult;
    }
}
