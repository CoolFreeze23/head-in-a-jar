package com.coolfreeze23.headinajar.form;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.block.LifeSupportCradleBlockEntity;
import com.coolfreeze23.headinajar.entity.HeadJarEntity;
import com.coolfreeze23.headinajar.entity.HeadlessBody;
import com.coolfreeze23.headinajar.item.LifeSupportJarItem;
import com.coolfreeze23.headinajar.menu.JarMenu;
import com.coolfreeze23.headinajar.module.Telekinesis;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Form changes. Server side only. */
public final class FormActions {
    /** The body keeps what it wears below the neck; a helmet stays with the head. */
    public static final EquipmentSlot[] BODY_ARMOR = {EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    /** Players whose next death is a real one even while linked (their head was destroyed). */
    public static final Set<UUID> REAL_DEATH = new HashSet<>();

    public static JarState state(Player player) {
        return player.getData(ModRegistry.JAR_STATE);
    }

    private static HeadRegistry registry(ServerPlayer player) {
        return HeadRegistry.get(player);
    }

    // --- the Separation Chair -------------------------------------------------------------------

    /**
     * The chair's result: the player stands up as their own headless body, LINKED to a head that the chair leaves
     * on its outlet shelf, or docks straight into a nearby Cradle.
     */
    public static boolean separate(ServerPlayer player, ItemStack jar, ItemStack goggles, ItemStack collar, Vec3 outlet,
                                   LifeSupportCradleBlockEntity cradle) {
        JarState state = state(player);
        if (state.form().isDecapitated()) return false;
        state.setForm(Form.LINKED);
        state.setSlots(jar.getItem() instanceof LifeSupportJarItem j ? j.slots() : 3);
        state.setCharge(JarState.maxCharge());
        state.fillJar();
        state.setGoggles(goggles.copyWithCount(1));
        state.setCollar(collar.copyWithCount(1));
        state.setBody(null);
        state.setCarryingHead(false);
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!helmet.isEmpty()) {
            give(player, helmet.copy());
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        }
        if (cradle != null && cradle.isEmpty()) {
            dock(player, cradle);
        } else {
            placeHead(player, player.serverLevel(), outlet, player.getYRot());
        }
        player.refreshDimensions();
        Network.sync(player);
        return true;
    }

    /** Puts the player's head down in the world as a Head Jar. */
    private static HeadJarEntity placeHead(ServerPlayer player, ServerLevel level, Vec3 at, float yaw) {
        HeadJarEntity head = new HeadJarEntity(ModRegistry.HEAD_JAR.get(), level);
        head.moveTo(at.x, at.y, at.z, yaw, 0);
        head.setOwner(player.getUUID(), player.getGameProfile().getName());
        head.setGoggles(state(player).goggles());
        level.addFreshEntity(head);
        registry(player).setHead(player.getUUID(), new HeadRegistry.Head(HeadRegistry.Where.ENTITY, new HeadRegistry.Spot(level.dimension(), at), head.getUUID()));
        return head;
    }

    private static void dock(ServerPlayer player, LifeSupportCradleBlockEntity cradle) {
        cradle.dock(player.getUUID(), player.getGameProfile().getName(), state(player).goggles());
        BlockPos pos = cradle.getBlockPos();
        registry(player).setHead(player.getUUID(), new HeadRegistry.Head(HeadRegistry.Where.CRADLE,
                new HeadRegistry.Spot(cradle.getLevel().dimension(), Vec3.atBottomCenterOf(pos)), null));
    }

    // --- swapping between body and head -------------------------------------------------------------

    /**
     * LINKED to JAR: the body is left standing and the player wakes up in their head, wherever it is.
     * Used by the Swap key and when the link breaks.
     */
    public static boolean linkedToJar(ServerPlayer player) {
        JarState state = state(player);
        if (state.form() != Form.LINKED) return false;
        HeadRegistry registry = registry(player);
        HeadRegistry.Head head = registry.head(player.getUUID());
        if (head == null && !state.carryingHead()) return false;

        HeadlessBody body = leaveBody(player, false);
        registry.setBody(player.getUUID(), new HeadRegistry.Body(body.getUUID(), HeadRegistry.Spot.of(body), false));
        state.setBody(body.getUUID());
        becomeHead(player, state, head);
        return true;
    }

    /** Spawns the player's body where they stand, wearing their armour (or holding everything, as a corpse). */
    private static HeadlessBody leaveBody(ServerPlayer player, boolean corpse) {
        ServerLevel level = player.serverLevel();
        HeadlessBody body = new HeadlessBody(ModRegistry.HEADLESS_BODY.get(), level);
        body.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0);
        body.setYBodyRot(player.yBodyRot);
        body.setOwner(player.getUUID(), player.getGameProfile().getName());
        body.setCollar(!state(player).collar().isEmpty());
        for (EquipmentSlot slot : BODY_ARMOR) {
            body.setItemSlot(slot, player.getItemBySlot(slot).copy());
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
        if (corpse) body.becomeCorpse(player);
        level.addFreshEntity(body);
        return body;
    }

    /** Moves the player into their head: onto its Cradle, to its Head Jar, or right here if they carried it. */
    private static void becomeHead(ServerPlayer player, JarState state, HeadRegistry.Head head) {
        HeadRegistry registry = registry(player);
        if (!state.carryingHead() && head != null && head.where() != HeadRegistry.Where.CARRIED) {
            ServerLevel level = player.server.getLevel(head.spot().dimension());
            if (level != null) {
                Vec3 to = head.where() == HeadRegistry.Where.CRADLE ? head.spot().pos().add(0, 0.5, 0) : head.spot().pos();
                player.teleportTo(level, to.x, to.y, to.z, player.getYRot(), player.getXRot());
            }
        }
        // a docked or placed head notices it is gone the next time it ticks
        registry.setHead(player.getUUID(), new HeadRegistry.Head(HeadRegistry.Where.PLAYER, HeadRegistry.Spot.of(player), null));
        state.setCarryingHead(false);
        state.setForm(Form.JAR);
        state.setDeadLinkTicks(0);
        player.setSprinting(false);
        player.stopRiding();
        player.refreshDimensions();
        Network.sync(player);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6f, 1.6f);
    }

    /** JAR to LINKED: put the head down here (or into the Cradle under it) and wake up in the body. */
    public static boolean jarToLinked(ServerPlayer player) {
        JarState state = state(player);
        HeadRegistry registry = registry(player);
        HeadRegistry.Body body = registry.body(player.getUUID());
        if (state.form() != Form.JAR || state.collar().isEmpty() || body == null || body.corpse()) return false;
        if (NeuroLink.quality(player, state, body.spot()) <= NeuroLink.QUALITY_TO_LINK) {
            player.displayClientMessage(Component.translatable("message.headinajar.signal_too_weak"), true);
            return false;
        }
        Telekinesis.release(player, state);
        LifeSupportCradleBlockEntity cradle = LifeSupportCradleBlockEntity.under(player);
        if (cradle != null && cradle.isEmpty()) dock(player, cradle);
        else placeHead(player, player.serverLevel(), player.position(), player.getYRot());

        ServerLevel level = player.server.getLevel(body.spot().dimension());
        Vec3 to = body.spot().pos();
        state.setForm(Form.LINKED);
        player.refreshDimensions();
        player.teleportTo(level, to.x, to.y, to.z, player.getYRot(), player.getXRot());
        Network.sync(player);
        // the body entity steps into the player when it next ticks (its chunk may still be loading)
        if (level.getEntity(body.entity()) instanceof HeadlessBody entity) absorbBody(player, entity);
        level.playSound(null, to.x, to.y, to.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6f, 1.4f);
        return true;
    }

    /** A LINKED player takes over their standing body: it disappears into them and its armour goes back on. */
    public static void absorbBody(ServerPlayer player, HeadlessBody body) {
        JarState state = state(player);
        if (body.isRemoved() || !body.getUUID().equals(state.body())) return;
        for (EquipmentSlot slot : BODY_ARMOR) {
            ItemStack worn = body.getItemBySlot(slot);
            if (worn.isEmpty()) continue;
            ItemStack current = player.getItemBySlot(slot);
            if (!current.isEmpty()) give(player, current);
            player.setItemSlot(slot, worn.copy());
            body.setItemSlot(slot, ItemStack.EMPTY);
        }
        state.setBody(null);
        registry(player).setBody(player.getUUID(), null);
        body.discard();
    }

    // --- carrying and docking the head ----------------------------------------------------------------

    /** A LINKED player with a Head Satchel picks up their own placed head. */
    public static boolean pickUpHead(ServerPlayer player, HeadJarEntity head) {
        JarState state = state(player);
        if (state.form() != Form.LINKED || !player.getUUID().equals(head.ownerId()) || !hasSatchel(player)) return false;
        head.discard();
        state.setCarryingHead(true);
        registry(player).setHead(player.getUUID(), new HeadRegistry.Head(HeadRegistry.Where.CARRIED, HeadRegistry.Spot.of(player), null));
        Network.sync(player);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1, 1);
        return true;
    }

    /** A LINKED player takes their head out of a Cradle into the satchel. */
    public static boolean undock(ServerPlayer player, LifeSupportCradleBlockEntity cradle) {
        JarState state = state(player);
        if (state.form() != Form.LINKED || !player.getUUID().equals(cradle.owner()) || !hasSatchel(player)) return false;
        cradle.clear();
        state.setCarryingHead(true);
        registry(player).setHead(player.getUUID(), new HeadRegistry.Head(HeadRegistry.Where.CARRIED, HeadRegistry.Spot.of(player), null));
        Network.sync(player);
        return true;
    }

    /** A LINKED player puts their carried head into an empty Cradle. */
    public static boolean dockCarried(ServerPlayer player, LifeSupportCradleBlockEntity cradle) {
        JarState state = state(player);
        if (state.form() != Form.LINKED || !state.carryingHead() || !cradle.isEmpty()) return false;
        state.setCarryingHead(false);
        dock(player, cradle);
        Network.sync(player);
        player.serverLevel().playSound(null, cradle.getBlockPos(), SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1, 0.8f);
        return true;
    }

    /** A LINKED player sets their carried head down on the ground in front of them. */
    public static boolean setDownHead(ServerPlayer player) {
        JarState state = state(player);
        if (state.form() != Form.LINKED || !state.carryingHead()) return false;
        state.setCarryingHead(false);
        Vec3 at = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(0.8));
        placeHead(player, player.serverLevel(), at, player.getYRot() + 180);
        Network.sync(player);
        return true;
    }

    /** Fits new Relay Goggles on your own head (placed or docked); the old pair comes back. */
    public static boolean fitGoggles(ServerPlayer player, ItemStack goggles, HeadJarEntity head, LifeSupportCradleBlockEntity cradle) {
        JarState state = state(player);
        if (!state.form().isDecapitated() || !(goggles.getItem() instanceof com.coolfreeze23.headinajar.item.RelayGogglesItem)) return false;
        ItemStack old = state.goggles();
        state.setGoggles(goggles.copyWithCount(1));
        give(player, old.copy());
        if (head != null) head.setGoggles(state.goggles());
        if (cradle != null) cradle.dock(player.getUUID(), player.getGameProfile().getName(), state.goggles());
        Network.sync(player);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1, 1.2f);
        return true;
    }

    public static boolean hasSatchel(Player player) {
        return player.getInventory().contains(new ItemStack(ModRegistry.HEAD_SATCHEL.get()));
    }

    // --- dying -----------------------------------------------------------------------------------

    /**
     * A LINKED body reached 0 health: not a death. The body becomes a corpse holding the player's things
     * (per config) and the player wakes up in their head with full health.
     */
    public static boolean bodyDied(ServerPlayer player) {
        JarState state = state(player);
        if (state.form() != Form.LINKED) return false;
        HeadRegistry.Head head = registry(player).head(player.getUUID());
        String rule = Config.get(Config.LINK_LOSS_INVENTORY, "corpse");
        HeadlessBody corpse = leaveBody(player, false);
        switch (rule) {
            case "keep" -> {
            }
            case "vanilla" -> player.getInventory().dropAll();
            default -> corpse.takeInventory(player);
        }
        corpse.becomeCorpse(null);
        registry(player).setBody(player.getUUID(), new HeadRegistry.Body(corpse.getUUID(), HeadRegistry.Spot.of(corpse), true));
        state.setBody(corpse.getUUID());
        // a carried head falls out where the body fell
        if (state.carryingHead()) head = new HeadRegistry.Head(HeadRegistry.Where.CARRIED, HeadRegistry.Spot.of(player), null);
        player.setHealth(player.getMaxHealth());
        player.removeAllEffects();
        player.clearFire();
        player.setAirSupply(player.getMaxAirSupply());
        player.fallDistance = 0;
        becomeHead(player, state, head);
        player.displayClientMessage(Component.translatable("message.headinajar.body_died"), false);
        return true;
    }

    /** A real death: the jar breaks. Drops modules, goggles and collar; an idle living body gives out. */
    public static void shatter(ServerPlayer player) {
        JarState state = state(player);
        if (!state.form().isDecapitated()) return;
        Telekinesis.release(player, state);
        for (int i = 0; i < JarState.MAX_SLOTS; i++) {
            ItemStack module = state.module(i);
            if (!module.isEmpty()) player.spawnAtLocation(module.copy());
            state.setModule(i, ItemStack.EMPTY);
        }
        if (!state.goggles().isEmpty()) player.spawnAtLocation(state.goggles().copy());
        if (!state.collar().isEmpty()) player.spawnAtLocation(state.collar().copy());
        state.setGoggles(ItemStack.EMPTY);
        state.setCollar(ItemStack.EMPTY);
        if (state.body() != null && player.serverLevel().getEntity(state.body()) instanceof HeadlessBody body && !body.isCorpse()) {
            body.collapse();
        }
        registry(player).clear(player.getUUID());
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    // --- putting the head back on -------------------------------------------------------------------

    /**
     * The Suture Kit: a LINKED player holding their own head, or a jar at its own body or corpse, becomes whole.
     * Goggles and collar come off a little worn.
     */
    public static boolean suture(ServerPlayer player, HeadlessBody body) {
        JarState state = state(player);
        if (state.form() == Form.LINKED && state.carryingHead()) {
            // the body is the player already
        } else if (state.form() == Form.JAR && body != null && player.getUUID().equals(body.ownerId())) {
            Telekinesis.release(player, state);
            player.teleportTo((net.minecraft.server.level.ServerLevel) body.level(), body.getX(), body.getY(), body.getZ(), body.getYRot(), player.getXRot());
            for (EquipmentSlot slot : BODY_ARMOR) {
                ItemStack worn = body.getItemBySlot(slot);
                if (worn.isEmpty()) continue;
                ItemStack current = player.getItemBySlot(slot);
                if (!current.isEmpty()) give(player, current);
                player.setItemSlot(slot, worn.copy());
                body.setItemSlot(slot, ItemStack.EMPTY);
            }
            if (body.isCorpse()) {
                body.giveInventoryTo(player);
                player.setHealth(Math.min(player.getMaxHealth(), 6));
            }
            body.discard();
        } else {
            return false;
        }
        returnJar(player, state);
        give(player, wear(state.goggles()));
        give(player, wear(state.collar()));
        state.setGoggles(ItemStack.EMPTY);
        state.setCollar(ItemStack.EMPTY);
        state.setCarryingHead(false);
        state.setBody(null);
        state.setForm(Form.BODY);
        registry(player).clear(player.getUUID());
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 600));
        player.getAbilities().flying = player.getAbilities().flying && player.isCreative();
        player.onUpdateAbilities();
        player.refreshDimensions();
        Network.sync(player);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 1, 0.7f);
        return true;
    }

    private static ItemStack wear(ItemStack stack) {
        if (stack.isEmpty()) return stack;
        ItemStack worn = stack.copy();
        if (worn.isDamageableItem()) worn.setDamageValue(Math.min(worn.getMaxDamage() - 1, worn.getDamageValue() + worn.getMaxDamage() / 5));
        return worn;
    }

    /** v1 path: a jar puts its head straight back onto a body that has no Collar Cam (admin and test separations). */
    public static boolean reattach(ServerPlayer player, HeadlessBody body) {
        JarState state = state(player);
        if (state.form() != Form.JAR || !player.getUUID().equals(body.ownerId()) || body.isCorpse()) return false;
        Telekinesis.release(player, state);
        for (EquipmentSlot slot : BODY_ARMOR) {
            ItemStack worn = body.getItemBySlot(slot);
            if (worn.isEmpty()) continue;
            ItemStack current = player.getItemBySlot(slot);
            if (!current.isEmpty()) give(player, current);
            player.setItemSlot(slot, worn.copy());
            body.setItemSlot(slot, ItemStack.EMPTY);
        }
        returnJar(player, state);
        state.setForm(Form.BODY);
        state.setBody(null);
        registry(player).clear(player.getUUID());
        player.stopRiding();
        player.getAbilities().flying = player.getAbilities().flying && player.isCreative();
        player.onUpdateAbilities();
        player.refreshDimensions();
        player.teleportTo(player.serverLevel(), body.getX(), body.getY(), body.getZ(), body.getYRot(), player.getXRot());
        body.discard();
        Network.sync(player);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 1.0f, 0.7f);
        return true;
    }

    /**
     * Admin and test shortcut (v1): takes the head straight off into a jar, leaving a plain body with no Collar Cam.
     * Survival players use the Separation Chair.
     */
    public static boolean detach(ServerPlayer player, int slots) {
        JarState state = state(player);
        if (state.form().isDecapitated()) return false;
        ServerLevel level = player.serverLevel();
        HeadlessBody body = leaveBody(player, false);
        state.setForm(Form.JAR);
        state.setSlots(slots);
        state.setCharge(JarState.maxCharge());
        state.fillJar();
        state.setBody(body.getUUID());
        registry(player).setBody(player.getUUID(), new HeadRegistry.Body(body.getUUID(), HeadRegistry.Spot.of(body), false));
        registry(player).setHead(player.getUUID(), new HeadRegistry.Head(HeadRegistry.Where.PLAYER, HeadRegistry.Spot.of(player), null));
        player.setSprinting(false);
        player.stopRiding();
        player.refreshDimensions();
        Network.sync(player);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS, 1.0f, 0.6f);
        level.sendParticles(ParticleTypes.BUBBLE_POP, player.getX(), player.getY() + 0.5, player.getZ(), 20, 0.3, 0.3, 0.3, 0.02);
        return true;
    }

    /** Gives back the fitted modules and an empty jar of the same tier. */
    private static void returnJar(ServerPlayer player, JarState state) {
        for (int i = 0; i < JarState.MAX_SLOTS; i++) {
            ItemStack module = state.module(i);
            if (!module.isEmpty()) give(player, module.copy());
            state.setModule(i, ItemStack.EMPTY);
        }
        give(player, new ItemStack(state.slots() > 3 ? ModRegistry.REINFORCED_JAR.get() : ModRegistry.LIFE_SUPPORT_JAR.get()));
    }

    public static void give(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    /** The Jar menu's "Let go" button: the head gives up, which resets everything. */
    public static void letGo(ServerPlayer player) {
        if (!state(player).form().isJarred()) return;
        player.kill();
    }

    public static void openMenu(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new JarMenu(id, inventory), Component.translatable("container.headinajar.jar")));
    }

    private FormActions() {
    }
}
