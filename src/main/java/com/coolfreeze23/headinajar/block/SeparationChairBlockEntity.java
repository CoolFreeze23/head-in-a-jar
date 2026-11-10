package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.entity.SeatEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.feedback.Refuser;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.item.CollarCamItem;
import com.coolfreeze23.headinajar.item.LifeSupportJarItem;
import com.coolfreeze23.headinajar.item.RelayGogglesItem;
import com.coolfreeze23.headinajar.menu.SeparationChairMenu;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Separation Chair: holds the parts and a full energy buffer, and runs the 12-second operation on the player
 * who sat down and signed. Once the cut has started it always finishes.
 * <p>
 * The operation runs on one clock: the game time it started at ({@link #opStart}), synced to every client. The
 * machine's animation and the patient's overlay both read {@link #elapsed}, so every viewer, however late, sees the
 * same moment.
 */
public class SeparationChairBlockEntity extends BlockEntity implements MenuProvider, GeoBlockEntity, Refuser {
    public static final int SLOT_JAR = 0, SLOT_GOGGLES = 1, SLOT_COLLAR = 2, SLOT_SALINE = 3;
    public static final int SALINE_NEEDED = 4;
    public static final int CAPACITY = 50_000;
    public static final int OP_TICKS = ChairTimeline.LENGTH;
    public static final int CUT_TICK = ChairTimeline.CUT_COMMIT;
    /** What the chair has loaded in its jar slot, as the client draws it. */
    public static final int JAR_NONE = 0, JAR_WIDE = 1, JAR_COMPACT = 2;
    /** Where the jar went at the end: a Head Jar entity at the outlet, or piped into a Cradle. */
    public static final int HANDOFF_ENTITY = 0, HANDOFF_CRADLE = 1;

    /**
     * Points of the model, in pixels in the model's own frame (the chair facing north: x to the patient's right, y up,
     * z to the back, origin at the block's bottom centre). They match bones of the geo model, which a test checks.
     */
    /** The jar's base centre with the tray out: 1.125 blocks to the patient's left, standing on the 1 px tray. */
    public static final Vec3 JAR_ANCHOR = new Vec3(-18, 1, 0);
    public static final Vec3 NECK = new Vec3(0, 18.5, 0);
    /** Under the dryer dome's front edge, where the anaesthetic comes from while the hood nods toward the face. */
    public static final Vec3 GAS_MOUTH = new Vec3(0, 26.5, -5.5);
    public static final Vec3 VENT_L = new Vec3(-5, 2, -1), VENT_R = new Vec3(5, 2, -1);

    private final Buffer energy = new Buffer(CAPACITY, 2_000, 0, this::setChanged);
    /** True while the chair itself moves parts in or out (consume, put back): the slots are locked to everyone else. */
    private boolean internal;
    private final ItemStackHandler items = new ItemStackHandler(4) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return validFor(slot, stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == SLOT_SALINE ? 16 : 1;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (locked() || slot == SLOT_JAR && !internal && trayBlocked() != null) return stack;
            return super.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return locked() ? ItemStack.EMPTY : super.extractItem(slot, amount, simulate);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            // the loaded jar is drawn standing at the outlet: tell the clients when it changes (not mid-operation,
            // when the jar in use is drawn instead)
            if (slot == SLOT_JAR && level != null && !level.isClientSide && opTick < CUT_TICK && jarKind != kindOf(getStackInSlot(SLOT_JAR))) {
                jarKind = kindOf(getStackInSlot(SLOT_JAR));
                changed();
            }
        }
    };

    private int wear;
    /** Ticks into the operation on the server, -1 when idle (saved as before). */
    private int opTick = -1;
    /** Game time the operation started at; the one clock everybody reads. -1 when idle. */
    private long opStart = -1;
    private UUID patient;
    private ItemStack inJar = ItemStack.EMPTY, inGoggles = ItemStack.EMPTY, inCollar = ItemStack.EMPTY;
    /** Decided at the point of no return, so the machine can fumble the head in front of everyone. */
    private boolean botched;
    /** Ticks the patient has been missing after the cut. */
    private int missingTicks;
    // synced to clients for drawing
    private int jarKind = JAR_NONE;
    private long abortAt = -1, finishAt = -1;
    private int handoff = HANDOFF_ENTITY;
    /** The jar the last operation used (it stays drawn until the real one has arrived). */
    private int lastJarKind = JAR_NONE;
    private UUID lastPatient;
    /** The head jar standing on the tray: it stands on the 1 px tray (no gravity) until it is moved off it. */
    private UUID onTray;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energy.getEnergyStored() / 10;
                case 1 -> opTick;
                case 2 -> wear;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    public SeparationChairBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.CHAIR_BE.get(), pos, state);
    }

    public static boolean validFor(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_JAR -> stack.getItem() instanceof LifeSupportJarItem;
            case SLOT_GOGGLES -> stack.getItem() instanceof RelayGogglesItem;
            case SLOT_COLLAR -> stack.getItem() instanceof CollarCamItem;
            case SLOT_SALINE -> stack.is(ModRegistry.SALINE_BOTTLE.get());
            default -> false;
        };
    }

    private static int kindOf(ItemStack jar) {
        if (!(jar.getItem() instanceof LifeSupportJarItem item)) return JAR_NONE;
        return item.compact() ? JAR_COMPACT : JAR_WIDE;
    }

    /** From the signature to the end of the operation nothing goes in or out of the slots but by the chair's hand. */
    public boolean locked() {
        return running() && !internal;
    }

    public Buffer energy() {
        return energy;
    }

    public ItemStackHandler items() {
        return items;
    }

    public int wear() {
        return wear;
    }

    public void setWear(int wear) {
        this.wear = Math.max(0, Math.min(100, wear));
        setChanged();
    }

    public boolean running() {
        return level != null && level.isClientSide ? opStart >= 0 : opTick >= 0;
    }

    /** Whole ticks into the operation (the server's count; on a client, read from the shared clock). */
    public int opTick() {
        if (level != null && level.isClientSide) return running() ? (int) elapsed(level.getGameTime(), 0) : -1;
        return opTick;
    }

    /**
     * Ticks into the operation at this game time and partial tick, from the synced start: the one clock the
     * animation, the overlay and any late arrival read. Clamped to the operation's length.
     */
    public float elapsed(long gameTime, float partialTick) {
        if (level != null && !level.isClientSide) return opTick < 0 ? -1 : opTick + partialTick;
        if (opStart < 0) return -1;
        return Math.max(0, Math.min(OP_TICKS, (float) (gameTime - opStart) + partialTick));
    }

    public long opStart() {
        return opStart;
    }

    public UUID patient() {
        return patient;
    }

    public boolean botched() {
        return botched;
    }

    /** The jar to draw: the one loaded, or (from the cut) the one in use. */
    public int jarKind() {
        return jarKind;
    }

    public int lastJarKind() {
        return lastJarKind;
    }

    public UUID lastPatient() {
        return lastPatient;
    }

    public long abortAt() {
        return abortAt;
    }

    public long finishAt() {
        return finishAt;
    }

    public int handoff() {
        return handoff;
    }

    public Direction facing() {
        return getBlockState().getValue(SeparationChairBlock.FACING);
    }

    public static int energyCost() {
        return Config.get(Config.BOOTH_ENERGY, 40_000);
    }

    /** What still stops the operation, as translation keys; empty means ready. */
    public List<String> missing() {
        List<String> out = new ArrayList<>();
        if (energy.getEnergyStored() < energyCost()) out.add("power");
        if (items.getStackInSlot(SLOT_JAR).isEmpty()) out.add("jar");
        if (items.getStackInSlot(SLOT_GOGGLES).isEmpty()) out.add("goggles");
        if (items.getStackInSlot(SLOT_COLLAR).isEmpty()) out.add("collar");
        if (items.getStackInSlot(SLOT_SALINE).getCount() < SALINE_NEEDED) out.add("saline");
        return out;
    }

    // --- where things are in the world --------------------------------------------------------------------------

    /** A point of the model (pixels, chair facing north) in the world, for a chair at {@code pos} facing {@code facing}. */
    public static Vec3 world(BlockPos pos, Direction facing, Vec3 px) {
        double x = px.x / 16, z = px.z / 16;
        double wx, wz;
        switch (facing) {
            case SOUTH -> {
                wx = -x;
                wz = -z;
            }
            case WEST -> {
                wx = z;
                wz = -x;
            }
            case EAST -> {
                wx = -z;
                wz = x;
            }
            default -> {
                wx = x;
                wz = z;
            }
        }
        return Vec3.atBottomCenterOf(pos).add(wx, px.y / 16, wz);
    }

    public Vec3 world(Vec3 px) {
        return world(worldPosition, facing(), px);
    }

    /** Where the chair's jar stands, and where the real jar is left: the base centre of the jar at the patient's left-front. */
    public static Vec3 outlet(BlockPos pos, Direction facing) {
        return world(pos, facing, JAR_ANCHOR);
    }

    /**
     * The blocks the machine works in: the tray and the jar take the block to the left (and the one above it); the hood
     * rises above the chair and swings through the front and front-left blocks one and two levels up; the patient's
     * feet and the barrier's tip use the front block.
     */
    public static List<BlockPos> workSpace(BlockPos pos, Direction facing) {
        Direction left = facing.getCounterClockWise();
        BlockPos l = pos.relative(left), f = pos.relative(facing), fl = f.relative(left);
        return List.of(l, l.above(), f, f.above(), f.above(2), fl.above(), fl.above(2), pos.above());
    }

    /** The first block in the work space that is in the way, or null when the space is clear. */
    public static BlockPos blockedAt(BlockGetter level, BlockPos pos, Direction facing) {
        for (BlockPos p : workSpace(pos, facing)) {
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return p;
        }
        return null;
    }

    /** The blocks the jar tray slides into: the block to the patient's left and the one above it. */
    public static List<BlockPos> traySpace(BlockPos pos, Direction facing) {
        BlockPos l = pos.relative(facing.getCounterClockWise());
        return List.of(l, l.above());
    }

    /** The first block in the tray's way, or null when a jar can be loaded. */
    public BlockPos trayBlocked() {
        if (level == null) return null;
        for (BlockPos p : traySpace(worldPosition, facing())) {
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return p;
        }
        return null;
    }

    /** A jar can't be loaded with the tray's space blocked: say so to whoever tried. */
    public boolean refuseJar(ServerPlayer player) {
        BlockPos blocked = trayBlocked();
        if (blocked == null) return false;
        Refusals.deny(player, this, "chair.tray_blocked", Component.translatable(spaceName(blocked)));
        return true;
    }

    /** The player in the chair, if any. */
    public ServerPlayer seated() {
        if (!(level instanceof ServerLevel server)) return null;
        for (SeatEntity seat : server.getEntitiesOfClass(SeatEntity.class, new net.minecraft.world.phys.AABB(worldPosition).inflate(0.5))) {
            if (seat.getFirstPassenger() instanceof ServerPlayer player) return player;
        }
        return null;
    }

    // --- the operation -------------------------------------------------------------------------------------------

    /** The player signed Form 27-B. Only the seated player can, and only when nothing is missing. */
    public boolean sign(ServerPlayer player) {
        if (running() || seated() != player) return false;
        List<String> missing = missing();
        if (!missing.isEmpty()) {
            player.displayClientMessage(Component.translatable("dolores.headinajar.refuse." + missing.get(0)), true);
            return false;
        }
        if (FormActions.state(player).form().isDecapitated()) {
            player.displayClientMessage(Component.translatable("dolores.headinajar.refuse.already"), true);
            return false;
        }
        BlockPos blocked = blockedAt(level, worldPosition, facing());
        if (blocked != null) {
            return Refusals.deny(player, this, "chair.blocked", Component.translatable(spaceName(blocked)));
        }
        patient = player.getUUID();
        opTick = 0;
        opStart = level.getGameTime();
        botched = false;
        missingTicks = 0;
        abortAt = finishAt = -1;
        player.closeContainer();
        changed();
        say(player, "start");
        com.coolfreeze23.headinajar.Advance.grant(player, "signed");
        return true;
    }

    private String spaceName(BlockPos blocked) {
        Direction facing = facing();
        BlockPos column = new BlockPos(blocked.getX(), worldPosition.getY(), blocked.getZ());
        String which = column.equals(worldPosition) ? "above"
                : column.equals(worldPosition.relative(facing)) ? "front"
                : column.equals(worldPosition.relative(facing.getCounterClockWise())) ? "left" : "front_left";
        return "gui.headinajar.chair.space." + which;
    }

    private void say(ServerPlayer player, String line) {
        player.displayClientMessage(Component.translatable("dolores.headinajar." + line).withStyle(net.minecraft.ChatFormatting.AQUA), true);
        voice(player, line);
    }

    /**
     * VOICE LINE HOOK: DOLORES says the line "dolores.headinajar.{line}" to the patient here (start, gas, scan, cut,
     * jar, done, oopsie, abort, already). Nothing is played yet.
     */
    private void voice(ServerPlayer player, String line) {
    }

    /** A sound from the chair, heard by everyone in range. */
    private void sound(SoundEvent sound, float volume, float pitch) {
        level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, volume, pitch);
    }

    /** A chair sound event at volume 1, its pitch drawn from its range around {@code pitch}. */
    private void chair(net.neoforged.neoforge.registries.DeferredHolder<SoundEvent, SoundEvent> event, float pitch, Vec3 at, Player except) {
        SoundEvent e = forPatient(event.get());
        level.playSound(except, at.x, at.y, at.z, e, SoundSource.BLOCKS, 1, ChairSounds.pitch(event.getId().getPath(), pitch, level.random));
    }

    /**
     * MUFFLED PATIENT HOOK: the events from the gas to the lid are meant to reach the patient muffled. Until those
     * versions exist this returns the event itself.
     */
    private SoundEvent forPatient(SoundEvent event) {
        return event;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SeparationChairBlockEntity chair) {
        if (chair.onTray != null && level.getGameTime() % 5 == 0) chair.watchTray();
        if (!chair.running()) return;
        ServerPlayer player = chair.seated();
        if (player == null || !player.getUUID().equals(chair.patient)) {
            // the patient got up (only possible before the cut) or vanished
            if (chair.opTick < CUT_TICK) {
                chair.abort(player, "abort");
                return;
            }
            // after the cut the chair waits for them (a relog brings the seat back); the clock waits too (the start
            // a client is sent is always now less the count)
            if (++chair.missingTicks >= ChairTimeline.PATIENT_GRACE) chair.abort(null, "abort");
            else if (chair.missingTicks % 10 == 0) chair.changed();
            return;
        }
        chair.missingTicks = 0;
        chair.step(player);
    }

    private void step(ServerPlayer player) {
        ServerLevel server = (ServerLevel) level;
        Vec3 c = Vec3.atCenterOf(worldPosition);
        Vec3 jar = world(JAR_ANCHOR.add(0, 12, 0));
        if (opTick == ChairTimeline.CURTAIN_UP) say(player, "privacy");
        switch (opTick) {
            case ChairTimeline.MOTOR -> chair(ChairSounds.SERVO_BARRIER, 1, c, null);
            case ChairTimeline.BARRIER_DROP -> chair(ChairSounds.BARRIER_DROP, 1, c, null);
            case ChairTimeline.BARRIER_CONTACT - ChairTimeline.LATCH_PREROLL -> chair(ChairSounds.BARRIER_LATCH, 1, c, null);
            case ChairTimeline.BARRIER_SETTLE - ChairTimeline.LATCH_PREROLL -> chair(ChairSounds.BARRIER_SETTLE, 1, c, null);
            case 20 -> say(player, "gas");
            case ChairTimeline.GAS -> chair(ChairSounds.GAS_HISS, 1, world(GAS_MOUTH), null);
            case ChairTimeline.SCAN -> {
                chair(ChairSounds.SCAN_CHIRP, 1, c, null);
                say(player, "scan");
            }
            case CUT_TICK -> {
                // the critical phase: everything is spent here, and nothing can stop it now
                consume();
                say(player, "cut");
            }
            case ChairTimeline.CUT -> {
                // the patient hears nothing at all here: their overlay is black
                chair(ChairSounds.CUT, 1, world(NECK), player);
                Vec3 neck = world(NECK);
                cutPuff(server, neck);
                // the head is the chair's now: whatever was worn on it comes off with it into the patient's pockets
                ItemStack helmet = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
                if (!helmet.isEmpty()) {
                    player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ItemStack.EMPTY);
                    if (!player.getInventory().add(helmet)) player.drop(helmet, false);
                }
            }
            case ChairTimeline.GLUG_LID -> {
                chair(ChairSounds.SALINE_GLUG, 1, jar, null);
                say(player, "jar");
            }
            case ChairTimeline.ANTENNA -> chair(ChairSounds.ANTENNA, 1, c, null);
            case ChairTimeline.BARRIER_LIFT -> chair(ChairSounds.BARRIER_LIFT, 1, c, null);
            default -> {
            }
        }
        for (int t : ChairTimeline.GLUG_POUR) if (opTick == t) chair(ChairSounds.SALINE_GLUG, 1, jar, null);
        for (int i = 0; i < ChairTimeline.RATCHET.length; i++) {
            if (opTick == ChairTimeline.RATCHET[i]) {
                chair(ChairSounds.RATCHET, 1 + 0.03f * i, world(NECK), null);
                if (i == 0) {
                    Vec3 neck = world(NECK);
                    server.sendParticles(ParticleTypes.SMOKE, neck.x, neck.y, neck.z, 2, 0.08, 0.03, 0.08, 0.005);
                }
            }
        }
        if (opTick >= ChairTimeline.GAS && opTick <= ChairTimeline.GAS_END && (opTick - ChairTimeline.GAS) % ChairTimeline.GAS_EVERY == 0) {
            // pale green anaesthetic from the horn under the chin
            Vec3 gas = world(GAS_MOUTH);
            server.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.72f, 0.92f, 0.70f), 0.9f),
                    gas.x, gas.y, gas.z, 3, 0.06, 0.04, 0.06, 0);
            server.sendParticles(ParticleTypes.POOF, gas.x, gas.y, gas.z, 1, 0.03, 0.02, 0.03, 0.005);
        }
        if (opTick >= OP_TICKS) {
            finish(player);
            return;
        }
        opTick++;
        if (opTick % 10 == 0) changed();
    }

    /**
     * The cut, seen from outside: a small flat ring of white at the neck seam and one wisp rising, gone in half a
     * second, so the head's hop and the blade leaving stay visible. Nothing red.
     */
    private static void cutPuff(ServerLevel server, Vec3 neck) {
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            double dx = Math.cos(a), dz = Math.sin(a);
            server.sendParticles(ParticleTypes.WHITE_SMOKE, neck.x + dx * 0.16, neck.y, neck.z + dz * 0.16, 0, dx, 0.05, dz, 0.035);
        }
        server.sendParticles(ParticleTypes.WHITE_SMOKE, neck.x, neck.y + 0.05, neck.z, 0, 0, 1, 0, 0.03);
        server.sendParticles(ParticleTypes.ELECTRIC_SPARK, neck.x, neck.y, neck.z, 1, 0.02, 0.0, 0.02, 0);
    }

    private void consume() {
        internal = true;
        inJar = items.extractItem(SLOT_JAR, 1, false);
        inGoggles = items.extractItem(SLOT_GOGGLES, 1, false);
        inCollar = items.extractItem(SLOT_COLLAR, 1, false);
        items.extractItem(SLOT_SALINE, SALINE_NEEDED, false);
        internal = false;
        energy.set(energy.getEnergyStored() - energyCost());
        // the blade decides now: a worn one fumbles the head on the way to the jar, in front of everyone
        botched = Config.get(Config.BOTCH_ENABLED, true) && wear > 80 && level.random.nextInt(100) < (wear - 80) * 2;
        // the jar slot is empty now, but the jar in use is still drawn
        jarKind = kindOf(inJar);
        changed();
    }

    /** Finishes at once (the patient is leaving the server after the cut). */
    public void finishNow(ServerPlayer player) {
        if (!running() || !player.getUUID().equals(patient)) return;
        if (opTick < CUT_TICK) {
            abort(player, "abort");
            return;
        }
        finish(player);
    }

    private void finish(ServerPlayer player) {
        ServerLevel server = (ServerLevel) level;
        boolean botch = botched;
        // the op is over: release the restraints before standing the patient up
        opTick = -1;
        opStart = -1;
        patient = null;
        player.stopRiding();
        if (botch) CollarCamItem.setCrooked(inCollar, true);
        Direction front = facing();
        Vec3 outlet = outlet(worldPosition, front);
        LifeSupportCradleBlockEntity cradle = LifeSupportCradleBlockEntity.nearbyEmpty(server, worldPosition, 3);
        boolean docked = cradle != null && cradle.isEmpty();
        Vec3 stand = Vec3.atBottomCenterOf(worldPosition).add(front.getStepX() * 0.9, 0, front.getStepZ() * 0.9);
        // they stand up facing out of the chair; the head is left facing the same way, square with the chair
        player.teleportTo(server, stand.x, stand.y, stand.z, front.toYRot(), player.getXRot());
        player.setYRot(front.toYRot());
        player.setYHeadRot(front.toYRot());
        FormActions.separate(player, inJar, inGoggles, inCollar, outlet, cradle);
        if (!docked) {
            for (var jar : server.getEntitiesOfClass(com.coolfreeze23.headinajar.entity.HeadJarEntity.class, new net.minecraft.world.phys.AABB(outlet, outlet).inflate(0.1),
                    j -> player.getUUID().equals(j.ownerId()))) {
                jar.setNoGravity(true);
                onTray = jar.getUUID();
                // a botched cut leaves the head upside down for as long as the patient's view is rolled
                if (botch) jar.setUpsideDown(FormActions.state(player).rollTicks() > 0 ? FormActions.state(player).rollTicks() : 600);
            }
        }
        com.coolfreeze23.headinajar.Advance.grant(player, "separated");
        if (botch) com.coolfreeze23.headinajar.Advance.grant(player, "botched");
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160));
        if (botch) {
            FormActions.state(player).setRollTicks(600);
            say(player, "oopsie");
            sound(com.coolfreeze23.headinajar.registry.ModSounds.BOOTH_OOPSIE.get(), 1, 1);
        } else {
            say(player, "done");
        }
        if (docked) {
            // piped away to the Cradle
            sound(SoundEvents.PISTON_CONTRACT, 0.6f, 1.4f);
            server.sendParticles(ParticleTypes.CLOUD, outlet.x, outlet.y + 0.4, outlet.z, 6, 0.2, 0.2, 0.2, 0.01);
        }
        sound(SoundEvents.PLAYER_LEVELUP, 0.6f, 0.6f);
        // a Mason on staff halves blade wear
        setWear(wear + Math.round(5 * (1 - 0.5f * HeadJobs.staff(level, worldPosition, "mason"))));
        lastJarKind = kindOf(inJar);
        lastPatient = player.getUUID();
        inJar = inGoggles = inCollar = ItemStack.EMPTY;
        botched = false;
        finishAt = level.getGameTime();
        handoff = docked ? HANDOFF_CRADLE : HANDOFF_ENTITY;
        jarKind = kindOf(items.getStackInSlot(SLOT_JAR));
        changed();
    }

    /** Stops before the cut: everything is kept except a quarter of the energy. After it, the parts are given back. */
    public void abort(ServerPlayer player, String why) {
        if (!running()) return;
        internal = true;
        if (opTick >= CUT_TICK) {
            // after the cut the parts are spent; give back what the chair still holds
            putBack(inJar, SLOT_JAR);
            putBack(inGoggles, SLOT_GOGGLES);
            putBack(inCollar, SLOT_COLLAR);
        } else {
            energy.set(energy.getEnergyStored() - energyCost() / 4);
        }
        internal = false;
        inJar = inGoggles = inCollar = ItemStack.EMPTY;
        opTick = -1;
        opStart = -1;
        patient = null;
        botched = false;
        missingTicks = 0;
        abortAt = level.getGameTime();
        jarKind = kindOf(items.getStackInSlot(SLOT_JAR));
        Vec3 c = Vec3.atCenterOf(worldPosition);
        chair(ChairSounds.UNLATCH, 1, c, null);
        chair(ChairSounds.BARRIER_LIFT, 1, c, null);
        if (player != null) say(player, why);
        changed();
    }

    /** Lets the head jar fall as normal once it is no longer standing on the tray (or the tray is gone). */
    private void watchTray() {
        if (!(level instanceof ServerLevel server)) return;
        net.minecraft.world.entity.Entity jar = server.getEntity(onTray);
        Vec3 outlet = outlet(worldPosition, facing());
        if (jar == null || jar.isRemoved()) {
            onTray = null;
        } else if (jar.position().distanceToSqr(outlet) > 0.01 || isRemoved()) {
            jar.setNoGravity(false);
            onTray = null;
        }
    }

    /** The chair is being destroyed: whatever is in use comes out with the rest, so nothing is lost. */
    public void dropAll() {
        if (running()) abort(null, "abort");
        if (onTray != null && level instanceof ServerLevel server && server.getEntity(onTray) != null) server.getEntity(onTray).setNoGravity(false);
        for (int i = 0; i < items.getSlots(); i++) {
            net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), items.getStackInSlot(i).copy());
        }
    }

    private void putBack(ItemStack stack, int slot) {
        if (stack.isEmpty()) return;
        ItemStack left = items.insertItem(slot, stack, false);
        if (!left.isEmpty() && level != null) {
            net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY() + 1, worldPosition.getZ(), left);
        }
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, SeparationChairBlockEntity chair) {
    }

    @Override
    public Vec3 refusalOrigin() {
        return Vec3.atCenterOf(worldPosition);
    }

    // --- saving and syncing --------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Energy", energy.serializeNBT(registries));
        tag.put("Items", items.serializeNBT(registries));
        tag.putInt("Wear", wear);
        tag.putInt("OpTick", opTick);
        if (patient != null) tag.putUUID("Patient", patient);
        tag.put("InJar", inJar.saveOptional(registries));
        tag.put("InGoggles", inGoggles.saveOptional(registries));
        tag.put("InCollar", inCollar.saveOptional(registries));
        tag.putBoolean("Botched", botched);
        if (onTray != null) tag.putUUID("OnTray", onTray);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Energy")) energy.deserializeNBT(registries, tag.get("Energy"));
        items.deserializeNBT(registries, tag.getCompound("Items"));
        wear = tag.getInt("Wear");
        opTick = tag.contains("OpTick") ? tag.getInt("OpTick") : -1;
        patient = tag.hasUUID("Patient") ? tag.getUUID("Patient") : null;
        inJar = ItemStack.parseOptional(registries, tag.getCompound("InJar"));
        inGoggles = ItemStack.parseOptional(registries, tag.getCompound("InGoggles"));
        inCollar = ItemStack.parseOptional(registries, tag.getCompound("InCollar"));
        botched = tag.getBoolean("Botched");
        onTray = tag.hasUUID("OnTray") ? tag.getUUID("OnTray") : null;
        jarKind = opTick >= CUT_TICK ? kindOf(inJar) : kindOf(items.getStackInSlot(SLOT_JAR));
        opStart = -1;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("OpTick", opTick);
        if (opTick >= 0 && level != null) tag.putLong("OpStart", level.getGameTime() - opTick);
        tag.putInt("Wear", wear);
        if (patient != null) tag.putUUID("Patient", patient);
        tag.putBoolean("Botched", botched);
        tag.putInt("Jar", jarKind);
        tag.putInt("LastJar", lastJarKind);
        if (lastPatient != null) tag.putUUID("LastPatient", lastPatient);
        tag.putLong("AbortAt", abortAt);
        tag.putLong("FinishAt", finishAt);
        tag.putInt("Handoff", handoff);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        opTick = tag.getInt("OpTick");
        opStart = opTick >= 0 && tag.contains("OpStart") ? tag.getLong("OpStart") : -1;
        wear = tag.getInt("Wear");
        patient = tag.hasUUID("Patient") ? tag.getUUID("Patient") : null;
        botched = tag.getBoolean("Botched");
        jarKind = tag.getInt("Jar");
        lastJarKind = tag.getInt("LastJar");
        lastPatient = tag.hasUUID("LastPatient") ? tag.getUUID("LastPatient") : null;
        abortAt = tag.contains("AbortAt") ? tag.getLong("AbortAt") : -1;
        finishAt = tag.contains("FinishAt") ? tag.getLong("FinishAt") : -1;
        handoff = tag.getInt("Handoff");
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        handleUpdateTag(packet.getTag(), registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.headinajar.separation_chair");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new SeparationChairMenu(id, inventory, this, data);
    }

    // --- GeckoLib ------------------------------------------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new ChairController(this));
        controllers.add(new ChairController.Tray(this));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /** GeckoLib's clock for this chair: game time plus the partial tick, the same clock the operation runs on. */
    @Override
    public double getTick(Object blockEntity) {
        return level != null && level.isClientSide ? com.coolfreeze23.headinajar.client.ChairClock.now(level) : 0;
    }
}
