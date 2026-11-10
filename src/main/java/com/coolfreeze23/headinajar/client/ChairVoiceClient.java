package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.DoloresVoice;
import com.coolfreeze23.headinajar.block.SeparationChairBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Plays DOLORES's lines on this client: in this client's language, muffled for the patient during the operation, one
 * line at a time per chair (a new line stops the one still playing), in the Voice category, and not at all with the
 * voice option off (the text is shown either way). While she speaks, the chair's motors, hisses, ticks, glugs and
 * sparks play quieter under her; the cut, the latches, the clamp and the ding never do.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID, value = Dist.CLIENT)
public final class ChairVoiceClient {
    private record Speaking(SoundInstance sound, long until, Vec3 at) {
    }

    private static final Map<BlockPos, Speaking> SPEAKING = new HashMap<>();
    /** What was played (game time, event), for the dev client's record. */
    public static final java.util.ArrayDeque<String> PLAYED = new java.util.ArrayDeque<>();

    public static boolean enabled() {
        return !Config.CLIENT_SPEC.isLoaded() || Config.DOLORES_VOICE.get();
    }

    /** The language this client hears: Portuguese for any Portuguese setting, else English. */
    public static String language() {
        String selected = Minecraft.getInstance().getLanguageManager().getSelected();
        return selected != null && selected.startsWith("pt") ? DoloresVoice.PT : DoloresVoice.EN;
    }

    public static void play(DoloresVoice.Say say) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !enabled()) return;
        String lang = language();
        if (!say.lang().isEmpty() && !say.lang().equals(lang)) return;
        DoloresVoice.Line line = DoloresVoice.LINES.get(say.line());
        if (line == null) return;
        // the patient hears the operation's lines through the anaesthetic
        boolean patient = mc.level.getBlockEntity(say.pos()) instanceof SeparationChairBlockEntity chair && chair.running()
                && mc.player.getUUID().equals(chair.patient());
        SoundEvent event = DoloresVoice.event(say.line(), lang, line.muffled() && patient);
        if (event == null) return;
        Speaking before = SPEAKING.remove(say.pos());
        if (before != null) mc.getSoundManager().stop(before.sound());
        Vec3 at = Vec3.atCenterOf(say.pos());
        SoundInstance sound = new SimpleSoundInstance(event, SoundSource.VOICE, 1, 1, SoundInstance.createUnseededRandom(), at.x, at.y, at.z);
        mc.getSoundManager().play(sound);
        SPEAKING.put(say.pos(), new Speaking(sound, mc.level.getGameTime() + line.ticks(lang), at));
        PLAYED.add(mc.level.getGameTime() + " " + event.getLocation());
        if (PLAYED.size() > 64) PLAYED.removeFirst();
    }

    /** True while a chair within a few blocks of {@code at} is speaking. */
    private static boolean speakingNear(double x, double y, double z) {
        var level = Minecraft.getInstance().level;
        if (level == null) return false;
        long now = level.getGameTime();
        SPEAKING.values().removeIf(s -> s.until() < now);
        for (Speaking s : SPEAKING.values()) {
            if (s.at().distanceToSqr(x, y, z) < 9) return true;
        }
        return false;
    }

    /** The chair's own sounds that should sit under her voice play quieter while she speaks. */
    @SubscribeEvent
    public static void duck(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || SPEAKING.isEmpty()) return;
        ResourceLocation id = sound.getLocation();
        if (!id.getNamespace().equals(HeadInAJar.MOD_ID) || !DoloresVoice.DUCK.contains(id.getPath())) return;
        if (speakingNear(sound.getX(), sound.getY(), sound.getZ())) {
            event.setSound(new Ducked(sound, (float) Math.pow(10, DoloresVoice.duckDb / 20)));
        }
    }

    /** A sound played at a fraction of its volume; everything else as the original. */
    private record Ducked(SoundInstance base, float factor) implements SoundInstance {
        @Override
        public ResourceLocation getLocation() {
            return base.getLocation();
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            return base.resolve(manager);
        }

        @Override
        public Sound getSound() {
            return base.getSound();
        }

        @Override
        public SoundSource getSource() {
            return base.getSource();
        }

        @Override
        public boolean isLooping() {
            return base.isLooping();
        }

        @Override
        public boolean isRelative() {
            return base.isRelative();
        }

        @Override
        public int getDelay() {
            return base.getDelay();
        }

        @Override
        public float getVolume() {
            return base.getVolume() * factor;
        }

        @Override
        public float getPitch() {
            return base.getPitch();
        }

        @Override
        public double getX() {
            return base.getX();
        }

        @Override
        public double getY() {
            return base.getY();
        }

        @Override
        public double getZ() {
            return base.getZ();
        }

        @Override
        public Attenuation getAttenuation() {
            return base.getAttenuation();
        }
    }

    private ChairVoiceClient() {
    }
}
