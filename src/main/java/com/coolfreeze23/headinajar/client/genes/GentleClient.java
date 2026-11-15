package com.coolfreeze23.headinajar.client.genes;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.fx.GeneSounds;
import com.coolfreeze23.headinajar.genetics.Gene;
import com.coolfreeze23.headinajar.genetics.GeneArchive;
import com.coolfreeze23.headinajar.genetics.GeneEffects;
import com.coolfreeze23.headinajar.genetics.GeneNumbers;
import com.coolfreeze23.headinajar.genetics.GentleGenes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The gentler genes on the client: the {@code geneQuirks} option (other people's moos, oinks, croaks, sneezes and sobs
 * muted), Roll Up's still legs, Keen Nose's scent trails and dust puffs, and Bamboo Diet's sitting down to eat.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID, value = Dist.CLIENT)
public final class GentleClient {
    private GentleClient() {
    }

    /**
     * {@code geneQuirks} off: a quirk sound not played at the listener's own body is not played. The carrier hears its
     * own (sounds are sent from where the body stands); the bystander does not.
     */
    @SubscribeEvent
    public static void sound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || !GeneSounds.quirk(sound.getLocation())) return;
        if (!Config.CLIENT_SPEC.isLoaded() || Config.GENE_QUIRKS.get()) return;
        Player me = Minecraft.getInstance().player;
        if (me == null) return;
        // a jar riding a host hears its own host as itself
        double x = me.getX(), y = me.getY(), z = me.getZ();
        double near = me.getVehicle() != null ? 9 : 4;
        double dx = sound.getX() - x, dy = sound.getY() - y, dz = sound.getZ() - z;
        if (dx * dx + dy * dy + dz * dz > near) event.setSound(null);
    }

    /** Curled up (Roll Up), the legs do not answer: only Sneak is kept, so letting go of it uncurls. */
    @SubscribeEvent
    public static void movement(MovementInputUpdateEvent event) {
        if (!GentleGenes.curled(event.getEntity())) return;
        Input input = event.getInput();
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.up = input.down = input.left = input.right = false;
        input.jumping = false;
    }

    /** Players drawn sitting this frame (Bamboo Diet), whose pose stack is popped after the draw. */
    private static final Set<Player> SITTING = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Bamboo Diet: sat down to eat, the body is drawn lower (the legs go forward, as when riding: see the model mixin). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void sitPre(RenderPlayerEvent.Pre event) {
        if (!GentleGenes.sitsToEat(event.getEntity())) return;
        event.getPoseStack().pushPose();
        event.getPoseStack().translate(0, -0.6, 0);
        SITTING.add(event.getEntity());
    }

    @SubscribeEvent
    public static void sitPost(RenderPlayerEvent.Post event) {
        if (SITTING.remove(event.getEntity())) event.getPoseStack().popPose();
    }

    /** Per species: whether it carries a gene at all (the library decides, each time, whether it is read). */
    private static final Map<EntityType<?>, List<Gene>> GENES = new WeakHashMap<>();

    private static boolean unread(LivingEntity creature) {
        List<Gene> genes = GENES.computeIfAbsent(creature.getType(), t -> Gene.of(BuiltInRegistries.ENTITY_TYPE.getKey(t).toString()));
        for (Gene g : genes) if (GeneArchive.clientLibrary.getOrDefault(g, 0f) < 100f) return true;
        return false;
    }

    /** Keen Nose, every 2 s: a scent where each creature with an unfinished gene stands, and dust off suspicious blocks. */
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        Player me = mc.player;
        var level = mc.level;
        if (me == null || level == null || mc.isPaused() || me.tickCount % GeneNumbers.NOSE_EVERY != 0) return;
        if (!GeneEffects.has(me, Gene.KEEN_NOSE)) return;
        var scent = GentleGenes.SCENT.get();
        for (LivingEntity c : level.getEntitiesOfClass(LivingEntity.class, me.getBoundingBox().inflate(GeneNumbers.NOSE_CREATURES),
                o -> o != me && o.isAlive() && !(o instanceof Player) && o.distanceToSqr(me) <= GeneNumbers.NOSE_CREATURES * GeneNumbers.NOSE_CREATURES)) {
            if (!unread(c)) continue;
            for (int i = 0; i < 3; i++) {
                level.addParticle(scent, c.getX() + (me.getRandom().nextDouble() - 0.5) * c.getBbWidth(), c.getY() + 0.1 + me.getRandom().nextDouble() * 0.3,
                        c.getZ() + (me.getRandom().nextDouble() - 0.5) * c.getBbWidth(), 0, 0, 0);
            }
        }
        int r = GeneNumbers.NOSE_BLOCKS;
        int x0 = ((int) Math.floor(me.getX()) - r) >> 4, x1 = ((int) Math.floor(me.getX()) + r) >> 4;
        int z0 = ((int) Math.floor(me.getZ()) - r) >> 4, z1 = ((int) Math.floor(me.getZ()) + r) >> 4;
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof BrushableBlockEntity) || be.getBlockPos().distToCenterSqr(me.position()) > r * r) continue;
                    var at = be.getBlockPos();
                    level.addParticle(ParticleTypes.DUST_PLUME, at.getX() + 0.5, at.getY() + 1.05, at.getZ() + 0.5, 0, 0.03, 0);
                }
            }
        }
    }
}
