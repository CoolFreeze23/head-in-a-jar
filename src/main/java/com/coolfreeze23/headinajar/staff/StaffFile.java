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
    /** Its career (0.7.2): grade, reviews, grievances, the Board. A file saved before has the defaults. */
    public Career career = new Career();

    /**
     * The career half of a file (0.7.2), saved as one field ({@code career}) so a file saved by 0.7.1 reads with every
     * value at its default: an Intern, never reviewed, no grievance, never promoted.
     */
    public static final class Career {
        /** 0 Intern, 1 Associate, 2 Senior Associate, 3 Team Lead ({@code staff.Grades}). */
        public int grade;
        /** {@link StaffFile#tasks} at its last review. */
        public int tasksAtReview;
        /** The day of its last review (-1 never). */
        public long reviewedDay = -1;
        /** Grievances filed and dawns gone unpaid since its last review (the slip's counts). */
        public int grievancesSinceReview, unpaidSinceReview;
        /** Dawns running at which it went unpaid (0 once paid); the strike test reads it. */
        public int unpaidRun;
        /** The day it was promoted to the Board (-1 never): set on the head; its body's file names the head's number. */
        public long promotedDay = -1;
        /** The staff number of its other half after a promotion (the head's on the body's file, the body's on the head's), 0 none. */
        public int link;
        /** A Grievance is open (Form 66-G): it faces the wall and does no work until morale is back to 40. */
        public boolean grievance;
        /** The last day a living worker had cake (-1 never): one a day, as a plinth head's. */
        public long cakeDay = -1;

        public static final Codec<Career> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("grade", 0).forGetter(c -> c.grade),
                Codec.INT.optionalFieldOf("tasksAtReview", 0).forGetter(c -> c.tasksAtReview),
                Codec.LONG.optionalFieldOf("reviewedDay", -1L).forGetter(c -> c.reviewedDay),
                Codec.INT.optionalFieldOf("grievances", 0).forGetter(c -> c.grievancesSinceReview),
                Codec.INT.optionalFieldOf("unpaid", 0).forGetter(c -> c.unpaidSinceReview),
                Codec.INT.optionalFieldOf("unpaidRun", 0).forGetter(c -> c.unpaidRun),
                Codec.LONG.optionalFieldOf("promotedDay", -1L).forGetter(c -> c.promotedDay),
                Codec.INT.optionalFieldOf("link", 0).forGetter(c -> c.link),
                Codec.BOOL.optionalFieldOf("grievance", false).forGetter(c -> c.grievance),
                Codec.LONG.optionalFieldOf("cakeDay", -1L).forGetter(c -> c.cakeDay)
        ).apply(i, (grade, tasksAtReview, reviewedDay, grievances, unpaid, unpaidRun, promotedDay, link, grievance, cakeDay) -> {
            Career c = new Career();
            c.grade = Math.max(0, Math.min(3, grade));
            c.tasksAtReview = tasksAtReview;
            c.reviewedDay = reviewedDay;
            c.grievancesSinceReview = grievances;
            c.unpaidSinceReview = unpaid;
            c.unpaidRun = unpaidRun;
            c.promotedDay = promotedDay;
            c.link = link;
            c.grievance = grievance;
            c.cakeDay = cakeDay;
            return c;
        }));

        /** Whether every value is its default (then nothing is saved: an old file stays byte for byte as it was). */
        public boolean blank() {
            return grade == 0 && tasksAtReview == 0 && reviewedDay == -1 && grievancesSinceReview == 0 && unpaidSinceReview == 0
                    && unpaidRun == 0 && promotedDay == -1 && link == 0 && !grievance && cakeDay == -1;
        }
    }

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
            Codec.INT.optionalFieldOf("tasks", 0).forGetter(f -> f.tasks),
            Career.CODEC.optionalFieldOf("career").forGetter(f -> f.career == null || f.career.blank() ? Optional.empty() : Optional.of(f.career))
    ).apply(i, StaffFile::of));

    public StaffFile() {
    }

    private static StaffFile of(int number, String species, int nameIndex, Optional<String> customName, Optional<UUID> employer, String employerName,
                                long joined, List<Entry> history, Optional<Left> left, int morale, long paidThrough, int lowDawns, long lastDawn, int tasks,
                                Optional<Career> career) {
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
        f.career = career.orElseGet(Career::new);
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
