package com.coolfreeze23.headinajar.staff;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.LifeSupportJarItem;
import com.coolfreeze23.headinajar.item.SurgicalSawItem;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.Tags;

import java.util.UUID;

/**
 * The saw's table: what each creature has to give, read before anything spins up. Babies, the Ender Dragon, the Wither,
 * other bosses, named creatures, other people's pets and hosts each refuse with their own line; the creatures that are
 * all head need a jar (the jar holds the whole creature); big heads need a big enough jar; a snow golem gives up its
 * pumpkin first. The table's rows are entity type tags, so a compat datapack can add creatures from other mods.
 */
public final class SawTable {
    public static final TagKey<EntityType<?>> HEAD_ONLY = tag("saw/head_only"), REINFORCED = tag("saw/reinforced_jar"),
            EXECUTIVE = tag("saw/executive_jar"), NEVER = tag("saw/never");

    /** The creature the saw is taking whole into a jar this moment: its drops are cancelled and it is removed, not split. */
    public static UUID swallow;

    private static TagKey<EntityType<?>> tag(String path) {
        return TagKey.create(Registries.ENTITY_TYPE, HeadInAJar.id(path));
    }

    /** What the table says: go ahead, take the pumpkin first, or a refusal with its reason and arguments. */
    public record Verdict(String reason, Object[] args, boolean pumpkin) {
        public static final Verdict OK = new Verdict(null, new Object[0], false);
        public static final Verdict PUMPKIN = new Verdict(null, new Object[0], true);

        public boolean ok() {
            return reason == null && !pumpkin;
        }

        public boolean refused() {
            return reason != null;
        }

        static Verdict no(String reason, Object... args) {
            return new Verdict(reason, args, false);
        }
    }

    private SawTable() {
    }

    /** A jar's size: 1 for the Life Support and Compact Jars, 2 for the Reinforced, 3 for the Executive; 0 for no jar. */
    public static int rank(ItemStack jar) {
        if (!(jar.getItem() instanceof LifeSupportJarItem item)) return 0;
        return item.slots() >= 7 ? 3 : item.slots() >= 5 ? 2 : 1;
    }

    /** The jar a creature's head needs (1 any, 2 Reinforced or bigger, 3 Executive), read at the cut. */
    public static int needs(Mob mob) {
        if (mob instanceof Slime slime) {
            // a slime and a magma cube are as big as they are: size 1 any jar, 2 Reinforced, 4 or more Executive
            int size = slime.getSize();
            if (size >= 4) return 3;
            if (size >= 2) return 2;
        }
        if (mob.getType().is(EXECUTIVE)) return 3;
        if (mob.getType().is(REINFORCED)) return 2;
        return 1;
    }

    /** The jar a kept head needs, from its type and its record (a slime's saved {@code Size} is its size less one). */
    public static int needs(EntityType<?> type, net.minecraft.nbt.CompoundTag record) {
        if (type == EntityType.SLIME || type == EntityType.MAGMA_CUBE) {
            int size = record.getInt("Size") + 1;
            if (size >= 4) return 3;
            if (size >= 2) return 2;
        }
        if (type.is(EXECUTIVE)) return 3;
        if (type.is(REINFORCED)) return 2;
        return 1;
    }

    /** A jar's size by its item id (as a kept head records it); 1 for anything unknown. */
    public static int rank(String jar) {
        var id = jar == null ? null : net.minecraft.resources.ResourceLocation.tryParse(jar);
        int rank = id == null ? 0 : rank(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id)));
        return Math.max(1, rank);
    }

    /** Whether this creature is all head (no body for the saw to leave). */
    public static boolean headOnly(Mob mob) {
        return mob.getType().is(HEAD_ONLY);
    }

    /**
     * The table's answer for this creature, this cutter and the off-hand item. With {@code full} false only the rules
     * about who may be cut are read (the jar and the health are left out), which is what {@link Hosts#canSaw} asks.
     */
    public static Verdict verdict(Mob mob, Player by, ItemStack offhand, boolean full) {
        EntityType<?> type = mob.getType();
        if (type == EntityType.ENDER_DRAGON) return Verdict.no("saw.dragon");
        if (type == EntityType.WITHER) return Verdict.no("saw.wither");
        if (type.is(Tags.EntityTypes.BOSSES)) return Verdict.no("saw.boss");
        if (type == ModRegistry.HEADLESS_BODY.get() || type == ModRegistry.HEAD_JAR.get() || type.is(Hosts.NOT_A_HOST)) {
            return Verdict.no("item.on_colleague", ModRegistry.SURGICAL_SAW.get().getDescription());
        }
        if (mob instanceof com.coolfreeze23.headinajar.entity.MechaEntity || mob instanceof com.coolfreeze23.headinajar.entity.ShellEntity) {
            return Verdict.no("saw.machine");
        }
        // living staff are let go before anything else is done to them
        if (com.coolfreeze23.headinajar.staff.work.Workforce.isWorker(mob)) return Verdict.no("staff.saw", com.coolfreeze23.headinajar.staff.work.Workforce.name(mob));
        if (Hosts.isHost(mob)) return Verdict.no("saw.host");
        if (type.is(NEVER)) return Verdict.no("saw.never");
        if (mob.hasCustomName()) return Verdict.no("saw.named");
        if (mob instanceof TamableAnimal tame && tame.isTame() && tame.getOwnerUUID() != null && !tame.getOwnerUUID().equals(by.getUUID())) {
            return Verdict.no("saw.pet", ownerName(by, tame.getOwnerUUID()));
        }
        if (mob.isBaby() && !com.coolfreeze23.headinajar.Config.get(com.coolfreeze23.headinajar.Config.ALLOW_BABIES, false)) return Verdict.no("saw.baby");
        if (!full) return Verdict.OK;
        if (mob instanceof SnowGolem golem && golem.hasPumpkin()) return Verdict.PUMPKIN;
        int rank = rank(offhand), needs = needs(mob);
        if (rank == 0 && headOnly(mob)) return Verdict.no(jarOnlyReason(type));
        if (rank > 0 && rank < needs) {
            return Verdict.no(needs >= 3 ? "saw.jar_small.executive" : "saw.jar_small.reinforced", type.getDescription());
        }
        if (mob.getHealth() > mob.getMaxHealth() * SurgicalSawItem.MAX_HEALTH_FRACTION) return Verdict.no("saw.too_lively");
        return Verdict.OK;
    }

    /** Each all-head creature's own line; a creature a compat datapack adds gets the plain one. */
    static String jarOnlyReason(EntityType<?> type) {
        if (type == EntityType.SLIME || type == EntityType.MAGMA_CUBE) return "saw.jar_only.slime";
        if (type == EntityType.SHULKER) return "saw.jar_only.shulker";
        if (type == EntityType.VEX) return "saw.jar_only.vex";
        if (type == EntityType.BEE) return "saw.jar_only.bee";
        if (type == EntityType.SILVERFISH || type == EntityType.ENDERMITE) return "saw.jar_only.small";
        if (type == EntityType.TADPOLE) return "saw.jar_only.tadpole";
        return "saw.jar_only";
    }

    /** A pet's owner as the line names them: online, then the server's profile cache, else "another employee". */
    static Object ownerName(Player by, UUID owner) {
        var server = by.getServer();
        if (server != null) {
            ServerPlayer online = server.getPlayerList().getPlayer(owner);
            if (online != null) return online.getGameProfile().getName();
            var cache = server.getProfileCache();
            if (cache != null) {
                var profile = cache.get(owner);
                if (profile.isPresent()) return profile.get().getName();
            }
        }
        return Component.translatable("staff.headinajar.someone");
    }
}
