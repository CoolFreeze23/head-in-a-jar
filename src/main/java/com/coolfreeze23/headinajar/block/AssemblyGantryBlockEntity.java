package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.item.MechaFrameItem;
import com.coolfreeze23.headinajar.item.MechaModuleItem;
import com.coolfreeze23.headinajar.machine.GantryPlan;
import com.coolfreeze23.headinajar.machine.KitMachine;
import com.coolfreeze23.headinajar.machine.MachineKit;
import com.coolfreeze23.headinajar.machine.MachineLamp;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.menu.AssemblyGantryMenu;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Assembly Gantry: put a frame and modules in, press Assemble (10 kFE) and the arm builds the mecha on the
 * turntable over the pedestal. The arm stands off, the frame stands on the deck 10 ticks after the press and plays its
 * own build while the arm welds each module home and the turntable turns it, then turns it to face its owner. Boarding
 * cuts the build short (the arm folds home). Disassemble takes the arm 60 ticks; Repair spends Mecha Plating (20 HP
 * each) and tops the battery up at once, then the arm makes a weld pass while the charge lead runs from the reel. A
 * mecha standing on the pad charges from the Gantry's buffer.
 */
public class AssemblyGantryBlockEntity extends BlockEntity implements MenuProvider, HasItems, KitMachine, software.bernie.geckolib.animatable.GeoBlockEntity {
    public static final int SLOT_FRAME = 0, SLOT_PLATING = 1, FIRST_MODULE = 2, MODULE_SLOTS = MechaFrame.maxSlots();
    public static final int ASSEMBLE_COST = 10_000;
    /** The jobs the arm is on (synced): none, building, taking apart, repairing. */
    public static final int JOB_NONE = 0, JOB_BUILD = 1, JOB_DISASSEMBLE = 2, JOB_REPAIR = 3;
    /**
     * What automation sees: a hopper may keep the Mecha Plating topped up (from the top and sides); the frame and the
     * modules are the builder's own, set by hand, and never taken by a pipe.
     */
    public static final MachineItems.Role[] ROLES = roles();

    private static MachineItems.Role[] roles() {
        MachineItems.Role[] roles = MachineItems.all(FIRST_MODULE + MODULE_SLOTS, MachineItems.Role.HAND);
        roles[SLOT_PLATING] = MachineItems.Role.INPUT;
        return roles;
    }

    private final MachineKit kit = new MachineKit(this);
    private final Buffer energy = new Buffer(100_000, 5_000, 0, this::setChanged);
    private final ItemStackHandler items = new ItemStackHandler(FIRST_MODULE + MODULE_SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == SLOT_FRAME) return stack.getItem() instanceof MechaFrameItem;
            if (slot == SLOT_PLATING) return stack.is(ModRegistry.MECHA_PLATING.get());
            MechaFrame frame = frame();
            if (frame == null || !(stack.getItem() instanceof MechaModuleItem module)) return false;
            return frame.slotAt(slot - FIRST_MODULE) == module.type().slot;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == SLOT_PLATING ? 64 : 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (slot == SLOT_FRAME || slot >= FIRST_MODULE && slot < FIRST_MODULE + 3) kit.sync();
        }
    };
    private int selected;

    // --- the job (synced) ---------------------------------------------------------------------------------------
    private int job = JOB_NONE;
    private long jobStart;
    private MechaFrame jobFrame;
    /** Build: the owner's bearing (turntable degrees) the frame is turned to. Repair, take-apart: where it stood. */
    private float jobAngle;
    /** When boarding cut a build short (the arm folds home from where it was), or never. */
    private long foldAt = Long.MIN_VALUE / 2;
    /** A mecha stands on the pad (the arm stands off for it); the pad is charging one now. */
    private boolean loaded, charging;
    // --- server only ---------------------------------------------------------------------------------------------
    /** The frame built, waiting for its moment to stand on the pad (LEAD ticks after the press); saved with the Gantry. */
    private MechaEntity pending;
    private CompoundTag pendingSave;
    private UUID jobMecha, jobPlayer;
    private long chargedAt = Long.MIN_VALUE / 2;
    // --- client only ---------------------------------------------------------------------------------------------
    public float reelAngle, reelAnglePrev, idleAngle, idleAnglePrev;
    private Item shownFrame = Items.AIR;
    private final Item[] shownModules = {Items.AIR, Items.AIR, Items.AIR};

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> energy.getEnergyStored() / 10;
                case 1 -> selected;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {
            if (i == 1) selected = v;
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    public AssemblyGantryBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.GANTRY_BE.get(), pos, state);
    }

    public Buffer energy() {
        return energy;
    }

    @Override
    public ItemStackHandler items() {
        return items;
    }

    @Override
    public MachineKit kit() {
        return kit;
    }

    public MechaFrame frame() {
        return items.getStackInSlot(SLOT_FRAME).getItem() instanceof MechaFrameItem f ? f.frame() : null;
    }

    public int job() {
        return job;
    }

    public long jobStart() {
        return jobStart;
    }

    public MechaFrame jobFrame() {
        return jobFrame;
    }

    public float jobAngle() {
        return jobAngle;
    }

    public long foldAt() {
        return foldAt;
    }

    public boolean loaded() {
        return loaded;
    }

    public boolean charging() {
        return charging;
    }

    public Item shownFrame() {
        return shownFrame;
    }

    public Item shownModule(int i) {
        return shownModules[i];
    }

    /** How long the job's clip runs (ticks), or 0. */
    public int jobLength() {
        GantryPlan.Frame plan = jobFrame == null ? null : GantryPlan.of(jobFrame);
        if (plan == null) return 0;
        return switch (job) {
            case JOB_BUILD -> plan.length();
            case JOB_DISASSEMBLE -> plan.disassemble();
            case JOB_REPAIR -> plan.repair();
            default -> 0;
        };
    }

    /** The turntable's angle (degrees counter-clockwise from above, 0 = the frame facing the front) at {@code now} during a job, else NaN. */
    public double turntable(double now) {
        if (job == JOB_NONE || jobFrame == null) return Double.NaN;
        GantryPlan.Frame plan = GantryPlan.of(jobFrame);
        if (plan == null) return Double.NaN;
        double tau = now - jobStart;
        return switch (job) {
            case JOB_BUILD -> GantryPlan.buildAngle(jobFrame, tau - GantryPlan.LEAD, jobAngle);
            case JOB_DISASSEMBLE -> GantryPlan.jobAngle(plan.disassembleTurn(), tau, jobAngle);
            case JOB_REPAIR -> GantryPlan.jobAngle(plan.repairTurn(), tau, jobAngle);
            default -> Double.NaN;
        };
    }

    /** The yaw (Minecraft degrees) of the Gantry's front. */
    public float frontYaw() {
        return getBlockState().getValue(MachineBlock.FACING).toYRot();
    }

    /** The yaw a frame stands at for a turntable angle. */
    public float yawFor(double angle) {
        return (float) Mth.wrapDegrees(frontYaw() - angle);
    }

    /** Where a frame stands on the deck. */
    public Vec3 deck() {
        return new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + AssemblyGantryBlock.DECK, worldPosition.getZ() + 0.5);
    }

    private AABB pad() {
        return new AABB(worldPosition.above()).inflate(1, 2, 1);
    }

    /** The mecha standing on the pad with nobody aboard, if any. */
    public MechaEntity onPad() {
        if (level == null) return null;
        List<MechaEntity> list = level.getEntitiesOfClass(MechaEntity.class, pad(), m -> !m.isVehicle());
        return list.isEmpty() ? null : list.get(0);
    }

    /** The mecha this job works on, on the client too (the one on the pad), or null. */
    public MechaEntity jobMecha() {
        return job == JOB_NONE ? null : onPad();
    }

    /** FE a tick the Gantry moves from its own buffer into a mecha standing on its pad (its intake rate). */
    public static final int PAD_CHARGE = 5_000;
    /** A top-up this small (a parked frame's idle draw) is not shown as charging. */
    private static final int TRICKLE = 100;

    public static void serverTick(Level level, BlockPos pos, BlockState state, AssemblyGantryBlockEntity gantry) {
        gantry.tickServer((ServerLevel) level);
    }

    private void tickServer(ServerLevel level) {
        long now = level.getGameTime();
        if (pendingSave != null && pending == null) {
            // a build saved between the press and the frame standing up: it stands now
            Entity e = net.minecraft.world.entity.EntityType.create(pendingSave, level).orElse(null);
            pending = e instanceof MechaEntity m ? m : null;
            pendingSave = null;
            if (pending != null) {
                job = JOB_BUILD;
                jobFrame = pending.frame();
                jobStart = now - GantryPlan.LEAD;
            }
        }
        if (job != JOB_NONE) tickJob(level, now);
        // a mecha on the pad, piloted or parked, charges from the Gantry's buffer; what it gains the Gantry loses
        List<MechaEntity> on = level.getEntitiesOfClass(MechaEntity.class, pad(), MechaEntity::isAlive);
        MechaEntity mecha = on.isEmpty() ? null : on.get(0);
        if (mecha != null) {
            if (job == JOB_NONE && !mecha.isVehicle()) settle(mecha);
            int moved = Math.max(0, Math.min(Math.min(PAD_CHARGE, energy.getEnergyStored()), mecha.batteryCapacity() - mecha.battery()));
            if (moved > 0) {
                mecha.setBattery(mecha.battery() + moved);
                energy.set(energy.getEnergyStored() - moved);
                // a real charge shows (the lead, the reel, the clip); topping up what a parked frame idles away does not
                if (moved > TRICKLE) chargedAt = now;
            }
        }
        loaded = mecha != null || pending != null;
        // charging is shown for at least a moment (the reel, the lead, the clip), not flickered on and off
        charging = mecha != null && now - chargedAt < MachineKit.HOLD;
        kit.tick(lamp(mecha), job != JOB_NONE || charging, false, 0);
        kit.sync();
    }

    /**
     * A parked mecha whose feet are below the deck, inside the Gantry's own cell (one parked on this Gantry before it had
     * a turntable: an older Gantry was a 6 px slab, so its frames rest 0.375 above the block, or sink to its base once the
     * new pedestal is there) is lifted onto it.
     */
    private void settle(MechaEntity mecha) {
        Vec3 deck = deck();
        double dx = mecha.getX() - deck.x, dz = mecha.getZ() - deck.z;
        if (mecha.getY() < deck.y - 1e-3 && mecha.getY() > worldPosition.getY() - 0.05 && Math.abs(dx) < 0.3 && Math.abs(dz) < 0.3) {
            mecha.setPos(mecha.getX(), deck.y, mecha.getZ());
        }
    }

    private MachineLamp lamp(MechaEntity mecha) {
        if (job != JOB_NONE || charging) return MachineLamp.WORK;
        int stored = energy.getEnergyStored();
        if (mecha != null) {
            // a frame parked on the pad, waiting for its pilot; short of power if it wants charge the Gantry hasn't got
            return stored <= 0 && mecha.battery() < mecha.batteryCapacity() ? MachineLamp.LOW : MachineLamp.WAIT;
        }
        if (frame() != null && stored < ASSEMBLE_COST) return MachineLamp.LOW;
        return stored >= ASSEMBLE_COST ? MachineLamp.READY : MachineLamp.OFF;
    }

    private void tickJob(ServerLevel level, long now) {
        long tau = now - jobStart;
        if (job == JOB_BUILD && pending != null) {
            if (tau < GantryPlan.LEAD) return;
            // the frame stands on the deck facing the front; its own build plays from here
            Vec3 deck = deck();
            pending.moveTo(deck.x, deck.y, deck.z, frontYaw(), 0);
            pending.setYBodyRot(frontYaw());
            pending.setYHeadRot(frontYaw());
            level.addFreshEntity(pending);
            pending.startBuild();
            jobMecha = pending.getUUID();
            pending = null;
            setChanged();
        }
        MechaEntity mecha = jobMecha == null ? null : level.getEntity(jobMecha) instanceof MechaEntity m ? m : null;
        if (mecha == null || !mecha.isAlive() || !pad().contains(mecha.position())) {
            end();
            return;
        }
        if (mecha.isVehicle()) {
            // boarded: the build is skipped (the frame boots), a take-apart or a repair pass called off; the arm folds home
            if (job == JOB_BUILD) foldAt = now;
            end();
            return;
        }
        // the turntable carries it: the server's yaw is the plan's, every tick
        float yaw = yawFor(turntable(now));
        Vec3 deck = deck();
        mecha.setPos(deck.x, deck.y, deck.z);
        mecha.setDeltaMovement(Vec3.ZERO);
        mecha.setYRot(yaw);
        mecha.yRotO = yaw;
        mecha.setYBodyRot(yaw);
        mecha.setYHeadRot(yaw);
        if (tau < jobLength()) return;
        if (job == JOB_DISASSEMBLE) {
            ServerPlayer player = jobPlayer == null ? null : level.getServer().getPlayerList().getPlayer(jobPlayer);
            takeApart(mecha, player);
        }
        end();
    }

    private void end() {
        job = JOB_NONE;
        jobMecha = null;
        jobPlayer = null;
        setChanged();
    }

    private boolean busy() {
        // a take-apart or a repair pass whose frame has gone (driven off, broken, taken) is over now, not on the next tick
        if ((job == JOB_DISASSEMBLE || job == JOB_REPAIR) && level instanceof ServerLevel server
                && !(jobMecha != null && server.getEntity(jobMecha) instanceof MechaEntity m && m.isAlive() && !m.isVehicle() && pad().contains(m.position()))) {
            end();
        }
        return job != JOB_NONE || pending != null;
    }

    /** Whether a mecha with someone aboard stands on the pad (it can't be worked on until they get out). */
    private boolean padOccupied() {
        return level != null && !level.getEntitiesOfClass(MechaEntity.class, pad(), MechaEntity::isVehicle).isEmpty();
    }

    /** "Pad empty", or "someone's aboard" when the only mecha there has a pilot or passenger. */
    private boolean noMecha(ServerPlayer player) {
        return say(player, padOccupied() ? "pad_occupied" : "pad_empty");
    }

    public boolean button(ServerPlayer player, int id) {
        MechaFrame frame = frame();
        boolean jobButton = id == AssemblyGantryMenu.ASSEMBLE || id == AssemblyGantryMenu.DISASSEMBLE || id == AssemblyGantryMenu.REPAIR;
        if (jobButton && busy()) return say(player, "busy");
        switch (id) {
            case AssemblyGantryMenu.ASSEMBLE -> {
                if (frame == null) return say(player, "no_frame");
                if (onPad() != null || padOccupied()) return say(player, "pad_busy");
                if (!com.coolfreeze23.headinajar.mecha.MechaLogic.walks(frame, this::fitted)) {
                    Object[] legs = com.coolfreeze23.headinajar.mecha.MechaLogic.WALKING_LEGS.stream()
                            .map(leg -> Component.translatable(ModRegistry.mechaModule(leg).getDescriptionId())).toArray();
                    return com.coolfreeze23.headinajar.feedback.Refusals.deny(player, com.coolfreeze23.headinajar.feedback.Refuser.at(this), "gantry.no_legs", legs);
                }
                if (energy.getEnergyStored() < ASSEMBLE_COST) return say(player, "no_power");
                MechaEntity mecha = ModRegistry.mechaType(frame).create(level);
                if (mecha == null) return say(player, "failed");
                Vec3 deck = deck();
                mecha.moveTo(deck.x, deck.y, deck.z, frontYaw(), 0);
                // the frame's box over the deck must be clear of blocks (it would stand inside them)
                if (!level.noBlockCollision(mecha, mecha.getBoundingBox().deflate(0.02))) return say(player, "pad_blocked");
                List<ItemStack> modules = new ArrayList<>();
                for (int i = 0; i < frame.totalSlots(); i++) modules.add(items.extractItem(FIRST_MODULE + i, 1, false));
                mecha.setModules(modules);
                MechaFrameItem.genes(items.getStackInSlot(SLOT_FRAME)).ifPresent(g -> mecha.getData(ModRegistry.GENES).copyFrom(g));
                mecha.setOwner(player.getUUID());
                // the new battery holds what the Gantry has left after the assembly (as much as it takes), and the Gantry
                // keeps the rest; the pad goes on charging it from there
                int left = energy.getEnergyStored() - ASSEMBLE_COST;
                int given = Math.max(0, Math.min(left, mecha.batteryCapacity()));
                mecha.setBattery(given);
                items.extractItem(SLOT_FRAME, 1, false);
                energy.set(left - given);
                // the arm stands off first; the frame stands on the deck LEAD ticks from now and ends facing its owner
                pending = mecha;
                startJob(JOB_BUILD, frame, bearing(player), player);
                com.coolfreeze23.headinajar.Advance.grant(player, "assembled");
                // built over its load lock it stands but cannot move: said once, as it is handed over
                if (mecha.load() > com.coolfreeze23.headinajar.mecha.MechaLogic.LOAD_LOCK) {
                    com.coolfreeze23.headinajar.feedback.Warnings.warn(player, 1, "gantry.over_lock", mecha.mass(), frame.load);
                }
                if (frame == MechaFrame.EXECUTIVE) com.coolfreeze23.headinajar.Advance.grant(player, "chairman");
                level.playSound(null, worldPosition, com.coolfreeze23.headinajar.registry.ModSounds.MECHA_BOOT.get(), SoundSource.BLOCKS, 1, 1);
                return true;
            }
            case AssemblyGantryMenu.DISASSEMBLE -> {
                MechaEntity mecha = onPad();
                if (mecha == null) return noMecha(player);
                if (!mecha.isOwnedBy(player)) return say(player, "not_yours");
                if (!slotsFree()) return say(player, "slots_busy");
                // the arm takes it apart over its clip; the slots fill at the end
                startJob(JOB_DISASSEMBLE, mecha.frame(), standing(mecha), player);
                jobMecha = mecha.getUUID();
                return true;
            }
            case AssemblyGantryMenu.REPAIR -> {
                MechaEntity mecha = onPad();
                if (mecha == null) return noMecha(player);
                if (!mecha.isOwnedBy(player)) return say(player, "not_yours");
                boolean hurt = mecha.getHealth() < mecha.getMaxHealth();
                ItemStack plating = items.getStackInSlot(SLOT_PLATING);
                int room = mecha.batteryCapacity() - mecha.battery();
                // nothing to mend with, or nothing to do: said, rather than an anvil's clang for nothing
                if (!hurt && (room <= 0 || energy.getEnergyStored() <= 0)) return say(player, "nothing_to_repair", mecha.getDisplayName());
                boolean mended = false;
                while (!plating.isEmpty() && mecha.getHealth() < mecha.getMaxHealth()) {
                    plating.shrink(1);
                    mended = true;
                    // smiths on staff make each plate go a third further
                    mecha.heal(20 / (1 - 0.25f * HeadJobs.staff(level, worldPosition, "armorer", "weaponsmith", "toolsmith")));
                }
                if (mended) setChanged();
                // the pad charges a mecha standing on it all the time; Repair also tops it up at once, honestly:
                // the charge moves from the Gantry into the battery, what the mecha gains, the Gantry loses
                int moved = Math.max(0, Math.min(energy.getEnergyStored(), room));
                mecha.setBattery(mecha.battery() + moved);
                energy.set(energy.getEnergyStored() - moved);
                Component plates = Component.translatable(ModRegistry.MECHA_PLATING.get().getDescriptionId());
                if (!mended && moved <= 0) return hurt ? say(player, "no_plating", plates) : say(player, "nothing_to_repair", mecha.getDisplayName());
                // the logic is done; the arm's weld pass and the charge lead show it
                startJob(JOB_REPAIR, mecha.frame(), standing(mecha), player);
                jobMecha = mecha.getUUID();
                level.playSound(null, worldPosition, com.coolfreeze23.headinajar.fx.HouseSounds.GANTRY_PATCH.get(), SoundSource.BLOCKS, 1, com.coolfreeze23.headinajar.fx.HouseSounds.pitch(level.random));
                // charged but not mended: the battery was topped up, but the dents stay: say what would mend them
                if (hurt && !mended) return say(player, "no_plating", plates);
                return true;
            }
            case AssemblyGantryMenu.PREV, AssemblyGantryMenu.NEXT -> {
                int n = frame == null ? MODULE_SLOTS : frame.totalSlots();
                selected = Math.floorMod(selected + (id == AssemblyGantryMenu.NEXT ? 1 : -1), n);
                return true;
            }
            case AssemblyGantryMenu.MINUS, AssemblyGantryMenu.PLUS -> {
                ItemStack stack = items.getStackInSlot(FIRST_MODULE + selected);
                if (!(stack.getItem() instanceof MechaModuleItem module)) return say(player, "slot_empty");
                if (module.type().slider == null) return say(player, "no_slider", stack.getHoverName());
                MechaModuleItem.setSlider(stack, MechaModuleItem.slider(stack) + (id == AssemblyGantryMenu.PLUS ? 10 : -10));
                setChanged();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean slotsFree() {
        if (frame() != null) return false;
        for (int i = 0; i < MODULE_SLOTS; i++) if (!items.getStackInSlot(FIRST_MODULE + i).isEmpty()) return false;
        return true;
    }

    /** The end of a take-apart: the frame and its modules into the slots, its charge back into the buffer, its cargo on the ground. */
    private void takeApart(MechaEntity mecha, ServerPlayer player) {
        if (!slotsFree()) {
            if (player != null) say(player, "slots_busy");
            return;
        }
        ItemStack frameStack = new ItemStack(ModRegistry.frameItem(mecha.frame()));
        MechaFrameItem.setGenes(frameStack, mecha.getData(ModRegistry.GENES));
        items.setStackInSlot(SLOT_FRAME, frameStack);
        for (int i = 0; i < mecha.modules().size(); i++) items.setStackInSlot(FIRST_MODULE + i, mecha.modules().get(i).copy());
        // the battery's charge comes back into the Gantry, as much as it has room for
        energy.set(energy.getEnergyStored() + mecha.battery());
        for (int i = 0; i < mecha.cargo().getContainerSize(); i++) net.minecraft.world.Containers.dropItemStack(level,
                worldPosition.getX(), worldPosition.getY() + 1, worldPosition.getZ(), mecha.cargo().removeItemNoUpdate(i));
        mecha.discard();
        level.playSound(null, worldPosition, com.coolfreeze23.headinajar.fx.HouseSounds.GANTRY_STRIP.get(), SoundSource.BLOCKS, 1, 0.8f);
    }

    private void startJob(int kind, MechaFrame frame, float angle, ServerPlayer player) {
        job = kind;
        jobStart = level.getGameTime();
        jobFrame = frame;
        jobAngle = angle;
        jobPlayer = player.getUUID();
        setChanged();
        kit.sync();
    }

    /** The turntable angle at which the frame faces this player from the deck. */
    private float bearing(Player player) {
        Vec3 deck = deck();
        double dx = player.getX() - deck.x, dz = player.getZ() - deck.z;
        float toPlayer = dx * dx + dz * dz < 1e-4 ? player.getYRot() + 180 : (float) Math.toDegrees(Math.atan2(-dx, dz));
        return Mth.wrapDegrees(frontYaw() - toPlayer);
    }

    /** The turntable angle a parked mecha stands at now. */
    private float standing(MechaEntity mecha) {
        return Mth.wrapDegrees(frontYaw() - mecha.getYRot());
    }

    /** Whether a module of this kind sits in one of the frame's slots. */
    private boolean fitted(MechaModule module) {
        MechaFrame frame = frame();
        if (frame == null) return false;
        for (int i = 0; i < frame.totalSlots(); i++) {
            if (items.getStackInSlot(FIRST_MODULE + i).getItem() instanceof MechaModuleItem item && item.type() == module) return true;
        }
        return false;
    }

    /** A refusal from the Gantry (drawn over its screen, where the action bar can't be seen). */
    private boolean say(ServerPlayer player, String reason, Object... args) {
        return com.coolfreeze23.headinajar.feedback.Refusals.deny(player, com.coolfreeze23.headinajar.feedback.Refuser.at(this), "gantry." + reason, args);
    }

    // --- the kit: what a client draws ---------------------------------------------------------------------------

    @Override
    public void writeSync(CompoundTag tag) {
        // never empty: an empty update is skipped by the client
        tag.putBoolean("Synced", true);
        if (job != JOB_NONE && jobFrame != null) {
            tag.putByte("job", (byte) job);
            tag.putLong("start", jobStart);
            tag.putByte("frame", (byte) jobFrame.ordinal());
            tag.putFloat("angle", jobAngle);
        }
        if (foldAt > Long.MIN_VALUE / 2) tag.putLong("fold", foldAt);
        if (loaded) tag.putBoolean("loaded", true);
        if (charging) tag.putBoolean("charging", true);
        ItemStack frameStack = items.getStackInSlot(SLOT_FRAME);
        if (!frameStack.isEmpty()) tag.putString("f", BuiltInRegistries.ITEM.getKey(frameStack.getItem()).toString());
        for (int i = 0; i < 3; i++) {
            ItemStack m = items.getStackInSlot(FIRST_MODULE + i);
            if (!m.isEmpty()) tag.putString("m" + i, BuiltInRegistries.ITEM.getKey(m.getItem()).toString());
        }
    }

    @Override
    public void readSync(CompoundTag tag) {
        job = tag.getByte("job");
        jobStart = tag.getLong("start");
        int f = tag.contains("frame") ? tag.getByte("frame") : -1;
        jobFrame = f >= 0 && f < MechaFrame.values().length ? MechaFrame.values()[f] : null;
        jobAngle = tag.getFloat("angle");
        foldAt = tag.contains("fold") ? tag.getLong("fold") : Long.MIN_VALUE / 2;
        loaded = tag.getBoolean("loaded");
        charging = tag.getBoolean("charging");
        shownFrame = item(tag.getString("f"));
        for (int i = 0; i < 3; i++) shownModules[i] = item(tag.getString("m" + i));
    }

    private static Item item(String id) {
        if (id.isEmpty()) return Items.AIR;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? Items.AIR : BuiltInRegistries.ITEM.get(rl);
    }

    /** Client, once a tick: the reel turns while the charge lead is out; with no job the turntable follows the frame on it. */
    @Override
    public void clientTick() {
        reelAnglePrev = reelAngle;
        idleAnglePrev = idleAngle;
        if (charging || job == JOB_REPAIR) reelAngle += 9;
        if (reelAngle >= 360) {
            reelAngle -= 360;
            reelAnglePrev -= 360;
        }
        MechaEntity m = onPad();
        if (job != JOB_NONE && m != null && level != null) {
            // the frame's own yaw on this client follows the turntable too, so when the job ends it is already where the
            // server left it (the yaw the server sends arrives eased and late, and the body would swing round to it)
            float yaw = yawFor(turntable(level.getGameTime()));
            m.setYRot(yaw);
            m.yRotO = yaw;
            m.setYBodyRot(yaw);
            m.yBodyRotO = yaw;
            m.setYHeadRot(yaw);
            m.yHeadRotO = yaw;
        }
        // with no job the turntable stands where the frame on it stands, or eases back to its rest
        float want = m != null ? Mth.wrapDegrees(frontYaw() - m.getYRot()) : 0;
        float d = Mth.wrapDegrees(want - idleAngle);
        idleAngle = Math.abs(d) < 0.5f ? want : idleAngle + d * (m != null ? 1 : 0.15f);
        if (Math.abs(idleAngle - idleAnglePrev) > 90) idleAnglePrev = idleAngle;
    }

    // --- saved data ---------------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Energy", energy.serializeNBT(registries));
        tag.put("Items", items.serializeNBT(registries));
        // a frame built but not yet stood on the pad keeps its modules: it stands as soon as the Gantry ticks again
        if (pending != null) {
            CompoundTag e = new CompoundTag();
            if (pending.saveAsPassenger(e)) tag.put("PendingBuild", e);
        } else if (pendingSave != null) {
            tag.put("PendingBuild", pendingSave);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Energy")) energy.deserializeNBT(registries, tag.get("Energy"));
        // older Gantries had fewer module slots: read into a handler of the saved size, then copy across
        CompoundTag saved = tag.getCompound("Items");
        ItemStackHandler old = new ItemStackHandler();
        old.deserializeNBT(registries, saved);
        for (int i = 0; i < items.getSlots(); i++) items.setStackInSlot(i, i < old.getSlots() ? old.getStackInSlot(i) : ItemStack.EMPTY);
        pendingSave = tag.contains("PendingBuild") ? tag.getCompound("PendingBuild") : null;
    }

    /** Only what a client draws (never the save). */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return kit.syncTag();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        kit.read(tag);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        kit.read(packet.getTag());
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        return kit.triggerEvent(id, param);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.headinajar.assembly_gantry");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        kit.startOpen(player);
        return new AssemblyGantryMenu(id, inventory, items, data, worldPosition);
    }

    public ServerLevel serverLevel() {
        return (ServerLevel) level;
    }

    // --- GeckoLib ------------------------------------------------------------------------------------------------

    private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache cache = software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
        com.coolfreeze23.headinajar.client.GantryClient.controllers(this, controllers);
    }

    @Override
    public software.bernie.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object blockEntity) {
        return com.coolfreeze23.headinajar.client.MachineAnimations.now(this);
    }
}
