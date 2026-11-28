package com.coolfreeze23.headinajar.host;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

import java.util.HashMap;
import java.util.Map;

/** Host bodies: which mobs are hosts, their data files, and where a jar sits on each. */
public final class Hosts {
    public static final TagKey<net.minecraft.world.entity.EntityType<?>> NOT_A_HOST =
            TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, HeadInAJar.id("not_a_host"));

    /** Server: the definitions loaded from the data files. */
    private static Map<ResourceLocation, HostDefinition> definitions = Map.of();
    private static Map<ResourceLocation, Vec3> clientNecks = Map.of();
    /** Client: the server's definitions (sent on login and reload), so the client that steers a host knows how it moves. */
    private static Map<ResourceLocation, HostDefinition> clientDefinitions = Map.of();

    public static HostData data(Entity entity) {
        return entity.getData(ModRegistry.HOST_DATA);
    }

    public static boolean isHost(Entity entity) {
        return entity instanceof Mob && entity.hasData(ModRegistry.HOST_DATA) && entity.getData(ModRegistry.HOST_DATA).host;
    }

    /**
     * The sync the body's traits, Ability, Secondary and 100% bonus open with: a seconded host's team sync (0.7.1: a mob
     * head earns it at work, whatever a jar once earned riding the body); for every other host its sync as always
     * ({@link HostData#effectiveSync}: a shell is always fully synced).
     */
    public static float bodySync(Entity host) {
        if (com.coolfreeze23.headinajar.staff.second.Secondees.seated(host)) return com.coolfreeze23.headinajar.staff.second.Secondees.teamSync(host);
        return data(host).effectiveSync();
    }

    /** The creature's data file, on either side (a client reads the copy the server sent). */
    public static HostDefinition definition(Entity entity) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        // single player shares the server's map until the copy arrives; a dedicated client has only the copy
        if (entity.level().isClientSide && !clientDefinitions.isEmpty()) return clientDefinitions.get(id);
        return definitions.get(id);
    }

    public static void setClientDefinitions(Map<ResourceLocation, HostDefinition> defs) {
        clientDefinitions = Map.copyOf(defs);
    }

    /** A creature that never had a head (the Headless Mule): a jar climbs on without the saw. */
    public static boolean headless(Entity entity) {
        HostDefinition def = entity instanceof Mob ? definition(entity) : null;
        return def != null && def.has("headless");
    }

    public static Map<ResourceLocation, HostDefinition> definitions() {
        return definitions;
    }

    public static void setClientNecks(Map<ResourceLocation, Vec3> necks) {
        clientNecks = necks;
    }

    public static Map<ResourceLocation, Vec3> necks() {
        Map<ResourceLocation, Vec3> out = new HashMap<>();
        definitions.forEach((id, def) -> {
            if (def.neckOffset() != null) out.put(id, def.neckOffset());
        });
        return out;
    }

    /** The neck relative to the host's feet, in the host's facing frame (x right, y up, z forward). */
    public static Vec3 neck(Entity host) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(host.getType());
        Vec3 offset = host.level().isClientSide ? clientNecks.get(id) : definitions.get(id) == null ? null : definitions.get(id).neckOffset();
        if (offset != null) return offset.scale(host instanceof LivingEntity l ? l.getScale() : 1);
        // default: humanoid-tall mobs carry the jar where the head was; others on top, a little forward
        float h = host.getBbHeight(), w = host.getBbWidth();
        return h > w * 1.5f ? new Vec3(0, h - 0.5, 0) : new Vec3(0, h * 0.85, w * 0.35);
    }

    /** Whether the saw may take this mob at all (its table's rules about who; the jar and the health are not asked). */
    public static boolean canSaw(Mob mob, Player by) {
        return com.coolfreeze23.headinajar.staff.SawTable.verdict(mob, by, net.minecraft.world.item.ItemStack.EMPTY, false).ok();
    }

    /** The jar riding this host, if any. */
    public static Player pilot(Entity host) {
        if (host.getFirstPassenger() instanceof Player player) {
            Form form = player.getData(ModRegistry.JAR_STATE).form();
            if (form == Form.HOSTED || form == Form.PILOT) return player;
        }
        return null;
    }

    /** Loads {@code data/<ns>/headinajar/host/*.json}. */
    public static class Loader extends SimpleJsonResourceReloadListener {
        public Loader() {
            super(new com.google.gson.Gson(), "headinajar/host");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, HostDefinition> out = new HashMap<>();
            var ops = makeConditionalOps();
            files.forEach((file, json) -> {
                // a file whose neoforge:conditions fail (a creature that is not registered) is skipped, as other data is
                if (!net.neoforged.neoforge.common.conditions.ICondition.conditionsMatched(ops, json)) return;
                HostDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                        .resultOrPartial(err -> HeadInAJar.LOGGER.warn("Host file {}: {}", file, err))
                        .ifPresent(def -> out.put(def.entity(), def));
            });
            definitions = Map.copyOf(out);
            HeadInAJar.LOGGER.info("Loaded {} host definitions", out.size());
        }
    }

    private Hosts() {
    }
}
