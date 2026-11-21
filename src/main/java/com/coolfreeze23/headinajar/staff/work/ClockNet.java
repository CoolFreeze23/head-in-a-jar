package com.coolfreeze23.headinajar.staff.work;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.TimeClockBlockEntity;
import com.coolfreeze23.headinajar.menu.TimeClockMenu;
import com.coolfreeze23.headinajar.staff.Band;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffFile;
import com.coolfreeze23.headinajar.staff.StaffTable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Time Clock's payloads, optional like the rest of the workforce's: the Roster's rows, the day's wages owed and the
 * rack's takers, sent to a player with the clock's screen open when it opens and each 20 ticks while anything in it
 * changes. A game without the channel sees the screen's slots and tabs, with no rows.
 */
public final class ClockNet {
    private ClockNet() {
    }

    /**
     * One worker on the roster. {@code entity} -1: not within reach (its chunk is not loaded; the number and species are
     * Head Office's). {@code shift} the {@link WorkerData.Shift} ordinal (-1 unknown); {@code face} the morale
     * {@link Band} ordinal; {@code status} a language key; {@code noDay}: it burns by day with no Hard Hat (Day refused).
     */
    public record Row(UUID id, int entity, int number, Component name, String species, String job, int shift, int face, String status, boolean noDay) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Row> STREAM = StreamCodec.of((buf, r) -> {
            buf.writeUUID(r.id);
            buf.writeVarInt(r.entity);
            buf.writeVarInt(r.number);
            ComponentSerialization.STREAM_CODEC.encode(buf, r.name);
            buf.writeUtf(r.species, 128);
            buf.writeUtf(r.job, 32);
            buf.writeByte(r.shift);
            buf.writeByte(r.face);
            buf.writeUtf(r.status, 128);
            buf.writeBoolean(r.noDay);
        }, buf -> new Row(buf.readUUID(), buf.readVarInt(), buf.readVarInt(), ComponentSerialization.STREAM_CODEC.decode(buf), buf.readUtf(128), buf.readUtf(32),
                buf.readByte(), buf.readByte(), buf.readUtf(128), buf.readBoolean()));

        /** The species as a type (for its name and icon), or null. */
        public EntityType<?> type() {
            ResourceLocation id = ResourceLocation.tryParse(species);
            return id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        }
    }

    /** A wage the day's payroll needs: an item and how many in all. */
    public record Owed(ResourceLocation item, int count) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Owed> STREAM = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Owed::item, ByteBufCodecs.VAR_INT, Owed::count, Owed::new);

        public Item asItem() {
            return BuiltInRegistries.ITEM.get(item);
        }
    }

    /** The screen's rows for one open clock menu ({@code menu}: its container id). {@code takers}: per rack slot. */
    public record ClockRoster(int menu, List<Row> rows, List<Owed> owed, List<Integer> takers) implements CustomPacketPayload {
        public static final Type<ClockRoster> TYPE = new Type<>(HeadInAJar.id("clock_roster"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ClockRoster> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ClockRoster::menu,
                Row.STREAM.apply(ByteBufCodecs.list(64)), ClockRoster::rows,
                Owed.STREAM.apply(ByteBufCodecs.list(64)), ClockRoster::owed,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(TimeClockBlockEntity.RACK)), ClockRoster::takers,
                ClockRoster::new);

        @Override
        public Type<ClockRoster> type() {
            return TYPE;
        }
    }

    /** An order from the Roster or the Shifts tab for one worker, by its UUID: {@link #RECALL}, or {@link #SHIFT} (arg: the shift's ordinal). */
    public record ClockOrder(int kind, UUID worker, int arg) implements CustomPacketPayload {
        public static final Type<ClockOrder> TYPE = new Type<>(HeadInAJar.id("clock_order"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ClockOrder> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ClockOrder::kind, net.minecraft.core.UUIDUtil.STREAM_CODEC, ClockOrder::worker, ByteBufCodecs.VAR_INT, ClockOrder::arg, ClockOrder::new);

        @Override
        public Type<ClockOrder> type() {
            return TYPE;
        }
    }

    public static final int RECALL = 0, SHIFT = 1;

    static void register(PayloadRegistrar registrar) {
        registrar.playToClient(ClockRoster.TYPE, ClockRoster.CODEC, (msg, ctx) -> received(ctx.player(), msg));
        registrar.playToServer(ClockOrder.TYPE, ClockOrder.CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp && sp.containerMenu instanceof TimeClockMenu menu) menu.order(sp, msg.kind(), msg.worker(), msg.arg());
        });
    }

    /** Client: rows for the open clock screen. */
    static void received(Player player, ClockRoster msg) {
        if (player != null && player.containerMenu instanceof TimeClockMenu menu && menu.containerId == msg.menu()) menu.roster = msg;
    }

    /** Server: sends the rows if this player's game has the channel. */
    public static void send(ServerPlayer player, ClockRoster rows) {
        if (player.connection != null && player.connection.hasChannel(ClockRoster.TYPE)) PacketDistributor.sendToPlayer(player, rows);
    }

    /** Server: the rows as they stand, for a clock's open menu. */
    public static ClockRoster rows(int menu, TimeClockBlockEntity clock) {
        List<Row> rows = new ArrayList<>();
        Map<ResourceLocation, Integer> owed = new LinkedHashMap<>();
        if (clock.getLevel() instanceof ServerLevel level) {
            Map<UUID, WorkerIndex.Entry> filed = clock.owner() == null ? Map.of() : byId(WorkerIndex.get(level.getServer()), clock.owner());
            for (UUID id : clock.roster()) {
                Entity e = level.getEntity(id);
                WorkerData d = e instanceof Mob ? Workforce.data(e) : null;
                EntityType<?> type;
                if (d != null) {
                    Mob mob = (Mob) e;
                    StaffFile f = Staff.of(mob);
                    type = mob.getType();
                    String job = d.state == WorkerData.State.ON_JOB ? orEmpty(Order.jobOf(level, d)) : "";
                    rows.add(new Row(id, mob.getId(), f == null ? 0 : f.number, Workforce.name(mob), BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(),
                            job, d.shift.ordinal(), Band.of(f).ordinal(), status(d), Shifts.dayRefused(mob)));
                } else {
                    WorkerIndex.Entry entry = filed.get(id);
                    String species = entry == null ? "" : entry.species;
                    ResourceLocation rl = ResourceLocation.tryParse(species);
                    type = rl == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null);
                    // not in the world: on someone's shoulder (a parrot), or where Head Office last saw it
                    String status = Detail.onShoulder(level.getServer(), id) ? Shifts.status("shoulder") : Shifts.status("off_site");
                    rows.add(new Row(id, -1, entry == null ? 0 : entry.number, type == null ? Component.literal("?") : type.getDescription(), species,
                            "", -1, Band.NONE.ordinal(), status, false));
                }
                if (type != null) {
                    StaffTable.Wage wage = StaffTable.wage(type);
                    Item item = wage.item() != null ? wage.item() : wage.tag() == null ? null
                            : BuiltInRegistries.ITEM.getTag(wage.tag()).flatMap(s -> s.stream().findFirst()).map(h -> h.value()).orElse(null);
                    if (item != null && wage.count() > 0) owed.merge(BuiltInRegistries.ITEM.getKey(item), wage.count(), Integer::sum);
                }
            }
        }
        List<Owed> owedList = new ArrayList<>();
        owed.forEach((k, v) -> owedList.add(new Owed(k, v)));
        List<Integer> takers = new ArrayList<>();
        for (int i = 0; i < TimeClockBlockEntity.RACK; i++) takers.add(clock.takers(i));
        return new ClockRoster(menu, List.copyOf(rows), List.copyOf(owedList), List.copyOf(takers));
    }

    private static Map<UUID, WorkerIndex.Entry> byId(WorkerIndex index, UUID owner) {
        Map<UUID, WorkerIndex.Entry> out = new java.util.HashMap<>();
        for (WorkerIndex.Entry e : index.of(owner)) out.put(e.id, e);
        return out;
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    /** The Roster's status for a worker: the line it last said, or its state's word. */
    static String status(WorkerData d) {
        return switch (d.state) {
            case DETAIL -> Shifts.status("detail");
            case NOTICE -> Shifts.status("notice");
            case AWAITING -> Shifts.status("awaiting");
            case OFF_SITE -> d.status != null && d.status.startsWith("staff.headinajar.say.") ? d.status : Shifts.status("off_site");
            default -> d.status != null ? d.status : Shifts.status(switch (d.state) {
                case ON_JOB -> "on_job";
                case OFF_SHIFT -> "off_shift";
                default -> "waiting";
            });
        };
    }
}
