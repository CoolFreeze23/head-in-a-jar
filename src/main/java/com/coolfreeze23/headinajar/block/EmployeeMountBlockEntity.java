package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * An Employee Mount: a bracket on a machine's side holding a Jarred Villager, who works there for 1 emerald a day from a
 * chest touching the mount. Unpaid staff work at half bonus and complain; nitwits do nothing and say so.
 */
public class EmployeeMountBlockEntity extends BlockEntity implements HasItems, HeadHolderBlock.Holder, com.coolfreeze23.headinajar.machine.KitMachine,
        software.bernie.geckolib.animatable.GeoBlockEntity, com.coolfreeze23.headinajar.staff.Badged {
    /** The staff badge's colour (-1 none), worked out on the server and synced with the kit. */
    private int badge = -1;
    /** Days paid running (saved only once known; an old mount reads 0 until its second paid day). Read for the band only. */
    private int paidStreak;

    @Override
    public int badge() {
        return badge;
    }

    @Override
    public void setBadge(int colour) {
        badge = colour;
    }

    /**
     * The villager's band, read from its pay record and nothing else: paid today and yesterday Engaged, paid today
     * Content, unpaid one or two days Disengaged, three or more Grievance (the days it already faces the wall).
     */
    public com.coolfreeze23.headinajar.staff.Band band() {
        if (head().isEmpty()) return com.coolfreeze23.headinajar.staff.Band.NONE;
        if (!com.coolfreeze23.headinajar.Config.get(com.coolfreeze23.headinajar.Config.MORALE, true)) return com.coolfreeze23.headinajar.staff.Band.CONTENT;
        if (paid()) return paidStreak >= 2 ? com.coolfreeze23.headinajar.staff.Band.ENGAGED : com.coolfreeze23.headinajar.staff.Band.CONTENT;
        return daysMissed() >= 3 ? com.coolfreeze23.headinajar.staff.Band.GRIEVANCE : com.coolfreeze23.headinajar.staff.Band.DISENGAGED;
    }

    /** Days paid running (tests). */
    public int paidStreak() {
        return paidStreak;
    }

    /**
     * 0.7.2: a strike on the site it stands in: it faces the wall and its complaints are the picket's (its pay, bonus and
     * their cadence unchanged). Not saved: looked up again with its payroll, every 100 ticks; synced.
     */
    private boolean solidarity;

    public boolean solidarity() {
        return solidarity;
    }
    /** Block events: a complaint (param: days unpaid), payday (param: days it had gone unpaid). */
    public static final int EV_COMPLAINT = com.coolfreeze23.headinajar.machine.MachineKit.EV_OWN, EV_PAYDAY = EV_COMPLAINT + 1;
    private final com.coolfreeze23.headinajar.machine.MachineKit kit = new com.coolfreeze23.headinajar.machine.MachineKit(this);
    public static final int COMPLAINT_TICKS = 2400;
    private static final String[] COMPLAINTS = {"dental", "overtime", "jar", "union", "view", "smell", "pension", "broth", "chair", "manager"};
    private final ItemStackHandler items = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return HeadJobs.type(stack) == EntityType.VILLAGER;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    };
    private long paidThrough = -1;
    /** 0.7.2: its Grievance (three days unpaid) has been filed: the history line, the stamp and the memo, once. */
    private boolean grievance;
    private final HeadInfo.Cache info = new HeadInfo.Cache();

    public EmployeeMountBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.MOUNT_BE.get(), pos, state);
    }

    @Override
    public ItemStackHandler items() {
        return items;
    }

    @Override
    public ItemStack donorHead() {
        return head();
    }

    public ItemStack head() {
        return items.getStackInSlot(0);
    }

    /** What the head is (cached until the slot changes). */
    public HeadInfo headInfo() {
        return info.get(head());
    }

    /** The villager's profession, or "" (cached until the slot changes). */
    public String profession() {
        return headInfo().profession();
    }

    /** The machine this mount is bolted to: the block behind it (as placed facing the machine), or the one under it. */
    public boolean facesInto(BlockPos machine) {
        Direction facing = getBlockState().getValue(MachineBlock.FACING);
        return worldPosition.relative(facing.getOpposite()).equals(machine) || worldPosition.below().equals(machine);
    }

    private long day() {
        return level == null ? 0 : level.getDayTime() / 24000L;
    }

    public boolean paid() {
        return !head().isEmpty() && !"nitwit".equals(profession()) && paidThrough >= day();
    }

    /** Takes today's emerald from any container touching the mount (never the machine itself). Returns whether it paid. */
    public boolean payroll() {
        if (level == null || head().isEmpty() || paidThrough >= day()) return paidThrough >= day();
        // a nitwit is never on the payroll (it always counts as unpaid), so it is never charged for it either
        if ("nitwit".equals(profession())) return false;
        for (Direction d : Direction.values()) {
            BlockPos at = worldPosition.relative(d);
            if (facesInto(at)) continue;
            var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, at, d.getOpposite());
            if (handler == null) continue;
            for (int i = 0; i < handler.getSlots(); i++) {
                if (handler.getStackInSlot(i).is(Items.EMERALD) && !handler.extractItem(i, 1, false).isEmpty()) {
                    long missed = Math.max(0, day() - 1 - paidThrough);
                    if (paidThrough >= 0 || missed > 0) kit.event(EV_PAYDAY, (int) Math.min(255, missed));
                    paidStreak = missed == 0 && paidThrough >= 0 ? paidStreak + 1 : 1;
                    paidThrough = day();
                    setChanged();
                    return true;
                }
            }
        }
        return false;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, EmployeeMountBlockEntity mount) {
        if (mount.head().isEmpty()) return;
        if (level.getGameTime() % 100 == 0) {
            mount.payroll();
            mount.grievanceBookkeeping();
            boolean s = com.coolfreeze23.headinajar.staff.Strikes.solidarity(level, pos);
            if (s != mount.solidarity) {
                mount.solidarity = s;
                level.sendBlockUpdated(pos, state, state, 3);
            }
        }
        // a block at negative x has a negative long: its slot in the cycle is counted from zero up, like every other block's
        if (level.getGameTime() % COMPLAINT_TICKS == Math.floorMod(pos.asLong(), (long) COMPLAINT_TICKS) && !mount.paid()) mount.complain();
    }

    /**
     * 0.7.2: the Grievance read from its pay record (three days unpaid; a Nitwit is never on the payroll and files none)
     * is filed once on the head's file (the history line, the stamp and the sigh, the memo), and withdrawn when it is
     * paid again ("Grievance withdrawn. Form 66-G has been shredded.", to players within 12, as its complaints are).
     * Nothing it makes or says otherwise changes.
     */
    public void grievanceBookkeeping() {
        if (level == null || head().isEmpty()) return;
        boolean now = !"nitwit".equals(profession()) && band() == com.coolfreeze23.headinajar.staff.Band.GRIEVANCE;
        if (now == grievance) return;
        grievance = now;
        setChanged();
        com.coolfreeze23.headinajar.staff.StaffFile f = com.coolfreeze23.headinajar.staff.Staff.of(head());
        long d = day();
        if (now) {
            if (f != null) {
                f.career.grievancesSinceReview++;
                f.note("grievance", d, "");
                com.coolfreeze23.headinajar.staff.Staff.write(head(), f);
                ServerPlayer employer = f.employer == null || level.getServer() == null ? null : level.getServer().getPlayerList().getPlayer(f.employer);
                if (employer != null) com.coolfreeze23.headinajar.clarity.Memos.send(employer, com.coolfreeze23.headinajar.clarity.Memos.Memo.GRIEVANCE);
            }
            level.playSound(null, worldPosition, com.coolfreeze23.headinajar.registry.ModSounds.STAFF_GRIEVANCE.get(), SoundSource.BLOCKS, 1, 1);
        } else {
            if (f != null) {
                f.note("grievance_withdrawn", d, "");
                com.coolfreeze23.headinajar.staff.Staff.write(head(), f);
            }
            Component line = Component.translatable("staff.headinajar.says", Component.translatable("entity.minecraft.villager." + profession()),
                    Component.translatable("staff.headinajar.say.grievance_withdrawn"));
            for (Player p : level.getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(worldPosition).inflate(12))) p.displayClientMessage(line, false);
        }
    }

    /** 0.7.2: whether its Grievance is on file (tests). */
    public boolean grievanceFiled() {
        return grievance;
    }

    private void complain() {
        String job = profession();
        String key = "nitwit".equals(job) ? "staff.headinajar.nitwit." + level.random.nextInt(4)
                : "staff.headinajar.complaint." + COMPLAINTS[level.random.nextInt(COMPLAINTS.length)];
        // 0.7.2: in solidarity with a strike on its site (the same draw, so the cadence and the dice are unchanged)
        if (solidarity && !"nitwit".equals(job)) key = "staff.headinajar.complaint." + (key.hashCode() % 2 == 0 ? "picket" : "dental_now");
        Component line = Component.translatable("staff.headinajar.says", Component.translatable("entity.minecraft.villager." + job), Component.translatable(key));
        kit.event(EV_COMPLAINT, (int) Math.min(255, Math.max(0, day() - paidThrough)));
        for (Player p : level.getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(worldPosition).inflate(12))) p.displayClientMessage(line, false);
        level.playSound(null, worldPosition, com.coolfreeze23.headinajar.registry.ModSounds.STAFF_COMPLAIN.get(), SoundSource.BLOCKS, 0.8f, 1);
    }

    /** Use: hang a jarred villager, check on your staff, or (sneaking) take them down. */
    @Override
    public void use(ServerPlayer player, ItemStack held) {
        if (head().isEmpty()) {
            if (HeadJobs.type(held) != EntityType.VILLAGER) {
                com.coolfreeze23.headinajar.feedback.Refusals.deny(player, com.coolfreeze23.headinajar.feedback.Refuser.at(this), "mount.needs_villager", EntityType.VILLAGER.getDescription());
                return;
            }
            items.setStackInSlot(0, held.split(1));
            com.coolfreeze23.headinajar.staff.StaffBlocks.fitted(this, head(), player);
            // hired today: counted from today, not from the world's first day, if the first wage isn't there yet
            if (!payroll()) {
                paidThrough = day() - 1;
                setChanged();
            }
            com.coolfreeze23.headinajar.Advance.grant(player, "staffed");
            level.playSound(null, worldPosition, com.coolfreeze23.headinajar.fx.HouseSounds.MOUNT_WORK.get(), SoundSource.BLOCKS, 1, 1);
            return;
        }
        if (player.isShiftKeyDown()) {
            com.coolfreeze23.headinajar.form.FormActions.give(player, items.extractItem(0, 1, false));
            return;
        }
        String job = profession();
        player.displayClientMessage(Component.translatable(paid() ? "staff.headinajar.status_paid" : "staff.headinajar.status_unpaid",
                Component.translatable("entity.minecraft.villager." + job)), true);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", items.serializeNBT(registries));
        tag.putLong("PaidThrough", paidThrough);
        if (paidStreak > 0) tag.putInt("PaidStreak", paidStreak);
        if (grievance) tag.putBoolean("Grievance", true);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        MachineItems.load(items, tag.getCompound("Items"), registries);
        paidThrough = tag.contains("PaidThrough") ? tag.getLong("PaidThrough") : -1;
        paidStreak = tag.getInt("PaidStreak");
        grievance = tag.getBoolean("Grievance");
    }

    /** The server's tick, then what the cubicle shows: the lamp, the head, the day it is paid through. */
    public static void kitTick(Level level, BlockPos pos, BlockState state, EmployeeMountBlockEntity mount) {
        serverTick(level, pos, state, mount);
        com.coolfreeze23.headinajar.staff.StaffBlocks.stationTick(mount, mount.items, 1, null);
        com.coolfreeze23.headinajar.machine.MachineLamp lamp;
        if (mount.head().isEmpty()) lamp = com.coolfreeze23.headinajar.machine.MachineLamp.OFF;
        else if ("nitwit".equals(mount.profession())) lamp = com.coolfreeze23.headinajar.machine.MachineLamp.WAIT;
        else lamp = mount.paid() ? com.coolfreeze23.headinajar.machine.MachineLamp.READY : com.coolfreeze23.headinajar.machine.MachineLamp.OFF;
        mount.kit.tick(lamp, false, false, 0);
    }

    @Override
    public com.coolfreeze23.headinajar.machine.MachineKit kit() {
        return kit;
    }

    /** Days the head has gone unpaid (0 paid; the client reckons it from the synced day). */
    public long daysMissed() {
        if (level == null || head().isEmpty()) return 0;
        return Math.max(0, day() - paidThrough);
    }

    @Override
    public void writeSync(CompoundTag tag) {
        if (level != null) tag.put("Items", HeadInfo.syncTag(items, level.registryAccess()));
        tag.putLong("paid", paidThrough);
        if (badge >= 0) tag.putInt("Badge", badge);
        if (solidarity) tag.putBoolean("Strike", true);
    }

    @Override
    public void readSync(CompoundTag tag) {
        if (level != null && tag.contains("Items")) MachineItems.load(items, tag.getCompound("Items"), level.registryAccess());
        paidThrough = tag.getLong("paid");
        badge = tag.contains("Badge") ? tag.getInt("Badge") : -1;
        solidarity = tag.getBoolean("Strike");
    }

    @Override
    public void clientTick() {
        // a head left unpaid three days or more sits facing the wall and sighs now and then
        if (level == null || paid() || daysMissed() < 3 || (level.getGameTime() + (worldPosition.asLong() & 255)) % 400 != 0) return;
        if (!com.coolfreeze23.headinajar.fx.IdleBudget.take(level.getGameTime())) return;
        level.playLocalSound(worldPosition.getX() + 0.5, worldPosition.getY() + 1.2, worldPosition.getZ() + 0.5,
                com.coolfreeze23.headinajar.machine.MachineSounds.MOUNT_SIGH.get(), net.minecraft.sounds.SoundSource.BLOCKS, 1,
                0.95f + level.random.nextFloat() * 0.1f, false);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = kit.syncTag();
        tag.putBoolean("Synced", true);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        kit.read(tag);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        kit.read(packet.getTag());
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        return kit.triggerEvent(id, param);
    }

    private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache cache = software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
        com.coolfreeze23.headinajar.client.MachineAnimations3.mount(this, controllers);
    }

    @Override
    public software.bernie.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object blockEntity) {
        return com.coolfreeze23.headinajar.client.MachineAnimations.now(this);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
