package com.coolfreeze23.headinajar.host;

import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** How a jar drives a host: the rider's input steers it like a horse; fliers rise on jump and sink on sneak. */
public final class HostControl {
    private HostControl() {
    }

    private static boolean powered(LivingEntity host, Player pilot) {
        HostData data = Hosts.data(host);
        return !data.shell || pilot.getData(ModRegistry.JAR_STATE).charge() > 0;
    }

    public static Vec3 riddenInput(LivingEntity host, Player pilot) {
        if (host instanceof com.coolfreeze23.headinajar.entity.MechaEntity m) return com.coolfreeze23.headinajar.mecha.MechaLogic.riddenInput(m, pilot);
        if (!powered(host, pilot)) return Vec3.ZERO;
        float strafe = pilot.xxa * 0.5f;
        float forward = pilot.zza;
        if (forward <= 0) forward *= 0.25f;
        return new Vec3(strafe, 0, forward);
    }

    public static void tickRidden(LivingEntity host, Player pilot) {
        if (host instanceof com.coolfreeze23.headinajar.entity.MechaEntity m) {
            com.coolfreeze23.headinajar.mecha.MechaLogic.tickRidden(m, pilot);
            return;
        }
        host.setYRot(pilot.getYRot());
        host.yRotO = host.getYRot();
        host.setXRot(pilot.getXRot() * 0.5f);
        host.yBodyRot = host.getYRot();
        host.yHeadRot = host.getYRot();
        HostDefinition def = Hosts.definition(host);
        boolean flier = def != null && def.has("flight");
        boolean jumping = pilot.jumping;
        if (flier) {
            Vec3 v = host.getDeltaMovement();
            double y = jumping ? 0.25 : pilot.isShiftKeyDown() ? -0.25 : Math.max(v.y, -0.04);
            host.setDeltaMovement(v.x, y, v.z);
            host.fallDistance = 0;
        } else if (jumping && host.onGround() && (def == null || !def.drawbacks().contains("no_jump"))) {
            host.jumpFromGround();
        }
        if (def != null && def.has("climb") && host.horizontalCollision) {
            Vec3 v = host.getDeltaMovement();
            host.setDeltaMovement(v.x, 0.2, v.z);
            host.fallDistance = 0;
        }
        if (def != null && def.has("slow_fall")) {
            Vec3 v = host.getDeltaMovement();
            if (v.y < -0.08) host.setDeltaMovement(v.x, -0.08, v.z);
            host.fallDistance = 0;
        }
    }

    /**
     * The highest base walking pace a driven land host keeps: about 5.1 blocks a second flat out, 5.6 at full sync (a
     * player's sprint is 5.6). Steady speed on flat ground is the pace times about 44 blocks a second.
     */
    public static final double LAND_PACE = 0.1164;

    /**
     * Whether a host's ridden pace is held to {@link #LAND_PACE}: a land creature on the ground, not a flier, a swimmer
     * in water, one of the mod's shells or mechas (they have their own scale), or a creature vanilla lets a player ride
     * at its own pace (horses, donkeys, mules, llamas, camels).
     */
    public static boolean paced(LivingEntity host, boolean flier) {
        if (flier || host.isInWater() || host.getAttribute(Attributes.MOVEMENT_SPEED) == null) return false;
        if (host instanceof com.coolfreeze23.headinajar.entity.ShellEntity || host instanceof com.coolfreeze23.headinajar.entity.MechaEntity) return false;
        return !(host instanceof net.minecraft.world.entity.animal.horse.AbstractHorse) && !(host instanceof net.minecraft.world.entity.animal.camel.Camel);
    }

    public static float riddenSpeed(LivingEntity host, Player pilot) {
        if (host instanceof com.coolfreeze23.headinajar.entity.MechaEntity m) return com.coolfreeze23.headinajar.mecha.MechaLogic.riddenSpeed(m);
        HostDefinition def = Hosts.definition(host);
        boolean flier = def != null && def.has("flight");
        double speed = flier && host.getAttribute(Attributes.FLYING_SPEED) != null
                ? host.getAttributeValue(Attributes.FLYING_SPEED) : host.getAttributeValue(Attributes.MOVEMENT_SPEED);
        if (paced(host, flier)) {
            // a mob's speed attribute is its walking pace under its own AI; driven flat out it would be a gallop. Its base
            // pace is held to a player's, the modifiers on it (stiffness, genes) still counted, so a cow at full sync
            // tops out near a sprint. A real mount keeps the pace vanilla rides it at.
            var attribute = host.getAttribute(Attributes.MOVEMENT_SPEED);
            double base = attribute.getBaseValue();
            if (base > LAND_PACE) speed *= LAND_PACE / base;
        }
        if (Hosts.data(host).effectiveSync() >= 100) speed *= 1.1;
        if (host instanceof com.coolfreeze23.headinajar.entity.ShellEntity) speed *= com.coolfreeze23.headinajar.entity.ShellEntity.RIDDEN_SCALE;
        return (float) Math.max(0.05, speed);
    }

    /** Where the jar sits: the host's neck, turned with its body. */
    public static Vec3 ridingPosition(LivingEntity host) {
        Vec3 neck = Hosts.neck(host);
        return host.position().add(neck.yRot(-host.yBodyRot * ((float) Math.PI / 180f)));
    }
}
