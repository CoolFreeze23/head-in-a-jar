package com.coolfreeze23.headinajar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Optional;

/** Head Office's advancements: one trigger, {@code headinajar:event}, keyed by an event name that the code fires. */
public final class Advance {
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, HeadInAJar.MOD_ID);
    public static final DeferredHolder<CriterionTrigger<?>, Trigger> EVENT = TRIGGERS.register("event", Trigger::new);

    private Advance() {
    }

    public static void register(IEventBus bus) {
        TRIGGERS.register(bus);
    }

    /** Fires {@code event} for a player (safe to call with anything that isn't a real server player). */
    public static void grant(Object player, String event) {
        if (player instanceof ServerPlayer sp && sp.connection != null) EVENT.get().trigger(sp, event);
    }

    /** Fires {@code event} for every player within {@code radius} of a spot. */
    public static void nearby(Level level, Vec3 at, double radius, String event) {
        for (Player p : level.getEntitiesOfClass(Player.class, new AABB(at, at).inflate(radius))) grant(p, event);
    }

    public static class Trigger extends SimpleCriterionTrigger<Trigger.Instance> {
        public record Instance(Optional<ContextAwarePredicate> player, String event) implements SimpleCriterionTrigger.SimpleInstance {
            public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    Codec.STRING.fieldOf("event").forGetter(Instance::event)
            ).apply(i, Instance::new));
        }

        @Override
        public Codec<Instance> codec() {
            return Instance.CODEC;
        }

        public void trigger(ServerPlayer player, String event) {
            trigger(player, instance -> instance.event().equals(event));
        }
    }
}
