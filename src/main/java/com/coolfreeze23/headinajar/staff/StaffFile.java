package com.coolfreeze23.headinajar.staff;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A Personnel File: the staff number the company gave a creature, its species, the name HR gave it, who employs it,
 * the day it joined, its last eight history lines, and for staff on the payroll its morale and pay record. It lives on
 * the creature (an attachment), in a jarred head item's data ({@code "Staff"}), on a placed head, or in the data of a
 * head at a station. History lines are language keys with a day and an argument, so each reader sees their own
 * language. A file with number 0 is no file.
 */
public final class StaffFile {
    /** History lines kept; the oldest is dropped first. */
    public static final int HISTORY = 8;
    /** Longest given name, in characters. */
    public static final int NAME = 16;

    /** One history line: its key ({@code staff.headinajar.history.<k>}), the day and one argument (a name, a station). */
    public record Entry(String k, long d, String a) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("k").forGetter(Entry::k),
                Codec.LONG.fieldOf("d").forGetter(Entry::d),
                Codec.STRING.optionalFieldOf("a", "").forGetter(Entry::a)
        ).apply(i, Entry::new));
    }

    /** The day it left the company and why (a reason key, or a killer's name). */
    public record Left(long d, String why) {
        public static final Codec<Left> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("d").forGetter(Left::d),
                Codec.STRING.fieldOf("why").forGetter(Left::why)
        ).apply(i, Left::new));
    }

    public int number;
    public String species = "";
    /** Index into the given-name pool ({@code staff.headinajar.name.<i>}), or -1 when renamed or none. */
    public int nameIndex = -1;
    /** A name its employer gave it (never a vanilla name tag), or null. */
    public String customName;
    public UUID employer;
    /** The employer's name as last seen online. */
    public String employerName = "";
    public long joined;
    public final List<Entry> history = new ArrayList<>();
    public Left left;
    /** 0 to 100 for staff on the payroll; -1 for a trophy or anything with no morale. */
    public int morale = -1;
    /** The last day paid for (-1 never). */
    public long paidThrough = -1;
    /** Dawns running at which morale was under 15. */
    public int lowDawns;
    /** The last day a dawn was run (-1 none). */
    public long lastDawn = -1;
    /** Tasks finished at work (a trip, 8 crops or 4 animals, a hostile seen off, an hour of patrol); grades read it. */
    public int tasks;

    public static final Codec<StaffFile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("number").forGetter(f -> f.number),
            Codec.STRING.optionalFieldOf("species", "").forGetter(f -> f.species),
            Codec.INT.optionalFieldOf("nameIndex", -1).forGetter(f -> f.nameIndex),
            Codec.STRING.optionalFieldOf("customName").forGetter(f -> Optional.ofNullable(f.customName)),
            UUIDUtil.CODEC.optionalFieldOf("employer").forGetter(f -> Optional.ofNullable(f.employer)),
            Codec.STRING.optionalFieldOf("employerName", "").forGetter(f -> f.employerName),
            Codec.LONG.optionalFieldOf("joined", 0L).forGetter(f -> f.joined),
            Entry.CODEC.listOf().optionalFieldOf("history", List.of()).forGetter(f -> f.history),
            Left.CODEC.optionalFieldOf("left").forGetter(f -> Optional.ofNullable(f.left)),
            Codec.INT.optionalFieldOf("morale", -1).forGetter(f -> f.morale),
            Codec.LONG.optionalFieldOf("paidThrough", -1L).forGetter(f -> f.paidThrough),
            Codec.INT.optionalFieldOf("lowDawns", 0).forGetter(f -> f.lowDawns),
            Codec.LONG.optionalFieldOf("lastDawn", -1L).forGetter(f -> f.lastDawn),
            Codec.INT.optionalFieldOf("tasks", 0).forGetter(f -> f.tasks)
    ).apply(i, StaffFile::of));

    public StaffFile() {
    }

    private static StaffFile of(int number, String species, int nameIndex, Optional<String> customName, Optional<UUID> employer, String employerName,
                                long joined, List<Entry> history, Optional<Left> left, int morale, long paidThrough, int lowDawns, long lastDawn, int tasks) {
        StaffFile f = new StaffFile();
        f.number = number;
        f.species = species;
        f.nameIndex = nameIndex;
        f.customName = customName.orElse(null);
        f.employer = employer.orElse(null);
        f.employerName = employerName;
        f.joined = joined;
        f.history.addAll(history);
        while (f.history.size() > HISTORY) f.history.remove(0);
        f.left = left.orElse(null);
        f.morale = morale;
        f.paidThrough = paidThrough;
        f.lowDawns = lowDawns;
        f.lastDawn = lastDawn;
        f.tasks = tasks;
        return f;
    }

    /** A file is real once it has a number. */
    public boolean filed() {
        return number > 0;
    }

    /** On the payroll: it has been paid by hand once (a trophy has no morale). */
    public boolean staff() {
        return morale >= 0;
    }

    public void note(String key, long day, String arg) {
        history.add(new Entry(key, day, arg == null ? "" : arg));
        while (history.size() > HISTORY) history.remove(0);
    }

    public CompoundTag save() {
        Tag tag = CODEC.encodeStart(NbtOps.INSTANCE, this).result().orElse(new CompoundTag());
        return tag instanceof CompoundTag c ? c : new CompoundTag();
    }

    /** A file from saved data, or null when there is none or it does not read. */
    public static StaffFile load(Tag tag) {
        if (!(tag instanceof CompoundTag c) || !c.contains("number")) return null;
        StaffFile f = CODEC.parse(NbtOps.INSTANCE, c).result().orElse(null);
        return f != null && f.filed() ? f : null;
    }

    public StaffFile copy() {
        StaffFile f = load(save());
        return f == null ? new StaffFile() : f;
    }
}
