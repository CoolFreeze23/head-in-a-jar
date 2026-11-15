package com.coolfreeze23.headinajar.staff;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The company's register of staff: numbering a creature the first time the company touches it, where its file is kept
 * (on the creature, in a jarred head's data, on a placed head or a station's head), and how a file is said (number,
 * name, department, employer).
 */
public final class Staff {
    /** Given names in each language's pool ({@code staff.headinajar.name.<i>}). */
    public static final int NAMES = 16;
    /** Where a file sits inside a stored mob's record (the attachment's own key), and on a head's data. */
    public static final String ATTACHED = "neoforge:attachments", KEY = HeadInAJar.MOD_ID + ":staff", ON_HEAD = "Staff";
    /** The fourteen species departments' ids (entity type tags {@code headinajar:department/<id>}), in the order they are looked up. */
    public static final List<String> DEPARTMENTS = List.of("livestock", "field_office", "logistics", "security", "night_shift", "facilities",
            "accounts", "legal", "sales", "marine", "demolitions", "senior_management");
    public static final String BOARD = "board", TEMPS = "temp_agency", GENERAL = "general";
    private static final List<TagKey<EntityType<?>>> TAGS = new ArrayList<>();

    static {
        for (String d : DEPARTMENTS) TAGS.add(TagKey.create(Registries.ENTITY_TYPE, HeadInAJar.id("department/" + d)));
    }

    /** What tests can read: every numbering, newest last ("number:species:key"). */
    private static final List<String> HISTORY = new ArrayList<>();

    private Staff() {
    }

    // --- days and numbers --------------------------------------------------------------------------------------------

    /** The day as the Employee Mount counts it (the Nether and the End share the Overworld's). */
    public static long day(Level level) {
        return level.getDayTime() / 24000L;
    }

    /** A day as people read it: the world's first day is day 1. */
    public static long shown(long day) {
        return day + 1;
    }

    /** A staff number as it is printed: four digits up to 9999, then as it is. */
    public static String number(int n) {
        return n <= 9999 ? String.format(java.util.Locale.ROOT, "%04d", n) : Integer.toString(n);
    }

    // --- files on things ---------------------------------------------------------------------------------------------

    /** The file on a living creature, or null. */
    public static StaffFile of(Entity entity) {
        if (entity == null || !entity.hasData(ModRegistry.STAFF)) return null;
        StaffFile f = entity.getData(ModRegistry.STAFF);
        return f.filed() ? f : null;
    }

    /** The file of a jarred head's data: its own {@code Staff}, or one still inside the stored mob's attachments. */
    public static StaffFile fromHeadTag(CompoundTag tag) {
        if (tag == null) return null;
        if (tag.contains(ON_HEAD)) return StaffFile.load(tag.get(ON_HEAD));
        CompoundTag attached = tag.getCompound("Entity").getCompound(ATTACHED);
        return attached.contains(KEY) ? StaffFile.load(attached.get(KEY)) : null;
    }

    /** Removes a file from a stored mob's record (it lives beside it, on the head, so there is one copy). */
    public static void stripEntity(CompoundTag entity) {
        if (!entity.contains(ATTACHED)) return;
        CompoundTag attached = entity.getCompound(ATTACHED);
        attached.remove(KEY);
        if (attached.isEmpty()) entity.remove(ATTACHED);
    }

    /** The file on a jarred head item (read in place). */
    public static StaffFile of(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : fromHeadTag(data.getUnsafe());
    }

    /** Writes a file onto a jarred head item, in place (a station calls setChanged after). */
    public static void write(ItemStack stack, StaffFile file) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (file == null) tag.remove(ON_HEAD);
            else tag.put(ON_HEAD, file.save());
            if (tag.contains("Entity")) stripEntity(tag.getCompound("Entity"));
        });
    }

    // --- registering -------------------------------------------------------------------------------------------------

    /** A new file with the next number, a given name from the pool and today's date. */
    public static StaffFile create(MinecraftServer server, Level level, EntityType<?> type) {
        StaffFile f = new StaffFile();
        f.number = StaffRegister.get(server).take();
        f.species = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        f.nameIndex = level.random.nextInt(NAMES);
        f.joined = day(level);
        HISTORY.add(f.number + ":" + f.species);
        return f;
    }

    /**
     * Registers a living creature (idempotent: one already on file only gains the history line). {@code key} is the
     * history line ({@code sampled}, {@code jarred}, {@code host}, {@code shell}, {@code retro}); {@code by} is who did
     * it (it gets the first-number memo), and becomes the employer if {@code employ}. Returns the file.
     */
    public static StaffFile register(Entity creature, ServerPlayer by, String key, boolean employ) {
        if (!(creature.level() instanceof net.minecraft.server.level.ServerLevel level)) return null;
        StaffFile f = of(creature);
        boolean fresh = f == null;
        if (fresh) {
            f = create(level.getServer(), level, creature.getType());
            creature.setData(ModRegistry.STAFF, f);
        }
        if (employ && by != null) employ(f, by);
        f.note(key, day(level), "");
        creature.setData(ModRegistry.STAFF, f);
        if (fresh && by != null) numbered(by, f);
        return f;
    }

    /**
     * A creature sampled (any collection tool, the Leech Jar, the Hemo-Syringe): numbered the first time, with no
     * employer (a wild sampled creature is on file but works for nobody). Sampled again the same day, it gains no line.
     */
    public static void sampled(Entity creature, ServerPlayer by) {
        if (!(creature instanceof net.minecraft.world.entity.Mob) || creature instanceof com.coolfreeze23.headinajar.entity.MechaEntity) return;
        StaffFile f = of(creature);
        if (f != null && !f.history.isEmpty()) {
            StaffFile.Entry last = f.history.get(f.history.size() - 1);
            if (last.k().equals("sampled") && last.d() == day(creature.level())) return;
        }
        register(creature, by, "sampled", false);
    }

    /** A head (an item's or a block's) that has no file yet gets one; returns it. Does not send memos. */
    public static StaffFile fileFor(Level level, EntityType<?> type, StaffFile existing) {
        if (existing != null || !(level instanceof net.minecraft.server.level.ServerLevel server) || type == null) return existing;
        return create(server.getServer(), level, type);
    }

    public static void employ(StaffFile f, Player by) {
        f.employer = by.getUUID();
        f.employerName = by.getGameProfile().getName();
    }

    /** The first time a player causes a numbering: DOLORES's memo and the advancement. */
    public static void numbered(ServerPlayer by, StaffFile f) {
        com.coolfreeze23.headinajar.Advance.grant(by, "staff_number");
        com.coolfreeze23.headinajar.clarity.Memos.send(by, com.coolfreeze23.headinajar.clarity.Memos.Memo.STAFF_NUMBER,
                number(f.number), species(f));
    }

    public static List<String> history() {
        return HISTORY;
    }

    // --- how a file is said ------------------------------------------------------------------------------------------

    public static EntityType<?> type(StaffFile f) {
        ResourceLocation id = ResourceLocation.tryParse(f.species);
        return id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
    }

    public static Component species(StaffFile f) {
        EntityType<?> type = type(f);
        return type == null ? Component.literal(f.species) : type.getDescription();
    }

    /** Its given name: one HR picked from the reader's own language's pool, or the one its employer gave it. */
    public static Component name(StaffFile f) {
        if (f.customName != null && !f.customName.isEmpty()) return Component.literal(f.customName);
        return Component.translatable("staff.headinajar.name." + Math.floorMod(f.nameIndex, NAMES));
    }

    /** The species' department id (from the entity type tags), or General Staff for a creature none maps. */
    public static String department(EntityType<?> type) {
        if (type == null) return GENERAL;
        if (type == EntityType.ENDER_DRAGON) return BOARD;
        for (int i = 0; i < TAGS.size(); i++) if (type.is(TAGS.get(i))) return DEPARTMENTS.get(i);
        return GENERAL;
    }

    /** A file's department where it is now: at a Cerebral Array, the Board; a Utility Shell, the Temp Agency. */
    public static String department(StaffFile f, String where) {
        if (where != null) return where;
        return department(type(f));
    }

    public static Component departmentName(String id) {
        return Component.translatable("department.headinajar." + id);
    }

    /** The employer's name, or "on file" when nobody employs it. */
    public static Component employer(StaffFile f) {
        if (f.employer == null) return Component.translatable("staff.headinajar.on_file");
        return Component.literal(f.employerName.isEmpty() ? "?" : f.employerName);
    }

    /** Whether {@code viewer} may see the name (the employer and those the employer trusts). */
    public static boolean maySeeName(StaffFile f, Player viewer) {
        if (f.employer == null || viewer == null) return false;
        return com.coolfreeze23.headinajar.form.HeadRegistry.get(viewer).trusts(f.employer, viewer);
    }

    /** Refreshes the employer's name when the employer is online. */
    public static void refresh(StaffFile f, MinecraftServer server) {
        if (f.employer == null || server == null) return;
        ServerPlayer p = server.getPlayerList().getPlayer(f.employer);
        if (p != null) f.employerName = p.getGameProfile().getName();
    }

    /** One history line as its reader sees it. */
    public static Component line(StaffFile.Entry e) {
        String day = Long.toString(shown(e.d()));
        String key = "staff.headinajar.history." + e.k();
        return switch (e.k()) {
            case "placed", "hired", "renamed", "lunch" -> Component.translatable(key, e.a(), day);
            case "fitted" -> {
                String[] parts = e.a().split("\n", 2);
                yield Component.translatable(key, Component.translatable(parts[0]), parts.length > 1 ? parts[1] : "", day);
            }
            case "left" -> Component.translatable(key, day, reason(e.a()));
            default -> Component.translatable(key, day);
        };
    }

    /** A reason for leaving: a reason key's word, or a killer's name as it was. */
    public static Component reason(String why) {
        return why.startsWith("staff.headinajar.reason.") ? Component.translatable(why) : Component.literal(why);
    }

    /** Whether this file's creature could ever leave the company with an Exit Interview (it has an employer). */
    public static boolean employed(StaffFile f) {
        return f != null && f.employer != null;
    }

    public static UUID employerId(StaffFile f) {
        return f == null ? null : f.employer;
    }
}
