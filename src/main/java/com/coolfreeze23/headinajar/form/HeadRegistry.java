package com.coolfreeze23.headinajar.form;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Where every decapitated player's head and idle body are, and who they trust. A player's head is never an item:
 * it is the player, an entity, a Cradle's content or carried. Saved with the overworld.
 */
public final class HeadRegistry extends SavedData {
    public enum Where {
        /** The player is the jar (JAR, HOSTED, PILOT, SOCKET). */
        PLAYER,
        /** A Head Jar entity in the world. */
        ENTITY,
        /** Docked in a Life Support Cradle. */
        CRADLE,
        /** In the player's own Head Satchel. */
        CARRIED
    }

    /** A place in a dimension. */
    public record Spot(ResourceKey<Level> dimension, Vec3 pos) {
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("dim", dimension.location().toString());
            tag.putDouble("x", pos.x);
            tag.putDouble("y", pos.y);
            tag.putDouble("z", pos.z);
            return tag;
        }

        static Spot load(CompoundTag tag) {
            return new Spot(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("dim"))),
                    new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z")));
        }

        public static Spot of(net.minecraft.world.entity.Entity entity) {
            return new Spot(entity.level().dimension(), entity.position());
        }
    }

    /** Where a head is. {@code entity} is the Head Jar entity's id when {@code where} is ENTITY. */
    public record Head(Where where, Spot spot, UUID entity) {
    }

    /** An idle body: a Headless Body or, once it has died, a Corpse. */
    public record Body(UUID entity, Spot spot, boolean corpse) {
    }

    private static final String NAME = "headinajar_heads";
    private final Map<UUID, Head> heads = new HashMap<>();
    private final Map<UUID, Body> bodies = new HashMap<>();
    private final Map<UUID, Set<UUID>> trust = new HashMap<>();

    public static HeadRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(HeadRegistry::new, HeadRegistry::load, null), NAME);
    }

    public static HeadRegistry get(Player player) {
        return get(player.getServer());
    }

    public Head head(UUID player) {
        return heads.get(player);
    }

    public void setHead(UUID player, Head head) {
        if (head == null) heads.remove(player);
        else heads.put(player, head);
        setDirty();
    }

    public Body body(UUID player) {
        return bodies.get(player);
    }

    public void setBody(UUID player, Body body) {
        if (body == null) bodies.remove(player);
        else bodies.put(player, body);
        setDirty();
    }

    /** Moves a body's recorded position without marking a structural change. */
    public void moveBody(UUID player, UUID entity, Spot spot) {
        Body body = bodies.get(player);
        if (body != null && body.entity().equals(entity) && !body.spot().equals(spot)) {
            bodies.put(player, new Body(entity, spot, body.corpse()));
            setDirty();
        }
    }

    public void moveHead(UUID player, UUID entity, Spot spot) {
        Head head = heads.get(player);
        if (head != null && head.where() == Where.ENTITY && entity.equals(head.entity()) && !head.spot().equals(spot)) {
            heads.put(player, new Head(Where.ENTITY, spot, entity));
            setDirty();
        }
    }

    /** Clears everything about a player who is whole again (or died for real). */
    public void clear(UUID player) {
        heads.remove(player);
        bodies.remove(player);
        setDirty();
    }

    public boolean trusts(UUID owner, UUID other) {
        return owner.equals(other) || trust.getOrDefault(owner, Set.of()).contains(other);
    }

    /** Owner, a trusted player, or a teammate. */
    public boolean trusts(UUID owner, Player other) {
        if (trusts(owner, other.getUUID())) return true;
        Player ownerPlayer = other.getServer() == null ? null : other.getServer().getPlayerList().getPlayer(owner);
        return ownerPlayer != null && ownerPlayer.isAlliedTo(other);
    }

    public Set<UUID> trusted(UUID owner) {
        return trust.getOrDefault(owner, Set.of());
    }

    public boolean setTrust(UUID owner, UUID other, boolean trusted) {
        Set<UUID> set = trust.computeIfAbsent(owner, k -> new HashSet<>());
        boolean changed = trusted ? set.add(other) : set.remove(other);
        if (changed) setDirty();
        return changed;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag headList = new ListTag();
        heads.forEach((id, head) -> {
            CompoundTag t = head.spot().save();
            t.putUUID("player", id);
            t.putString("where", head.where().name());
            if (head.entity() != null) t.putUUID("entity", head.entity());
            headList.add(t);
        });
        tag.put("heads", headList);
        ListTag bodyList = new ListTag();
        bodies.forEach((id, body) -> {
            CompoundTag t = body.spot().save();
            t.putUUID("player", id);
            t.putUUID("entity", body.entity());
            t.putBoolean("corpse", body.corpse());
            bodyList.add(t);
        });
        tag.put("bodies", bodyList);
        ListTag trustList = new ListTag();
        trust.forEach((id, set) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("player", id);
            ListTag others = new ListTag();
            for (UUID other : set) others.add(NbtUtils.createUUID(other));
            t.put("trusted", others);
            trustList.add(t);
        });
        tag.put("trust", trustList);
        return tag;
    }

    private static HeadRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        HeadRegistry r = new HeadRegistry();
        for (Tag t : tag.getList("heads", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            r.heads.put(c.getUUID("player"), new Head(Where.valueOf(c.getString("where").toUpperCase(Locale.ROOT)), Spot.load(c),
                    c.hasUUID("entity") ? c.getUUID("entity") : null));
        }
        for (Tag t : tag.getList("bodies", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            r.bodies.put(c.getUUID("player"), new Body(c.getUUID("entity"), Spot.load(c), c.getBoolean("corpse")));
        }
        for (Tag t : tag.getList("trust", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            Set<UUID> set = new HashSet<>();
            for (Tag u : c.getList("trusted", Tag.TAG_INT_ARRAY)) set.add(NbtUtils.loadUUID(u));
            r.trust.put(c.getUUID("player"), set);
        }
        return r;
    }
}
