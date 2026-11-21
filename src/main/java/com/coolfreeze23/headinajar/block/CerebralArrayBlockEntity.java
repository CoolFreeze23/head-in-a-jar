package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.menu.LabMenu;
import com.coolfreeze23.headinajar.registry.ModFluids;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * The Cerebral Array: up to nine jarred heads make FE by thinking very hard about broth (1 mB of Nutrient Broth per head per
 * second). Five or more heads hold a Board Meeting: +10% output, and now and then a vote. A Dragon Head needs the Board Room
 * Core in the tenth slot. Power goes out to every neighbour that takes FE.
 */
public class CerebralArrayBlockEntity extends LabMachine implements com.coolfreeze23.headinajar.machine.KitMachine, software.bernie.geckolib.animatable.GeoBlockEntity,
        com.coolfreeze23.headinajar.staff.Badged {
    /** One staff badge for the whole Board (-1 none), worked out on the server and synced with the kit. */
    private int badge = -1;

    @Override
    public int badge() {
        return badge;
    }

    @Override
    public void setBadge(int colour) {
        badge = colour;
    }

    /** Block event: the gavel lifts for a vote (it falls ten ticks later, as the vote is taken); param ayes | nays << 4 | motion << 8. */
    public static final int EV_VOTE = com.coolfreeze23.headinajar.machine.MachineKit.EV_OWN;
    private final com.coolfreeze23.headinajar.machine.MachineKit kit = new com.coolfreeze23.headinajar.machine.MachineKit(this);
    private boolean fedShown;
    /** A vote announced at the gavel's lift and taken at its strike: its tick and its numbers. */
    private long voteAt = -1;
    private int voteAyes, voteMotion;
    public static final int HEAD_SLOTS = 9, SLOT_CORE = 9, TANK = 8000, MEETING = 5, VOTE_TICKS = 6000;
    private static final String[] MOTIONS = {"broth", "lunch", "windows", "chair", "union", "dental", "merger", "picnic"};
    private final FluidTank broth = new FluidTank(TANK, f -> f.getFluid().isSame(ModFluids.BROTH.source.get())) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private int output;
    /** What the clients last saw in the head slots (the rack is drawn from it). */
    private int shownHeads = -1;
    private final HeadInfo.Cache[] info = HeadInfo.caches(HEAD_SLOTS);
    private static final MachineItems.Role[] ROLES = MachineItems.all(HEAD_SLOTS + 1, MachineItems.Role.HAND);

    public CerebralArrayBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.ARRAY_BE.get(), pos, state, LabMenu.Kind.CEREBRAL_ARRAY, 500_000, 20_000);
    }

    @Override
    protected boolean valid(int slot, ItemStack stack) {
        if (slot == SLOT_CORE) return stack.is(ModRegistry.BOARD_ROOM_CORE.get());
        return HeadJobs.type(stack) != null || stack.is(Items.DRAGON_HEAD);
    }

    /** Heads and the Board Room Core go in by hand only. */
    @Override
    public MachineItems.Role[] roles() {
        return ROLES;
    }

    /** What the head in slot {@code i} is (cached until the slot changes). */
    public HeadInfo headInfo(int i) {
        return info[i].get(items.getStackInSlot(i));
    }

    @Override
    protected int value(int i) {
        return switch (i) {
            case 2 -> broth.getFluidAmount();
            case 3 -> heads();
            default -> 0;
        };
    }

    public IFluidHandler broth() {
        return broth;
    }

    public int heads() {
        int n = 0;
        for (int i = 0; i < HEAD_SLOTS; i++) if (!items.getStackInSlot(i).isEmpty()) n++;
        return n;
    }

    /**
     * FE/t right now, before broth: each head's output, +10% at a Board Meeting. The first
     * {@code staff.promotedArrayCap} heads promoted to the Board (in slot order) make {@code staff.promotedArrayBonus}
     * percent of their species' rate, rounded; every other head exactly its rate. A promoted head is one head of five.
     */
    public int potential() {
        boolean boardRoom = !items.getStackInSlot(SLOT_CORE).isEmpty();
        int sum = 0, working = 0;
        boolean dragon = false;
        int promotedLeft = com.coolfreeze23.headinajar.Config.get(com.coolfreeze23.headinajar.Config.PROMOTED_ARRAY_CAP, 3);
        int bonus = com.coolfreeze23.headinajar.Config.get(com.coolfreeze23.headinajar.Config.PROMOTED_ARRAY_BONUS, 150);
        for (int i = 0; i < HEAD_SLOTS; i++) {
            ItemStack head = items.getStackInSlot(i);
            if (head.isEmpty()) continue;
            if (head.is(Items.DRAGON_HEAD)) {
                // one chair at the head of the table: a second Dragon Head sits in and makes nothing
                if (dragon) continue;
                dragon = true;
                sum += boardRoom ? HeadJobs.DRAGON_HEAD : 0;
            } else {
                HeadInfo info = headInfo(i);
                int rate = HeadJobs.arrayOutput(info.type());
                if (info.promoted() && promotedLeft > 0) {
                    promotedLeft--;
                    rate = promotedRate(rate, bonus);
                }
                sum += rate;
            }
            working++;
        }
        return working >= MEETING ? Math.round(sum * 1.1f) : sum;
    }

    /** A promoted head's rate: its species' rate times the bonus in percent, rounded half up (cow 10 at 150: 15). */
    public static int promotedRate(int rate, int bonusPercent) {
        return Math.round(rate * bonusPercent / 100f);
    }

    /** Broth owed to the next draw (a fraction of a mB, from a cow head's saving; not saved). */
    private float brothCarry;

    public int output() {
        return output;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CerebralArrayBlockEntity a) {
        int heads = a.heads();
        // broth: 1 mB per head per second (a paid Farmer halves it)
        if (heads > 0 && level.getGameTime() % 20 == 0) {
            float farmer = HeadJobs.staff(level, pos, "farmer");
            int need = Math.max(1, Math.round(heads * (1 - 0.5f * farmer)));
            if (DeskJobs.cowNear(level, pos)) {
                // a fed cow head within 8 makes it last a tenth longer: whole mB drawn, the rest carried (not saved)
                float want = heads * (1 - 0.5f * farmer) / 1.1f + a.brothCarry;
                need = (int) Math.floor(want);
                a.brothCarry = want - need;
            } else {
                a.brothCarry = 0;
            }
            FluidStack got = need > 0 ? a.broth.drain(need, IFluidHandler.FluidAction.EXECUTE) : FluidStack.EMPTY;
            a.getPersistentData().putBoolean("Fed", need > 0 ? got.getAmount() >= need : a.broth.getFluidAmount() > 0);
        }
        boolean fed = a.getPersistentData().getBoolean("Fed");
        a.output = fed ? a.potential() : 0;
        a.progress = a.output;
        if (a.output > 0) a.energy.set(a.energy.getEnergyStored() + a.output);
        a.push(level, pos);
        if (level.getGameTime() % 10 == 0) {
            int signature = 0;
            // cheap: what is drawn is the creature in each slot, not the whole stored mob
            for (int i = 0; i <= SLOT_CORE; i++) {
                ItemStack stack = a.items.getStackInSlot(i);
                var type = i < HEAD_SLOTS ? a.headInfo(i).type() : null;
                signature = signature * 31 + (stack.isEmpty() ? 0 : stack.getItem().hashCode() * 7 + (type == null ? 0 : type.hashCode()) + stack.getCount());
            }
            if (signature != a.shownHeads) {
                a.shownHeads = signature;
                level.sendBlockUpdated(pos, state, state, 3);
            }
        }
        // the gavel lifts ten ticks before the vote is taken (the strike lands on it)
        if (heads >= MEETING && fed && (level.getGameTime() + 10) % VOTE_TICKS == Math.floorMod(pos.asLong(), (long) VOTE_TICKS)) a.callVote(level);
        if (a.voteAt >= 0 && level.getGameTime() >= a.voteAt) a.vote(level);
        if (fed && level.getGameTime() % 100 == 0) {
            if (heads >= MEETING) com.coolfreeze23.headinajar.Advance.nearby(level, pos.getCenter(), 16, "board_meeting");
            if (a.output >= 500) com.coolfreeze23.headinajar.Advance.nearby(level, pos.getCenter(), 16, "brain_trust");
        }
    }

    /** Shares FE among the neighbours that take it. */
    private void push(Level level, BlockPos pos) {
        PowerBlockEntities.share(level, pos, energy, 20_000);
    }

    /** The motion and its count, decided as the gavel lifts; the cards show them as it falls. Its mover votes aye. */
    private void callVote(Level level) {
        callVote(level, 1 + level.random.nextInt(heads()), level.random.nextInt(MOTIONS.length));
    }

    /** Lifts the gavel on motion {@code motion} with {@code ayes} for it and the other heads against; taken ten ticks on. */
    public void callVote(Level level, int ayes, int motion) {
        voteAyes = ayes;
        voteMotion = motion;
        voteAt = level.getGameTime() + 10;
        int nays = heads() - voteAyes;
        kit.event(EV_VOTE, voteAyes | nays << 4 | voteMotion << 8);
    }

    /** A motion carries with more ayes than nays; a tie does not carry. */
    public static boolean carried(int ayes, int nays) {
        return ayes > nays;
    }

    private void vote(Level level) {
        voteAt = -1;
        int ayes = Math.min(voteAyes, heads()), nays = heads() - ayes;
        // 0.7.2: "unionise", honestly carried, walks out every site within 48 that already meets the strike test
        boolean walkout = carried(ayes, nays) && "union".equals(MOTIONS[voteMotion]) && level instanceof net.minecraft.server.level.ServerLevel server
                && com.coolfreeze23.headinajar.staff.Strikes.boardCarried(server, worldPosition, ayes, nays) > 0;
        Component line = Component.translatable(walkout ? "array.headinajar.vote_walkout" : carried(ayes, nays) ? "array.headinajar.vote" : "array.headinajar.vote_lost",
                Component.translatable("array.headinajar.motion." + MOTIONS[voteMotion]), ayes, nays);
        for (Player p : level.getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(worldPosition).inflate(32))) p.displayClientMessage(line, false);
        // the gavel's strike and the room's murmur
        level.playSound(null, worldPosition, com.coolfreeze23.headinajar.machine.MachineSounds.ARRAY_GAVEL.get(), net.minecraft.sounds.SoundSource.BLOCKS, 1, 1);
        level.playSound(null, worldPosition, com.coolfreeze23.headinajar.machine.MachineSounds.ARRAY_MURMUR.get(), net.minecraft.sounds.SoundSource.BLOCKS, 1, 1);
    }

    /** The server's tick, then what the stand shows: the lamp, the urn's broth, whether the heads are fed. */
    public static void kitTick(Level level, BlockPos pos, BlockState state, CerebralArrayBlockEntity a) {
        serverTick(level, pos, state, a);
        com.coolfreeze23.headinajar.staff.StaffBlocks.stationTick(a, a.items, HEAD_SLOTS, null);
        boolean fed = a.getPersistentData().getBoolean("Fed");
        int heads = a.heads();
        a.fedShown = fed && heads > 0;
        com.coolfreeze23.headinajar.machine.MachineLamp lamp;
        if (heads == 0) lamp = com.coolfreeze23.headinajar.machine.MachineLamp.OFF;
        else if (!fed) lamp = com.coolfreeze23.headinajar.machine.MachineLamp.WAIT;
        else if (a.energy.getEnergyStored() >= a.energy.getMaxEnergyStored()) lamp = com.coolfreeze23.headinajar.machine.MachineLamp.READY;
        else lamp = com.coolfreeze23.headinajar.machine.MachineLamp.WORK;
        a.kit.level(CerebralArrayBlock.BROTH, com.coolfreeze23.headinajar.machine.MachineLamp.step(a.broth.getFluidAmount(), TANK));
        a.kit.tick(lamp, a.output > 0, a.output > 0, 0);
    }

    @Override
    public com.coolfreeze23.headinajar.machine.MachineKit kit() {
        return kit;
    }

    /** The heads are fed (upright; unfed they sag). */
    public boolean fed() {
        return fedShown;
    }

    @Override
    public void writeSync(CompoundTag tag) {
        if (level != null) tag.put("Items", HeadInfo.syncTag(items, level.registryAccess()));
        if (fedShown) tag.putBoolean("fed", true);
        if (badge >= 0) tag.putInt("Badge", badge);
    }

    @Override
    public void readSync(CompoundTag tag) {
        if (level != null && tag.contains("Items")) MachineItems.load(items, tag.getCompound("Items"), level.registryAccess());
        fedShown = tag.getBoolean("fed");
        badge = tag.contains("Badge") ? tag.getInt("Badge") : -1;
    }

    @Override
    public void clientTick() {
        // the broth urn steams faintly while the heads are fed
        com.coolfreeze23.headinajar.client.MachineLoopSound.follow(this, com.coolfreeze23.headinajar.machine.MachineSounds.ARRAY_STEAM, fedShown ? 1 : 0, 1);
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
    public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider registries) {
        kit.read(packet.getTag());
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        return kit.triggerEvent(id, param);
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inventory,
                                                                         net.minecraft.world.entity.player.Player player) {
        kit.startOpen(player);
        return super.createMenu(id, inventory, player);
    }

    private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache cache = software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
        com.coolfreeze23.headinajar.client.MachineAnimations3.array(this, controllers);
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
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Broth", broth.writeToNBT(registries, new CompoundTag()));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        broth.readFromNBT(registries, tag.getCompound("Broth"));
    }
}
