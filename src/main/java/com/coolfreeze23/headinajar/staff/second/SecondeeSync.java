package com.coolfreeze23.headinajar.staff.second;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.HeadInfo;
import com.coolfreeze23.headinajar.item.JarredHeadItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * What clients are told about a collared host (the optional payload {@code headinajar:secondee}): the head seated and
 * its jar, the canister's step, the glass and the team sync, so the collar, the canister and the jar can be drawn at
 * the neck. Sent on start tracking and whenever a shown value changes step (never every tick). Optional: a game on
 * either side without it still connects, and the server sends it only where the channel is open.
 */
public final class SecondeeSync {
    private SecondeeSync() {
    }

    // --- what is shown ----------------------------------------------------------------------------------------------

    /** The step's bits: a collar is on, a head is seated, the head is dry. */
    public static final int COLLAR = 1, SEATED = 1 << 1, DRY = 1 << 2;
    /** Where the canister step (2 bits), the glass stage (2 bits) and the team sync quarter (3 bits) sit in the step. */
    public static final int CANISTER_SHIFT = 3, GLASS_SHIFT = 5, QUARTER_SHIFT = 7;

    /**
     * A collared host as a client draws it. {@code step}: {@link #COLLAR}, {@link #SEATED}, {@link #DRY} and the three
     * small numbers (0 for a host with no collar: the client forgets it). {@code head}: the seated head's species id,
     * empty for none; {@code jar}: the jar it went into (an item id); {@code villager}: a villager head's data (empty for
     * any other); {@code size}: a slime's or magma cube's size, else 0.
     */
    public record View(int entity, int step, String head, String jar, CompoundTag villager, int size) implements CustomPacketPayload {
        public static final Type<View> TYPE = new Type<>(HeadInAJar.id("secondee"));
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, View::entity, ByteBufCodecs.VAR_INT, View::step, ByteBufCodecs.stringUtf8(256), View::head,
                ByteBufCodecs.stringUtf8(256), View::jar, ByteBufCodecs.COMPOUND_TAG, View::villager, ByteBufCodecs.VAR_INT, View::size,
                View::new);

        @Override
        public Type<View> type() {
            return TYPE;
        }

        public boolean collar() {
            return (step & COLLAR) != 0;
        }

        public boolean seated() {
            return (step & SEATED) != 0;
        }

        public boolean dry() {
            return (step & DRY) != 0;
        }

        /** The canister's sight glass: 0 full, 1 two thirds, 2 one third, 3 empty. */
        public int canister() {
            return step >> CANISTER_SHIFT & 3;
        }

        /** The jar's glass: 0 whole to 3 nearly gone (a player jar's stages). */
        public int glass() {
            return step >> GLASS_SHIFT & 3;
        }

        /** Team sync in quarters, 0-4. */
        public int quarter() {
            return step >> QUARTER_SHIFT & 7;
        }
    }

    /** The canister's sight-glass step for {@code mB}: 0 full (over two thirds), 1 two thirds, 2 one third, 3 empty. */
    public static int canisterStep(int mB) {
        if (mB <= 0) return 3;
        return 3 - (int) Math.ceil(Math.min(mB, SecondeeData.CANISTER) * 3.0 / SecondeeData.CANISTER);
    }

    /** The jar's crack stage for its glass, as a player jar's ({@code JarLife.stage}): 0 whole to 3. */
    public static int glassStage(int glass, int max) {
        float g = glass / (float) Math.max(1, max);
        return g <= 0.25f ? 3 : g <= 0.5f ? 2 : g <= 0.75f ? 1 : 0;
    }

    /** A seated head out of broth: it sags in its jar. */
    public static boolean dry(SecondeeData d) {
        return d.seated() && d.canister <= 0;
    }

    /** The step this host shows now (0 for no collar). */
    public static int step(Mob host) {
        SecondeeData d = Secondees.data(host);
        if (d == null) return 0;
        int step = COLLAR | canisterStep(d.canister) << CANISTER_SHIFT;
        if (d.seated()) {
            step |= SEATED | glassStage(d.glass, d.glassMax) << GLASS_SHIFT | Math.min(4, (int) (d.teamSync / 25)) << QUARTER_SHIFT;
            if (dry(d)) step |= DRY;
        }
        return step;
    }

    /** The payload for this host as it is now. */
    public static View view(Mob host) {
        SecondeeData d = Secondees.data(host);
        int step = step(host);
        if (d == null || !d.seated()) return new View(host.getId(), step, "", "", new CompoundTag(), 0);
        EntityType<?> type = JarredHeadItem.type(d.head);
        CustomData data = d.head.get(DataComponents.CUSTOM_DATA);
        String jar = HeadInfo.jar(data == null ? null : data.getUnsafe());
        CompoundTag stored = Secondees.entityTag(d.head);
        CompoundTag villager = stored.contains("VillagerData") ? stored.getCompound("VillagerData").copy() : new CompoundTag();
        int size = (type == EntityType.SLIME || type == EntityType.MAGMA_CUBE) && stored.contains("Size") ? stored.getInt("Size") + 1 : 0;
        return new View(host.getId(), step, type == null ? "" : BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(), jar, villager, size);
    }

    // --- sending ----------------------------------------------------------------------------------------------------

    /** What was sent, for tests: the payload, how many games it reached and why it went ("change", "tracking"). */
    public record Sent(View view, int recipients, String why) {
    }

    private static final ArrayDeque<Sent> SENT = new ArrayDeque<>();

    /** The payloads sent about this entity, oldest first (from the last 256 of all). */
    public static synchronized List<Sent> sent(int entity) {
        List<Sent> out = new ArrayList<>();
        for (Sent s : SENT) if (s.view.entity() == entity) out.add(s);
        return out;
    }

    private static synchronized void record(View view, int recipients, String why) {
        SENT.addLast(new Sent(view, recipients, why));
        while (SENT.size() > 256) SENT.removeFirst();
    }

    /** Whether this player's game has the channel. */
    public static boolean hears(ServerPlayer player) {
        return player.connection != null && player.connection.hasChannel(View.TYPE);
    }

    /** Registers the payload on an optional registrar ({@code registrar.optional()} in {@code Network}). */
    public static void registerPayload(PayloadRegistrar registrar) {
        registrar.playToClient(View.TYPE, View.CODEC, (msg, ctx) -> com.coolfreeze23.headinajar.client.second.SecondeeClient.view(msg));
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(SecondeeSync::tracking);
    }

    /** A player starts seeing a collared host: it is told what sits on the neck. */
    private static void tracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof Mob mob) || !(event.getEntity() instanceof ServerPlayer viewer) || !Secondees.collared(mob)) return;
        View view = view(mob);
        boolean heard = hears(viewer);
        if (heard) PacketDistributor.sendToPlayer(viewer, view);
        record(view, heard ? 1 : 0, "tracking");
    }

    /**
     * Something a client draws changed on this host (a collar fitted or taken off, a head seated or unseated, a step):
     * sent to every game near enough to be drawing it, and only when the shown step is not the one last sent. Cheap:
     * call it on every change of a canister, glass, team sync or head value. A host whose collar just came off is
     * always sent (the client forgets it).
     */
    public static void changed(Mob host) {
        if (!(host.level() instanceof ServerLevel level)) return;
        SecondeeData d = Secondees.data(host);
        int step = step(host);
        if (d != null) {
            if (d.sentStep == step) return;
            d.sentStep = step;
        }
        View view = view(host);
        double range = Math.max(2, host.getType().clientTrackingRange()) * 16 + 16;
        int n = 0;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(host) < range * range && hears(p)) {
                PacketDistributor.sendToPlayer(p, view);
                n++;
            }
        }
        record(view, n, "change");
    }
}
