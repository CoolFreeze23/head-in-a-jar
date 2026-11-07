package com.coolfreeze23.headinajar.form;

import com.coolfreeze23.headinajar.entity.HeadlessBody;
import com.coolfreeze23.headinajar.menu.JarMenu;
import com.coolfreeze23.headinajar.module.Telekinesis;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Form changes. Server side only. */
public final class FormActions {
    /** The body keeps what it wears below the neck; a helmet stays with the head. */
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public static JarState state(Player player) {
        return player.getData(ModRegistry.JAR_STATE);
    }

    /** Takes the player's head off into a jar with this many module slots. Their body stays standing where they were. */
    public static boolean detach(ServerPlayer player, int slots) {
        JarState state = state(player);
        if (state.form().isJarred()) return false;
        ServerLevel level = player.serverLevel();

        HeadlessBody body = new HeadlessBody(ModRegistry.HEADLESS_BODY.get(), level);
        body.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0);
        body.setYBodyRot(player.yBodyRot);
        body.setYHeadRot(player.getYHeadRot());
        body.setOwner(player.getUUID(), player.getGameProfile().getName());
        for (EquipmentSlot slot : ARMOR) {
            body.setItemSlot(slot, player.getItemBySlot(slot).copy());
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
        level.addFreshEntity(body);

        state.setForm(Form.JAR);
        state.setSlots(slots);
        state.setCharge(JarState.maxCharge());
        state.setBody(body.getUUID());
        player.setSprinting(false);
        player.stopRiding();
        player.refreshDimensions();
        // the jar is smaller than the body: keep the player centred where their neck was
        player.teleportTo(player.getX(), player.getY(), player.getZ());
        Network.sync(player);

        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS, 1.0f, 0.6f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1.0f, 0.8f);
        level.sendParticles(ParticleTypes.BUBBLE_POP, player.getX(), player.getY() + 0.5, player.getZ(), 20, 0.3, 0.3, 0.3, 0.02);
        return true;
    }

    /** Puts the player's head back on this body. */
    public static boolean reattach(ServerPlayer player, HeadlessBody body) {
        JarState state = state(player);
        if (state.form() != Form.JAR || !player.getUUID().equals(body.ownerId())) return false;
        Telekinesis.release(player, state);

        for (EquipmentSlot slot : ARMOR) {
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

    /** Gives back the fitted modules and an empty jar of the same tier. */
    private static void returnJar(ServerPlayer player, JarState state) {
        for (int i = 0; i < JarState.MAX_SLOTS; i++) {
            ItemStack module = state.module(i);
            if (!module.isEmpty()) give(player, module.copy());
            state.setModule(i, ItemStack.EMPTY);
        }
        give(player, new ItemStack(state.slots() > 3 ? ModRegistry.REINFORCED_JAR.get() : ModRegistry.LIFE_SUPPORT_JAR.get()));
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    /** The jar breaks: drop the modules where the head was. Called on death, before the state resets. */
    public static void shatter(ServerPlayer player) {
        JarState state = state(player);
        if (!state.form().isJarred()) return;
        Telekinesis.release(player, state);
        for (int i = 0; i < JarState.MAX_SLOTS; i++) {
            ItemStack module = state.module(i);
            if (!module.isEmpty()) player.spawnAtLocation(module.copy());
            state.setModule(i, ItemStack.EMPTY);
        }
        if (state.body() != null && player.serverLevel().getEntity(state.body()) instanceof HeadlessBody body) {
            body.collapse();
        }
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f);
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
