package com.coolfreeze23.headinajar.form;

import com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.item.QuantumItems;
import com.coolfreeze23.headinajar.mecha.MechaLogic;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * An electromagnetic pulse: breaks neuro-links (unless the Collar Cam has Shielded Housing), drains jars and mecha, and
 * knocks turrets offline. Thrown as an EMP Grenade.
 */
public final class Emp {
    public static final double RADIUS = 8;

    private Emp() {
    }

    /** Fires a pulse; returns how many things it hit. */
    public static int burst(ServerLevel level, Vec3 at, double radius) {
        int hit = 0;
        AABB box = new AABB(at, at).inflate(radius);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 80, radius / 3, radius / 3, radius / 3, 0.2);
        level.playSound(null, at.x, at.y, at.z, com.coolfreeze23.headinajar.fx.HouseSounds.EMP_BURST.get(), SoundSource.BLOCKS, 2, 0.5f);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, box, p -> p.position().distanceTo(at) <= radius)) {
            JarState state = FormActions.state(p);
            if (QuantumItems.has(p, QuantumItems.Upgrade.SHIELDED_HOUSING)) continue;
            if (state.form() == Form.LINKED) {
                FormActions.linkedToJar(p);
                p.displayClientMessage(Component.translatable("message.headinajar.emp"), true);
                hit++;
            } else if (state.form().isJarred()) {
                state.setCharge(state.charge() / 2);
                Network.sync(p);
                hit++;
            }
        }
        for (MechaEntity m : level.getEntitiesOfClass(MechaEntity.class, box)) {
            m.setBattery(m.battery() / 2);
            if (m.vent() == 0) m.setVent(MechaLogic.VENT_TICKS);
            hit++;
        }
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(at).offset(-(int) radius, -(int) radius, -(int) radius),
                BlockPos.containing(at).offset((int) radius, (int) radius, (int) radius))) {
            if (level.getBlockEntity(pos) instanceof TurretPedestalBlockEntity turret) {
                turret.energy().set(0);
                hit++;
            }
        }
        return hit;
    }

    /** A thrown EMP Grenade. */
    public static class Grenade extends ThrowableItemProjectile {
        public Grenade(EntityType<? extends Grenade> type, Level level) {
            super(type, level);
        }

        public Grenade(Level level, LivingEntity thrower) {
            super(ModRegistry.EMP_GRENADE_ENTITY.get(), thrower, level);
        }

        @Override
        protected Item getDefaultItem() {
            return ModRegistry.EMP_GRENADE.get();
        }

        @Override
        protected void onHit(HitResult hit) {
            super.onHit(hit);
            if (level() instanceof ServerLevel server) {
                burst(server, hit.getLocation(), RADIUS);
                discard();
            }
        }
    }

    /** The grenade item: throw it like a snowball. */
    public static class GrenadeItem extends Item {
        public GrenadeItem(Properties properties) {
            super(properties);
        }

        @Override
        public net.minecraft.world.InteractionResultHolder<net.minecraft.world.item.ItemStack> use(Level level, net.minecraft.world.entity.player.Player player,
                                                                                                    net.minecraft.world.InteractionHand hand) {
            var stack = player.getItemInHand(hand);
            if (!level.isClientSide) {
                Grenade g = new Grenade(level, player);
                g.setItem(stack);
                g.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, 1.2f, 1);
                level.addFreshEntity(g);
            }
            level.playSound(null, player.blockPosition(), com.coolfreeze23.headinajar.fx.HouseSounds.EMP_THROW.get(), SoundSource.PLAYERS, 0.6f, 0.6f);
            player.getCooldowns().addCooldown(this, 40);
            stack.consume(1, player);
            return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
    }
}
