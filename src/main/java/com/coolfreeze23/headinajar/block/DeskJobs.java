package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.feedback.Refuser;
import com.coolfreeze23.headinajar.fx.HeadSounds;
import com.coolfreeze23.headinajar.staff.Band;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffBlocks;
import com.coolfreeze23.headinajar.staff.StaffFile;
import com.coolfreeze23.headinajar.staff.StaffTable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SuspiciousEffectHolder;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Lunch and the desk jobs of heads on plinths. A fed head eats on a staggered two-second pass and does its species'
 * job (the staff table's {@code desk}); a head with an empty bowl returns at once. Daily jobs give at the head's dawn.
 * The jobs that take an item are used from {@link JarredHeadBlock#useItemOn}; the bell's from {@link #ring}.
 */
public final class DeskJobs {
    /** The pass: every 40 ticks, staggered by position. */
    public static final int PASS = 40;
    /** Broth: 1 mB every 72 ticks of eating, counted in hundredths of a tick. */
    public static final int EAT = 72 * 100;
    /** A Disengaged head complains this often (ticks, staggered). */
    public static final int COMPLAIN = 2400;
    /** Desk-job reach (blocks), the cow's and the evoker's; the cat keeps creepers off as a live cat does. */
    public static final double REACH = 8, CAT_CREEPERS = 6, CAT_PHANTOMS = 16, GOLEM = 16, ALLAY = 16, WARDEN = 16;
    /** The piglin: inspects gold for 6 s; turns after 15 s off a Nether floor outside a piglin-safe dimension. */
    public static final int INSPECT = 120, TURNS = 300;
    /** The blaze: cooking progress it adds a pass (a quarter of the pass's 40). */
    public static final int BLAZE = 10;
    /** The warden: this many vibrations in a minute is too many; the shriek's Darkness (ticks). */
    public static final int TOO_LOUD = 20, DARKNESS = 200;
    /** Morale at dawn and any time. */
    public static final int FED = 5, FED_CAP = 60, HUNGRY = -5, HUNGRY_FLOOR = 30, COLLEAGUE = 2, RIVAL = -5, DISLIKE = -5,
            CAKE = 40, SAMPLED = -10, GOLEM_CHEER = 2;
    /** What a head may dislike: a bit each, in this order. */
    public static final List<String> DISLIKES = List.of("rain", "thunder", "open_sky", "hot", "warm", "not_nether", "warped_fungus", "noise", "water");
    /** The Nether floor a piglin head is calm on. */
    public static final TagKey<Block> NETHER_FLOOR = TagKey.create(Registries.BLOCK, HeadInAJar.id("nether_floor"));

    /**
     * A pair of species that mind each other at dawn, by id (a pair with a creature that is not registered never meets),
     * and the line one of them says when the other is close enough ({@code desk.headinajar.<line>}; null for none).
     */
    public record Pair(String a, String b, String line) {
        boolean of(String x, String y) {
            return a.equals(x) && b.equals(y) || a.equals(y) && b.equals(x);
        }
    }

    /** Rival species: a head with one of these within 4 blocks loses 5 at dawn. */
    private static final List<Pair> RIVALS = List.of(
            new Pair("minecraft:villager", "minecraft:zombie_villager", null), new Pair("minecraft:villager", "minecraft:pillager", null),
            new Pair("minecraft:villager", "minecraft:vindicator", null), new Pair("minecraft:skeleton", "minecraft:wolf", null),
            new Pair("minecraft:piglin", "minecraft:wither_skeleton", null), new Pair("minecraft:creeper", "minecraft:cat", null),
            new Pair("minecraft:creeper", "minecraft:ocelot", null), new Pair("minecraft:spider", "minecraft:armadillo", null),
            // 0.8.0, the new arrivals: one works when nobody watches, one weeps when everybody does; and two copper golem
            // departments, one from each supplier
            new Pair("minecraft:creaking", "minecraft:ghast", "rivals.night_auditor"),
            new Pair("minecraft:copper_golem", "friendsandfoes:copper_golem", "rivals.copper_golems"));
    /** Colleagues across departments: a head with one of these within 8 counts it as a colleague (+2 at dawn, once). */
    private static final List<Pair> COLLEAGUES = List.of(new Pair("minecraft:happy_ghast", "minecraft:phantom", "colleagues.wellbeing_sleep"));

    /** One blaze per furnace: when each furnace was last breathed on, per level (not saved). */
    private static final Map<Level, Map<Long, Long>> BREATHED = new WeakHashMap<>();
    /** What tests can read: desk lines said, newest last ("x y z|key"). */
    private static final List<String> SAID = new ArrayList<>();

    private DeskJobs() {
    }

    public static List<String> said() {
        return SAID;
    }

    private static boolean on() {
        return Config.get(Config.DESK_JOBS, true);
    }

    /** The head's front (where it looks, where things pop out of its bowl). */
    public static Direction front(JarredHeadBlockEntity head) {
        return head.getBlockState().getValue(JarredHeadBlock.FACING).getOpposite();
    }

    /** Gives something from the bowl: it pops out of the plinth's front. */
    public static void give(ServerLevel level, JarredHeadBlockEntity head, ItemStack stack) {
        if (!stack.isEmpty()) Block.popResourceFromFace(level, head.getBlockPos(), front(head), stack);
    }

    /** A ghast's tear: it runs down the front of the glass and drops at the foot of the plinth (no scatter). */
    static void weep(ServerLevel level, JarredHeadBlockEntity head, ItemStack stack) {
        Direction f = front(head);
        var at = net.minecraft.world.phys.Vec3.atCenterOf(head.getBlockPos()).add(f.getStepX() * 0.62, 0.15, f.getStepZ() * 0.62);
        var tear = new net.minecraft.world.entity.item.ItemEntity(level, at.x, at.y, at.z, stack, 0, 0, 0);
        tear.setDefaultPickUpDelay();
        level.addFreshEntity(tear);
    }

    /** Rate jobs: Engaged a fifth faster, Disengaged (and the Grievance label) at half speed. */
    public static float rate(JarredHeadBlockEntity head) {
        Band b = Band.of(head.staff());
        return switch (b) {
            case ENGAGED -> 1.2f;
            case DISENGAGED, GRIEVANCE -> 0.5f;
            default -> 1f;
        };
    }

    private static void line(JarredHeadBlockEntity head, ServerPlayer to, String key, Object... args) {
        HeadSpeak.desk(head, to, key, args);
        SAID.add(head.getBlockPos().toShortString() + "|" + key);
    }

    /** A desk moment said in the head's own voice: {@code desk.headinajar.<key>.noise} and {@code .hr}. */
    private static void voice(JarredHeadBlockEntity head, ServerPlayer to, String key) {
        HeadSpeak.say(head, to, Component.translatable("desk.headinajar." + key + ".noise"), Component.translatable("desk.headinajar." + key + ".hr"));
        SAID.add(head.getBlockPos().toShortString() + "|" + key);
    }

    // --- the tick ------------------------------------------------------------------------------------------------------

    public static void tick(ServerLevel level, BlockPos pos, JarredHeadBlockEntity head) {
        long now = level.getGameTime();
        if (head.inspectUntil != 0 && now >= head.inspectUntil) barter(level, head);
        // a Disengaged head complains whether or not it is fed (the band is a field read)
        if (head.band().low() && (now + Math.floorMod(pos.hashCode(), COMPLAIN)) % COMPLAIN == 0) complain(level, head);
        if (!head.fed()) return;
        EntityType<?> type = head.entityType();
        if (type == EntityType.WARDEN && on()) VibrationSystem.Ticker.tick(level, head.getVibrationData(), head.getVibrationUser());
        if ((now + Math.floorMod(pos.hashCode(), PASS)) % PASS != 0) return;
        pass(level, pos, head, type, now);
    }

    /** One pass of a fed head, now, whatever the time (tests step a day's passes this way). */
    public static void runPass(ServerLevel level, JarredHeadBlockEntity head) {
        if (head.fed()) pass(level, head.getBlockPos(), head, head.entityType(), level.getGameTime());
    }

    private static void pass(ServerLevel level, BlockPos pos, JarredHeadBlockEntity head, EntityType<?> type, long now) {
        head.passes++;
        // eating: 40 ticks of it, less with a cow head in reach or a paid Farmer facing the plinth
        if (head.passes % 15 == 1) head.farmer = HeadJobs.staff(level, pos, "farmer");
        float factor = (1 - 0.5f * head.farmer) * (cowNear(level, pos) ? 1 / 1.1f : 1f);
        head.hunger += Math.round(PASS * 100 * factor);
        int eaten = head.hunger / EAT;
        if (eaten > 0) {
            head.hunger -= eaten * EAT;
            head.setBowl(head.bowl - eaten);
        }
        head.setChanged();
        if (head.passes % 30 == 0) level.blockEvent(pos, head.getBlockState().getBlock(), JarredHeadBlockEntity.EV_SPOON, 0);
        if (type == null) return;
        record(level, pos, head, type);
        if (!on()) return;
        String job = StaffTable.desk(type);
        if (job == null) return;
        switch (job) {
            case "chicken" -> chicken(level, head, now);
            case "blaze" -> blaze(level, head);
            case "ghast" -> ghastNight(level, head);
            case "piglin" -> piglinFloor(level, head);
            case "allay" -> {
                if (head.passes % 15 == 0) allay(level, head);
            }
            case "frog" -> frog(level, head);
            case "goat" -> goatScream(level, head);
            case "evoker" -> {
                if (head.passes % 15 == 0) evoker(level, head);
            }
            case "warden" -> wardenPass(level, head);
            case "zombie" -> zombie(level, head);
            default -> {
            }
        }
    }

    /** A fed cow head within 8 (its own bowl counts: a cow makes its own broth last). */
    public static boolean cowNear(Level level, BlockPos pos) {
        return DeskIndex.any(level, "cow", pos.getCenter(), REACH);
    }

    /** What it dislikes and sees through the day, kept for its dawn (only for a species that dislikes something). */
    private static void record(ServerLevel level, BlockPos pos, JarredHeadBlockEntity head, EntityType<?> type) {
        StaffTable.Row row = StaffTable.row(type);
        if (row == null || row.dislikes().isEmpty()) return;
        int seen = 0;
        BlockPos up = pos.above();
        if (level.isRainingAt(up)) seen |= bit("rain");
        if (level.isThundering() && level.canSeeSky(up)) seen |= bit("thunder");
        if (level.isDay() && level.canSeeSky(up)) seen |= bit("open_sky");
        for (Direction d : Direction.values()) {
            if (level.getFluidState(pos.relative(d)).is(net.minecraft.tags.FluidTags.WATER)) {
                seen |= bit("water");
                break;
            }
        }
        if (seen != (head.disliked | seen)) {
            head.disliked |= seen;
            head.setChanged();
        }
    }

    public static int bit(String dislike) {
        return 1 << DISLIKES.indexOf(dislike);
    }

    // --- complaints -------------------------------------------------------------------------------------------------------

    static void complain(ServerLevel level, JarredHeadBlockEntity head) {
        StaffFile f = head.staff();
        if (f == null || !f.staff() || head.entityType() == null) return;
        if (!Config.get(Config.COMPLAINTS, true)) return;
        Component[] c = HeadSpeak.complaint(head.entityType(), level.random);
        head.say(c[0], c[1]);
        SAID.add(head.getBlockPos().toShortString() + "|complaint");
        level.playSound(null, head.getBlockPos(), com.coolfreeze23.headinajar.registry.ModSounds.STAFF_COMPLAIN.get(), SoundSource.BLOCKS, 0.6f, 1f);
        if (!head.toldDisengaged && f.employer != null) {
            ServerPlayer employer = level.getServer().getPlayerList().getPlayer(f.employer);
            if (employer != null) {
                head.toldDisengaged = true;
                head.setChanged();
                com.coolfreeze23.headinajar.clarity.Memos.send(employer, com.coolfreeze23.headinajar.clarity.Memos.Memo.DISENGAGED, Staff.name(f));
            }
        }
    }

    // --- the bell -----------------------------------------------------------------------------------------------------------

    /**
     * Rung with an empty hand (or with an emerald, for a villager): the bell, a nod, and a line. A second ring within a
     * second is ignored. Returns false for a head with no lines (it gives its old answer).
     */
    public static boolean ring(ServerLevel level, JarredHeadBlockEntity head, ServerPlayer player) {
        EntityType<?> type = head.entityType();
        if (type == null) return false;
        int count = HeadSpeak.bells(type, head.record());
        if (count <= 0) return false;
        long now = level.getGameTime();
        if (now - head.rungAt < HeadSpeak.RING_GAP) return true;
        head.rungAt = now;
        BlockPos pos = head.getBlockPos();
        level.blockEvent(pos, head.getBlockState().getBlock(), JarredHeadBlockEntity.EV_BELL, 0);
        level.blockEvent(pos, head.getBlockState().getBlock(), JarredHeadBlockEntity.EV_NOD, 1);
        level.playSound(null, pos, HeadSounds.NOD.get(), SoundSource.BLOCKS, 0.4f, 1f);
        // a big head in a big jar: its first line, ever, is always the same (before any job answers the bell)
        if (!head.snug && HeadSpeak.snug(type, head.record(), head.jar())) {
            head.snug = true;
            head.setChanged();
            HeadSpeak.say(head, player, HeadSpeak.noise(type), Component.translatable(HeadSpeak.SNUG));
            SAID.add(pos.toShortString() + "|snug");
            return true;
        }
        long day = Staff.day(level);
        boolean fed = head.fed() && on();
        String job = StaffTable.desk(type);
        // the jobs that answer a ring
        if (fed && "warden".equals(job)) {
            level.playSound(null, pos, HeadSounds.WARDEN_SHUSH.get(), SoundSource.BLOCKS, 1f, 1f);
            wardenLog(head, player);
            return true;
        }
        if (fed && "goat".equals(job) && goatRam(level, head, player)) return true;
        if (fed && "iron_golem".equals(job) && head.poppyDay != day) {
            head.poppyDay = day;
            head.setChanged();
            com.coolfreeze23.headinajar.form.FormActions.give(player, new ItemStack(Items.POPPY));
            voice(head, player, "iron_golem.poppy");
            return true;
        }
        int n = HeadSpeak.pick(head, count, level.random);
        head.lastBell = n;
        head.setChanged();
        Component[] l = HeadSpeak.bell(head, n, player);
        HeadSpeak.say(head, player, l[0], l[1]);
        SAID.add(pos.toShortString() + "|bell." + n);
        return true;
    }

    /** A fed villager head with an emerald in its ringer's hand reads its best offer aloud (the screen opens as well). */
    public static void bestOffer(ServerLevel level, JarredHeadBlockEntity head, ServerPlayer player) {
        Villager v = head.standIn();
        if (v == null) return;
        MerchantOffer best = null;
        float bestRatio = -1;
        for (MerchantOffer o : v.getOffers()) {
            if (o.isOutOfStock()) continue;
            int emeralds = (o.getCostA().is(Items.EMERALD) ? o.getCostA().getCount() : 0) + (o.getCostB().is(Items.EMERALD) ? o.getCostB().getCount() : 0);
            if (emeralds <= 0) continue;
            float ratio = o.getResult().getCount() / (float) emeralds;
            if (ratio > bestRatio) {
                bestRatio = ratio;
                best = o;
            }
        }
        if (best == null) {
            line(head, player, "villager.no_offer");
            return;
        }
        ItemStack a = best.getCostA(), b = best.getCostB(), r = best.getResult();
        if (b.isEmpty()) line(head, player, "villager.best_offer", a.getCount(), a.getHoverName(), r.getCount(), r.getHoverName());
        else line(head, player, "villager.best_offer2", a.getCount(), a.getHoverName(), b.getCount(), b.getHoverName(), r.getCount(), r.getHoverName());
    }

    // --- items used on a head --------------------------------------------------------------------------------------------

    /** Whether this item is this species' desk item (so it goes to the desk job and not to the wage check). */
    public static boolean deskItem(JarredHeadBlockEntity head, ItemStack stack) {
        String job = StaffTable.desk(head.entityType());
        if (job == null || stack.isEmpty()) return false;
        return switch (job) {
            case "sheep" -> stack.is(Items.SHEARS) || stack.getItem() instanceof DyeItem;
            case "mooshroom" -> stack.is(Items.BOWL) || stack.is(Items.SHEARS)
                    || stack.is(ItemTags.SMALL_FLOWERS) && "brown".equals(head.record().getString("Type"));
            case "piglin" -> stack.is(Items.GOLD_INGOT);
            case "squid" -> stack.is(Items.GLASS_BOTTLE);
            // an allay takes anything but its own wage (that pays it)
            case "allay" -> !StaffTable.wage(head.entityType()).matches(stack);
            default -> false;
        };
    }

    /** A desk item used on the head (see {@link #deskItem}): the job, or its refusal. */
    public static void useItem(ServerLevel level, JarredHeadBlockEntity head, ServerPlayer player, ItemStack stack, InteractionHand hand) {
        Refuser refuser = Refuser.at(head);
        if (!head.fed()) {
            Refusals.deny(player, refuser, "desk.unfed");
            return;
        }
        long day = Staff.day(level);
        BlockPos pos = head.getBlockPos();
        CompoundTag rec = head.record();
        switch (StaffTable.desk(head.entityType())) {
            case "sheep" -> {
                if (stack.getItem() instanceof DyeItem dye) {
                    DyeColor colour = dye.getDyeColor();
                    if (rec.getByte("Color") == colour.getId()) {
                        Refusals.deny(player, refuser, "desk.same_colour", colourName(colour));
                        return;
                    }
                    rec.putByte("Color", (byte) colour.getId());
                    stack.consume(1, player);
                    level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.PLAYERS, 1, 1);
                    head.recordChanged();
                    line(head, player, "sheep.dyed", colourName(colour));
                    return;
                }
                if (rec.getBoolean("Sheared")) {
                    Refusals.deny(player, refuser, "desk.too_soon");
                    return;
                }
                rec.putBoolean("Sheared", true);
                head.recordChanged();
                give(level, head, new ItemStack(wool(DyeColor.byId(rec.getByte("Color")))));
                level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1, 1);
                stack.hurtAndBreak(1, player, slot(hand));
                line(head, player, "sheep.shorn");
            }
            case "mooshroom" -> {
                if (stack.is(Items.SHEARS)) {
                    line(head, player, "mooshroom.shears");
                    return;
                }
                if (stack.is(ItemTags.SMALL_FLOWERS)) {
                    SuspiciousEffectHolder holder = SuspiciousEffectHolder.tryGet(stack.getItem());
                    if (holder == null || rec.contains("stew_effects")) {
                        Refusals.deny(player, refuser, "desk.too_soon");
                        return;
                    }
                    SuspiciousStewEffects.CODEC.encodeStart(NbtOps.INSTANCE, holder.getSuspiciousEffects()).ifSuccess(t -> rec.put("stew_effects", t));
                    stack.consume(1, player);
                    level.playSound(null, pos, SoundEvents.MOOSHROOM_EAT, SoundSource.BLOCKS, 2, 1);
                    level.sendParticles(ParticleTypes.EFFECT, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.05);
                    head.recordChanged();
                    line(head, player, "mooshroom.flower", stack.getHoverName());
                    return;
                }
                if (head.deskDay == day) {
                    Refusals.deny(player, refuser, "desk.too_soon");
                    return;
                }
                head.deskDay = day;
                ItemStack stew;
                if (rec.contains("stew_effects")) {
                    stew = new ItemStack(Items.SUSPICIOUS_STEW);
                    SuspiciousStewEffects.CODEC.parse(NbtOps.INSTANCE, rec.get("stew_effects")).result()
                            .ifPresent(e -> stew.set(DataComponents.SUSPICIOUS_STEW_EFFECTS, e));
                    rec.remove("stew_effects");
                    level.playSound(null, pos, SoundEvents.MOOSHROOM_MILK_SUSPICIOUSLY, SoundSource.PLAYERS, 1, 1);
                    line(head, player, "mooshroom.suspicious");
                } else {
                    stew = new ItemStack(Items.MUSHROOM_STEW);
                    level.playSound(null, pos, SoundEvents.MOOSHROOM_MILK, SoundSource.PLAYERS, 1, 1);
                    line(head, player, "mooshroom.stew");
                }
                player.setItemInHand(hand, net.minecraft.world.item.ItemUtils.createFilledResult(stack, player, stew, false));
                head.recordChanged();
            }
            case "piglin" -> {
                if (head.inspectUntil != 0) {
                    Refusals.deny(player, refuser, "desk.busy");
                    return;
                }
                stack.consume(1, player);
                int ticks = Math.round(INSPECT / rate(head));
                head.inspectUntil = level.getGameTime() + ticks;
                level.blockEvent(pos, head.getBlockState().getBlock(), JarredHeadBlockEntity.EV_ADMIRE, ticks);
                level.playSound(null, pos, SoundEvents.PIGLIN_ADMIRING_ITEM, SoundSource.BLOCKS, 1, 1);
                voice(head, player, "piglin.inspect");
            }
            case "squid" -> {
                if (head.deskDay == day) {
                    Refusals.deny(player, refuser, "desk.too_soon");
                    return;
                }
                head.deskDay = day;
                head.setChanged();
                com.coolfreeze23.headinajar.form.FormActions.give(player, new ItemStack(Items.INK_SAC));
                level.playSound(null, pos, SoundEvents.SQUID_SQUIRT, SoundSource.BLOCKS, 1, 1);
                line(head, player, "squid.ink");
            }
            case "allay" -> {
                ItemStack held = allayItem(level, head);
                if (!held.isEmpty() && ItemStack.isSameItemSameComponents(held, stack)) {
                    Refusals.deny(player, refuser, "desk.allay_has", held.getHoverName());
                    return;
                }
                ItemStack one = stack.copyWithCount(1);
                stack.consume(1, player);
                setAllayItem(level, head, one);
                if (!held.isEmpty()) com.coolfreeze23.headinajar.form.FormActions.give(player, held);
                level.playSound(null, pos, SoundEvents.ALLAY_ITEM_TAKEN, SoundSource.BLOCKS, 1, 1);
                if (held.isEmpty()) line(head, player, "allay.took", one.getHoverName());
                else line(head, player, "allay.swapped", one.getHoverName(), held.getHoverName());
            }
            default -> {
            }
        }
    }

    private static EquipmentSlot slot(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
    }

    public static Component colourName(DyeColor colour) {
        return Component.translatable("color.minecraft." + colour.getName());
    }

    static net.minecraft.world.item.Item wool(DyeColor colour) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(colour.getName() + "_wool"));
    }

    /** A cake used on a head: +40 once a day for a plinth head on staff. */
    public static void cake(ServerLevel level, JarredHeadBlockEntity head, ServerPlayer player, ItemStack stack) {
        StaffFile f = head.staff();
        Refuser refuser = Refuser.at(head);
        if (f == null || !f.staff()) {
            Refusals.deny(player, refuser, "staff.cake_trophy");
            return;
        }
        long day = Staff.day(level);
        if (head.cakeDay == day) {
            Refusals.deny(player, refuser, "staff.no_cake");
            return;
        }
        head.cakeDay = day;
        stack.consume(1, player);
        StaffBlocks.adjust(f, CAKE);
        // 0.7.2: a cake that brings a grievance back to 40 ends it (Let Them Eat Cake)
        com.coolfreeze23.headinajar.staff.Grievances.check(head, f, player);
        head.setStaff(f);
        level.playSound(null, head.getBlockPos(), SoundEvents.GENERIC_EAT, SoundSource.BLOCKS, 1, 1);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, head.getBlockPos().getX() + 0.5, head.getBlockPos().getY() + 1.0, head.getBlockPos().getZ() + 0.5, 6, 0.25, 0.2, 0.25, 0);
        player.displayClientMessage(Component.translatable("message.headinajar.staff.cake", Staff.name(f)), true);
        StaffBlocks.show(head, f);
    }

    // --- lunch by hand -------------------------------------------------------------------------------------------------

    /**
     * A Nutrient Broth Bucket on the plinth: the bowl fills and the bucket comes back empty. A trophy fed for the first
     * time is hired by its feeder (the lunch, not a wage, puts it on staff).
     */
    public static void feed(ServerLevel level, JarredHeadBlockEntity head, ServerPlayer player, ItemStack stack, InteractionHand hand) {
        Refuser refuser = Refuser.at(head);
        // a bucket is the whole 1,000 mB: it goes in once the bowl is down to a quarter, never to top it up
        if (head.bowl > REFILL_AT) {
            Refusals.deny(player, refuser, "desk.bowl_full", daysToRefill(head));
            return;
        }
        head.setBowl(JarredHeadBlockEntity.BOWL);
        if (!player.getAbilities().instabuild) player.setItemInHand(hand, net.minecraft.world.item.ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
        level.playSound(null, head.getBlockPos(), HeadSounds.SLURP.get(), SoundSource.BLOCKS, 1, 1);
        hire(level, head, player);
        com.coolfreeze23.headinajar.Advance.grant(player, "lunch_is_served");
        // a piglin fed where it would turn is told so first
        if (head.entityType() == EntityType.PIGLIN && turnsHere(level, head)) {
            com.coolfreeze23.headinajar.feedback.Warnings.warn(player, 0, "desk.piglin_turns");
        }
    }

    /** The bowl takes a fresh bucket once it is down to this much (a quarter). */
    public static final int REFILL_AT = JarredHeadBlockEntity.BOWL / 4;

    /** Days until the bowl is low enough for the next bucket, one decimal (as the full-bowl refusal says it). */
    public static String daysToRefill(JarredHeadBlockEntity head) {
        double days = Math.max(0, head.bowl - REFILL_AT) * 72.0 / 24000.0;
        return String.format(java.util.Locale.ROOT, "%.1f", Math.max(0.1, days));
    }

    /** Days of lunch left in the bowl, one decimal (as the full-bowl refusal says it). */
    public static String daysLeft(JarredHeadBlockEntity head) {
        double days = head.bowl * 72.0 / 24000.0;
        return String.format(java.util.Locale.ROOT, "%.1f", days);
    }

    /** Puts a trophy on staff because it was fed (by hand, or a pipe on an already-hired head never gets here). */
    static void hire(ServerLevel level, JarredHeadBlockEntity head, ServerPlayer by) {
        StaffFile f = head.staff();
        if (f == null) {
            f = Staff.create(level.getServer(), level, head.entityType());
            f.note("retro", Staff.day(level), "");
        }
        if (f.staff()) {
            head.setStaff(f);
            return;
        }
        f.morale = StaffBlocks.START;
        f.lowDawns = 0;
        Staff.employ(f, by);
        f.note("lunch", Staff.day(level), by.getGameProfile().getName());
        f.lastDawn = Math.max(f.lastDawn, Staff.day(level));
        head.setStaff(f);
        by.displayClientMessage(Component.translatable("message.headinajar.staff.on_lunch", Staff.name(f)), true);
        StaffBlocks.show(head, f);
    }

    // --- the dawn ------------------------------------------------------------------------------------------------------

    /**
     * A plinth head's dawn, after its pay: morale moves for lunch, colleagues, rivals and dislikes, and the daily desk
     * jobs give (a ghast's tear, a sniffer's find, a cat's gift, a chicken's feather, a sheep's fleece grows back).
     */
    public static void dawn(ServerLevel level, BlockPos pos, JarredHeadBlockEntity head, StaffFile f) {
        EntityType<?> type = head.entityType();
        if (type == null) return;
        if (head.lunch) {
            if (head.fed()) {
                if (f.morale < FED_CAP) f.morale = Math.min(FED_CAP, StaffBlocks.moved(f, FED));
            } else if (f.morale > HUNGRY_FLOOR) {
                f.morale = Math.max(HUNGRY_FLOOR, StaffBlocks.moved(f, HUNGRY));
            }
        }
        neighbours(level, pos, head, f, type);
        int seen = head.disliked | dislikedNow(level, pos, type);
        head.disliked = 0;
        for (String d : DISLIKES) {
            if ((seen & bit(d)) != 0 && StaffTable.dislikes(type, d)) {
                StaffBlocks.adjust(f, DISLIKE);
                break;
            }
        }
        // a villager head on staff near a fed iron golem head feels safer
        if (type == EntityType.VILLAGER && DeskIndex.any(level, "iron_golem", pos.getCenter(), GOLEM)) StaffBlocks.adjust(f, GOLEM_CHEER);
        // a sheep's fleece grows back overnight
        if (head.record().getBoolean("Sheared")) {
            head.record().remove("Sheared");
            head.recordChanged();
        }
        if (head.shrieked && f.morale > Band.CONTENT_AT) head.shrieked = false;
        head.setChanged();
        if (!head.fed() || !on()) return;
        String job = StaffTable.desk(type);
        if (job == null) return;
        switch (job) {
            case "ghast" -> {
                boolean two = Band.of(f).low();
                weep(level, head, new ItemStack(Items.GHAST_TEAR, two ? 2 : 1));
                level.playSound(null, pos, HeadSounds.GHAST_WEEP.get(), SoundSource.BLOCKS, 1, 1);
                level.sendParticles(ParticleTypes.FALLING_WATER, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 6, 0.25, 0.2, 0.25, 0);
                if (!head.firstTear) {
                    head.firstTear = true;
                    line(head, null, "ghast.first_tear");
                } else {
                    line(head, null, two ? "ghast.two_tears" : "ghast.tear");
                }
            }
            case "sniffer" -> {
                if (level.getBlockState(pos.below()).is(BlockTags.SNIFFER_DIGGABLE_BLOCK)) {
                    for (ItemStack s : roll(level, BuiltInLootTables.SNIFFER_DIGGING, EntityType.SNIFFER, head, LootContextParamSets.GIFT)) give(level, head, s);
                    level.playSound(null, pos, SoundEvents.SNIFFER_DROP_SEED, SoundSource.BLOCKS, 1, 1);
                    line(head, null, "sniffer.found");
                }
            }
            case "cat" -> {
                if (head.giftDue) {
                    head.giftDue = false;
                    for (ItemStack s : roll(level, BuiltInLootTables.CAT_MORNING_GIFT, EntityType.CAT, head, LootContextParamSets.GIFT)) give(level, head, s);
                    level.playSound(null, pos, SoundEvents.CAT_PURR, SoundSource.BLOCKS, 1, 1);
                    voice(head, null, "cat.gift");
                }
            }
            case "chicken" -> {
                give(level, head, new ItemStack(Items.FEATHER));
                line(head, null, "chicken.feather");
            }
            default -> {
            }
        }
    }

    /** Colleagues within 8 (+2) and rivals within 4 (-5), from the heads on plinths and stations nearby (once a day). */
    private static void neighbours(ServerLevel level, BlockPos pos, JarredHeadBlockEntity head, StaffFile f, EntityType<?> type) {
        String dept = Staff.department(type);
        boolean colleague = false, rival = false;
        // a pair's line is said once a dawn, by its first member (so the two heads do not both say it)
        String said = null, self = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        for (Map.Entry<BlockPos, EntityType<?>> e : HeadDonor.headsNear(level, pos, (int) REACH).entrySet()) {
            if (e.getKey().equals(pos)) continue;
            EntityType<?> other = e.getValue();
            double d = e.getKey().distSqr(pos);
            if (!colleague && d <= REACH * REACH && Staff.department(other).equals(dept) && !Staff.GENERAL.equals(dept)) colleague = true;
            if (!colleague && d <= REACH * REACH) {
                Pair p = pair(COLLEAGUES, type, other);
                if (p != null) {
                    colleague = true;
                    if (said == null && p.a().equals(self)) said = p.line();
                }
            }
            if (!rival && d <= 16) {
                Pair p = pair(RIVALS, type, other);
                if (p != null) {
                    rival = true;
                    if (said == null && p.a().equals(self)) said = p.line();
                }
            }
        }
        if (colleague) StaffBlocks.adjust(f, COLLEAGUE);
        if (rival) StaffBlocks.adjust(f, RIVAL);
        // a pair with a line of its own says it, over the head, at the dawn it counts
        if (said != null) line(head, null, said);
    }

    public static boolean rivals(EntityType<?> a, EntityType<?> b) {
        return pair(RIVALS, a, b) != null;
    }

    /** Whether two species count as colleagues across departments. */
    public static boolean colleagues(EntityType<?> a, EntityType<?> b) {
        return pair(COLLEAGUES, a, b) != null;
    }

    private static Pair pair(List<Pair> pairs, EntityType<?> a, EntityType<?> b) {
        if (a == null || b == null) return null;
        return pair(pairs, BuiltInRegistries.ENTITY_TYPE.getKey(a).toString(), BuiltInRegistries.ENTITY_TYPE.getKey(b).toString());
    }

    private static Pair pair(List<Pair> pairs, String x, String y) {
        for (Pair p : pairs) if (p.of(x, y)) return p;
        return null;
    }

    /** The rival pair of two species by id (one may be from a mod that is not installed), or null. */
    public static Pair rivalPair(String a, String b) {
        return pair(RIVALS, a, b);
    }

    /** The cross-department colleague pair of two species by id, or null. */
    public static Pair colleaguePair(String a, String b) {
        return pair(COLLEAGUES, a, b);
    }

    /** The dislikes that can be seen at dawn itself: rain or thunder now, the biome, the dimension, warped fungus. */
    private static int dislikedNow(ServerLevel level, BlockPos pos, EntityType<?> type) {
        StaffTable.Row row = StaffTable.row(type);
        if (row == null || row.dislikes().isEmpty()) return 0;
        int seen = 0;
        if (level.isRainingAt(pos.above())) seen |= bit("rain");
        if (level.isThundering() && level.canSeeSky(pos.above())) seen |= bit("thunder");
        float t = level.getBiome(pos).value().getBaseTemperature();
        if (t > 1.0f || level.dimensionType().ultraWarm()) seen |= bit("hot");
        if (t > 0.8f) seen |= bit("warm");
        if (level.dimension() != Level.NETHER) seen |= bit("not_nether");
        if (row.dislikes().contains("warped_fungus")) {
            for (BlockPos p : BlockPos.betweenClosed(pos.offset(-8, -8, -8), pos.offset(8, 8, 8))) {
                if (level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.WARPED_FUNGUS)) {
                    seen |= bit("warped_fungus");
                    break;
                }
            }
        }
        return seen;
    }

    /** One roll of a vanilla gift table, with a stand-in creature of the head's kind as the table's creature. */
    static List<ItemStack> roll(ServerLevel level, net.minecraft.resources.ResourceKey<LootTable> table, EntityType<?> kind, JarredHeadBlockEntity head,
                                net.minecraft.world.level.storage.loot.parameters.LootContextParamSet set) {
        Entity standIn = kind.create(level);
        if (standIn == null) return List.of();
        Vec3 at = head.getBlockPos().getCenter();
        standIn.setPos(at);
        LootParams.Builder b = new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY, standIn);
        if (set == LootContextParamSets.GIFT) b.withParameter(LootContextParams.ORIGIN, at);
        return level.getServer().reloadableRegistries().getLootTable(table).getRandomItems(b.create(set));
    }

    // --- the passes, job by job ----------------------------------------------------------------------------------------

    private static void chicken(ServerLevel level, JarredHeadBlockEntity head, long now) {
        long d = level.getDayTime();
        if (d / 1000 == (d - PASS) / 1000) return;
        level.playSound(null, head.getBlockPos(), HeadSounds.CHICKEN_HOUR.get(), SoundSource.BLOCKS, 1, 1);
        // day time 0 is six in the morning
        int hour = (int) ((d / 1000 + 6) % 12);
        line(head, null, "chicken.hour", hour == 0 ? 12 : hour);
    }

    private static void blaze(ServerLevel level, JarredHeadBlockEntity head) {
        Map<Long, Long> breathed = BREATHED.computeIfAbsent(level, l -> new HashMap<>());
        long now = level.getGameTime();
        int add = Math.round(BLAZE * rate(head));
        boolean any = false;
        for (Direction d : Direction.values()) {
            BlockPos at = head.getBlockPos().relative(d);
            if (!(level.getBlockEntity(at) instanceof AbstractFurnaceBlockEntity furnace)) continue;
            var state = furnace.getBlockState();
            if (!state.hasProperty(AbstractFurnaceBlock.LIT) || !state.getValue(AbstractFurnaceBlock.LIT) || furnace.cookingProgress <= 0) continue;
            // one blaze per furnace: another head breathed on it this pass
            Long last = breathed.get(at.asLong());
            if (last != null && now - last < PASS) continue;
            breathed.put(at.asLong(), now);
            furnace.cookingProgress = Math.min(furnace.cookingTotalTime - 1, furnace.cookingProgress + add);
            furnace.setChanged();
            any = true;
            Direction mouth = state.hasProperty(AbstractFurnaceBlock.FACING) ? state.getValue(AbstractFurnaceBlock.FACING) : Direction.UP;
            Vec3 p = at.getCenter().add(mouth.getStepX() * 0.55, -0.2, mouth.getStepZ() * 0.55);
            level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 3, 0.1, 0.05, 0.1, 0.01);
        }
        if (breathed.size() > 512) breathed.values().removeIf(t -> now - t > PASS * 4);
        if (any && level.random.nextInt(8) == 0) level.playSound(null, head.getBlockPos(), SoundEvents.BLAZE_BURN, SoundSource.BLOCKS, 0.5f, 1);
    }

    /** Whether a blaze head breathes on this furnace (tests). */
    public static boolean breathedOn(Level level, BlockPos furnace) {
        Map<Long, Long> b = BREATHED.get(level);
        return b != null && b.containsKey(furnace.asLong());
    }

    private static void ghastNight(ServerLevel level, JarredHeadBlockEntity head) {
        long t = level.getDayTime() % 24000;
        long day = Staff.day(level);
        if (t < 13000 || t > 23000 || head.wailDay == day) return;
        // once, at a random pass between 13,000 and 23,000
        int left = (int) ((23000 - t) / PASS) + 1;
        if (level.random.nextInt(left) != 0) return;
        head.wailDay = day;
        level.playSound(null, head.getBlockPos(), SoundEvents.GHAST_SCREAM, SoundSource.HOSTILE, 1.5f, 0.8f);
        line(head, null, "ghast.wail");
    }

    /** A piglin head fed where it would turn: outside a piglin-safe dimension, off a Nether floor. */
    public static boolean turnsHere(Level level, JarredHeadBlockEntity head) {
        return !level.dimensionType().piglinSafe() && !level.getBlockState(head.getBlockPos().below()).is(NETHER_FLOOR);
    }

    private static void piglinFloor(ServerLevel level, JarredHeadBlockEntity head) {
        if (!turnsHere(level, head)) {
            head.offNether = 0;
            return;
        }
        head.offNether += PASS;
        if (head.offNether < TURNS) return;
        // it turns, for good: its file and its desk stay
        head.inspectUntil = 0;
        CompoundTag rec = new CompoundTag();
        head.becomes(EntityType.ZOMBIFIED_PIGLIN, rec);
        level.playSound(null, head.getBlockPos(), SoundEvents.PIGLIN_CONVERTED_TO_ZOMBIFIED, SoundSource.BLOCKS, 1, 1);
        level.sendParticles(ParticleTypes.SMOKE, head.getBlockPos().getX() + 0.5, head.getBlockPos().getY() + 1.0, head.getBlockPos().getZ() + 0.5, 12, 0.25, 0.3, 0.25, 0.02);
        line(head, null, "piglin.turned");
    }

    private static void barter(ServerLevel level, JarredHeadBlockEntity head) {
        head.inspectUntil = 0;
        if (head.entityType() != EntityType.PIGLIN) return;
        for (ItemStack s : roll(level, BuiltInLootTables.PIGLIN_BARTERING, EntityType.PIGLIN, head, LootContextParamSets.PIGLIN_BARTER)) give(level, head, s);
        level.playSound(null, head.getBlockPos(), SoundEvents.PIGLIN_CELEBRATE, SoundSource.BLOCKS, 1, 1);
    }

    /** The allay's held item (its record's main hand), or empty. */
    public static ItemStack allayItem(Level level, JarredHeadBlockEntity head) {
        ListTag hands = head.record().getList("HandItems", net.minecraft.nbt.Tag.TAG_COMPOUND);
        if (hands.isEmpty()) return ItemStack.EMPTY;
        return ItemStack.parseOptional(level.registryAccess(), hands.getCompound(0));
    }

    static void setAllayItem(Level level, JarredHeadBlockEntity head, ItemStack stack) {
        ListTag hands = head.record().getList("HandItems", net.minecraft.nbt.Tag.TAG_COMPOUND);
        ListTag out = new ListTag();
        out.add(stack.isEmpty() ? new CompoundTag() : stack.save(level.registryAccess()));
        out.add(hands.size() > 1 ? hands.getCompound(1) : new CompoundTag());
        head.record().put("HandItems", out);
        head.recordChanged();
    }

    private static void allay(ServerLevel level, JarredHeadBlockEntity head) {
        ItemStack kind = allayItem(level, head);
        if (kind.isEmpty()) return;
        BlockPos pos = head.getBlockPos();
        List<ItemEntity> found = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(ALLAY),
                e -> e.isAlive() && ItemStack.isSameItemSameComponents(e.getItem(), kind) && e.distanceToSqr(pos.getCenter()) <= ALLAY * ALLAY);
        if (found.isEmpty()) return;
        ItemEntity item = found.get(0);
        for (Direction d : Direction.values()) {
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos.relative(d), d.getOpposite());
            if (handler == null) continue;
            ItemStack left = ItemHandlerHelper.insertItem(handler, item.getItem().copy(), false);
            if (left.getCount() == item.getItem().getCount()) continue;
            Vec3 from = item.position();
            if (left.isEmpty()) item.discard();
            else item.setItem(left);
            // a short trail from where it lay toward the plinth
            Vec3 to = pos.getCenter();
            for (int i = 0; i <= 6; i++) {
                Vec3 p = from.lerp(to, i / 6.0);
                level.sendParticles(ParticleTypes.END_ROD, p.x, p.y + 0.3, p.z, 1, 0, 0, 0, 0);
            }
            level.playSound(null, pos, SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.BLOCKS, 1, 1);
            line(head, null, "allay.called", kind.getHoverName());
            return;
        }
    }

    private static void frog(ServerLevel level, JarredHeadBlockEntity head) {
        if (!level.dimensionType().hasSkyLight() || level.dimensionType().hasCeiling()) return;
        if (!(level.getLevelData() instanceof ServerLevelData data) || data.getClearWeatherTime() > 0) return;
        String due = null;
        long at = 0;
        if (!level.isRaining() && data.getRainTime() <= 1200) {
            due = "frog.rain";
            at = level.getGameTime() + data.getRainTime();
        } else if (level.isRaining() && !level.isThundering() && data.getThunderTime() <= 1200) {
            due = "frog.thunder";
            at = level.getGameTime() + data.getThunderTime();
        }
        if (due == null || Math.abs(at - head.forecastFor) < PASS * 2) return;
        head.forecastFor = at;
        level.playSound(null, head.getBlockPos(), HeadSounds.FROG_FORECAST.get(), SoundSource.BLOCKS, 1, 1);
        line(head, null, due);
    }

    /** On a ring: a goat with a horn left rams its glass and a horn drops (twice, ever). */
    private static boolean goatRam(ServerLevel level, JarredHeadBlockEntity head, ServerPlayer player) {
        CompoundTag rec = head.record();
        boolean left = !rec.contains("HasLeftHorn") || rec.getBoolean("HasLeftHorn");
        boolean right = !rec.contains("HasRightHorn") || rec.getBoolean("HasRightHorn");
        if (!left && !right) return false;
        var goat = EntityType.GOAT.create(level);
        if (goat == null) return false;
        goat.load(rec);
        ItemStack horn = goat.createHorn();
        rec.putBoolean(left ? "HasLeftHorn" : "HasRightHorn", false);
        if (!rec.contains("HasRightHorn")) rec.putBoolean("HasRightHorn", right);
        head.recordChanged();
        BlockPos pos = head.getBlockPos();
        level.blockEvent(pos, head.getBlockState().getBlock(), JarredHeadBlockEntity.EV_RAM, 0);
        level.playSound(null, pos, SoundEvents.GOAT_RAM_IMPACT, SoundSource.BLOCKS, 1, 1);
        level.playSound(null, pos, SoundEvents.GOAT_HORN_BREAK, SoundSource.BLOCKS, 1, 1);
        give(level, head, horn);
        voice(head, player, "goat.horn");
        return true;
    }

    private static void goatScream(ServerLevel level, JarredHeadBlockEntity head) {
        if (!head.record().getBoolean("IsScreamingGoat")) return;
        long t = level.getDayTime() % 24000;
        if (t < 21000 || t - PASS >= 21000) return;
        level.playSound(null, head.getBlockPos(), SoundEvents.GOAT_SCREAMING_AMBIENT, SoundSource.BLOCKS, 1.5f, 1);
        line(head, null, "goat.scream");
    }

    private static void evoker(ServerLevel level, JarredHeadBlockEntity head) {
        BlockPos pos = head.getBlockPos();
        Vec3 c = pos.getCenter();
        boolean done = false;
        for (Sheep sheep : level.getEntitiesOfClass(Sheep.class, new AABB(pos).inflate(REACH), s -> s.getColor() == DyeColor.BLUE && s.distanceToSqr(c) <= REACH * REACH)) {
            sheep.setColor(DyeColor.RED);
            done = true;
            break;
        }
        if (!done) {
            for (BlockEntity be : HeadDonor.blockEntitiesNear(level, pos, (int) REACH)) {
                if (be instanceof JarredHeadBlockEntity other && other.entityType() == EntityType.SHEEP && be.getBlockPos().distSqr(pos) <= REACH * REACH
                        && other.record().getByte("Color") == DyeColor.BLUE.getId()) {
                    other.record().putByte("Color", (byte) DyeColor.RED.getId());
                    other.recordChanged();
                    done = true;
                    break;
                }
            }
        }
        if (!done) return;
        level.playSound(null, pos, SoundEvents.EVOKER_PREPARE_WOLOLO, SoundSource.BLOCKS, 1, 1);
        level.sendParticles(ParticleTypes.WITCH, c.x, c.y + 0.6, c.z, 8, 0.3, 0.3, 0.3, 0);
        line(head, null, "evoker.sheep");
    }

    private static void zombie(ServerLevel level, JarredHeadBlockEntity head) {
        BlockPos pos = head.getBlockPos();
        if (head.doors == null || head.passes % 15 == 0) {
            List<BlockPos> doors = new ArrayList<>();
            for (BlockPos p : BlockPos.betweenClosed(pos.offset(-4, -2, -4), pos.offset(4, 2, 4))) {
                var s = level.getBlockState(p);
                if (s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) doors.add(p.immutable());
            }
            head.doors = doors;
        }
        long now = level.getGameTime();
        if (!head.doors.isEmpty()) {
            for (ServerPlayer p : level.players()) {
                if (p.isSpectator() || p.distanceToSqr(pos.getCenter()) > 64) continue;
                Long last = head.knocked.get(p.getUUID());
                if (last != null && now - last < 600) continue;
                for (BlockPos door : head.doors) {
                    if (p.distanceToSqr(door.getCenter()) > 16) continue;
                    boolean iron = level.getBlockState(door).is(net.minecraft.world.level.block.Blocks.IRON_DOOR);
                    level.levelEvent(iron ? 1020 : 1019, door, 0);
                    head.knocked.put(p.getUUID(), now);
                    line(head, null, "zombie.knock");
                    break;
                }
            }
        }
        // under open sky in daylight it smokes (no harm done) and hates it
        if (level.isDay() && level.canSeeSky(pos.above())) {
            level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 3, 0.2, 0.15, 0.2, 0.01);
        }
    }

    // --- the warden ----------------------------------------------------------------------------------------------------

    private static void wardenPass(ServerLevel level, JarredHeadBlockEntity head) {
        StaffFile f = head.staff();
        if (f == null || head.shrieked || Band.of(f) != Band.GRIEVANCE) return;
        head.shrieked = true;
        head.setChanged();
        BlockPos pos = head.getBlockPos();
        level.playSound(null, pos, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.BLOCKS, 2, 1);
        if (Config.get(Config.WARDEN_DARKNESS, true)) {
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(pos.getCenter()) <= WARDEN * WARDEN && !p.isSpectator()) p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DARKNESS, 0, false, false));
            }
        }
        voice(head, null, "warden.shriek");
    }

    /** The kind of a vibration, as the Noise Log counts it: vanilla's frequency (1 a step, 15 a departure). */
    static String kind(Holder<GameEvent> e) {
        return Integer.toString(VibrationSystem.getGameEventFrequency(e));
    }

    /** Rung: the warden reads its log ("Logged: 3 steps, 1 opening."), or says nothing was logged. */
    static void wardenLog(JarredHeadBlockEntity head, ServerPlayer player) {
        if (head.noiseLog.isEmpty()) {
            line(head, player, "warden.log_empty");
            return;
        }
        MutableComponent list = Component.empty();
        boolean first = true;
        for (int k = 1; k <= 15; k++) {
            int n = 0;
            for (String s : head.noiseLog) if (s.equals(Integer.toString(k))) n++;
            if (n == 0) continue;
            if (!first) list.append(Component.translatable("desk.headinajar.warden.log_join"));
            first = false;
            list.append(n == 1 ? Component.translatable("desk.headinajar.warden.part." + k + ".one")
                    : Component.translatable("desk.headinajar.warden.part." + k + ".many", n));
        }
        line(head, player, "warden.log", list);
    }

    /** The warden head's ears: a sculk sensor's, at 16 blocks, only while it is fed. */
    public static final class WardenEars implements VibrationSystem.User {
        private final JarredHeadBlockEntity head;
        private PositionSource source;

        public WardenEars(JarredHeadBlockEntity head) {
            this.head = head;
        }

        @Override
        public int getListenerRadius() {
            return head.entityType() == EntityType.WARDEN && head.fed() && on() ? (int) WARDEN : 0;
        }

        @Override
        public PositionSource getPositionSource() {
            if (source == null) source = new BlockPositionSource(head.getBlockPos());
            return source;
        }

        @Override
        public boolean canReceiveVibration(ServerLevel level, BlockPos pos, Holder<GameEvent> event, GameEvent.Context context) {
            return getListenerRadius() > 0 && !head.isRemoved();
        }

        @Override
        public void onReceiveVibration(ServerLevel level, BlockPos pos, Holder<GameEvent> event, Entity source, Entity projectileOwner, float distance) {
            head.noiseLog.add(kind(event));
            while (head.noiseLog.size() > 5) head.noiseLog.remove(0);
            head.setHeard(pos);
            long now = level.getGameTime();
            if (now - head.noiseMinute >= 1200) {
                head.noiseMinute = now;
                head.noiseCount = 0;
            }
            if (++head.noiseCount == TOO_LOUD + 1) {
                StaffFile f = head.staff();
                if (f != null && f.staff()) {
                    StaffBlocks.adjust(f, -5);
                    head.setStaff(f);
                    StaffBlocks.show(head, f);
                }
                voice(head, null, "warden.noise");
            }
            head.setChanged();
        }

        @Override
        public void onDataChanged() {
            head.setChanged();
        }
    }
}
