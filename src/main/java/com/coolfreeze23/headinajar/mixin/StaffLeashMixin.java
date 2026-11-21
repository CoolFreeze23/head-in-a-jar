package com.coolfreeze23.headinajar.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.npc.AbstractVillager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Staff can be led on a lead, whatever their kind (vanilla refuses monsters, villagers, pandas, turtles and an angry
 * wolf); a worker who is let go is back under vanilla's rule.
 */
@Mixin({Mob.class, AbstractVillager.class, WaterAnimal.class, Wolf.class, Panda.class, Turtle.class})
public abstract class StaffLeashMixin {
    @Inject(method = "canBeLeashed", at = @At("HEAD"), cancellable = true)
    private void headinajar$staffOnALead(CallbackInfoReturnable<Boolean> cir) {
        if (com.coolfreeze23.headinajar.staff.work.Workforce.isWorker((Mob) (Object) this)) cir.setReturnValue(true);
    }
}
