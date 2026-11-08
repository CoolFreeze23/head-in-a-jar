package com.coolfreeze23.headinajar.host;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.Map;

/** Host bodies over time: decay, sync, traits, and what happens around them. */
public final class HostEvents {
    private static final ResourceLocation STIFF = HeadInAJar.id("host_stiff");
    private static final ResourceLocation SYNCED = HeadInAJar.id("host_synced");
    private static final String FILE_PREFIX = "host_file_";

    private HostEvents() {
    }

    @SubscribeEvent
    public static void reload(AddReloadListenerEvent event) {
        event.addListener(new Hosts.Loader());
    }

    @SubscribeEvent
    public static void datapackSync(OnDatapackSyncEvent event) {
        event.getRelevantPlayers().forEach(Network::sendNecks);
    }

    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event) {
        if (Hosts.isHost(event.getTarget()) && event.getEntity() instanceof ServerPlayer viewer) Network.syncHostTo(viewer, event.getTarget());
    }

    @SubscribeEvent
    public static void levelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) HostAbilities.tickWebs(level);
    }

    @SubscribeEvent
    public static void tick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide || !(entity instanceof Mob host) || !Hosts.isHost(host)) return;
        HostData data = Hosts.data(host);
        HostDefinition def = Hosts.definition(host);
        Player pilot = Hosts.pilot(host);
        long time = host.level().getGameTime();
        if (data.primaryCooldown > 0) data.primaryCooldown--;
        if (data.secondaryCooldown > 0) data.secondaryCooldown--;

        // decay: 2% a minute by default, half while embalmed, none with a Neural Lace or for shells
        if (!data.shell && !data.laced) {
            float perTick = (def == null ? 2f : def.decayPerMinute()) / 1200f;
            if (time < data.embalmedUntil) perTick /= 2;
            data.freshness -= perTick;
            if (data.freshness <= 25 && time % 20 == 0 && host.level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.MYCELIUM, host.getX(), host.getY() + host.getBbHeight(), host.getZ(), 3, 0.3, 0.3, 0.3, 0);
            }
            if (data.freshness <= 0) {
                // it falls apart: normal loot, and the jar pops out
                host.ejectPassengers();
                host.hurt(host.damageSources().genericKill(), Float.MAX_VALUE);
                return;
            }
        }
        // sync: 1% per 12 s of piloting
        if (pilot != null && !data.shell && data.sync < 100) {
            data.sync = Math.min(100, data.sync + 1f / 240f);
            if (data.sync >= 100) com.coolfreeze23.headinajar.Advance.grant(pilot, "synced");
        }

        float sync = data.effectiveSync();
        attributes(host, def, data, sync);
        if (def != null && sync >= 25) traits(host, def, pilot);
        if (pilot != null && data.shell) FormActions.state(pilot).spend(20);
        if (time % 20 == 0) Network.syncHost(host);
    }

    private static void attributes(Mob host, HostDefinition def, HostData data, float sync) {
        float slow = data.shell ? 0 : data.freshness < 25 ? -0.3f : data.freshness < 50 ? -0.15f : 0;
        set(host, Attributes.MOVEMENT_SPEED, STIFF, slow, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        float bonus = sync >= 100 ? 0.1f : 0;
        set(host, Attributes.MAX_HEALTH, SYNCED, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        set(host, Attributes.ATTACK_DAMAGE, SYNCED, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        if (def == null) return;
        for (Map.Entry<String, Double> e : def.attributes().entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(e.getKey());
            if (id == null) continue;
            Holder<Attribute> attr = BuiltInRegistries.ATTRIBUTE.getHolder(id).orElse(null);
            if (attr != null) set(host, attr, HeadInAJar.id(FILE_PREFIX + id.getPath().replace('.', '_')), e.getValue(), AttributeModifier.Operation.ADD_VALUE);
        }
    }

    private static void set(LivingEntity e, Holder<Attribute> attribute, ResourceLocation id, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = e.getAttribute(attribute);
        if (inst == null) return;
        if (amount == 0) {
            if (inst.hasModifier(id)) inst.removeModifier(id);
        } else {
            AttributeModifier existing = inst.getModifier(id);
            if (existing == null || existing.amount() != amount) inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
        }
    }

    private static void traits(Mob host, HostDefinition def, Player pilot) {
        if (def.has("fire_immune")) host.clearFire();
        if (def.has("water_breathing")) host.setAirSupply(host.getMaxAirSupply());
        if (def.has("knockback_immune")) set(host, Attributes.KNOCKBACK_RESISTANCE, HeadInAJar.id("host_knockback"), 1, AttributeModifier.Operation.ADD_VALUE);
        if (pilot != null && def.has("night_vision")) {
            MobEffectInstance nv = pilot.getEffect(MobEffects.NIGHT_VISION);
            if (nv == null || nv.getDuration() < 220) pilot.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, true, false, false));
        }
        if (pilot != null && def.has("speed_aura")) pilot.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 60, 0, true, false, false));
    }

    // --- around hosts -----------------------------------------------------------------------------

    @SubscribeEvent
    public static void hostHurt(LivingIncomingDamageEvent event) {
        LivingEntity e = event.getEntity();
        if (!Hosts.isHost(e)) return;
        HostDefinition def = Hosts.definition(e);
        if (def == null || Hosts.data(e).effectiveSync() < 25) return;
        var src = event.getSource();
        if (def.has("fire_immune") && src.is(DamageTypeTags.IS_FIRE)) event.setCanceled(true);
        if (def.has("no_fall_damage") && src.is(DamageTypeTags.IS_FALL)) event.setCanceled(true);
        if (def.has("water_breathing") && src.is(DamageTypes.DROWN)) event.setCanceled(true);
    }

    /** Creatures a host is "one of" leave it and its pilot alone. */
    @SubscribeEvent
    public static void target(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target == null) return;
        Entity host = Hosts.isHost(target) ? target : target.getVehicle() != null && Hosts.isHost(target.getVehicle()) ? target.getVehicle() : null;
        if (host == null || Hosts.data(host).effectiveSync() < 25) return;
        HostDefinition def = Hosts.definition(host);
        if (def == null) return;
        for (String tag : def.neutralTo()) {
            boolean match = tag.startsWith("#")
                    ? event.getEntity().getType().is(TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, ResourceLocation.parse(tag.substring(1))))
                    : BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString().equals(tag);
            if (match) {
                event.setCanceled(true);
                return;
            }
        }
    }

    /** The saw acts before the mob's own use (a villager would otherwise open its trades). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void saw(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        if (!event.getItemStack().is(ModRegistry.SURGICAL_SAW.get()) || !(event.getTarget() instanceof Mob mob)) return;
        event.setCanceled(true);
        if (event.getEntity() instanceof ServerPlayer player) {
            event.setCancellationResult(com.coolfreeze23.headinajar.item.SurgicalSawItem.saw(player, event.getItemStack(), mob, event.getHand()));
        } else {
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        }
    }

    /** A hosted jar attacks with its host. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void attack(AttackEntityEvent event) {
        Player player = event.getEntity();
        JarState state = FormActions.state(player);
        if (state.form() != Form.HOSTED || !(player.getVehicle() instanceof Mob host)) return;
        event.setCanceled(true);
        if (player.level().isClientSide || event.getTarget() == host) return;
        double reach = host.getBbWidth() * 0.5 + 2.5;
        if (event.getTarget().distanceTo(host) <= reach) {
            host.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            if (host.getAttribute(Attributes.ATTACK_DAMAGE) != null) host.doHurtTarget(event.getTarget());
            else event.getTarget().hurt(host.damageSources().mobAttack(host), 2);
        }
    }
}
