package com.coolfreeze23.headinajar.mecha;

import com.coolfreeze23.headinajar.entity.HeldBlockEntity;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Mecha arms: left and right mouse buttons. Each module acts on press, while held, or on release. */
public final class MechaArms {
    private MechaArms() {
    }

    public static void tick(MechaEntity m, ServerPlayer pilot, int side, boolean held, boolean pressed, boolean released) {
        MechaModule arm = m.arm(side);
        MechaLogic.ArmState s = m.armState[side];
        if (s.cooldown > 0) s.cooldown--;
        if (arm == null) return;
        float eff = MechaLogic.effectiveness(m);
        if (pressed) MechaSystems.revealed(m);
        if (released) release(m, side);
        if (eff <= 0) return;
        switch (arm) {
            case DRILL -> {
                if (held) drill(m, pilot, side, eff);
                else s.target = null;
            }
            case CHAINSAW -> {
                if (pressed) {
                    Entity target = MechaLogic.lookEntity(pilot, m, 4);
                    if (target instanceof LivingEntity victim && m.spend(40)) {
                        victim.hurt(m.serverLevel().damageSources().mobAttack(m), 8 * eff);
                        m.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1, 0.6f);
                        return;
                    }
                }
                if (held) chainsaw(m, pilot, side, eff);
            }
            case HYDRAULIC_FIST -> {
                if (!pressed || s.cooldown > 0) return;
                Entity target = MechaLogic.lookEntity(pilot, m, 4.5);
                int force = m.armSlider(side);
                if (target instanceof LivingEntity victim && m.spend(300 + 12 * force)) {
                    victim.hurt(m.serverLevel().damageSources().mobAttack(m), (10 + 0.12f * force) * eff);
                    Vec3 push = pilot.getLookAngle().multiply(1, 0, 1).normalize();
                    victim.knockback(1 + force / 50.0, -push.x, -push.z);
                    m.setHeat(m.heat() + 4 * force / 100f);
                    m.playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.2f, 0.7f);
                    s.cooldown = 12;
                }
            }
            case GRAPPLE_HOOK -> {
                if (!pressed || s.cooldown > 0 || !m.spend(800)) return;
                s.cooldown = 20;
                Entity target = MechaLogic.lookEntity(pilot, m, 32);
                if (target != null) {
                    Vec3 pull = m.position().subtract(target.position()).normalize().scale(1.4);
                    target.setDeltaMovement(pull.add(0, 0.3, 0));
                    target.hurtMarked = true;
                } else {
                    BlockHitResult hit = MechaLogic.lookBlock(pilot, 32);
                    if (MechaLogic.miss(hit)) return;
                    Vec3 pull = hit.getLocation().subtract(m.position()).normalize().scale(1.6);
                    m.setDeltaMovement(pull.add(0, 0.35, 0));
                    m.hurtMarked = true;
                }
                m.playSound(SoundEvents.FISHING_BOBBER_THROW, 1, 0.5f);
            }
            case TRACTOR_BEAM -> beam(m, pilot, side, held, pressed);
            case RIVET_GUN -> {
                if (!held || s.cooldown > 0) return;
                if (!takeAmmo(m, pilot) || !m.spend(30)) return;
                s.cooldown = 4;
                Vec3 look = pilot.getLookAngle();
                Arrow rivet = new Arrow(m.serverLevel(), m, new ItemStack(Items.ARROW), null);
                rivet.setPos(pilot.getEyePosition().add(look.scale(1.2)));
                rivet.setBaseDamage(1.0 * eff);
                rivet.pickup = AbstractArrow.Pickup.DISALLOWED;
                rivet.shoot(look.x, look.y, look.z, 3.5f, 1.5f);
                m.serverLevel().addFreshEntity(rivet);
                m.setHeat(m.heat() + 0.5f);
                m.playSound(SoundEvents.CROSSBOW_SHOOT, 0.6f, 1.8f);
            }
            case MANIPULATOR_HAND -> {
                if (pressed && s.cooldown <= 0 && m.spend(50)) {
                    s.cooldown = 5;
                    hand(m, pilot);
                }
            }
            case EXCAVATOR_SCOOP -> {
                if (held && s.cooldown <= 0) {
                    s.cooldown = 4;
                    scoop(m, pilot);
                }
            }
            case FLAMETHROWER -> {
                if (held && m.spend(60)) flame(m, pilot, side, eff);
            }
            case ARC_CANNON -> {
                if (pressed && s.cooldown <= 0 && m.battery() >= 6000) {
                    m.spend(6000);
                    s.cooldown = 10 + 30 * m.armSlider(side) / 100;
                    arc(m, pilot, (8 + 8 * m.armSlider(side) / 100f) * eff);
                }
            }
            case HEMO_SYRINGE -> {
                if (pressed && s.cooldown <= 0 && m.spend(20)) {
                    s.cooldown = 10;
                    hemo(m, pilot, eff);
                }
            }
            case HARVESTER -> {
                if (pressed && s.cooldown <= 0) {
                    s.cooldown = 10;
                    harvest(m, pilot);
                }
            }
            case WELDING_TORCH -> {
                if (held && m.spend(80)) weld(m, pilot, eff);
            }
            case PLASMA_LANCE -> {
                if (held && m.spend(600)) lance(m, pilot, eff);
            }
            default -> {
            }
        }
    }

    /** Manipulator Hand: uses the pilot's selected hotbar item like a hand would (on a block, a creature, or the air). */
    private static void hand(MechaEntity m, ServerPlayer pilot) {
        ItemStack stack = pilot.getMainHandItem();
        Entity target = MechaLogic.lookEntity(pilot, m, 6);
        if (target != null) {
            pilot.attack(target);
            return;
        }
        BlockHitResult hit = MechaLogic.lookBlock(pilot, 6);
        if (hit.getType() == HitResult.Type.BLOCK) {
            pilot.gameMode.useItemOn(pilot, m.serverLevel(), stack, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        } else if (!stack.isEmpty()) {
            pilot.gameMode.useItem(pilot, m.serverLevel(), stack, net.minecraft.world.InteractionHand.MAIN_HAND);
        }
    }

    /** Excavator Scoop: 3x3 of shovel blocks at a time, straight into the Cargo Hold. */
    private static void scoop(MechaEntity m, ServerPlayer pilot) {
        BlockHitResult hit = MechaLogic.lookBlock(pilot, 6);
        if (MechaLogic.miss(hit)) return;
        int dug = 0;
        for (BlockPos p : square(hit.getBlockPos(), hit.getDirection())) {
            BlockState state = m.serverLevel().getBlockState(p);
            if (!state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_SHOVEL) || m.battery() < 20) continue;
            if (MechaLogic.breakAs(m, pilot, p)) {
                m.spend(20);
                dug++;
            }
        }
        if (dug > 0) m.playSound(SoundEvents.GRAVEL_BREAK, 1, 0.7f);
    }

    /** Flamethrower: a 6-block cone that sets creatures alight. */
    private static void flame(MechaEntity m, ServerPlayer pilot, int side, float eff) {
        ServerLevel level = m.serverLevel();
        Vec3 eye = pilot.getEyePosition(), look = pilot.getLookAngle();
        double cone = 0.95 - 0.25 * m.armSlider(side) / 100.0;
        for (int i = 0; i < 4; i++) {
            Vec3 p = eye.add(look.scale(1.5 + level.random.nextDouble() * 4.5));
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, p.x, p.y, p.z, 2, 0.2, 0.2, 0.2, 0.02);
        }
        if (m.tickCount % 5 == 0) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, m.getBoundingBox().inflate(7), e -> e != m && e != pilot && e.getVehicle() != m)) {
                Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0).subtract(eye);
                if (to.length() > 7 || to.normalize().dot(look) < cone) continue;
                e.igniteForSeconds(4);
                e.hurt(level.damageSources().inFire(), 1 * eff);
            }
        }
        m.setHeat(m.heat() + 8);
    }

    /** Arc Cannon: a hitscan bolt to the first creature in 16 blocks that jumps to up to three hostile ones nearby. */
    private static void arc(MechaEntity m, ServerPlayer pilot, float damage) {
        ServerLevel level = m.serverLevel();
        Entity first = MechaLogic.lookEntity(pilot, m, 16);
        m.setHeat(m.heat() + 25);
        m.playSound(SoundEvents.LIGHTNING_BOLT_IMPACT, 1, 1.6f);
        if (!(first instanceof LivingEntity victim)) return;
        java.util.Set<Entity> hit = new java.util.HashSet<>();
        LivingEntity from = pilot, at = victim;
        for (int jump = 0; jump < 4 && at != null; jump++) {
            hit.add(at);
            at.hurt(level.damageSources().lightningBolt(), damage);
            Vec3 a = from.getEyePosition(), b = at.getEyePosition();
            for (int i = 0; i <= 10; i++) {
                Vec3 p = a.lerp(b, i / 10.0);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
            }
            from = at;
            LivingEntity last = at;
            at = level.getEntitiesOfClass(LivingEntity.class, at.getBoundingBox().inflate(4),
                            e -> !hit.contains(e) && e != m && e != pilot && e instanceof net.minecraft.world.entity.monster.Enemy)
                    .stream().min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(last))).orElse(null);
        }
    }

    /** Hemo-Syringe: a jab that draws a Blood Sample (quality 50-80) and patches the frame with 10% of the damage. */
    private static void hemo(MechaEntity m, ServerPlayer pilot, float eff) {
        Entity target = MechaLogic.lookEntity(pilot, m, 4);
        if (!(target instanceof LivingEntity victim) || target instanceof net.minecraft.world.entity.player.Player) return;
        float damage = 4 * eff;
        if (!victim.hurt(m.serverLevel().damageSources().mobAttack(m), damage)) return;
        m.heal(damage * 0.1f);
        var blood = com.coolfreeze23.headinajar.genetics.BloodType.of(victim);
        if (blood.bloodless) return;
        int quality = com.coolfreeze23.headinajar.item.GeneticsItems.SyringeItem.quality(victim, 50 + m.getRandom().nextInt(31));
        ItemStack sample = com.coolfreeze23.headinajar.item.GeneticsItems.sample(com.coolfreeze23.headinajar.registry.ModRegistry.BLOOD_SAMPLE.get(), victim.getType(), quality);
        ItemStack left = m.has(MechaModule.CARGO_HOLD) ? m.cargo().addItem(sample) : sample;
        if (!left.isEmpty()) com.coolfreeze23.headinajar.form.FormActions.give(pilot, left);
        m.playSound(SoundEvents.BEE_STING, 1, 0.8f);
    }

    /** Harvester: ripe crops in a 5x5 around the block you look at are picked and replanted. */
    private static void harvest(MechaEntity m, ServerPlayer pilot) {
        BlockHitResult hit = MechaLogic.lookBlock(pilot, 8);
        if (MechaLogic.miss(hit)) return;
        ServerLevel level = m.serverLevel();
        for (BlockPos p : BlockPos.betweenClosed(hit.getBlockPos().offset(-2, -1, -2), hit.getBlockPos().offset(2, 1, 2))) {
            BlockState state = level.getBlockState(p);
            if (!(state.getBlock() instanceof net.minecraft.world.level.block.CropBlock crop) || !crop.isMaxAge(state) || m.battery() < 10) continue;
            if (!level.mayInteract(pilot, p)) continue;
            BlockPos at = p.immutable();
            var drops = net.minecraft.world.level.block.Block.getDrops(state, level, at, null, pilot, ItemStack.EMPTY);
            ItemStack seed = crop.getCloneItemStack(level, at, state);
            level.setBlock(at, crop.getStateForAge(0), 3);
            m.spend(10);
            boolean seedTaken = false;
            for (ItemStack drop : drops) {
                // replanting costs one seed
                if (!seedTaken && drop.is(seed.getItem())) {
                    drop.shrink(1);
                    seedTaken = true;
                }
                if (drop.isEmpty()) continue;
                ItemStack left = m.has(MechaModule.CARGO_HOLD) ? m.cargo().addItem(drop) : drop;
                if (!left.isEmpty()) net.minecraft.world.level.block.Block.popResource(level, at, left);
            }
        }
    }

    /** Welding Torch: repairs the mecha you look at, or your own, 2 HP/s for Mecha Plating from the hold (1 per 20 HP). */
    private static void weld(MechaEntity m, ServerPlayer pilot, float eff) {
        Entity looked = MechaLogic.lookEntity(pilot, m, 5);
        MechaEntity target = looked instanceof MechaEntity other ? other : m;
        m.setHeat(m.heat() + 6);
        if (m.tickCount % 10 != 0 || target.getHealth() >= target.getMaxHealth()) return;
        var data = m.getPersistentData();
        int credit = data.getInt("headinajar_weld");
        if (credit <= 0) {
            boolean plate = false;
            for (int i = 0; i < m.cargo().getContainerSize() && !plate; i++) {
                if (m.cargo().getItem(i).is(com.coolfreeze23.headinajar.registry.ModRegistry.MECHA_PLATING.get())) {
                    m.cargo().removeItem(i, 1);
                    plate = true;
                }
            }
            if (!plate) return;
            credit = 20;
        }
        target.heal(1 * eff);
        data.putInt("headinajar_weld", credit - 1);
        m.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA, target.getX(), target.getY() + target.getBbHeight() * 0.5,
                target.getZ(), 2, 0.4, 0.4, 0.4, 0);
    }

    /** Plasma Lance: a 32-block beam, 20 damage a second, that sets things alight. */
    private static void lance(MechaEntity m, ServerPlayer pilot, float eff) {
        ServerLevel level = m.serverLevel();
        Vec3 eye = pilot.getEyePosition(), look = pilot.getLookAngle();
        Entity target = MechaLogic.lookEntity(pilot, m, 32);
        BlockHitResult block = MechaLogic.lookBlock(pilot, 32);
        Vec3 end = target != null ? target.position().add(0, target.getBbHeight() / 2, 0) : block.getLocation();
        for (int i = 2; i < (int) eye.distanceTo(end); i += 2) {
            Vec3 p = eye.add(look.scale(i));
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
        if (target instanceof LivingEntity victim) {
            victim.hurt(level.damageSources().indirectMagic(m, pilot), 1 * eff);
            victim.igniteForSeconds(3);
        }
        m.setHeat(m.heat() + 15);
    }

    private static boolean takeAmmo(MechaEntity m, ServerPlayer pilot) {
        for (var container : new net.minecraft.world.Container[]{m.cargo(), pilot.getInventory()}) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (container.getItem(i).is(Items.IRON_NUGGET)) {
                    container.removeItem(i, 1);
                    return true;
                }
            }
        }
        return false;
    }

    private static void drill(MechaEntity m, ServerPlayer pilot, int side, float eff) {
        MechaLogic.ArmState s = m.armState[side];
        BlockHitResult hit = MechaLogic.lookBlock(pilot, 6);
        if (MechaLogic.miss(hit)) {
            s.target = null;
            return;
        }
        BlockPos pos = hit.getBlockPos();
        if (!pos.equals(s.target)) {
            s.target = pos;
            s.progress = 0;
        }
        ServerLevel level = m.serverLevel();
        BlockState state = level.getBlockState(pos);
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0) return;
        int torque = m.armSlider(side);
        boolean area = torque >= 50;
        // torque buys speed; the 3x3 head is half as fast per block
        float speed = (4 + 12 * (torque / 100f)) * eff * (area ? 0.5f : 1f);
        s.progress += speed / Math.max(0.5f, hardness * 30f);
        if (m.tickCount % 4 == 0) level.levelEvent(2001, pos, net.minecraft.world.level.block.Block.getId(state));
        if (s.progress < 1) return;
        s.progress = 0;
        s.target = null;
        int broken = 0;
        for (BlockPos p : area ? square(pos, hit.getDirection()) : java.util.List.of(pos)) {
            if (m.battery() < 50) break;
            if (MechaLogic.breakAs(m, pilot, p)) {
                m.spend(50);
                broken++;
            }
        }
        m.setHeat(m.heat() + broken);
    }

    private static Iterable<BlockPos> square(BlockPos center, Direction face) {
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                out.add(switch (face.getAxis()) {
                    case X -> center.offset(0, a, b);
                    case Y -> center.offset(a, 0, b);
                    case Z -> center.offset(a, b, 0);
                });
            }
        }
        return out;
    }

    private static void chainsaw(MechaEntity m, ServerPlayer pilot, int side, float eff) {
        MechaLogic.ArmState s = m.armState[side];
        BlockHitResult hit = MechaLogic.lookBlock(pilot, 5);
        if (MechaLogic.miss(hit) || !m.serverLevel().getBlockState(hit.getBlockPos()).is(net.minecraft.tags.BlockTags.LOGS)) {
            s.target = null;
            return;
        }
        if (!hit.getBlockPos().equals(s.target)) {
            s.target = hit.getBlockPos();
            s.progress = 0;
        }
        s.progress += 0.08f * eff;
        if (s.progress < 1) return;
        s.target = null;
        int felled = 0;
        for (BlockPos log : MechaLogic.tree(m.serverLevel(), hit.getBlockPos())) {
            if (m.battery() < 40) break;
            if (MechaLogic.breakAs(m, pilot, log)) {
                m.spend(40);
                felled++;
            }
        }
        m.setHeat(m.heat() + felled * 0.5f);
        m.playSound(SoundEvents.AXE_STRIP, 1.2f, 0.6f);
    }

    /** Tractor Beam: v1 telekinesis at mecha scale (24 blocks). Grab on press, carry while held, drop on release. */
    private static void beam(MechaEntity m, ServerPlayer pilot, int side, boolean holding, boolean pressed) {
        MechaLogic.ArmState s = m.armState[side];
        ServerLevel level = m.serverLevel();
        if (pressed && s.held < 0) {
            Entity target = MechaLogic.lookEntity(pilot, m, 24);
            if (target == null) {
                BlockHitResult hit = MechaLogic.lookBlock(pilot, 24);
                if (hit.getType() == HitResult.Type.BLOCK) {
                    BlockState state = level.getBlockState(hit.getBlockPos());
                    if (!state.hasBlockEntity() && state.getDestroySpeed(level, hit.getBlockPos()) >= 0 && state.getDestroySpeed(level, hit.getBlockPos()) <= 50
                            && !net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(level, hit.getBlockPos(), state, pilot)).isCanceled()) {
                        level.removeBlock(hit.getBlockPos(), false);
                        target = HeldBlockEntity.lift(level, hit.getBlockPos(), state);
                        level.addFreshEntity(target);
                    }
                }
            }
            if (target != null) {
                s.held = target.getId();
                target.setNoGravity(true);
            }
        }
        if (s.held < 0) return;
        Entity held = level.getEntity(s.held);
        if (held == null || !held.isAlive() || !m.spend(80)) {
            release(m, side);
            return;
        }
        Vec3 target = pilot.getEyePosition().add(pilot.getLookAngle().scale(4 + held.getBbWidth())).subtract(0, held.getBbHeight() / 2, 0);
        held.setDeltaMovement(target.subtract(held.position()).scale(0.35));
        held.hurtMarked = true;
        held.fallDistance = 0;
        if (held instanceof HeldBlockEntity block) block.keepHeld();
        m.setHeat(m.heat() + 3 * 0.05f);
    }

    /** Lets go of anything the arm carries. */
    public static void release(MechaEntity m, int side) {
        MechaLogic.ArmState s = m.armState[side];
        s.target = null;
        if (s.held >= 0) {
            Entity held = m.level().getEntity(s.held);
            if (held instanceof HeldBlockEntity block) block.drop(block.getDeltaMovement().scale(0.5), false);
            else if (held != null) held.setNoGravity(false);
            s.held = -1;
        }
    }
}
