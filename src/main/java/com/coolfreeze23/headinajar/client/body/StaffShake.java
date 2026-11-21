package com.coolfreeze23.headinajar.client.body;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.fx.Fx;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

import java.util.Set;

/**
 * A creature the saw refused shakes its head no ({@link Fx#STAFF_SHAKE}): 25 degrees either way, period 6 ticks, dying
 * away over 12, as a placed head shakes in its jar. Added to the head's turn just for its draw and taken off again
 * after; a creature whose model has no turning head turns its whole body instead. A villager says no its own way (the
 * server shakes it), and the bosses do not shake.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID, value = Dist.CLIENT)
public final class StaffShake {
    /** Creatures whose models ignore the head's turn: their bodies shake. */
    private static final Set<EntityType<?>> BODY = Set.of(EntityType.SLIME, EntityType.MAGMA_CUBE, EntityType.GHAST, EntityType.SILVERFISH,
            EntityType.ENDERMITE, EntityType.BEE);
    private static final int TICKS = 12;
    /** Entity id to the game time its shake began (the client's world). */
    private static final Int2LongOpenHashMap STARTED = new Int2LongOpenHashMap();
    /** The creature being drawn shaken now, and what its turns were before. */
    private static LivingEntity shaking;
    private static float bodyRot, bodyRotO, headRot, headRotO;

    private StaffShake() {
    }

    static void start(Fx fx) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (STARTED.size() > 256) STARTED.clear();
        STARTED.put(fx.entityB(), mc.level.getGameTime());
    }

    /** The shake's angle now (degrees), {@code t} ticks in. */
    public static float angle(double t) {
        if (t < 0 || t >= TICKS) return 0;
        return (float) (25 * Math.sin(2 * Math.PI * t / 6) * (1 - t / TICKS));
    }

    // last, so nothing after it can cancel the draw (and its Post) once the turn is changed
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void before(RenderLivingEvent.Pre<?, ?> event) {
        if (STARTED.isEmpty()) return;
        LivingEntity e = event.getEntity();
        if (!STARTED.containsKey(e.getId())) return;
        var mc = Minecraft.getInstance();
        // the creature itself, not a stand-in drawn somewhere with the same number
        if (mc.level == null || e.level() != mc.level || mc.level.getEntity(e.getId()) != e) return;
        double t = mc.level.getGameTime() + event.getPartialTick() - STARTED.get(e.getId());
        if (t >= TICKS) {
            STARTED.remove(e.getId());
            return;
        }
        if (e instanceof net.minecraft.world.entity.npc.AbstractVillager || e.getType() == EntityType.ENDER_DRAGON || e.getType() == EntityType.WITHER) return;
        float a = angle(t);
        shaking = e;
        bodyRot = e.yBodyRot;
        bodyRotO = e.yBodyRotO;
        headRot = e.yHeadRot;
        headRotO = e.yHeadRotO;
        // a host with no head on its neck has nothing to turn: its body shakes (a seated secondee's head shakes in its jar)
        if (BODY.contains(e.getType()) || com.coolfreeze23.headinajar.host.Hosts.isHost(e)
                && !com.coolfreeze23.headinajar.client.second.SecondeeClient.seated(e)) {
            e.yBodyRot += a;
            e.yBodyRotO += a;
        }
        // the head turns with the body (or, for the rest, alone): its turn relative to the body is what the model draws
        e.yHeadRot += a;
        e.yHeadRotO += a;
    }

    @SubscribeEvent
    public static void after(RenderLivingEvent.Post<?, ?> event) {
        if (shaking == null || event.getEntity() != shaking) return;
        shaking.yBodyRot = bodyRot;
        shaking.yBodyRotO = bodyRotO;
        shaking.yHeadRot = headRot;
        shaking.yHeadRotO = headRotO;
        shaking = null;
    }
}
