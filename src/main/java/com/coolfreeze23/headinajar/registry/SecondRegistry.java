package com.coolfreeze23.headinajar.registry;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.entity.RedundancyPackageEntity;
import com.coolfreeze23.headinajar.item.SecondmentCollarItem;
import com.coolfreeze23.headinajar.staff.second.SecondeeData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.function.Supplier;

/**
 * The secondment's entries (0.7.1): the collar a host wears ({@code headinajar:secondee}), the Secondment Collar item,
 * the Redundancy Package (the falling head under its canopy) and the clasp's sound. Loaded from
 * {@code ModRegistry.register}, before its registers are handed to the bus.
 */
public final class SecondRegistry {
    /** A host wearing a Secondment Collar (saved with it; none, or no collar: an ordinary host). */
    public static final Supplier<AttachmentType<SecondeeData>> SECONDEE = ModRegistry.ATTACHMENTS.register("secondee",
            () -> AttachmentType.builder(SecondeeData::new).serialize(SecondeeData.CODEC, d -> d.collar).build());

    public static final DeferredItem<Item> SECONDMENT_COLLAR = ModRegistry.tabbed(ModRegistry.ITEMS.register("secondment_collar",
            () -> new SecondmentCollarItem(new Item.Properties().stacksTo(16))));

    /** A seconded head on its way down after its body died: no gravity of its own, never hurt, never picked up. */
    public static final DeferredHolder<EntityType<?>, EntityType<RedundancyPackageEntity>> REDUNDANCY_PACKAGE = ModRegistry.ENTITIES.register("redundancy_package",
            () -> EntityType.Builder.<RedundancyPackageEntity>of(RedundancyPackageEntity::new, MobCategory.MISC).sized(0.6f, 0.8f)
                    .fireImmune().clientTrackingRange(8).updateInterval(1).build("redundancy_package"));

    /** The collar's clasp. */
    public static final DeferredHolder<SoundEvent, SoundEvent> COLLAR_FITTED = ModSounds.SOUNDS.register("staff.collar_fitted",
            () -> SoundEvent.createVariableRangeEvent(HeadInAJar.id("staff.collar_fitted")));

    private SecondRegistry() {
    }

    static void register(IEventBus bus) {
    }
}
