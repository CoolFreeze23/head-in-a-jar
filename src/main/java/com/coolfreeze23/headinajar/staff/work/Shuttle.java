package com.coolfreeze23.headinajar.staff.work;

import com.coolfreeze23.headinajar.block.TimeClockBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The company shuttle: a member of staff taken out of the world (its whole entity record, attachments and all) and set
 * down somewhere else later, never loading a chunk for it and never making two of it.
 * <ul>
 * <li><b>Home</b>: the record goes into its Time Clock's arrivals list ({@code TimeClockBlockEntity.arrive}), and the clock
 * sets it down beside itself on its own tick. A clock not loaded now gets it when it is: the record waits here, in this
 * world's saved data, until then (nothing is lost).</li>
 * <li><b>Catch-up</b>: a member following more than 32 behind vanishes in a puff and is set down beside its player
 * {@link #CATCH_UP_TICKS} later, if the player is in the same dimension and a spot beside them is loaded and safe;
 * otherwise it takes the shuttle home.</li>
 * </ul>
 * The record is always written before the creature is removed, and a record is never set down while a creature with
 * its UUID is in the world.
 */
public final class Shuttle {
    /** The catch-up shuttle's trip (5 seconds). */
    public static final int CATCH_UP_TICKS = 100;
    private static final String NAME = "headinajar_shuttle";

    private Shuttle() {
    }

    /** This world's shuttle records. */
    public static Data data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
    }

    // --- taking a creature out ---------------------------------------------------------------------------------------

    /** Its whole record, ready to travel (riders off, lead dropped), or null when it cannot be taken. */
    private static CompoundTag record(Mob mob) {
        if (!mob.isAlive() || mob.isRemoved()) return null;
        mob.ejectPassengers();
        mob.stopRiding();
        if (mob.isLeashed()) mob.dropLeash(true, true);
        CompoundTag tag = new CompoundTag();
        return mob.saveAsPassenger(tag) ? tag : null;
    }

    /** The puff it leaves in. */
    private static void puff(Mob mob) {
        if (!(mob.level() instanceof ServerLevel level)) return;
        level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + mob.getBbHeight() / 2, mob.getZ(), 14,
                mob.getBbWidth() / 2, mob.getBbHeight() / 2, mob.getBbWidth() / 2, 0.02);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.4f, 1.7f);
    }

    /** The shuttle home: into its clock's arrivals (or held here until the clock is loaded), then gone in a puff. */
    public static boolean home(Mob mob, WorkerData d) {
        if (d.clock == null || !(mob.level() instanceof ServerLevel level)) return false;
        WorkerData.State was = d.state;
        d.state = WorkerData.State.SHUTTLE;
        d.detail = null;
        d.dropTask();
        CompoundTag tag = record(mob);
        if (tag == null) {
            d.state = was;
            return false;
        }
        send(level.getServer(), d.clock, tag, com.coolfreeze23.headinajar.supplier.Records.now());
        puff(mob);
        mob.discard();
        return true;
    }

    /** A record to its clock now, or kept until the clock is loaded. */
    private static void send(MinecraftServer server, GlobalPos clock, CompoundTag tag, int stamp) {
        TimeClockBlockEntity c = Sites.clock(server.overworld(), clock);
        if (c != null) c.arrive(tag, stamp);
        else data(server).hold(clock, tag, stamp);
    }

    /** The catch-up shuttle: gone in a puff, beside the player {@link #CATCH_UP_TICKS} later (or home). */
    public static boolean catchUp(Mob mob, WorkerData d, ServerPlayer p) {
        if (!(mob.level() instanceof ServerLevel level)) return false;
        d.state = WorkerData.State.SHUTTLE;
        CompoundTag tag = record(mob);
        if (tag == null) {
            d.state = WorkerData.State.DETAIL;
            return false;
        }
        Data data = data(level.getServer());
        data.catching.add(new Catching(p.getUUID(), level.dimension(), mob.blockPosition(), level.getServer().overworld().getGameTime() + CATCH_UP_TICKS,
                d.clock, tag, com.coolfreeze23.headinajar.supplier.Records.now()));
        data.setDirty();
        puff(mob);
        mob.discard();
        return true;
    }

    // --- setting it down ---------------------------------------------------------------------------------------------

    /** Each server tick: catch-ups that are due; once a second, records waiting for their clock to load. */
    static void tick(MinecraftServer server) {
        Data data = data(server);
        if (data.catching.isEmpty() && data.waiting.isEmpty()) return;
        long now = server.overworld().getGameTime();
        for (Iterator<Catching> it = data.catching.iterator(); it.hasNext(); ) {
            Catching c = it.next();
            if (now < c.due) continue;
            it.remove();
            data.setDirty();
            if (!beside(server, c)) {
                // the shuttle home instead; one with no site is set down where it left, awaiting a clock
                if (c.clock != null) send(server, c.clock, homeBound(c.tag), c.stamp);
                else data.waiting.add(new Waiting(GlobalPos.of(c.dim, c.from), gone(c.tag), c.stamp));
            }
        }
        if (now % 20 != 0 || data.waiting.isEmpty()) return;
        for (Iterator<Waiting> it = data.waiting.iterator(); it.hasNext(); ) {
            Waiting w = it.next();
            ServerLevel level = server.getLevel(w.clock.dimension());
            if (level == null || !level.isLoaded(w.clock.pos())) continue;
            TimeClockBlockEntity c = Sites.clock(level, w.clock);
            if (c != null) {
                c.arrive(w.tag, w.stamp);
                it.remove();
                data.setDirty();
            } else if (setDown(level, gone(w.tag), w.clock.pos(), null) != Placed.NOT_YET) {
                // the clock is gone: it waits where the clock stood for a new one
                it.remove();
                data.setDirty();
            }
        }
    }

    /** A record bound home: its state SHUTTLE (the clock sets it down as TO_CLOCK). */
    private static CompoundTag homeBound(CompoundTag tag) {
        return withState(tag, WorkerData.State.SHUTTLE, null, false);
    }

    /** A record whose clock is gone: awaiting a new clock where it is set down. */
    private static CompoundTag gone(CompoundTag tag) {
        return withState(tag, WorkerData.State.AWAITING, null, true);
    }

    /** The record with its worker state changed (its attachment, as saved). */
    private static CompoundTag withState(CompoundTag tag, WorkerData.State state, UUID detail, boolean dropClock) {
        CompoundTag all = tag.getCompound(net.neoforged.neoforge.attachment.AttachmentHolder.ATTACHMENTS_NBT_KEY);
        String key = com.coolfreeze23.headinajar.HeadInAJar.id("worker").toString();
        if (!all.contains(key)) return tag;
        CompoundTag w = all.getCompound(key);
        w.putString("state", state.getSerializedName());
        if (detail != null) w.putUUID("detail", detail);
        else w.remove("detail");
        if (dropClock) w.remove("clock");
        all.put(key, w);
        tag.put(net.neoforged.neoforge.attachment.AttachmentHolder.ATTACHMENTS_NBT_KEY, all);
        return tag;
    }

    private static boolean beside(MinecraftServer server, Catching c) {
        ServerPlayer p = server.getPlayerList().getPlayer(c.player);
        if (p == null || !p.isAlive() || !p.level().dimension().equals(c.dim)) return false;
        UUID id = c.tag.hasUUID("UUID") ? c.tag.getUUID("UUID") : null;
        if (id == null || !Detail.members(p).contains(id)) return false;
        Placed placed = setDown(p.serverLevel(), c.tag, p.blockPosition(), p);
        return placed != Placed.NOT_YET;
    }

    enum Placed {
        /** Set down. */
        DONE,
        /** Already in the world: the record is dropped (never two of it). */
        ALREADY,
        /** Nowhere safe and loaded (or it would not load). */
        NOT_YET
    }

    /**
     * Sets a record down near {@code near}, on a loaded, safe spot 2 or 3 blocks off (the tamed wolf's rule), as a
     * member of {@code player}'s detail if one is given.
     */
    static Placed setDown(ServerLevel level, CompoundTag tag, BlockPos near, ServerPlayer player) {
        if (tag.hasUUID("UUID") && Detail.find(level.getServer(), tag.getUUID("UUID")) != null) return Placed.ALREADY;
        Entity e = EntityType.loadEntityRecursive(tag, level, x -> x);
        if (!(e instanceof Mob mob)) return Placed.NOT_YET;
        BlockPos spot = safe(level, mob, near);
        if (spot == null) return Placed.NOT_YET;
        mob.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, mob.getYRot(), mob.getXRot());
        WorkerData d = Workforce.data(mob);
        if (d != null && player != null) {
            d.state = WorkerData.State.DETAIL;
            d.detail = player.getUUID();
        }
        if (!level.addFreshEntity(mob)) return Placed.NOT_YET;
        level.playSound(null, spot, SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 0.6f, 1.2f);
        level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + mob.getBbHeight() / 2, mob.getZ(), 10,
                mob.getBbWidth() / 2, mob.getBbHeight() / 2, mob.getBbWidth() / 2, 0.02);
        return Placed.DONE;
    }

    /** A spot 2 or 3 blocks from {@code near} (within 1 up or down) that is loaded, walkable and clear for this creature. */
    static BlockPos safe(ServerLevel level, Mob mob, BlockPos near) {
        var random = level.getRandom();
        for (int i = 0; i < 24; i++) {
            int dx = random.nextIntBetweenInclusive(-3, 3), dz = random.nextIntBetweenInclusive(-3, 3);
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;
            BlockPos at = near.offset(dx, random.nextIntBetweenInclusive(-1, 1), dz);
            if (!level.isLoaded(at.offset(-1, -1, -1)) || !level.isLoaded(at.offset(1, 1, 1))) continue;
            mob.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            if (WalkNodeEvaluator.getPathTypeStatic(mob, at) != PathType.WALKABLE) continue;
            if (!level.noCollision(mob, mob.getBoundingBox())) continue;
            return at;
        }
        return null;
    }

    /** Whether a worker is in the shuttle now (on its way home, waiting for its clock, or catching up). */
    public static boolean aboard(MinecraftServer server, UUID worker) {
        Data data = data(server);
        for (Waiting w : data.waiting) if (w.tag.hasUUID("UUID") && w.tag.getUUID("UUID").equals(worker)) return true;
        for (Catching c : data.catching) if (c.tag.hasUUID("UUID") && c.tag.getUUID("UUID").equals(worker)) return true;
        return false;
    }

    // --- the records -------------------------------------------------------------------------------------------------

    /** A record bound for a clock that is not loaded ({@code stamp}: the data version it was written at, 0 unknown). */
    record Waiting(GlobalPos clock, CompoundTag tag, int stamp) {
    }

    /** A record catching up with its player. */
    record Catching(UUID player, ResourceKey<Level> dim, BlockPos from, long due, GlobalPos clock, CompoundTag tag, int stamp) {
    }

    /** The world's shuttle records (saved data {@code headinajar_shuttle}). */
    public static final class Data extends SavedData {
        final List<Waiting> waiting = new ArrayList<>();
        final List<Catching> catching = new ArrayList<>();
        /** Members that died while their supervisor was offline: player -> workers, taken off the roll at login. */
        private final Map<UUID, Set<UUID>> leavers = new HashMap<>();
        /** Members that converted (a new UUID) while their supervisor was offline: player -> old -> new. */
        private final Map<UUID, Map<UUID, UUID>> renames = new HashMap<>();

        void rename(UUID player, UUID old, UUID now) {
            Map<UUID, UUID> m = renames.computeIfAbsent(player, k -> new HashMap<>());
            // a second conversion follows the first
            m.replaceAll((from, to) -> to.equals(old) ? now : to);
            if (!m.containsValue(now)) m.put(old, now);
            setDirty();
        }

        Map<UUID, UUID> renames(UUID player) {
            Map<UUID, UUID> m = renames.remove(player);
            if (m != null) setDirty();
            return m == null ? Map.of() : m;
        }

        /** Keeps a record bound home for a clock that is not loaded now (it goes into the arrivals when it is). */
        public void hold(GlobalPos clock, CompoundTag entity) {
            hold(clock, entity, com.coolfreeze23.headinajar.supplier.Records.now());
        }

        /** As {@link #hold(GlobalPos, CompoundTag)}, keeping the data version the record was written at (0: unknown). */
        public void hold(GlobalPos clock, CompoundTag entity, int stamp) {
            waiting.add(new Waiting(clock, homeBound(entity), stamp));
            setDirty();
        }

        public int waitingCount() {
            return waiting.size();
        }

        public int catchingCount() {
            return catching.size();
        }

        void leaver(UUID player, UUID worker) {
            leavers.computeIfAbsent(player, k -> new HashSet<>()).add(worker);
            setDirty();
        }

        Set<UUID> leavers(UUID player) {
            Set<UUID> gone = leavers.remove(player);
            if (gone != null) setDirty();
            return gone == null ? Set.of() : gone;
        }

        private static Tag pos(GlobalPos pos) {
            return GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, pos).result().orElse(new CompoundTag());
        }

        private static GlobalPos pos(Tag tag) {
            return tag == null ? null : GlobalPos.CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(null);
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag w = new ListTag();
            for (Waiting x : waiting) {
                CompoundTag t = new CompoundTag();
                if (x.clock() != null) t.put("Clock", pos(x.clock()));
                t.put("Entity", x.tag());
                com.coolfreeze23.headinajar.supplier.Records.put(t, "Entity", x.stamp());
                w.add(t);
            }
            tag.put("Waiting", w);
            ListTag c = new ListTag();
            for (Catching x : catching) {
                CompoundTag t = new CompoundTag();
                t.putUUID("Player", x.player());
                t.putString("Dim", x.dim().location().toString());
                t.putLong("From", x.from().asLong());
                t.putLong("Due", x.due());
                if (x.clock() != null) t.put("Clock", pos(x.clock()));
                t.put("Entity", x.tag());
                com.coolfreeze23.headinajar.supplier.Records.put(t, "Entity", x.stamp());
                c.add(t);
            }
            tag.put("Catching", c);
            ListTag l = new ListTag();
            leavers.forEach((player, workers) -> workers.forEach(worker -> {
                CompoundTag t = new CompoundTag();
                t.putUUID("Player", player);
                t.putUUID("Worker", worker);
                l.add(t);
            }));
            tag.put("Leavers", l);
            ListTag r = new ListTag();
            renames.forEach((player, m) -> m.forEach((from, to) -> {
                CompoundTag t = new CompoundTag();
                t.putUUID("Player", player);
                t.putUUID("From", from);
                t.putUUID("To", to);
                r.add(t);
            }));
            tag.put("Renames", r);
            return tag;
        }

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            for (Tag t : tag.getList("Waiting", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                d.waiting.add(new Waiting(c.contains("Clock") ? pos(c.get("Clock")) : null, c.getCompound("Entity"),
                        com.coolfreeze23.headinajar.supplier.Records.stampOf(c, "Entity")));
            }
            for (Tag t : tag.getList("Catching", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(c.getString("Dim")));
                d.catching.add(new Catching(c.getUUID("Player"), dim, BlockPos.of(c.getLong("From")), c.getLong("Due"), c.contains("Clock") ? pos(c.get("Clock")) : null,
                        c.getCompound("Entity"), com.coolfreeze23.headinajar.supplier.Records.stampOf(c, "Entity")));
            }
            for (Tag t : tag.getList("Leavers", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                d.leaver(c.getUUID("Player"), c.getUUID("Worker"));
            }
            for (Tag t : tag.getList("Renames", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                d.renames.computeIfAbsent(c.getUUID("Player"), k -> new HashMap<>()).put(c.getUUID("From"), c.getUUID("To"));
            }
            return d;
        }
    }
}
