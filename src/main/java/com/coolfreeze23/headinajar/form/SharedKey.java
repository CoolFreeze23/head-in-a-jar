package com.coolfreeze23.headinajar.form;

import com.coolfreeze23.headinajar.module.ModuleType;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * The ability key and sprint on one key (bindings are left as the player set them). The client says when a press came
 * from a shared key; the first time the ability actually answers such a press, the player is told once, ever, that the
 * key does both and can be rebound.
 */
public final class SharedKey {
    /** Remembered in the part of a player's data that survives death. */
    public static final String TOLD = "headinajar_shared_key_told";

    private SharedKey() {
    }

    /** Whether the ability key does something in this player's form right now (sprint aside). */
    public static boolean abilityAnswers(ServerPlayer player) {
        JarState state = FormActions.state(player);
        return switch (state.form()) {
            case JAR -> state.heldEntity() >= 0 || state.fitted(ModuleType.TELEKINESIS);
            case BODY, LINKED -> !com.coolfreeze23.headinajar.genetics.GeneEffects.actives(player).isEmpty();
            case PILOT -> player.getVehicle() instanceof com.coolfreeze23.headinajar.entity.MechaEntity mecha
                    && (!com.coolfreeze23.headinajar.mecha.MechaSystems.utilities(mecha).isEmpty()
                    || !com.coolfreeze23.headinajar.genetics.GeneEffects.actives(mecha).isEmpty() || state.fitted(ModuleType.TELEKINESIS));
            case HOSTED -> player.getVehicle() instanceof Mob host && com.coolfreeze23.headinajar.host.Hosts.definition(host) != null
                    && com.coolfreeze23.headinajar.host.Hosts.definition(host).primary().isPresent();
            default -> false;
        };
    }

    /** A press of the shared key: the one-time notice, if the ability answers it. Returns whether the notice was sent. */
    public static boolean pressed(ServerPlayer player) {
        CompoundTag kept = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (kept.getBoolean(TOLD) || !abilityAnswers(player)) return false;
        kept.putBoolean(TOLD, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, kept);
        player.sendSystemMessage(Component.translatable("message.headinajar.shared_key", Component.keybind("key.headinajar.ability"),
                Component.translatable("key.sprint"), Component.translatable("controls.title")).withStyle(ChatFormatting.GRAY));
        return true;
    }
}
