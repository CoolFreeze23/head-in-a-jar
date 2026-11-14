package com.coolfreeze23.headinajar.mecha;

import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.feedback.Refuser;
import com.coolfreeze23.headinajar.host.Hosts;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Wheelhouse's Rush Hour, on the drive key: a press winds it up (the RIGHT OF WAY flag snaps up, the exhaust flaps
 * blow open, the buffer beam runs out), the boost comes on at the wind-up clip's tick 12 and lasts 80 ticks, and while
 * it runs at speed the buffer knocks aside whatever stands in front of it. Then the beam winds back in, and the tug
 * cools down before it will assert right of way again. A press it can't honour (off-road, a flat battery, venting,
 * cooling down) is answered by the refusal clip, its sound and a line, never by nothing. Server-authoritative: the
 * moment is synced on the entity, so the pilot's client (which moves it) and every watcher agree. Nothing is saved.
 */
public final class Wheelhouse {
    /** Kinds of the synced moment: the boost's wind-up, a refused press. */
    public static final int BOOST = 0, REFUSED = 1;
    /** The wind-up's length (the boost starts on its last tick), the boost's, the wind-down's, the refusal clip's. */
    public static final int WINDUP = 12, BOOST_TICKS = 80, END_TICKS = 24, REFUSED_TICKS = 34;
    /** Ticks after a boost ends before the next may start. */
    public static final int COOLDOWN = 200;
    /** Speed while boosting. */
    public static final double BOOST_SPEED = 1.6;
    /**
     * Power, by the frames' rule: a full-strength use costs about 1% of the frame's battery (300 kFE: 3,000 FE at the
     * press), and the boost while it runs the frame's own idle drain a tick (3 FE). Heat at the moment it comes on.
     */
    public static final int RUSH_FE = 3000, BOOST_FE_TICK = 3;
    public static final float RUSH_HEAT = 8;
    /** The ram: faster than this (blocks a tick), each creature at most once in this many ticks (once a boost). */
    public static final double RAM_SPEED = 0.3;
    public static final int RAM_DEBOUNCE = WINDUP + BOOST_TICKS;
    /** The ram's box: from 1.0 to 2.1 blocks ahead of the middle (the buffer's face is 1.6 ahead), 1.4 wide, 1.4 tall. */
    static final double RAM_NEAR = 1.0, RAM_FAR = 2.1, RAM_HALF_WIDTH = 0.7, RAM_HEIGHT = 1.4;

    private Wheelhouse() {
    }

    static boolean rushes(MechaEntity m) {
        return MechaRig.of(m.frame()) != null && MechaRig.of(m.frame()).verb == MechaRig.Verb.RUSH;
    }

    /** Ticks since the last boost was pressed, or -1 (none, or the last press was refused). */
    public static double boostTick(MechaEntity m, double now) {
        long at = m.rushAt();
        if (at < 0 || (at & 3) != BOOST) return -1;
        return now - at / 4;
    }

    /** The speed factor Rush Hour gives now: 1.6 from the wind-up's end for 80 ticks, else 1. */
    public static double boost(MechaEntity m, long now) {
        if (!rushes(m) || m.vent() > 0) return 1;
        double t = boostTick(m, now);
        return t >= WINDUP && t < WINDUP + BOOST_TICKS ? BOOST_SPEED : 1;
    }

    /** Why Rush Hour can't start now, or null. */
    public static String refusal(MechaEntity m, long now) {
        if (m.vent() > 0) return "mecha.rush.venting";
        if (m.battery() < RUSH_FE) return "mecha.rush.no_power";
        if (MechaLogic.offRoad(m)) return "mecha.rush.off_road";
        if (now < m.rushEnded + COOLDOWN) return "mecha.rush.cooldown";
        return null;
    }

    /** The drive key went down (the pogo payload's handler routes the Wheelhouse here); its release does nothing. */
    public static void input(ServerPlayer pilot, boolean held) {
        if (!held || !(pilot.getVehicle() instanceof MechaEntity m) || m.getFirstPassenger() != pilot || !rushes(m)) return;
        long now = m.level().getGameTime();
        double t = boostTick(m, now);
        if (t >= 0 && t < WINDUP + BOOST_TICKS + END_TICKS) {
            // already asserting it: said in text only (replaying the refusal over the boost would break its clip)
            Refusals.deny(pilot, Refuser.at(m), "mecha.rush.busy");
            return;
        }
        String why = refusal(m, now);
        if (why != null) {
            m.setRushAt(now, REFUSED);
            Hopper.play(m, "rush_refused");
            if (why.equals("mecha.rush.cooldown")) {
                Refusals.deny(pilot, Refuser.at(m), why, Math.max(1, (m.rushEnded + COOLDOWN - now + 19) / 20));
            } else {
                Refusals.deny(pilot, Refuser.at(m), why, Component.translatable("block.headinajar.assembly_gantry"));
            }
            return;
        }
        m.spend(RUSH_FE);
        m.setRushAt(now, BOOST);
        m.rushEnded = now + WINDUP + BOOST_TICKS;
    }

    /** Each server tick of a Wheelhouse: the boost's moment, its drain, and the ram. */
    public static void serverTick(MechaEntity m) {
        if (!rushes(m)) return;
        ServerLevel level = m.serverLevel();
        long now = level.getGameTime();
        double t = boostTick(m, now);
        Player pilot = Hosts.pilot(m);
        // its pilot's client moves it between ticks, so its speed is read from where it was the tick before
        Vec3 was = m.rushLastPos;
        m.rushLastPos = m.position();
        double speed = was == null ? 0 : Math.sqrt(Mth.square(m.getX() - was.x) + Mth.square(m.getZ() - was.z));
        if (t < 0 || t >= WINDUP + BOOST_TICKS) return;
        if (pilot == null) {
            // nobody aboard: the boost ends where it is
            m.rushEnded = now;
            m.setRushAt(-1, BOOST);
            return;
        }
        if (t == WINDUP) {
            // the signature moment: the sound, flames and smoke at both pipes, the line, heat
            Hopper.play(m, "rush_go");
            for (String pipe : new String[]{"fx_exhaust_l", "fx_exhaust_r"}) {
                Vec3 at = at(m, MechaRig.WHEELHOUSE.locators.get(pipe).px());
                level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 6, 0.05, 0.05, 0.05, 0.02);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 3, 0.1, 0.05, 0.1, 0.02);
            }
            m.setHeat(m.heat() + RUSH_HEAT);
            if (pilot instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("hud.headinajar.rush_go"), true);
        }
        if (t < WINDUP) return;
        m.spend(BOOST_FE_TICK);
        if (speed > RAM_SPEED) ram(m, pilot, speed, now);
    }

    /** A point of the model (Blockbench pixels, the machine facing north) in the world, at rest, turned by the body yaw. */
    static Vec3 at(MechaEntity m, Vec3 px) {
        float yaw = m.yBodyRot * Mth.DEG_TO_RAD;
        double lx = -px.x / 16, lz = -px.z / 16;
        return new Vec3(m.getX() + lx * Mth.cos(yaw) - lz * Mth.sin(yaw), m.getY() + px.y / 16, m.getZ() + lx * Mth.sin(yaw) + lz * Mth.cos(yaw));
    }

    /**
     * The buffer beam meets whatever stands in front of it: each creature in the box ahead is knocked up and aside, away
     * from the centre line, and hurt 4 to 8 by the speed, with the pilot as the attacker, once a boost (so a boost knocks a creature aside rather than pushing it along and grinding it down). A player is touched only if
     * that player trusts the pilot (the pogo landing's rule); machines and anything being ridden are left alone (their
     * own clients move them). The Wheelhouse takes nothing.
     */
    static void ram(MechaEntity m, Player pilot, double speed, long now) {
        float yaw = m.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)), left = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
        Vec3 mid = m.position().add(fwd.scale((RAM_NEAR + RAM_FAR) / 2));
        AABB box = new AABB(mid, mid).inflate(1.6, 0, 1.6).expandTowards(0, RAM_HEIGHT, 0);
        boolean hit = false;
        for (LivingEntity e : m.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != m && e != pilot && e.isAlive())) {
            if (m.hasPassenger(e) || e instanceof MechaEntity || e.isPassenger() || e.isVehicle()) continue;
            if (e instanceof Player other && (other.isSpectator() || other.isCreative() || !Hopper.trusts(other, pilot))) continue;
            Vec3 rel = e.position().subtract(m.position());
            double ahead = rel.dot(fwd), side = rel.dot(left);
            if (ahead < RAM_NEAR || ahead > RAM_FAR + e.getBbWidth() / 2 || Math.abs(side) > RAM_HALF_WIDTH + e.getBbWidth() / 2) continue;
            if (e.getY() > m.getY() + RAM_HEIGHT || e.getY() + e.getBbHeight() < m.getY()) continue;
            Long last = m.rammed.get(e.getUUID());
            if (last != null && now - last < RAM_DEBOUNCE) continue;
            m.rammed.put(e.getUUID(), now);
            float damage = (float) (4 + 4 * Mth.clamp((speed - RAM_SPEED) / 0.53, 0, 1));
            e.hurt(m.damageSources().playerAttack(pilot), damage);
            double away = side >= 0 ? 1 : -1;
            Vec3 knock = left.scale(away * 0.9).add(fwd.scale(0.3)).add(0, 0.4, 0);
            e.setDeltaMovement(knock);
            e.hurtMarked = true;
            hit = true;
        }
        m.rammed.values().removeIf(at -> now - at > 200);
        if (!hit) return;
        m.setRamAt(now);
        Hopper.play(m, "rush_ram");
        Vec3 at = at(m, MechaRig.WHEELHOUSE.locators.get("fx_buffer").px());
        m.serverLevel().sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 8, 0.2, 0.15, 0.2, 0.04);
        m.serverLevel().sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.1);
    }
}
