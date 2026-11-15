package com.coolfreeze23.headinajar.genetics;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Whether a gene may change a block: Phase's arrival, Bulldoze's trampling, Snow Trail's snow and Ruminant Gut's grazing
 * all ask here, the way Telekinesis asks before it lifts a block (the player may build there, the level lets them
 * interact, and no protection mod cancels a break posted for them). A body with nobody behind it (a host walking on its
 * own) follows the mob griefing rule.
 */
public final class GeneClaims {
    private GeneClaims() {
    }

    /** The player answerable for what this body does: itself, or the jar riding the host or piloting the mecha. */
    public static ServerPlayer answerable(Entity body) {
        if (body instanceof ServerPlayer player) return player;
        return com.coolfreeze23.headinajar.host.Hosts.pilot(body) instanceof ServerPlayer pilot ? pilot : null;
    }

    /** Whether {@code body}'s gene may change the block at {@code pos} (break it, or put something there). */
    public static boolean mayChange(Entity body, BlockPos pos) {
        if (!(body.level() instanceof ServerLevel level) || !level.isLoaded(pos)) return false;
        if (!level.getWorldBorder().isWithinBounds(pos)) return false;
        ServerPlayer player = answerable(body);
        if (player == null) return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        if (!player.mayBuild() || !level.mayInteract(player, pos)) return false;
        BlockState state = level.getBlockState(pos);
        return !NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player)).isCanceled();
    }
}
