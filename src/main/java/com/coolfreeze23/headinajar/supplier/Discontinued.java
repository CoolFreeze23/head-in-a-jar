package com.coolfreeze23.headinajar.supplier;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.clarity.Memos;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffFile;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Discontinued lines: staff and hosts of a creature this game has never heard of. The game drops such a creature when
 * its chunk loads (and a ridden host when its rider logs in); Head Office reads its file on the way out, and at the
 * employer's next login one memo lists everyone who went, by name and species. Nobody died, on paper: no Condolence
 * Card, no Exit Interview. The records are only read (the game drops them either way).
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID)
public final class Discontinued {
    private static final String ATTACHED = "neoforge:attachments", HOST = HeadInAJar.MOD_ID + ":host";
    /** The most names one memo lists (the rest are "and N more"). */
    public static final int MAX_NAMES = 12;
    /** Who is being put into the world right now (a login), so a ridden host that does not load is theirs. */
    private static final ThreadLocal<UUID> PLACING = new ThreadLocal<>();
    /** Found while loading (on any thread), filed on the next server tick. */
    private static final ConcurrentLinkedQueue<Gone> FOUND = new ConcurrentLinkedQueue<>();

    /**
     * One creature that did not load: whose it was, its name (null: none, only its species), its species id and its own
     * id (null when the record has none).
     */
    record Gone(UUID employer, Component name, String species, UUID id) {
    }

    private Discontinued() {
    }

    // --- what the game is loading ---------------------------------------------------------------------------------------

    /** A chunk's creatures are loading: any of a discontinued species with a file is noted. */
    public static void chunk(List<? extends Tag> tags, Level level) {
        if (!(level instanceof ServerLevel) || tags == null) return;
        for (Tag t : tags) if (t instanceof CompoundTag c) look(c, null, false);
    }

    /** A creature is loading on its own: only a login's ridden host is looked at (the rider is its employer). */
    public static void single(CompoundTag tag, Level level) {
        UUID placing = PLACING.get();
        if (placing == null || !(level instanceof ServerLevel) || tag == null) return;
        look(tag, placing, false);
    }

    /**
     * Looks at a record and its riders. A record of a species this game lacks is dropped with everything riding it, so
     * from there down every filed creature is noted ({@code dropped}).
     */
    static void look(CompoundTag tag, UUID rider, boolean dropped) {
        String id = tag.getString("id");
        boolean gone = dropped || Records.discontinued(id);
        if (gone) {
            CompoundTag attached = tag.getCompound(ATTACHED);
            StaffFile f = attached.contains(Staff.KEY) ? StaffFile.load(attached.get(Staff.KEY)) : null;
            String species = f != null && !f.species.isEmpty() ? f.species : id;
            UUID self = tag.hasUUID("UUID") ? tag.getUUID("UUID") : null;
            if (f != null && f.employer != null) {
                FOUND.add(new Gone(f.employer, Staff.name(f), species, self));
            } else if (rider != null && attached.getCompound(HOST).getBoolean("host")) {
                FOUND.add(new Gone(rider, null, species, self));
            }
        }
        for (Tag p : tag.getList("Passengers", Tag.TAG_COMPOUND)) look((CompoundTag) p, rider, gone);
    }

    @SubscribeEvent
    static void loading(PlayerEvent.LoadFromFile event) {
        PLACING.set(event.getEntity().getUUID());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void placed(PlayerEvent.PlayerLoggedInEvent event) {
        PLACING.remove();
    }

    @SubscribeEvent
    static void tick(ServerTickEvent.Post event) {
        // a login that never finished leaves nobody being placed
        PLACING.remove();
        file(event.getServer());
    }

    /** Files what was found since the last tick. */
    public static void file(MinecraftServer server) {
        if (FOUND.isEmpty()) return;
        Register r = Register.get(server);
        for (Gone g = FOUND.poll(); g != null; g = FOUND.poll()) {
            Component line = g.name() == null ? Records.speciesName(g.species())
                    : Component.translatable("memo.headinajar.discontinued.entry", g.name(), Records.speciesName(g.species()));
            r.hold(g.employer(), Component.Serializer.toJson(line, server.registryAccess()));
            // off the books: the creature is gone for good, so it no longer counts against its employer's headcount
            if (g.id() != null) com.coolfreeze23.headinajar.staff.work.WorkerIndex.get(server).remove(g.employer(), g.id());
        }
    }

    /** At login, after the rider's own host had its chance to load: the memo, once, for everyone who went. */
    @SubscribeEvent(priority = EventPriority.LOW)
    static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) tell(player);
    }

    /** Until it is out: tried again every few seconds while another memo has the reader's minute. */
    @SubscribeEvent
    static void playerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 40 == 0
                && Register.get(player.server).waiting(player.getUUID()) > 0) {
            tell(player);
        }
    }

    /**
     * Sends the memo listing this employer's seconded staff, if any, and clears them from the register. Waits (and keeps
     * them) while another memo has the reader's minute, so the list is never lost in a queue at logout.
     */
    public static boolean tell(ServerPlayer player) {
        if (player instanceof net.neoforged.neoforge.common.util.FakePlayer) return false;
        file(player.server);
        if (Register.get(player.server).waiting(player.getUUID()) == 0 || !Memos.canSendNow(player)) return false;
        List<String> lines = Register.get(player.server).release(player.getUUID());
        if (lines.isEmpty()) return false;
        MutableComponent list = Component.empty();
        int shown = Math.min(lines.size(), MAX_NAMES);
        for (int i = 0; i < shown; i++) {
            if (i > 0) list.append(Component.translatable("memo.headinajar.discontinued.and"));
            Component c = Component.Serializer.fromJson(lines.get(i), player.registryAccess());
            list.append(c == null ? Component.literal("?") : c);
        }
        if (lines.size() > shown) list.append(Component.translatable("memo.headinajar.discontinued.more", lines.size() - shown));
        Memos.sendAgain(player, Memos.Memo.DISCONTINUED, list);
        return true;
    }

    /** Notes a dropped record as the game would on loading it (tests: a record built by hand). */
    public static void noteForTest(CompoundTag tag, UUID rider) {
        look(tag, rider, false);
    }

    /** The waiting lists, per employer (saved data {@code headinajar_seconded}). */
    public static final class Register extends SavedData {
        private static final String NAME = "headinajar_seconded";
        private final Map<UUID, List<String>> waiting = new HashMap<>();

        public static Register get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Register::new, Register::load, null), NAME);
        }

        void hold(UUID employer, String json) {
            waiting.computeIfAbsent(employer, k -> new ArrayList<>()).add(json);
            setDirty();
        }

        /** The lines waiting for an employer, removed. */
        public List<String> release(UUID employer) {
            List<String> l = waiting.remove(employer);
            if (l != null) setDirty();
            return l == null ? List.of() : l;
        }

        /** How many wait for an employer (tests). */
        public int waiting(UUID employer) {
            List<String> l = waiting.get(employer);
            return l == null ? 0 : l.size();
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            CompoundTag w = new CompoundTag();
            waiting.forEach((id, lines) -> {
                ListTag list = new ListTag();
                for (String s : lines) list.add(StringTag.valueOf(s));
                w.put(id.toString(), list);
            });
            tag.put("waiting", w);
            return tag;
        }

        static Register load(CompoundTag tag, HolderLookup.Provider registries) {
            Register r = new Register();
            CompoundTag w = tag.getCompound("waiting");
            for (String key : w.getAllKeys()) {
                try {
                    List<String> lines = new ArrayList<>();
                    for (Tag t : w.getList(key, Tag.TAG_STRING)) lines.add(t.getAsString());
                    r.waiting.put(UUID.fromString(key), lines);
                } catch (IllegalArgumentException ignored) {
                }
            }
            return r;
        }
    }
}
