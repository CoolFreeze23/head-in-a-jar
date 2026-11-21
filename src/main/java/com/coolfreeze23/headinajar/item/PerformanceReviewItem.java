package com.coolfreeze23.headinajar.item;

import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.Grades;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffFile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * The Performance Review (0.7.2): the slip one Paper becomes when a worker is reviewed at the Time Clock's Roster. Read
 * by hovering. Everything on it is kept as numbers and keys ({@link Slip}, data component {@code headinajar:review}), so
 * each reader sees it in their own language: who was reviewed, the counts since the last review, strengths and areas for
 * growth (the species' own pair, else its department's), the rating, the grade, the reviewer and the day. A slip with no
 * data (a blank, from the creative tab) says what it is for.
 */
public class PerformanceReviewItem extends Item {
    /** The ratings, in the order they are checked after the Performance Plan. */
    public static final int EXCEEDS = 0, MEETS = 1, NEEDS = 2, PLAN = 3;
    private static final String[] RATINGS = {"exceeds", "meets", "needs", "plan"};
    private static final String SLIP = "item.headinajar.performance_review.";

    public PerformanceReviewItem(Properties properties) {
        super(properties);
    }

    /** What a slip says. {@code tasks}, {@code unpaid} and {@code grievances} are since the last review. */
    public record Slip(int number, String species, int nameIndex, String customName, String department, int tasks, int unpaid,
                       int grievances, int rating, int grade, boolean raised, String reviewer, long day) {
        public static final Codec<Slip> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("number").forGetter(Slip::number),
                Codec.STRING.optionalFieldOf("species", "").forGetter(Slip::species),
                Codec.INT.optionalFieldOf("nameIndex", 0).forGetter(Slip::nameIndex),
                Codec.STRING.optionalFieldOf("customName", "").forGetter(Slip::customName),
                Codec.STRING.optionalFieldOf("department", Staff.GENERAL).forGetter(Slip::department),
                Codec.INT.optionalFieldOf("tasks", 0).forGetter(Slip::tasks),
                Codec.INT.optionalFieldOf("unpaid", 0).forGetter(Slip::unpaid),
                Codec.INT.optionalFieldOf("grievances", 0).forGetter(Slip::grievances),
                Codec.INT.optionalFieldOf("rating", NEEDS).forGetter(Slip::rating),
                Codec.INT.optionalFieldOf("grade", 0).forGetter(Slip::grade),
                Codec.BOOL.optionalFieldOf("raised", false).forGetter(Slip::raised),
                Codec.STRING.optionalFieldOf("reviewer", "").forGetter(Slip::reviewer),
                Codec.LONG.optionalFieldOf("day", 0L).forGetter(Slip::day)
        ).apply(i, Slip::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Slip> STREAM = StreamCodec.of((buf, s) -> {
            buf.writeVarInt(s.number);
            buf.writeUtf(s.species, 128);
            buf.writeVarInt(s.nameIndex);
            buf.writeUtf(s.customName, 64);
            buf.writeUtf(s.department, 64);
            buf.writeVarInt(s.tasks);
            buf.writeVarInt(s.unpaid);
            buf.writeVarInt(s.grievances);
            buf.writeByte(s.rating);
            buf.writeByte(s.grade);
            buf.writeBoolean(s.raised);
            buf.writeUtf(s.reviewer, 64);
            buf.writeVarLong(s.day);
        }, buf -> new Slip(buf.readVarInt(), buf.readUtf(128), buf.readVarInt(), buf.readUtf(64), buf.readUtf(64), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readByte(), buf.readByte(), buf.readBoolean(), buf.readUtf(64), buf.readVarLong()));

        /** Nothing done since the last review (and no Grievance either): the empty review. */
        public boolean empty() {
            return tasks == 0 && grievances == 0;
        }
    }

    /** A slip for a file as reviewed now (its counts read before the review resets them). */
    public static ItemStack of(StaffFile f, String department, int rating, int grade, boolean raised, String reviewer, long day) {
        ItemStack stack = new ItemStack(WorkRegistry.PERFORMANCE_REVIEW.get());
        stack.set(WorkRegistry.REVIEW.get(), new Slip(f.number, f.species, f.nameIndex, f.customName == null ? "" : f.customName, department,
                Math.max(0, f.tasks - f.career.tasksAtReview), f.career.unpaidSinceReview, f.career.grievancesSinceReview, rating, grade, raised,
                reviewer, day));
        return stack;
    }

    public static Component rating(int rating) {
        return Component.translatable("staff.headinajar.rating." + RATINGS[Math.max(0, Math.min(PLAN, rating))]);
    }

    /**
     * The slip's strengths or areas for growth ({@code strength} / {@code growth}): the species' own pair where it has
     * one, else its department's, else General Staff's.
     */
    static Component pair(String which, Slip s) {
        Language lang = Language.getInstance();
        ResourceLocation id = ResourceLocation.tryParse(s.species);
        String own = "staff.headinajar." + which + "." + (id == null ? "" : id.getPath());
        if (id != null && lang.has(own)) return Component.translatable(own);
        String dept = "staff.headinajar." + which + ".dept." + s.department;
        return Component.translatable(lang.has(dept) ? dept : "staff.headinajar." + which + ".dept." + Staff.GENERAL);
    }

    /** Used on a creature: the slip is read, not handed over (0.7.2; the air is answered in {@code ItemAnswers}). */
    @Override
    public net.minecraft.world.InteractionResult interactLivingEntity(ItemStack stack, net.minecraft.world.entity.player.Player player,
                                                                       net.minecraft.world.entity.LivingEntity target, net.minecraft.world.InteractionHand hand) {
        if (player instanceof net.minecraft.server.level.ServerPlayer server) {
            com.coolfreeze23.headinajar.feedback.Refusals.deny(server, com.coolfreeze23.headinajar.feedback.Refuser.at(target), "review.read");
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Slip s = stack.get(WorkRegistry.REVIEW.get());
        if (s == null) {
            tooltip.add(Component.translatable(SLIP + "blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        StaffFile f = new StaffFile();
        f.number = s.number;
        f.species = s.species;
        f.nameIndex = s.nameIndex;
        f.customName = s.customName.isEmpty() ? null : s.customName;
        tooltip.add(Component.translatable(SLIP + "header", Staff.number(s.number), Staff.name(f), Staff.species(f), Staff.departmentName(s.department))
                .withStyle(ChatFormatting.GRAY));
        if (s.empty()) {
            tooltip.add(Component.translatable(SLIP + "empty").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable(SLIP + "counts", s.tasks, s.unpaid, s.grievances).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable(SLIP + "lines", pair("strength", s), pair("growth", s)).withStyle(ChatFormatting.GRAY));
        // the empty review's rating is in its own line; a grade it raised all the same is still said
        if (!s.empty() || s.raised) {
            Component rating = rating(s.rating).copy().withStyle(s.rating == PLAN ? ChatFormatting.RED : ChatFormatting.WHITE);
            tooltip.add(Component.translatable(SLIP + (s.raised ? "rating" : "rating_same"), rating, Grades.name(s.grade)).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable(SLIP + "reviewer", s.reviewer).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(SLIP + "day", Staff.shown(s.day)).withStyle(ChatFormatting.DARK_GRAY));
    }
}
