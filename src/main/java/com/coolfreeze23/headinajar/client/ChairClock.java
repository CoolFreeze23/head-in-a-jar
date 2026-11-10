package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.block.SeparationChairBlockEntity;
import com.coolfreeze23.headinajar.entity.SeatEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The client's side of the chair's one clock, and the little the chair's animation asks about the world. */
public final class ChairClock {
    /** The client's game time plus the partial tick: what the chair's animation and the patient's overlay both read. */
    public static double now(Level level) {
        return level.getGameTime() + partialTick();
    }

    public static float partialTick() {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
    }

    /** Ticks into the chair's operation this frame, or -1 when it isn't running. */
    public static float elapsed(SeparationChairBlockEntity chair) {
        Level level = chair.getLevel();
        if (level == null || chair.opStart() < 0) return -1;
        return chair.elapsed(level.getGameTime(), partialTick());
    }

    /** Somebody sits in the chair. */
    public static boolean seated(SeparationChairBlockEntity chair) {
        Level level = chair.getLevel();
        return level != null && !level.getEntitiesOfClass(SeatEntity.class, new AABB(chair.getBlockPos()).inflate(0.5),
                seat -> !seat.getPassengers().isEmpty()).isEmpty();
    }

    public static double cameraDistance(SeparationChairBlockEntity chair) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return camera.distanceTo(Vec3.atCenterOf(chair.getBlockPos()));
    }

    /**
     * The jar tray is out: a jar is loaded (and its space is clear), the operation runs, the chair is still showing its
     * jar after one, or the finished head jar stands on the tray.
     */
    public static boolean trayOut(SeparationChairBlockEntity chair) {
        Level level = chair.getLevel();
        if (level == null) return false;
        if (chair.opStart() >= 0) return true;
        if (SeparationChairRenderer.view(chair).jarKind() != SeparationChairBlockEntity.JAR_NONE) return true;
        Vec3 at = SeparationChairBlockEntity.outlet(chair.getBlockPos(), chair.facing());
        return !level.getEntitiesOfClass(com.coolfreeze23.headinajar.entity.HeadJarEntity.class, new AABB(at, at).inflate(0.5)).isEmpty();
    }

    /** The chair this player is strapped into while it runs, or null. */
    public static SeparationChairBlockEntity runningChairOf(Player player) {
        if (player.getVehicle() instanceof SeatEntity seat && seat.chair() != null && seat.chair().running()) return seat.chair();
        return null;
    }

    /** The patient's own head and hat are not drawn on their body from the cut: the chair has it. */
    public static boolean headTaken(Player player) {
        SeparationChairBlockEntity chair = runningChairOf(player);
        return chair != null && elapsed(chair) >= com.coolfreeze23.headinajar.block.ChairTimeline.HEAD_OFF;
    }

    private ChairClock() {
    }
}
