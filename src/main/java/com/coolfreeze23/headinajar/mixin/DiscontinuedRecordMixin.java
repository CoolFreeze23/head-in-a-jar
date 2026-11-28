package com.coolfreeze23.headinajar.mixin;

import com.coolfreeze23.headinajar.supplier.Discontinued;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Reads the records the game is about to load, so staff of a species it does not have (and so drops) are noted before
 * they go. Looks only: what loads is unchanged. The game fires no event for a creature it skips.
 */
@Mixin(EntityType.class)
public abstract class DiscontinuedRecordMixin {
    @Inject(method = "loadEntitiesRecursive", at = @At("HEAD"))
    private static void headinajar$chunk(List<? extends Tag> tags, Level level, CallbackInfoReturnable<Stream<Entity>> cir) {
        Discontinued.chunk(tags, level);
    }

    @Inject(method = "loadEntityRecursive", at = @At("HEAD"))
    private static void headinajar$single(CompoundTag tag, Level level, Function<Entity, Entity> function, CallbackInfoReturnable<Entity> cir) {
        Discontinued.single(tag, level);
    }
}
