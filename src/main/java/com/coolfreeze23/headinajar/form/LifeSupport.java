package com.coolfreeze23.headinajar.form;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.entity.HeadlessBody;
import com.coolfreeze23.headinajar.module.ModuleType;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Keeping a head alive: Cranial Saline drains slowly while the head is out in the world (never while docked or
 * carried), a dry jar loses Jar Integrity, and integrity soaks up damage before the player's health.
 */
public final class LifeSupport {
    private LifeSupport() {
    }

    public static int drainTicks(JarState state) {
        int seconds = Config.SPEC.isLoaded() ? Config.SALINE_DRAIN_SECONDS.get() : 12;
        return seconds * 20 * (state.has(ModuleType.SALINE_RECYCLER) ? 2 : 1);
    }

    /** Whether the head is drying out: it's the player, or a Head Jar standing in the world. */
    public static boolean exposed(ServerPlayer player, JarState state) {
        if (state.form().isJarred()) return true;
        if (state.form() != Form.LINKED || state.carryingHead()) return false;
        HeadRegistry.Head head = HeadRegistry.get(player).head(player.getUUID());
        return head != null && head.where() == HeadRegistry.Where.ENTITY;
    }

    public static void tick(ServerPlayer player, JarState state) {
        if (exposed(player, state)) {
            if (player.tickCount % drainTicks(state) == 0) state.setSaline(state.saline() - 1);
            if (state.saline() <= 0 && player.tickCount % 600 == 0) dehydrate(player, state);
        }
        // Life Sign Monitor: the idle body's health on the jar's HUD
        if (state.form() == Form.JAR && player.tickCount % 20 == 0 && state.body() != null) {
            if (player.serverLevel().getEntity(state.body()) instanceof HeadlessBody body) {
                state.setBodyHealth(body.isCorpse() ? 0 : body.getHealth(), body.getMaxHealth());
            }
        }
    }

    private static void dehydrate(ServerPlayer player, JarState state) {
        player.displayClientMessage(Component.translatable("message.headinajar.dehydrating"), true);
        if (state.integrity() > 0) {
            state.setIntegrity(state.integrity() - 1);
            if (state.integrity() > 0 || state.form().isJarred()) return;
        }
        if (state.form() == Form.LINKED) {
            // the placed head dried out completely
            FormActions.REAL_DEATH.add(player.getUUID());
            player.hurt(player.damageSources().dryOut(), Float.MAX_VALUE);
        } else {
            player.hurt(player.damageSources().dryOut(), 1);
        }
    }

    /** Integrity soaks up damage to a jar first. */
    public static void absorb(Player player, JarState state, LivingIncomingDamageEvent event) {
        if (state.integrity() <= 0 || event.getAmount() <= 0) return;
        float absorbed = Math.min(state.integrity(), event.getAmount());
        if (!player.level().isClientSide) {
            state.setIntegrity(state.integrity() - (int) Math.ceil(absorbed));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GLASS_HIT, SoundSource.PLAYERS, 1, 1.1f);
        }
        event.setAmount(event.getAmount() - absorbed);
        // the Shock Lid zaps whoever hit the jar in melee
        if (state.has(ModuleType.SHOCK_LID) && event.getSource().getDirectEntity() instanceof LivingEntity attacker
                && event.getSource().getDirectEntity() == event.getSource().getEntity() && state.spend(500)) {
            attacker.hurt(player.damageSources().lightningBolt(), 3);
            attacker.knockback(0.6, player.getX() - attacker.getX(), player.getZ() - attacker.getZ());
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEE_STING, SoundSource.PLAYERS, 1, 0.5f);
        }
    }

    /** Uses one refill item on this player's jar: a saline bottle (+250 mB) or a glass patch (+5 integrity). */
    public static boolean refill(ServerPlayer player, JarState state, Item item) {
        if (!state.form().isDecapitated()) return false;
        if (item == ModRegistry.SALINE_BOTTLE.get() && state.saline() < state.maxSaline()) {
            state.setSaline(state.saline() + 250);
            FormActions.give(player, new ItemStack(Items.GLASS_BOTTLE));
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 1, 1);
            return true;
        }
        if (item == ModRegistry.GLASS_PATCH.get() && state.integrity() < state.maxIntegrity()) {
            state.setIntegrity(state.integrity() + 5);
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 1, 1.4f);
            return true;
        }
        return false;
    }
}
