package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.staff.StaffTable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;

/**
 * What a head says and what it is called. Every line is a language key, so each viewer reads it in their own language:
 * <ul>
 * <li>its title {@code title.headinajar.<ns>.<path>} (a villager's by profession, {@code ...villager.<profession>});</li>
 * <li>its bell lines {@code bell.headinajar.<ns>.<path>[.<variant>].<n>.noise} and {@code .hr} (n from 1);</li>
 * <li>complaints: its noise and one of the Employee Mount's ten, in its own words where it has them;</li>
 * <li>its goodbye and its Lookout's line ({@code goodbye.} / {@code lookout.headinajar.<ns>.<path>.noise}/{@code .hr});</li>
 * <li>desk lines {@code desk.headinajar.<job>.<what>} (plain) or {@code .noise}/{@code .hr} (in its voice).</li>
 * </ul>
 * On the action bar a two-part line is framed by {@code speech.headinajar.frame}: "Moo." (HR translates: "...").
 */
public final class HeadSpeak {
    /** A second ring within this many ticks is ignored. */
    public static final int RING_GAP = 20;
    public static final String FRAME = "speech.headinajar.frame";
    /** A big head's first line in its big jar (HR's part; the noise is the species'). */
    public static final String SNUG = "bell.headinajar.snug_fit";

    private HeadSpeak() {
    }

    /** "minecraft.cow" for minecraft:cow. */
    public static String path(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return id.getNamespace() + "." + id.getPath();
    }

    /** A villager's profession from its record ("librarian", "none", "nitwit"), or "" for anything else. */
    public static String profession(EntityType<?> type, CompoundTag record) {
        if (type != EntityType.VILLAGER) return "";
        String p = record.getCompound("VillagerData").getString("profession");
        int colon = p.indexOf(':');
        return colon >= 0 ? p.substring(colon + 1) : p.isEmpty() ? "none" : p;
    }

    /** The title's language key (the client shows it when its language has it). */
    public static String titleKey(EntityType<?> type, CompoundTag record) {
        String key = "title.headinajar." + path(type);
        return type == EntityType.VILLAGER ? key + "." + profession(type, record) : key;
    }

    /** The bell lines' variant: a nitwit has its own. */
    static String variant(EntityType<?> type, CompoundTag record) {
        return "nitwit".equals(profession(type, record)) ? "nitwit" : "";
    }

    /** How many bell lines this head has (0: it keeps its old line). */
    public static int bells(EntityType<?> type, CompoundTag record) {
        String v = variant(type, record);
        return v.isEmpty() ? StaffTable.bells(type) : StaffTable.lines(type, "bells." + v);
    }

    /**
     * Whether this is a big head in a big jar: one that needs a Reinforced or an Executive Jar, kept in one. Its first ring
     * says "It is a snug fit." (a head recorded in no jar was cut before 0.6.0, into a Life Support Jar).
     */
    public static boolean snug(EntityType<?> type, CompoundTag record, String jar) {
        return com.coolfreeze23.headinajar.staff.SawTable.needs(type, record) >= 2 && com.coolfreeze23.headinajar.staff.SawTable.rank(jar) >= 2;
    }

    /** The species' noise ("Moo.") as a line. */
    public static Component noise(EntityType<?> type) {
        return Component.translatable(StaffTable.noise(type));
    }

    /** A two-part line as the action bar says it. */
    public static Component framed(Component first, Component second) {
        return second == null ? first : Component.translatable(FRAME, first, second);
    }

    /** Says a two-part line over the jar, and to {@code to} on the action bar (if not null). */
    public static void say(JarredHeadBlockEntity head, ServerPlayer to, Component first, Component second) {
        head.say(first, second);
        if (to != null) to.displayClientMessage(framed(first, second), true);
    }

    /** A desk line ({@code desk.headinajar.<key>}) over the jar, and to {@code to} on the action bar. */
    public static void desk(JarredHeadBlockEntity head, ServerPlayer to, String key, Object... args) {
        say(head, to, Component.translatable("desk.headinajar." + key, args), null);
    }

    /**
     * Picks a bell line for this head, never the one it said last (1-based); 0 when it has none. A head with one line
     * says it every time.
     */
    public static int pick(JarredHeadBlockEntity head, int count, net.minecraft.util.RandomSource random) {
        if (count <= 0) return 0;
        if (count == 1) return 1;
        if (head.lastBell <= 0 || head.lastBell > count) return 1 + random.nextInt(count);
        int n = 1 + random.nextInt(count - 1);
        return n >= head.lastBell ? n + 1 : n;
    }

    /** The bell line {@code n}: its noise and HR's translation (the phantom's counts the ringer's nights awake). */
    public static Component[] bell(JarredHeadBlockEntity head, int n, ServerPlayer ringer) {
        EntityType<?> type = head.entityType();
        String v = variant(type, head.record());
        String key = "bell.headinajar." + path(type) + (v.isEmpty() ? "" : "." + v) + "." + n;
        Component hr = type == EntityType.PHANTOM && ringer != null
                ? Component.translatable(key + ".hr", nights(ringer)) : Component.translatable(key + ".hr");
        return new Component[]{Component.translatable(key + ".noise"), hr};
    }

    /** Whole nights since the player last slept (vanilla's own count). */
    public static int nights(ServerPlayer player) {
        int ticks = player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        return Math.min(999, ticks / 24000);
    }

    /** The Employee Mount's ten complaints, by id ({@code staff.headinajar.complaint.<id>}). */
    public static final java.util.List<String> COMPLAINTS = java.util.List.of("dental", "overtime", "jar", "union", "view", "smell", "pension",
            "broth", "chair", "manager");

    /**
     * A complaint in the head's voice: its noise, and one of the ten, in its own words where it has them
     * ({@code complaint.headinajar.<ns>.<path>.<id>}, listed in its staff file as {@code lines."complaint.<id>"}).
     */
    public static Component[] complaint(EntityType<?> type, net.minecraft.util.RandomSource random) {
        String id = COMPLAINTS.get(random.nextInt(COMPLAINTS.size()));
        String key = StaffTable.lines(type, "complaint." + id) > 0 ? "complaint.headinajar." + path(type) + "." + id : "staff.headinajar.complaint." + id;
        return new Component[]{noise(type), Component.translatable(key)};
    }

    /** Its goodbye: the zombie family's in their own voice, anyone else's "Clearing my desk." */
    public static Component[] goodbye(EntityType<?> type) {
        if (StaffTable.lines(type, "goodbye") > 0) {
            String key = "goodbye.headinajar." + path(type);
            return new Component[]{Component.translatable(key + ".noise"), Component.translatable(key + ".hr")};
        }
        return new Component[]{Component.translatable("goodbye.headinajar.default"), null};
    }

    /** The goodbyes floating where a plinth stood, and when each goes (server). */
    private static final java.util.List<Object[]> GOODBYES = new java.util.ArrayList<>();
    public static final String GOODBYE_TAG = "headinajar_goodbye";

    /**
     * A goodbye said where a plinth stood, as it is broken. The breaking player's game removes the block before any
     * block-entity line could reach it, so the line floats as a vanilla text display for five seconds instead (each
     * viewer reads it in their own language), and then goes.
     */
    public static void goodbyeAt(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, Component first, Component second) {
        net.minecraft.network.chat.MutableComponent text = first.copy().withStyle(net.minecraft.ChatFormatting.WHITE);
        if (second != null) text.append(" ").append(second.copy().withStyle(net.minecraft.ChatFormatting.GRAY));
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:text_display");
        tag.putString("text", Component.Serializer.toJson(text, level.registryAccess()));
        tag.putString("billboard", "center");
        tag.putInt("line_width", 120);
        tag.putInt("background", 0x80000000);
        tag.putFloat("view_range", 0.25f);
        net.minecraft.nbt.ListTag tags = new net.minecraft.nbt.ListTag();
        tags.add(net.minecraft.nbt.StringTag.valueOf(GOODBYE_TAG));
        tag.put("Tags", tags);
        var at = net.minecraft.world.phys.Vec3.atBottomCenterOf(pos).add(0, JarredHeadBlockEntity.SAY_HEIGHT, 0);
        net.minecraft.world.entity.Entity e = EntityType.loadEntityRecursive(tag, level, en -> {
            en.moveTo(at.x, at.y, at.z, 0, 0);
            return en;
        });
        if (e == null || !level.addFreshEntity(e)) return;
        GOODBYES.add(new Object[]{e, level.getGameTime() + JarredHeadBlockEntity.SAY_TICKS});
    }

    /** Server, each tick: goodbyes whose five seconds are up go. */
    public static void tidy(net.minecraft.server.level.ServerLevel level) {
        if (GOODBYES.isEmpty()) return;
        long now = level.getGameTime();
        GOODBYES.removeIf(g -> {
            net.minecraft.world.entity.Entity e = (net.minecraft.world.entity.Entity) g[0];
            if (e.level() != level) return false;
            if (now < (long) g[1] && !e.isRemoved()) return false;
            e.discard();
            return true;
        });
    }

    /** What a Lookout says when it first sees a hostile (a creature with no line of its own: the default). */
    public static Component[] lookout(EntityType<?> type) {
        String key = StaffTable.lines(type, "lookout") > 0 ? "lookout.headinajar." + path(type) : "lookout.headinajar.default";
        return new Component[]{Component.translatable(key + ".noise"), Component.translatable(key + ".hr")};
    }
}
