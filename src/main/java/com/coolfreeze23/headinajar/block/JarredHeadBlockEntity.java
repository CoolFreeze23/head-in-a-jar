package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;

import java.util.ArrayList;
import java.util.List;

/**
 * A placed jarred head. A villager's head keeps its whole villager record, so it can trade: using the jar opens the
 * normal trading screen with a stand-in villager that is saved back when trading ends. It restocks twice a day.
 * <p>
 * The plinth has a bowl: a head fed Nutrient Broth eats 1 mB every 72 ticks and does its desk job ({@link DeskJobs});
 * its bell answers with a line over the jar ({@link HeadSpeak}); it gives one blood sample a day ({@link HeadDonor}).
 * Everything its desk keeps is in its {@code Desk} tag, which goes into the item when it is broken.
 */
public class JarredHeadBlockEntity extends BlockEntity implements com.coolfreeze23.headinajar.feedback.Refuser, com.coolfreeze23.headinajar.staff.Badged,
        GameEventListener.Provider<VibrationSystem.Listener>, VibrationSystem {
    /**
     * Block events: the head shakes no (a refusal), the bell, a nod (a trade done), a wince (blood drawn), the spoon
     * taps the bowl, a goat's ram, a piglin admiring gold (param: the inspection's ticks). A 0.6.0 game ignores 4-7.
     */
    public static final int EV_REFUSE = 1, EV_BELL = 2, EV_NOD = 3, EV_WINCE = 4, EV_SPOON = 5, EV_RAM = 6, EV_ADMIRE = 7;
    public static final int EVENTS = 8;
    /** The head looks at the nearest player this close (client only). */
    public static final double LOOK = 6;
    /** The bowl holds a bucket. */
    public static final int BOWL = 1000;
    /** How long a speech line stays over the jar (ticks). */
    public static final int SAY_TICKS = 100;
    /** How high above the plinth's floor its line is drawn (blocks). */
    public static final double SAY_HEIGHT = 1.45;
    private String type = "";
    /** While trading: the stand-in's offers' total uses, to see a trade go through. */
    private int uses = -1;
    /** Client: the head's turn and nod toward the nearest player (degrees), and last tick's; when each moment was seen. */
    public float yaw, yawPrev, pitch, pitchPrev;
    private final long[] seen = new long[EVENTS];
    /** Client: each block event's last param (the piglin's inspection length). */
    private final int[] param = new int[EVENTS];
    private CompoundTag entity = new CompoundTag();
    private long lastRestock;
    private Villager trader;
    /** {@link #type} parsed once (it is drawn every frame). */
    private String parsed;
    private EntityType<?> parsedType;
    /** The jar it went into (an item id), or null when none is recorded (a head cut before 0.6.0: a Life Support Jar). */
    private String jar;
    /** Its Personnel File (server; null until it is registered). */
    private com.coolfreeze23.headinajar.staff.StaffFile staff;
    /** Whether this load has been checked for a file yet (not saved: the first tick after every load looks). */
    private boolean filed;
    /** Client and server: the badge's colour (0xRRGGBB, -1 none) and the morale band, as the update tag carries them. */
    private int badge = -1;
    private com.coolfreeze23.headinajar.staff.Band band = com.coolfreeze23.headinajar.staff.Band.NONE;

    // --- the desk (server; saved in "Desk", and kept in the item) ---------------------------------------------------
    /** The bowl's broth (mB) and the eating carry (ticks toward the next mB). */
    public int bowl, hunger;
    /** It has been on lunch (fed at least once): an empty bowl now means hungry. */
    public boolean lunch;
    /** The bell line said last (1-based; 0 none). */
    public int lastBell;
    /** Days: the last blood drawn, the cake, the daily desk job given, the golem's poppy (-1 never). */
    public long libraryDay = -1, cakeDay = -1, deskDay = -1, poppyDay = -1;
    /** Distinct days drawn by hand (Blood Bank). */
    public int libraryDays;
    /** The ghast has cried its first tear. */
    public boolean firstTear;
    /** What it saw since its last dawn that it dislikes (bits of {@link DeskJobs#DISLIKES}). */
    public int disliked;
    /** A cat head's employer slept: a gift at the next dawn. */
    public boolean giftDue;
    /** The warden's last five vibrations (short kinds), newest last; it shrieked at this Grievance. */
    public final List<String> noiseLog = new ArrayList<>();
    public boolean shrieked;
    /** The Disengaged memo went to this head's employer (once per head). */
    public boolean toldDisengaged;
    /** A big head in a big jar has said its first line, "It is a snug fit." (once per head). */
    public boolean snug;
    // not saved
    /** When it was last rung (game time). */
    public long rungAt = Long.MIN_VALUE / 2;
    /** A piglin's inspection ends at this game time (0 none); the ticks it has stood off a Nether floor. */
    public long inspectUntil;
    public int offNether;
    /** The warden's vibrations in the current minute, and when the minute began. */
    public int noiseCount;
    public long noiseMinute;
    /** Passes run since load (jobs that run every n-th pass). */
    public int passes;
    /** A paid Farmer facing the plinth (rechecked every 15th pass); the night the ghast last wailed; the frog's forecast. */
    public float farmer;
    public long wailDay = -1, forecastFor;
    /** The zombie's doors within 4 (rescanned every 30 s), and when each player last made it knock. */
    public List<BlockPos> doors;
    public final java.util.Map<java.util.UUID, Long> knocked = new java.util.HashMap<>();

    // --- the warden's ears ------------------------------------------------------------------------------------------
    private VibrationSystem.Data vibrationData = new VibrationSystem.Data();
    private final VibrationSystem.Listener vibrationListener = new VibrationSystem.Listener(this);
    private final VibrationSystem.User vibrationUser = new DeskJobs.WardenEars(this);
    /** Where the warden last heard something (client and server; -1 none). */
    private long heard = -1;

    // --- what it says (server and client) ---------------------------------------------------------------------------
    private Component sayFirst, saySecond;
    private long sayT = Long.MIN_VALUE / 2;
    /** Client: the bowl's step (0-4) and the look the renderer dresses the head in. */
    private int bowlStep;
    private CompoundTag look = new CompoundTag();

    public JarredHeadBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.JARRED_HEAD_BE.get(), pos, state);
    }

    /** From the item being placed: the head, and when its trades last restocked (so breaking and re-placing doesn't restock). */
    public void load(CompoundTag tag) {
        type = tag.getString("Type");
        entity = tag.getCompound("Entity");
        if (tag.contains("LastRestock")) lastRestock = tag.getLong("LastRestock");
        jar = tag.contains("Jar") ? tag.getString("Jar") : null;
        staff = com.coolfreeze23.headinajar.staff.Staff.fromHeadTag(tag);
        // the file goes on the block, not also inside the stored mob
        com.coolfreeze23.headinajar.staff.Staff.stripEntity(entity);
        readDesk(tag.getCompound("Desk"));
        setChanged();
        if (level instanceof ServerLevel server) DeskIndex.update(server, this);
    }

    /** The jar this head is in (an item id; a Life Support Jar when none is recorded). */
    public String jar() {
        return jar == null ? HeadInfo.LIFE_SUPPORT : jar;
    }

    /** Whether it is drawn in the Compact Jar's shape. */
    public boolean compact() {
        return HeadInfo.COMPACT.equals(jar());
    }

    /** The Personnel File (server), or null. */
    public com.coolfreeze23.headinajar.staff.StaffFile staff() {
        return staff;
    }

    public void setStaff(com.coolfreeze23.headinajar.staff.StaffFile file) {
        staff = file;
        setChanged();
    }

    /** The badge's colour as clients see it (0xRRGGBB), or -1 for no badge (a trophy, or a head nobody employs). */
    @Override
    public int badge() {
        return badge;
    }

    /** The morale band as clients see it. */
    public com.coolfreeze23.headinajar.staff.Band band() {
        return band;
    }

    @Override
    public void setBadge(int colour) {
        show(colour, band);
    }

    /** Server: sets what clients are told (the badge colour and band), and tells them if it changed. */
    public void show(int badge, com.coolfreeze23.headinajar.staff.Band band) {
        if (this.badge == badge && this.band == band) return;
        this.badge = badge;
        this.band = band;
        sync();
    }

    /** Server: tells every client watching this head what it now looks like. */
    public void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /** Server: true once, on the first tick after this head was loaded or placed (the retroactive register's look). */
    public boolean firstLook() {
        if (filed) return false;
        filed = true;
        return true;
    }

    public EntityType<?> entityType() {
        if (!type.equals(parsed)) {
            parsed = type;
            ResourceLocation id = ResourceLocation.tryParse(type);
            parsedType = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        }
        return parsedType;
    }

    /** Server: the head becomes another species (a piglin turning), keeping its file and its desk. */
    public void becomes(EntityType<?> to, CompoundTag record) {
        if (level instanceof ServerLevel server) DeskIndex.remove(server, this);
        type = BuiltInRegistries.ENTITY_TYPE.getKey(to).toString();
        entity = record;
        if (staff != null) staff.species = type;
        setChanged();
        if (level instanceof ServerLevel server) DeskIndex.update(server, this);
        sync();
    }

    /** The stored mob's record (server: the whole mob; client: what draws it). Changes are the caller's to save. */
    public CompoundTag record() {
        return entity;
    }

    /** The record changed (a dye, a shorn fleece, a horn): saved, and the look sent to clients. */
    public void recordChanged() {
        setChanged();
        sync();
    }

    public ItemStack toItem() {
        ItemStack stack = new ItemStack(ModRegistry.JARRED_HEAD.get());
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", type);
        tag.put("Entity", entity.copy());
        if (lastRestock != 0) tag.putLong("LastRestock", lastRestock);
        if (jar != null) tag.putString("Jar", jar);
        if (staff != null) tag.put("Staff", staff.save());
        // the bowl's broth, the day it last gave blood and its other days go with it: breaking a head is no fresh start
        CompoundTag desk = writeDesk();
        if (!desk.isEmpty()) tag.put("Desk", desk);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
        return stack;
    }

    // --- the desk's saved state ---------------------------------------------------------------------------------------

    private CompoundTag writeDesk() {
        CompoundTag d = new CompoundTag();
        if (bowl > 0) d.putInt("Bowl", bowl);
        if (hunger > 0) d.putInt("Hunger", hunger);
        if (lunch) d.putBoolean("Lunch", true);
        if (lastBell > 0) d.putByte("LastBell", (byte) lastBell);
        if (libraryDay >= 0) d.putLong("LibraryDay", libraryDay);
        if (libraryDays > 0) d.putInt("LibraryDays", libraryDays);
        if (cakeDay >= 0) d.putLong("CakeDay", cakeDay);
        if (deskDay >= 0) d.putLong("DeskDay", deskDay);
        if (poppyDay >= 0) d.putLong("PoppyDay", poppyDay);
        if (firstTear) d.putBoolean("FirstTear", true);
        if (disliked != 0) d.putInt("Disliked", disliked);
        if (giftDue) d.putBoolean("GiftDue", true);
        if (shrieked) d.putBoolean("Shrieked", true);
        if (toldDisengaged) d.putBoolean("ToldDisengaged", true);
        if (snug) d.putBoolean("Snug", true);
        if (!noiseLog.isEmpty()) {
            ListTag log = new ListTag();
            for (String s : noiseLog) log.add(StringTag.valueOf(s));
            d.put("NoiseLog", log);
        }
        return d;
    }

    private void readDesk(CompoundTag d) {
        bowl = Math.max(0, Math.min(BOWL, d.getInt("Bowl")));
        hunger = d.getInt("Hunger");
        lunch = d.getBoolean("Lunch");
        lastBell = d.getByte("LastBell");
        libraryDay = d.contains("LibraryDay") ? d.getLong("LibraryDay") : -1;
        libraryDays = d.getInt("LibraryDays");
        cakeDay = d.contains("CakeDay") ? d.getLong("CakeDay") : -1;
        deskDay = d.contains("DeskDay") ? d.getLong("DeskDay") : -1;
        poppyDay = d.contains("PoppyDay") ? d.getLong("PoppyDay") : -1;
        firstTear = d.getBoolean("FirstTear");
        disliked = d.getInt("Disliked");
        giftDue = d.getBoolean("GiftDue");
        shrieked = d.getBoolean("Shrieked");
        toldDisengaged = d.getBoolean("ToldDisengaged");
        snug = d.getBoolean("Snug");
        noiseLog.clear();
        for (Tag t : d.getList("NoiseLog", Tag.TAG_STRING)) noiseLog.add(t.getAsString());
    }

    // --- lunch ---------------------------------------------------------------------------------------------------------

    /** Whether the bowl has broth in it (a fed head works). */
    public boolean fed() {
        return bowl > 0;
    }

    /** The bowl's level as clients draw it: 0 empty, then quarter steps (1 a little, 4 full). */
    public static int step(int bowl) {
        return bowl <= 0 ? 0 : Math.min(4, 1 + (bowl - 1) * 4 / BOWL);
    }

    /** Server: sets the bowl, keeps the index, and tells clients when its drawn level changes. */
    public void setBowl(int mb) {
        int before = step(bowl);
        boolean was = bowl > 0;
        bowl = Math.max(0, Math.min(BOWL, mb));
        if (bowl > 0) lunch = true;
        setChanged();
        if (level instanceof ServerLevel server && was != (bowl > 0)) DeskIndex.update(server, this);
        if (step(bowl) != before) sync();
    }

    /** The bowl as a pipe sees it: fill only, Nutrient Broth only, and only for a head on staff (a pipe hires no trophy). */
    public net.neoforged.neoforge.fluids.capability.IFluidHandler pipes() {
        return pipes;
    }

    private final net.neoforged.neoforge.fluids.capability.IFluidHandler pipes = new net.neoforged.neoforge.fluids.capability.IFluidHandler() {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public net.neoforged.neoforge.fluids.FluidStack getFluidInTank(int tank) {
            return bowl <= 0 ? net.neoforged.neoforge.fluids.FluidStack.EMPTY
                    : new net.neoforged.neoforge.fluids.FluidStack(com.coolfreeze23.headinajar.registry.ModFluids.BROTH.source.get(), bowl);
        }

        @Override
        public int getTankCapacity(int tank) {
            return BOWL;
        }

        @Override
        public boolean isFluidValid(int tank, net.neoforged.neoforge.fluids.FluidStack stack) {
            return stack.getFluid().isSame(com.coolfreeze23.headinajar.registry.ModFluids.BROTH.source.get());
        }

        @Override
        public int fill(net.neoforged.neoforge.fluids.FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !isFluidValid(0, resource) || staff == null || !staff.staff()) return 0;
            int took = Math.min(BOWL - bowl, resource.getAmount());
            if (took > 0 && action.execute()) setBowl(bowl + took);
            return took;
        }

        @Override
        public net.neoforged.neoforge.fluids.FluidStack drain(net.neoforged.neoforge.fluids.FluidStack resource, FluidAction action) {
            return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
        }

        @Override
        public net.neoforged.neoforge.fluids.FluidStack drain(int maxDrain, FluidAction action) {
            return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
        }
    };

    /** Client: the bowl's level (0-4). */
    public int bowlStep() {
        return bowlStep;
    }

    /** Client: what the renderer dresses the head in (sheep colour and fleece, mooshroom type, goat horns, the allay's item). */
    public CompoundTag look() {
        return look;
    }

    /** Client and server: where the warden last heard something, or null. */
    public BlockPos heard() {
        return heard == -1 ? null : BlockPos.of(heard);
    }

    public void setHeard(BlockPos pos) {
        heard = pos == null ? -1 : pos.asLong();
        sync();
    }

    // --- speech ----------------------------------------------------------------------------------------------------------

    /**
     * Server: says a line over the jar for {@link #SAY_TICKS} ticks, to every client watching (each in its own
     * language): {@code first} in white, {@code second} (HR's translation, or null) in grey under it.
     */
    public void say(Component first, Component second) {
        if (level == null || level.isClientSide) return;
        sayFirst = first;
        saySecond = second;
        sayT = level.getGameTime();
        sync();
    }

    /** Server: says a line now, to every client watching, though the block is about to go (a goodbye). */
    public void sayNow(Component first, Component second) {
        if (!(level instanceof ServerLevel server)) return;
        sayFirst = first;
        saySecond = second;
        sayT = level.getGameTime();
        var packet = ClientboundBlockEntityDataPacket.create(this);
        for (ServerPlayer p : server.getChunkSource().chunkMap.getPlayers(new net.minecraft.world.level.ChunkPos(worldPosition), false)) p.connection.send(packet);
    }

    /** The line said last (server: what it said; client: what it heard), or null, and when. */
    public Component said() {
        return sayFirst;
    }

    public Component saidSecond() {
        return saySecond;
    }

    public long saidAt() {
        return sayT;
    }

    // --- trading ---------------------------------------------------------------------------------------------------------

    /** Someone other than {@code player} is trading with this head now. */
    public boolean busy(ServerPlayer player) {
        return trader != null && trader.getTradingPlayer() != null && trader.getTradingPlayer() != player;
    }

    /**
     * Ends any trade now and keeps what it changed (uses, experience) in the head's record. The block is about to go
     * (broken, blown up, carried off), so the trade screen is closed rather than left working on a stand-in.
     */
    public void settle() {
        if (trader == null) return;
        var trading = trader.getTradingPlayer();
        trader.setTradingPlayer(null);
        if (trading instanceof ServerPlayer player && player.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu) player.closeContainer();
        CompoundTag tag = new CompoundTag();
        trader.saveWithoutId(tag);
        entity = tag;
        trader = null;
        setChanged();
    }

    /** The trader while someone trades (its offers are the live ones), else a stand-in loaded from the record, or null. */
    public Villager standIn() {
        if (trader != null) return trader;
        if (entityType() != EntityType.VILLAGER || level == null) return null;
        Villager v = EntityType.VILLAGER.create(level);
        if (v == null) return null;
        v.load(entity);
        v.setPos(worldPosition.getX() + 0.5, worldPosition.getY(), worldPosition.getZ() + 0.5);
        return v;
    }

    /** Opens trading for a jarred villager. Returns false for other heads, or while someone else is trading. */
    public boolean trade(ServerPlayer player) {
        if (entityType() != EntityType.VILLAGER || level == null) return false;
        if (trader != null && trader.getTradingPlayer() != null) return false;
        trader = EntityType.VILLAGER.create(level);
        if (trader == null) return false;
        trader.load(entity);
        trader.setPos(worldPosition.getX() + 0.5, worldPosition.getY(), worldPosition.getZ() + 0.5);
        long now = level.getGameTime();
        if (now - lastRestock > 12_000) {
            trader.restock();
            lastRestock = now;
        }
        trader.setTradingPlayer(player);
        trader.openTradingScreen(player, trader.getDisplayName(), trader.getVillagerData().getLevel());
        uses = uses(trader);
        // the push bell on the plinth's front
        level.blockEvent(worldPosition, getBlockState().getBlock(), EV_BELL, 0);
        return true;
    }

    private static int uses(Villager trader) {
        int n = 0;
        for (var offer : trader.getOffers()) n += offer.getUses();
        return n;
    }

    /** The house refusal at a placed head: it shakes its head (the line and the sound come from the refusal). */
    @Override
    public net.minecraft.world.phys.Vec3 refusalOrigin() {
        return net.minecraft.world.phys.Vec3.atCenterOf(worldPosition).add(0, 0.3, 0);
    }

    @Override
    public void showRefusal(ServerPlayer player, String reason) {
        if (level != null) level.blockEvent(worldPosition, getBlockState().getBlock(), EV_REFUSE, 0);
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id < 1 || id >= EVENTS) return false;
        if (level != null && level.isClientSide) {
            seen[id] = level.getGameTime();
            this.param[id] = param;
            if (id == EV_BELL) {
                // the push bell, at the plinth's front (the model's bell at (5, 4, -8) px, turned with the head)
                var p = net.minecraft.world.phys.Vec3.atBottomCenterOf(worldPosition)
                        .add(new net.minecraft.world.phys.Vec3(5 / 16.0, 4 / 16.0, -8 / 16.0).yRot((float) Math.toRadians(-getBlockState().getValue(JarredHeadBlock.FACING).toYRot())));
                level.playLocalSound(p.x, p.y, p.z, com.coolfreeze23.headinajar.fx.HeadSounds.DESK_BELL.get(), net.minecraft.sounds.SoundSource.BLOCKS, 0.3f, 2.0f, false);
            }
        }
        return true;
    }

    /** Ticks (with the partial tick) since moment {@code id} was seen on this client. */
    public double since(int id, double now) {
        return now - seen[id];
    }

    /** Client: the param of the last block event {@code id} (a piglin's inspection length in ticks). */
    public int param(int id) {
        return param[id];
    }

    /**
     * Client: the head turns to the nearest player within {@link #LOOK} blocks (and back to the front when none),
     * chased a little each tick so it looks round rather than snaps. A warden head turns to the last thing it heard.
     */
    public void clientTick() {
        yawPrev = yaw;
        pitchPrev = pitch;
        if (level == null) return;
        float wantYaw = 0, wantPitch = 0;
        var c = net.minecraft.world.phys.Vec3.atBottomCenterOf(worldPosition).add(0, 12.5 / 16.0, 0);
        net.minecraft.world.phys.Vec3 eye = null;
        if (entityType() == EntityType.WARDEN) {
            if (heard != -1) eye = net.minecraft.world.phys.Vec3.atCenterOf(BlockPos.of(heard));
        } else {
            net.minecraft.world.entity.player.Player p = level.getNearestPlayer(c.x, c.y, c.z, LOOK, false);
            if (p != null) eye = p.getEyePosition();
        }
        if (eye != null) {
            double dx = eye.x - c.x, dy = eye.y - c.y, dz = eye.z - c.z;
            float to = (float) Math.toDegrees(Math.atan2(-dx, dz));
            // relative to the way the jar faces, clockwise from above as a player's head turns
            wantYaw = net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(to - getBlockState().getValue(JarredHeadBlock.FACING).getOpposite().toYRot()), -75, 75);
            wantPitch = (float) net.minecraft.util.Mth.clamp(-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))), -20, 20);
        }
        yaw += (wantYaw - yaw) * 0.2f;
        pitch += (wantPitch - pitch) * 0.2f;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, JarredHeadBlockEntity head) {
        // a trade went through: the head nods
        if (head.trader != null) {
            int now = uses(head.trader);
            if (head.uses >= 0 && now > head.uses) level.blockEvent(pos, state.getBlock(), EV_NOD, 0);
            head.uses = now;
        }
        // trading has ended: keep what changed (uses, experience) and drop the stand-in
        if (head.trader != null && head.trader.getTradingPlayer() == null) {
            CompoundTag tag = new CompoundTag();
            head.trader.saveWithoutId(tag);
            head.entity = tag;
            head.trader = null;
            head.setChanged();
        }
        // its Personnel File: registered on the first tick after a load, its dawn, its badge
        com.coolfreeze23.headinajar.staff.StaffBlocks.tick(level, pos, head);
        // lunch and the desk job (an unfed head returns after a field read or two)
        DeskJobs.tick((ServerLevel) level, pos, head);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) DeskIndex.update(server, this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel server) DeskIndex.remove(server, this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level instanceof ServerLevel server) DeskIndex.remove(server, this);
    }

    // --- the warden's ears (every head has a listener; only a fed warden's hears anything) ----------------------------

    @Override
    public VibrationSystem.Listener getListener() {
        return vibrationListener;
    }

    @Override
    public VibrationSystem.Data getVibrationData() {
        return vibrationData;
    }

    @Override
    public VibrationSystem.User getVibrationUser() {
        return vibrationUser;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Type", type);
        tag.put("Entity", entity);
        tag.putLong("LastRestock", lastRestock);
        // written only once known, so an old head's save keeps exactly its old keys until it is registered
        if (jar != null) tag.putString("Jar", jar);
        if (staff != null) tag.put("Staff", staff.save());
        CompoundTag desk = writeDesk();
        if (!desk.isEmpty()) tag.put("Desk", desk);
        if (entityType() == EntityType.WARDEN) {
            VibrationSystem.Data.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), vibrationData)
                    .resultOrPartial(e -> { }).ifPresent(t -> tag.put("Listener", t));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        type = tag.getString("Type");
        entity = tag.getCompound("Entity");
        lastRestock = tag.getLong("LastRestock");
        jar = tag.contains("Jar") ? tag.getString("Jar") : null;
        staff = com.coolfreeze23.headinajar.staff.StaffFile.load(tag.get("Staff"));
        filed = false;
        readDesk(tag.getCompound("Desk"));
        if (tag.contains("Listener", Tag.TAG_COMPOUND)) {
            VibrationSystem.Data.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.getCompound("Listener"))
                    .resultOrPartial(e -> { }).ifPresent(d -> vibrationData = d);
        }
    }

    /** The parts of the stored mob that change how its head is drawn (only those: never the whole record). */
    private static final String[] LOOK_KEYS = {"Color", "Sheared", "HasLeftHorn", "HasRightHorn", "IsScreamingGoat", "HandItems"};

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", type);
        // the client only needs what draws the head: a villager's profession and biome type, the jar, the badge and the
        // band (never the name: that is for the employer's eyes, through Jade and the file card)
        if (entity.contains("VillagerData")) tag.put("VillagerData", entity.get("VillagerData"));
        // a slime's or magma cube's size (it is drawn whole, at its size)
        if (entity.contains("Size")) tag.putInt("Size", entity.getInt("Size"));
        if (jar != null) tag.putString("Jar", jar);
        if (badge >= 0) tag.putInt("Badge", badge);
        if (band != com.coolfreeze23.headinajar.staff.Band.NONE) tag.putByte("Band", (byte) band.ordinal());
        // the bowl, the head's look, the warden's last sound, and a line said in the last five seconds (a 0.6.0 game
        // reads none of these)
        int step = step(bowl);
        if (step > 0) tag.putByte("BowlStep", (byte) step);
        CompoundTag look = new CompoundTag();
        for (String k : LOOK_KEYS) if (entity.contains(k)) look.put(k, entity.get(k).copy());
        if (entityType() == EntityType.MOOSHROOM && entity.contains("Type")) look.putString("Type", entity.getString("Type"));
        if (!look.isEmpty()) tag.put("Look", look);
        if (heard != -1) tag.putLong("Heard", heard);
        if (sayFirst != null && level != null && level.getGameTime() - sayT < SAY_TICKS) {
            tag.putString("Say", Component.Serializer.toJson(sayFirst, registries));
            if (saySecond != null) tag.putString("Say2", Component.Serializer.toJson(saySecond, registries));
            tag.putLong("SayT", sayT);
        }
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        type = tag.getString("Type");
        CompoundTag e = new CompoundTag();
        if (tag.contains("VillagerData")) e.put("VillagerData", tag.get("VillagerData"));
        if (tag.contains("Size")) e.putInt("Size", tag.getInt("Size"));
        entity = e;
        jar = tag.contains("Jar") ? tag.getString("Jar") : null;
        badge = tag.contains("Badge") ? tag.getInt("Badge") : -1;
        band = com.coolfreeze23.headinajar.staff.Band.byId(tag.getByte("Band"));
        bowlStep = tag.getByte("BowlStep");
        look = tag.getCompound("Look");
        heard = tag.contains("Heard") ? tag.getLong("Heard") : -1;
        if (tag.contains("SayT") && tag.getLong("SayT") != sayT) {
            sayT = tag.getLong("SayT");
            sayFirst = Component.Serializer.fromJson(tag.getString("Say"), registries);
            saySecond = tag.contains("Say2") ? Component.Serializer.fromJson(tag.getString("Say2"), registries) : null;
            if (level != null && level.isClientSide && sayFirst != null) {
                com.coolfreeze23.headinajar.client.HeadSpeech.heard(worldPosition, SAY_HEIGHT, sayT, sayFirst, saySecond);
            }
        }
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        handleUpdateTag(packet.getTag(), registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** The client-side entity record (for drawing). */
    public CompoundTag entityTag() {
        return entity;
    }
}
