package com.coolfreeze23.headinajar.genetics;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.clarity.Memos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Staff Directory: which species a team (or a lone player, keyed as the gene library is) has on file, stamped
 * sampled, jarred and ridden as a host (hired comes with the Lanyard). Saved on its own ("headinajar_directory"),
 * never inside the gene library, whose loader reads every compound it holds as a library. Sent to the client on an
 * optional channel: at login, when a DNA Sequencer's screen opens, and to every online member of the key when a stamp
 * is added (with who filed it, for the tab's toast). Milestones go to every online member, and are checked again at
 * login for those who were away.
 */
public final class Directory extends SavedData {
    private static final String NAME = "headinajar_directory";

    /** The stamps on a cell, in the order they are drawn and saved. HIRED stays empty until the Lanyard. */
    public enum Stamp {
        SAMPLED, JARRED, RIDDEN, HIRED;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The 82 vanilla creatures the company keeps a cell for, by department. */
    public static final List<String> VANILLA = List.of(
            "cow", "mooshroom", "pig", "sheep", "chicken", "goat", "rabbit",
            "fox", "panda", "bee", "parrot", "armadillo", "sniffer", "frog", "tadpole",
            "horse", "donkey", "mule", "llama", "trader_llama", "camel", "enderman", "endermite", "shulker", "allay",
            "iron_golem", "wolf", "cat", "ocelot", "polar_bear",
            "zombie", "husk", "drowned", "zombie_villager", "skeleton", "stray", "bogged", "skeleton_horse", "phantom",
            "zombified_piglin", "zoglin", "zombie_horse", "giant",
            "blaze", "magma_cube", "slime", "ghast", "strider", "breeze", "snow_golem", "spider", "cave_spider", "silverfish", "bat",
            "piglin", "piglin_brute", "hoglin", "wither_skeleton",
            "evoker", "vindicator", "pillager", "illusioner", "vex", "ravager", "witch",
            "villager", "wandering_trader",
            "squid", "glow_squid", "cod", "salmon", "tropical_fish", "pufferfish", "dolphin", "turtle", "axolotl", "guardian",
            "creeper",
            "elder_guardian", "warden", "wither",
            "ender_dragon").stream().map(p -> "minecraft:" + p).toList();
    /** Creatures Head Office has no record of until someone files one: they show as [REDACTED] and count for nothing. */
    public static final Set<String> REDACTED = Set.of("minecraft:giant", "minecraft:illusioner", "minecraft:zombie_horse");
    /** The 77 species on the books: every vanilla creature but the Dragon, the Wither and the three [REDACTED]. */
    public static final List<String> ON_THE_BOOKS = VANILLA.stream()
            .filter(id -> !REDACTED.contains(id) && !id.equals("minecraft:ender_dragon") && !id.equals("minecraft:wither")).toList();
    /** Species with any stamp before the first memo; jarred species for Headhunter. */
    public static final int TEN = 10, HEADHUNTER = 25;

    /** One key's sets and who filed each species first (a player's name; "" for a machine). */
    public static final class Book {
        private final EnumMap<Stamp, Set<String>> sets = new EnumMap<>(Stamp.class);
        private final Map<String, String> filedBy = new HashMap<>();

        Book() {
            for (Stamp s : Stamp.values()) sets.put(s, new LinkedHashSet<>());
        }

        public Set<String> set(Stamp stamp) {
            return java.util.Collections.unmodifiableSet(sets.get(stamp));
        }

        public boolean has(Stamp stamp, String species) {
            return sets.get(stamp).contains(species);
        }

        public Map<String, String> filedBy() {
            return java.util.Collections.unmodifiableMap(filedBy);
        }

        /** Species with any stamp, the [REDACTED] left out (they count for nothing). */
        public Set<String> counted() {
            Set<String> any = new LinkedHashSet<>();
            for (Set<String> s : sets.values()) any.addAll(s);
            any.removeAll(REDACTED);
            return any;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            for (Stamp s : Stamp.values()) {
                ListTag list = new ListTag();
                for (String id : sets.get(s)) list.add(StringTag.valueOf(id));
                t.put(s.id(), list);
            }
            CompoundTag by = new CompoundTag();
            filedBy.forEach(by::putString);
            t.put("FiledBy", by);
            return t;
        }

        static Book load(CompoundTag t) {
            Book b = new Book();
            for (Stamp s : Stamp.values()) {
                ListTag list = t.getList(s.id(), Tag.TAG_STRING);
                for (int i = 0; i < list.size(); i++) b.sets.get(s).add(list.getString(i));
            }
            CompoundTag by = t.getCompound("FiledBy");
            for (String id : by.getAllKeys()) b.filedBy.put(id, by.getString(id));
            return b;
        }
    }

    private final Map<String, Book> books = new HashMap<>();

    public static Directory get(MinecraftServer server) {
        // made the first time a world asks for it (a 0.6.1 world on its first 0.6.2 load): filled from the library
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(
                () -> backfill(GeneArchive.get(server)), Directory::load, null), NAME);
    }

    /** A key's book (an empty one is made for a key that has none yet). */
    public Book book(String key) {
        return books.computeIfAbsent(key, k -> new Book());
    }

    public Set<String> keys() {
        return java.util.Collections.unmodifiableSet(books.keySet());
    }

    /**
     * Old saves: the library records reads of genes, not samples, so a species is marked sampled only when its gene
     * has that species as its one source in this game (seven genes are shared, and a read of Gills could be any of
     * five fish) and a read above 0. Jarred, ridden and hired start blank.
     */
    public static Directory backfill(GeneArchive archive) {
        Directory d = new Directory();
        Map<Gene, String> own = new EnumMap<>(Gene.class);
        for (Gene gene : Gene.values()) {
            List<String> installed = gene.sources.stream().filter(Directory::exists).toList();
            if (installed.size() == 1) own.put(gene, installed.get(0));
        }
        for (String key : archive.keys()) {
            archive.library(key).forEach((gene, read) -> {
                String species = own.get(gene);
                if (species != null && read > 0) d.book(key).sets.get(Stamp.SAMPLED).add(species);
            });
        }
        d.setDirty();
        return d;
    }

    private static boolean exists(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl != null && BuiltInRegistries.ENTITY_TYPE.containsKey(rl);
    }

    public static String id(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
    }

    // --- filing ------------------------------------------------------------------------------------------------------

    /** {@code by} stamped {@code type} for their key (a sample drawn, a head jarred, a host boarded). */
    public static void file(ServerPlayer by, EntityType<?> type, Stamp stamp) {
        if (by == null || type == null || by.server == null) return;
        file(by.server, GeneArchive.key(by), type, stamp, by.getGameProfile().getName());
    }

    /**
     * Stamps {@code type} for {@code key}; {@code by} is the filer's name, or null for a machine (an automated
     * Sequencer). A stamp already there changes nothing. Returns whether the stamp is new.
     */
    public static boolean file(MinecraftServer server, String key, EntityType<?> type, Stamp stamp, String by) {
        if (server == null || key == null || key.isEmpty() || type == null) return false;
        String species = id(type);
        Directory d = get(server);
        Book book = d.book(key);
        if (!book.sets.get(stamp).add(species)) return false;
        book.filedBy.putIfAbsent(species, by == null ? "" : by);
        d.setDirty();
        for (ServerPlayer p : members(server, key)) {
            milestones(p, book);
            send(p, book, species, stamp, by == null ? "" : by);
        }
        return true;
    }

    private static List<ServerPlayer> members(MinecraftServer server, String key) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) if (GeneArchive.key(p).equals(key)) out.add(p);
        return out;
    }

    /** The milestones a book has reached, granted to one member (a memo and an advancement come once each). */
    static void milestones(ServerPlayer p, Book book) {
        if (book.counted().size() >= TEN) Memos.send(p, Memos.Memo.DIRECTORY_TEN);
        Set<String> jarred = new LinkedHashSet<>(book.sets.get(Stamp.JARRED));
        jarred.removeAll(REDACTED);
        if (jarred.size() >= HEADHUNTER) com.coolfreeze23.headinajar.Advance.grant(p, "headhunter");
        if (book.sets.get(Stamp.SAMPLED).containsAll(ON_THE_BOOKS)) {
            com.coolfreeze23.headinajar.Advance.grant(p, "complete_census");
            Memos.send(p, Memos.Memo.CENSUS);
        }
        // 0.8.0: the New Arrivals row has its own induction; the census stays the 77
        List<String> arrivals = com.coolfreeze23.headinajar.supplier.Supplier.delivered();
        if (!arrivals.isEmpty()) {
            for (Set<String> s : book.sets.values()) {
                if (s.stream().anyMatch(arrivals::contains)) {
                    Memos.send(p, Memos.Memo.NEW_ARRIVAL);
                    break;
                }
            }
            if (book.sets.get(Stamp.SAMPLED).containsAll(arrivals)) com.coolfreeze23.headinajar.Advance.grant(p, "new_starters");
        }
        for (Set<String> s : book.sets.values()) {
            if (s.stream().anyMatch(REDACTED::contains)) {
                com.coolfreeze23.headinajar.Advance.grant(p, "off_the_books");
                break;
            }
        }
    }

    /** A member back from away: what the team reached meanwhile, and the client's copy. */
    public static void login(ServerPlayer p) {
        if (p.server == null) return;
        Book book = get(p.server).book(GeneArchive.key(p));
        milestones(p, book);
        send(p, book, "", -1, "");
    }

    /** The client's copy of this player's key, with no toast (a Sequencer's screen opening). */
    public static void sync(ServerPlayer p) {
        if (p.server == null) return;
        send(p, get(p.server).book(GeneArchive.key(p)), "", -1, "");
    }

    private static void send(ServerPlayer p, Book book, String species, Stamp stamp, String by) {
        send(p, book, species, stamp.ordinal(), by);
    }

    private static void send(ServerPlayer p, Book book, String species, int stamp, String by) {
        // a game without the channel (or a mock player) is never sent it: NeoForge would refuse the send
        if (p.connection == null || !p.connection.hasChannel(Sync.TYPE)) return;
        List<List<String>> sets = new ArrayList<>();
        for (Stamp s : Stamp.values()) sets.add(new ArrayList<>(book.sets.get(s)));
        PacketDistributor.sendToPlayer(p, new Sync(sets, new HashMap<>(book.filedBy), species, stamp, by));
    }

    // --- saving ------------------------------------------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        books.forEach((key, book) -> all.put(key, book.save()));
        tag.put("Books", all);
        return tag;
    }

    public static Directory load(CompoundTag tag, HolderLookup.Provider registries) {
        Directory d = new Directory();
        CompoundTag all = tag.getCompound("Books");
        for (String key : all.getAllKeys()) d.books.put(key, Book.load(all.getCompound(key)));
        return d;
    }

    // --- the payload -------------------------------------------------------------------------------------------------

    /**
     * The four sets of the player's key, in {@link Stamp} order, who filed each species, and what was just filed
     * ({@code filed} empty when nothing was: a login or a screen opening; {@code by} empty for a machine).
     */
    public record Sync(List<List<String>> sets, Map<String, String> filedBy, String filed, int stamp, String by) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(HeadInAJar.id("directory"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).apply(ByteBufCodecs.list()), Sync::sets,
                ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8), Sync::filedBy,
                ByteBufCodecs.STRING_UTF8, Sync::filed, ByteBufCodecs.VAR_INT, Sync::stamp, ByteBufCodecs.STRING_UTF8, Sync::by, Sync::new);

        @Override
        public Type<Sync> type() {
            return TYPE;
        }
    }

    /** Registers the payload on an optional registrar ({@code registrar.optional()}): a game without it still connects. */
    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(Sync.TYPE, Sync.CODEC, (msg, ctx) -> Client.receive(msg));
    }

    /** The client's copy of its key's Directory (the Sequencer's Directory tab and the Bio-Scanner read it). */
    public static final class Client {
        private static final EnumMap<Stamp, Set<String>> SETS = new EnumMap<>(Stamp.class);
        private static Map<String, String> filedBy = Map.of();
        /** The last filing heard of, and when (wall-clock milliseconds): the tab shows it for 60 ticks. */
        public static String toastSpecies = "", toastBy = "";
        public static int toastStamp = -1;
        public static long toastAt;
        /** Raised on every payload, so a screen knows to rebuild its grid. */
        public static int version;

        static {
            for (Stamp s : Stamp.values()) SETS.put(s, new LinkedHashSet<>());
        }

        private Client() {
        }

        static void receive(Sync msg) {
            for (Stamp s : Stamp.values()) {
                Set<String> set = SETS.get(s);
                set.clear();
                if (s.ordinal() < msg.sets().size()) set.addAll(msg.sets().get(s.ordinal()));
            }
            filedBy = Map.copyOf(msg.filedBy());
            if (!msg.filed().isEmpty()) {
                toastSpecies = msg.filed();
                toastStamp = msg.stamp();
                toastBy = msg.by();
                toastAt = System.currentTimeMillis();
            }
            version++;
        }

        public static boolean has(Stamp stamp, String species) {
            return SETS.get(stamp).contains(species);
        }

        public static boolean any(String species) {
            for (Set<String> s : SETS.values()) if (s.contains(species)) return true;
            return false;
        }

        /** Every species with a stamp (a mod's creature whose mod is gone included). */
        public static Set<String> all() {
            Set<String> out = new LinkedHashSet<>();
            for (Set<String> s : SETS.values()) out.addAll(s);
            return out;
        }

        /** Who filed it first: a name, "" for a machine, null when nobody has. */
        public static String filedBy(String species) {
            return filedBy.get(species);
        }
    }

    /** Login: the client's copy and the milestones reached while away. */
    @EventBusSubscriber(modid = HeadInAJar.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void login(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer p) Directory.login(p);
        }
    }
}
