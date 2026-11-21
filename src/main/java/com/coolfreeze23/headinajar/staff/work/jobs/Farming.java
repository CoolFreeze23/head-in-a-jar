package com.coolfreeze23.headinajar.staff.work.jobs;

import com.coolfreeze23.headinajar.staff.work.Order;
import com.coolfreeze23.headinajar.staff.work.WorkTable;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.WorkerLooks;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * Farming. A field (an area on the card): ripe crops are harvested, 8 a task: {@code CropBlock.isMaxAge} (wheat,
 * carrots, potatoes, beetroot and modded crops that extend it), nether wart at age 3, sweet berries at 2-3 by the bush's
 * own pick (the fox's twice), pumpkins and melons by taking the fruit only (one a stem points at), sugar cane and bamboo
 * above the bottom block. Drops come from the block's loot table with the worker as {@code THIS_ENTITY}; one seed goes
 * back into the ground ({@code getStateForAge(0)}); bare farmland is planted only from seeds the card's Pick-Up holds.
 * The harvest goes to the card's Drop-Off when the carry is full (6 stacks) or the task is done. Two farmers on one
 * field reserve their crops. Staff never trample farmland (a rule of {@code JobEvents}).
 * <p>
 * A pen (sheep or cows in the area), 4 animals a task: sheep shorn through NeoForge's {@code IShearable} with shears
 * taken from the Pick-Up into the worker's tool (wear 1 a sheep, half for a Toolsmith), cows milked with empty buckets
 * from the Pick-Up. Both need hands. No breeding.
 * <p>
 * A worker that carries nothing (the bee) pollinates instead: every 600 ticks one random crop in its field grows a stage.
 */
public final class Farming {
    static final int CROPS = 8, ANIMALS = 4;
    /** A bee's growth: one crop a stage this often. */
    static final int GROW_TICKS = 600;
    /** A cow milked rests this long. */
    static final int MILK_REST = 2400;
    /** A field is looked over at most this often. */
    static final int SCAN_TICKS = 40;
    /** No shears, buckets or seeds at the Pick-Up: it asks again after this long. */
    static final int ASK_AGAIN = 200;
    static final String TOOLSMITH = "toolsmith", BERRIES_X2 = "berries_x2";

    /** Crops being worked: level -> crop -> who and until when. */
    private static final Map<ResourceKey<Level>, Map<BlockPos, Hold>> RESERVED = new HashMap<>();
    /** Cows milked: level -> cow -> when it may be milked again. */
    private static final Map<ResourceKey<Level>, Map<UUID, Long>> MILKED = new HashMap<>();
    /** Sheep shorn by each worker (a Toolsmith wears its shears every other one). */
    private static final Map<Mob, Integer> SHORN = new WeakHashMap<>();

    private record Hold(UUID who, long until) {
    }

    private enum Kind {CROP, PLANT, SHEAR, MILK}

    private enum Fetch {SEEDS, SHEARS, BUCKETS}

    /** The worker's task in its field. */
    static final class Field {
        final Order.Card card;
        final ArrayDeque<BlockPos> ripe = new ArrayDeque<>(), bare = new ArrayDeque<>();
        final ArrayDeque<UUID> animals = new ArrayDeque<>();
        long scanAt, retryAt, readyAt;
        Kind kind;
        BlockPos target, stand;
        UUID animal;
        Fetch fetch;
        int fetchFrom;
        Order.Stop dest;
        final Jobs.Walk walk = new Jobs.Walk();
        int crops, shorn;
        long noShears, noBuckets, noSeeds;
        long nextGrow;
        int grown;

        Field(Order.Card card) {
            this.card = card;
        }

        void aim(Kind k, BlockPos at) {
            kind = k;
            target = at;
            stand = null;
            walk.reset();
            readyAt = 0;
        }

        void clear() {
            kind = null;
            target = null;
            animal = null;
            stand = null;
            walk.reset();
            readyAt = 0;
        }
    }

    private Farming() {
    }

    static void think(ServerLevel level, Mob mob, WorkerData d, WorkTable.Spec spec, Order.Card card) {
        Field f = d.task instanceof Field field && field.card.equals(card) ? field : null;
        if (f == null) {
            release(mob);
            f = new Field(card);
            d.task = f;
        }
        long now = level.getGameTime();
        if (now < f.retryAt) return;
        AABB box = Jobs.box(d);
        float pace = Jobs.pace(mob, spec, WorkTable.FARMING);
        if (spec.carry() <= 0) {
            pollinate(level, mob, d, f, pace, box);
            return;
        }
        // the harvest goes in when the carry is full or the task is done
        if (d.carrying() && (f.dest != null || full(d) || f.crops >= CROPS || f.shorn >= ANIMALS || f.kind == null && nothingLeft(f))) {
            deliver(level, mob, d, f, pace, box);
            return;
        }
        finishTasks(mob, d, f);
        if (f.fetch != null) {
            fetch(level, mob, d, f, pace, box);
            return;
        }
        if (f.kind == null) choose(level, mob, d, spec, f, box);
        if (f.fetch != null) return;
        if (f.kind == null) {
            if (d.carrying()) return;
            Jobs.working(d);
            d.status = "staff.headinajar.say.field_done";
            f.retryAt = now + Jobs.RETRY_TICKS;
            return;
        }
        switch (f.kind) {
            case CROP -> crop(level, mob, d, spec, f, pace, box);
            case PLANT -> plant(level, mob, d, f, pace, box);
            case SHEAR, MILK -> animal(level, mob, d, spec, f, pace, box);
        }
    }

    private static boolean nothingLeft(Field f) {
        return f.ripe.isEmpty() && f.bare.isEmpty() && f.animals.isEmpty();
    }

    /** Tasks counted when they are done and nothing of theirs is still carried. */
    private static void finishTasks(Mob mob, WorkerData d, Field f) {
        if (d.carrying()) return;
        while (f.crops >= CROPS) {
            f.crops -= CROPS;
            Jobs.taskDone(mob);
        }
        while (f.shorn >= ANIMALS) {
            f.shorn -= ANIMALS;
            Jobs.taskDone(mob);
        }
    }

    // --- looking over the field ---------------------------------------------------------------------------------------

    private static void choose(ServerLevel level, Mob mob, WorkerData d, WorkTable.Spec spec, Field f, AABB box) {
        long now = level.getGameTime();
        if (nothingLeft(f) && now >= f.scanAt) scan(level, mob, d, spec, f);
        while (!f.ripe.isEmpty()) {
            BlockPos p = f.ripe.poll();
            if (level.isLoaded(p) && ripe(level, p, level.getBlockState(p)) && reserve(level, mob, p)) {
                f.aim(Kind.CROP, p);
                return;
            }
        }
        if (spec.hands() && f.animals.size() > 1) nearestFirst(level, mob, f.animals);
        while (spec.hands() && !f.animals.isEmpty()) {
            UUID id = f.animals.peek();
            Entity e = level.getEntity(id);
            if (!(e instanceof Animal a) || !a.isAlive() || !inField(f, a.blockPosition()) || !box.contains(a.position())) {
                f.animals.poll();
                continue;
            }
            if (a instanceof Sheep sheep) {
                if (!sheep.readyForShearing()) {
                    f.animals.poll();
                    continue;
                }
                if (!shears(d.tool)) {
                    if (now < f.noShears) {
                        f.animals.poll();
                        continue;
                    }
                    f.fetch = Fetch.SHEARS;
                    f.fetchFrom = 0;
                    return;
                }
                f.animals.poll();
                f.aim(Kind.SHEAR, a.blockPosition());
                f.animal = id;
                return;
            }
            if (a instanceof Cow) {
                if (!milkable(level, a, now)) {
                    f.animals.poll();
                    continue;
                }
                if (count(d, s -> s.is(Items.BUCKET)) == 0) {
                    if (now < f.noBuckets) {
                        f.animals.poll();
                        continue;
                    }
                    f.fetch = Fetch.BUCKETS;
                    f.fetchFrom = 0;
                    return;
                }
                f.animals.poll();
                f.aim(Kind.MILK, a.blockPosition());
                f.animal = id;
                return;
            }
            f.animals.poll();
        }
        while (!f.bare.isEmpty()) {
            BlockPos p = f.bare.peek();
            if (!level.isLoaded(p) || !bare(level, p)) {
                f.bare.poll();
                continue;
            }
            if (seedFor(level, d, p) < 0) {
                if (now < f.noSeeds || f.card.order().pickUps().isEmpty()) {
                    f.bare.clear();
                    return;
                }
                f.fetch = Fetch.SEEDS;
                f.fetchFrom = 0;
                return;
            }
            f.bare.poll();
            f.aim(Kind.PLANT, p);
            return;
        }
    }

    /** Looks over every area of the card once (the blocks of a 16 by 16 field, its height and two above). */
    private static void scan(ServerLevel level, Mob mob, WorkerData d, WorkTable.Spec spec, Field f) {
        f.scanAt = level.getGameTime() + SCAN_TICKS;
        BlockPos clock = d.clock.pos();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (Order.Area a : f.card.order().areas()) {
            if (!level.isLoaded(a.a()) || !level.isLoaded(a.b())) continue;
            BlockPos lo = a.min(), hi = a.max();
            for (int x = lo.getX(); x <= hi.getX(); x++) {
                for (int z = lo.getZ(); z <= hi.getZ(); z++) {
                    for (int y = lo.getY() - 1; y <= hi.getY() + 2; y++) {
                        p.set(x, y, z);
                        if (!Workforce.inBox(clock, p)) continue;
                        BlockState s = level.getBlockState(p);
                        if (s.isAir()) continue;
                        if (ripe(level, p, s) && !reservedByOther(level, mob, p)) f.ripe.add(p.immutable());
                        else if (s.getBlock() instanceof FarmBlock && level.getBlockState(p.above()).isAir()) f.bare.add(p.above().immutable());
                    }
                }
            }
            if (spec.hands()) {
                long now = level.getGameTime();
                for (Animal e : level.getEntitiesOfClass(Animal.class, a.box(), x -> !x.isBaby() && !Workforce.isWorker(x)
                        && (x instanceof Sheep s && s.readyForShearing() || x instanceof Cow && milkable(level, x, now)))) {
                    f.animals.add(e.getUUID());
                }
            }
        }
    }

    /** The animal nearest the worker to the front of the queue. */
    private static void nearestFirst(ServerLevel level, Mob mob, ArrayDeque<UUID> animals) {
        UUID best = null;
        double bestD = Double.MAX_VALUE;
        for (UUID id : animals) {
            Entity e = level.getEntity(id);
            if (e == null) continue;
            double dist = e.distanceToSqr(mob);
            if (dist < bestD) {
                bestD = dist;
                best = id;
            }
        }
        if (best != null && !best.equals(animals.peek())) {
            animals.remove(best);
            animals.addFirst(best);
        }
    }

    private static boolean inField(Field f, BlockPos p) {
        for (Order.Area a : f.card.order().areas()) if (a.contains(p)) return true;
        return false;
    }

    /** Whether a block is a crop ready to harvest (the plan's list). */
    static boolean ripe(Level level, BlockPos pos, BlockState s) {
        Block b = s.getBlock();
        if (b instanceof CropBlock crop) return crop.isMaxAge(s);
        if (b instanceof NetherWartBlock) return s.getValue(NetherWartBlock.AGE) >= 3;
        if (b instanceof SweetBerryBushBlock) return s.getValue(SweetBerryBushBlock.AGE) >= 2;
        if (s.is(Blocks.PUMPKIN) || s.is(Blocks.MELON)) return onStem(level, pos);
        if (s.is(Blocks.SUGAR_CANE) || s.is(Blocks.BAMBOO)) {
            return !level.getBlockState(pos.below()).is(b) && level.getBlockState(pos.above()).is(b);
        }
        return false;
    }

    /** A pumpkin or melon a stem points at (a pumpkin on its own is somebody's decoration). */
    private static boolean onStem(Level level, BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos n = pos.relative(dir);
            if (!level.isLoaded(n)) continue;
            BlockState s = level.getBlockState(n);
            if (s.getBlock() instanceof AttachedStemBlock && s.getValue(AttachedStemBlock.FACING) == dir.getOpposite()) return true;
        }
        return false;
    }

    private static boolean bare(Level level, BlockPos above) {
        return level.getBlockState(above).isAir() && level.getBlockState(above.below()).getBlock() instanceof FarmBlock;
    }

    // --- reservations -------------------------------------------------------------------------------------------------

    private static boolean reserve(ServerLevel level, Mob mob, BlockPos p) {
        Map<BlockPos, Hold> m = RESERVED.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        long now = level.getGameTime();
        Hold h = m.get(p);
        if (h != null && !h.who().equals(mob.getUUID()) && h.until() > now) return false;
        m.put(p, new Hold(mob.getUUID(), now + 400));
        return true;
    }

    private static boolean reservedByOther(ServerLevel level, Mob mob, BlockPos p) {
        Map<BlockPos, Hold> m = RESERVED.get(level.dimension());
        Hold h = m == null ? null : m.get(p);
        return h != null && !h.who().equals(mob.getUUID()) && h.until() > level.getGameTime();
    }

    private static void unreserve(ServerLevel level, BlockPos p) {
        Map<BlockPos, Hold> m = RESERVED.get(level.dimension());
        if (m != null) m.remove(p);
    }

    /** Lets go of every crop this worker holds. */
    static void release(Mob mob) {
        UUID id = mob.getUUID();
        for (Map<BlockPos, Hold> m : RESERVED.values()) m.values().removeIf(h -> h.who().equals(id));
    }

    /** A level unloads: its reservations and milking times go. */
    static void unload(ResourceKey<Level> level) {
        RESERVED.remove(level);
        MILKED.remove(level);
    }

    // --- harvesting ---------------------------------------------------------------------------------------------------

    private static void crop(ServerLevel level, Mob mob, WorkerData d, WorkTable.Spec spec, Field f, float pace, AABB box) {
        BlockPos p = f.target;
        if (!level.isLoaded(p) || !ripe(level, p, level.getBlockState(p))) {
            unreserve(level, p);
            f.clear();
            return;
        }
        if (f.stand == null) f.stand = Jobs.standBy(level, mob, p, box);
        if (!Jobs.walk(mob, d, f.walk, p, f.stand, pace, box)) {
            if (f.walk.gaveUp) {
                Jobs.held(mob, d, "cant_get_there");
                unreserve(level, p);
                f.clear();
            }
            return;
        }
        long now = level.getGameTime();
        if (f.readyAt == 0) {
            f.readyAt = now + Jobs.actionTicks(pace);
            mob.getLookControl().setLookAt(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
            return;
        }
        if (now < f.readyAt) return;
        boolean was = d.carrying();
        for (ItemStack s : harvest(level, mob, p, spec)) carry(mob, d, s);
        unreserve(level, p);
        f.crops++;
        f.clear();
        mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        Jobs.working(d);
        if (was != d.carrying()) WorkerLooks.changed(mob);
    }

    /**
     * Harvests a ripe crop: its loot with the worker as {@code THIS_ENTITY} (no tool), and the crop set back: a seed from
     * the drops replanted at age 0, a berry bush back to age 1, a pumpkin or melon gone, sugar cane or bamboo down to its
     * bottom block. Returns the drops left over.
     */
    static List<ItemStack> harvest(ServerLevel level, Mob mob, BlockPos pos, WorkTable.Spec spec) {
        BlockState s = level.getBlockState(pos);
        Block b = s.getBlock();
        List<ItemStack> drops = new ArrayList<>();
        if (b instanceof SweetBerryBushBlock) {
            int age = s.getValue(SweetBerryBushBlock.AGE);
            int n = 1 + level.random.nextInt(2) + (age == 3 ? 1 : 0);
            if (spec != null && spec.quirk(BERRIES_X2)) n *= 2;
            drops.add(new ItemStack(Items.SWEET_BERRIES, n));
            level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1, 0.8f + level.random.nextFloat() * 0.4f);
            BlockState after = s.setValue(SweetBerryBushBlock.AGE, 1);
            level.setBlock(pos, after, Block.UPDATE_CLIENTS);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(mob, after));
            return drops;
        }
        if (s.is(Blocks.SUGAR_CANE) || s.is(Blocks.BAMBOO)) {
            // from the top down to just above the bottom block, so nothing above pops off into the world
            int top = pos.getY();
            while (level.isLoaded(new BlockPos(pos.getX(), top + 1, pos.getZ())) && level.getBlockState(new BlockPos(pos.getX(), top + 1, pos.getZ())).is(b)) top++;
            for (int y = top; y > pos.getY(); y--) {
                BlockPos at = new BlockPos(pos.getX(), y, pos.getZ());
                BlockState piece = level.getBlockState(at);
                drops.addAll(Block.getDrops(piece, level, at, null, mob, ItemStack.EMPTY));
                level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                level.levelEvent(2001, at, Block.getId(piece));
            }
            level.gameEvent(GameEvent.BLOCK_DESTROY, pos.above(), GameEvent.Context.of(mob, s));
            return drops;
        }
        drops.addAll(Block.getDrops(s, level, pos, level.getBlockEntity(pos), mob, ItemStack.EMPTY));
        level.levelEvent(2001, pos, Block.getId(s));
        BlockState after = Blocks.AIR.defaultBlockState();
        if (b instanceof CropBlock crop) {
            if (takeOne(drops, crop.getCloneItemStack(level, pos, s))) after = crop.getStateForAge(0);
        } else if (b instanceof NetherWartBlock) {
            if (takeOne(drops, new ItemStack(Items.NETHER_WART))) after = s.setValue(NetherWartBlock.AGE, 0);
        }
        level.setBlock(pos, after, Block.UPDATE_ALL);
        level.gameEvent(after.isAir() ? GameEvent.BLOCK_DESTROY : GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(mob, after));
        return drops;
    }

    /** Takes one of an item out of a list of drops; false if there is none. */
    private static boolean takeOne(List<ItemStack> drops, ItemStack kind) {
        if (kind.isEmpty()) return false;
        for (ItemStack s : drops) {
            if (ItemStack.isSameItem(s, kind)) {
                s.shrink(1);
                drops.removeIf(ItemStack::isEmpty);
                return true;
            }
        }
        return false;
    }

    // --- planting -----------------------------------------------------------------------------------------------------

    /** The carried slot holding a seed that grows on this farmland, or -1. */
    private static int seedFor(Level level, WorkerData d, BlockPos above) {
        for (int i = 0; i < d.carried.size(); i++) {
            ItemStack s = d.carried.get(i);
            if (seed(s) && ((BlockItem) s.getItem()).getBlock().defaultBlockState().canSurvive(level, above)) return i;
        }
        return -1;
    }

    /** A seed this job plants: an item that places a crop. */
    static boolean seed(ItemStack s) {
        return !s.isEmpty() && s.getItem() instanceof BlockItem bi && bi.getBlock() instanceof CropBlock;
    }

    private static void plant(ServerLevel level, Mob mob, WorkerData d, Field f, float pace, AABB box) {
        BlockPos p = f.target;
        if (!level.isLoaded(p) || !bare(level, p)) {
            f.clear();
            return;
        }
        if (f.stand == null) f.stand = Jobs.standBy(level, mob, p, box);
        if (!Jobs.walk(mob, d, f.walk, p, f.stand, pace, box)) {
            if (f.walk.gaveUp) {
                Jobs.held(mob, d, "cant_get_there");
                f.clear();
            }
            return;
        }
        long now = level.getGameTime();
        if (f.readyAt == 0) {
            f.readyAt = now + Jobs.actionTicks(pace);
            return;
        }
        if (now < f.readyAt) return;
        int i = seedFor(level, d, p);
        f.clear();
        if (i < 0) return;
        ItemStack seed = d.carried.get(i);
        BlockState crop = ((BlockItem) seed.getItem()).getBlock().defaultBlockState();
        level.setBlock(p, crop, Block.UPDATE_ALL);
        level.gameEvent(GameEvent.BLOCK_PLACE, p, GameEvent.Context.of(mob, crop));
        level.playSound(null, p, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1, 1);
        seed.shrink(1);
        if (seed.isEmpty()) d.carried.remove(i);
        if (!d.carrying()) WorkerLooks.changed(mob);
        mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        Jobs.working(d);
    }

    // --- the pen ------------------------------------------------------------------------------------------------------

    private static boolean shears(ItemStack s) {
        return !s.isEmpty() && (s.getItem() instanceof ShearsItem || s.is(Tags.Items.TOOLS_SHEAR));
    }

    private static boolean milkable(ServerLevel level, Animal cow, long now) {
        Map<UUID, Long> m = MILKED.get(level.dimension());
        Long next = m == null ? null : m.get(cow.getUUID());
        return next == null || next <= now;
    }

    private static void animal(ServerLevel level, Mob mob, WorkerData d, WorkTable.Spec spec, Field f, float pace, AABB box) {
        Entity e = level.getEntity(f.animal);
        if (!(e instanceof Animal a) || !a.isAlive() || !box.contains(a.position())) {
            f.clear();
            return;
        }
        BlockPos at = a.blockPosition();
        double reach = Jobs.ARRIVE + a.getBbWidth() / 2;
        boolean close = mob.distanceToSqr(a) <= reach * reach;
        if (!close) {
            f.readyAt = 0;
            Jobs.walk(mob, d, f.walk, at, at, pace, box);
            if (f.walk.gaveUp) {
                Jobs.held(mob, d, "cant_get_there");
                f.clear();
            }
            return;
        }
        long now = level.getGameTime();
        mob.getLookControl().setLookAt(a, 30, 30);
        if (f.readyAt == 0) {
            f.readyAt = now + Jobs.actionTicks(pace);
            return;
        }
        if (now < f.readyAt) return;
        boolean was = d.carrying();
        if (f.kind == Kind.SHEAR && a instanceof Sheep sheep) {
            if (shears(d.tool) && sheep.isShearable(null, d.tool, level, at)) {
                List<ItemStack> wool = sheep.onSheared(null, d.tool, level, at);
                level.gameEvent(GameEvent.SHEAR, a.position(), GameEvent.Context.of(mob));
                for (ItemStack w : wool) carry(mob, d, w.copy());
                wear(level, mob, d, spec);
                f.shorn++;
            }
        } else if (f.kind == Kind.MILK && a instanceof Cow) {
            int b = find(d, s -> s.is(Items.BUCKET));
            if (b >= 0) {
                ItemStack buckets = d.carried.get(b);
                buckets.shrink(1);
                if (buckets.isEmpty()) d.carried.remove(b);
                carry(mob, d, new ItemStack(Items.MILK_BUCKET));
                level.playSound(null, a.getX(), a.getY(), a.getZ(), SoundEvents.COW_MILK, SoundSource.NEUTRAL, 1, 1);
                MILKED.computeIfAbsent(level.dimension(), k -> new HashMap<>()).put(a.getUUID(), now + MILK_REST);
                f.shorn++;
            }
        }
        f.clear();
        mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        Jobs.working(d);
        if (was != d.carrying()) WorkerLooks.changed(mob);
    }

    /** One sheep's wear on the shears (a Toolsmith's every other sheep); worn out, they are gone. */
    private static void wear(ServerLevel level, Mob mob, WorkerData d, WorkTable.Spec spec) {
        int n = SHORN.merge(mob, 1, Integer::sum);
        if (spec.quirk(TOOLSMITH) && n % 2 == 1) return;
        d.tool.hurtAndBreak(1, level, mob, item -> {
        });
        if (d.tool.isEmpty()) d.tool = ItemStack.EMPTY;
    }

    // --- fetching from the Pick-Up and delivering to the Drop-Off ------------------------------------------------------

    private static void fetch(ServerLevel level, Mob mob, WorkerData d, Field f, float pace, AABB box) {
        List<Order.Stop> picks = f.card.order().pickUps();
        long now = level.getGameTime();
        if (f.fetchFrom >= picks.size()) {
            // none of the Pick-Ups has it
            switch (f.fetch) {
                case SHEARS -> {
                    f.noShears = now + ASK_AGAIN;
                    Jobs.held(mob, d, "no_tool", Items.SHEARS.getDescription());
                }
                case BUCKETS -> {
                    f.noBuckets = now + ASK_AGAIN;
                    Jobs.held(mob, d, "no_buckets");
                }
                case SEEDS -> f.noSeeds = now + ASK_AGAIN;
            }
            f.fetch = null;
            f.clear();
            return;
        }
        Order.Stop stop = picks.get(f.fetchFrom);
        if (f.target == null || !f.target.equals(stop.pos())) {
            f.target = stop.pos();
            f.stand = null;
            f.walk.reset();
            f.readyAt = 0;
        }
        if (!level.isLoaded(stop.pos())) {
            f.fetchFrom++;
            f.target = null;
            return;
        }
        if (f.stand == null) f.stand = Jobs.standBy(level, mob, stop.pos(), box);
        if (!Jobs.walk(mob, d, f.walk, stop.pos(), f.stand, pace, box)) {
            if (f.walk.gaveUp) {
                Jobs.held(mob, d, "cant_get_there");
                f.fetchFrom++;
                f.target = null;
            }
            return;
        }
        if (f.readyAt == 0) {
            f.readyAt = now + Jobs.actionTicks(pace);
            return;
        }
        if (now < f.readyAt) return;
        f.readyAt = 0;
        IItemHandler from = Jobs.handler(level, stop);
        boolean was = d.carrying();
        boolean got = false;
        if (from != null && !Jobs.catOn(level, stop.pos())) {
            switch (f.fetch) {
                case SHEARS -> {
                    ItemStack s = extract(from, Farming::shears, 1);
                    s = s.isEmpty() ? s : com.coolfreeze23.headinajar.staff.work.Quirks.taken(mob, d, s);
                    if (!s.isEmpty()) {
                        if (!d.tool.isEmpty()) carry(mob, d, d.tool);
                        d.tool = s;
                        got = true;
                    }
                }
                case BUCKETS -> {
                    ItemStack s = extract(from, x -> x.is(Items.BUCKET), Math.max(1, ANIMALS - f.shorn));
                    s = s.isEmpty() ? s : com.coolfreeze23.headinajar.staff.work.Quirks.taken(mob, d, s);
                    if (!s.isEmpty()) {
                        carry(mob, d, s);
                        got = true;
                    }
                }
                case SEEDS -> {
                    ItemStack s = extract(from, Farming::seed, Math.max(1, Math.min(64, f.bare.size())));
                    s = s.isEmpty() ? s : com.coolfreeze23.headinajar.staff.work.Quirks.taken(mob, d, s);
                    if (!s.isEmpty()) {
                        carry(mob, d, s);
                        got = true;
                    }
                }
            }
        }
        if (!got) {
            f.fetchFrom++;
            f.target = null;
            return;
        }
        mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        f.fetch = null;
        f.clear();
        Jobs.working(d);
        if (was != d.carrying()) WorkerLooks.changed(mob);
    }

    /** Takes up to {@code n} of the first kind matching from a container, simulated first. */
    private static ItemStack extract(IItemHandler from, Predicate<ItemStack> kind, int n) {
        for (int slot = 0; slot < from.getSlots(); slot++) {
            ItemStack seen = from.extractItem(slot, n, true);
            if (seen.isEmpty() || !kind.test(seen)) continue;
            ItemStack got = from.extractItem(slot, Math.min(n, seen.getCount()), false);
            if (!got.isEmpty()) return got;
        }
        return ItemStack.EMPTY;
    }

    private static void deliver(ServerLevel level, Mob mob, WorkerData d, Field f, float pace, AABB box) {
        long now = level.getGameTime();
        ItemStack load = d.carried.get(0);
        if (f.dest == null || !f.dest.accepts(load) && !f.dest.everythingElse()) {
            f.dest = null;
            for (Order.Stop s : f.card.order().destinations(load, false)) {
                if (Hauling.room(level, s, load) > 0) {
                    f.dest = s;
                    break;
                }
            }
            if (f.dest == null) {
                Jobs.held(mob, d, f.card.order().destinations(load, false).isEmpty() ? "cant_get_there" : "drop_off_full");
                f.retryAt = now + Jobs.RETRY_TICKS;
                return;
            }
            f.target = f.dest.pos();
            f.stand = null;
            f.walk.reset();
            f.readyAt = 0;
        }
        Order.Stop dest = f.dest;
        if (f.stand == null) f.stand = Jobs.standBy(level, mob, dest.pos(), box);
        if (!Jobs.walk(mob, d, f.walk, dest.pos(), f.stand, pace, box)) {
            if (f.walk.gaveUp) {
                Jobs.held(mob, d, "cant_get_there");
                f.dest = null;
                f.retryAt = now + Jobs.RETRY_TICKS;
            }
            return;
        }
        if (f.readyAt == 0) {
            f.readyAt = now + Jobs.actionTicks(pace);
            return;
        }
        if (now < f.readyAt) return;
        f.readyAt = 0;
        IItemHandler into = Jobs.handler(level, dest);
        if (into == null || Jobs.catOn(level, dest.pos())) {
            Jobs.held(mob, d, into == null ? "cant_get_there" : "cat_on_chest");
            f.dest = null;
            f.retryAt = now + Jobs.RETRY_TICKS;
            return;
        }
        // everything this Drop-Off takes goes in at once
        for (int i = 0; i < d.carried.size(); i++) {
            ItemStack s = d.carried.get(i);
            if (!dest.accepts(s) && !dest.everythingElse()) continue;
            ItemStack rest = ItemHandlerHelper.insertItemStacked(into, s.copy(), false);
            if (rest.isEmpty()) d.carried.remove(i--);
            else d.carried.set(i, rest);
        }
        mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        f.dest = null;
        f.clear();
        if (!d.carrying()) {
            WorkerLooks.changed(mob);
            finishTasks(mob, d, f);
            Jobs.working(d);
        }
    }

    // --- the carry ----------------------------------------------------------------------------------------------------

    /** Whether the carry is full (6 stacks). */
    private static boolean full(WorkerData d) {
        return d.carried.size() >= WorkerData.MAX_CARRIED;
    }

    /** Into the carry, merged where it fits; what does not fit is set down at its feet. */
    static void carry(Mob mob, WorkerData d, ItemStack stack) {
        if (stack.isEmpty()) return;
        for (ItemStack c : d.carried) {
            if (ItemStack.isSameItemSameComponents(c, stack) && c.getCount() < c.getMaxStackSize()) {
                int n = Math.min(stack.getCount(), c.getMaxStackSize() - c.getCount());
                c.grow(n);
                stack.shrink(n);
                if (stack.isEmpty()) return;
            }
        }
        if (d.carried.size() < WorkerData.MAX_CARRIED) d.carried.add(stack);
        else mob.spawnAtLocation(stack);
    }

    private static int count(WorkerData d, Predicate<ItemStack> kind) {
        int n = 0;
        for (ItemStack s : d.carried) if (kind.test(s)) n += s.getCount();
        return n;
    }

    private static int find(WorkerData d, Predicate<ItemStack> kind) {
        for (int i = 0; i < d.carried.size(); i++) if (kind.test(d.carried.get(i))) return i;
        return -1;
    }

    // --- the bee ----------------------------------------------------------------------------------------------------

    /** A bee in its field: it flies from point to point, and every 600 ticks one crop there grows a stage. */
    private static void pollinate(ServerLevel level, Mob mob, WorkerData d, Field f, float pace, AABB box) {
        long now = level.getGameTime();
        List<Order.Area> areas = f.card.order().areas();
        if (areas.isEmpty()) return;
        Jobs.working(d);
        if (f.nextGrow == 0) f.nextGrow = now + GROW_TICKS;
        Order.Area a = areas.get(mob.getRandom().nextInt(areas.size()));
        if (mob.getNavigation().isDone() && (f.target == null || Jobs.arrived(mob, f.target) || f.walk.gaveUp)) {
            f.target = Security.patrolPoint(level, mob, a.box().intersect(box), box);
            f.walk.reset();
        }
        if (f.target != null) Jobs.walk(mob, d, f.walk, f.target, f.target, pace, box);
        if (now < f.nextGrow) return;
        f.nextGrow = now + GROW_TICKS;
        if (grow(level, a, mob.getRandom(), d.clock.pos())) {
            if (++f.grown % CROPS == 0) Jobs.taskDone(mob);
        }
    }

    /** One random crop in an area that is not yet ripe grows a stage; returns whether one did. */
    public static boolean grow(ServerLevel level, Order.Area a, net.minecraft.util.RandomSource random, BlockPos clock) {
        BlockPos lo = a.min(), hi = a.max();
        List<BlockPos> growing = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = lo.getX(); x <= hi.getX(); x++) {
            for (int z = lo.getZ(); z <= hi.getZ(); z++) {
                for (int y = lo.getY() - 1; y <= hi.getY() + 2; y++) {
                    p.set(x, y, z);
                    if (!level.isLoaded(p) || !Workforce.inBox(clock, p)) continue;
                    BlockState s = level.getBlockState(p);
                    if (s.getBlock() instanceof CropBlock crop && !crop.isMaxAge(s)) growing.add(p.immutable());
                }
            }
        }
        if (growing.isEmpty()) return false;
        BlockPos at = growing.get(random.nextInt(growing.size()));
        BlockState s = level.getBlockState(at);
        CropBlock crop = (CropBlock) s.getBlock();
        level.setBlock(at, crop.getStateForAge(Math.min(crop.getMaxAge(), crop.getAge(s) + 1)), Block.UPDATE_CLIENTS);
        level.levelEvent(1505, at, 6);
        return true;
    }
}
