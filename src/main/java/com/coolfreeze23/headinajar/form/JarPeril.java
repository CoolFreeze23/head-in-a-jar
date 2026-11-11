package com.coolfreeze23.headinajar.form;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.feedback.Warnings;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What a head is told before its glass gives out, and how its end is announced. Jar Integrity soaks up damage first
 * ({@link LifeSupport#absorb}); every knock it soaks is said to the jar's owner with what is left and what mends it, and
 * louder once three points or fewer remain; once the glass is gone, that the next hits are real. A real death of a head
 * in a jar is announced in the company's own words, naming whatever broke it.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID)
public final class JarPeril {
    /** The jar itself gave out (a jar, a jar on a host, in a socket or a cockpit). Only ever a death message. */
    public static final ResourceKey<DamageType> JAR_BROKEN = key("jar_broken");
    /** The same, with whoever broke it named. */
    public static final ResourceKey<DamageType> JAR_SMASHED = key("jar_smashed");
    /** A head set down in the world was lost, jar and all, while its owner was out in their body. */
    public static final ResourceKey<DamageType> HEAD_LOST = key("head_lost");
    /** The same, by fire or lava. */
    public static final ResourceKey<DamageType> HEAD_BURNED = key("head_burned");
    /** At or under this many points of glass the warnings say the next knock may be the last. */
    public static final int LOW = 3;
    /** Ticks between two of the same warning while it keeps happening. */
    public static final int EVERY = 40;

    private static final Map<UUID, Integer> BEFORE = new HashMap<>();

    private JarPeril() {
    }

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, HeadInAJar.id(name));
    }

    public static DamageSource source(Entity in, ResourceKey<DamageType> type, Entity direct, Entity cause) {
        Holder<DamageType> holder = in.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type);
        return new DamageSource(holder, direct, cause);
    }

    /** The glass's state in a line's arguments: points left, out of how many, and what mends it. */
    public static Object[] glass(JarState state) {
        return new Object[]{state.integrity(), state.maxIntegrity(), Component.translatable("item.headinajar.glass_patch")};
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void before(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.hasData(ModRegistry.JAR_STATE)) {
            BEFORE.put(player.getUUID(), FormActions.state(player).integrity());
        }
    }

    /** After the jar's own rules ran: what the hit did to the glass, said to the jar. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void after(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.hasData(ModRegistry.JAR_STATE)) return;
        Integer before = BEFORE.remove(player.getUUID());
        JarState state = FormActions.state(player);
        if (before == null || event.isCanceled() || !state.form().isJarred()) return;
        if (state.integrity() < before) {
            if (state.integrity() <= LOW) Warnings.danger(player, EVERY, "jar.glass_low", glass(state));
            else Warnings.warn(player, EVERY, "jar.glass_hit", glass(state));
        } else if (state.integrity() <= 0 && event.getAmount() > 0) {
            Warnings.danger(player, EVERY, "jar.glass_gone", Component.translatable("block.headinajar.life_support_cradle"));
        }
    }

    /**
     * A real death of a head in a jar reads as one: the jar broke, and who or what broke it. Runs last, once the death
     * is known to stand, and only adds the line the death screen and the chat read (nothing else is changed).
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void death(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.hasData(ModRegistry.JAR_STATE)) return;
        DamageSource source = event.getSource();
        if (!FormActions.state(player).form().isJarred() || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || source.is(DamageTypes.DRY_OUT)) return;
        boolean by = source.getEntity() != null || source.getDirectEntity() != null;
        player.getCombatTracker().recordDamage(source(player, by ? JAR_SMASHED : JAR_BROKEN, source.getDirectEntity(), source.getEntity()), 0);
    }
}
