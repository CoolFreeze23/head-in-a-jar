package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.entity.HeadlessBody;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.HeadRegistry;
import com.coolfreeze23.headinajar.menu.BodyPrinterMenu;
import com.coolfreeze23.headinajar.registry.ModFluids;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.UUID;

/**
 * The Body Printer: 16 rotten flesh, 8 bone blocks, 4 redstone blocks, 8 slime balls and 2000 mB Cranial Saline, then
 * 200 kFE over 60 seconds, print a fresh Headless Body (Collar Cam fitted) for the head that pressed Print.
 */
public class BodyPrinterBlockEntity extends BlockEntity implements MenuProvider, HasItems, com.coolfreeze23.headinajar.machine.KitMachine,
        software.bernie.geckolib.animatable.GeoBlockEntity {
    public static final int[] NEED = {16, 8, 4, 8};
    public static final int SALINE = 2000;
    public static final int TICKS = 1200;
    public static final int ENERGY = 200_000;
    /** What each slot holds, in order (the recipe {@link #NEED} counts). */
    private static final net.minecraft.world.item.Item[] MATERIALS = {Items.ROTTEN_FLESH, Items.BONE_BLOCK, Items.REDSTONE_BLOCK, Items.SLIME_BALL};
    public static final MachineItems.Role[] ROLES = MachineItems.all(4, MachineItems.Role.INPUT);

    private final Buffer energy = new Buffer(250_000, 5_000, 0, this::setChanged);
    private final FluidTank saline = new FluidTank(4000, f -> f.getFluid().isSame(ModFluids.SALINE.source.get())) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final ItemStackHandler items = new ItemStackHandler(4) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case 0 -> stack.is(Items.ROTTEN_FLESH);
                case 1 -> stack.is(Items.BONE_BLOCK);
                case 2 -> stack.is(Items.REDSTONE_BLOCK);
                case 3 -> stack.is(Items.SLIME_BALL);
                default -> false;
            };
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private int progress = -1;
    private UUID client;
    private String clientName = "";

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> energy.getEnergyStored() / 10;
                case 1 -> saline.getFluidAmount();
                case 2 -> Math.max(0, progress);
                case 3 -> printing() && progress < TICKS && energy.getEnergyStored() < ENERGY / TICKS ? com.coolfreeze23.headinajar.menu.MachineMenu.NO_POWER : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public BodyPrinterBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.PRINTER_BE.get(), pos, state);
    }

    // --- the machine kit: what the booth shows (the print's rule above is untouched) ---------------------------

    private final com.coolfreeze23.headinajar.machine.MachineKit kit = new com.coolfreeze23.headinajar.machine.MachineKit(this);
    /** Synced: the materials in (a bit each, flesh, bone, redstone, slime), the delivered body still in front of the door, whose skin is printed. */
    private int materials;
    private boolean held;
    private UUID skin;
    // server: the last tick's progress, the body just delivered and when
    private int lastProgress = -1;
    /** The last tick a running print went without power (its lamp says so for a second after). */
    private long starvedAt = Long.MIN_VALUE / 2;
    private UUID lastClient, deliveredBody;
    private long deliveredAt = Long.MIN_VALUE / 2;

    @Override
    public com.coolfreeze23.headinajar.machine.MachineKit kit() {
        return kit;
    }

    public int materials() {
        return materials;
    }

    /** The printed body still stands in the doorway (the door stays open over it). */
    public boolean held() {
        return held;
    }

    public UUID skin() {
        return skin;
    }

    public int progress() {
        return progress;
    }

    /** The print's tick, then what the booth shows. */
    public static void kitTick(Level level, BlockPos pos, BlockState state, BodyPrinterBlockEntity printer) {
        if (printer.printing()) printer.lastClient = printer.client;
        serverTick(level, pos, state, printer);
        printer.show(level);
    }

    /** Where the server puts the new body: one block out of the door. */
    private Vec3 doorstep() {
        return Vec3.atBottomCenterOf(worldPosition.relative(getBlockState().getValue(MachineBlock.FACING)));
    }

    private void show(Level level) {
        long now = level.getGameTime();
        boolean advanced = printing() && progress > lastProgress && lastProgress >= 0;
        // a print that stood still this tick (not its first, not one done and waiting) was short of power
        if (printing() && lastProgress >= 0 && !advanced && progress < TICKS) starvedAt = now;
        if (lastProgress >= 0 && !printing()) {
            // the print came out: the body that stepped out holds the door open while it stands in it
            kit.event(com.coolfreeze23.headinajar.machine.MachineKit.EV_FINISH, 0);
            deliveredAt = now;
            Vec3 at = doorstep();
            deliveredBody = level.getEntitiesOfClass(HeadlessBody.class, new net.minecraft.world.phys.AABB(at, at).inflate(1),
                    b -> lastClient != null && lastClient.equals(b.ownerId())).stream().findFirst().map(net.minecraft.world.entity.Entity::getUUID).orElse(null);
        }
        lastProgress = printing() ? progress : -1;
        if (deliveredBody != null && level instanceof ServerLevel server) {
            var body = server.getEntity(deliveredBody);
            held = body != null && body.isAlive() && body.position().distanceTo(doorstep()) < 0.6 && now - deliveredAt < 600;
            if (!held) deliveredBody = null;
        } else {
            held = false;
        }
        materials = 0;
        for (int i = 0; i < NEED.length; i++) if (!items.getStackInSlot(i).isEmpty()) materials |= 1 << i;
        skin = printing() ? client : held ? lastClient : null;
        boolean waiting = printing() && progress >= TICKS;
        com.coolfreeze23.headinajar.machine.MachineLamp lamp;
        if (waiting) lamp = com.coolfreeze23.headinajar.machine.MachineLamp.WAIT;
        else if (printing()) lamp = now - starvedAt < 20 ? com.coolfreeze23.headinajar.machine.MachineLamp.LOW : com.coolfreeze23.headinajar.machine.MachineLamp.WORK;
        else lamp = energy.getEnergyStored() > 0 ? com.coolfreeze23.headinajar.machine.MachineLamp.READY : com.coolfreeze23.headinajar.machine.MachineLamp.OFF;
        kit.level(BodyPrinterBlock.SALINE, com.coolfreeze23.headinajar.machine.MachineLamp.step(saline.getFluidAmount(), saline.getCapacity()));
        kit.tick(lamp, advanced, printing(), printing() ? Math.min(progress, TICKS) : 0);
    }

    @Override
    public void writeSync(CompoundTag tag) {
        if (materials != 0) tag.putByte("mats", (byte) materials);
        if (held) tag.putBoolean("held", true);
        if (skin != null) tag.putUUID("skin", skin);
        if (printing() && progress >= TICKS) tag.putBoolean("waits", true);
    }

    /** Client: the finished body waits in the vat (its owner has a body on); it is drawn standing in the open doorway. */
    private boolean waits;

    public boolean waits() {
        return waits;
    }

    @Override
    public void readSync(CompoundTag tag) {
        materials = tag.getByte("mats");
        held = tag.getBoolean("held");
        skin = tag.hasUUID("skin") ? tag.getUUID("skin") : null;
        waits = tag.getBoolean("waits");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return kit.syncTag();
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        kit.read(tag);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        kit.read(packet.getTag());
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        return kit.triggerEvent(id, param);
    }

    /** Client: the carriage's whirr while printing. */
    @Override
    public void clientTick() {
        com.coolfreeze23.headinajar.client.MachineLoopSound.follow(this, com.coolfreeze23.headinajar.machine.MachineSounds.PRINTER_WHIRR,
                kit.advancing() ? kit.rate() / 4f : 0, 0.8f + 0.05f * kit.rate());
    }

    private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache cache = software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
        com.coolfreeze23.headinajar.client.MachineAnimations.printer(this, controllers);
    }

    @Override
    public software.bernie.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object blockEntity) {
        return com.coolfreeze23.headinajar.client.MachineAnimations.now(this);
    }

    public Buffer energy() {
        return energy;
    }

    public FluidTank saline() {
        return saline;
    }

    @Override
    public ItemStackHandler items() {
        return items;
    }

    /** Mid-print, the materials are already in the vat: breaking the printer gives them back (the saline is lost with the tank). */
    @Override
    public java.util.List<ItemStack> consumed() {
        if (!printing()) return java.util.List.of();
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        for (int i = 0; i < NEED.length; i++) out.add(new ItemStack(MATERIALS[i], NEED[i]));
        return out;
    }

    public boolean printing() {
        return progress >= 0;
    }

    /** The Print button: only a decapitated player can order a body, and only with everything loaded. */
    public boolean start(ServerPlayer player) {
        var refuser = com.coolfreeze23.headinajar.feedback.Refuser.at(this);
        if (printing() && progress >= TICKS) return com.coolfreeze23.headinajar.feedback.Refusals.deny(player, refuser, "printer.waiting", clientName);
        if (printing()) return com.coolfreeze23.headinajar.feedback.Refusals.deny(player, refuser, "printer.busy", Math.max(0, (TICKS - progress) / 20));
        if (FormActions.state(player).form() != Form.JAR) return com.coolfreeze23.headinajar.feedback.Refusals.deny(player, refuser, "printer.has_head");
        for (int i = 0; i < NEED.length; i++) {
            int short_ = NEED[i] - items.getStackInSlot(i).getCount();
            if (short_ > 0) {
                return com.coolfreeze23.headinajar.feedback.Refusals.deny(player, refuser, "printer.missing", short_, Component.translatable(MATERIALS[i].getDescriptionId()));
            }
        }
        if (saline.getFluidAmount() < SALINE) {
            return com.coolfreeze23.headinajar.feedback.Refusals.deny(player, refuser, "printer.no_saline", SALINE - saline.getFluidAmount(),
                    Component.translatable("fluid_type.headinajar.cranial_saline"));
        }
        // as the chair: nothing is taken until the whole print's power is stored, so a print never stalls half done
        if (energy.getEnergyStored() < ENERGY) {
            return com.coolfreeze23.headinajar.feedback.Refusals.deny(player, refuser, "printer.no_power", ENERGY / 1000, energy.getEnergyStored() / 1000,
                    Component.translatable("block.headinajar.galvanic_pile"));
        }
        for (int i = 0; i < NEED.length; i++) items.extractItem(i, NEED[i], false);
        saline.drain(SALINE, IFluidHandler.FluidAction.EXECUTE);
        client = player.getUUID();
        clientName = player.getGameProfile().getName();
        progress = 0;
        setChanged();
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BodyPrinterBlockEntity printer) {
        if (!printer.printing()) return;
        // a print with nobody to print it for (its progress set by hand, say) is no print: the vat goes back to idle
        if (printer.client == null) {
            printer.progress = -1;
            printer.setChanged();
            return;
        }
        // a finished body waits in the vat while its owner has a body on
        if (printer.progress >= TICKS) {
            if (level.getGameTime() % 20 == 0) printer.deliver((ServerLevel) level);
            return;
        }
        int perTick = ENERGY / TICKS;
        if (printer.energy.getEnergyStored() < perTick) return;
        printer.energy.set(printer.energy.getEnergyStored() - perTick);
        printer.progress++;
        if (printer.progress % 40 == 0) level.playSound(null, pos, SoundEvents.SLIME_SQUISH, SoundSource.BLOCKS, 0.6f, 0.6f);
        if (printer.progress >= TICKS) printer.deliver((ServerLevel) level);
    }

    /** The owner told that their finished body waits, this time they had one on (not saved: told again after a reload). */
    private boolean toldWaiting;

    /**
     * The print is done. A body comes out for a head in a jar (or one away from the game, who takes it on when back as a
     * jar); for an owner who has their head on again, or is out in another body, it stays in the vat, and they are told
     * so once, until they are a jar again.
     */
    private void deliver(ServerLevel level) {
        ServerPlayer owner = client == null ? null : level.getServer().getPlayerList().getPlayer(client);
        if (owner != null && !FormActions.state(owner).form().isJarred()) {
            if (!toldWaiting) com.coolfreeze23.headinajar.feedback.Warnings.warn(owner, 20, "printer.body_waits", Component.translatable("block.headinajar.body_printer"));
            toldWaiting = true;
            return;
        }
        toldWaiting = false;
        print(level);
    }

    private void print(ServerLevel level) {
        Direction front = getBlockState().getValue(MachineBlock.FACING);
        Vec3 at = Vec3.atBottomCenterOf(worldPosition.relative(front));
        HeadlessBody body = new HeadlessBody(ModRegistry.HEADLESS_BODY.get(), level);
        body.moveTo(at.x, at.y, at.z, front.toYRot(), 0);
        body.setYBodyRot(front.toYRot());
        body.setOwner(client, clientName);
        body.setCollar(true);
        level.addFreshEntity(body);
        HeadRegistry.get(level.getServer()).setBody(client, new HeadRegistry.Body(body.getUUID(), HeadRegistry.Spot.of(body), false));
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(client);
        if (owner != null) {
            var state = FormActions.state(owner);
            // whatever carries the head now (a host, a mecha, a socket), the new body is the one to go back to
            if (state.form().isJarred()) state.setBody(body.getUUID());
            owner.displayClientMessage(Component.translatable("message.headinajar.printed"), false);
        }
        level.playSound(null, worldPosition, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.8f, 0.5f);
        level.sendParticles(ParticleTypes.POOF, at.x, at.y + 1, at.z, 20, 0.3, 0.6, 0.3, 0.02);
        progress = -1;
        client = null;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Energy", energy.serializeNBT(registries));
        tag.put("Items", items.serializeNBT(registries));
        tag.put("Saline", saline.writeToNBT(registries, new CompoundTag()));
        tag.putInt("Progress", progress);
        if (client != null) tag.putUUID("Client", client);
        tag.putString("ClientName", clientName);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Energy")) energy.deserializeNBT(registries, tag.get("Energy"));
        MachineItems.load(items, tag.getCompound("Items"), registries);
        saline.readFromNBT(registries, tag.getCompound("Saline"));
        progress = tag.contains("Progress") ? tag.getInt("Progress") : -1;
        client = tag.hasUUID("Client") ? tag.getUUID("Client") : null;
        clientName = tag.getString("ClientName");
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.headinajar.body_printer");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        kit.startOpen(player);
        return new BodyPrinterMenu(id, inventory, items, data, worldPosition);
    }
}
