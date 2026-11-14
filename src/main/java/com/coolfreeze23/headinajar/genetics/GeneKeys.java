package com.coolfreeze23.headinajar.genetics;

import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.feedback.Refuser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;

/**
 * The ability and secondary keys on a body's active genes (your own body, a host, a mecha): the gene fires, or the press
 * is answered with why not (no active gene and where one comes from; only one gene, on the other key; still recovering;
 * nothing to aim at).
 */
public final class GeneKeys {
    private GeneKeys() {
    }

    public static Component name(Gene gene) {
        return Component.translatable("gene.headinajar." + gene.name().toLowerCase(Locale.ROOT));
    }

    /** Presses key {@code which} (0 ability, 1 secondary) on {@code body}'s genes for {@code player}. */
    public static boolean press(ServerPlayer player, LivingEntity body, int which) {
        String why = GeneEffects.fire(body, player, which);
        if (why == null) return true;
        Refuser source = body == player ? null : Refuser.at(body);
        Object[] args = switch (why) {
            case "gene.none" -> new Object[]{Component.translatable("block.headinajar.injection_rig")};
            case "gene.one_only" -> new Object[]{name(GeneEffects.keyed(body, 0)), Component.keybind("key.headinajar.ability")};
            default -> new Object[]{name(GeneEffects.keyed(body, which))};
        };
        return source == null ? Refusals.deny(player, why, args) : Refusals.deny(player, source, why, args);
    }
}
