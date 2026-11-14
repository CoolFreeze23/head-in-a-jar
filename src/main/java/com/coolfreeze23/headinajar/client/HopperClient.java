package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.mecha.HopperAnimation;
import com.coolfreeze23.headinajar.mecha.HopperNet;
import com.coolfreeze23.headinajar.mecha.HopperSounds;
import com.coolfreeze23.headinajar.mecha.HopperView;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

/**
 * The client's side of the animated walkers: the drive key held and let go in the cockpit (the Pogo Protocol), the motor
 * hum that follows each walker's real speed, the boot's relay clicks (one per fitted module), the drill's spin, a small
 * camera answer to the pilot's own hard landings, and the pilot's player hidden so the frame draws the jar in its collar.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID, value = Dist.CLIENT)
public final class HopperClient {
    private static boolean driveHeld;
    private static int driveFor = -1;
    private static final Map<Integer, Motor> MOTORS = new HashMap<>();
    private static float trauma;
    private static long seenLanding = -1;
    private static long seenHeavy = -1;
    /** Beyond this distance from the camera (blocks) a machine drops its small motions: the sensor's look, the boot, the tic. */
    public static final double DETAIL_RANGE = 32;

    /** This machine is beyond the small motions' range from the camera. */
    public static boolean far(MechaEntity m) {
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return camera.distanceToSqr(m.getX(), m.getY(), m.getZ()) > DETAIL_RANGE * DETAIL_RANGE;
    }

    private HopperClient() {
    }

    /** The animated frame the local player pilots, or null. */
    public static MechaEntity piloting() {
        MechaEntity m = PilotClient.mecha();
        return m != null && m.frame().animated() ? m : null;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            MOTORS.clear();
            return;
        }
        MechaEntity mine = piloting();
        boolean down = mine != null && mc.screen == null && ClientSetup.DRIVE.isDown();
        if (mine != null && (down != driveHeld || driveFor != mine.getId())) {
            if (down != driveHeld) PacketDistributor.sendToServer(new HopperNet.Pogo(down));
            driveFor = mine.getId();
        }
        driveHeld = down;
        if (mine == null) driveFor = -1;
        if (mine != null) {
            camera(mine);
            leap(mc, mine);
            // the sneak key lets a climbing frame go of its wall (read here, on the client that moves it)
            if (mc.options.keyShift.isDown() || mc.player.isCrouching()) mine.clingLetGo = true;
        }
        trauma = Math.max(0, trauma - 0.05f);

        long now = mc.level.getGameTime();
        for (var e : mc.level.entitiesForRendering()) {
            if (!(e instanceof MechaEntity m) || !m.frame().animated()) continue;
            HopperView v = m.view;
            // the drill's bit: chases its working speed and coasts down for about 20 ticks after release
            int side = -1;
            for (int s = 0; s < 2; s++) if (m.arm(s) == MechaModule.DRILL) side = s;
            boolean working = side >= 0 && (m.armsShown() & (1 << side)) != 0;
            float target = working ? 0.9f : 0;
            v.drillSpeed += (target - v.drillSpeed) * (working ? 0.25f : 0.12f);
            v.drillAngle = (v.drillAngle + v.drillSpeed) % (Mth.TWO_PI * 64);
            relays(m, now);
            unboard(m, now);
            reel(m);
            motor(mc, m);
            hum(mc, m);
            carry(mc, m);
            wheeled(mc, m, now);
        }
        MOTORS.values().removeIf(Motor::isStopped);
    }

    private static long lastPogo = -1, handledLeap = -1;

    /**
     * The pilot's client moves its own mecha (as a horse's rider does), so the leap's impulse is applied here, the moment
     * the server's leap time arrives, with the charge the synced crouch gave it; the server sets the same launch for
     * everyone else's view of it.
     */
    private static void leap(Minecraft mc, MechaEntity m) {
        if (m.pogoAt() >= 0) lastPogo = m.pogoAt();
        long leap = m.leapAt();
        if (leap < 0 || leap == handledLeap) return;
        handledLeap = leap;
        if (lastPogo < 0 || leap < lastPogo || mc.level.getGameTime() - leap > 10) return;
        float charge = Mth.clamp((leap - lastPogo) / (float) com.coolfreeze23.headinajar.mecha.Hopper.CHARGE_TICKS, 0, 1);
        m.setDeltaMovement(com.coolfreeze23.headinajar.mecha.Hopper.launch(charge, mc.player.getYRot(), mc.player.getXRot()));
        m.hasImpulse = true;
        lastPogo = -1;
    }

    /**
     * A frame on wheels: the reversing beeper once a second (code, so it never speeds up with the clip), and the
     * buffer's ram felt in the camera: hard for its pilot, lightly for anyone within 8 blocks.
     */
    private static void wheeled(Minecraft mc, MechaEntity m, long now) {
        var rig = com.coolfreeze23.headinajar.mecha.MechaRig.of(m.frame());
        if (rig == null || rig.wheels == null) return;
        var w = m.view.wheels;
        if (HopperAnimation.piloted(m) && w.signed < -com.coolfreeze23.headinajar.mecha.Wheels.MOVING && (now + m.getId()) % 20 == 0) {
            HopperKeyframes.play(m, m.position().add(0, 1, 0), "reverse_beep", 1, 1);
        }
        long ram = m.ramAt();
        if (ram >= 0 && ram != m.view.ramSeen) {
            boolean fresh = m.view.ramSeen != Long.MIN_VALUE && now - ram < 3;
            m.view.ramSeen = ram;
            if (fresh && !LinkHud.photosensitive()) {
                if (mc.player.getVehicle() == m) trauma = Math.min(1, trauma + 0.3f);
                else if (mc.player.distanceToSqr(m) < 64) trauma = Math.min(1, trauma + 0.1f);
            }
        }
    }

    /** Rush Hour widens the pilot's view a tenth while the boost runs. */
    @SubscribeEvent
    public static void rushFov(net.neoforged.neoforge.client.event.ComputeFovModifierEvent event) {
        MechaEntity m = piloting();
        if (m == null || com.coolfreeze23.headinajar.mecha.Wheelhouse.boost(m, m.level().getGameTime()) <= 1) return;
        event.setNewFovModifier(event.getNewFovModifier() * 1.1f);
    }

    /** The drill's turn this frame (radians). */
    static float drillAngle(MechaEntity m, float partialTick) {
        return m.view.drillAngle + m.view.drillSpeed * partialTick;
    }

    /** The Dragline's reel turns with the line it winds in or pays out (the 4 px drum). */
    private static void reel(MechaEntity m) {
        if (!com.coolfreeze23.headinajar.mecha.Skitter.out(m)) {
            m.view.reelLength = Float.NaN;
            return;
        }
        float length = m.lineLength() > 0 ? m.lineLength()
                : (float) com.coolfreeze23.headinajar.mecha.Skitter.hangPoint(m).distanceTo(m.anchor());
        if (!Float.isNaN(m.view.reelLength)) m.view.reelAngle = (m.view.reelAngle + com.coolfreeze23.headinajar.mecha.Skitter.reelTurn(m.view.reelLength - length)) % 360;
        m.view.reelLength = length;
    }

    /** Getting out the ordinary way: the clamps let go and the hoop lifts, then it comes down again over the empty collar. */
    private static void unboard(MechaEntity m, long now) {
        long boarded = m.boardedAt();
        double t = boarded < -1 && !HopperAnimation.piloted(m) && m.ejectedAt() < now - 60 && !HopperAnimation.cageShut(m) ? now + boarded : -1;
        double before = m.view.unboardTick;
        m.view.unboardTick = t;
        if (t < 0 || t >= HopperAnimation.UNBOARD) return;
        // the boarding's own sounds, in the reverse order: unlatch as the clamps open, the servo, the latch as it closes
        var rig = com.coolfreeze23.headinajar.mecha.MechaRig.of(m.frame());
        for (Object[] cue : rig.unboardSounds) {
            int at = (int) cue[0];
            if (before < at && t >= at) HopperKeyframes.play(m, m.position().add(0, m.getBbHeight(), 0), (String) cue[1], 1, (float) cue[2]);
        }
    }

    /** One relay click per fitted module after the boot's lamps, three ticks apart: you hear your loadout count off. */
    private static void relays(MechaEntity m, long now) {
        long boarded = m.boardedAt();
        double t = boarded < 0 || !HopperAnimation.piloted(m) ? -1 : now - boarded - HopperAnimation.BOOT_START;
        if (t < 0 || t > 40) {
            m.view.bootTick = -1;
            return;
        }
        int n = 0;
        for (int i = 0; i < m.frame().totalSlots(); i++) {
            if (m.moduleAt(i) == null) continue;
            double at = HopperAnimation.RELAY_FROM + HopperAnimation.RELAY_EVERY * n;
            if (m.view.bootTick < at && t >= at && t - at < 2) {
                HopperKeyframes.play(m, m.position().add(0, 1.4, 0), "relay_click", 1, 1 + 0.04f * n);
            }
            n++;
        }
        m.view.bootTick = t;
    }

    private static void motor(Minecraft mc, MechaEntity m) {
        boolean wanted = HopperAnimation.piloted(m) && m.isAlive() && mc.player.distanceToSqr(m) < 24 * 24;
        Motor motor = MOTORS.get(m.getId());
        if (wanted && (motor == null || motor.isStopped()) && m.view.speed > 0.01) {
            var sound = HopperSounds.get(m.frame(), "motor");
            if (sound == null) return;
            motor = new Motor(m, sound);
            MOTORS.put(m.getId(), motor);
            mc.getSoundManager().play(motor);
        }
    }

    /**
     * Heavy Lifting: this client puts the held load where the server does (the chest, the lift, the throw's hoist), from
     * the same synced moments, after every entity has ticked, so it rides the fists smoothly instead of trailing the
     * server's position updates. A lifted player is moved by the server alone.
     */
    private static void carry(Minecraft mc, MechaEntity m) {
        int id = m.heldId();
        if (id < 0) {
            m.view.heldSeen = -1;
            return;
        }
        var load = mc.level.getEntity(id);
        if (load == null || load == mc.player) return;
        if (m.view.heldSeen != id) {
            m.view.heldSeen = id;
            m.view.liftFrom = load.position();
        }
        if (com.coolfreeze23.headinajar.mecha.Kite.talons(m)) {
            com.coolfreeze23.headinajar.mecha.Kite.place(m, load, com.coolfreeze23.headinajar.mecha.Kite.feet(m, load, mc.level.getGameTime(), m.view.liftFrom));
        } else {
            com.coolfreeze23.headinajar.mecha.Bulwark.place(m, load, com.coolfreeze23.headinajar.mecha.Bulwark.feet(m, load, mc.level.getGameTime(), m.view.liftFrom));
        }
    }

    /**
     * Vanilla draws a ridden creature's health as rows of mount hearts beside the crosshair; a mecha's pilot already has
     * hull, power and heat in the mod's own HUD, and the hearts sit over the machine in the shoulder view. Not drawn for
     * mecha pilots (any other mount keeps them).
     */
    @SubscribeEvent
    public static void hideMountHearts(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre event) {
        if (!event.getName().equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.VEHICLE_HEALTH)) return;
        var player = Minecraft.getInstance().player;
        // a machine's own HUD shows its health (a mecha's panel, a shell's bar): vanilla's rows of mount hearts would cover it
        if (player != null && (player.getVehicle() instanceof MechaEntity
                || player.getVehicle() instanceof com.coolfreeze23.headinajar.entity.ShellEntity s && s.animated())) event.setCanceled(true);
    }

    /**
     * The ground shakes under a heavy machine for whoever stands near it (the Colossus's footfalls, its spades going in
     * and coming out): strongest at the machine, nothing past {@code range} blocks. Off under the photosensitive option.
     */
    public static void shake(MechaEntity m, float amount, double range) {
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double d = Math.sqrt(camera.distanceToSqr(m.position()));
        if (d < range) trauma = Math.min(1, trauma + amount * (float) (1 - d / range));
    }

    /** The anchored Colossus's hum: one loop per sieged machine within 24 blocks, from locked until it lets go. */
    private static void hum(Minecraft mc, MechaEntity m) {
        if (m.view.humming || !com.coolfreeze23.headinajar.mecha.Siege.locked(m) || mc.player.distanceToSqr(m) > 24 * 24) return;
        var sound = HopperSounds.get(m.frame(), "siege_hum");
        if (sound == null) return;
        m.view.humming = true;
        mc.getSoundManager().play(new Hum(m, sound));
    }

    /** The pilot's own hard landings answer in the camera (off under the photosensitive option). */
    private static void camera(MechaEntity m) {
        long landed = m.landedAt();
        if (landed >= 0 && landed != seenLanding) {
            if (seenLanding != -1 || m.level().getGameTime() - landed < 3) trauma = Math.min(1, trauma + 0.45f);
            seenLanding = landed;
        }
        long land = m.landAt();
        if (land >= 0 && land != seenHeavy) {
            if ((land & 1) == 1 && seenHeavy != -1) trauma = Math.min(1, trauma + 0.3f);
            seenHeavy = land;
        }
    }

    @SubscribeEvent
    public static void cameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (trauma <= 0 || LinkHud.photosensitive()) return;
        double t = event.getPartialTick() + (Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime());
        float shake = trauma * trauma * 6;
        event.setPitch(event.getPitch() + shake * (float) Math.sin(t * 1.7));
        event.setRoll(event.getRoll() + shake * 0.6f * (float) Math.sin(t * 2.3 + 1));
    }

    /** The frame draws its pilot's jar at its anchor: the pilot's own player is not drawn (before the jar code draws it). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void hidePilot(RenderPlayerEvent.Pre event) {
        if (HopperRenderer.drawsPilot(event.getEntity())) event.setCanceled(true);
    }

    /** The Golden Parachute's canopy over a falling jar: drawn after the jar, whoever drew it. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void parachute(RenderPlayerEvent.Pre event) {
        Parachute.render(event.getEntity(), event.getPartialTick(), event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
    }

    /** The Siege hum: loops while its machine is anchored, fading in and out. */
    static final class Hum extends AbstractTickableSoundInstance {
        private final MechaEntity m;

        Hum(MechaEntity m, net.minecraft.sounds.SoundEvent sound) {
            super(sound, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.m = m;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01f;
            this.x = m.getX();
            this.y = m.getY() + 2;
            this.z = m.getZ();
        }

        @Override
        public void tick() {
            boolean on = !m.isRemoved() && m.isAlive() && com.coolfreeze23.headinajar.mecha.Siege.locked(m);
            volume += ((on ? 1 : 0) - volume) * 0.15f;
            if (!on && volume < 0.02f) {
                m.view.humming = false;
                stop();
            }
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }

    /** The motor hum of one walker: pitch 0.8 + 0.4 s and volume sqrt(s) from its measured speed, chased. */
    static final class Motor extends AbstractTickableSoundInstance {
        private final MechaEntity m;
        private final float offset;
        private float s;

        Motor(MechaEntity m, net.minecraft.sounds.SoundEvent sound) {
            super(sound, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.m = m;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.001f;
            this.offset = 0.97f + 0.06f * ((m.getId() * 31 & 255) / 255f);
            this.pitch = 0.8f * offset;
            this.x = m.getX();
            this.y = m.getY() + 1;
            this.z = m.getZ();
        }

        @Override
        public void tick() {
            if (m.isRemoved() || !m.isAlive()) {
                stop();
                return;
            }
            float target = HopperAnimation.piloted(m) ? (float) Mth.clamp(m.view.speed / com.coolfreeze23.headinajar.mecha.MechaRig.of(m.frame()).walkDesign, 0, 1) : 0;
            s += (target - s) * 0.2f;
            // a load in the fists pulls the drivetrain down a little
            float wantPitch = (0.8f + 0.4f * s) * offset * (m.heldId() >= 0 ? 0.92f : 1f);
            pitch += Mth.clamp(wantPitch - pitch, -0.05f, 0.05f);
            volume += ((float) Math.sqrt(s) - volume) * 0.2f;
            x = m.getX();
            y = m.getY() + 1;
            z = m.getZ();
            if (volume < 0.01f && target == 0) stop();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
