package com.coolfreeze23.headinajar.body;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.ChairSounds;
import com.coolfreeze23.headinajar.entity.HeadlessBody;
import com.coolfreeze23.headinajar.fx.BodySounds;
import com.coolfreeze23.headinajar.fx.Dust;
import com.coolfreeze23.headinajar.fx.Fx;
import com.coolfreeze23.headinajar.host.Hosts;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The server's half of the head-and-body moments: it plays each one's sounds at the tick they belong to and sends the
 * one memo every client draws it from. Nothing here changes what the moment does; the actions themselves stay
 * instant.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID)
public final class BodyServer {
    private record Later(ServerLevel level, long at, Runnable run) {
    }

    private static final List<Later> LATER = new ArrayList<>();

    /** Runs {@code run} on the server {@code ticks} from now (dropped if the level is gone). */
    public static void later(ServerLevel level, int ticks, Runnable run) {
        LATER.add(new Later(level, level.getGameTime() + ticks, run));
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (LATER.isEmpty()) return;
        List<Later> due = new ArrayList<>();
        for (Iterator<Later> it = LATER.iterator(); it.hasNext(); ) {
            Later l = it.next();
            if (l.level.getServer() != event.getServer()) {
                it.remove();
            } else if (l.level.getGameTime() >= l.at) {
                due.add(l);
                it.remove();
            }
        }
        for (Later l : due) l.run.run();
    }

    private static void play(ServerLevel level, Vec3 at, SoundEvent sound, SoundSource source, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, source, volume, pitch);
    }

    /** Where a creature's neck is, in the world. */
    public static Vec3 neck(Entity mob) {
        Vec3 n = Hosts.neck(mob);
        float yaw = (mob instanceof LivingEntity l ? l.yBodyRot : mob.getYRot()) * net.minecraft.util.Mth.DEG_TO_RAD;
        // the host's facing frame (x right, z forward) turned into the world's
        double s = net.minecraft.util.Mth.sin(yaw), c = net.minecraft.util.Mth.cos(yaw);
        return mob.position().add(-n.x * c - n.z * s, n.y, -n.x * s + n.z * c);
    }

    /**
     * The saw takes a head: the creature freezes for 3 ticks (the cork's whine) and the head pops on the third, with
     * the shears' snip; the desk ding follows 0.4 s later. With a jar in the off hand the head arcs into it.
     */
    public static void cut(ServerPlayer cutter, Mob mob, boolean intoJar) {
        ServerLevel level = cutter.serverLevel();
        Vec3 at = neck(mob);
        play(level, at, BodySounds.CORK.get(), SoundSource.NEUTRAL, 1.0f, 1.0f);
        later(level, 3, () -> play(level, at, com.coolfreeze23.headinajar.fx.HouseSounds.SNIP.get(), SoundSource.PLAYERS, 0.8f, 1.2f));
        later(level, 11, () -> play(level, at, com.coolfreeze23.headinajar.fx.HouseSounds.DESK_DING.get(), SoundSource.PLAYERS, 1.0f, 1.0f));
        Fx.send(level, Fx.of(Fx.HOST_CUT, at, cutter.getEyePosition(), cutter.getId(), mob.getId(), intoJar ? 1 : 0));
    }

    /** A jar seats on a host's neck: the bayonet twist and seal, then the host wakes, groggy (its own voice, low). */
    public static void seat(ServerPlayer pilot, Mob host) {
        ServerLevel level = pilot.serverLevel();
        Vec3 at = neck(host);
        play(level, at, BodySounds.BAYONET.get(), SoundSource.NEUTRAL, 1.0f, 0.97f + level.random.nextFloat() * 0.06f);
        Fx.send(level, Fx.of(Fx.HOST_SEAT, at, null, pilot.getId(), host.getId(), 0));
        later(level, 8, () -> groggy(host, 0.65f));
    }

    /**
     * However a hosted jar comes off (sneak, the host dying, a command), its host is heard letting go. Last in line and
     * never for a refused dismount (a flier too high up refuses one every tick sneak is held).
     */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void dismount(net.neoforged.neoforge.event.entity.EntityMountEvent event) {
        if (event.isMounting() || !(event.getEntityMounting() instanceof ServerPlayer player)) return;
        if (com.coolfreeze23.headinajar.form.FormActions.state(player).form() != com.coolfreeze23.headinajar.form.Form.HOSTED) return;
        Entity host = event.getEntityBeingMounted();
        if (host != null && Hosts.isHost(host)) unseat(host);
    }

    /** The jar leaves: the seal pops, the teeth, and the host goes back to sleep. */
    public static void unseat(Entity host) {
        if (!(host.level() instanceof ServerLevel level)) return;
        Vec3 at = neck(host);
        play(level, at, BodySounds.UNSEAT.get(), SoundSource.NEUTRAL, 1.0f, 0.97f + level.random.nextFloat() * 0.06f);
        Fx.send(level, Fx.of(Fx.HOST_UNSEAT, at, null, -1, host.getId(), 0));
        if (host instanceof Mob mob) later(level, 6, () -> groggy(mob, 0.5f));
    }

    /** The host's own ambient sound, quiet and low: waking up, or nodding off. */
    private static void groggy(Mob host, float pitch) {
        if (!host.isAlive() || !(host.level() instanceof ServerLevel level)) return;
        SoundEvent voice = MobVoice.ambient(host);
        if (voice != null) play(level, host.getEyePosition(), voice, host.getSoundSource(), 0.6f, pitch);
    }

    /** Embalming: a short vacuum hiss, the desk ding, and a lavender sparkle seen by everyone near. */
    public static void embalmed(Mob host) {
        if (!(host.level() instanceof ServerLevel level)) return;
        Vec3 at = neck(host);
        play(level, at, com.coolfreeze23.headinajar.fx.HouseSounds.EMBALM.get(), SoundSource.NEUTRAL, 0.6f, 1.2f);
        later(level, 10, () -> play(level, at, com.coolfreeze23.headinajar.fx.HouseSounds.DESK_DING.get(), SoundSource.NEUTRAL, 1.0f, 1.0f));
        Fx.send(level, Fx.of(Fx.HOST_EMBALMED, at, null, -1, host.getId(), 1));
    }

    /** Tells one viewer that this host is embalmed (so its flies stay away), when it comes into view. */
    public static void embalmedSeen(ServerPlayer viewer, Mob host) {
        if (viewer.connection == null || !viewer.connection.hasChannel(Fx.TYPE)) return;
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(viewer, Fx.of(Fx.HOST_EMBALMED, neck(host), null, -1, host.getId(), 0));
    }

    /** A tool worked: the hand's motion for everyone who can see it, and its small sound. */
    public static void tool(ServerPlayer user, Entity target, int kind) {
        ServerLevel level = user.serverLevel();
        Vec3 at = target != null ? target.position().add(0, target.getBbHeight() * 0.6, 0) : user.getEyePosition();
        switch (kind) {
            case Fx.TOOL_SYRINGE -> later(level, 4, () -> play(level, at, BodySounds.PLIP.get(), SoundSource.PLAYERS, 1.0f, 0.95f + level.random.nextFloat() * 0.1f));
            case Fx.TOOL_SUTURE -> play(level, user.position(), com.coolfreeze23.headinajar.fx.HouseSounds.DESK_DING.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
            case Fx.TOOL_WRENCH -> play(level, at, ChairSounds.RATCHET.get(), SoundSource.PLAYERS, 0.9f, 1.0f);
            default -> {
            }
        }
        Fx.send(level, Fx.of(Fx.TOOL, at, null, user.getId(), target == null ? -1 : target.getId(), kind));
    }

    /**
     * A body has died and lies down over 24 ticks: its knees go (6 ticks), it sits back hard (a thud and dust from the
     * ground), and tips onto its back with a smaller bump at tick 18. Its owner gets HR's card.
     */
    public static void corpse(HeadlessBody body) {
        corpse(body, true);
    }

    /** As above; {@code card} false leaves out HR's card (the owner has already been told, in one line). */
    public static void corpse(HeadlessBody body, boolean card) {
        if (!(body.level() instanceof ServerLevel level)) return;
        Vec3 feet = body.position();
        Fx.send(level, Fx.of(Fx.CORPSE, feet, null, -1, body.getId(), 0));
        later(level, 6, () -> {
            play(level, feet, com.coolfreeze23.headinajar.fx.HouseSounds.SLUMP.get(), SoundSource.NEUTRAL, 1.0f, 0.7f);
            Dust.server(level, feet, 8, 1f);
        });
        later(level, 18, () -> play(level, feet, com.coolfreeze23.headinajar.fx.HouseSounds.CLOTHES.get(), SoundSource.NEUTRAL, 0.7f, 0.6f));
        later(level, 20, () -> play(level, feet, BodySounds.POWER_DOWN.get(), SoundSource.NEUTRAL, 0.8f, 0.8f));
        ServerPlayer owner = body.ownerId() == null || level.getServer() == null ? null : level.getServer().getPlayerList().getPlayer(body.ownerId());
        if (owner != null && card) owner.displayClientMessage(Component.translatable("message.headinajar.body.hr_card"), false);
    }

    /** A body left standing powers down; one entered powers up. The beeps come from the collar. */
    public static void power(LivingEntity body, boolean up) {
        if (!(body.level() instanceof ServerLevel level)) return;
        play(level, body.position().add(0, 1.3, 0), up ? BodySounds.POWER_UP.get() : BodySounds.POWER_DOWN.get(), SoundSource.NEUTRAL, 1.0f, 1.0f);
        if (!up) later(level, 8, () -> play(level, body.position(), com.coolfreeze23.headinajar.fx.HouseSounds.SLUMP.get(), SoundSource.NEUTRAL, 0.6f, 0.7f));
    }

    /** A creature's own ambient sound (protected on the mob, so looked up once by name; null when it has none). */
    static final class MobVoice {
        private static java.lang.reflect.Method method;
        private static boolean failed;

        static SoundEvent ambient(Mob mob) {
            if (failed) return null;
            try {
                if (method == null) {
                    method = Mob.class.getDeclaredMethod("getAmbientSound");
                    method.setAccessible(true);
                }
                return (SoundEvent) method.invoke(mob);
            } catch (ReflectiveOperationException | RuntimeException e) {
                failed = true;
                return null;
            }
        }
    }

    private BodyServer() {
    }
}
