package com.coolfreeze23.headinajar.genetics;

import com.coolfreeze23.headinajar.HeadInAJar;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;

/**
 * Blood types. Warm, cold and ichor are all "flesh" and mix; the rest only match themselves. Marrow, oil, ecto and sap
 * are bloodless: they need their own collection tool (sap is drawn by the Oil Tap, as oil is). Types come from entity
 * tags {@code headinajar:blood/<type>}; untagged creatures are warm.
 */
public enum BloodType {
    WARM(false), COLD(false), ICHOR(false), ENDER(false), BRIMSTONE(false), GRAVE(false), ARCANE(false),
    MARROW(true), OIL(true), ECTO(true),
    /** 0.8.0: what a creaking runs on. Our competitor's, not ours. */
    SAP(true);

    /** Hides only a Leviathan Needle (or a bloodless tool) gets through: bosses, the Elder Guardian, the Warden. */
    public static final TagKey<EntityType<?>> THICK_HIDE = TagKey.create(Registries.ENTITY_TYPE, HeadInAJar.id("thick_hide"));

    public final boolean bloodless;
    public final TagKey<EntityType<?>> tag;

    BloodType(boolean bloodless) {
        this.bloodless = bloodless;
        this.tag = TagKey.create(Registries.ENTITY_TYPE, HeadInAJar.id("blood/" + name().toLowerCase(Locale.ROOT)));
    }

    public static BloodType of(EntityType<?> type) {
        for (BloodType blood : values()) if (type.is(blood.tag)) return blood;
        return WARM;
    }

    public static BloodType of(LivingEntity entity) {
        if (entity instanceof Player) return WARM;
        return of(entity.getType());
    }

    public boolean flesh() {
        return this == WARM || this == COLD || this == ICHOR;
    }

    public boolean compatible(BloodType other) {
        return this == other || flesh() && other.flesh();
    }

    /** Whether a collection tool of this kind draws from a creature of {@code blood} (a syringe is WARM: any blood). */
    public boolean draws(BloodType blood) {
        if (!bloodless) return !blood.bloodless;
        return blood == this || this == OIL && blood == SAP;
    }
}
