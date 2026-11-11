package com.coolfreeze23.headinajar.module;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.entity.HeadJarEntity;
import com.coolfreeze23.headinajar.entity.HeadlessBody;
import com.coolfreeze23.headinajar.entity.HeldBlockEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.HeadRegistry;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.fx.Fx;
import com.coolfreeze23.headinajar.fx.FxSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.UUID;

/**
 * The Telekinesis module: grab what you look at, carry it in front of the jar, set it down or throw it. Server side.
 * <p>
 * A held creature or item never has its saved NoGravity flag touched: the hold cancels gravity each tick it lasts, so
 * whatever ends the hold (a logout, a server stop, a portal, the holder's death) leaves something that simply falls.
 * <p>
 * Another player, their placed head or their idle body can be lifted only by a jar whose owner is on that player's
 * trust list ({@code /headoffice trust}). Every way a hold begins or ends sends one effect memo ({@link Fx}); what the
 * hold looks like while it lasts is drawn by each client from the synced held id.
 */
public final class Telekinesis {
    /** How a hold ended. */
    public enum Release { SET_DOWN, THROWN, NO_CHARGE, OUT_OF_RANGE, GONE }

    public static void toggle(ServerPlayer player, JarState state) {
        if (state.heldEntity() >= 0) {
            release(player, state, Release.SET_DOWN);
        } else if (state.form() == com.coolfreeze23.headinajar.form.Form.JAR && state.has(ModuleType.TELEKINESIS)) {
            grab(player, state);
        }
    }

    /** Grabs the nearest thing on the player's line of sight; returns the held entity, or null after saying why not. */
    public static Entity grab(ServerPlayer player, JarState state) {
        ServerLevel level = player.serverLevel();
        double range = Config.TELEKINESIS_RANGE.get();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(range));

        BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        double blockDist = blockHit.getType() == HitResult.Type.MISS ? range * range : blockHit.getLocation().distanceToSqr(eye);
        AABB box = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
        // the first thing on the line, liftable or not, so a refused creature or player is answered rather than skipped
        // a jar that has just come out of its body stands inside it: the body is skipped unless nothing else is in
        // line and the jar looks down at it
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eye, eye.add(look.scale(Math.sqrt(blockDist))), box,
                e -> candidate(player, e) && !standingIn(player, e), blockDist);
        if (entityHit == null && player.getXRot() > 45) {
            for (HeadlessBody own : level.getEntitiesOfClass(HeadlessBody.class, player.getBoundingBox().inflate(0.5), b -> standingIn(player, b))) {
                if (own.getBoundingBox().clip(eye, eye.add(look.scale(2))).isPresent() || own.getBoundingBox().contains(eye)) {
                    entityHit = new EntityHitResult(own);
                    break;
                }
            }
        }

        Entity held = null;
        if (entityHit != null) {
            Entity e = entityHit.getEntity();
            String why = whyNot(player, e);
            if (why != null) {
                Object arg = e instanceof Player || e instanceof HeadJarEntity || e instanceof HeadlessBody ? ownerName(player, e) : e.getDisplayName();
                return fizzle(player, centre(e), e.getId(), why.equals("tk.not_trusted") ? Fx.WHY_TRUST : Fx.WHY_BOSS, why, arg);
            }
            held = e;
        } else if (blockHit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = blockHit.getBlockPos();
            BlockState block = level.getBlockState(pos);
            String why = whyNot(player, level, pos, block);
            if (why != null) {
                int look2 = switch (why) {
                    case "tk.too_hard" -> Fx.WHY_HARD;
                    case "tk.machine" -> Fx.WHY_MACHINE;
                    case "tk.nothing" -> Fx.WHY_NOTHING;
                    default -> Fx.WHY_OTHER;
                };
                return fizzle(player, blockHit.getLocation(), -1, look2, why);
            }
            level.removeBlock(pos, false);
            held = HeldBlockEntity.lift(level, pos, block);
            level.addFreshEntity(held);
        }
        if (held == null) return fizzle(player, eye.add(look.scale(4)), -1, Fx.WHY_NOTHING, "tk.nothing");
        state.setHeldEntity(held.getId());
        state.spend(500);
        Fx.send(level, Fx.of(Fx.TK_GRAB, centre(held), null, player.getId(), held.getId(), 0));
        play(level, held.position(), FxSounds.TK_GRAB.get(), player);
        return held;
    }

    /** The grab fails out loud: the refusal line, the fizzle sound, and the line seen reaching out and curling back. */
    private static Entity fizzle(ServerPlayer player, Vec3 at, int target, int look, String reason, Object... args) {
        Refusals.deny(player, reason, args);
        Fx.send(player.serverLevel(), Fx.of(Fx.TK_FIZZLE, at, null, player.getId(), target, look));
        play(player.serverLevel(), player.position(), FxSounds.TK_FIZZLE.get(), player);
        return null;
    }

    private static Vec3 centre(Entity e) {
        return e.position().add(0, e instanceof ItemEntity ? 0.25 : e.getBbHeight() / 2, 0);
    }

    /** The jar's own idle body, with the jar standing inside its box. */
    private static boolean standingIn(ServerPlayer player, Entity e) {
        return e instanceof HeadlessBody body && player.getUUID().equals(body.ownerId()) && body.getBoundingBox().inflate(0.1).contains(player.getEyePosition());
    }

    private static boolean candidate(ServerPlayer player, Entity e) {
        if (e == player || e.isSpectator() || !e.isAlive() || e.isPassenger() && e.getVehicle() == player) return false;
        return e instanceof LivingEntity || e instanceof ItemEntity || e instanceof HeldBlockEntity || e instanceof FallingBlockEntity
                || e instanceof HeadJarEntity;
    }

    /** Why {@code e} can't be lifted by {@code player} (a refusal reason), or null when it can. */
    public static String whyNot(ServerPlayer player, Entity e) {
        if (e.getType().is(Tags.EntityTypes.BOSSES)) return "tk.boss";
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return "tk.untouchable";
        UUID owner = owner(e);
        if (owner != null && !owner.equals(player.getUUID()) && !HeadRegistry.get(player.server).trusts(owner, player.getUUID())) return "tk.not_trusted";
        return null;
    }

    /** The player a thing belongs to, for the trust rule: a player, their placed head, their idle body; else null. */
    private static UUID owner(Entity e) {
        if (e instanceof Player p) return p.getUUID();
        if (e instanceof HeadJarEntity head) return head.ownerId();
        if (e instanceof HeadlessBody body) return body.ownerId();
        return null;
    }

    private static Component ownerName(ServerPlayer player, Entity e) {
        if (e instanceof Player p) return p.getDisplayName();
        UUID owner = owner(e);
        Player online = owner == null ? null : player.server.getPlayerList().getPlayer(owner);
        if (online != null) return online.getDisplayName();
        var cache = player.server.getProfileCache();
        String name = owner == null || cache == null ? "?" : cache.get(owner).map(com.mojang.authlib.GameProfile::getName).orElse("?");
        return Component.literal(name);
    }

    /** Why this block can't be lifted (a refusal reason), or null when it can. */
    private static String whyNot(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState block) {
        if (block.isAir() || !block.getFluidState().isEmpty() && block.getFluidState().isSource()) return "tk.nothing";
        if (block.hasBlockEntity()) return "tk.machine";
        float hardness = block.getDestroySpeed(level, pos);
        if (hardness < 0 || hardness > Config.TELEKINESIS_MAX_HARDNESS.get()) return "tk.too_hard";
        if (block.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) || block.hasProperty(BlockStateProperties.BED_PART)) return "tk.too_big";
        if (!player.mayBuild() || !level.mayInteract(player, pos)) return "tk.protected";
        return NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, block, player)).isCanceled() ? "tk.protected" : null;
    }

    public static void tick(ServerPlayer player, JarState state) {
        if (state.heldEntity() < 0) return;
        Entity held = player.level().getEntity(state.heldEntity());
        double range = Config.TELEKINESIS_RANGE.get() + 8;
        if (held == null || !held.isAlive()) {
            release(player, state, Release.GONE);
            return;
        }
        // Telekinesis is the bare jar's: on a host, in a cockpit or in a socket the hold ends
        if (state.form() != com.coolfreeze23.headinajar.form.Form.JAR) {
            release(player, state, Release.GONE);
            return;
        }
        if (!state.has(ModuleType.TELEKINESIS)) {
            // fitted but out of charge (or taken out): the hold fails
            release(player, state, state.fitted(ModuleType.TELEKINESIS) ? Release.NO_CHARGE : Release.GONE);
            return;
        }
        if (held.distanceToSqr(player) > range * range) {
            release(player, state, Release.OUT_OF_RANGE);
            return;
        }
        Vec3 target = holdPoint(player, held);
        // heavier things follow more slowly, so they lag and swing behind
        Vec3 pull = target.subtract(held.position()).scale(0.35 / (1 + 0.6 * heaviness(held)));
        if (pull.length() > 1.2) pull = pull.normalize().scale(1.2);
        // items and falling blocks take their gravity before they move: give it back so they hang where the pull puts
        // them (a creature takes it after moving, and the next tick's pull replaces what is left); a placed head adds
        // its own 0.04
        if (fallsBeforeMoving(held)) pull = pull.add(0, held instanceof HeadJarEntity ? 0.04 : held.getGravity(), 0);
        held.setDeltaMovement(pull);
        held.fallDistance = 0;
        // a creature's client follows its position updates, and a lifted block's go out every tick anyway; a player
        // moves itself and an item flies itself, so those need the velocity every tick
        if (held instanceof Player || held instanceof ItemEntity || held instanceof FallingBlockEntity || held instanceof HeadJarEntity) held.hurtMarked = true;
        if (held instanceof HeldBlockEntity block) block.keepHeld();
        if (held instanceof Mob mob) mob.getNavigation().stop();
        state.spend(50);
    }

    /** 0 for a pebble, 1 for a cow or bigger: the held thing's size as a share of a cow's box. */
    static double heaviness(Entity held) {
        return Math.min(1, held.getBbWidth() * held.getBbWidth() * held.getBbHeight() / 1.1);
    }

    /** How far above the crosshair the top of a held thing may reach when the jar looks level: 20 degrees. */
    private static final double TOP_SLOPE = Math.tan(Math.toRadians(20));
    /** The least a held thing's feet clear the ground under it. */
    private static final double MIN_CLEARANCE = 0.6;

    /**
     * Where the held thing's feet are carried. Looking level, its feet are a little above the jar's eyes (a cow's about
     * 0.7 of a block off the ground, a jar's eyes being half a block up) and it is far enough out that its top edge is no
     * more than 20 degrees above the crosshair, so the jar sees all of what it holds; never nearer than keeps it out of
     * the jar's face. Looking up lifts it, looking down lowers it, but never below {@link #MIN_CLEARANCE} over the
     * ground beneath it. Bigger things sit farther out by the same rule; heavier ones hang a little lower.
     */
    public static Vec3 holdPoint(Player player, Entity held) {
        double lift = 0.3 - 0.1 * heaviness(held);
        double distance = Math.max(2.6 + held.getBbWidth() * 0.8, (lift + held.getBbHeight()) / TOP_SLOPE);
        Vec3 feet = player.getEyePosition().add(player.getLookAngle().scale(distance)).add(0, lift, 0);
        var ground = player.level().clip(new ClipContext(feet.add(0, 1, 0), feet.subtract(0, 4, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, held));
        if (ground.getType() == HitResult.Type.BLOCK && feet.y < ground.getLocation().y + MIN_CLEARANCE) {
            feet = new Vec3(feet.x, ground.getLocation().y + MIN_CLEARANCE, feet.z);
        }
        return feet;
    }

    private static boolean fallsBeforeMoving(Entity held) {
        return !(held instanceof LivingEntity) && !(held instanceof HeldBlockEntity);
    }

    /** Lets go of whatever is held because the hold ended (the swap, the module taken out). */
    public static void release(ServerPlayer player, JarState state) {
        release(player, state, Release.GONE);
    }

    /** Lets go of whatever is held; {@code why} picks the look and the memo. */
    public static void release(ServerPlayer player, JarState state, Release why) {
        Entity held = state.heldEntity() >= 0 ? player.level().getEntity(state.heldEntity()) : null;
        int heldId = state.heldEntity();
        state.setHeldEntity(-1);
        if (heldId < 0) return;
        Vec3 at = held == null ? player.getEyePosition() : centre(held);
        int kind = why == Release.SET_DOWN ? Fx.TK_SET_DOWN : Fx.TK_DROP;
        int code = switch (why) {
            case NO_CHARGE -> Fx.DROP_CHARGE;
            case OUT_OF_RANGE -> Fx.DROP_RANGE;
            default -> Fx.DROP_GONE;
        };
        Fx.send(player.serverLevel(), Fx.of(kind, at, null, player.getId(), heldId, kind == Fx.TK_DROP ? code : 0));
        play(player.serverLevel(), at, FxSounds.TK_SET_DOWN.get(), player, why == Release.SET_DOWN ? 1f : 0.8f);
        if (held == null) return;
        if (held instanceof HeldBlockEntity block) {
            block.drop(block.getDeltaMovement().scale(0.3), false);
        } else {
            letFall(held);
        }
    }

    /**
     * What is let go falls. Earlier versions switched gravity off with the saved flag and could leave a creature hanging
     * for good; lifting it once more and letting go gives its gravity back.
     */
    private static void letFall(Entity held) {
        if (held.isNoGravity() && !(held instanceof HeadJarEntity)) held.setNoGravity(false);
    }

    /** Throws what is held along the player's look. */
    public static void throwHeld(ServerPlayer player, JarState state) {
        Entity held = state.heldEntity() >= 0 ? player.level().getEntity(state.heldEntity()) : null;
        int heldId = state.heldEntity();
        state.setHeldEntity(-1);
        if (held == null) {
            if (heldId >= 0) Fx.send(player.serverLevel(), Fx.of(Fx.TK_DROP, player.getEyePosition(), null, player.getId(), heldId, Fx.DROP_GONE));
            return;
        }
        Vec3 velocity = player.getLookAngle().scale(held instanceof ItemEntity ? 1.2 : 1.6);
        Vec3 from = centre(held);
        Entity flying = held;
        if (held instanceof HeldBlockEntity block) {
            Entity falling = block.drop(velocity, true);
            if (falling != null) flying = falling;
        } else {
            letFall(held);
            held.setDeltaMovement(velocity);
            held.hurtMarked = true;
        }
        state.spend(2000);
        // the client follows what flies: the falling block a thrown block becomes reaches it a tick later
        Fx.send(player.serverLevel(), Fx.of(Fx.TK_THROW, from, velocity, player.getId(), flying.getId(), 0));
        play(player.serverLevel(), player.position(), FxSounds.TK_THROW.get(), player);
    }

    private static void play(ServerLevel level, Vec3 at, SoundEvent sound, ServerPlayer player) {
        play(level, at, sound, player, 1);
    }

    /** At volume 1.0 (the file carries its loudness), pitch per play from the takes' range. */
    private static void play(ServerLevel level, Vec3 at, SoundEvent sound, ServerPlayer player, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, 1.0f, pitch * FxSounds.pitch(player.getRandom()));
    }

    /** A player left floating by an earlier version's hold gets their gravity back when they log in. */
    public static void landStranded(Player player) {
        if (player.isNoGravity()) player.setNoGravity(false);
    }

    private Telekinesis() {
    }
}
