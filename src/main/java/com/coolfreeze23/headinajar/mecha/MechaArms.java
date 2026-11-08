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
            default -> {
            }
        }
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
