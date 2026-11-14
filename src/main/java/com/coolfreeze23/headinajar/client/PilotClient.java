package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.mecha.MechaLogic;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** The pilot's side of a mecha: mouse buttons drive the arms, jump fires jets, sneak + J ejects; the HUD and camera. */
public final class PilotClient {
    private static int sentArms;
    private static boolean wasJumping;
    private static int ejectHold;
    /** The mecha the arm state was last sent for: a new one is always told what is held, even if nothing is. */
    private static int sentFor = -1;

    private PilotClient() {
    }

    public static MechaEntity mecha() {
        var player = Minecraft.getInstance().player;
        if (player == null || player.getData(ModRegistry.JAR_STATE).form() != Form.PILOT) return null;
        return player.getVehicle() instanceof MechaEntity m ? m : null;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        MechaEntity m = mecha();
        if (m == null || mc.screen != null) {
            if (sentArms != 0) PacketDistributor.sendToServer(new Network.ArmInput(sentArms = 0));
            ejectHold = 0;
            if (m == null) sentFor = -1;
            return;
        }
        int arms = (mc.options.keyAttack.isDown() ? 1 : 0) | (mc.options.keyUse.isDown() ? 2 : 0);
        if (arms != sentArms || sentFor != m.getId()) PacketDistributor.sendToServer(new Network.ArmInput(sentArms = arms));
        sentFor = m.getId();
        boolean jumping = mc.options.keyJump.isDown();
        if (jumping && !wasJumping && !m.onGround() && m.has(MechaModule.JUMP_JETS)) {
            PacketDistributor.sendToServer(new Network.JarAction(Network.Action.JET));
        }
        wasJumping = jumping;
        boolean cortex = mc.player.getData(ModRegistry.JAR_STATE).has(com.coolfreeze23.headinajar.module.ModuleType.CORTEX_COPROCESSOR);
        if ((m.has(MechaModule.TARGET_LOCK) || cortex) && m.battery() > 0) lockOn(mc, m);
        if (mc.options.keyShift.isDown() && ClientSetup.JAR_MENU.isDown()) {
            if (++ejectHold == 20) PacketDistributor.sendToServer(new Network.JarAction(Network.Action.EJECT));
        } else {
            ejectHold = 0;
        }
    }

    /** Target Lock: a hostile within 24 blocks and about 12 degrees of your aim pulls the aim gently onto it. */
    private static void lockOn(Minecraft mc, MechaEntity m) {
        var player = mc.player;
        var eye = player.getEyePosition();
        var look = player.getLookAngle();
        net.minecraft.world.entity.LivingEntity best = null;
        double bestDot = 0.978;
        for (var e : player.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, m.getBoundingBox().inflate(24),
                e -> e instanceof net.minecraft.world.entity.monster.Enemy && e.isAlive())) {
            var to = e.getBoundingBox().getCenter().subtract(eye).normalize();
            double dot = to.dot(look);
            if (dot > bestDot) {
                bestDot = dot;
                best = e;
            }
        }
        if (best == null) return;
        var to = best.getBoundingBox().getCenter().subtract(eye);
        float yaw = (float) (net.minecraft.util.Mth.atan2(to.z, to.x) * (180 / Math.PI)) - 90;
        float pitch = (float) -(net.minecraft.util.Mth.atan2(to.y, to.horizontalDistance()) * (180 / Math.PI));
        player.setYRot(player.getYRot() + net.minecraft.util.Mth.wrapDegrees(yaw - player.getYRot()) * 0.2f);
        player.setXRot(player.getXRot() + (pitch - player.getXRot()) * 0.2f);
    }

    /** How far back the chase camera sits: twice the frame's height, or for a long low frame (the Wheelhouse) its width. */
    public static float chase(com.coolfreeze23.headinajar.mecha.MechaFrame frame) {
        return Math.max(frame.height * 2.0f, frame.width * 2.5f);
    }

    /**
     * How far back the chase camera sits behind a body of this size (the frames' rule, for an animated shell too), and
     * never nearer than {@link #NEAREST_CHASE}: from closer, a low shell's wheels or legs fall behind the hotbar.
     */
    public static float chase(float width, float height) {
        return Math.max(NEAREST_CHASE, Math.max(height * 2.0f, width * 2.5f));
    }

    /** The nearest the chase camera comes (the Rover's and the Spider Walker's; every frame's own is further). */
    public static final float NEAREST_CHASE = 4.5f;

    /**
     * The chase distance for what the local player is driving: a mecha's frame, or an animated shell (the Lunk 5.4
     * blocks, the Rover and the Spider Walker 4.5); -1 for anything else (vanilla's own).
     */
    public static float chaseNow() {
        MechaEntity m = mecha();
        if (m != null) return chase(m.frame());
        var player = Minecraft.getInstance().player;
        if (player != null && player.getVehicle() instanceof com.coolfreeze23.headinajar.entity.ShellEntity s && s.animated() && s.getFirstPassenger() == player) {
            return chase(s.getBbWidth(), s.getBbHeight());
        }
        return -1;
    }

    /** Where the third-person camera turns about on a shell: this share of its height up its centre line. */
    public static final double SHELL_PIVOT = 2 / 3.0;
    /** The pivot is on (off only to film the old framing beside the new). */
    public static boolean PIVOT = true;
    /** Whether Shoulder Surfing's own view is on now (set by its plugin when the mod is there). */
    public static java.util.function.BooleanSupplier shoulderView = () -> false;

    /**
     * The point the third-person camera turns about for the local player in a cockpit or on an animated shell: on the
     * machine's centre line at its rig's pivot share of its height (a shell's two thirds); null to keep the pilot's eye
     * (on foot, or the Hopper, whose framing is the reference).
     */
    public static net.minecraft.world.phys.Vec3 pivot(net.minecraft.world.entity.Entity camera, float partialTick) {
        var mc = Minecraft.getInstance();
        if (!PIVOT || camera != mc.player || mc.player == null) return null;
        var vehicle = mc.player.getVehicle();
        double share;
        if (vehicle instanceof MechaEntity m && m.getFirstPassenger() == mc.player && mecha() == m) {
            var rig = com.coolfreeze23.headinajar.mecha.MechaRig.of(m.frame());
            if (rig == null || Double.isNaN(rig.pivot)) return null;
            share = rig.pivot;
        } else if (vehicle instanceof com.coolfreeze23.headinajar.entity.ShellEntity s && s.animated() && s.getFirstPassenger() == mc.player) {
            share = SHELL_PIVOT;
        } else {
            return null;
        }
        net.minecraft.world.phys.Vec3 at = vehicle.getPosition(partialTick);
        if (!shoulderView.getAsBoolean()) {
            // vanilla's third person has no offset of its own: turning about the machine's top (its box or the jar on it,
            // whichever is higher) keeps the crosshair's line over the machine instead of through its middle
            double jar = net.minecraft.util.Mth.lerp(partialTick, mc.player.yo, mc.player.getY()) + 1.0;
            return new net.minecraft.world.phys.Vec3(at.x, Math.max(at.y + vehicle.getBbHeight(), jar), at.z);
        }
        return at.add(0, vehicle.getBbHeight() * share, 0);
    }

    /** The chase camera sits further back from bigger frames and shells. */
    public static void cameraDistance(CalculateDetachedCameraDistanceEvent event) {
        float d = chaseNow();
        if (d > 0) event.setDistance(d);
    }

    public static void hud(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        MechaEntity m = mecha();
        // the auto-dimming visor while the pilot's own Plasma Lance burns (drawn with the HUD hidden too: it is the glass)
        if (m != null) com.coolfreeze23.headinajar.client.fx.ArmBeams.visor(g, m, delta.getRealtimeDeltaTicks());
        if (m == null || mc.options.hideGui) return;
        // a line at a time in the HUD's side column (top left), so nothing here meets chat, the hotbar or another line
        HudStack.gap();
        HudStack.text(g, m.getDisplayName(), 0xFFFFFF);
        var utility = com.coolfreeze23.headinajar.mecha.MechaSystems.selectedUtility(m);
        if (utility != null) {
            HudStack.text(g, Component.translatable("hud.headinajar.utility", ClientSetup.ABILITY.getTranslatedKeyMessage(),
                    Component.translatable("item.headinajar." + utility.id()), ClientSetup.SECONDARY.getTranslatedKeyMessage()), 0xFFD080);
        }
        if (m.has(MechaModule.CARGO_HOLD)) {
            // the swap key opens the hold from the cockpit
            HudStack.text(g, Component.translatable("hud.headinajar.cargo", ClientSetup.SWAP.getTranslatedKeyMessage(),
                    Component.translatable("item.headinajar." + MechaModule.CARGO_HOLD.id())), 0xFFD080);
        }
        HudStack.bar(g, 90, m.getHealth() / m.getMaxHealth(), 0xFFE04848, 0xFF9A2E2E, null);
        HudStack.bar(g, 90, m.battery() / (float) m.batteryCapacity(), 0xFF3FD8F0, 0xFF1F9AB8,
                Component.translatable("hud.headinajar.clarity.battery", m.battery() / 1000));
        float heat = m.heat() / m.frame().heatCap;
        int heatColor = heat >= MechaLogic.THROTTLE ? 0xFFE04848 : heat >= 0.6f ? 0xFFE0C040 : 0xFF60C060;
        // a cool machine's heat bar is short and green, never the red of a low battery
        HudStack.bar(g, 90, heat, heatColor, heatColor, Component.translatable("hud.headinajar.heat", Math.round(heat * 100)), false);
        float load = m.load();
        HudStack.text(g, Component.translatable("hud.headinajar.load", m.mass(), m.frame().load), load > 1 ? 0xFF5555 : load > 0.6f ? 0xFFD040 : 0xC0C0C0);
        // each arm by the control that fires it, as the player has bound it (attack and use may be either button)
        HudStack.text(g, Component.translatable("hud.headinajar.clarity.arm", mc.options.keyAttack.getTranslatedKeyMessage(), arm(m, 0)), 0xA0E0FF);
        HudStack.text(g, Component.translatable("hud.headinajar.clarity.arm", mc.options.keyUse.getTranslatedKeyMessage(), arm(m, 1)), 0xA0E0FF);
        // the shoulder weapons fire with the button of their side, as the arm below them does
        if (m.frame().hasShoulders()) {
            // outside Siege a shoulder fires together with the arm below it (one button a side); planted, it fires alone
            boolean alone = com.coolfreeze23.headinajar.mecha.Siege.rooted(m);
            HudStack.text(g, Component.translatable(alone ? "hud.headinajar.shoulders" : "hud.headinajar.shoulders_with_arms", arm(m, 2), arm(m, 3)), 0xA0E0FF);
        }
        // the Bio-Scanner reads what the crosshair is on
        Component scan = mc.crosshairPickEntity instanceof net.minecraft.world.entity.LivingEntity seen && seen != m
                ? com.coolfreeze23.headinajar.mecha.MechaSystems.bioScan(m, seen) : null;
        if (scan != null) HudStack.text(g, scan, 0x90E0A0);
        Component status = null;
        int statusColor = 0xFFFFFF;
        if (m.vent() > 0) status = Component.translatable("hud.headinajar.venting").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        else if (m.battery() <= 0) status = Component.translatable("hud.headinajar.limp").withStyle(ChatFormatting.YELLOW);
        else if (load > MechaLogic.LOAD_LOCK) status = Component.translatable("hud.headinajar.servos_locked_why",
                Component.translatable("block.headinajar.assembly_gantry")).withStyle(ChatFormatting.RED);
        else if (!MechaLogic.canWalk(m) && !MechaLogic.canFly(m) && !(m.isInWater() && m.has(MechaModule.AQUA_FINS))) {
            status = Component.translatable("hud.headinajar.no_legs", Component.translatable("block.headinajar.assembly_gantry")).withStyle(ChatFormatting.YELLOW);
        } else if (ejectHold > 0) {
            status = Component.translatable("hud.headinajar.ejecting", 100 * ejectHold / 20);
            statusColor = 0xFFD040;
        } else if (m.frame().animated() && m.pogoAt() >= 0) {
            int charge = Math.round(100 * net.minecraft.util.Mth.clamp((mc.level.getGameTime() - m.pogoAt()) / (float) com.coolfreeze23.headinajar.mecha.Hopper.CHARGE_TICKS, 0, 1));
            status = Component.translatable("hud.headinajar.pogo", charge).withStyle(ChatFormatting.GOLD);
        }
        if (status != null) HudStack.text(g, status, statusColor);
        // what the frame's own verb (the drive key) does now, in the same column
        var rig = com.coolfreeze23.headinajar.mecha.MechaRig.of(m.frame());
        if (rig != null && rig.verb == com.coolfreeze23.headinajar.mecha.MechaRig.Verb.POGO && m.pogoAt() < 0 && MechaLogic.canWalk(m)) {
            HudStack.text(g, Component.translatable("hud.headinajar.pogo_hint", ClientSetup.DRIVE.getTranslatedKeyMessage()), 0x909090);
        } else if (rig != null && rig.verb == com.coolfreeze23.headinajar.mecha.MechaRig.Verb.LIFT) {
            // Heavy Lifting: what the drive key does now
            String hint = m.heldId() >= 0 ? "hud.headinajar.lift_hold" : "hud.headinajar.lift_hint";
            HudStack.text(g, Component.translatable(hint, ClientSetup.DRIVE.getTranslatedKeyMessage()), m.heldId() >= 0 ? 0xFFD080 : 0x909090);
        } else if (rig != null && rig.verb == com.coolfreeze23.headinajar.mecha.MechaRig.Verb.SIEGE) {
            // Siege: kneel, or stand up again once anchored
            boolean held = com.coolfreeze23.headinajar.mecha.Siege.locked(m);
            HudStack.text(g, Component.translatable(held ? "hud.headinajar.siege_held" : "hud.headinajar.siege_hint", ClientSetup.DRIVE.getTranslatedKeyMessage()),
                    held ? 0xFFD080 : 0x909090);
        } else if (rig != null && rig.verb == com.coolfreeze23.headinajar.mecha.MechaRig.Verb.JINGLE) {
            HudStack.text(g, Component.translatable("hud.headinajar.jingle_hint", ClientSetup.DRIVE.getTranslatedKeyMessage()), 0x909090);
        } else if (rig != null && rig.verb == com.coolfreeze23.headinajar.mecha.MechaRig.Verb.RUSH) {
            // Rush Hour: what the drive key does, or that it is running
            boolean on = com.coolfreeze23.headinajar.mecha.Wheelhouse.boost(m, mc.level.getGameTime()) > 1;
            HudStack.text(g, on ? Component.translatable("hud.headinajar.rush_on")
                    : Component.translatable("hud.headinajar.rush_hint", ClientSetup.DRIVE.getTranslatedKeyMessage()), on ? 0xFFD040 : 0x909090);
        } else if (rig != null && rig.verb == com.coolfreeze23.headinajar.mecha.MechaRig.Verb.GRAFT) {
            // the Graft: what the drive key does, or the grafted gene and its seconds left (in the veins' colour)
            var gene = com.coolfreeze23.headinajar.mecha.Chimera.grafted(m);
            HudStack.text(g, gene != null
                    ? Component.translatable("hud.headinajar.graft_on", Component.translatable("gene.headinajar." + gene.id()),
                    com.coolfreeze23.headinajar.mecha.Chimera.secondsLeft(m))
                    : Component.translatable("hud.headinajar.graft_hint", ClientSetup.DRIVE.getTranslatedKeyMessage()),
                    gene != null ? 0xFF000000 | brighten(com.coolfreeze23.headinajar.mecha.Chimera.tint(m)) : 0x909090);
        } else if (rig != null && rig.verb == com.coolfreeze23.headinajar.mecha.MechaRig.Verb.DRAGLINE) {
            // the Dragline: what the drive key does now, and while hanging, how to pay the line out
            var drive = ClientSetup.DRIVE.getTranslatedKeyMessage();
            if (com.coolfreeze23.headinajar.mecha.Skitter.out(m)) {
                double length = m.lineLength() > 0 ? m.lineLength() : com.coolfreeze23.headinajar.mecha.Skitter.hangPoint(m).distanceTo(m.anchor());
                HudStack.text(g, Component.translatable("hud.headinajar.dragline_out", String.format(java.util.Locale.ROOT, "%.1f", length), drive), 0xFFD040);
                if (!m.onGround()) HudStack.text(g, Component.translatable("hud.headinajar.dragline_hang", mc.options.keyJump.getTranslatedKeyMessage()), 0xFFD040);
            } else {
                HudStack.text(g, Component.translatable("hud.headinajar.dragline_hint", drive), 0x909090);
            }
        } else if (rig != null && rig.verb == com.coolfreeze23.headinajar.mecha.MechaRig.Verb.TALON) {
            // the Talon Lift: what the drive key does now
            String hint = m.heldId() >= 0 ? "hud.headinajar.talon_hold" : "hud.headinajar.talon_hint";
            HudStack.text(g, Component.translatable(hint, ClientSetup.DRIVE.getTranslatedKeyMessage()), m.heldId() >= 0 ? 0xFFD080 : 0x909090);
        }
    }

    /** A colour lifted towards white so a dark one (oil, a cold blue) still reads on the HUD. */
    private static int brighten(int c) {
        int r = c >> 16 & 255, gr = c >> 8 & 255, b = c & 255;
        return (r + (255 - r) / 3) << 16 | (gr + (255 - gr) / 3) << 8 | (b + (255 - b) / 3);
    }

    private static Component arm(MechaEntity m, int side) {
        MechaModule mod = m.arm(side);
        return mod == null ? Component.literal("-") : Component.translatable("item.headinajar." + mod.id());
    }
}
