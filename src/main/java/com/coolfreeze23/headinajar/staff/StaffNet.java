package com.coolfreeze23.headinajar.staff;

import com.coolfreeze23.headinajar.HeadInAJar;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The file card's two payloads, both optional (a game on either side without them still connects under the same
 * payload version): the card the server has filled in, and a rename sent back from it.
 */
public final class StaffNet {
    private StaffNet() {
    }

    /**
     * A placed head's file card, every line already decided by the server (in the viewer's own language once drawn: the
     * lines are translatable components). {@code card} holds:
     * <ul>
     * <li>{@code Type} the creature's id, {@code VillagerData} (optional), {@code Jar} the jar's id: what draws the head;</li>
     * <li>{@code Title} the staff number line, as a component (JSON);</li>
     * <li>{@code Rows} a list of {@code {label, value}} components (JSON), in order;</li>
     * <li>{@code History} a list of components (JSON), oldest first, at most eight;</li>
     * <li>{@code Name} the given name as it stands (plain text), only when the viewer may rename it.</li>
     * </ul>
     */
    public record Card(BlockPos pos, CompoundTag card, boolean canRename) implements CustomPacketPayload {
        public static final Type<Card> TYPE = new Type<>(HeadInAJar.id("staff_card"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Card> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Card::pos, ByteBufCodecs.COMPOUND_TAG, Card::card, ByteBufCodecs.BOOL, Card::canRename, Card::new);

        @Override
        public Type<Card> type() {
            return TYPE;
        }
    }

    /** A new name for the head at {@code pos}, typed on its file card (at most 64 bytes; the server keeps 16 characters). */
    public record Rename(BlockPos pos, String name) implements CustomPacketPayload {
        public static final Type<Rename> TYPE = new Type<>(HeadInAJar.id("staff_rename"));
        public static final StreamCodec<FriendlyByteBuf, Rename> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Rename::pos, ByteBufCodecs.stringUtf8(64), Rename::name, Rename::new);

        @Override
        public Type<Rename> type() {
            return TYPE;
        }
    }

    /** Registers both on an optional registrar ({@code registrar.optional()}). */
    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(Card.TYPE, Card.CODEC, (msg, ctx) -> com.coolfreeze23.headinajar.client.staff.PersonnelFileScreen.open(msg));
        registrar.playToServer(Rename.TYPE, Rename.CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer player) StaffCards.rename(player, msg.pos(), msg.name());
        });
    }

    /** Whether this player's game can show the card (a 0.5.4 game cannot: it gets the card's first lines in chat). */
    public static boolean canShow(ServerPlayer player) {
        return player.connection != null && player.connection.hasChannel(Card.TYPE);
    }

    public static void send(ServerPlayer player, Card card) {
        PacketDistributor.sendToPlayer(player, card);
    }
}
