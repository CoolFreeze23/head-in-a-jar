package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.genetics.BloodType;
import com.coolfreeze23.headinajar.item.GeneticsItems;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.staff.SawTable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.Tags;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * What the Body Printer will print from a Template, and how much of each material it takes. The booth's door is one
 * block wide and two tall, so that is the rule: a model up to 1 block wide and 2 tall goes through it, in one of three
 * material classes (a quarter, a half or all of an own-body print); anything bigger waits for the Mk II booth. Marine
 * models are delivered into water. Creatures from other mods have no class yet, so they are not on the approved list.
 * <p>
 * Shared by both sides: the server decides, the screen shows the same verdict from the synced numbers.
 */
public final class PrintCatalogue {
    /** Under this quality a fraction is too poor to print from. */
    public static final int MIN_QUALITY = 40;
    /** The door: a model must be this narrow and this short to step out of the booth. */
    public static final float DOOR_WIDTH = 1.0f, DOOR_HEIGHT = 2.0f;

    /** A material class: the share of an own-body print's materials, saline and power it takes. */
    public enum Size {
        TINY(1, 4), SMALL(1, 2), MEDIUM(1, 1);

        public final int num, den;

        Size(int num, int den) {
            this.num = num;
            this.den = den;
        }

        /** {@code full} of an own-body print, scaled to this class. */
        public int of(int full) {
            return full * num / den;
        }

        public String key() {
            return "gui.headinajar.printer.size." + name().toLowerCase(java.util.Locale.ROOT);
        }

        /** 1, 2, 3 in the menu's numbers; 0 is no class. */
        public int code() {
            return ordinal() + 1;
        }

        public static Size ofCode(int code) {
            return code >= 1 && code <= values().length ? values()[code - 1] : null;
        }
    }

    private static final Map<ResourceLocation, Size> SIZES = new HashMap<>();

    static {
        put(Size.TINY, "chicken", "rabbit", "bat", "frog", "parrot", "cod", "salmon", "tropical_fish", "pufferfish", "axolotl");
        put(Size.SMALL, "pig", "sheep", "wolf", "fox", "cat", "ocelot", "armadillo", "cave_spider");
        // the book's medium class, less the spider, the panda and the turtle (too wide for the door), plus the strider,
        // the llama and the trader llama (thin enough, and under two blocks tall); the blaze takes no rods in this booth
        put(Size.MEDIUM, "cow", "mooshroom", "goat", "villager", "wandering_trader", "witch", "pillager", "vindicator", "evoker",
                "zombie", "husk", "drowned", "zombie_villager", "skeleton", "stray", "bogged", "creeper", "blaze", "piglin",
                "piglin_brute", "zombified_piglin", "squid", "glow_squid", "dolphin", "guardian", "phantom", "strider", "llama",
                "trader_llama");
    }

    /** Delivered into water: they drown, dry out or flop on a dry doorstep. */
    private static final Set<String> MARINE = Set.of("squid", "glow_squid", "dolphin", "guardian", "cod", "salmon", "tropical_fish",
            "pufferfish", "axolotl");
    /** Burn in daylight with nothing over them (the husk does not). */
    private static final Set<String> BURNS = Set.of("zombie", "drowned", "zombie_villager", "skeleton", "stray", "bogged", "phantom");

    private static void put(Size size, String... paths) {
        for (String path : paths) SIZES.put(ResourceLocation.withDefaultNamespace(path), size);
    }

    private PrintCatalogue() {
    }

    /** The printer's answer for a template: its size class, or a refusal (key under {@code refusal.headinajar.}, arguments). */
    public record Verdict(Size size, EntityType<?> type, int quality, String reason, Object[] args) {
        public boolean ok() {
            return reason == null;
        }

        static Verdict no(EntityType<?> type, int quality, String reason, Object... args) {
            return new Verdict(null, type, quality, reason, args);
        }
    }

    /** The material class of a species, or null when it has none (too big, not flesh, or from another firm). */
    public static Size size(EntityType<?> type) {
        return type == null ? null : SIZES.get(BuiltInRegistries.ENTITY_TYPE.getKey(type));
    }

    public static boolean marine(EntityType<?> type) {
        return MARINE.contains(path(type));
    }

    public static boolean burnsInDaylight(EntityType<?> type) {
        return BURNS.contains(path(type));
    }

    private static String path(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return id.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE) ? id.getPath() : "";
    }

    /** Whether this server licenses specimen printing (its config; a client reads it from the printer's menu). */
    public static boolean licensed() {
        return Config.get(Config.SPECIMEN_PRINTING, true);
    }

    /**
     * Everything about the template itself, in the order the printer reads it: the licence, a fraction at all, its
     * quality, a player, a boss, a thick hide, a golem or spirit, a creature that is all head, a species this world does
     * not have, too tall, too wide, then whether the species has a class. The doorstep's water is the booth's own check
     * ({@link #needsWater}).
     */
    public static Verdict model(ItemStack template, boolean licensed) {
        if (!licensed) return Verdict.no(null, 0, "printer.not_licensed");
        if (!template.is(ModRegistry.DNA_FRACTION.get())) return Verdict.no(null, 0, "printer.not_fraction");
        int quality = GeneticsItems.quality(template);
        EntityType<?> type = GeneticsItems.species(template);
        if (quality < MIN_QUALITY) return Verdict.no(type, quality, "printer.poor", quality);
        if (type == EntityType.PLAYER) return Verdict.no(type, quality, "printer.player");
        if (type != null) {
            if (type == EntityType.ENDER_DRAGON || type == EntityType.WITHER || type.is(Tags.EntityTypes.BOSSES)) return Verdict.no(type, quality, "printer.boss");
            if (type.is(BloodType.THICK_HIDE)) return Verdict.no(type, quality, "printer.licence");
            BloodType blood = BloodType.of(type);
            if (blood == BloodType.OIL || blood == BloodType.ECTO) return Verdict.no(type, quality, "printer.facilities");
            if (type.is(SawTable.HEAD_ONLY)) return Verdict.no(type, quality, "printer.all_head", type.getDescription());
        }
        if (type == null) return Verdict.no(null, quality, "lab.sequencer.unreadable");
        EntityDimensions box = type.getDimensions();
        if (box.height() > DOOR_HEIGHT) return Verdict.no(type, quality, "printer.too_tall");
        if (box.width() > DOOR_WIDTH) return Verdict.no(type, quality, "printer.too_wide");
        Size size = size(type);
        if (size == null) return Verdict.no(type, quality, "printer.not_approved");
        return new Verdict(size, type, quality, null, new Object[0]);
    }

    /** A marine model with a dry doorstep. */
    public static boolean needsWater(EntityType<?> type, Level level, BlockPos door) {
        return marine(type) && !level.getFluidState(door).is(FluidTags.WATER);
    }

    /** Would burn where it is delivered: an undead model at a doorstep open to the daytime sky. */
    public static boolean burnsAt(EntityType<?> type, Level level, BlockPos door) {
        return burnsInDaylight(type) && level.isDay() && level.canSeeSky(door);
    }

    /** The refusal keys the screen can show from a synced number (index 0 is "no refusal"). */
    public static final String[] REASONS = {"", "printer.not_licensed", "printer.not_fraction", "printer.poor", "printer.player",
            "printer.boss", "printer.licence", "printer.facilities", "printer.all_head", "lab.sequencer.unreadable",
            "printer.too_tall", "printer.too_wide", "printer.not_approved"};

    public static int reasonCode(String reason) {
        if (reason == null) return 0;
        for (int i = 1; i < REASONS.length; i++) if (REASONS[i].equals(reason)) return i;
        return 0;
    }
}
