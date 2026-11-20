package com.coolfreeze23.headinajar.mixin;

import com.coolfreeze23.headinajar.block.Treadmills;
import com.coolfreeze23.headinajar.host.Hosts;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A host on a running Treadmill walks in place: on the client its walk is played at one fixed stride instead of from
 * how far it moved this tick (it moves nowhere). The block under its feet says the belt runs, so no packet is needed.
 * The belt scrolls 1 px (1/16 block) a tick toward the foot. A vanilla leg swings by {@code cos(pos * 0.6662) * 1.4 *
 * speed} with its walk position advancing {@code speed} a tick, so a foot at the end of a leg {@code L} blocks long
 * sweeps back at most {@code 0.93 * speed^2 * L} blocks a tick: it keeps pace with the belt at {@code speed =
 * sqrt(0.0625 / (0.93 * L))}, about 0.30 for a 12 px leg (cow, sheep, villager, zombie, horse) and 0.46 for a
 * chicken's 5 px one. The few legs far from 12 px have their own speed; the rest walk at the 12 px one.
 */
@Mixin(LivingEntity.class)
public abstract class WalkInPlaceMixin {
    /** The walk speed (vanilla's walk animation speed, 0..1) whose feet keep pace with the belt, by leg length. */
    private static final float STRIDE = 0.30f;
    /*
     * The horse family's model swings its legs 0.8 (front) and 0.5 (hind) where the others swing 1.4: at the 12 px
     * stride its feet ran at a third to a half of the belt in the film. At 0.44 the front feet go a little faster than
     * the belt and the hind a little slower. A spider sweeps its legs sideways (0.4, twice as often) from 16 px, which at
     * 0.30 works out at about half the belt.
     */
    private static final java.util.Map<net.minecraft.world.entity.EntityType<?>, Float> STRIDES = java.util.Map.ofEntries(
            java.util.Map.entry(net.minecraft.world.entity.EntityType.CHICKEN, 0.46f),   // 5 px legs
            java.util.Map.entry(net.minecraft.world.entity.EntityType.PIG, 0.42f),   // 6 px
            java.util.Map.entry(net.minecraft.world.entity.EntityType.CAT, 0.40f),   // 6-7 px
            java.util.Map.entry(net.minecraft.world.entity.EntityType.OCELOT, 0.40f),
            java.util.Map.entry(net.minecraft.world.entity.EntityType.FOX, 0.42f),
            java.util.Map.entry(net.minecraft.world.entity.EntityType.WOLF, 0.36f),   // 8 px
            java.util.Map.entry(net.minecraft.world.entity.EntityType.LLAMA, 0.28f),   // 14 px
            java.util.Map.entry(net.minecraft.world.entity.EntityType.TRADER_LLAMA, 0.28f),
            java.util.Map.entry(net.minecraft.world.entity.EntityType.CAMEL, 0.24f),   // the longest legs on a belt
            java.util.Map.entry(net.minecraft.world.entity.EntityType.HORSE, 0.44f),   // see above
            java.util.Map.entry(net.minecraft.world.entity.EntityType.DONKEY, 0.44f),
            java.util.Map.entry(net.minecraft.world.entity.EntityType.MULE, 0.44f),
            java.util.Map.entry(net.minecraft.world.entity.EntityType.SKELETON_HORSE, 0.44f),
            java.util.Map.entry(net.minecraft.world.entity.EntityType.ZOMBIE_HORSE, 0.44f),
            java.util.Map.entry(net.minecraft.world.entity.EntityType.SPIDER, 0.45f));   // see above

    @Shadow
    protected abstract void updateWalkAnimation(float distance);

    @Inject(method = "calculateEntityAnimation", at = @At("HEAD"), cancellable = true)
    private void headinajar$walkInPlace(boolean includeHeight, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.level().isClientSide || !Hosts.isHost(self) || !Treadmills.onRunningBelt(self)) return;
        // vanilla turns the distance moved into a speed of min(distance * 4, 1)
        updateWalkAnimation(STRIDES.getOrDefault(self.getType(), STRIDE) / 4f);
        ci.cancel();
    }
}
