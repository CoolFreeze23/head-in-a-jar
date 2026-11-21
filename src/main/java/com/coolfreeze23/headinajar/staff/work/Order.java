package com.coolfreeze23.headinajar.staff.work;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * An order on a Clipboard (data component {@code headinajar:order}): the site it is bound to, the job, its stops (a
 * container and the face it was clicked on, Pick-Up or Drop-Off, a Drop-Off's filter and "Everything else") and areas
 * (two corners), a name, and the first corner of an area being marked. A Job Card ({@code headinajar:job_card}) is an
 * order printed: the same, with who printed it and the day.
 */
public record Order(Optional<GlobalPos> clock, String job, List<Stop> stops, List<Area> areas, String name, Optional<BlockPos> corner) {
    public static final Order EMPTY = new Order(Optional.empty(), WorkTable.HAULING, List.of(), List.of(), "", Optional.empty());

    /** An order with no area being marked. */
    public Order(Optional<GlobalPos> clock, String job, List<Stop> stops, List<Area> areas, String name) {
        this(clock, job, stops, areas, name, Optional.empty());
    }
    /** Longest order name, in characters. */
    public static final int NAME = 24;
    /** Most stops and areas an order holds. */
    public static final int MAX_STOPS = 12, MAX_AREAS = 4;
    /** Widest area, in blocks on each side. */
    public static final int FIELD = 16;

    /** A container stop. {@code dropOff} false: a Pick-Up. */
    public record Stop(BlockPos pos, Direction face, boolean dropOff, ItemStack filter, boolean everythingElse) {
        public static final Codec<Stop> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Stop::pos),
                Direction.CODEC.optionalFieldOf("face", Direction.UP).forGetter(Stop::face),
                Codec.BOOL.optionalFieldOf("drop_off", false).forGetter(Stop::dropOff),
                ItemStack.OPTIONAL_CODEC.optionalFieldOf("filter", ItemStack.EMPTY).forGetter(Stop::filter),
                Codec.BOOL.optionalFieldOf("everything_else", false).forGetter(Stop::everythingElse)
        ).apply(i, Stop::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Stop> STREAM = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Stop::pos, Direction.STREAM_CODEC, Stop::face, ByteBufCodecs.BOOL, Stop::dropOff,
                ItemStack.OPTIONAL_STREAM_CODEC, Stop::filter, ByteBufCodecs.BOOL, Stop::everythingElse, Stop::new);

        /** Whether a stack may go into this Drop-Off by its filter (none: anything). */
        public boolean accepts(ItemStack stack) {
            return filter.isEmpty() || ItemStack.isSameItem(filter, stack);
        }

        /** Whether its filter names this stack's item (an empty filter names nothing). */
        public boolean names(ItemStack stack) {
            return !filter.isEmpty() && ItemStack.isSameItem(filter, stack);
        }

        public Stop withFilter(ItemStack f) {
            return new Stop(pos, face, dropOff, f.isEmpty() ? ItemStack.EMPTY : f.copyWithCount(1), everythingElse);
        }

        public Stop withEverythingElse(boolean e) {
            return new Stop(pos, face, dropOff, filter, e);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Stop s && s.pos.equals(pos) && s.face == face && s.dropOff == dropOff && s.everythingElse == everythingElse
                    && ItemStack.isSameItemSameComponents(s.filter, filter);
        }

        @Override
        public int hashCode() {
            return pos.hashCode() * 31 + (dropOff ? 1 : 0);
        }
    }

    /** An area between two corner blocks (inclusive): a field, a pen or a patrol. */
    public record Area(BlockPos a, BlockPos b) {
        public static final Codec<Area> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("a").forGetter(Area::a),
                BlockPos.CODEC.fieldOf("b").forGetter(Area::b)
        ).apply(i, Area::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Area> STREAM = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Area::a, BlockPos.STREAM_CODEC, Area::b, Area::new);

        public BlockPos min() {
            return new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        }

        public BlockPos max() {
            return new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        }

        /** Its width and depth in blocks (corners included). */
        public int sizeX() {
            return Math.abs(a.getX() - b.getX()) + 1;
        }

        public int sizeZ() {
            return Math.abs(a.getZ() - b.getZ()) + 1;
        }

        /** Whether a block is in it: inside the corners on the level, from one below the lower corner to two above the higher. */
        public boolean contains(BlockPos p) {
            BlockPos lo = min(), hi = max();
            return p.getX() >= lo.getX() && p.getX() <= hi.getX() && p.getZ() >= lo.getZ() && p.getZ() <= hi.getZ()
                    && p.getY() >= lo.getY() - 1 && p.getY() <= hi.getY() + 2;
        }

        /** The space it covers, as {@link #contains} reads it. */
        public net.minecraft.world.phys.AABB box() {
            BlockPos lo = min(), hi = max();
            return new net.minecraft.world.phys.AABB(lo.getX(), lo.getY() - 1, lo.getZ(), hi.getX() + 1, hi.getY() + 3, hi.getZ() + 1);
        }
    }

    public static final Codec<Order> CODEC = RecordCodecBuilder.create(i -> i.group(
            GlobalPos.CODEC.optionalFieldOf("clock").forGetter(Order::clock),
            Codec.STRING.optionalFieldOf("job", WorkTable.HAULING).forGetter(Order::job),
            Stop.CODEC.listOf().optionalFieldOf("stops", List.of()).forGetter(Order::stops),
            Area.CODEC.listOf().optionalFieldOf("areas", List.of()).forGetter(Order::areas),
            Codec.STRING.optionalFieldOf("name", "").forGetter(Order::name),
            BlockPos.CODEC.optionalFieldOf("corner").forGetter(Order::corner)
    ).apply(i, Order::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, Order> STREAM = StreamCodec.composite(
            ByteBufCodecs.optional(GlobalPos.STREAM_CODEC), Order::clock, ByteBufCodecs.STRING_UTF8, Order::job,
            Stop.STREAM.apply(ByteBufCodecs.list(64)), Order::stops, Area.STREAM.apply(ByteBufCodecs.list(16)), Order::areas,
            ByteBufCodecs.stringUtf8(96), Order::name, ByteBufCodecs.optional(BlockPos.STREAM_CODEC), Order::corner, Order::new);

    public List<Stop> pickUps() {
        return stops.stream().filter(s -> !s.dropOff()).toList();
    }

    public List<Stop> dropOffs() {
        return stops.stream().filter(Stop::dropOff).toList();
    }

    /** The stop at a block, or null. */
    public Stop stopAt(BlockPos pos) {
        for (Stop s : stops) if (s.pos().equals(pos)) return s;
        return null;
    }

    /** The area with a corner at a block, or null. */
    public Area areaAt(BlockPos pos) {
        for (Area a : areas) if (a.a().equals(pos) || a.b().equals(pos)) return a;
        return null;
    }

    /**
     * Where a stack may go, best first: the Drop-Offs whose filter names it; failing that, the open Drop-Offs (no
     * filter) and then those marked "Everything else". Sorting tries "Everything else" before an open Drop-Off.
     */
    public List<Stop> destinations(ItemStack stack, boolean sorting) {
        List<Stop> named = new ArrayList<>(), open = new ArrayList<>(), rest = new ArrayList<>();
        for (Stop s : dropOffs()) {
            if (s.names(stack)) named.add(s);
            else if (s.everythingElse()) rest.add(s);
            else if (s.filter().isEmpty()) open.add(s);
        }
        if (!named.isEmpty()) return named;
        List<Stop> out = new ArrayList<>(sorting ? rest : open);
        out.addAll(sorting ? open : rest);
        return out;
    }

    public Order withClock(GlobalPos c) {
        return new Order(Optional.ofNullable(c), job, stops, areas, name, corner);
    }

    public Order withJob(String j) {
        return new Order(clock, j, stops, areas, name, corner);
    }

    public Order withStops(List<Stop> s) {
        return new Order(clock, job, List.copyOf(s), areas, name, corner);
    }

    public Order withAreas(List<Area> a) {
        return new Order(clock, job, stops, List.copyOf(a), name, corner);
    }

    public Order withName(String n) {
        return new Order(clock, job, stops, areas, n.length() > NAME ? n.substring(0, NAME) : n, corner);
    }

    public Order withCorner(BlockPos c) {
        return new Order(clock, job, stops, areas, name, Optional.ofNullable(c));
    }

    /** A stop changed in place (the same index). */
    public Order withStop(int index, Stop s) {
        List<Stop> l = new ArrayList<>(stops);
        l.set(index, s);
        return withStops(l);
    }

    /** A printed Job Card: the order, who printed it and the day it was printed. */
    public record Card(Order order, String printer, long day) {
        public static final Codec<Card> CODEC = RecordCodecBuilder.create(i -> i.group(
                Order.CODEC.fieldOf("order").forGetter(Card::order),
                Codec.STRING.optionalFieldOf("printer", "").forGetter(Card::printer),
                Codec.LONG.optionalFieldOf("day", 0L).forGetter(Card::day)
        ).apply(i, Card::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Card> STREAM = StreamCodec.composite(
                Order.STREAM, Card::order, ByteBufCodecs.STRING_UTF8, Card::printer, ByteBufCodecs.VAR_LONG, Card::day, Card::new);
    }

    /** The order on a Clipboard (an empty one if none). */
    public static Order of(ItemStack clipboard) {
        Order o = clipboard.isEmpty() ? null : clipboard.get(com.coolfreeze23.headinajar.registry.WorkRegistry.ORDER.get());
        return o == null ? EMPTY : o;
    }

    /** The card on a Job Card item, or null. */
    public static Card card(ItemStack stack) {
        return stack.isEmpty() ? null : stack.get(com.coolfreeze23.headinajar.registry.WorkRegistry.JOB_CARD_DATA.get());
    }

    /**
     * The card a worker is working: its personal card, or the posting in its clock's rack it took (read only if the
     * clock's chunk is loaded). Null: none.
     */
    public static Card working(ServerLevel level, WorkerData d) {
        if (!d.card.isEmpty()) return card(d.card);
        if (d.rackSlot < 0 || d.clock == null || !d.clock.dimension().equals(level.dimension()) || !level.isLoaded(d.clock.pos())) return null;
        if (!(level.getBlockEntity(d.clock.pos()) instanceof com.coolfreeze23.headinajar.block.TimeClockBlockEntity clock)) return null;
        return card(clock.rack().getStackInSlot(d.rackSlot));
    }

    /** The job a worker is working, or null. */
    public static String jobOf(ServerLevel level, WorkerData d) {
        Card c = working(level, d);
        return c == null ? null : c.order().job();
    }
}
